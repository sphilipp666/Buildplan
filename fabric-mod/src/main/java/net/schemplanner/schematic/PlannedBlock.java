package net.schemplanner.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block planned to be placed at a specific world coordinate.
 */
public record PlannedBlock(
    BlockPos worldPos,
    BlockState state,
    int relativeX,
    int relativeY,
    int relativeZ
) {}
