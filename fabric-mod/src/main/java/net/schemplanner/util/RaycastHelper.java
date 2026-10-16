package net.schemplanner.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Helper to compute smart placement positions directly in the player's line of sight.
 * Ensures the schematic appears right in front of the player and never behind them.
 */
public class RaycastHelper {

    public record PlacementTarget(BlockPos pos, int rotation) {}

    public static PlacementTarget getPlacementTarget(Minecraft client) {
        if (client.player == null) {
            return new PlacementTarget(BlockPos.ZERO, 0);
        }

        // 1. Raycast up to 40 blocks
        HitResult hit = client.player.pick(40.0d, 0.0f, false);
        BlockPos target;

        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            // Place on top or next to the targeted surface
            target = blockHit.getBlockPos().relative(blockHit.getDirection());
        } else {
            // Looking at air / sky: place 6 blocks directly ahead at player eye height
            Vec3 look = client.player.getLookAngle();
            Vec3 ahead = client.player.getEyePosition().add(look.scale(6.0d));
            target = BlockPos.containing(ahead);
        }

        // 2. Compute rotation matching player facing direction
        Direction facing = client.player.getDirection();
        int rotation = switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0; // NORTH
        };

        return new PlacementTarget(target, rotation);
    }

    public static BlockPos getTargetedBlockPos(Minecraft client) {
        if (client.player == null) {
            return BlockPos.ZERO;
        }
        HitResult hit = client.player.pick(50.0d, 0.0f, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos();
        }
        return client.player.blockPosition();
    }
}
