package dev.attuned.menu;

import dev.attuned.content.AttunedContent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Container menu for the Altar of Reweaving. Three Focus input slots, one shard
 * fragment catalyst slot, and one output slot are kept in a transient container
 * and returned to the player on close.
 */
public class ReweavingMenu extends AbstractContainerMenu {
	public static final int FOCUS_INPUTS = 3;
	public static final int CATALYST_SLOT = 3;
	public static final int OUTPUT_SLOT = 4;
	public static final int CONTAINER_SIZE = 5;
	public static final int INPUT_START = 0;
	public static final int INVENTORY_X = 27;
	public static final int INVENTORY_Y = 108;

	private static final int SLOT_ITEM_SIZE = 16;
	private static final int PAINTED_INPUT_WELL_SIZE = 24;
	private static final int PAINTED_OUTPUT_WELL_SIZE = 26;
	private static final int SLOT_INSET = (PAINTED_INPUT_WELL_SIZE - SLOT_ITEM_SIZE) / 2;
	private static final int OUTPUT_SLOT_INSET = (PAINTED_OUTPUT_WELL_SIZE - SLOT_ITEM_SIZE) / 2;
	private static final int SLOT_VISUAL_OFFSET_Y = 2;
	private static final int OUTPUT_VISUAL_OFFSET_X = 2;
	private static final int OUTPUT_VISUAL_OFFSET_Y = 1;

	private static final int[] FOCUS_WELL_X = {26, 53, 80};
	private static final int FOCUS_WELL_Y = 56;
	private static final int CATALYST_WELL_X = 107;
	private static final int CATALYST_WELL_Y = 56;
	private static final int OUTPUT_WELL_X = 151;
	private static final int OUTPUT_WELL_Y = 47;

	private static final int[] FOCUS_SLOT_X = {
		FOCUS_WELL_X[0] + SLOT_INSET,
		FOCUS_WELL_X[1] + SLOT_INSET,
		FOCUS_WELL_X[2] + SLOT_INSET
	};
	private static final int FOCUS_SLOT_Y = FOCUS_WELL_Y + SLOT_INSET + SLOT_VISUAL_OFFSET_Y;
	private static final int CATALYST_SLOT_X = CATALYST_WELL_X + SLOT_INSET;
	private static final int CATALYST_SLOT_Y = CATALYST_WELL_Y + SLOT_INSET + SLOT_VISUAL_OFFSET_Y;
	private static final int OUTPUT_SLOT_X = OUTPUT_WELL_X + OUTPUT_SLOT_INSET + OUTPUT_VISUAL_OFFSET_X;
	private static final int OUTPUT_SLOT_Y = OUTPUT_WELL_Y + OUTPUT_SLOT_INSET + OUTPUT_VISUAL_OFFSET_Y;

	private final Container container;
	private final ContainerLevelAccess access;

	public ReweavingMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(CONTAINER_SIZE), ContainerLevelAccess.NULL);
	}

	public ReweavingMenu(int containerId, Inventory inventory, Container container, ContainerLevelAccess access) {
		super(ReweavingMenuType.TYPE, containerId);
		checkContainerSize(container, CONTAINER_SIZE);
		this.container = container;
		this.access = access;

		for (int i = 0; i < FOCUS_INPUTS; i++) {
			final int index = i;
			this.addSlot(new Slot(container, index, FOCUS_SLOT_X[i], FOCUS_SLOT_Y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return isFocus(stack);
				}
			});
		}
		this.addSlot(new Slot(container, CATALYST_SLOT, CATALYST_SLOT_X, CATALYST_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT);
			}
		});
		this.addSlot(new Slot(container, OUTPUT_SLOT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		this.addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
	}

	public Container container() {
		return this.container;
	}

	public ContainerLevelAccess access() {
		return this.access;
	}

	public boolean hasAllInputs() {
		for (int i = 0; i < FOCUS_INPUTS; i++) {
			if (!isFocus(this.container.getItem(i))) {
				return false;
			}
		}
		return this.container.getItem(CATALYST_SLOT).is(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT);
	}

	public ItemStack outputStack() {
		return this.container.getItem(OUTPUT_SLOT);
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, AttunedContent.ALTAR_OF_REWEAVING);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack moved = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		moved = stack.copy();
		if (slotIndex < CONTAINER_SIZE) {
			if (!this.moveItemStackTo(stack, CONTAINER_SIZE, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (isFocus(stack)) {
			if (!this.moveItemStackTo(stack, INPUT_START, FOCUS_INPUTS, false)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.is(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT)) {
			if (!this.moveItemStackTo(stack, CATALYST_SLOT, CATALYST_SLOT + 1, false)) {
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

	@Override
	public void removed(Player player) {
		super.removed(player);
		for (int i = 0; i < CONTAINER_SIZE; i++) {
			ItemStack leftover = this.container.removeItemNoUpdate(i);
			if (leftover.isEmpty()) {
				continue;
			}
			if (player.isAlive() && !player.isRemoved()) {
				player.getInventory().placeItemBackInInventory(leftover);
			} else {
				player.drop(leftover, false);
			}
		}
	}

	private static boolean isFocus(ItemStack stack) {
		return !stack.isEmpty() && AttunedContent.FOCI.contains(stack.getItem());
	}
}
