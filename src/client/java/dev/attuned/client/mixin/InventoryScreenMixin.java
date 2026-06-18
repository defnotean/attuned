package dev.attuned.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.FormattedCharSequence;
import dev.attuned.client.AttunementReadout;
import dev.attuned.client.FocusPanel;
import dev.attuned.menu.FocusLayout;
import dev.attuned.menu.FocusSlot;
import java.util.ArrayList;
import java.util.List;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Legacy survival-inventory hook for Attuned's Focus side panel.
 *
 * <p>The panel is drawn in {@code renderBg} and its tooltip is drawn at the end
 * of {@code render}. The recipe book claims the same left edge, so the panel
 * and its slots are suppressed while it is open.</p>
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractContainerScreen<InventoryMenu> {
	private InventoryScreenMixin() {
		// Never invoked; mixin classes are not instantiated.
		super(null, null, null);
	}

	@Inject(method = "render", at = @At("HEAD"))
	private void attuned$syncFocusSlotVisibility(PoseStack poseStack, int mouseX, int mouseY,
			float partialTick, CallbackInfo ci) {
		FocusSlot.setSuppressed(attuned$recipeBookOpen());
	}

	@Inject(method = "renderBg", at = @At("TAIL"))
	private void attuned$drawFocusPanel(PoseStack poseStack, float partialTick, int mouseX, int mouseY,
			CallbackInfo ci) {
		if (attuned$recipeBookOpen()) {
			return;
		}
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		GuiGraphics graphics = new GuiGraphics(Minecraft.getInstance(), poseStack);
		FocusPanel.draw(graphics, this.leftPos, this.topPos,
			FocusLayout.INVENTORY_X, FocusLayout.INVENTORY_Y, player);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void attuned$renderFocusReadoutTooltip(PoseStack poseStack, int mouseX, int mouseY,
			float partialTick, CallbackInfo ci) {
		if (attuned$recipeBookOpen()
				|| !FocusPanel.overReadout(FocusLayout.INVENTORY_X, FocusLayout.INVENTORY_Y,
					mouseX - this.leftPos, mouseY - this.topPos)) {
			return;
		}
		Player player = Minecraft.getInstance().player;
		if (player != null) {
			List<FormattedCharSequence> lines = new ArrayList<>();
			for (var c : AttunementReadout.tooltip(player)) {
				lines.add(c.getVisualOrderText());
			}
			this.renderTooltip(poseStack, lines, mouseX, mouseY);
		}
	}

	@Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
	private void attuned$keepFocusPanelInside(double mx, double my, int xo, int yo, int button,
			CallbackInfoReturnable<Boolean> cir) {
		if (!attuned$recipeBookOpen()
				&& FocusPanel.withinPanel(FocusLayout.INVENTORY_X, FocusLayout.INVENTORY_Y, mx - xo, my - yo)) {
			cir.setReturnValue(false);
		}
	}

	private boolean attuned$recipeBookOpen() {
		RecipeBookComponent book = ((InventoryScreen) (Object) this).getRecipeBookComponent();
		return book != null && book.isVisible();
	}
}
