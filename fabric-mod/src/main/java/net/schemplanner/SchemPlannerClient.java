package net.schemplanner;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Text;
import net.schemplanner.config.ProjectManager;
import net.schemplanner.gui.MaterialListScreen;
import net.schemplanner.input.KeyBindings;
import net.schemplanner.render.GhostBlockRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point for the Schematic Build Planner mod targeting Fabric 26.2.
 */
public class SchemPlannerClient implements ClientModInitializer {
    public static final String MOD_ID = "schemplanner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[SchemPlanner] Initialisiere Client Mod fuer Fabric 26.2...");

        // 1. Register Keybindings
        KeyBindings.register();

        // 2. Initialize and Load Projects from .minecraft/config/schemplanner/projects/
        ProjectManager.initialize();

        // 3. Register Key Listeners in Client Tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Page Up: Layer +1
            while (KeyBindings.keyLayerUp.wasPressed()) {
                GhostBlockRenderer.layerUp();
                client.player.sendMessage(Text.translatable("msg.schemplanner.layer_set", GhostBlockRenderer.currentLayerY), true);
            }

            // Page Down: Layer -1
            while (KeyBindings.keyLayerDown.wasPressed()) {
                GhostBlockRenderer.layerDown();
                client.player.sendMessage(Text.translatable("msg.schemplanner.layer_set", GhostBlockRenderer.currentLayerY), true);
            }

            // Home: Toggle Layer Filter / All Layers
            while (KeyBindings.keyLayerToggle.wasPressed()) {
                GhostBlockRenderer.toggleLayerFilter();
                if (GhostBlockRenderer.isLayerFilterActive()) {
                    client.player.sendMessage(Text.translatable("msg.schemplanner.layer_set", GhostBlockRenderer.currentLayerY), true);
                } else {
                    client.player.sendMessage(Text.translatable("msg.schemplanner.layer_all"), true);
                }
            }

            // M: Open Material To-Do Screen
            while (KeyBindings.keyMaterialList.wasPressed()) {
                client.setScreen(new MaterialListScreen());
            }

            // F10: Reload Schematics & Configs
            while (KeyBindings.keyReload.wasPressed()) {
                ProjectManager.reloadProjects();
                client.player.sendMessage(Text.translatable("msg.schemplanner.reloaded", ProjectManager.getLoadedProjects().size()), false);
            }
        });

        LOGGER.info("[SchemPlanner] Client fuer Fabric 26.2 erfolgreich initialisiert.");
    }
}
