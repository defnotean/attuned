package dev.attuned.client;

import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.menu.FocusLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

/**
 * Draws the Focus side panel: a vanilla-coloured extension of the inventory with
 * six recessed slot wells. Shared by the survival and creative inventory screens
 * so both render an identical panel — only the column's position differs.
 */
public final class FocusPanel {
	private FocusPanel() {}

	// Padding between the slot column and the panel edge, in GUI pixels.
	private static final int PAD_X = 5;
	private static final int PAD_Y = 8;

	// ARGB palette — sampled directly from the vanilla inventory texture.
	private static final int PANEL_FACE = 0xFFC6C6C6;
	private static final int WELL_SHADOW = 0xFF373737;
	private static final int WELL_HIGHLIGHT = 0xFFFFFFFF;
	private static final int WELL_FACE = 0xFF8B8B8B;
	private static final int DORMANT_DIM = 0x55000000;

	/**
	 * Draws the panel and six slot wells for {@code player}, with the slot
	 * column's top-left corner at ({@code slotX}, {@code slotY}) relative to the
	 * screen's top-left ({@code leftPos}, {@code topPos}).
	 */
	public static void draw(GuiGraphicsExtractor graphics, int leftPos, int topPos,
			int slotX, int slotY, Player player) {
		int x0 = leftPos + slotX - PAD_X;
		int x1 = leftPos + slotX + FocusLayout.SLOT + PAD_X;
		int y0 = topPos + slotY - PAD_Y;
		int y1 = topPos + slotY + AttunedInv.SIZE * FocusLayout.SLOT + PAD_Y;

		// Panel: a flat fill in the exact vanilla inventory colour, so it reads as
		// a seamless extension of the inventory rather than a separate bordered box.
		graphics.fill(x0, y0, x1, y1, PANEL_FACE);

		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int i = 0; i < AttunedInv.SIZE; i++) {
			int sx = leftPos + slotX;
			int sy = topPos + slotY + i * FocusLayout.SLOT;
			// Inset bevel: dark on the top/left, white on the bottom/right.
			graphics.fill(sx, sy, sx + FocusLayout.SLOT, sy + FocusLayout.SLOT, WELL_SHADOW);
			graphics.fill(sx + 1, sy + 1, sx + FocusLayout.SLOT, sy + FocusLayout.SLOT, WELL_HIGHLIGHT);
			graphics.fill(sx + 1, sy + 1, sx + FocusLayout.SLOT - 1, sy + FocusLayout.SLOT - 1, WELL_FACE);
			// Dim only an equipped-but-dormant Focus (over budget) — empty wells stay clean.
			if (!inv.get(i).isEmpty() && !Attunement.isActive(player, i)) {
				graphics.fill(sx + 1, sy + 1, sx + FocusLayout.SLOT - 1, sy + FocusLayout.SLOT - 1, DORMANT_DIM);
			}
		}
	}

	/**
	 * True when the point ({@code relX}, {@code relY}) — measured from the
	 * screen's top-left — lies within the Focus panel whose slot column starts at
	 * ({@code slotX}, {@code slotY}). Used so the inventory screens treat clicks
	 * on the panel as inside the GUI, even though it sits past the window edge.
	 */
	public static boolean withinPanel(int slotX, int slotY, double relX, double relY) {
		return relX >= slotX - PAD_X
			&& relX < slotX + FocusLayout.SLOT + PAD_X
			&& relY >= slotY - PAD_Y
			&& relY < slotY + AttunedInv.SIZE * FocusLayout.SLOT + PAD_Y;
	}
}
