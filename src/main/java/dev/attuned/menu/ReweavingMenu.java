package dev.attuned.menu;

import dev.attuned.AttunedConfig;
import dev.attuned.attunement.Attunement;
import dev.attuned.content.AttunedComponents;
import dev.attuned.content.AttunedContent;
import dev.attuned.content.TemperingResolver;
import net.minecraft.core.registries.BuiltInRegistries;
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
	private static final int SLOT_VISUAL_OFFSET_Y = 0;
	private static final int OUTPUT_VISUAL_OFFSET_X = 0;
	private static final int OUTPUT_VISUAL_OFFSET_Y = 0;

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
	private final Player player;
	private int affinityLoomRerolls = 0;

	public ReweavingMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(CONTAINER_SIZE), ContainerLevelAccess.NULL);
	}

	public ReweavingMenu(int containerId, Inventory inventory, Container container, ContainerLevelAccess access) {
		super(ReweavingMenuType.TYPE, containerId);
		checkContainerSize(container, CONTAINER_SIZE);
		this.container = container;
		this.access = access;
		this.player = inventory.player;

		for (int i = 0; i < FOCUS_INPUTS; i++) {
			final int index = i;
			this.addSlot(new Slot(container, index, FOCUS_SLOT_X[i], FOCUS_SLOT_Y) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return ReweavingMenu.this.isFocusInput(stack);
				}
			});
		}
		this.addSlot(new Slot(container, CATALYST_SLOT, CATALYST_SLOT_X, CATALYST_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				if (stack.is(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT)) {
					return true;
				}
				return stack.is(AttunedContent.ATTUNEMENT_SHARD) && ReweavingMenu.this.affinityLoomCatalystAllowed();
			}
		});
		this.addSlot(new Slot(container, OUTPUT_SLOT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		addPlayerInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
	}

	private void addPlayerInventorySlots(Inventory inventory, int x, int y) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 9; column++) {
				addSlot(new Slot(inventory, column + row * 9 + 9, x + column * 18, y + row * 18));
			}
		}
		for (int column = 0; column < 9; column++) {
			addSlot(new Slot(inventory, column, x + column * 18, y + 58));
		}
	}

	public Container container() {
		return this.container;
	}

	public ContainerLevelAccess access() {
		return this.access;
	}

	public boolean hasAllInputs() {
		if (!hasAllFocusInputs()) {
			return false;
		}
		return this.container.getItem(CATALYST_SLOT).is(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT);
	}

	public boolean hasAllFocusInputs() {
		for (int i = 0; i < FOCUS_INPUTS; i++) {
			if (!this.isFocusInput(this.container.getItem(i))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether the inputs describe a valid tempering: exactly the first two Focus
	 * slots hold the same untempered Focus, the third Focus slot is empty, and a
	 * Shard Fragment catalyst is present. Gated through {@link TemperingResolver}.
	 */
	public boolean canTemper() {
		if (!this.container.getItem(CATALYST_SLOT).is(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT)) {
			return false;
		}
		ItemStack first = this.container.getItem(0);
		ItemStack second = this.container.getItem(1);
		if (!this.isFocusInput(first) || !this.isFocusInput(second)) {
			return false;
		}
		if (!this.container.getItem(2).isEmpty()) {
			return false;
		}
		return TemperingResolver.canTemper(
			focusId(first), focusId(second), isTempered(first), isTempered(second));
	}

	/** Escalating shard cost for Affinity Loom rerolls within one menu session. */
	public int affinityLoomShardCost() {
		AttunedConfig config = AttunedConfig.get();
		return Math.min(
			config.affinityLoomMaxShardCost(),
			config.affinityLoomBaseShardCost() + this.affinityLoomRerolls);
	}

	/**
	 * Whether the inputs describe a valid Affinity Loom roll: one Focus in slot 0,
	 * empty slots 1-2, enough Attunement Shards in the catalyst slot, and not a
	 * tempering layout.
	 */
	public boolean canAffinityLoom() {
		if (this.canTemper()) {
			return false;
		}
		if (!this.isFocusInput(this.container.getItem(0))) {
			return false;
		}
		if (!this.container.getItem(1).isEmpty() || !this.container.getItem(2).isEmpty()) {
			return false;
		}
		ItemStack catalyst = this.container.getItem(CATALYST_SLOT);
		int cost = affinityLoomShardCost();
		return catalyst.is(AttunedContent.ATTUNEMENT_SHARD) && catalyst.getCount() >= cost;
	}

	public boolean affinityLoomLayout() {
		if (this.canTemper()) {
			return false;
		}
		if (!this.isFocusInput(this.container.getItem(0))) {
			return false;
		}
		return this.container.getItem(1).isEmpty() && this.container.getItem(2).isEmpty();
	}

	public void incrementAffinityLoomRerolls() {
		this.affinityLoomRerolls++;
	}

	private boolean affinityLoomCatalystAllowed() {
		return this.isFocusInput(this.container.getItem(0))
			&& this.container.getItem(1).isEmpty()
			&& this.container.getItem(2).isEmpty();
	}

	private static String focusId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static boolean isTempered(ItemStack stack) {
		return stack.has(AttunedComponents.TEMPERED);
	}

	public ItemStack outputStack() {
		return this.container.getItem(OUTPUT_SLOT);
	}

	@Override
	public boolean stillValid(Player player) {
		return stillValid(this.access, player, AttunedContent.ALTAR_OF_REWEAVING.get());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		if (slotIndex < 0 || slotIndex >= this.slots.size()) {
			return ItemStack.EMPTY;
		}
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
		} else if (this.isFocusInput(stack)) {
			if (!this.moveItemStackTo(stack, INPUT_START, FOCUS_INPUTS, false)) {
				return ItemStack.EMPTY;
			}
		} else if (stack.is(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT)
				|| stack.is(AttunedContent.ATTUNEMENT_SHARD)) {
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

	private boolean isFocusInput(ItemStack stack) {
		return Attunement.definitionFor(this.player, stack).isPresent();
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
}
