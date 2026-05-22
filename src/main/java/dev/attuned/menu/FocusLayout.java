package dev.attuned.menu;

import dev.attuned.attunement.AttunedInv;

/**
 * Shared geometry for the Focus slots. The menu (which places the slots) and the
 * inventory screens (which draw the panel behind them) both read these constants,
 * so the slot column and its backdrop always agree.
 */
public final class FocusLayout {
	private FocusLayout() {}

	/** Edge length of one slot, in GUI pixels. */
	public static final int SLOT = 18;

	/**
	 * Offset of the slot (item + click box) from its well's top-left corner.
	 * Each well is drawn as an 18px square with a 1px bevel, so the slot sits one
	 * pixel inside it — which keeps the click box aligned with the visible well.
	 */
	public static final int SLOT_INSET = 1;

	/** Top-left of the Focus well column in the survival inventory screen. */
	public static final int INVENTORY_X = 180;
	public static final int INVENTORY_Y = 30;

	/**
	 * Top-left of the Focus column in the creative inventory screen, relative to
	 * the window's top-left corner. The column sits to the <em>left</em> of the
	 * window — hence the negative X — because the creative screen renders the
	 * player's active potion effects down its right edge, exactly where a
	 * right-hand panel would collide with them.
	 */
	public static final int CREATIVE_X = -22;
	public static final int CREATIVE_Y = 14;

	/**
	 * First menu index occupied by a Focus slot. Vanilla {@code InventoryMenu}
	 * builds slots 0-45, so our six appended slots take indices 46-51.
	 */
	public static final int MENU_START = 46;

	/** One past the last Focus slot's menu index — an exclusive bound. */
	public static final int MENU_END = MENU_START + AttunedInv.SIZE;
}
