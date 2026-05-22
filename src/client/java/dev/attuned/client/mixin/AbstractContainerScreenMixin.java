package dev.attuned.client.mixin;

import dev.attuned.client.FocusPanel;
import dev.attuned.menu.FocusLayout;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps clicks on the survival inventory's Focus panel from being treated as
 * clicks outside the GUI.
 *
 * <p>{@code hasClickedOutside} flags anything past the window's right edge as
 * outside — and the Focus panel sits exactly there. A click flagged "outside"
 * has its slot id rewritten to -999, which turns a place/take into a drop and
 * disables shift-click. We veto that flag for points over the panel.</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

	@Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
	private void attuned$keepFocusPanelInside(double mx, double my, int xo, int yo, CallbackInfoReturnable<Boolean> cir) {
		// Only the survival inventory; the creative screen has its own override.
		if ((Object) this instanceof InventoryScreen
				&& FocusPanel.withinPanel(FocusLayout.INVENTORY_X, FocusLayout.INVENTORY_Y, mx - xo, my - yo)) {
			cir.setReturnValue(false);
		}
	}
}
