package net.schemplanner.selection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.schemplanner.config.ProjectManager;
import net.schemplanner.schematic.SchematicWriter;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Manages 3D region selection (Pos1 and Pos2) in the world and handles exporting to .schem.
 */
public class AreaSelectionManager {
    private static BlockPos pos1 = null;
    private static BlockPos pos2 = null;
    private static boolean active = false;

    public static synchronized void setPos1(BlockPos pos) {
        pos1 = (pos != null) ? pos.immutable() : null;
    }

    public static synchronized void setPos2(BlockPos pos) {
        pos2 = (pos != null) ? pos.immutable() : null;
    }

    public static synchronized BlockPos getPos1() {
        return pos1;
    }

    public static synchronized BlockPos getPos2() {
        return pos2;
    }

    public static synchronized boolean hasPos1() {
        return pos1 != null;
    }

    public static synchronized boolean hasPos2() {
        return pos2 != null;
    }

    public static synchronized boolean isComplete() {
        return pos1 != null && pos2 != null;
    }

    public static synchronized void clear() {
        pos1 = null;
        pos2 = null;
    }

    public static synchronized boolean isActive() {
        return active;
    }

    public static synchronized void setActive(boolean a) {
        active = a;
    }

    public static synchronized BlockPos getMinPos() {
        if (pos1 == null || pos2 == null) return null;
        return new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ())
        );
    }

    public static synchronized BlockPos getMaxPos() {
        if (pos1 == null || pos2 == null) return null;
        return new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
        );
    }

    public static synchronized int getWidth() {
        if (!isComplete()) return 0;
        return Math.abs(pos1.getX() - pos2.getX()) + 1;
    }

    public static synchronized int getHeight() {
        if (!isComplete()) return 0;
        return Math.abs(pos1.getY() - pos2.getY()) + 1;
    }

    public static synchronized int getLength() {
        if (!isComplete()) return 0;
        return Math.abs(pos1.getZ() - pos2.getZ()) + 1;
    }

    public static synchronized int getVolume() {
        if (!isComplete()) return 0;
        return getWidth() * getHeight() * getLength();
    }

    public static synchronized String getDimensionsString() {
        if (!isComplete()) return "Keine Auswahl";
        return String.format("%dx%dx%d (%d Blöcke)", getWidth(), getHeight(), getLength(), getVolume());
    }

    public static int countNonAirBlocks(Level level) {
        if (!isComplete() || level == null) return 0;
        BlockPos min = getMinPos();
        BlockPos max = getMaxPos();
        int count = 0;
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    if (!state.isAir()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public static synchronized SchematicWriter.SaveResult saveToSchematic(Level level, String rawName, boolean includeAir) throws IOException {
        if (!isComplete()) {
            throw new IllegalStateException("Pos 1 und Pos 2 müssen gesetzt sein, bevor gespeichert werden kann!");
        }

        String name = rawName.trim();
        if (name.isEmpty()) {
            name = "bauplan_auswahl";
        }
        name = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (!name.toLowerCase().endsWith(".schem")) {
            name += ".schem";
        }

        Path target = ProjectManager.getSchematicsDir().resolve(name);
        SchematicWriter.SaveResult result = SchematicWriter.saveFromWorld(level, pos1, pos2, target, includeAir);

        // Instantly reload projects so the new schematic is recognized
        ProjectManager.reloadProjects();

        return result;
    }
}
