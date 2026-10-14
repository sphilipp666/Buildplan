package net.schemplanner.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.schemplanner.schematic.MaterialRequirement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Scrollable list displaying material requirements, inventory counts, and missing quantities.
 * Updated for Fabric 26.2 / modern ElementListWidget API.
 */
public class MaterialListWidget extends ElementListWidget<MaterialListWidget.MaterialEntry> {
    private final List<MaterialEntry> allEntries = new ArrayList<>();
    private String currentSearch = "";
    private boolean onlyMissing = false;

    public MaterialListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
        super(client, width, height, y, itemHeight);
        this.centerListVertically = false;
    }

    public void setMaterials(Collection<MaterialRequirement> requirements) {
        allEntries.clear();
        PlayerInventory inventory = client.player != null ? client.player.getInventory() : null;

        for (MaterialRequirement req : requirements) {
            allEntries.add(new MaterialEntry(client, req, inventory));
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
                String name = entry.getItemStack().getName().getString().toLowerCase();
                if (!name.contains(currentSearch)) {
                    continue;
                }
            }

            addEntry(entry);
        }
    }

    public static class MaterialEntry extends ElementListWidget.Entry<MaterialEntry> {
        private final MinecraftClient client;
        private final MaterialRequirement requirement;
        private final ItemStack itemStack;
        private final int inInventory;
        private final int missing;
        private final boolean complete;

        public MaterialEntry(MinecraftClient client, MaterialRequirement requirement, PlayerInventory inventory) {
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
        public List<? extends net.minecraft.client.gui.Element> children() {
            return List.of();
        }

        @Override
        public List<? extends net.minecraft.client.gui.Selectable> selectableChildren() {
            return List.of();
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Background hover highlight
            if (hovered) {
                context.fill(x - 2, y - 1, x + entryWidth + 2, y + entryHeight - 1, 0x22FFFFFF);
            }

            // Draw Item Icon
            context.drawItem(itemStack, x + 4, y + 3);

            // Item Display Name
            Text itemName = itemStack.getName();
            context.drawText(client.textRenderer, itemName, x + 26, y + 2, 0xFFFFFF, true);

            // Format Stack info: e.g. "128 (2 Stacks)"
            int req = requirement.getRequiredCount();
            int stacks = req / 64;
            int remainder = req % 64;
            String stackStr = stacks > 0 ? String.format("(%dstk %s)", stacks, remainder > 0 ? "+ " + remainder : "") : "";

            // Soll / Ist / Fehlend Line
            String statsLine = String.format("§7Soll: §f%d %s §8| §7Inventar: §f%d", req, stackStr, inInventory);
            context.drawText(client.textRenderer, Text.literal(statsLine), x + 26, y + 13, 0xAAAAAA, false);

            // Status Badge on the right
            if (complete) {
                String status = "§a✔ Vorhanden";
                int textW = client.textRenderer.getWidth(status);
                context.drawText(client.textRenderer, Text.literal(status), x + entryWidth - textW - 8, y + 7, 0x55FF55, true);
            } else {
                String status = String.format("§cFehlt noch: %d", missing);
                int textW = client.textRenderer.getWidth(status);
                context.drawText(client.textRenderer, Text.literal(status), x + entryWidth - textW - 8, y + 7, 0xFF5555, true);
            }
        }
    }
}
