package net.schemplanner.mixin;

import net.minecraft.client.render.WorldRenderer;
import net.schemplanner.render.GhostBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin injecting into WorldRenderer to render semi-transparent ghost blocks.
 * Uses a clean TAIL injection to remain resilient across 26.x rendering pipeline revisions.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(
        method = "render",
        at = @At("TAIL")
    )
    private void schemplanner$onRenderWorld(CallbackInfo ci) {
        GhostBlockRenderer.renderGhostBlocks();
    }
}
