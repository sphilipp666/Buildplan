package net.schemplanner.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

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
