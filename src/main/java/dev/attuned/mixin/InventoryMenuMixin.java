package dev.attuned.mixin;

import dev.attuned.attunement.AttunedInv;
import dev.attuned.menu.FocusContainer;
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
 * Appends Attuned's six Focus slots to the survival player inventory menu.
 *
 * <p>Vanilla {@link InventoryMenu} builds slots 0-45. We append six more
 * ({@link FocusSlot}) at constructor TAIL, so they occupy menu indices 46-51.
 * Slot value changes broadcast to the client automatically, so no packets
 * are required.</p>
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {
	/** First menu index occupied by a Focus slot (slots 46-51). */
	private static final int ATTUNED_FOCUS_START = 46;
	private static final int ATTUNED_FOCUS_END = ATTUNED_FOCUS_START + AttunedInv.SIZE;

	/** Layout: a column of six 18px slots in a side panel on the inventory's right edge. */
	private static final int ATTUNED_FOCUS_X = 180;
	private static final int ATTUNED_FOCUS_Y = 30;
	private static final int ATTUNED_SLOT_SIZE = 18;

	// Player inventory (incl. hotbar) menu-index range, from InventoryMenu's
	// own constants: INV_SLOT_START (9) .. USE_ROW_SLOT_END (45).
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
				ATTUNED_FOCUS_X,
				ATTUNED_FOCUS_Y + i * ATTUNED_SLOT_SIZE
			));
		}
	}

	/**
	 * Vanilla {@link InventoryMenu#quickMoveStack} only knows slots 0-45, so a
	 * shift-click on a Focus slot (46-51) would be mishandled. We intercept those
	 * indices and move the stack down into the player inventory ourselves; every
	 * other index falls through to vanilla untouched.
	 */
	@Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
	private void attuned$quickMoveFromFocus(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
		if (index < ATTUNED_FOCUS_START || index >= ATTUNED_FOCUS_END) {
			return;
		}
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasItem()) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}
		ItemStack stackInSlot = slot.getItem();
		ItemStack original = stackInSlot.copy();

		// Move into the main inventory + hotbar. true = fill from the back (hotbar)
		// first, matching how vanilla unloads armor/crafting slots.
		if (!this.moveItemStackTo(stackInSlot, ATTUNED_INV_START, ATTUNED_INV_END, true)) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}

		if (stackInSlot.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		if (stackInSlot.getCount() == original.getCount()) {
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}

		slot.onTake(player, stackInSlot);
		cir.setReturnValue(original);
	}
}
