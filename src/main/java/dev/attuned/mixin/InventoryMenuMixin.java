package dev.attuned.mixin;

import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.menu.FocusContainer;
import dev.attuned.menu.FocusLayout;
import dev.attuned.menu.FocusSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Appends Attuned's six Focus slots to the survival player inventory menu and
 * teaches shift-click to move accessories in and out of them.
 *
 * <p>Vanilla {@link InventoryMenu} builds slots 0-45. We append six more
 * ({@link FocusSlot}) at constructor TAIL, so they occupy menu indices 46-51.
 * Slot value changes broadcast to the client automatically, so no packets
 * are required.</p>
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {
	// Player inventory + hotbar menu-index range, from InventoryMenu's own
	// constants: INV_SLOT_START (9) .. USE_ROW_SLOT_END (45).
	private static final int ATTUNED_INV_START = 9;
	private static final int ATTUNED_INV_END = 45;

	private InventoryMenuMixin() {
		// Never invoked: mixin classes are never instantiated. Required because
		// the superclass has no no-arg constructor.
		super(null, 0);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void attuned$addFocusSlots(Inventory inventory, boolean active, Player player, CallbackInfo ci) {
		FocusContainer focusContainer = new FocusContainer(player);
		for (int i = 0; i < AttunedInv.SIZE; i++) {
			this.addSlot(new FocusSlot(
				focusContainer,
				i,
				FocusLayout.INVENTORY_X + FocusLayout.SLOT_INSET,
				FocusLayout.INVENTORY_Y + FocusLayout.SLOT_INSET + i * FocusLayout.SLOT
			));
		}
	}

	/**
	 * Takes over the two shift-click cases vanilla {@link InventoryMenu#quickMoveStack}
	 * cannot handle, since it only knows slots 0-45:
	 * <ul>
	 *   <li>out of a Focus slot — into the main inventory;</li>
	 *   <li>a Focus item out of the inventory — into the first free Focus slot.</li>
	 * </ul>
	 * Every other shift-click falls through to vanilla untouched.
	 */
	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void attuned$quickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (index < 0 || index >= this.slots.size()) {
			return;
		}
		Slot slot = this.slots.get(index);
		if (slot instanceof FocusSlot) {
			// Out of a Focus slot. Vanilla mishandles these indices entirely, so
			// we always take over and cancel.
			ItemStack moved = slot.hasItem()
				? attuned$moveStack(player, slot, ATTUNED_INV_START, ATTUNED_INV_END, true)
				: ItemStack.EMPTY;
			cir.setReturnValue(moved);
		} else if (slot != null && slot.hasItem() && index != 0 && slot.mayPickup(player)
				&& Attunement.definitionFor(player, slot.getItem()).isPresent()) {
			// index 0 is the crafting result slot: taking over there would bypass
			// onQuickCraft (stats, recipe unlocks, multi-craft ingredient use).
			// A Focus item in the inventory — equip it into the first free Focus
			// slot. If every Focus slot is full, fall through to vanilla untouched.
			ItemStack moved = attuned$moveStack(player, slot, FocusLayout.MENU_START, FocusLayout.MENU_END, false);
			if (!moved.isEmpty()) {
				cir.setReturnValue(moved);
			}
		}
	}

	/**
	 * Moves {@code slot}'s stack into the menu-index range
	 * [{@code destStart}, {@code destEnd}) and settles the source slot. Returns
	 * the pre-move stack on success (the {@code quickMoveStack} contract), or
	 * {@link ItemStack#EMPTY} if nothing moved.
	 */
	private ItemStack attuned$moveStack(Player player, Slot slot, int destStart, int destEnd, boolean reverse) {
		ItemStack stackInSlot = slot.getItem();
		ItemStack original = stackInSlot.copy();

		if (!this.moveItemStackTo(stackInSlot, destStart, destEnd, reverse)) {
			return ItemStack.EMPTY;
		}

		if (stackInSlot.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		if (stackInSlot.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}

		slot.onTake(player, stackInSlot);
		return original;
	}
}
