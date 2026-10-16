package net.schemplanner.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.schemplanner.config.ProjectManager;
import net.schemplanner.placement.PlacementSession;
import net.schemplanner.schematic.SpongeSchematic;
import net.schemplanner.util.RaycastHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * List widget displaying both active placed projects and available .schem / .litematic templates.
 * Supports multiple concurrent placements of the same schematic and deleting instances.
 */
public class SchematicListWidget extends ObjectSelectionList<SchematicListWidget.SchematicEntry> {
    private final MaterialListScreen parentScreen;
    private final List<SchematicEntry> allEntries = new ArrayList<>();

    public SchematicListWidget(MaterialListScreen parent, Minecraft client, int width, int height, int y, int itemHeight) {
        super(client, width, height, y, itemHeight);
        this.parentScreen = parent;
        refreshList();
    }

    @Override
    public int getRowWidth() {
        return Math.min(this.width - 24, 450);
    }

    public void refreshList() {
        clearEntries();
        allEntries.clear();

        // 1. Placed / Active Projects in World
        List<ProjectManager.LoadedProject> allProjects = ProjectManager.getLoadedProjects();
        boolean filterAssigned = ProjectManager.getVisibilityFilter() == ProjectManager.VisibilityFilter.ASSIGNED_ONLY;
        String playerName = (minecraft.player != null) ? minecraft.player.getName().getString() : "";

        List<ProjectManager.LoadedProject> visibleProjects = allProjects.stream()
                .filter(p -> !filterAssigned || p.config().isAssignedTo(playerName))
                .toList();

        if (!visibleProjects.isEmpty()) {
            String filterLabel = filterAssigned ? " §6[🎯 Meine Bauten]" : " §b[🌐 Alle Bauten]";
            addEntry(new HeaderEntry(minecraft, "§e--- Aktive Baupläne (" + visibleProjects.size() + "/" + allProjects.size() + ")" + filterLabel + " ---"));
            for (int i = 0; i < allProjects.size(); i++) {
                ProjectManager.LoadedProject proj = allProjects.get(i);
                if (!filterAssigned || proj.config().isAssignedTo(playerName)) {
                    ProjectEntry entry = new ProjectEntry(minecraft, parentScreen, proj, i);
                    allEntries.add(entry);
                    addEntry(entry);
                }
            }
        } else if (filterAssigned && !allProjects.isEmpty()) {
            addEntry(new HeaderEntry(minecraft, "§7--- Keine dir zugewiesenen Baupläne [🎯 Nur meine Bauten] ---"));
        }

        // 2. Available Schematics on disk (Always visible to allow multiple placements!)
        List<ProjectManager.AvailableSchematic> available = ProjectManager.getAvailableSchematics();
        if (!available.isEmpty()) {
            addEntry(new HeaderEntry(minecraft, "§b--- Vorlagen / Schematics-Ordner (" + available.size() + ") ---"));
            for (ProjectManager.AvailableSchematic schem : available) {
                FileEntry entry = new FileEntry(minecraft, parentScreen, schem);
                allEntries.add(entry);
                addEntry(entry);
            }
        }
    }

    public abstract static class SchematicEntry extends ObjectSelectionList.Entry<SchematicEntry> {}

    public static class HeaderEntry extends SchematicEntry {
        private final Minecraft client;
        private final String title;

        public HeaderEntry(Minecraft client, String title) {
            this.client = client;
            this.title = title;
        }

        @Override
        public Component getNarration() {
            return Component.literal(title);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = getContentX();
            int y = getContentY();
            int w = getContentWidth();
            graphics.centeredText(client.font, Component.literal(title), x + w / 2, y + 12, 0xFFE0E0E0);
        }
    }

    public static class ProjectEntry extends SchematicEntry {
        private final Minecraft client;
        private final MaterialListScreen parent;
        private final ProjectManager.LoadedProject project;
        private final int projectIndex;

        public ProjectEntry(Minecraft client, MaterialListScreen parent, ProjectManager.LoadedProject project, int projectIndex) {
            this.client = client;
            this.parent = parent;
            this.project = project;
            this.projectIndex = projectIndex;
        }

        public ProjectManager.LoadedProject getProject() {
            return project;
        }

