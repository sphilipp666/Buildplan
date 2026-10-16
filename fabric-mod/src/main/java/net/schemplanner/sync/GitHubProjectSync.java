package net.schemplanner.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.schemplanner.config.ProjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Handles asynchronous project and schematic synchronization from a GitHub repository.
 * Downloads index.json and synchronizes all missing or updated .json and .schem files.
 */
public class GitHubProjectSync {
    private static final Logger LOGGER = LoggerFactory.getLogger("SchemPlanner-Sync");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String DEFAULT_REPO_URL = "https://raw.githubusercontent.com/sphilipp666/Buildplan/main/shared_projects/";

    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("schemplanner")
            .resolve("sync_config.json");

    public static class SyncConfig {
        public String baseUrl = DEFAULT_REPO_URL;
    }

    public record ProjectIndexEntry(String id, String title, String jsonUrl, String schemUrl) {}

    private static SyncConfig cachedConfig = null;
    private static final AtomicBoolean isSyncing = new AtomicBoolean(false);

    public static synchronized SyncConfig getConfig() {
        if (cachedConfig != null) return cachedConfig;

        if (Files.exists(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
                cachedConfig = GSON.fromJson(reader, SyncConfig.class);
            } catch (Exception e) {
                LOGGER.error("[SchemPlanner] Fehler beim Lesen von sync_config.json", e);
            }
        }

        if (cachedConfig == null || cachedConfig.baseUrl == null || cachedConfig.baseUrl.isBlank()) {
            cachedConfig = new SyncConfig();
            saveConfig(cachedConfig);
        }

        return cachedConfig;
    }

