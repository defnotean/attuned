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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Focus Reliquary. It exposes three Focus stores as real slots so
 * vanilla drag-and-drop moves Foci between them: the held reliquary's grid, the
 * player's six equipped Focus slots (a labelled column beside the window), and
 * the player inventory. The equipped slots write through {@link FocusContainer}
 * to the synced attachment, exactly as the Altar menu does.
 *
 * <p>The same class backs both reliquary tiers. The reliquary grid row count,
 * the menu index of the equipped slots, and the inventory Y all derive from the
 * <em>actual</em> container size ({@code rows = size / 9}), so the 27-slot
 * satchel and the 54-slot Grand Focus Reliquary share one menu and one screen.
 */
public class SatchelMenu extends AbstractContainerMenu {
	public static final int SATCHEL_X = 8;
	public static final int SATCHEL_Y = 18;
	public static final int INVENTORY_X = 8;
	/** Equipped Focus slots sit in a 3x2 grid in the right panel, inside the window bounds. */
	public static final int EQUIPPED_X = 182;
	public static final int EQUIPPED_Y = 18;
	public static final int EQUIPPED_COLS = 3;
	/**
	 * Gap below the reliquary grid before the player inventory begins. Chosen so the
	 * small (3-row) tier keeps its historical INVENTORY_Y of 84 (18 + 3*18 + 12),
	 * exactly matching the baked leather texture's inventory wells.
	 */
	private static final int INVENTORY_GAP = 12;

	private final Container satchel;
	private final InteractionHand hand;
	private final Player owner;
	private final boolean handUnknown;
	/** Actual reliquary grid size of THIS menu (27 or 54), read from the container. */
	private final int satchelSize;

	public SatchelMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(AttunedComponents.SATCHEL_SIZE),
			InteractionHand.MAIN_HAND, true, SatchelMenuType.TYPE);
	}

	/** Client-side constructor for the Grand Focus Reliquary (54-slot grid). */
	public static SatchelMenu grand(int containerId, Inventory inventory) {
		return new SatchelMenu(containerId, inventory, new SimpleContainer(AttunedComponents.GRAND_SATCHEL_SIZE),
			InteractionHand.MAIN_HAND, true, SatchelMenuType.GRAND_TYPE);
	}

	public SatchelMenu(int containerId, Inventory inventory, Container satchel, InteractionHand hand,
			MenuType<SatchelMenu> type) {
		this(containerId, inventory, satchel, hand, false, type);
	}

	private SatchelMenu(int containerId, Inventory inventory, Container satchel, InteractionHand hand,
			boolean handUnknown, MenuType<SatchelMenu> type) {
		super(type, containerId);
		this.satchel = satchel;
		this.hand = hand;
		this.owner = inventory.player;
		this.handUnknown = handUnknown;
		this.satchelSize = satchel.getContainerSize();

		int rows = satchelSize / 9;

		// Reliquary grid (slots 0 .. satchelSize-1). Row count derives from the size.
		for (int row = 0; row < rows; row++) {
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
							&& stack.getItem() != AttunedContent.SATCHEL_OF_FOCI.get()
							&& stack.getItem() != AttunedContent.GRAND_SATCHEL_OF_FOCI.get();
					}
				});
			}
		}

		// Equipped Focus slots (slots equippedStart() .. equippedEnd()-1), backed by the
		// synced attachment so dragging a Focus here equips it. FocusSlot already caps
		// at one item and rejects non-Foci (and so the reliquary items too, which are not Foci).
		FocusContainer equipped = new FocusContainer(inventory.player);
		for (int i = 0; i < AttunedInv.SIZE; i++) {
			this.addSlot(new FocusSlot(equipped, i,
				EQUIPPED_X + (i % EQUIPPED_COLS) * 18, EQUIPPED_Y + (i / EQUIPPED_COLS) * 18));
		}

		this.addStandardInventorySlots(inventory, INVENTORY_X, inventoryY());
	}

	/** Menu index of the first equipped Focus slot (the reliquary grid takes 0..satchelSize-1). */
	public int equippedStart() {
		return satchelSize;
	}

	/** One past the last equipped Focus slot index. */
	public int equippedEnd() {
		return equippedStart() + AttunedInv.SIZE;
	}

	/** Number of 9-wide reliquary grid rows in THIS menu (3 for the satchel, 6 for the grand). */
	public int satchelRows() {
		return satchelSize / 9;
	}

	/** Reliquary grid size of THIS menu. */
	public int satchelSize() {
		return satchelSize;
	}

	/** Player-inventory top Y, pushed below the reliquary grid (which grows with rows). */
	public int inventoryY() {
		return SATCHEL_Y + satchelRows() * 18 + INVENTORY_GAP;
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
			return isReliquary(player.getMainHandItem())
				|| isReliquary(player.getOffhandItem());
		}
		return isReliquary(player.getItemInHand(hand));
	}

	private static boolean isReliquary(ItemStack stack) {
		return stack.getItem() == AttunedContent.SATCHEL_OF_FOCI.get()
			|| stack.getItem() == AttunedContent.GRAND_SATCHEL_OF_FOCI.get();
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		int satchelEnd = satchelSize;
		int equippedEnd = equippedEnd();
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
