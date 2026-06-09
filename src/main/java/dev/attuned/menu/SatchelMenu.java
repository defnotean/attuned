package dev.attuned.menu;

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

/** Menu for moving Foci between the player's inventory and the held satchel. */
public class SatchelMenu extends AbstractContainerMenu {
	public static final int SATCHEL_X = 8;
	public static final int SATCHEL_Y = 18;
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = 84;

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
		if (slotIndex < 0 || slotIndex >= this.slots.size()) {
			return ItemStack.EMPTY;
		}
		Slot slot = this.slots.get(slotIndex);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();
		if (slotIndex < AttunedComponents.SATCHEL_SIZE) {
			if (!this.moveItemStackTo(stack, AttunedComponents.SATCHEL_SIZE, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (Attunement.definitionFor(player, stack).isPresent()
				&& stack.getItem() != AttunedContent.SATCHEL_OF_FOCI) {
			if (!this.moveItemStackTo(stack, 0, AttunedComponents.SATCHEL_SIZE, false)) {
				return ItemStack.EMPTY;
			}
		} else {
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
