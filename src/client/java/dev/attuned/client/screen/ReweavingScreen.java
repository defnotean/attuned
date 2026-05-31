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
	private static final int BUTTON_W = 58;
	private static final int BUTTON_H = 20;
	private static final int BUTTON_X = 148;
	private static final int BUTTON_Y = 66;

	private Button reweaveButton;

	public ReweavingScreen(ReweavingMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
		this.inventoryLabelY = this.imageHeight - 94;
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
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, this.leftPos, this.topPos,
			0.0F, 0.0F, IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TITLE_TEXT, false);
		graphics.text(this.font, this.playerInventoryTitle,
			this.inventoryLabelX, this.inventoryLabelY, MUTED_TEXT, false);
		graphics.text(this.font, Component.translatable("screen.attuned.reweaving_altar.hint.title"),
			14, 24, BODY_TEXT, false);
		Component hint = hint();
		int color = this.menu.hasAllInputs() && this.menu.outputStack().isEmpty() ? BODY_TEXT : WARNING_TEXT;
		graphics.text(this.font, hint, 14, 90, color, false);
	}

	private void refreshButtonState() {
		if (this.reweaveButton != null) {
			this.reweaveButton.active = this.menu.hasAllInputs() && this.menu.outputStack().isEmpty();
		}
	}

	private Component hint() {
		if (!this.menu.outputStack().isEmpty()) {
			return Component.translatable("screen.attuned.reweaving_altar.hint.output_blocked");
		}
		if (!hasThreeFoci()) {
			return Component.translatable("screen.attuned.reweaving_altar.hint.missing_foci");
		}
		if (!this.menu.container().getItem(ReweavingMenu.CATALYST_SLOT)
				.is(dev.attuned.content.AttunedContent.ATTUNEMENT_SHARD_FRAGMENT)) {
			return Component.translatable("screen.attuned.reweaving_altar.hint.missing_fragment");
		}
		return Component.translatable("screen.attuned.reweaving_altar.hint.ready");
	}

	private boolean hasThreeFoci() {
		for (int i = 0; i < ReweavingMenu.FOCUS_INPUTS; i++) {
			if (!dev.attuned.content.AttunedContent.FOCI.contains(this.menu.container().getItem(i).getItem())) {
				return false;
			}
		}
		return true;
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
			int face = this.active
				? (isHoveredOrFocused() ? 0xFF3D5C68 : 0xFF253942)
				: 0xFF24222A;
			int trim = this.active ? 0xFF70D7FF : 0xFF5F596A;
			graphics.fill(x0, y0, x1, y1, 0xFF15131B);
			graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, trim);
			graphics.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, face);
			graphics.fill(x0 + 3, y0 + 3, x1 - 3, y0 + 4, this.active ? 0xFFAEEAFF : 0xFF77707E);
			graphics.fill(x0 + 3, y1 - 4, x1 - 3, y1 - 3, 0xFF17151D);
			extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
		}
	}
}
