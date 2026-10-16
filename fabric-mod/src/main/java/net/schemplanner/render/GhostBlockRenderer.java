package net.schemplanner.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.schemplanner.config.ProjectManager;
import net.schemplanner.placement.PlacementSession;
import net.schemplanner.schematic.PlannedBlock;
import net.schemplanner.schematic.SpongeSchematic;
import net.schemplanner.selection.AreaSelectionManager;

import java.util.List;

/**
 * Handles in-game 3D textured block hologram rendering, layer-by-layer filtering, and bounding box visualization.
 * Ensures textured models are always rendered even when intersecting terrain.
 */
public class GhostBlockRenderer {
    public static int currentLayerY = Integer.MAX_VALUE;
    private static final double MAX_RENDER_DISTANCE_SQ = 96.0 * 96.0;

    // Reusable render helper instances
    private static BlockModelResolver blockModelResolver;
    private static final BlockModelRenderState modelRenderState = new BlockModelRenderState();
    private static final BlockDisplayContext displayContext = BlockDisplayContext.create();

    public static boolean isLayerFilterActive() {
        return currentLayerY != Integer.MAX_VALUE;
    }

    public static void toggleLayerFilter() {
        if (isLayerFilterActive()) {
            currentLayerY = Integer.MAX_VALUE;
        } else {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                currentLayerY = client.player.blockPosition().getY();
            } else {
                currentLayerY = 64;
            }
        }
    }

    public static void layerUp() {
        if (!isLayerFilterActive()) {
            Minecraft client = Minecraft.getInstance();
            currentLayerY = (client.player != null) ? client.player.blockPosition().getY() : 64;
        } else {
            currentLayerY++;
        }
    }

    public static void layerDown() {
        if (!isLayerFilterActive()) {
            Minecraft client = Minecraft.getInstance();
            currentLayerY = (client.player != null) ? client.player.blockPosition().getY() : 64;
        } else {
            currentLayerY--;
        }
    }

    public static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        List<ProjectManager.LoadedProject> projects = ProjectManager.getLoadedProjects();
        if (projects.isEmpty()) return;

        if (blockModelResolver == null) {
            blockModelResolver = new BlockModelResolver(client.getModelManager());
        }

        Vec3 camPos = client.gameRenderer.mainCamera().position();
        double camX = camPos.x;
        double camY = camPos.y;
        double camZ = camPos.z;

        PoseStack poseStack = context.poseStack();
        if (poseStack == null) {
            poseStack = new PoseStack();
        }

        try (var ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
            // 0. Render Area Selection (Copy Box)
            BlockPos p1 = AreaSelectionManager.getPos1();
            BlockPos p2 = AreaSelectionManager.getPos2();
            if (p1 != null && p2 != null) {
                BlockPos min = AreaSelectionManager.getMinPos();
                BlockPos max = AreaSelectionManager.getMaxPos();
                AABB selBox = new AABB(min.getX(), min.getY(), min.getZ(),
                        max.getX() + 1, max.getY() + 1, max.getZ() + 1);
                Gizmos.cuboid(selBox, GizmoStyle.stroke(0xFF00FFCC, 2.5f));
                Gizmos.cuboid(p1, GizmoStyle.stroke(0xFF00FF66, 3.0f));
                Gizmos.cuboid(p2, GizmoStyle.stroke(0xFF0088FF, 3.0f));
            } else if (p1 != null) {
                Gizmos.cuboid(p1, GizmoStyle.stroke(0xFF00FF66, 3.0f));
            } else if (p2 != null) {
                Gizmos.cuboid(p2, GizmoStyle.stroke(0xFF0088FF, 3.0f));
            }

            String playerName = (client.player != null) ? client.player.getName().getString() : null;
            for (ProjectManager.LoadedProject proj : projects) {
                if (!ProjectManager.isProjectVisibleForPlayer(proj, playerName)) continue;
                SpongeSchematic schem = proj.schematic();
                if (schem == null) continue;

                boolean isSessionActive = PlacementSession.isActive() &&
                        PlacementSession.getActiveProject().config().id.equals(proj.config().id);

                // 1. Draw overall bounding box for the schematic
                BlockPos origin = schem.getCurrentOrigin();
                int w = schem.getRotatedWidth();
                int h = schem.getHeight();
                int l = schem.getRotatedLength();
                AABB boundingBox = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                        origin.getX() + w, origin.getY() + h, origin.getZ() + l);

                int boxColor = isSessionActive ? 0xFFFFBB00 : 0x88FFDD44;
                float boxWidth = isSessionActive ? 2.5f : 1.5f;
                Gizmos.cuboid(boundingBox, GizmoStyle.stroke(boxColor, boxWidth));

                // 2. Render blocks
                List<PlannedBlock> blocks = schem.getPlannedBlocks();
                for (PlannedBlock planned : blocks) {
                    BlockPos pos = planned.worldPos();

                    // Layer culling
                    if (isLayerFilterActive() && pos.getY() > currentLayerY) {
                        continue;
                    }

                    // Distance culling
                    double dx = pos.getX() + 0.5 - camX;
                    double dy = pos.getY() + 0.5 - camY;
                    double dz = pos.getZ() + 0.5 - camZ;
                    if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DISTANCE_SQ) {
                        continue;
                    }

                    BlockState worldState = client.level.getBlockState(pos);

                    // Case A: Correct block already placed -> Skip completely (already built!)
                    if (worldState.equals(planned.state())) {
                        continue;
                    }

                    // ALWAYS render real 3D textured block model (even if intersecting ground)
                    if (context.submitNodeCollector() != null) {
                        poseStack.pushPose();
                        poseStack.translate(pos.getX() - camX, pos.getY() - camY, pos.getZ() - camZ);

                        // Hologram effect: slight inset so it doesn't z-fight with adjacent blocks
                        poseStack.translate(0.04f, 0.04f, 0.04f);
                        poseStack.scale(0.92f, 0.92f, 0.92f);

                        try {
                            blockModelResolver.update(modelRenderState, planned.state(), displayContext);
                            modelRenderState.submit(poseStack, context.submitNodeCollector(), 15728880, OverlayTexture.NO_OVERLAY, 0);
                        } catch (Exception ignoredCatch) {
                        }

                        poseStack.popPose();
                    }

                    // Contextual outlines:
                    boolean isActiveLayer = isLayerFilterActive() && (pos.getY() == currentLayerY);
                    boolean isWrongBlock = !worldState.isAir();

                    if (isWrongBlock) {
                        // Red wireframe warning only if an incorrect block is occupying this spot
                        Gizmos.cuboid(pos, GizmoStyle.stroke(0xFFFF2222, 2.0f));
                    } else if (isActiveLayer) {
                        // Cyan outline for active building layer
                        Gizmos.cuboid(pos, GizmoStyle.stroke(0xFF00E5FF, 1.5f));
                    }
                }
            }
        }
    }
}
