package dev.attuned.menu;

import dev.attuned.attunement.Attunement;
import dev.attuned.attunement.FocusHolder;
import dev.attuned.content.AttunedComponents;
import dev.attuned.content.AttunedContent;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Component-backed container view over the satchel currently held in a hand. */
public final class SatchelContainer implements Container {
	private final Player player;
	private final InteractionHand hand;

	public SatchelContainer(Player player, InteractionHand hand) {
		this.player = player;
		this.hand = hand;
	}

	private ItemStack satchel() {
		return player.getItemInHand(hand);
	}

	private boolean hasLiveSatchel() {
		return satchel().getItem() == AttunedContent.SATCHEL_OF_FOCI;
	}

	private FocusHolder holder() {
		if (!hasLiveSatchel()) {
			return AttunedComponents.emptyContents();
		}
		FocusHolder holder = satchel().get(AttunedComponents.SATCHEL_CONTENTS);
		return holder == null ? AttunedComponents.emptyContents() : holder;
	}

	@Override
	public int getContainerSize() {
		return AttunedComponents.SATCHEL_SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (int i = 0; i < AttunedComponents.SATCHEL_SIZE; i++) {
			if (!focusStackAt(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		if (slot < 0 || slot >= AttunedComponents.SATCHEL_SIZE) {
			return ItemStack.EMPTY;
		}
		return focusStackAt(slot);
	}

	private ItemStack focusStackAt(int slot) {
		ItemStack stack = holder().get(slot);
		return stack.isEmpty() || Attunement.definitionFor(player, stack).isEmpty()
			? ItemStack.EMPTY
			: stack;
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		if (!hasLiveSatchel()) {
			return ItemStack.EMPTY;
		}
		ItemStack current = getItem(slot);
		if (current.isEmpty() || amount <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack remaining = current.copy();
		ItemStack taken = remaining.split(amount);
		satchel().set(AttunedComponents.SATCHEL_CONTENTS,
			holder().with(slot, remaining.isEmpty() ? ItemStack.EMPTY : remaining));
		return taken;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		if (!hasLiveSatchel()) {
			return ItemStack.EMPTY;
		}
		ItemStack current = getItem(slot);
		if (current.isEmpty()) {
			return ItemStack.EMPTY;
		}
		satchel().set(AttunedComponents.SATCHEL_CONTENTS, holder().with(slot, ItemStack.EMPTY));
		return current;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (!hasLiveSatchel()) {
			return;
		}
		if (slot < 0 || slot >= AttunedComponents.SATCHEL_SIZE) {
			return;
		}
		if (stack == null || stack.isEmpty()) {
			satchel().set(AttunedComponents.SATCHEL_CONTENTS, holder().with(slot, ItemStack.EMPTY));
			return;
		}
		if (Attunement.definitionFor(player, stack).isEmpty()) {
			return;
		}
		satchel().set(AttunedComponents.SATCHEL_CONTENTS, holder().with(slot, cappedStack(stack)));
	}

	@Override
	public int getMaxStackSize() {
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
		// No-op: mutators write the holder back to the live ItemStack component.
	}

	@Override
	public boolean stillValid(Player who) {
		return who == player && hasLiveSatchel();
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < AttunedComponents.SATCHEL_SIZE; i++) {
			setItem(i, ItemStack.EMPTY);
		}
	}
}
