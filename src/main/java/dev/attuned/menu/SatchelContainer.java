package dev.attuned.menu;

import dev.attuned.attunement.Attunement;
import dev.attuned.attunement.FocusHolder;
import dev.attuned.content.AttunedComponents;
import dev.attuned.content.AttunedContent;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Component-backed container view over the reliquary currently held in a hand.
 * The same view backs both tiers: the contents component type, the accepted
 * reliquary item, and the grid size are parameterized so the held small satchel
 * or Grand Focus Reliquary reads/writes its own {@link FocusHolder}.
 */
public final class SatchelContainer implements Container {
	private final Player player;
	private final InteractionHand hand;
	private final Item reliquaryItem;
	private final int size;
	private final boolean grand;

	/** Small Focus Reliquary view (27 slots). */
	public SatchelContainer(Player player, InteractionHand hand) {
		this(player, hand, AttunedContent.SATCHEL_OF_FOCI,
			AttunedComponents.SATCHEL_SIZE, false);
	}

	public SatchelContainer(Player player, InteractionHand hand, Item reliquaryItem, int size, boolean grand) {
		this.player = player;
		this.hand = hand;
		this.reliquaryItem = reliquaryItem;
		this.size = size;
		this.grand = grand;
	}

	private ItemStack satchel() {
		return player.getItemInHand(hand);
	}

	private boolean hasLiveSatchel() {
		return satchel().getItem() == reliquaryItem;
	}

	private FocusHolder emptyContents() {
		return FocusHolder.empty(size, 1);
	}

	private FocusHolder holder() {
		if (!hasLiveSatchel()) {
			return emptyContents();
		}
		return AttunedComponents.getContents(satchel(), grand);
	}

	@Override
	public int getContainerSize() {
		return size;
	}

	@Override
	public boolean isEmpty() {
		for (int i = 0; i < size; i++) {
			if (!focusStackAt(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		if (slot < 0 || slot >= size) {
			return ItemStack.EMPTY;
		}
		return focusStackAt(slot);
	}

	private ItemStack focusStackAt(int slot) {
		ItemStack stack = holder().get(slot);
		return isReliquary(stack) ? ItemStack.EMPTY : stack;
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
		AttunedComponents.setContents(satchel(), grand,
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
		AttunedComponents.setContents(satchel(), grand, holder().with(slot, ItemStack.EMPTY));
		return current;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (!hasLiveSatchel()) {
			return;
		}
		if (slot < 0 || slot >= size) {
			return;
		}
		if (stack == null || stack.isEmpty()) {
			AttunedComponents.setContents(satchel(), grand, holder().with(slot, ItemStack.EMPTY));
			return;
		}
		if (isReliquary(stack)) {
			return;
		}
		if (Attunement.definitionFor(player, stack).isEmpty()) {
			return;
		}
		AttunedComponents.setContents(satchel(), grand, holder().with(slot, cappedStack(stack)));
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

	private static boolean isReliquary(ItemStack stack) {
		return stack.getItem() == AttunedContent.SATCHEL_OF_FOCI
			|| stack.getItem() == AttunedContent.GRAND_SATCHEL_OF_FOCI;
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
		for (int i = 0; i < size; i++) {
			setItem(i, ItemStack.EMPTY);
		}
	}
}
