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

import java.util.Optional;

/**
 * Brings the six Focus slots into the creative inventory's Survival Inventory
 * tab.
 *
 * <p>The creative menu rebuilds its slots every tab switch by wrapping each slot
 * of the real {@code InventoryMenu} — Focus slots included — in a private
 * {@code SlotWrapper}. It lays them out with the vanilla inventory formula,
 * which drops our slots on top of the hotbar, so we re-place each Focus wrapper
 * into the side panel as it is built and draw the panel behind it.</p>
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin
		extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {

	@Shadow
	public abstract boolean isInventoryOpen();

	private CreativeModeInventoryScreenMixin() {
		// Never invoked; mixin classes are not instantiated.
		super(null, null, null);
	}

	/**
	 * Each Focus slot is wrapped with the wrong screen coordinates. We catch the
	 * wrapper as it is constructed and, when it wraps a {@link FocusSlot}, move it
	 * into the side panel column instead.
	 */
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
		// The Focus-slot suppression flag is a survival-only, recipe-book concern;
		// clear it so the creative slots are never left hidden by a stale value.
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
			graphics.setTooltipForNextFrame(this.font, AttunementReadout.tooltip(AttunementReadout.cached(player)),
				Optional.empty(), mouseX, mouseY);
		}
	}

	/**
	 * The Focus panel sits past the creative window's left edge, which {@code
	 * hasClickedOutside} would otherwise flag as outside the GUI — turning a
	 * place/take on a Focus slot into a drop. Veto that flag over the panel.
	 */
	@Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
	private void attuned$keepFocusPanelInside(double mx, double my, int xo, int yo, CallbackInfoReturnable<Boolean> cir) {
		if (this.isInventoryOpen()
				&& FocusPanel.withinPanel(FocusLayout.CREATIVE_X, FocusLayout.CREATIVE_Y, mx - xo, my - yo)) {
			cir.setReturnValue(false);
		}
	}
}
