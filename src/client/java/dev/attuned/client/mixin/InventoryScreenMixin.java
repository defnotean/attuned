package dev.attuned.client.mixin;

import dev.attuned.client.FocusPanel;
import dev.attuned.menu.FocusLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws Attuned's six Focus slots as a side panel on the right edge of the
 * survival inventory screen. The drawing itself lives in {@link FocusPanel},
 * shared with the creative inventory screen.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractContainerScreen<InventoryMenu> {
	private InventoryScreenMixin() {
		// Never invoked; mixin classes are not instantiated.
		super(null, null, null);
	}

	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void attuned$drawFocusPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		Player player = Minecraft.getInstance().player;
		if (player != null) {
			FocusPanel.draw(graphics, this.leftPos, this.topPos,
				FocusLayout.INVENTORY_X, FocusLayout.INVENTORY_Y, player);
		}
	}
}
