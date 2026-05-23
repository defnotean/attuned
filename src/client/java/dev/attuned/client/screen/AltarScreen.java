package dev.attuned.client.screen;

import dev.attuned.AttunedConfig;
import dev.attuned.attunement.Attunement;
import dev.attuned.client.AttunementReadout;
import dev.attuned.content.AttunementAltarBlock;
import dev.attuned.menu.AltarMenu;
import dev.attuned.menu.BindShardPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Attunement Altar's screen — a flat-rectangle GUI showing the player's
 * current attunement readout, a single shard input slot and a Bind button. The
 * background is drawn from solid fills rather than a texture so the mod ships
 * without a placeholder art file; the user-supplied texture pass replaces this
 * later if desired.
 */
public class AltarScreen extends AbstractContainerScreen<AltarMenu> {

	// Window dimensions, in GUI pixels — sized to match a vanilla single-row chest
	// so the inventory grid below it has its usual proportions.
	private static final int IMAGE_WIDTH = 176;
	private static final int IMAGE_HEIGHT = 166;

	// Colour palette — the same vanilla-inventory grey used by FocusPanel, so the
	// Altar screen reads as a sibling of the inventory GUI rather than a stranger.
	private static final int PANEL_FACE = 0xFFC6C6C6;
	private static final int PANEL_SHADOW = 0xFF555555;
	private static final int PANEL_HIGHLIGHT = 0xFFFFFFFF;
	private static final int WELL_SHADOW = 0xFF373737;
	private static final int WELL_HIGHLIGHT = 0xFFFFFFFF;
	private static final int WELL_FACE = 0xFF8B8B8B;
	private static final int BAR_TRACK = 0xFF373737;
	private static final int LABEL_DARK = 0xFF404040;

	// Polish accents — texture-less detail layered over the flat panel so the
	// screen reads as designed without commissioning art.
	// Bracket arms are 3 GUI pixels long and 1 pixel thick: long enough to read as
	// a frame corner at the screen's typical scale, short enough to stay
	// subordinate to the slot well and Bind button.
	private static final int BRACKET_ARM = 3;
	private static final int BRACKET_COLOR = 0xFF373737;
	// Hover ring on the Bind button: white at moderate alpha so the highlight
	// reads as a glow rather than a hard outline.
	private static final int BUTTON_HOVER_ARGB = 0xC0FFFFFF;
	// Half-alpha mask for the stance-tinted shard well inner border. ORed with
	// the stance ARGB after its alpha has been masked off.
	private static final int WELL_TINT_ALPHA = 0x80000000;

	// Slot geometry — keep in sync with the {@code addSlot(input, …, 80, 22)} call
	// in {@link AltarMenu}: the well is drawn one pixel outside the slot bounds.
	private static final int SLOT_X = 80;
	private static final int SLOT_Y = 22;
	private static final int SLOT_SIZE = 18;

	// Bind button geometry, positioned to the right of the shard slot.
	private static final int BUTTON_W = 60;
	private static final int BUTTON_H = 20;
	private static final int BUTTON_X = 105;
	private static final int BUTTON_Y = 21;

	// Budget bar, drawn under the readout text. Sits just below the slot/Bind
	// row at y=22-40 with room for the hint underneath, all above the inventory
	// label at y=72.
	private static final int BAR_X = 8;
	private static final int BAR_Y = 46;
	private static final int BAR_W = IMAGE_WIDTH - 16;
	private static final int BAR_H = 3;

	/** Y-coordinate for the hint label, sitting above the inventory label at y=72. */
	private static final int HINT_Y = 58;
	/**
	 * Horizontal divider between the altar section and the player inventory. A
	 * dark hairline anchored just above the inventory label so the two sections
	 * read as distinct without commissioning a full texture.
	 */
	private static final int DIVIDER_Y = 70;

	private Button bindButton;

	public AltarScreen(AltarMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		this.bindButton = Button.builder(Component.translatable("screen.attuned.altar.bind"), btn -> sendBind())
			.bounds(this.leftPos + BUTTON_X, this.topPos + BUTTON_Y, BUTTON_W, BUTTON_H)
			.build();
		this.addRenderableWidget(this.bindButton);
		refreshButtonState();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		// Re-check every tick so the button flips disabled the instant the player
		// drags the last shard out of the slot, and re-enables when one is added.
		refreshButtonState();
	}

