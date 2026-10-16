package net.schemplanner.schematic;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Tracks the required, current and missing quantity for an item in Minecraft 26.2.
 */
public class MaterialRequirement {
    private final Item item;
    private int requiredCount;

    public MaterialRequirement(Item item, int requiredCount) {
        this.item = item;
        this.requiredCount = requiredCount;
    }

    public void increment(int amount) {
        this.requiredCount += amount;
    }

    public Item getItem() {
        return item;
    }

    public int getRequiredCount() {
        return requiredCount;
    }

    /**
     * Counts how many of this item are currently present in the player's inventory.
     */
    public int countInInventory(Inventory inventory) {
        if (inventory == null) return 0;
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Calculates how many items are still needed.
     */
    public int getMissingCount(Inventory inventory) {
        int inInv = countInInventory(inventory);
        return Math.max(0, requiredCount - inInv);
    }

    public boolean isComplete(Inventory inventory) {
        return countInInventory(inventory) >= requiredCount;
    }
}
