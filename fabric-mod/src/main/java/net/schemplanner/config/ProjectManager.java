package net.schemplanner.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.schemplanner.schematic.MaterialRequirement;
import net.schemplanner.schematic.PlannedBlock;
import net.schemplanner.schematic.SpongeSchematic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Manages loading, caching, scanning and live updating of schematic projects.
 * Supports schematics in .minecraft/config/schemplanner/projects/ and .minecraft/schematics/
 */
public class ProjectManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("SchemPlanner");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path PROJECTS_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("schemplanner")
            .resolve("projects");

    private static final Path SCHEMATICS_DIR = FabricLoader.getInstance()
            .getGameDir()
            .resolve("schematics");

    public record LoadedProject(ProjectConfig config, SpongeSchematic schematic) {}
    public record AvailableSchematic(String name, String relativePath, Path fullPath, String format) {}

    private static final List<LoadedProject> loadedProjects = new ArrayList<>();
    private static int activeProjectIndex = 0;

    public static void initialize() {
        try {
            if (!Files.exists(PROJECTS_DIR)) {
                Files.createDirectories(PROJECTS_DIR);
            }
            if (!Files.exists(SCHEMATICS_DIR)) {
                Files.createDirectories(SCHEMATICS_DIR);
            }
        } catch (Exception e) {
            LOGGER.error("[SchemPlanner] Fehler beim Erstellen der Verzeichnisse", e);
        }
        reloadProjects();
    }

    public static synchronized void reloadProjects() {
        loadedProjects.clear();

        if (!Files.exists(PROJECTS_DIR)) {
            return;
        }

        try (Stream<Path> stream = Files.list(PROJECTS_DIR)) {
            List<Path> jsonFiles = stream.filter(p -> p.toString().endsWith(".json")).toList();
            for (Path jsonPath : jsonFiles) {
                try (Reader reader = Files.newBufferedReader(jsonPath)) {
                    ProjectConfig config = GSON.fromJson(reader, ProjectConfig.class);
                    if (config == null || config.schematic == null || config.schematic.isEmpty()) {
                        continue;
                    }

                    Path schemPath = resolveSchematicPath(config.schematic);
                    if (schemPath == null || !Files.exists(schemPath)) {
                        LOGGER.warn("[SchemPlanner] Schematic-Datei nicht gefunden: {}", config.schematic);
                        continue;
                    }

                    SpongeSchematic schematic = SpongeSchematic.load(
                            config.id,
                            schemPath,
                            config.origin.toBlockPos(),
                            config.rotation
                    );

                    loadedProjects.add(new LoadedProject(config, schematic));
                } catch (Exception e) {
                    LOGGER.error("[SchemPlanner] Fehler beim Laden des Projekts von " + jsonPath, e);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[SchemPlanner] Fehler beim Durchsuchen des Projektverzeichnisses", e);
        }

        if (activeProjectIndex >= loadedProjects.size()) {
            activeProjectIndex = Math.max(0, loadedProjects.size() - 1);
        }

        LOGGER.info("[SchemPlanner] Erfolgreich {} Projekte geladen.", loadedProjects.size());
    }

    private static Path resolveSchematicPath(String schemRef) {
        // 1. In projects dir
        Path p1 = PROJECTS_DIR.resolve(schemRef);
        if (Files.exists(p1)) return p1;

        // 2. In schematics dir
        Path p2 = SCHEMATICS_DIR.resolve(schemRef);
        if (Files.exists(p2)) return p2;

        // 3. Absolute path
        try {
            Path p3 = Path.of(schemRef);
            if (Files.exists(p3)) return p3;
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Recursively scans .minecraft/schematics/ and .minecraft/config/schemplanner/projects/
     * for all available .schem, .schematic, .litematic, and .nbt files.
     */
    public static List<AvailableSchematic> getAvailableSchematics() {
        List<AvailableSchematic> list = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();

        scanDirForSchematics(SCHEMATICS_DIR, SCHEMATICS_DIR, list, seenPaths);
        scanDirForSchematics(PROJECTS_DIR, PROJECTS_DIR, list, seenPaths);

        list.sort(Comparator.comparing(AvailableSchematic::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private static void scanDirForSchematics(Path root, Path dir, List<AvailableSchematic> list, Set<String> seenPaths) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                if (Files.isDirectory(path)) {
                    scanDirForSchematics(root, path, list, seenPaths);
                } else {
                    String fileName = path.getFileName().toString();
                    String lower = fileName.toLowerCase();
                    String format = null;
                    if (lower.endsWith(".litematic")) format = "Litematica";
                    else if (lower.endsWith(".schem") || lower.endsWith(".schematic")) format = "Sponge";
                    else if (lower.endsWith(".nbt")) format = "NBT Structure";

                    if (format != null) {
                        String absKey = path.toAbsolutePath().normalize().toString();
                        if (seenPaths.add(absKey)) {
                            String rel = root.relativize(path).toString().replace('\\', '/');
                            list.add(new AvailableSchematic(fileName, rel, path, format));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Creates a new project from any discovered schematic file and places it at the target world position.
     * Supports placing multiple copies of the same schematic concurrently.
     */
    public static synchronized LoadedProject createAndPlaceProject(Path schemFile, BlockPos originPos, int rotation) {
        try {
            String fileName = schemFile.getFileName().toString();
            String baseId = fileName.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
            if (baseId.endsWith(".schem") || baseId.endsWith(".litematic") || baseId.endsWith(".nbt")) {
                baseId = baseId.substring(0, baseId.lastIndexOf('.'));
            }

            // Generate unique instance ID so schematics can be placed multiple times
            String id = baseId;
            int count = 2;
            while (isProjectIdTaken(id)) {
                id = baseId + "_" + count++;
            }
            final String finalId = id;

            // Reference relative to schematics folder if inside it, else relative to projects dir or absolute
            String schemRef;
            if (schemFile.startsWith(SCHEMATICS_DIR)) {
                schemRef = SCHEMATICS_DIR.relativize(schemFile).toString().replace('\\', '/');
            } else if (schemFile.startsWith(PROJECTS_DIR)) {
                schemRef = PROJECTS_DIR.relativize(schemFile).toString().replace('\\', '/');
            } else {
                schemRef = schemFile.toAbsolutePath().toString();
            }

            ProjectConfig config = new ProjectConfig(finalId, schemRef, originPos, rotation, true);
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null) {
                String playerName = client.player.getName().getString();
                config.owner = playerName;
                config.assignPlayer(playerName);
            }
            SpongeSchematic schematic = SpongeSchematic.load(finalId, schemFile, originPos, rotation);

            // Save JSON config
            saveProjectConfig(config);

            LoadedProject loaded = new LoadedProject(config, schematic);
            loadedProjects.add(loaded);
            activeProjectIndex = loadedProjects.size() - 1;

            LOGGER.info("[SchemPlanner] Neues Projekt '{}' erstellt und platziert bei {} mit Drehung {}°", finalId, originPos, rotation);
            return loaded;
        } catch (Exception e) {
            LOGGER.error("[SchemPlanner] Fehler beim Erstellen des Projekts für " + schemFile, e);
            return null;
        }
    }

    public static synchronized LoadedProject createAndPlaceProject(Path schemFile, BlockPos originPos) {
        return createAndPlaceProject(schemFile, originPos, 0);
    }

    public static synchronized boolean isProjectIdTaken(String id) {
        return loadedProjects.stream().anyMatch(p -> p.config().id.equalsIgnoreCase(id));
    }

    /**
     * Updates an active project's origin and rotation in real-time.
     */
    public static synchronized void updateProjectTransform(String projectId, BlockPos newOrigin, int newRotation) {
        for (LoadedProject proj : loadedProjects) {
            if (proj.config.id.equals(projectId)) {
                proj.config.origin.x = newOrigin.getX();
                proj.config.origin.y = newOrigin.getY();
                proj.config.origin.z = newOrigin.getZ();
                proj.config.rotation = ((newRotation % 360) + 360) % 360;

                proj.schematic.updateTransform(newOrigin, proj.config.rotation);
                saveProjectConfig(proj.config);
                break;
            }
        }
    }

    /**
     * Toggles visibility (unlocked) of a project.
     */
    public static synchronized void toggleProjectVisibility(String projectId) {
        for (LoadedProject proj : loadedProjects) {
            if (proj.config.id.equals(projectId)) {
                proj.config.unlocked = !proj.config.unlocked;
                saveProjectConfig(proj.config);
                break;
            }
        }
    }

    /**
     * Removes/deletes a project configuration.
     */
    public static synchronized void deleteProject(String projectId) {
        loadedProjects.removeIf(p -> p.config.id.equals(projectId));
        try {
            Path jsonPath = PROJECTS_DIR.resolve(projectId + ".json");
            Files.deleteIfExists(jsonPath);
        } catch (Exception ignored) {}
        if (activeProjectIndex >= loadedProjects.size()) {
            activeProjectIndex = Math.max(0, loadedProjects.size() - 1);
        }
    }

    public static synchronized void saveProjectConfig(ProjectConfig config) {
        try {
            Path jsonPath = PROJECTS_DIR.resolve(config.id + ".json");
            try (Writer writer = Files.newBufferedWriter(jsonPath)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception e) {
            LOGGER.error("[SchemPlanner] Fehler beim Speichern der Konfiguration für " + config.id, e);
        }
    }

    public static synchronized LoadedProject getProject(String id) {
        if (id == null) return null;
        for (LoadedProject proj : loadedProjects) {
            if (proj.config.id.equalsIgnoreCase(id)) {
                return proj;
            }
        }
        return null;
    }

    public enum VisibilityFilter {
        ALL,
        ASSIGNED_ONLY
    }

    private static VisibilityFilter currentVisibilityFilter = VisibilityFilter.ALL;

    public static synchronized VisibilityFilter getVisibilityFilter() {
        return currentVisibilityFilter;
    }

    public static synchronized void setVisibilityFilter(VisibilityFilter filter) {
        currentVisibilityFilter = filter;
    }

    public static synchronized void toggleVisibilityFilter() {
        currentVisibilityFilter = (currentVisibilityFilter == VisibilityFilter.ALL)
                ? VisibilityFilter.ASSIGNED_ONLY
                : VisibilityFilter.ALL;
    }

    public static boolean isProjectVisibleForPlayer(LoadedProject proj, String playerName) {
        if (proj == null || proj.config() == null || !proj.config().unlocked) return false;
        if (currentVisibilityFilter == VisibilityFilter.ALL) return true;
        return proj.config().isAssignedTo(playerName);
    }

    public static synchronized LoadedProject getActiveProject() {
        if (loadedProjects.isEmpty()) return null;
        if (activeProjectIndex < 0 || activeProjectIndex >= loadedProjects.size()) {
            activeProjectIndex = 0;
        }
        return loadedProjects.get(activeProjectIndex);
    }

    public static synchronized void setActiveProjectIndex(int index) {
        if (index >= 0 && index < loadedProjects.size()) {
            activeProjectIndex = index;
        }
    }

    public static synchronized int getActiveProjectIndex() {
        return activeProjectIndex;
    }

    public static Path getProjectsDir() {
        return PROJECTS_DIR;
    }

    public static Path getSchematicsDir() {
        return SCHEMATICS_DIR;
    }

    public static void openSchematicsFolder() {
        try {
            if (!Files.exists(SCHEMATICS_DIR)) {
                Files.createDirectories(SCHEMATICS_DIR);
            }
            Util.getPlatform().openPath(SCHEMATICS_DIR);
        } catch (Exception e) {
            LOGGER.error("[SchemPlanner] Konnte Ordner nicht öffnen", e);
        }
    }

    public static synchronized List<LoadedProject> getLoadedProjects() {
        return Collections.unmodifiableList(loadedProjects);
    }

    public static synchronized List<PlannedBlock> getAllPlannedBlocks() {
        Minecraft client = Minecraft.getInstance();
        String playerName = (client != null && client.player != null) ? client.player.getName().getString() : null;

        List<PlannedBlock> list = new ArrayList<>();
        for (LoadedProject project : loadedProjects) {
            if (isProjectVisibleForPlayer(project, playerName) && project.schematic != null) {
                list.addAll(project.schematic.getPlannedBlocks());
            }
        }
        return list;
    }

    public static synchronized Map<Item, MaterialRequirement> getAggregatedMaterials() {
        Minecraft client = Minecraft.getInstance();
        String playerName = (client != null && client.player != null) ? client.player.getName().getString() : null;

        Map<Item, MaterialRequirement> map = new LinkedHashMap<>();
        for (LoadedProject project : loadedProjects) {
            if (isProjectVisibleForPlayer(project, playerName) && project.schematic != null) {
                for (MaterialRequirement req : project.schematic.getMaterialRequirements().values()) {
                    map.computeIfAbsent(req.getItem(), k -> new MaterialRequirement(req.getItem(), 0))
                            .increment(req.getRequiredCount());
                }
            }
        }
        return map;
    }
}
