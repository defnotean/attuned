package dev.attuned.client.screen;

import dev.attuned.Attuned;
import dev.attuned.api.focus.Affinity;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.client.AttunementReadout;
import dev.attuned.client.hud.CombatHud;
import dev.attuned.combat.Apex;
import dev.attuned.content.AttunementAltarBlock;
import dev.attuned.menu.AltarMenu;
import dev.attuned.menu.BindShardPayload;
import dev.attuned.pacts.Pact;
import dev.attuned.pacts.Pacts;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Attunement Altar's screen: a custom-textured ritual panel showing the
 * player's current attunement readout, a single shard input slot, and a Bind
 * button. The texture supplies the static stonework while code draws dynamic
 * stance, capacity, hover, and status details.
 */
public class AltarScreen extends AbstractContainerScreen<AltarMenu> {
	private static final Identifier BACKGROUND_TEXTURE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "textures/gui/altar.png");

	// Window dimensions, in GUI pixels. Wider and taller than a vanilla chest row
	// so the altar readout can breathe above the centered player inventory.
	private static final int IMAGE_WIDTH = 216;
	private static final int IMAGE_HEIGHT = 190;

	private static final int BAR_TRACK = 0xFF373737;
	private static final int LABEL_DARK = 0xFF404040;

	// Hover ring on the Bind button: white at moderate alpha so the highlight
	// reads as a glow rather than a hard outline.
	private static final int BUTTON_HOVER_ARGB = 0xC0FFFFFF;
	// Half-alpha mask for the stance-tinted shard well inner border. ORed with
	// the stance ARGB after its alpha has been masked off.
	private static final int WELL_TINT_ALPHA = 0x80000000;

	// Slot geometry — keep in sync with the {@link AltarMenu} slot positions.
	private static final int SLOT_X = AltarMenu.INPUT_SLOT_X;
	private static final int SLOT_Y = AltarMenu.INPUT_SLOT_Y;
	private static final int SLOT_SIZE = 18;

	// Bind button geometry, positioned to the right of the shard slot.
	private static final int BUTTON_W = 54;
	private static final int BUTTON_H = 20;
	private static final int BUTTON_X = 153;
	private static final int BUTTON_Y = 33;

	// Budget bar, drawn under the forecast line with room for the status strip.
	private static final int BAR_X = 14;
	private static final int BAR_Y = 67;
	private static final int BAR_W = IMAGE_WIDTH - 28;
	private static final int BAR_H = 3;

	/** Y-coordinate for the hint label, sitting in the altar status strip. */
	private static final int HINT_Y = 78;
	private Button bindButton;

	public AltarScreen(AltarMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		this.bindButton = new BindButton(
			this.leftPos + BUTTON_X,
			this.topPos + BUTTON_Y,
			Component.translatable("screen.attuned.altar.bind"),
			btn -> sendBind());
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
		boolean roomLeft = Attunement.capacity(player) < this.menu.capacityCap();
		this.bindButton.active = hasShard && roomLeft;
	}

	private void sendBind() {
		ClientPlayNetworking.send(new BindShardPayload());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = this.leftPos;
		int y = this.topPos;

		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, x, y,
			0.0F, 0.0F, IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);

		int sx = x + SLOT_X - 1;
		int sy = y + SLOT_Y - 1;

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
	 * Draws a 1-pixel horizontal accent line above the "Attunement:" readout. The
	 * line spans 60% of the panel's width, centred horizontally, and sits four
	 * pixels above the readout label (which renders at {@code y + 18}) so the
	 * accent feels anchored to the text without crowding it.
	 */
	private static void drawReadoutAccent(GuiGraphicsExtractor graphics, int panelX, int panelY, int stance) {
		int accentY = panelY + 17;
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
		int cap = this.menu.capacityCap();
		int active = Attunement.activeSlots(player).size();
		int dormant = dormantFocusCount(player);

		Component readout = Component.literal("Capacity " + used + " / " + capacity);
		graphics.text(this.font, readout, 14, 24, LABEL_DARK, false);

		// Stance row: a small textured gem prefix that visually says "this is your
		// stance," followed only by the affinity name in its colour. Dropping the
		// "Stance:" label keeps the row clear of the shard slot at x=80 — with a
		// 10-pixel gem and the affinity name at most ~30 pixels wide, the whole
		// row fits comfortably in the left half of the panel.
		Optional<Affinity> committed = Attunement.committedAffinity(player);
		boolean discord = Attunement.isDiscord(player);
		int stanceGemSize = 10;
		int stanceGemX = 14;
		int stanceGemY = 39;
		CombatHud.drawGem(graphics, stanceGemX, stanceGemY, stanceGemSize,
			committed.orElse(null), discord, false, false);
		graphics.text(this.font, AttunementAltarBlock.stanceLabel(player),
			stanceGemX + stanceGemSize + 4, 41, LABEL_DARK, false);
		graphics.text(this.font, forecastLine(player, active, dormant), 14, 55, LABEL_DARK, false);

		// Hint text under the slot, swapped out when capacity is full or empty.
		Component hint;
		if (capacity >= cap) {
			hint = Component.translatable("screen.attuned.altar.hint.cap", cap);
		} else if (this.menu.inputStack().isEmpty()) {
			hint = Component.translatable("screen.attuned.altar.hint.empty");
		} else {
			ItemStack shard = this.menu.inputStack();
			int next = Math.min(cap, capacity + this.menu.capacityPerShard());
			hint = Component.translatable("screen.attuned.altar.hint.ready",
				shard.getCount(), next, cap);
		}
		graphics.text(this.font, hint, 14, HINT_Y, LABEL_DARK, false);
	}

	private static int dormantFocusCount(Player player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		int dormant = 0;
		for (int slot = 0; slot < AttunedInv.SIZE; slot++) {
			if (!inv.get(slot).isEmpty()
					&& !Attunement.isActive(player, slot)
					&& Attunement.definitionFor(player, inv.get(slot)).isPresent()) {
				dormant++;
			}
		}
		return dormant;
	}

	private static Component forecastLine(Player player, int active, int dormant) {
		Component line = Component.literal(active + " active / " + dormant + " dormant");
		Optional<Pact> pact = Pacts.activeOf(player);
		if (pact.isPresent()) {
			return line.copy()
				.append(Component.literal(" / "))
				.append(pact.get().displayName());
		}
		if (Apex.affinityOf(player).isPresent()) {
			return line.copy().append(Component.literal(" / Apex ready"));
		}
		return line;
	}

	private static final class BindButton extends Button {
		private BindButton(int x, int y, Component message, OnPress onPress) {
			super(x, y, BUTTON_W, BUTTON_H, message, onPress, DEFAULT_NARRATION);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int x0 = getX();
			int y0 = getY();
			int x1 = x0 + getWidth();
			int y1 = y0 + getHeight();
			int face = this.active
				? (isHoveredOrFocused() ? 0xFF4B415F : 0xFF2D2935)
				: 0xFF24222A;
			int trim = this.active ? 0xFFB995FF : 0xFF5F596A;
			graphics.fill(x0, y0, x1, y1, 0xFF15131B);
			graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, trim);
			graphics.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, face);
			graphics.fill(x0 + 3, y0 + 3, x1 - 3, y0 + 4, this.active ? 0xFFE0C6FF : 0xFF77707E);
			graphics.fill(x0 + 3, y1 - 4, x1 - 3, y1 - 3, 0xFF17151D);
			extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
		}
	}
}
