package dev.attuned.menu;

import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A thin {@link Container} adapter that exposes a player's six Focus slots to the
 * vanilla menu/slot machinery. It owns no state of its own: every read and write
 * delegates straight to {@link AttunedAttachments}, so the data attachment remains
 * the single source of truth (and keeps handling persistence + client sync).
 */
public final class FocusContainer implements Container {
	private final Player player;

	public FocusContainer(Player player) {
		this.player = player;
	}

	/** The player whose Focus slots this container exposes. */
	public Player player() {
		return player;
	}

	private AttunedInv inv() {
		return AttunedAttachments.getInventory(player);
	}

	@Override
	public int getContainerSize() {
		return AttunedInv.SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (int i = 0; i < AttunedInv.SIZE; i++) {
			if (!inv().get(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		if (slot < 0 || slot >= AttunedInv.SIZE) {
			return ItemStack.EMPTY;
		}
		return inv().get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack current = getItem(slot);
		if (current.isEmpty() || amount <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack removed = current.copy();
		ItemStack taken = removed.split(amount);
		AttunedAttachments.setSlot(player, slot, removed.isEmpty() ? ItemStack.EMPTY : removed);
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack current = getItem(slot);
		if (current.isEmpty()) {
			return ItemStack.EMPTY;
		}
		AttunedAttachments.setSlot(player, slot, ItemStack.EMPTY);
		return current;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot < 0 || slot >= AttunedInv.SIZE) {
			return;
		}
		if (stack == null || stack.isEmpty()) {
			AttunedAttachments.setSlot(player, slot, ItemStack.EMPTY);
			return;
		}
		if (Attunement.definitionFor(player, stack).isEmpty()) {
			return;
		}
		AttunedAttachments.setSlot(player, slot, cappedStack(stack));
	}

	@Override
	public int getMaxStackSize() {
		// Focus slots only ever hold a single accessory.
		return 1;
	}

	private ItemStack cappedStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack copy = stack.copy();
		copy.setCount(Math.min(copy.getCount(), getMaxStackSize()));
		return copy;
	}

	@Override
	public void setChanged() {
		// No-op: writes already go straight to the attachment, which dirties itself.
	}

	@Override
	public boolean stillValid(Player who) {
		return true;
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < AttunedInv.SIZE; i++) {
			AttunedAttachments.setSlot(player, i, ItemStack.EMPTY);
		}
	}
}
