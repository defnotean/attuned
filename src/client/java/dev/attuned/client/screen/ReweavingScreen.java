package dev.attuned.client.screen;

import dev.attuned.Attuned;
import dev.attuned.menu.ReweavePayload;
import dev.attuned.menu.ReweavingMenu;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Screen for the Altar of Reweaving. */
public class ReweavingScreen extends AbstractContainerScreen<ReweavingMenu> {
	private static final Identifier BACKGROUND_TEXTURE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "textures/gui/altar_of_reweaving.png");
	private static final int IMAGE_WIDTH = 216;
	private static final int IMAGE_HEIGHT = 190;
	private static final int TITLE_TEXT = 0xFFEDE6FF;
	private static final int BODY_TEXT = 0xFFE3D8F5;
	private static final int MUTED_TEXT = 0xFFB8ACC8;
	private static final int WARNING_TEXT = 0xFFFFD37A;
	private static final int SCREEN_BACKDROP = 0xB0101218;
	private static final int BUTTON_HOVER_ARGB = 0xC0FFFFFF;
	private static final int BUTTON_W = 54;
	private static final int BUTTON_H = 16;
	private static final int BUTTON_X = 134;
	private static final int BUTTON_Y = 83;
	private static final int HINT_X = 14;
	private static final int HINT_Y = 88;
	private static final int HINT_MAX_WIDTH = BUTTON_X - HINT_X - 7;

	private Button reweaveButton;

	public ReweavingScreen(ReweavingMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
		this.titleLabelX = 14;
		this.titleLabelY = 13;
		this.inventoryLabelY = ReweavingMenu.INVENTORY_Y - 10;
	}

	@Override
	protected void init() {
		super.init();
		this.reweaveButton = new ReweaveButton(
			this.leftPos + BUTTON_X,
			this.topPos + BUTTON_Y,
			Component.translatable("screen.attuned.reweaving_altar.reweave"),
			button -> ClientPlayNetworking.send(new ReweavePayload()));
		this.addRenderableWidget(this.reweaveButton);
		refreshButtonState();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		refreshButtonState();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, SCREEN_BACKDROP);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, this.leftPos, this.topPos,
			0.0F, 0.0F, IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE_TEXT, false);
		Component hint = hint();
		int color = canCommit() ? BODY_TEXT : WARNING_TEXT;
		drawTrimmedText(graphics, hint, HINT_X, HINT_Y, HINT_MAX_WIDTH, color);
	}

	private void refreshButtonState() {
		if (this.reweaveButton != null) {
			this.reweaveButton.active = canCommit();
		}
	}

	private boolean canCommit() {
		return (this.menu.hasAllInputs() || this.menu.canTemper()) && this.menu.outputStack().isEmpty();
	}

	private Component hint() {
		if (!this.menu.outputStack().isEmpty()) {
			return Component.translatable("screen.attuned.reweaving_altar.hint.output_blocked");
		}
		if (this.menu.canTemper()) {
			return Component.translatable("screen.attuned.reweaving_altar.hint.temper");
		}
		if (!this.menu.hasAllFocusInputs()) {
			return Component.translatable("screen.attuned.reweaving_altar.hint.missing_foci");
		}
		if (!this.menu.container().getItem(ReweavingMenu.CATALYST_SLOT)
				.is(dev.attuned.content.AttunedContent.ATTUNEMENT_SHARD_FRAGMENT)) {
			return Component.translatable("screen.attuned.reweaving_altar.hint.missing_fragment");
		}
		return Component.translatable("screen.attuned.reweaving_altar.hint.ready");
	}

	private void drawTrimmedText(GuiGraphicsExtractor graphics, Component text, int x, int y, int maxWidth, int color) {
		if (this.font.width(text) <= maxWidth) {
			graphics.text(this.font, text, x, y, color, false);
			return;
		}
		String ellipsis = "...";
		int ellipsisWidth = this.font.width(ellipsis);
		String trimmed = this.font.plainSubstrByWidth(text.getString(), Math.max(0, maxWidth - ellipsisWidth));
		graphics.text(this.font, Component.literal(trimmed + ellipsis), x, y, color, false);
	}

	private static void drawButtonOutline(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int argb) {
		graphics.fill(x0, y0, x1, y0 + 1, argb);
		graphics.fill(x0, y1 - 1, x1, y1, argb);
		graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, argb);
		graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, argb);
	}

	private static final class ReweaveButton extends Button {
		private ReweaveButton(int x, int y, Component message, OnPress onPress) {
			super(x, y, BUTTON_W, BUTTON_H, message, onPress, DEFAULT_NARRATION);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int x0 = getX();
			int y0 = getY();
			int x1 = x0 + getWidth();
			int y1 = y0 + getHeight();
			if (!this.active) {
				graphics.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, 0x99000000);
			} else if (isHoveredOrFocused()) {
				drawButtonOutline(graphics, x0, y0, x1, y1, BUTTON_HOVER_ARGB);
			}
			extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
		}
	}
}
