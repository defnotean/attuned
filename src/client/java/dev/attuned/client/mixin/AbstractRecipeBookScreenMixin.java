package dev.attuned.client.mixin;

import dev.attuned.client.FocusPanel;
import dev.attuned.menu.FocusLayout;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps clicks on the survival inventory's Focus panel from being treated as
 * clicks outside the GUI.
 *
 * <p>{@code InventoryScreen} inherits {@code hasClickedOutside} from
 * {@code AbstractRecipeBookScreen} rather than the base container screen, so the
 * veto has to be injected here to take effect. A click flagged "outside" has its
 * slot id rewritten to -999, which turns a place/take into a drop and disables
 * shift-click — we veto that for points over the panel. While the recipe book is
 * open it covers the panel, so we stand down and let vanilla decide.</p>
 */
@Mixin(AbstractRecipeBookScreen.class)
public abstract class AbstractRecipeBookScreenMixin {

	@Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
	private void attuned$keepFocusPanelInside(double mx, double my, int xo, int yo, CallbackInfoReturnable<Boolean> cir) {
		// Only the survival inventory carries a Focus panel.
		if (!((Object) this instanceof InventoryScreen)) {
			return;
		}
		// With the recipe book open, the panel is hidden behind it — defer to vanilla.
		RecipeBookComponent<?> book =
			((AbstractRecipeBookScreenAccessor) (Object) this).attuned$recipeBookComponent();
		if (book != null && book.isVisible()) {
			return;
		}
		if (FocusPanel.withinPanel(FocusLayout.INVENTORY_X, FocusLayout.INVENTORY_Y, mx - xo, my - yo)) {
			cir.setReturnValue(false);
		}
	}
}