        @Override
        public Component getNarration() {
            return Component.literal(project.config().id);
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            int x = getContentX();
            int y = getContentY();
            int w = getContentWidth();
            double mx = event.x();
            double my = event.y();

            String playerName = (client.player != null) ? client.player.getName().getString() : "";

            // Button 1: Team Toggle [+Ich / -Ich] -> [x + w - 176 to x + w - 134, y + 8 to y + 28]
            if (mx >= x + w - 176 && mx <= x + w - 134 && my >= y + 8 && my <= y + 28) {
                if (!playerName.isBlank()) {
                    if (project.config().assignedPlayers != null && project.config().assignedPlayers.stream().anyMatch(p -> p.equalsIgnoreCase(playerName))) {
                        project.config().unassignPlayer(playerName);
                    } else {
                        project.config().assignPlayer(playerName);
                    }
                    ProjectManager.saveProjectConfig(project.config());
                    parent.refreshAll();
                }
                return true;
            }

            // Button 2: Toggle Visibility (An / Aus) -> [x + w - 130 to x + w - 96, y + 8 to y + 28]
            if (mx >= x + w - 130 && mx <= x + w - 96 && my >= y + 8 && my <= y + 28) {
                ProjectManager.toggleProjectVisibility(project.config().id);
                parent.refreshAll();
                return true;
            }

            // Button 3: Adjust Placement (Justieren) -> [x + w - 92 to x + w - 38, y + 8 to y + 28]
            if (mx >= x + w - 92 && mx <= x + w - 38 && my >= y + 8 && my <= y + 28) {
                ProjectManager.setActiveProjectIndex(projectIndex);
                parent.openPlacementMode(project);
                return true;
            }

            // Button 4: Delete Project Instance (✕) -> [x + w - 34 to x + w - 6, y + 8 to y + 28]
            if (mx >= x + w - 34 && mx <= x + w - 6 && my >= y + 8 && my <= y + 28) {
                ProjectManager.deleteProject(project.config().id);
                parent.refreshAll();
                return true;
            }

            // Click on Status Badge to cycle status [PLANNED -> IN_PROGRESS -> COMPLETED] -> [x + 6 to x + 75, y + 2 to y + 14]
            if (mx >= x + 6 && mx <= x + 75 && my >= y + 2 && my <= y + 14) {
                cycleProjectStatus();
                parent.refreshAll();
                return true;
            }

            // Click row to select
            ProjectManager.setActiveProjectIndex(projectIndex);
            return super.mouseClicked(event, doubleClick);
        }

        private void cycleProjectStatus() {
            String current = (project.config().status != null) ? project.config().status.toUpperCase() : "PLANNED";
            String next = switch (current) {
                case "PLANNED" -> "IN_PROGRESS";
                case "IN_PROGRESS" -> "COMPLETED";
                default -> "PLANNED";
            };
            project.config().status = next;
            ProjectManager.saveProjectConfig(project.config());
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = getContentX();
            int y = getContentY();
            int w = getContentWidth();
            int h = getContentHeight();

            boolean isActive = (ProjectManager.getActiveProject() != null &&
                    ProjectManager.getActiveProject().config().id.equals(project.config().id));

            if (isActive) {
                graphics.fill(x - 2, y - 1, x + w + 2, y + h - 1, 0x4400AAFF);
            } else if (hovered) {
                graphics.fill(x - 2, y - 1, x + w + 2, y + h - 1, 0x22FFFFFF);
            }

            String playerName = (client.player != null) ? client.player.getName().getString() : "";
            boolean isAssigned = (project.config().assignedPlayers != null &&
                    project.config().assignedPlayers.stream().anyMatch(p -> p.equalsIgnoreCase(playerName)));

            // 1. Line 1: Status Badge + Unlocked/Active Badge + Title
            String statusBadge = project.config().getStatusBadge();
            String visBadge = project.config().unlocked ? "§a[Sichtbar]" : "§7[Aus]";
            String title = project.config().getDisplayTitle();
            graphics.text(client.font, Component.literal(statusBadge + " " + visBadge + " §f" + title), x + 6, y + 4, 0xFFFFFFFF, true);

            // 2. Line 2: Team / Assigned Players & Owner
            String teamStr;
            if (project.config().assignedPlayers == null || project.config().assignedPlayers.isEmpty()) {
                teamStr = "§bAlle (öffentlich)";
            } else {
                teamStr = "§b" + String.join(", ", project.config().assignedPlayers);
            }
            String ownerStr = (project.config().owner != null && !project.config().owner.isBlank())
                    ? " §8| §7Owner: §e" + project.config().owner
                    : "";
            graphics.text(client.font, Component.literal("§7Team: " + teamStr + ownerStr), x + 6, y + 15, 0xFFAAAAAA, false);

            // 3. Line 3: Pos, Rot, Block count
            BlockPos o = project.config().origin.toBlockPos();
            SpongeSchematic s = project.schematic();
            int blockCount = s != null ? s.getPlannedBlocks().size() : 0;
            String info = String.format("§7Pos: §b%d, %d, %d §8| §7Rot: §e%d° §8| §7Blöcke: §a%d",
                    o.getX(), o.getY(), o.getZ(), project.config().rotation, blockCount);
            graphics.text(client.font, Component.literal(info), x + 6, y + 25, 0xFF888888, false);

            // Button 1: Team Toggle [+Ich / -Ich] -> [x + w - 176 to x + w - 134, y + 8 to y + 28]
            boolean teamHover = (mouseX >= x + w - 176 && mouseX <= x + w - 134 && mouseY >= y + 8 && mouseY <= y + 28);
            graphics.fill(x + w - 176, y + 8, x + w - 134, y + 28, teamHover ? 0x883388EE : 0x55224488);
            graphics.outline(x + w - 176, y + 8, 42, 20, 0xFF3388EE);
            String teamBtnText = isAssigned ? "§e-Ich" : "§a+Ich";
            graphics.centeredText(client.font, Component.literal(teamBtnText), x + w - 155, y + 13, 0xFFFFFFFF);

            // Button 2: Toggle Visibility (An / Aus) -> [x + w - 130 to x + w - 96, y + 8 to y + 28]
            boolean visHover = (mouseX >= x + w - 130 && mouseX <= x + w - 96 && mouseY >= y + 8 && mouseY <= y + 28);
            graphics.fill(x + w - 130, y + 8, x + w - 96, y + 28, visHover ? 0x884488FF : 0x55333333);
            graphics.outline(x + w - 130, y + 8, 34, 20, 0xFF888888);
            String visText = project.config().unlocked ? "§cAus" : "§aAn";
            graphics.centeredText(client.font, Component.literal(visText), x + w - 113, y + 13, 0xFFFFFFFF);

            // Button 3: Adjust Placement (Justieren) -> [x + w - 92 to x + w - 38, y + 8 to y + 28]
            boolean adjHover = (mouseX >= x + w - 92 && mouseX <= x + w - 38 && mouseY >= y + 8 && mouseY <= y + 28);
            graphics.fill(x + w - 92, y + 8, x + w - 38, y + 28, adjHover ? 0x8800CC88 : 0x55225533);
            graphics.outline(x + w - 92, y + 8, 54, 20, 0xFF00AA66);
            graphics.centeredText(client.font, Component.literal("§fJustieren"), x + w - 65, y + 13, 0xFFFFFFFF);

            // Button 4: Delete Instance (✕) -> [x + w - 34 to x + w - 6, y + 8 to y + 28]
            boolean delHover = (mouseX >= x + w - 34 && mouseX <= x + w - 6 && mouseY >= y + 8 && mouseY <= y + 28);
            graphics.fill(x + w - 34, y + 8, x + w - 6, y + 28, delHover ? 0x88CC2222 : 0x55552222);
            graphics.outline(x + w - 34, y + 8, 28, 20, 0xFFAA3333);
            graphics.centeredText(client.font, Component.literal("§c✕"), x + w - 20, y + 13, 0xFFFFFFFF);
        }
    }

