package dev.attuned.client.mixin;

import dev.attuned.client.FocusPanel;
import dev.attuned.menu.FocusLayout;
import dev.attuned.menu.FocusSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws Attuned's six Focus slots as a side panel on the left of the survival
 * inventory screen — clear of the potion effects vanilla draws down the right
 * edge. The drawing itself lives in {@link FocusPanel}, shared with the creative
 * inventory screen.
 *
 * <p>The recipe book opens across that same left side, so whenever it is showing
 * the panel and its slots are suppressed, then restored once it closes.</p>
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractContainerScreen<InventoryMenu> {
	private InventoryScreenMixin() {
		// Never invoked; mixin classes are not instantiated.
		super(null, null, null);
	}

	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void attuned$drawFocusPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		// The recipe book claims the same edge as the Focus panel. While it is
		// open, suppress the panel and its slots; restore them once it closes.
		boolean recipeBookOpen = attuned$recipeBookOpen();
		FocusSlot.setSuppressed(recipeBookOpen);
		if (recipeBookOpen) {
			return;
		}
		Player player = Minecraft.getInstance().player;
		if (player != null) {
			FocusPanel.draw(graphics, this.leftPos, this.topPos,
				FocusLayout.INVENTORY_X, FocusLayout.INVENTORY_Y, player);
		}
	}

	private boolean attuned$recipeBookOpen() {
		RecipeBookComponent<?> book =
			((AbstractRecipeBookScreenAccessor) (Object) this).attuned$recipeBookComponent();
		return book != null && book.isVisible();
	}
}
