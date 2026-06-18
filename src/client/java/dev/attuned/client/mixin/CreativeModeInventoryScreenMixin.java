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
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Legacy creative Survival Inventory tab hook for Attuned's Focus side panel. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin
		extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {

	@Shadow
	public abstract int getSelectedTab();

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
	private void attuned$drawFocusPanel(PoseStack poseStack, float partialTick, int mouseX, int mouseY,
			CallbackInfo ci) {
		FocusSlot.setSuppressed(false);
		if (!attuned$isInventoryOpen()) {
			return;
		}
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		GuiGraphics graphics = new GuiGraphics(Minecraft.getInstance(), poseStack);
		FocusPanel.draw(graphics, this.leftPos, this.topPos,
			FocusLayout.CREATIVE_X, FocusLayout.CREATIVE_Y, player);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void attuned$renderFocusReadoutTooltip(PoseStack poseStack, int mouseX, int mouseY,
			float partialTick, CallbackInfo ci) {
		if (!attuned$isInventoryOpen()
				|| !FocusPanel.overReadout(FocusLayout.CREATIVE_X, FocusLayout.CREATIVE_Y,
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
		if (attuned$isInventoryOpen()
				&& FocusPanel.withinPanel(FocusLayout.CREATIVE_X, FocusLayout.CREATIVE_Y, mx - xo, my - yo)) {
			cir.setReturnValue(false);
		}
	}

	private boolean attuned$isInventoryOpen() {
		return this.getSelectedTab() == CreativeModeTab.TAB_INVENTORY.getId();
	}
}