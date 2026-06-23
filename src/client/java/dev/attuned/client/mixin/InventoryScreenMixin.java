package dev.attuned.client.mixin;

import dev.attuned.client.AttunementReadout;
import dev.attuned.client.FocusPanel;
import dev.attuned.menu.FocusLayout;
import dev.attuned.menu.FocusSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 1.20.1 survival-inventory hook for Attuned's Focus side panel. */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractContainerScreen<InventoryMenu> {
	private InventoryScreenMixin() {
		super(null, null, null);
	}

	@Inject(method = "renderBg", at = @At("TAIL"))
	private void attuned$drawFocusPanel(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
		boolean recipeBookOpen = attuned$recipeBookOpen();
		FocusSlot.setSuppressed(recipeBookOpen);
		if (recipeBookOpen) {
			return;
		}
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		FocusPanel.draw(graphics, this.leftPos, this.topPos,
			FocusLayout.INVENTORY_X, FocusLayout.INVENTORY_Y, player);
		if (FocusPanel.overReadout(FocusLayout.INVENTORY_X, FocusLayout.INVENTORY_Y,
				mouseX - this.leftPos, mouseY - this.topPos)) {
			graphics.renderComponentTooltip(this.font,
				AttunementReadout.tooltip(AttunementReadout.cached(player)), mouseX, mouseY);
		}
	}

	private boolean attuned$recipeBookOpen() {
		RecipeBookComponent book = ((InventoryScreen) (Object) this).getRecipeBookComponent();
		return book != null && book.isVisible();
	}
}