    public static synchronized void saveConfig(SyncConfig config) {
        cachedConfig = config;
        try {
            if (CONFIG_FILE.getParent() != null && !Files.exists(CONFIG_FILE.getParent())) {
                Files.createDirectories(CONFIG_FILE.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception e) {
            LOGGER.error("[SchemPlanner] Fehler beim Speichern von sync_config.json", e);
        }
    }

    public static void setBaseUrl(String newUrl) {
        SyncConfig config = getConfig();
        String trimmed = newUrl.trim();
        if (!trimmed.endsWith("/")) {
            trimmed += "/";
        }
        config.baseUrl = trimmed;
        saveConfig(config);
    }

    public static void sync(Minecraft client, Consumer<String> feedbackConsumer) {
        if (!isSyncing.compareAndSet(false, true)) {
            sendFeedback(client, feedbackConsumer, "§e[GitHub-Sync] Ein Synchronisationsvorgang läuft bereits...");
            return;
        }

        SyncConfig config = getConfig();
        String baseUrl = config.baseUrl;
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        sendFeedback(client, feedbackConsumer, "§b[GitHub-Sync] §7Verbinde mit GitHub: §f" + baseUrl + "...");

        final String finalBaseUrl = baseUrl;

        CompletableFuture.runAsync(() -> {
            try {
                HttpClient httpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();

                // 1. Fetch index.json
                URI indexUri = URI.create(finalBaseUrl + "index.json");
                HttpRequest indexReq = HttpRequest.newBuilder(indexUri)
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "SchemPlanner-Fabric")
                        .GET()
                        .build();

                HttpResponse<String> indexResp = httpClient.send(indexReq, HttpResponse.BodyHandlers.ofString());
                if (indexResp.statusCode() != 200) {
                    sendFeedback(client, feedbackConsumer, String.format("§c[GitHub-Sync] Fehler: index.json konnte nicht geladen werden (HTTP %d)", indexResp.statusCode()));
                    return;
                }

                ProjectIndexEntry[] entries = GSON.fromJson(indexResp.body(), ProjectIndexEntry[].class);
                if (entries == null || entries.length == 0) {
                    sendFeedback(client, feedbackConsumer, "§e[GitHub-Sync] Keine Projekte in index.json vorhanden.");
                    return;
                }

                sendFeedback(client, feedbackConsumer, String.format("§b[GitHub-Sync] §7%d Projekt(e) in index.json gefunden. Prüfe lokale Dateien...", entries.length));

                Path projectsDir = ProjectManager.getProjectsDir();
                Path schematicsDir = ProjectManager.getSchematicsDir();
                if (!Files.exists(projectsDir)) Files.createDirectories(projectsDir);
                if (!Files.exists(schematicsDir)) Files.createDirectories(schematicsDir);

                int downloadedProjects = 0;
                int downloadedFiles = 0;

                for (ProjectIndexEntry entry : entries) {
                    if (entry == null || entry.id() == null || entry.id().isBlank()) continue;

                    String jsonFileName = (entry.jsonUrl() != null && !entry.jsonUrl().isBlank())
                            ? entry.jsonUrl()
                            : (entry.id() + ".json");
                    String schemFileName = (entry.schemUrl() != null && !entry.schemUrl().isBlank())
                            ? entry.schemUrl()
                            : (entry.id() + ".schem");

                    Path targetJson = projectsDir.resolve(jsonFileName);
                    Path targetSchemInSchematics = schematicsDir.resolve(schemFileName);
                    Path targetSchemInProjects = projectsDir.resolve(schemFileName);

                    boolean needJson = !Files.exists(targetJson);
                    boolean needSchem = !Files.exists(targetSchemInSchematics) && !Files.exists(targetSchemInProjects);

                    if (needJson || needSchem) {
                        boolean projectSuccess = true;

                        // Download JSON
                        if (needJson) {
                            URI jUri = URI.create(finalBaseUrl + jsonFileName);
                            HttpRequest jReq = HttpRequest.newBuilder(jUri)
                                    .timeout(Duration.ofSeconds(15))
                                    .header("User-Agent", "SchemPlanner-Fabric")
                                    .GET()
                                    .build();
                            HttpResponse<String> jResp = httpClient.send(jReq, HttpResponse.BodyHandlers.ofString());
                            if (jResp.statusCode() == 200) {
                                Files.writeString(targetJson, jResp.body());
                                downloadedFiles++;
                            } else {
                                LOGGER.warn("[SchemPlanner] Konnte {} nicht herunterladen: HTTP {}", jsonFileName, jResp.statusCode());
                                projectSuccess = false;
                            }
                        }

                        // Download Schematic
                        if (needSchem) {
                            URI sUri = URI.create(finalBaseUrl + schemFileName);
                            HttpRequest sReq = HttpRequest.newBuilder(sUri)
                                    .timeout(Duration.ofSeconds(30))
                                    .header("User-Agent", "SchemPlanner-Fabric")
                                    .GET()
                                    .build();
                            HttpResponse<byte[]> sResp = httpClient.send(sReq, HttpResponse.BodyHandlers.ofByteArray());
                            if (sResp.statusCode() == 200) {
                                byte[] bytes = sResp.body();
                                Files.write(targetSchemInSchematics, bytes);
                                Files.write(targetSchemInProjects, bytes);
                                downloadedFiles++;
                            } else {
                                LOGGER.warn("[SchemPlanner] Konnte {} nicht herunterladen: HTTP {}", schemFileName, sResp.statusCode());
                                projectSuccess = false;
                            }
                        }

                        if (projectSuccess) {
                            downloadedProjects++;
                            String title = (entry.title() != null && !entry.title().isBlank()) ? entry.title() : entry.id();
                            sendFeedback(client, feedbackConsumer, String.format("§a✔ [GitHub-Sync] Heruntergeladen: §f%s §7(%s)", title, entry.id()));
                        }
                    }
                }

                // 2. Reload projects locally on main thread
                client.execute(ProjectManager::reloadProjects);

                if (downloadedProjects > 0 || downloadedFiles > 0) {
                    sendFeedback(client, feedbackConsumer, String.format("§a✔ [GitHub-Sync] Erfolgreich synchronisiert! §f%d Projekt(e) §aaktualisiert.", downloadedProjects));
                } else {
                    sendFeedback(client, feedbackConsumer, String.format("§a✔ [GitHub-Sync] Alles aktuell! Alle §f%d §aProjekte sind bereits lokal vorhanden.", entries.length));
                }

            } catch (Exception e) {
                LOGGER.error("[SchemPlanner] GitHub-Sync Fehler", e);
                sendFeedback(client, feedbackConsumer, "§c[GitHub-Sync] Fehler: " + e.getMessage());
            } finally {
                isSyncing.set(false);
            }
        });
    }

    private static void sendFeedback(Minecraft client, Consumer<String> consumer, String message) {
        if (client != null) {
            client.execute(() -> {
                if (consumer != null) {
                    consumer.accept(message);
                } else if (client.player != null) {
                    client.player.sendSystemMessage(Component.literal(message));
                }
            });
        }
    }
}
