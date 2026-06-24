package dev.attuned.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.attuned.Attuned;
import dev.attuned.attunement.Attunement;
import dev.attuned.client.AttunementReadout;
import dev.attuned.client.ClientNetworkPackets;
import dev.attuned.client.hud.CombatHud;
import dev.attuned.menu.AltarMenu;
import dev.attuned.menu.BindShardPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import dev.attuned.client.compat.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
	private static final ResourceLocation BACKGROUND_TEXTURE =
		new ResourceLocation(Attuned.MOD_ID, "textures/gui/altar.png");

	// Window dimensions, in GUI pixels. Wider and taller than a vanilla chest row
	// so the altar readout can breathe above the centered player inventory.
	private static final int IMAGE_WIDTH = 216;
	private static final int IMAGE_HEIGHT = 190;

	private static final int BAR_TRACK = 0xFF373737;
	private static final int TITLE_TEXT = 0xFFEDE6FF;
	private static final int BODY_TEXT = 0xFFE3D8F5;
	private static final int MUTED_TEXT = 0xFFB8ACC8;
	private static final int WARNING_TEXT = 0xFFFFD37A;
	private static final int SCREEN_BACKDROP = 0xB0101218;

	// Hover ring on the Bind button: white at moderate alpha so the highlight
	// reads as a glow rather than a hard outline.
	private static final int BUTTON_HOVER_ARGB = 0xC0FFFFFF;
	// Half-alpha mask for the stance-tinted shard well inner border. ORed with
	// the stance ARGB after its alpha has been masked off.
	private static final int WELL_TINT_ALPHA = 0x80000000;

	// Painted shard well geometry, used for custom polish around the menu slot.
	private static final int SLOT_WELL_X = AltarMenu.INPUT_WELL_X;
	private static final int SLOT_WELL_Y = AltarMenu.INPUT_WELL_Y;
	private static final int SLOT_WELL_WIDTH = AltarMenu.INPUT_WELL_WIDTH;
	private static final int SLOT_WELL_HEIGHT = AltarMenu.INPUT_WELL_HEIGHT;

	// Bind button geometry, positioned over the generated button recess.
	private static final int BUTTON_W = 50;
	private static final int BUTTON_H = 20;
	private static final int BUTTON_X = 151;
	private static final int BUTTON_Y = 31;

	// Budget bar, drawn above the lower status strip.
	private static final int BAR_X = 14;
	private static final int BAR_Y = 75;
	private static final int BAR_W = IMAGE_WIDTH - 28;
	private static final int BAR_H = 3;

	/** Y-coordinate for the hint label, sitting in the altar status strip. */
	private static final int HINT_Y = 88;
	private static final int READOUT_X = 24;
	private static final int TEXT_BOX_X = 18;
	private static final int HINT_MAX_WIDTH = IMAGE_WIDTH - TEXT_BOX_X - 18;
	private static final int STATUS_X = TEXT_BOX_X;
	private static final int STATUS_MAX_WIDTH = 84;
	private static final int DETAIL_MAX_WIDTH = STATUS_MAX_WIDTH;
	private static final int STATUS_Y = 55;
	private static final int DETAIL_Y = 65;
	private Button bindButton;

	public AltarScreen(AltarMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = IMAGE_WIDTH;
		this.imageHeight = IMAGE_HEIGHT;
		this.titleLabelX = READOUT_X;
		this.titleLabelY = 13;
		this.inventoryLabelY = AltarMenu.INVENTORY_Y - 10;
	}

	@Override
	protected void init() {
		super.init();
		this.bindButton = new BindButton(
			this.leftPos + BUTTON_X,
			this.topPos + BUTTON_Y,
			new net.minecraft.network.chat.TranslatableComponent("screen.attuned.altar.bind"),
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
		ClientNetworkPackets.send(new BindShardPayload());
	}

	@Override
	protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
		GuiGraphics graphics = new GuiGraphics(Minecraft.getInstance(), poseStack);
		int x = this.leftPos;
		int y = this.topPos;

		graphics.fill(0, 0, this.width, this.height, SCREEN_BACKDROP);
		graphics.blit(BACKGROUND_TEXTURE, x, y,
			0.0F, 0.0F, IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);

		int sx = x + SLOT_WELL_X;
		int sy = y + SLOT_WELL_Y;

		// Player-dependent polish: stance-tinted shard well border, the accent line
		// above the readout, the budget bar fill and the Bind button hover ring all
		// need the player's stance colour, so resolve it once here.
		Player player = this.minecraft != null ? this.minecraft.player : null;
		if (player != null) {
			AttunementReadout.Snapshot readout = AttunementReadout.cached(player);
			int stance = AttunementReadout.apexAwareStanceArgb(readout);

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
			int capacity = readout.capacity();
			int used = readout.used();
			int fill = AttunementReadout.budgetFillWidth(BAR_W, used, capacity);
			if (fill > 0) {
				graphics.fill(barX, barY, barX + fill, barY + BAR_H, stance);
			}

		}
	}

	/**
	 * Draws a 1-pixel inner border on the four edges of the shard well, one pixel
	 * inside the bevel so the tint reads as a glow on the slot face rather than as
	 * a thickening of the bevel. {@code wellX} and {@code wellY} are the top-left
	 * of the well's outer dark border; the custom well rectangle inside it is
	 * the face that holds the slot itself.
	 */
	private static void drawWellInnerBorder(GuiGraphics graphics, int wellX, int wellY, int argb) {
		int innerX0 = wellX + 1;
		int innerY0 = wellY + 1;
		int innerX1 = wellX + SLOT_WELL_WIDTH - 1;
		int innerY1 = wellY + SLOT_WELL_HEIGHT - 1;
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
	private static void drawReadoutAccent(GuiGraphics graphics, int panelX, int panelY, int stance) {
		int accentY = panelY + 17;
		int accentWidth = IMAGE_WIDTH * 6 / 10;
		int accentX = panelX + (IMAGE_WIDTH - accentWidth) / 2;
		graphics.fill(accentX, accentY, accentX + accentWidth, accentY + 1, stance);
	}

	@Override
	protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
		GuiGraphics graphics = new GuiGraphics(Minecraft.getInstance(), poseStack);
		// Window title: drawn ourselves so it stays readable over the dark altar art.
		graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE_TEXT, false);

		Player player = this.minecraft != null ? this.minecraft.player : null;
		if (player == null) {
			return;
		}

		AttunementReadout.Snapshot readout = AttunementReadout.cached(player);
		int capacity = readout.capacity();
		int used = readout.used();
		int cap = this.menu.capacityCap();
		int perShard = this.menu.capacityPerShard();
		int active = readout.active();

		Component budgetText = new net.minecraft.network.chat.TranslatableComponent("screen.attuned.altar.budget", used, capacity);
		graphics.drawString(this.font, budgetText, READOUT_X, 24, BODY_TEXT, false);

		// Stance row: a small textured gem prefix that visually says "this is your
		// stance," followed only by the affinity name in its colour. Dropping the
		// "Stance:" label keeps the row clear of the shard slot at x=80 — with a
		// 10-pixel gem and the affinity name at most ~30 pixels wide, the whole
		// row fits comfortably in the left half of the panel.
		int stanceGemSize = 10;
		int stanceGemX = TEXT_BOX_X;
		int stanceGemY = 39;
		CombatHud.drawPlayerGem(graphics, stanceGemX, stanceGemY, stanceGemSize,
			readout.committed().orElse(null), readout.discord(), readout.capstone().orElse(null),
			readout.atApex());
		graphics.drawString(this.font, AttunementReadout.stanceLabel(readout),
			stanceGemX + stanceGemSize + 4, 41, BODY_TEXT, false);
		drawTrimmedText(graphics, new net.minecraft.network.chat.TextComponent(active + " active"),
			STATUS_X, STATUS_Y, STATUS_MAX_WIDTH, BODY_TEXT);
		drawTrimmedText(graphics, detailLine(readout),
			STATUS_X, DETAIL_Y, DETAIL_MAX_WIDTH, MUTED_TEXT);

		// Hint text under the slot, swapped out when capacity is full or empty.
		Component hint;
		int hintColor = BODY_TEXT;
		if (capacity >= cap) {
			hint = new net.minecraft.network.chat.TranslatableComponent("screen.attuned.altar.hint.cap", cap);
			hintColor = WARNING_TEXT;
		} else if (this.menu.inputStack().isEmpty()) {
			hint = new net.minecraft.network.chat.TranslatableComponent("screen.attuned.altar.hint.empty");
		} else {
			ItemStack shard = this.menu.inputStack();
			int next = Math.min(cap, capacity + perShard);
			hint = shard.getCount() == 1
				? new net.minecraft.network.chat.TranslatableComponent("screen.attuned.altar.hint.ready.one", capacity, next, cap)
				: new net.minecraft.network.chat.TranslatableComponent("screen.attuned.altar.hint.ready.many",
					capacity, next, cap, shard.getCount());
		}
		drawTrimmedText(graphics, hint, TEXT_BOX_X, HINT_Y, HINT_MAX_WIDTH, hintColor);
	}

	private void drawTrimmedText(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
		if (this.font.width(text) <= maxWidth) {
			graphics.drawString(this.font, text, x, y, color, false);
			return;
		}
		String ellipsis = "...";
		int ellipsisWidth = this.font.width(ellipsis);
		String trimmed = this.font.plainSubstrByWidth(text.getString(), Math.max(0, maxWidth - ellipsisWidth));
		graphics.drawString(this.font, new net.minecraft.network.chat.TextComponent(trimmed + ellipsis), x, y, color, false);
	}

	private static Component detailLine(AttunementReadout.Snapshot readout) {
		Component prefix = new net.minecraft.network.chat.TextComponent(readout.dormant() + " dormant");
		if (readout.pact().isPresent()) {
			return prefix.copy().append(new net.minecraft.network.chat.TextComponent(" / Pact"));
		}
		if (readout.capstone().isPresent()) {
			return prefix.copy().append(new net.minecraft.network.chat.TextComponent(" / Apex"));
		}
		return prefix;
	}

	private static void drawButtonOutline(GuiGraphics graphics, int x0, int y0, int x1, int y1, int argb) {
		graphics.fill(x0, y0, x1, y0 + 1, argb);
		graphics.fill(x0, y1 - 1, x1, y1, argb);
		graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, argb);
		graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, argb);
	}

	private static final class BindButton extends Button {
		private BindButton(int x, int y, Component message, OnPress onPress) {
			super(x, y, BUTTON_W, BUTTON_H, message, onPress);
		}

		@Override
		public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
			GuiGraphics graphics = new GuiGraphics(Minecraft.getInstance(), poseStack);
			int x0 = this.x;
			int y0 = this.y;
			int x1 = x0 + getWidth();
			int y1 = y0 + getHeight();
			if (!this.active) {
				graphics.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, 0x99000000);
			} else if (isHoveredOrFocused()) {
				drawButtonOutline(graphics, x0, y0, x1, y1, BUTTON_HOVER_ARGB);
			}
			GuiComponent.drawCenteredString(poseStack, Minecraft.getInstance().font, getMessage(),
				x0 + getWidth() / 2, y0 + (getHeight() - 8) / 2, BODY_TEXT);
		}
	}
}
