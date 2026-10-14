package net.schemplanner.schematic;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Tracks the required, current and missing quantity for an item.
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
    public int countInInventory(PlayerInventory inventory) {
        if (inventory == null) return 0;
        int count = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.isOf(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Calculates how many items are still needed.
     */
    public int getMissingCount(PlayerInventory inventory) {
        int inInv = countInInventory(inventory);
        return Math.max(0, requiredCount - inInv);
    }

    public boolean isComplete(PlayerInventory inventory) {
        return countInInventory(inventory) >= requiredCount;
    }
}
