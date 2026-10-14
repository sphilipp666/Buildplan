package net.schemplanner.render;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.schemplanner.config.ProjectManager;
import net.schemplanner.schematic.PlannedBlock;

import java.util.List;

/**
 * Handles in-game translucent ghost hologram rendering and layer-by-layer filtering for Fabric 26.2.
 */
public class GhostBlockRenderer {
    /**
     * Static layer filter Y level.
     * Blocks with Y > currentLayerY will be hidden.
     * Integer.MAX_VALUE means all layers are rendered.
     */
    public static int currentLayerY = Integer.MAX_VALUE;

    // Maximum distance from camera to render ghost blocks (in blocks)
    private static final double MAX_RENDER_DISTANCE_SQ = 96.0 * 96.0;

    public static boolean isLayerFilterActive() {
        return currentLayerY != Integer.MAX_VALUE;
    }

    public static void toggleLayerFilter() {
        if (isLayerFilterActive()) {
            currentLayerY = Integer.MAX_VALUE;
        } else {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                currentLayerY = client.player.getBlockY();
            } else {
                currentLayerY = 64;
            }
        }
    }

    public static void layerUp() {
        if (!isLayerFilterActive()) {
            MinecraftClient client = MinecraftClient.getInstance();
            currentLayerY = (client.player != null) ? client.player.getBlockY() : 64;
        } else {
            currentLayerY++;
        }
    }

    public static void layerDown() {
        if (!isLayerFilterActive()) {
            MinecraftClient client = MinecraftClient.getInstance();
            currentLayerY = (client.player != null) ? client.player.getBlockY() : 64;
        } else {
            currentLayerY--;
        }
    }

    /**
     * Parameterless render entry point called from WorldRendererMixin.
     */
    public static void renderGhostBlocks() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        Camera camera = client.gameRenderer.getCamera();
        if (camera == null) return;

        MatrixStack matrices = new MatrixStack();
        renderGhostBlocks(matrices, camera);
    }

    public static void renderGhostBlocks(MatrixStack matrices, Camera camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        List<PlannedBlock> blocks = ProjectManager.getAllPlannedBlocks();
        if (blocks.isEmpty()) return;

        Vec3d camPos = camera.getPos();
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer translucentBuffer = immediate.getBuffer(RenderLayer.getTranslucent());
        TranslucentVertexConsumer ghostConsumer = new TranslucentVertexConsumer(translucentBuffer, 140);

        // Custom VertexConsumerProvider that routes block rendering through our semi-transparent consumer
        VertexConsumerProvider ghostProvider = layer -> ghostConsumer;

        matrices.push();
        // Translate world matrix relative to camera position
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        for (PlannedBlock planned : blocks) {
            BlockPos pos = planned.worldPos();

            // 1. Layer-by-Layer Culling
            if (isLayerFilterActive() && pos.getY() > currentLayerY) {
                continue;
            }

            // 2. Frustum / Distance Culling
            double distSq = pos.getSquaredDistance(camPos.x, camPos.y, camPos.z);
            if (distSq > MAX_RENDER_DISTANCE_SQ) {
                continue;
            }

            BlockState worldState = client.world.getBlockState(pos);

            // 3. Comparison with real world:
            // Case A: Block is already placed correctly -> Skip
            if (worldState.equals(planned.state())) {
                continue;
            }

            // Case B: Wrong block is placed here -> Red outline warning
            if (!worldState.isAir()) {
                VertexConsumer lineConsumer = immediate.getBuffer(RenderLayer.getLines());
                Box box = new Box(pos);
                WorldRenderer.drawBox(matrices, lineConsumer, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 1.0f, 0.2f, 0.2f, 0.9f);
                continue;
            }

            // Case C: Air block -> Render Ghost Block
            matrices.push();
            matrices.translate(pos.getX(), pos.getY(), pos.getZ());

            // Render translucent block model
            client.getBlockRenderManager().renderBlockAsEntity(
                    planned.state(),
                    matrices,
                    ghostProvider,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    OverlayTexture.DEFAULT_UV
            );

            matrices.pop();

            // If this block is on the active layer, draw subtle blueprint highlight outline
            if (isLayerFilterActive() && pos.getY() == currentLayerY) {
                VertexConsumer lineConsumer = immediate.getBuffer(RenderLayer.getLines());
                Box box = new Box(pos);
                WorldRenderer.drawBox(matrices, lineConsumer, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 0.2f, 0.8f, 1.0f, 0.6f);
            }
        }

        matrices.pop();

        // Flush immediate buffers
        immediate.draw(RenderLayer.getTranslucent());
        immediate.draw(RenderLayer.getLines());
    }
}
