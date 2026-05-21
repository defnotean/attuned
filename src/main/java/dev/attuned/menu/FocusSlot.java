package dev.attuned.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A single Focus accessory slot in the player inventory menu. Behaves like a
 * normal slot except it is capped at one item, matching the {@link FocusContainer}
 * it is backed by.
 */
public class FocusSlot extends Slot {
	public FocusSlot(Container container, int index, int x, int y) {
		super(container, index, x, y);
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		// v1: accept any item. Focus-item validation can tighten this later.
		return true;
	}
}
