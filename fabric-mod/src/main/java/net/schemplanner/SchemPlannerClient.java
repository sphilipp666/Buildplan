package net.schemplanner;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.schemplanner.config.ProjectManager;
import net.schemplanner.gui.MaterialListScreen;
import net.schemplanner.input.KeyBindings;
import net.schemplanner.placement.PlacementSession;
import net.schemplanner.render.GhostBlockRenderer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.schemplanner.selection.AreaSelectionManager;
import net.schemplanner.sync.GitHubProjectSync;
import net.schemplanner.util.RaycastHelper;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
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

        // 3. Register Level Rendering Hook for Ghost Hologram
        LevelRenderEvents.BEFORE_GIZMOS.register(GhostBlockRenderer::render);

        // 4. Register Key Listeners in Client Tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Free-Walk Ingame Placement Session Handling
            if (client.gui != null && client.gui.screen() == null && PlacementSession.isActive()) {
                ProjectManager.LoadedProject proj = PlacementSession.getActiveProject();
                if (proj != null) {
                    BlockPos o = proj.config().origin.toBlockPos();
                    int rot = proj.config().rotation;
                    String msg = String.format("§6[Justieren: §f%s§6] §bPos: %d, %d, %d §8| §eRot: %d° §8| §f[C] §7Blickziel §8| §f[R] §7Drehen §8| §f[P] §7Menü §8| §a[Enter] §7Fertig",
                            proj.config().id, o.getX(), o.getY(), o.getZ(), rot);
                    client.player.sendOverlayMessage(Component.literal(msg));

                    // R: Rotate 90°
                    while (KeyBindings.keyRotate.consumeClick()) {
                        PlacementSession.rotate();
                    }

                    // C: Snap to crosshair block
                    while (KeyBindings.keySnapTarget.consumeClick()) {
                        PlacementSession.snapToCrosshair(client);
                        client.player.sendOverlayMessage(Component.literal("§b[Bauplan] §aAnvisierter Block übernommen!"));
                    }

                    // Enter: Finish and lock
                    while (KeyBindings.keyConfirmPlacement.consumeClick()) {
                        String id = proj.config().id;
                        PlacementSession.stop();
                        client.player.sendSystemMessage(Component.literal("§a✔ Bauplan '" + id + "' platziert!"));
                    }
                }
            } else if (client.gui != null && client.gui.screen() == null && AreaSelectionManager.isActive()) {
                BlockPos p1 = AreaSelectionManager.getPos1();
                BlockPos p2 = AreaSelectionManager.getPos2();
                String p1Str = (p1 != null) ? (p1.getX() + "," + p1.getY() + "," + p1.getZ()) : "§7[Nicht gesetzt]";
                String p2Str = (p2 != null) ? (p2.getX() + "," + p2.getY() + "," + p2.getZ()) : "§7[Nicht gesetzt]";
                String dimStr = AreaSelectionManager.isComplete() ? (" §8| §b" + AreaSelectionManager.getWidth() + "x" + AreaSelectionManager.getHeight() + "x" + AreaSelectionManager.getLength()) : "";
                String msg = String.format("§e[Kopieren] §aP1: §f%s §8| §9P2: §f%s%s §8| §f[V] §7Pos 1 §8| §f[B] §7Pos 2 §8| §f[P] §7Speichern",
                        p1Str, p2Str, dimStr);
                client.player.sendOverlayMessage(Component.literal(msg));
            }

            // V: Select Pos 1
            while (KeyBindings.keySelectPos1.consumeClick()) {
                BlockPos target = RaycastHelper.getTargetedBlockPos(client);
                AreaSelectionManager.setPos1(target);
                AreaSelectionManager.setActive(true);
                String feedback = String.format("§a✔ [Kopieren] Pos 1 gesetzt: §f%d, %d, %d", target.getX(), target.getY(), target.getZ());
                if (AreaSelectionManager.isComplete()) {
                    feedback += " §8| §b" + AreaSelectionManager.getDimensionsString();
                }
                client.player.sendOverlayMessage(Component.literal(feedback));
                client.player.sendSystemMessage(Component.literal(feedback));
            }

            // B: Select Pos 2
            while (KeyBindings.keySelectPos2.consumeClick()) {
                BlockPos target = RaycastHelper.getTargetedBlockPos(client);
                AreaSelectionManager.setPos2(target);
                AreaSelectionManager.setActive(true);
                String feedback = String.format("§9✔ [Kopieren] Pos 2 gesetzt: §f%d, %d, %d", target.getX(), target.getY(), target.getZ());
                if (AreaSelectionManager.isComplete()) {
                    feedback += " §8| §b" + AreaSelectionManager.getDimensionsString();
                }
                client.player.sendOverlayMessage(Component.literal(feedback));
                client.player.sendSystemMessage(Component.literal(feedback));
            }

            // Page Up: Layer +1
            while (KeyBindings.keyLayerUp.consumeClick()) {
                GhostBlockRenderer.layerUp();
                client.player.sendOverlayMessage(Component.translatable("msg.schemplanner.layer_set", GhostBlockRenderer.currentLayerY));
            }

            // Page Down: Layer -1
            while (KeyBindings.keyLayerDown.consumeClick()) {
                GhostBlockRenderer.layerDown();
                client.player.sendOverlayMessage(Component.translatable("msg.schemplanner.layer_set", GhostBlockRenderer.currentLayerY));
            }

            // Home: Toggle Layer Filter / All Layers
            while (KeyBindings.keyLayerToggle.consumeClick()) {
                GhostBlockRenderer.toggleLayerFilter();
                if (GhostBlockRenderer.isLayerFilterActive()) {
                    client.player.sendOverlayMessage(Component.translatable("msg.schemplanner.layer_set", GhostBlockRenderer.currentLayerY));
                } else {
                    client.player.sendOverlayMessage(Component.translatable("msg.schemplanner.layer_all"));
                }
            }

            // M: Open Material To-Do Screen
            while (KeyBindings.keyMaterialList.consumeClick()) {
                client.setScreenAndShow(new MaterialListScreen());
            }

            // P: Open Schematic & Placement Manager or Area Selection directly
            while (KeyBindings.keyPlacement.consumeClick()) {
                MaterialListScreen screen = new MaterialListScreen();
                if (AreaSelectionManager.isComplete() || AreaSelectionManager.isActive()) {
                    screen.switchToAreaSelectionTab();
                } else {
                    screen.switchToSchematicsTab();
                }
                client.setScreenAndShow(screen);
            }

            // F10: Reload Schematics & Configs
            while (KeyBindings.keyReload.consumeClick()) {
                ProjectManager.reloadProjects();
                client.player.sendSystemMessage(Component.translatable("msg.schemplanner.reloaded", ProjectManager.getLoadedProjects().size()));
            }
        });

        // 5. Register Client Commands (/schem and /project)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(buildProjectCommands("schem"));
            dispatcher.register(buildProjectCommands("project"));
        });

        LOGGER.info("[SchemPlanner] Client fuer Fabric 26.2 erfolgreich initialisiert.");
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildProjectCommands(String root) {
        return ClientCommands.literal(root)
            .then(ClientCommands.literal("sync")
                .executes(ctx -> {
                    Minecraft client = ctx.getSource().getClient();
                    GitHubProjectSync.sync(client, msg -> ctx.getSource().sendFeedback(Component.literal(msg)));
                    return 1;
                })
            )
            .then(ClientCommands.literal("filter")
                .executes(ctx -> {
                    ProjectManager.toggleVisibilityFilter();
                    boolean isAssignedOnly = ProjectManager.getVisibilityFilter() == ProjectManager.VisibilityFilter.ASSIGNED_ONLY;
                    ctx.getSource().sendFeedback(Component.literal(isAssignedOnly
                        ? "§e[Bauplan] Filter aktiv: §6[🎯 Nur meine Bauten]"
                        : "§e[Bauplan] Filter aktiv: §b[🌐 Alle Bauten anzeigen]"));
                    return 1;
                })
                .then(ClientCommands.literal("all")
                    .executes(ctx -> {
                        ProjectManager.setVisibilityFilter(ProjectManager.VisibilityFilter.ALL);
                        ctx.getSource().sendFeedback(Component.literal("§e[Bauplan] Filter aktiv: §b[🌐 Alle Bauten anzeigen]"));
                        return 1;
                    })
                )
                .then(ClientCommands.literal("mine")
                    .executes(ctx -> {
                        ProjectManager.setVisibilityFilter(ProjectManager.VisibilityFilter.ASSIGNED_ONLY);
                        ctx.getSource().sendFeedback(Component.literal("§e[Bauplan] Filter aktiv: §6[🎯 Nur meine Bauten]"));
                        return 1;
                    })
                )
            )
            .then(ClientCommands.literal("assign")
                .then(ClientCommands.argument("projectId", StringArgumentType.word())
                    .then(ClientCommands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "projectId");
                            String player = StringArgumentType.getString(ctx, "player");
                            ProjectManager.LoadedProject proj = ProjectManager.getProject(id);
                            if (proj == null) {
                                ctx.getSource().sendFeedback(Component.literal("§c✖ Kein Bauplan mit ID '" + id + "' gefunden."));
                                return 0;
                            }
                            if (proj.config().assignPlayer(player)) {
                                ProjectManager.saveProjectConfig(proj.config());
                                ctx.getSource().sendFeedback(Component.literal("§a✔ Spieler '" + player + "' wurde dem Bauplan '" + proj.config().getDisplayTitle() + "' zugewiesen."));
                            } else {
                                ctx.getSource().sendFeedback(Component.literal("§eℹ Spieler '" + player + "' ist diesem Bauplan bereits zugewiesen."));
                            }
                            return 1;
                        })
                    )
                )
            )
            .then(ClientCommands.literal("unassign")
                .then(ClientCommands.argument("projectId", StringArgumentType.word())
                    .then(ClientCommands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "projectId");
                            String player = StringArgumentType.getString(ctx, "player");
                            ProjectManager.LoadedProject proj = ProjectManager.getProject(id);
                            if (proj == null) {
                                ctx.getSource().sendFeedback(Component.literal("§c✖ Kein Bauplan mit ID '" + id + "' gefunden."));
                                return 0;
                            }
                            if (proj.config().unassignPlayer(player)) {
                                ProjectManager.saveProjectConfig(proj.config());
                                ctx.getSource().sendFeedback(Component.literal("§a✔ Spieler '" + player + "' wurde von Bauplan '" + proj.config().getDisplayTitle() + "' entfernt."));
                            } else {
                                ctx.getSource().sendFeedback(Component.literal("§eℹ Spieler '" + player + "' war diesem Bauplan nicht zugewiesen."));
                            }
                            return 1;
                        })
                    )
                )
            )
            .then(ClientCommands.literal("list")
                .executes(ctx -> {
                    var list = ProjectManager.getLoadedProjects();
                    if (list.isEmpty()) {
                        ctx.getSource().sendFeedback(Component.literal("§7[Bauplan] Keine aktiven Baupläne in der Welt."));
                        return 1;
                    }
                    ctx.getSource().sendFeedback(Component.literal("§e--- Aktive Baupläne (" + list.size() + ") ---"));
                    for (ProjectManager.LoadedProject p : list) {
                        String team = (p.config().assignedPlayers == null || p.config().assignedPlayers.isEmpty())
                            ? "§bAlle"
                            : "§b" + String.join(", ", p.config().assignedPlayers);
                        String status = p.config().getStatusBadge();
                        String line = String.format(" %s §f%s §7(ID: §8%s§7) -> Team: %s", status, p.config().getDisplayTitle(), p.config().id, team);
                        ctx.getSource().sendFeedback(Component.literal(line));
                    }
                    return 1;
                })
            );
    }
}
