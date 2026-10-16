package net.schemplanner.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.schemplanner.schematic.MaterialRequirement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Scrollable list displaying material requirements, inventory counts, and missing quantities.
 * Updated for Fabric 26.2 with 32-bit ARGB font colors and proper row width.
 */
public class MaterialListWidget extends ObjectSelectionList<MaterialListWidget.MaterialEntry> {
    private final List<MaterialEntry> allEntries = new ArrayList<>();
    private String currentSearch = "";
    private boolean onlyMissing = false;

    public MaterialListWidget(Minecraft client, int width, int height, int y, int itemHeight) {
        super(client, width, height, y, itemHeight);
    }

    @Override
    public int getRowWidth() {
        return Math.min(this.width - 24, 440);
    }

    public void setMaterials(Collection<MaterialRequirement> requirements) {
        allEntries.clear();
        Inventory inventory = minecraft.player != null ? minecraft.player.getInventory() : null;

        for (MaterialRequirement req : requirements) {
            allEntries.add(new MaterialEntry(minecraft, req, inventory));
        }

        applyFilter();
    }

    public void setSearchFilter(String search) {
        this.currentSearch = search.trim().toLowerCase();
        applyFilter();
    }

    public void setOnlyMissing(boolean onlyMissing) {
        this.onlyMissing = onlyMissing;
        applyFilter();
    }

    public boolean isOnlyMissing() {
        return onlyMissing;
    }

    private void applyFilter() {
        clearEntries();

        for (MaterialEntry entry : allEntries) {
            if (onlyMissing && entry.isComplete()) {
                continue;
            }

            if (!currentSearch.isEmpty()) {
                String name = entry.getItemStack().getHoverName().getString().toLowerCase();
                if (!name.contains(currentSearch)) {
                    continue;
                }
            }

            addEntry(entry);
        }
    }

    public static class MaterialEntry extends ObjectSelectionList.Entry<MaterialEntry> {
        private final Minecraft client;
        private final MaterialRequirement requirement;
        private final ItemStack itemStack;
        private final int inInventory;
        private final int missing;
        private final boolean complete;

        public MaterialEntry(Minecraft client, MaterialRequirement requirement, Inventory inventory) {
            this.client = client;
            this.requirement = requirement;
            this.itemStack = new ItemStack(requirement.getItem());
            this.inInventory = requirement.countInInventory(inventory);
            this.missing = requirement.getMissingCount(inventory);
            this.complete = requirement.isComplete(inventory);
        }

        public ItemStack getItemStack() {
            return itemStack;
        }

        public boolean isComplete() {
            return complete;
        }

        @Override
        public Component getNarration() {
            return itemStack.getHoverName();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int x = getContentX();
            int y = getContentY();
            int entryWidth = getContentWidth();
            int entryHeight = getContentHeight();

            // Background hover highlight
            if (hovered) {
                graphics.fill(x - 2, y - 1, x + entryWidth + 2, y + entryHeight - 1, 0x33FFFFFF);
            }

            // Draw Item Icon
            graphics.item(itemStack, x + 4, y + 4);

            // Item Display Name (Must use 0xFFFFFFFF for full opacity in 26.2 ARGB)
            Component itemName = itemStack.getHoverName();
            graphics.text(client.font, itemName, x + 26, y + 3, 0xFFFFFFFF, true);

            // Format Stack info: e.g. "128 (2 Stacks)"
            int req = requirement.getRequiredCount();
            int stacks = req / 64;
            int remainder = req % 64;
            String stackStr = stacks > 0 ? String.format("(%dstk %s)", stacks, remainder > 0 ? "+ " + remainder : "") : "";

            // Soll / Ist / Fehlend Line
            String statsLine = String.format("§7Soll: §f%d %s §8| §7Inventar: §f%d", req, stackStr, inInventory);
            graphics.text(client.font, Component.literal(statsLine), x + 26, y + 14, 0xFFAAAAAA, false);

            // Status Badge on the right
            if (complete) {
                String status = "§aVorhanden";
                int textW = client.font.width(status);
                graphics.text(client.font, Component.literal(status), x + entryWidth - textW - 8, y + 8, 0xFF55FF55, true);
            } else {
                String status = String.format("§cFehlt: %d", missing);
                int textW = client.font.width(status);
                graphics.text(client.font, Component.literal(status), x + entryWidth - textW - 8, y + 8, 0xFFFF5555, true);
            }
        }
    }
}
