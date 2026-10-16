package net.schemplanner.config;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Representation of a project configuration JSON file for Fabric 26.2.
 * Supports team ownership, player assignments, and construction status.
 */
public class ProjectConfig {
    public String id = "";
    public String title = "";
    public String schematic = "";
    public String owner = "";
    public List<String> assignedPlayers = new ArrayList<>();
    public String status = "PLANNED"; // PLANNED, IN_PROGRESS, COMPLETED
    public Origin origin = new Origin();
    public int rotation = 0; // 0, 90, 180, 270
    public boolean unlocked = true;

    public ProjectConfig() {}

    public ProjectConfig(String id, String schematic, BlockPos originPos, int rotation, boolean unlocked) {
        this.id = id;
        this.title = id;
        this.schematic = schematic;
        this.origin = new Origin(originPos.getX(), originPos.getY(), originPos.getZ());
        this.rotation = rotation;
        this.unlocked = unlocked;
    }

    public ProjectConfig(String id, String title, String schematic, String owner, List<String> assignedPlayers, String status, BlockPos originPos, int rotation, boolean unlocked) {
        this.id = id;
        this.title = (title != null && !title.isBlank()) ? title : id;
        this.schematic = schematic;
        this.owner = (owner != null) ? owner : "";
        this.assignedPlayers = (assignedPlayers != null) ? new ArrayList<>(assignedPlayers) : new ArrayList<>();
        this.status = (status != null && !status.isBlank()) ? status : "PLANNED";
        this.origin = new Origin(originPos.getX(), originPos.getY(), originPos.getZ());
        this.rotation = rotation;
        this.unlocked = unlocked;
    }

    public String getDisplayTitle() {
        return (title != null && !title.isBlank()) ? title : id;
    }

    public String getStatusBadge() {
        if (status == null) return "§e[Plan]";
        return switch (status.toUpperCase()) {
            case "IN_PROGRESS", "BAU" -> "§6[Bau]";
            case "COMPLETED", "FERTIG" -> "§a[Fertig]";
            default -> "§e[Plan]";
        };
    }

    public boolean isAssignedTo(String playerName) {
        if (playerName == null || playerName.isBlank()) return true;
        // If no players are explicitly assigned, it is a public/unassigned build visible to everyone
        if (assignedPlayers == null || assignedPlayers.isEmpty()) return true;
        if (owner != null && owner.equalsIgnoreCase(playerName)) return true;
        for (String p : assignedPlayers) {
            if (p != null && p.equalsIgnoreCase(playerName)) return true;
        }
        return false;
    }

    public boolean assignPlayer(String name) {
        if (name == null || name.isBlank()) return false;
        String clean = name.trim();
        if (assignedPlayers == null) assignedPlayers = new ArrayList<>();
        for (String p : assignedPlayers) {
            if (p.equalsIgnoreCase(clean)) return false; // already assigned
        }
        assignedPlayers.add(clean);
        return true;
    }

    public boolean unassignPlayer(String name) {
        if (name == null || assignedPlayers == null) return false;
        String clean = name.trim();
        return assignedPlayers.removeIf(p -> p.equalsIgnoreCase(clean));
    }

    public static class Origin {
        public int x = 0;
        public int y = 64;
        public int z = 0;

        public Origin() {}

        public Origin(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }
    }
}