    public static class FileEntry extends SchematicEntry {
        private final Minecraft client;
        private final MaterialListScreen parent;
        private final ProjectManager.AvailableSchematic file;

        public FileEntry(Minecraft client, MaterialListScreen parent, ProjectManager.AvailableSchematic file) {
            this.client = client;
            this.parent = parent;
            this.file = file;
        }

        @Override
        public Component getNarration() {
            return Component.literal(file.name());
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            int x = getContentX();
            int y = getContentY();
            int w = getContentWidth();
            double mx = event.x();
            double my = event.y();

            // Button: [+ Platzieren] -> [x + w - 105 to x + w - 6, y + 8 to y + 28]
            if (mx >= x + w - 105 && mx <= x + w - 6 && my >= y + 8 && my <= y + 28) {
                // Raycast in front of player so it NEVER spawns behind!
                RaycastHelper.PlacementTarget target = RaycastHelper.getPlacementTarget(client);
                ProjectManager.LoadedProject proj = ProjectManager.createAndPlaceProject(file.fullPath(), target.pos(), target.rotation());
                if (proj != null) {
                    parent.openPlacementMode(proj);
                }
                return true;
            }

            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = getContentX();
            int y = getContentY();
            int w = getContentWidth();
            int h = getContentHeight();

            if (hovered) {
                graphics.fill(x - 2, y - 1, x + w + 2, y + h - 1, 0x22FFFFFF);
            }

            // File Name + Format Badge
            String badge = "§e[" + file.format() + "]";
            graphics.text(client.font, Component.literal(badge + " §f" + file.name()), x + 6, y + 6, 0xFFFFFFFF, true);

            // Sub-path info
            graphics.text(client.font, Component.literal("§8" + file.relativePath()), x + 6, y + 18, 0xFF888888, false);

            // Button: [+ Platzieren]
            boolean btnHover = (mouseX >= x + w - 105 && mouseX <= x + w - 6 && mouseY >= y + 8 && mouseY <= y + 28);
            graphics.fill(x + w - 105, y + 8, x + w - 6, y + 28, btnHover ? 0x8800AAFF : 0x550055AA);
            graphics.outline(x + w - 105, y + 8, 99, 20, 0xFF3388EE);
            graphics.centeredText(client.font, Component.literal("§f+ Platzieren"), x + w - 55, y + 13, 0xFFFFFFFF);
        }
    }
}
