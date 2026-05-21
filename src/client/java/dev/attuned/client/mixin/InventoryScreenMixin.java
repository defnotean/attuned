package dev.attuned.client.mixin;

import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
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
 * inventory screen. The panel face uses the exact vanilla inventory colour so it
 * reads as a seamless extension of the inventory; each slot is a vanilla-style
 * recessed well. A Focus that is equipped but dormant (over budget) is dimmed.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractContainerScreen<InventoryMenu> {
	// Slot column — MUST match the slot positions in InventoryMenuMixin.
	private static final int FOCUS_SLOT_X = 180;
	private static final int FOCUS_SLOT_Y = 30;
	private static final int SLOT = 18;

	// Side-panel rectangle, derived from the slot column.
	private static final int PANEL_X0 = FOCUS_SLOT_X - 5;
	private static final int PANEL_X1 = FOCUS_SLOT_X + SLOT + 5;
	private static final int PANEL_Y0 = FOCUS_SLOT_Y - 8;
	private static final int PANEL_Y1 = FOCUS_SLOT_Y + AttunedInv.SIZE * SLOT + 8;

	// ARGB palette — sampled directly from the vanilla inventory texture.
	private static final int PANEL_FACE = 0xFFC6C6C6;
	private static final int WELL_SHADOW = 0xFF373737;
	private static final int WELL_HIGHLIGHT = 0xFFFFFFFF;
	private static final int WELL_FACE = 0xFF8B8B8B;
	private static final int DORMANT_DIM = 0x55000000;

	private InventoryScreenMixin() {
		// Never invoked; mixin classes are not instantiated.
		super(null, null, null);
	}

	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void attuned$drawFocusPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		Player player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		AttunedInv inv = AttunedAttachments.getInventory(player);

		// Panel: a flat fill in the exact vanilla inventory colour, so it reads as a
		// seamless extension of the inventory rather than a separate bordered box.
		graphics.fill(
			this.leftPos + PANEL_X0, this.topPos + PANEL_Y0,
			this.leftPos + PANEL_X1, this.topPos + PANEL_Y1,
			PANEL_FACE);

		// Six recessed slot wells.
		for (int i = 0; i < AttunedInv.SIZE; i++) {
			int sx = this.leftPos + FOCUS_SLOT_X;
			int sy = this.topPos + FOCUS_SLOT_Y + i * SLOT;
			// Inset bevel: dark on the top/left, white on the bottom/right.
			graphics.fill(sx, sy, sx + SLOT, sy + SLOT, WELL_SHADOW);
			graphics.fill(sx + 1, sy + 1, sx + SLOT, sy + SLOT, WELL_HIGHLIGHT);
			graphics.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, WELL_FACE);
			// Dim only an equipped-but-dormant Focus (over budget) — empty wells stay clean.
			if (!inv.get(i).isEmpty() && !Attunement.isActive(player, i)) {
				graphics.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, DORMANT_DIM);
			}
		}
	}
}
