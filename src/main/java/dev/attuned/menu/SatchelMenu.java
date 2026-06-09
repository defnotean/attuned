package dev.attuned.menu;

import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.content.AttunedComponents;
import dev.attuned.content.AttunedContent;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Focus Reliquary. It exposes three Focus stores as real slots so
 * vanilla drag-and-drop moves Foci between them: the held reliquary's grid, the
 * player's six equipped Focus slots (a labelled column beside the window), and
 * the player inventory. The equipped slots write through {@link FocusContainer}
 * to the synced attachment, exactly as the Altar menu does.
 */
public class SatchelMenu extends AbstractContainerMenu {
	public static final int SATCHEL_X = 8;
	public static final int SATCHEL_Y = 18;
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = 84;
	/** Equipped Focus slots sit in a column to the left of the reliquary window. */
	public static final int EQUIPPED_X = -26;
	public static final int EQUIPPED_Y = 18;

	/** Menu index of the first equipped Focus slot (the satchel grid takes 0..n-1). */
	public static final int EQUIPPED_START = AttunedComponents.SATCHEL_SIZE;
	/** One past the last equipped Focus slot index. */
	public static final int EQUIPPED_END = EQUIPPED_START + AttunedInv.SIZE;

	private final Container satchel;
	private final InteractionHand hand;
	private final Player owner;
	private final boolean handUnknown;

	public SatchelMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(AttunedComponents.SATCHEL_SIZE), InteractionHand.MAIN_HAND, true);
	}

	public SatchelMenu(int containerId, Inventory inventory, Container satchel, InteractionHand hand) {
		this(containerId, inventory, satchel, hand, false);
	}

	private SatchelMenu(int containerId, Inventory inventory, Container satchel, InteractionHand hand,
			boolean handUnknown) {
		super(SatchelMenuType.TYPE, containerId);
		checkContainerSize(satchel, AttunedComponents.SATCHEL_SIZE);
		this.satchel = satchel;
		this.hand = hand;
		this.owner = inventory.player;
		this.handUnknown = handUnknown;

		// Reliquary grid (slots 0 .. SATCHEL_SIZE-1).
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				int index = row * 9 + col;
				this.addSlot(new Slot(satchel, index, SATCHEL_X + col * 18, SATCHEL_Y + row * 18) {
					@Override
					public int getMaxStackSize() {
						return 1;
					}

					@Override
					public boolean mayPlace(ItemStack stack) {
						return Attunement.definitionFor(inventory.player, stack).isPresent()
							&& stack.getItem() != AttunedContent.SATCHEL_OF_FOCI;
					}
				});
			}
		}

		// Equipped Focus slots (slots EQUIPPED_START .. EQUIPPED_END-1), backed by the
		// synced attachment so dragging a Focus here equips it. FocusSlot already caps
		// at one item and rejects non-Foci (and so the reliquary item too, which is not a Focus).
		FocusContainer equipped = new FocusContainer(inventory.player);
		for (int i = 0; i < AttunedInv.SIZE; i++) {
			this.addSlot(new FocusSlot(equipped, i, EQUIPPED_X, EQUIPPED_Y + i * 18));
		}

		this.addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
	}

	public InteractionHand hand() {
		return hand;
	}

	@Override
	public boolean stillValid(Player player) {
		return player == owner && hasLiveSatchel(player);
	}

	private boolean hasLiveSatchel(Player player) {
		if (handUnknown) {
			return player.getMainHandItem().getItem() == AttunedContent.SATCHEL_OF_FOCI
				|| player.getOffhandItem().getItem() == AttunedContent.SATCHEL_OF_FOCI;
		}
		return player.getItemInHand(hand).getItem() == AttunedContent.SATCHEL_OF_FOCI;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		int satchelEnd = AttunedComponents.SATCHEL_SIZE;
		int equippedEnd = EQUIPPED_END;
		int totalEnd = this.slots.size();
		if (slotIndex < 0 || slotIndex >= totalEnd) {
			return ItemStack.EMPTY;
		}
		Slot slot = this.slots.get(slotIndex);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();

		boolean shifted;
		if (slotIndex < satchelEnd) {
			// Reliquary grid -> equip first, otherwise drop into the inventory.
			shifted = moveItemStackTo(stack, satchelEnd, equippedEnd, false)
				|| moveItemStackTo(stack, equippedEnd, totalEnd, false);
		} else if (slotIndex < equippedEnd) {
			// Equipped -> store in the reliquary first, otherwise the inventory.
			shifted = moveItemStackTo(stack, 0, satchelEnd, false)
				|| moveItemStackTo(stack, equippedEnd, totalEnd, false);
		} else {
			// Inventory -> equip first, otherwise stash in the reliquary. Non-Foci match
			// neither slot's mayPlace, so they simply stay put.
			shifted = moveItemStackTo(stack, satchelEnd, equippedEnd, false)
				|| moveItemStackTo(stack, 0, satchelEnd, false);
		}
		if (!shifted) {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return moved;
	}
}
