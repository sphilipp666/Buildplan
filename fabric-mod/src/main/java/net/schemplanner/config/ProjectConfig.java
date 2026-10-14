package net.schemplanner.config;

import net.minecraft.util.math.BlockPos;

/**
 * Representation of a project configuration JSON file.
 * Example schema:
 * {
 *   "id": "projekt_name",
 *   "schematic": "projekt_name.schem",
 *   "origin": { "x": 100, "y": 64, "z": -250 },
 *   "rotation": 0,
 *   "unlocked": true
 * }
 */
public class ProjectConfig {
    public String id = "";
    public String schematic = "";
    public Origin origin = new Origin();
    public int rotation = 0; // 0, 90, 180, 270
    public boolean unlocked = true;

    public static class Origin {
        public int x = 0;
        public int y = 64;
        public int z = 0;

        public BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }
    }
}
