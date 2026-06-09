package dev.attuned.client.screen;

import dev.attuned.Attuned;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.FocusPreset;
import dev.attuned.menu.ApplyPresetPayload;
import dev.attuned.menu.DeletePresetPayload;
import dev.attuned.menu.SatchelMenu;
import dev.attuned.menu.SavePresetPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Focus Reliquary. Foci move by native drag-and-drop between the
 * reliquary grid, the equipped Focus column (left), and the player inventory.
 * Saved loadouts ("builds") render as a clickable list on the right; click a name
 * to select it, then Apply.
 */
public class SatchelScreen extends AbstractContainerScreen<SatchelMenu> {
	private static final Identifier BACKGROUND_TEXTURE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "textures/gui/satchel.png");
	private static final int IMAGE_WIDTH = 176;
	private static final int IMAGE_HEIGHT = 166;
	private static final int BODY_TEXT = 0xFFE3D8F5;
	private static final int LABEL_TEXT = 0xFFB8ACC8;
	private static final int SELECTED_TEXT = 0xFFFFD37A;
	private static final int SCREEN_BACKDROP = 0xB0101218;
	private static final int PANEL_FILL = 0xC01A1622;
	private static final int WELL_FILL = 0xFF0E0B14;
	private static final int WELL_EDGE = 0xFF3A3346;
	private static final int BUTTON_HOVER_ARGB = 0xC0FFFFFF;
	private static final int SELECTED_FILL_ARGB = 0x40FFD37A;

	// Equipped Focus column, mirroring SatchelMenu's equipped slot geometry.
	private static final int EQUIPPED_X = SatchelMenu.EQUIPPED_X;
	private static final int EQUIPPED_Y = SatchelMenu.EQUIPPED_Y;

	// Builds panel, drawn to the right of the reliquary window.
	private static final int BUILDS_X = 180;
	private static final int BUILDS_LABEL_Y = 6;
	private static final int BUILDS_LIST_Y = 18;
	private static final int BUILD_ROW_H = 11;
	private static final int BUILD_ROW_INNER_H = 10;
	private static final int BUILDS_W = 66;
	private static final int ACTION_H = 12;
	private static final int SAVE_Y = 120;
	private static final int APPLY_Y = 133;
	private static final int DELETE_Y = 146;

	private Button saveButton;
	private Button applyButton;
	private Button deleteButton;
	private final List<Button> buildButtons = new ArrayList<>();
	private String buildSignature = "";
	private int selectedIndex = -1;

	public SatchelScreen(SatchelMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelY = SatchelMenu.INVENTORY_Y - 10;
	}

	@Override
	protected void init() {
		super.init();
		this.saveButton = new PresetButton(this.leftPos + BUILDS_X, this.topPos + SAVE_Y, BUILDS_W, ACTION_H,
			Component.translatable("screen.attuned.preset.save"),
			button -> ClientPlayNetworking.send(new SavePresetPayload(nextPresetName())));
		this.applyButton = new PresetButton(this.leftPos + BUILDS_X, this.topPos + APPLY_Y, BUILDS_W, ACTION_H,
			Component.translatable("screen.attuned.preset.apply"),
			button -> ClientPlayNetworking.send(new ApplyPresetPayload(selectedIndex)));
		this.deleteButton = new PresetButton(this.leftPos + BUILDS_X, this.topPos + DELETE_Y, BUILDS_W, ACTION_H,
			Component.translatable("screen.attuned.preset.delete"),
			button -> ClientPlayNetworking.send(new DeletePresetPayload(selectedIndex)));
		this.addRenderableWidget(this.saveButton);
		this.addRenderableWidget(this.applyButton);
		this.addRenderableWidget(this.deleteButton);
		this.buildButtons.clear();
		this.buildSignature = "";
		refreshBuildButtons();
		refreshPresetState();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		if (!signatureOf(presets()).equals(buildSignature)) {
			refreshBuildButtons();
		}
		refreshPresetState();
	}

	/** Rebuilds one clickable button per saved build whenever the synced list changes. */
	private void refreshBuildButtons() {
		for (Button button : buildButtons) {
			this.removeWidget(button);
		}
		buildButtons.clear();
		List<FocusPreset> presets = presets();
		for (int i = 0; i < presets.size(); i++) {
			int index = i;
			String label = trimToWidth(presets.get(i).name(), BUILDS_W - 6);
			Button button = new PresetButton(this.leftPos + BUILDS_X, this.topPos + BUILDS_LIST_Y + i * BUILD_ROW_H,
				BUILDS_W, BUILD_ROW_INNER_H, Component.literal(label),
				ignored -> selectBuild(index), () -> selectedIndex == index);
			buildButtons.add(button);
			this.addRenderableWidget(button);
		}
		buildSignature = signatureOf(presets);
	}

	private void selectBuild(int index) {
		selectedIndex = index;
	}

	private void refreshPresetState() {
		List<FocusPreset> presets = presets();
		if (presets.isEmpty()) {
			selectedIndex = -1;
		} else if (selectedIndex < 0 || selectedIndex >= presets.size()) {
			selectedIndex = 0;
		}
		boolean hasSelection = selectedIndex >= 0 && selectedIndex < presets.size();
		if (this.saveButton != null) {
			this.saveButton.active = presets.size() < AttunedAttachments.MAX_PRESETS;
		}
		if (this.applyButton != null) {
			this.applyButton.active = hasSelection;
		}
		if (this.deleteButton != null) {
			this.deleteButton.active = hasSelection;
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, SCREEN_BACKDROP);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, this.leftPos, this.topPos,
			0.0F, 0.0F, IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);

		// Equipped Focus column (left of the window): a small backing panel plus a well per slot.
		graphics.fill(this.leftPos + EQUIPPED_X - 3, this.topPos + EQUIPPED_Y - 3,
			this.leftPos + EQUIPPED_X + 19, this.topPos + EQUIPPED_Y + 6 * 18 - 1, PANEL_FILL);
		for (int i = 0; i < 6; i++) {
			drawWell(graphics, this.leftPos + EQUIPPED_X - 1, this.topPos + EQUIPPED_Y - 1 + i * 18);
		}

		// Builds panel (right of the window).
		graphics.fill(this.leftPos + BUILDS_X - 3, this.topPos + BUILDS_LIST_Y - 3,
			this.leftPos + BUILDS_X + BUILDS_W + 3, this.topPos + DELETE_Y + ACTION_H + 3, PANEL_FILL);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, Component.translatable("screen.attuned.equipped"),
			EQUIPPED_X - 2, EQUIPPED_Y - 12, LABEL_TEXT, false);
		graphics.text(this.font, Component.translatable("screen.attuned.builds"),
			BUILDS_X, BUILDS_LABEL_Y, LABEL_TEXT, false);
		if (presets().isEmpty()) {
			graphics.text(this.font, Component.translatable("screen.attuned.builds.empty"),
				BUILDS_X, BUILDS_LIST_Y, LABEL_TEXT, false);
		}
	}

	private void drawWell(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fill(x, y, x + 18, y + 18, WELL_EDGE);
		graphics.fill(x + 1, y + 1, x + 17, y + 17, WELL_FILL);
	}

	private String nextPresetName() {
		Set<String> usedNames = new HashSet<>();
		for (FocusPreset preset : presets()) {
			usedNames.add(preset.name());
		}
		for (int i = 1; i <= AttunedAttachments.MAX_PRESETS; i++) {
			String name = i == 1 ? "Build" : "Build " + i;
			if (!usedNames.contains(name)) {
				return name;
			}
		}
		return "Build";
	}

	private List<FocusPreset> presets() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return List.of();
		}
		return AttunedAttachments.getPresets(this.minecraft.player);
	}

	private static String signatureOf(List<FocusPreset> presets) {
		StringBuilder builder = new StringBuilder();
		for (FocusPreset preset : presets) {
			builder.append(preset.name()).append(' ');
		}
		return builder.toString();
	}

	private String trimToWidth(String text, int maxWidth) {
		if (this.font.width(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "...";
		int ellipsisWidth = this.font.width(ellipsis);
		return this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - ellipsisWidth)) + ellipsis;
	}

	private static void drawButtonOutline(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int argb) {
		graphics.fill(x0, y0, x1, y0 + 1, argb);
		graphics.fill(x0, y1 - 1, x1, y1, argb);
		graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, argb);
		graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, argb);
	}

	private static final class PresetButton extends Button {
		private final BooleanSupplier selected;

		private PresetButton(int x, int y, int width, int height, Component message, OnPress onPress) {
			this(x, y, width, height, message, onPress, () -> false);
		}

		private PresetButton(int x, int y, int width, int height, Component message, OnPress onPress,
				BooleanSupplier selected) {
			super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
			this.selected = selected;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int x0 = getX();
			int y0 = getY();
			int x1 = x0 + getWidth();
			int y1 = y0 + getHeight();
			if (!this.active) {
				graphics.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, 0x99000000);
			} else if (this.selected.getAsBoolean()) {
				graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, SELECTED_FILL_ARGB);
				drawButtonOutline(graphics, x0, y0, x1, y1, SELECTED_TEXT);
			} else if (isHoveredOrFocused()) {
				drawButtonOutline(graphics, x0, y0, x1, y1, BUTTON_HOVER_ARGB);
			}
			extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
		}
	}
}
