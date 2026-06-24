package dev.attuned.client.mixin;

import dev.attuned.client.AttunementReadout;
import dev.attuned.client.FocusPanel;
import dev.attuned.menu.FocusLayout;
import dev.attuned.menu.FocusSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** 1.20.1 creative Survival Inventory tab hook for Attuned's Focus side panel. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin
		extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {

	@Shadow
	public abstract boolean isInventoryOpen();

	private CreativeModeInventoryScreenMixin() {
		super(null, null, null);
	}

	@ModifyArgs(
		method = "selectTab",
		require = 1,
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$SlotWrapper;<init>(Lnet/minecraft/world/inventory/Slot;III)V"
		)
	)
	private void attuned$placeFocusSlot(Args args) {
		if (args.get(0) instanceof FocusSlot focusSlot) {
			int i = focusSlot.getContainerSlot();
			args.set(2, FocusLayout.CREATIVE_X + FocusLayout.SLOT_INSET);
			args.set(3, FocusLayout.CREATIVE_Y + FocusLayout.SLOT_INSET + i * FocusLayout.SLOT);
		}
	}

	@Inject(method = "renderBg", at = @At("TAIL"))
	private void attuned$drawFocusPanel(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
		FocusSlot.setSuppressed(false);
		if (!this.isInventoryOpen()) {
			return;
		}
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		FocusPanel.draw(graphics, this.leftPos, this.topPos,
			FocusLayout.CREATIVE_X, FocusLayout.CREATIVE_Y, player);
		if (FocusPanel.overReadout(FocusLayout.CREATIVE_X, FocusLayout.CREATIVE_Y,
				mouseX - this.leftPos, mouseY - this.topPos)) {
			graphics.renderComponentTooltip(this.font,
				AttunementReadout.tooltip(AttunementReadout.cached(player)), mouseX, mouseY);
		}
	}

	@Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
	private void attuned$keepFocusPanelInside(double mx, double my, int xo, int yo, int button, CallbackInfoReturnable<Boolean> cir) {
		if (this.isInventoryOpen()
				&& FocusPanel.withinPanel(FocusLayout.CREATIVE_X, FocusLayout.CREATIVE_Y, mx - xo, my - yo)) {
			cir.setReturnValue(false);
		}
	}
}
