package net.schemplanner.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.schemplanner.schematic.MaterialRequirement;
import net.schemplanner.schematic.PlannedBlock;
import net.schemplanner.schematic.SpongeSchematic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Manages loading and caching of all projects from .minecraft/config/schemplanner/projects/
 */
public class ProjectManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("SchemPlanner");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path PROJECTS_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("schemplanner")
            .resolve("projects");

    private static final List<LoadedProject> loadedProjects = new ArrayList<>();

    public record LoadedProject(ProjectConfig config, SpongeSchematic schematic) {}

    public static void initialize() {
        try {
            if (!Files.exists(PROJECTS_DIR)) {
                Files.createDirectories(PROJECTS_DIR);
                LOGGER.info("[SchemPlanner] Projektverzeichnis erstellt unter: {}", PROJECTS_DIR);
            }
        } catch (Exception e) {
            LOGGER.error("[SchemPlanner] Fehler beim Erstellen des Projektverzeichnisses", e);
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
            LOGGER.info("[SchemPlanner] Gefundene Projektdateien: {}", jsonFiles.size());

            for (Path jsonPath : jsonFiles) {
                try (Reader reader = Files.newBufferedReader(jsonPath)) {
                    ProjectConfig config = GSON.fromJson(reader, ProjectConfig.class);
                    if (config == null || config.schematic == null || config.schematic.isEmpty()) {
                        LOGGER.warn("[SchemPlanner] Ungültige Konfiguration in: {}", jsonPath);
                        continue;
                    }

                    if (!config.unlocked) {
                        LOGGER.info("[SchemPlanner] Projekt '{}' ist gesperrt (unlocked=false), wird ignoriert.", config.id);
                        continue;
                    }

                    Path schemPath = PROJECTS_DIR.resolve(config.schematic);
                    if (!Files.exists(schemPath)) {
                        LOGGER.warn("[SchemPlanner] Schematic-Datei nicht gefunden: {}", schemPath);
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

        LOGGER.info("[SchemPlanner] Erfolgreich {} freigeschaltete Projekte geladen.", loadedProjects.size());
    }

    public static Path getProjectsDir() {
        return PROJECTS_DIR;
    }

    public static synchronized List<LoadedProject> getLoadedProjects() {
        return Collections.unmodifiableList(loadedProjects);
    }

    /**
     * Collects all planned blocks across all currently loaded, unlocked projects.
     */
    public static synchronized List<PlannedBlock> getAllPlannedBlocks() {
        List<PlannedBlock> list = new ArrayList<>();
        for (LoadedProject project : loadedProjects) {
            list.addAll(project.schematic().getPlannedBlocks());
        }
        return list;
    }

    /**
     * Aggregates required materials across all loaded unlocked projects.
     */
    public static synchronized Map<Item, MaterialRequirement> getAggregatedMaterials() {
        Map<Item, MaterialRequirement> map = new LinkedHashMap<>();
        for (LoadedProject project : loadedProjects) {
            for (MaterialRequirement req : project.schematic().getMaterialRequirements().values()) {
                map.computeIfAbsent(req.getItem(), k -> new MaterialRequirement(req.getItem(), 0))
                        .increment(req.getRequiredCount());
            }
        }
        return map;
    }
}
