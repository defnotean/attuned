package dev.attuned.menu;

import dev.attuned.AttunedConfig;
import dev.attuned.content.AttunedContent;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Attunement Altar's container menu. Exposes a single input slot that only
 * accepts Attunement Shards alongside the player's normal inventory, so the
 * shard can be shift-clicked in and out like any other container. The Bind
 * action itself is driven by a separate client-to-server payload — the menu's
 * job is to hold the shard until the player commits it.
 */
public class AltarMenu extends AbstractContainerMenu {

	/** Number of input slots — a single shard slot, kept as a constant for clarity. */
	public static final int INPUT_SIZE = 1;
	/** Index of the shard input slot within {@link #slots}. */
	public static final int INPUT_SLOT = 0;
	/** GUI X-coordinate for the shard input slot. */
	public static final int INPUT_SLOT_X = 128;
	/** GUI Y-coordinate for the shard input slot. */
	public static final int INPUT_SLOT_Y = 34;
	/** GUI X-coordinate for the player inventory grid. */
	public static final int INVENTORY_X = 27;
	/** GUI Y-coordinate for the player inventory grid. */
	public static final int INVENTORY_Y = 108;

	private final Container input;
	private int capacityCap = AttunedConfig.DEFAULT.capacityCap();
	private int capacityPerShard = AttunedConfig.DEFAULT.capacityPerShard();

	/**
	 * Reachability accessor anchored to the Altar block. On the server this is
	 * a real {@link ContainerLevelAccess#create} over the Altar's level and
	 * {@code BlockPos}; on the client it is {@link ContainerLevelAccess#NULL}.
	 * The bind handler uses it to look up the right level and blockstate so a
	 * player who has crossed dimensions cannot bind against a stale position.
	 */
	private final ContainerLevelAccess access;

	/**
	 * Client-side constructor used by {@link AltarMenuType} when the menu is
	 * opened from a packet. Spins up a throwaway {@link SimpleContainer} for
	 * the shard slot and a {@link ContainerLevelAccess#NULL} accessor; the
	 * authoritative state lives on the server menu, and the client only needs
	 * a matching slot layout to display the GUI.
	 */
	public AltarMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(INPUT_SIZE), ContainerLevelAccess.NULL);
	}

	/**
	 * Builds the menu around an existing input container. The server keeps the
	 * authoritative container and a {@link ContainerLevelAccess} pinned to the
	 * Altar's level and position; the {@code SimpleContainer} is fine here
	 * because the Altar block has no per-block storage and we drop any
	 * leftover shard back to the player when the menu closes.
	 */
	public AltarMenu(int containerId, Inventory inventory, Container input, ContainerLevelAccess access) {
		super(AltarMenuType.TYPE, containerId);
		checkContainerSize(input, INPUT_SIZE);
		this.input = input;
		this.access = access;

		// Shard input slot — only Attunement Shards are accepted, and it stacks
		// to a full shard pile so a player can dump a stack in if they want.
		this.addSlot(new Slot(input, 0, INPUT_SLOT_X, INPUT_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(AttunedContent.ATTUNEMENT_SHARD);
			}
		});

		// Player inventory (3 rows) + hotbar, centered under the wider Altar header.
		// Keeping these coordinates shared with the screen prevents drawn wells and
		// clickable slots from drifting apart.
		this.addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
		addConfigDataSlots();
	}

	/** The shard currently held in the input slot, possibly empty. */
	public ItemStack inputStack() {
		return this.input.getItem(INPUT_SLOT);
	}

	/**
	 * The reachability accessor for this menu, pinned to the Altar's level and
	 * position. Used by the bind handler to resolve the block in the right
	 * dimension; never {@code null}.
	 */
	public ContainerLevelAccess access() {
		return this.access;
	}

	/** The input container, exposed for server-side handlers that need to mutate it. */
	public Container inputContainer() {
		return this.input;
	}

	/** Server-synced capacity cap used by the Altar screen. */
	public int capacityCap() {
		return capacityCap;
	}

	/** Server-synced capacity gain per bound shard used by the Altar screen. */
	public int capacityPerShard() {
		return capacityPerShard;
	}

	private void addConfigDataSlots() {
		this.addDataSlot(new DataSlot() {
			@Override
			public int get() {
				return AttunedConfig.get().capacityCap();
			}

			@Override
			public void set(int value) {
				capacityCap = value;
			}
		});
		this.addDataSlot(new DataSlot() {
			@Override
			public int get() {
				return AttunedConfig.get().capacityPerShard();
			}

			@Override
			public void set(int value) {
				capacityPerShard = value;
			}
		});
	}

	@Override
	public boolean stillValid(Player player) {
		// Mirrors the vanilla pattern from EnchantmentMenu / CraftingMenu —
		// closes the GUI if the player walks out of range, dimension-changes,
		// or the Altar block is broken.
		return stillValid(this.access, player, AttunedContent.ATTUNEMENT_ALTAR);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack moved = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			moved = stack.copy();
			if (slotIndex < INPUT_SIZE) {
				// From input back to inventory.
				if (!this.moveItemStackTo(stack, INPUT_SIZE, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (stack.is(AttunedContent.ATTUNEMENT_SHARD)) {
				// From inventory into the shard slot, only if it's a shard.
				if (!this.moveItemStackTo(stack, 0, INPUT_SIZE, false)) {
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
		}
		return moved;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		// Return any leftover shard to the player so closing the GUI never eats items.
		ItemStack leftover = this.input.removeItemNoUpdate(INPUT_SLOT);
		if (!leftover.isEmpty()) {
			if (player.isAlive() && !player.isRemoved()) {
				player.getInventory().placeItemBackInInventory(leftover);
			} else {
				player.drop(leftover, false);
			}
		}
	}
}