	private void refreshButtonState() {
		if (this.bindButton == null) {
			return;
		}
		Player player = this.minecraft != null ? this.minecraft.player : null;
		if (player == null) {
			this.bindButton.active = false;
			return;
		}
		boolean hasShard = !this.menu.inputStack().isEmpty();
		boolean roomLeft = Attunement.capacity(player) < AttunedConfig.get().capacityCap();
		this.bindButton.active = hasShard && roomLeft;
	}

	private void sendBind() {
		ClientPlayNetworking.send(new BindShardPayload());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;

		// Panel: bevelled rectangle in the vanilla inventory grey.
		graphics.fill(x, y, x + IMAGE_WIDTH, y + IMAGE_HEIGHT, PANEL_SHADOW);
		graphics.fill(x + 1, y + 1, x + IMAGE_WIDTH, y + IMAGE_HEIGHT, PANEL_HIGHLIGHT);
		graphics.fill(x + 1, y + 1, x + IMAGE_WIDTH - 1, y + IMAGE_HEIGHT - 1, PANEL_FACE);

		// Corner brackets: a 3-pixel L at each panel corner, drawn in the same dark
		// tone as the slot well shadow so the four corners read as part of one frame.
		drawCornerBrackets(graphics, x, y);

		// Horizontal hairline divider between the altar section and the player
		// inventory grid below. Keeps the eye from reading the two sections as
		// one cramped block of text and slots.
		graphics.fill(x + 8, y + DIVIDER_Y, x + IMAGE_WIDTH - 8, y + DIVIDER_Y + 1, BRACKET_COLOR);

		// Shard slot well: drawn at the slot position with an inset bevel that matches
		// the wells on the inventory's Focus panel.
		int sx = x + SLOT_X - 1;
		int sy = y + SLOT_Y - 1;
		graphics.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, WELL_SHADOW);
		graphics.fill(sx + 1, sy + 1, sx + SLOT_SIZE, sy + SLOT_SIZE, WELL_HIGHLIGHT);
		graphics.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, WELL_FACE);

		// Player-inventory and hotbar slot wells. Vanilla containers paint these as
		// part of their background texture; with a texture-less screen we have to
		// draw them ourselves or the inventory area renders as blank grey. Positions
		// match {@code AltarMenu.addStandardInventorySlots(inventory, 8, 84)} —
		// three 9-slot rows at y=84/102/120 and a 9-slot hotbar at y=142.
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				drawInventoryWell(graphics, x + 8 + col * 18, y + 84 + row * 18);
			}
		}
		for (int col = 0; col < 9; col++) {
			drawInventoryWell(graphics, x + 8 + col * 18, y + 142);
		}

		// Player-dependent polish: stance-tinted shard well border, the accent line
		// above the readout, the budget bar fill and the Bind button hover ring all
		// need the player's stance colour, so resolve it once here.
		Player player = this.minecraft != null ? this.minecraft.player : null;
		if (player != null) {
			int stance = AttunementReadout.stanceArgb(player);

			// Stance-tinted inner border around the shard well, sitting one pixel
			// inside the well's bevel so the tint reads as a glow on the slot face.
			int tintedBorder = (stance & 0x00FFFFFF) | WELL_TINT_ALPHA;
			drawWellInnerBorder(graphics, sx, sy, tintedBorder);

			// Accent line above the "Attunement:" label — anchors the readout text
			// to the panel and gives the stance colour another surface to live on.
			drawReadoutAccent(graphics, x, y, stance);

			// Budget bar: dark track plus an affinity-tinted fill proportional to
			// the attunement points currently spent. Drawn under the readout text in
			// the inventory section so the GUI shows the same numbers as the readout text.
			int barX = x + BAR_X;
			int barY = y + BAR_Y;
			graphics.fill(barX, barY, barX + BAR_W, barY + BAR_H, BAR_TRACK);
			int capacity = Attunement.capacity(player);
			int used = Attunement.used(player);
			if (capacity > 0 && used > 0) {
				int fill = Math.max(1, Math.round(BAR_W * (used / (float) capacity)));
				graphics.fill(barX, barY, barX + fill, barY + BAR_H, stance);
			}

			// Bind button hover ring: a 1-pixel highlight border traced around the
			// button when it is enabled and the cursor is over it. Disabled buttons
			// get no extra treatment so the disabled state still reads as inert.
			if (this.bindButton != null && this.bindButton.active
					&& isOverBindButton(mouseX, mouseY)) {
				drawBindHoverRing(graphics, x, y);
			}
		}
	}

	/**
	 * Draws one player-inventory or hotbar slot well: a beveled 18x18 dark frame
	 * matching the shard well's idiom but without the stance-coloured inner tint.
	 * {@code slotX} and {@code slotY} are the screen-space coordinates of the
	 * 16x16 slot's top-left corner — the well is drawn one pixel outside on every
	 * side so the slot's items render cleanly over the bevel.
	 */
	private static void drawInventoryWell(GuiGraphicsExtractor graphics, int slotX, int slotY) {
		int wx = slotX - 1;
		int wy = slotY - 1;
		graphics.fill(wx, wy, wx + SLOT_SIZE, wy + SLOT_SIZE, WELL_SHADOW);
		graphics.fill(wx + 1, wy + 1, wx + SLOT_SIZE, wy + SLOT_SIZE, WELL_HIGHLIGHT);
		graphics.fill(wx + 1, wy + 1, wx + SLOT_SIZE - 1, wy + SLOT_SIZE - 1, WELL_FACE);
	}

	/**
	 * Draws a 1-pixel inner border on the four edges of the shard well, one pixel
	 * inside the bevel so the tint reads as a glow on the slot face rather than as
	 * a thickening of the bevel. {@code wellX} and {@code wellY} are the top-left
	 * of the well's outer dark border; the {@link #SLOT_SIZE} square inside it is
	 * the face that holds the slot itself.
	 */
	private static void drawWellInnerBorder(GuiGraphicsExtractor graphics, int wellX, int wellY, int argb) {
		int innerX0 = wellX + 1;
		int innerY0 = wellY + 1;
		int innerX1 = wellX + SLOT_SIZE - 1;
		int innerY1 = wellY + SLOT_SIZE - 1;
		// Top and bottom edges of the inner border.
		graphics.fill(innerX0, innerY0, innerX1, innerY0 + 1, argb);
		graphics.fill(innerX0, innerY1 - 1, innerX1, innerY1, argb);
		// Left and right edges, inset to avoid double-blending the corners.
		graphics.fill(innerX0, innerY0 + 1, innerX0 + 1, innerY1 - 1, argb);
		graphics.fill(innerX1 - 1, innerY0 + 1, innerX1, innerY1 - 1, argb);
	}

	/**
	 * Draws a 3-pixel L-shaped bracket at each of the panel's four corners. The
	 * brackets sit on the panel edge so they crown the existing bevel rather than
	 * floating inside the panel face.
	 */
	private static void drawCornerBrackets(GuiGraphicsExtractor graphics, int panelX, int panelY) {
		drawCornerBracket(graphics, panelX, panelY, +1, +1);
		drawCornerBracket(graphics, panelX + IMAGE_WIDTH, panelY, -1, +1);
		drawCornerBracket(graphics, panelX, panelY + IMAGE_HEIGHT, +1, -1);
		drawCornerBracket(graphics, panelX + IMAGE_WIDTH, panelY + IMAGE_HEIGHT, -1, -1);
	}

	/**
	 * Draws one corner bracket — an L-shape made of a horizontal and a vertical
	 * 1-pixel arm of length {@link #BRACKET_ARM}, anchored at the corner pixel
	 * ({@code cornerX}, {@code cornerY}) and growing in the directions given by
	 * {@code dirX} and {@code dirY} (each {@code +1} or {@code -1}).
	 */
	private static void drawCornerBracket(GuiGraphicsExtractor graphics,
			int cornerX, int cornerY, int dirX, int dirY) {
		int hx0 = dirX > 0 ? cornerX : cornerX - BRACKET_ARM;
		int hx1 = dirX > 0 ? cornerX + BRACKET_ARM : cornerX;
		int hy0 = dirY > 0 ? cornerY : cornerY - 1;
		int hy1 = dirY > 0 ? cornerY + 1 : cornerY;
		graphics.fill(hx0, hy0, hx1, hy1, BRACKET_COLOR);

		int vx0 = dirX > 0 ? cornerX : cornerX - 1;
		int vx1 = dirX > 0 ? cornerX + 1 : cornerX;
		int vy0 = dirY > 0 ? cornerY : cornerY - BRACKET_ARM;
		int vy1 = dirY > 0 ? cornerY + BRACKET_ARM : cornerY;
		graphics.fill(vx0, vy0, vx1, vy1, BRACKET_COLOR);
	}

	/**
	 * Draws a 1-pixel horizontal accent line above the "Attunement:" readout. The
	 * line spans 60% of the panel's width, centred horizontally, and sits four
	 * pixels above the readout label (which renders at {@code y + 18}) so the
	 * accent feels anchored to the text without crowding it.
	 */
	private static void drawReadoutAccent(GuiGraphicsExtractor graphics, int panelX, int panelY, int stance) {
		int labelY = panelY + 18;
		int accentY = labelY - 4;
		int accentWidth = IMAGE_WIDTH * 6 / 10;
		int accentX = panelX + (IMAGE_WIDTH - accentWidth) / 2;
		graphics.fill(accentX, accentY, accentX + accentWidth, accentY + 1, stance);
	}

	/**
	 * Draws a 1-pixel highlight border around the Bind button when it is active and
	 * hovered. Traced just outside the button's bounds so the ring crowns the
	 * vanilla button render rather than painting over its bevel.
	 */
	private static void drawBindHoverRing(GuiGraphicsExtractor graphics, int panelX, int panelY) {
		int bx0 = panelX + BUTTON_X - 1;
		int by0 = panelY + BUTTON_Y - 1;
		int bx1 = panelX + BUTTON_X + BUTTON_W + 1;
		int by1 = panelY + BUTTON_Y + BUTTON_H + 1;
		// Top and bottom edges of the ring.
		graphics.fill(bx0, by0, bx1, by0 + 1, BUTTON_HOVER_ARGB);
		graphics.fill(bx0, by1 - 1, bx1, by1, BUTTON_HOVER_ARGB);
		// Left and right edges, inset to avoid double-blending the corners.
		graphics.fill(bx0, by0 + 1, bx0 + 1, by1 - 1, BUTTON_HOVER_ARGB);
		graphics.fill(bx1 - 1, by0 + 1, bx1, by1 - 1, BUTTON_HOVER_ARGB);
	}

	/**
	 * True when the given mouse position lies within the Bind button's bounds.
	 * Uses {@link #BUTTON_X}/{@link #BUTTON_Y} relative to {@code leftPos} and
	 * {@code topPos} rather than going through {@link #bindButton} so the hover
	 * test stays self-contained and matches the button's actual hit-box exactly.
	 */
	private boolean isOverBindButton(int mouseX, int mouseY) {
		int bx0 = this.leftPos + BUTTON_X;
		int by0 = this.topPos + BUTTON_Y;
		return mouseX >= bx0 && mouseX < bx0 + BUTTON_W
			&& mouseY >= by0 && mouseY < by0 + BUTTON_H;
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		// Window title — drawn ourselves so we control its colour and position.
		graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, LABEL_DARK, false);
		graphics.text(this.font, this.playerInventoryTitle,
			this.inventoryLabelX, this.inventoryLabelY, LABEL_DARK, false);

		Player player = this.minecraft != null ? this.minecraft.player : null;
		if (player == null) {
			return;
		}

		int capacity = Attunement.capacity(player);
		int used = Attunement.used(player);
		int cap = AttunedConfig.get().capacityCap();

		// Readout row: used / capacity, then stance.
		Component readout = Component.translatable("screen.attuned.altar.attunement")
			.append(Component.literal(used + " / " + capacity));
		graphics.text(this.font, readout, 8, 18, LABEL_DARK, false);

		Component stance = Component.translatable("screen.attuned.altar.stance")
			.append(AttunementAltarBlock.stanceLabel(player));
		graphics.text(this.font, stance, 8, 30, LABEL_DARK, false);

		// Hint text under the slot, swapped out when capacity is full or empty.
		Component hint;
		if (capacity >= cap) {
			hint = Component.translatable("screen.attuned.altar.hint.cap", cap);
		} else if (this.menu.inputStack().isEmpty()) {
			hint = Component.translatable("screen.attuned.altar.hint.empty");
		} else {
			ItemStack shard = this.menu.inputStack();
			int next = Math.min(cap, capacity + AttunedConfig.get().capacityPerShard());
			hint = Component.translatable("screen.attuned.altar.hint.ready",
				shard.getCount(), next, cap);
		}
		graphics.text(this.font, hint, 8, HINT_Y, LABEL_DARK, false);
	}
}
