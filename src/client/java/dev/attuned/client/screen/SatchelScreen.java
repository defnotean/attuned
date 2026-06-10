package dev.attuned.client.screen;

import dev.attuned.Attuned;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.FocusPreset;
import dev.attuned.menu.ApplyPresetPayload;
import dev.attuned.menu.DeletePresetPayload;
import dev.attuned.menu.FocusSlot;
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
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/**
 * Screen for the Focus Reliquary. The reliquary grid and inventory render under
 * the leather texture (left); the equipped Focus slots (a 3x2 grid) and the saved
 * "builds" panel render in the right half of the window. Every slot stays inside
 * the window bounds so Foci move by native drag, click-to-grab/drop, or shift-click
 * without ever being dropped on the ground. Type a name, Save, then click a build
 * to select and Apply it.
 */
public class SatchelScreen extends AbstractContainerScreen<SatchelMenu> {
	private static final Identifier BACKGROUND_TEXTURE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "textures/gui/satchel.png");
	// Logical window encloses the leather texture (left) plus the equipped/builds panel (right),
	// so the whole UI is centred and on-screen and no slot sits outside the click bounds.
	private static final int IMAGE_WIDTH = 252;
	private static final int IMAGE_HEIGHT = 200;
	private static final int TEX_W = 176;
	private static final int TEX_H = 166;
	private static final int LABEL_TEXT = 0xFFB8ACC8;
	private static final int SELECTED_TEXT = 0xFFFFD37A;
	private static final int SCREEN_BACKDROP = 0xB0101218;
	private static final int WINDOW_FILL = 0xE01A1622;
	private static final int WELL_FILL = 0xFF0E0B14;
	private static final int WELL_EDGE = 0xFF3A3346;
	private static final int BUTTON_HOVER_ARGB = 0xC0FFFFFF;
	private static final int SELECTED_FILL_ARGB = 0x40FFD37A;
	private static final int NAME_FIELD_TEXT = 0xFFE3D8F5;

	// Equipped Focus 3x2 grid, mirroring SatchelMenu's equipped slot geometry.
	private static final int EQUIPPED_X = SatchelMenu.EQUIPPED_X;
	private static final int EQUIPPED_Y = SatchelMenu.EQUIPPED_Y;
	private static final int EQUIPPED_COLS = SatchelMenu.EQUIPPED_COLS;

	// Builds panel (right half of the window).
	private static final int EQUIPPED_LABEL_Y = 8;
	private static final int BUILDS_X = 178;
	private static final int BUILDS_W = 70;
	private static final int BUILDS_LABEL_Y = 56;
	private static final int NAME_Y = 66;
	private static final int FIELD_H = 12;
	private static final int SAVE_Y = 82;
	private static final int BUILDS_LIST_Y = 98;
	private static final int BUILD_ROW_H = 10;
	private static final int BUILD_ROW_INNER_H = 9;
	private static final int ACTION_ROW_Y = 189;
	private static final int ACTION_H = 10;
	private static final int HALF_W = 34;

	private EditBox nameField;
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
		// The equipped Focus slots share FocusSlot's suppression flag with the survival
		// inventory panel; make sure they are active (clickable/drawn) in this screen.
		FocusSlot.setSuppressed(false);

		this.nameField = new EditBox(this.font, this.leftPos + BUILDS_X, this.topPos + NAME_Y, BUILDS_W, FIELD_H,
			Component.translatable("screen.attuned.builds.name"));
		this.nameField.setMaxLength(32);
		this.nameField.setTextColor(NAME_FIELD_TEXT);
		this.nameField.setHint(Component.translatable("screen.attuned.builds.name_hint"));
		this.addRenderableWidget(this.nameField);

		this.saveButton = new PresetButton(this.leftPos + BUILDS_X, this.topPos + SAVE_Y, BUILDS_W, FIELD_H,
			Component.translatable("screen.attuned.preset.save"), button -> saveBuild());
		this.applyButton = new PresetButton(this.leftPos + BUILDS_X, this.topPos + ACTION_ROW_Y, HALF_W, ACTION_H,
			Component.translatable("screen.attuned.preset.apply"), button -> applySelectedBuild());
		this.deleteButton = new PresetButton(this.leftPos + BUILDS_X + HALF_W + 2, this.topPos + ACTION_ROW_Y, HALF_W, ACTION_H,
			Component.translatable("screen.attuned.preset.delete"), button -> deleteSelectedBuild());
		this.addRenderableWidget(this.saveButton);
		this.addRenderableWidget(this.applyButton);
		this.addRenderableWidget(this.deleteButton);

		this.buildButtons.clear();
		this.buildSignature = "";
		refreshBuildButtons();
		refreshPresetState();
	}

	private void applySelectedBuild() {
		// Re-validate against the live synced list: a sync packet can shrink it between
		// the tick that enabled this button and the click being processed.
		if (selectedIndex >= 0 && selectedIndex < presets().size()) {
			ClientPlayNetworking.send(new ApplyPresetPayload(selectedIndex));
		}
	}

	private void deleteSelectedBuild() {
		List<FocusPreset> presets = presets();
		if (selectedIndex >= 0 && selectedIndex < presets.size()) {
			ClientPlayNetworking.send(new DeletePresetPayload(selectedIndex, presets.get(selectedIndex).name()));
		}
	}

	private void saveBuild() {
		String typed = this.nameField.getValue().trim();
		String name = typed.isEmpty() ? nextPresetName() : typed;
		ClientPlayNetworking.send(new SavePresetPayload(name));
		this.nameField.setValue("");
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// ESC must always close the screen, even while the name field is focused.
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			return super.keyPressed(event);
		}
		// Give the name field keyboard priority so typing (e.g. the inventory key 'e'
		// or a number) edits the build name instead of closing the screen or swapping hotbar.
		if (this.nameField != null
				&& (this.nameField.keyPressed(event) || this.nameField.canConsumeInput())) {
			return true;
		}
		return super.keyPressed(event);
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
		if (selectedIndex >= presets.size()) {
			// The selected build was deleted (or the list shrank): clear the selection
			// instead of silently retargeting Apply/Delete at another build.
			selectedIndex = -1;
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
		// Solid window base so the right-hand panel reads as part of the window, then the
		// leather reliquary texture over the grid/inventory in the left half.
		graphics.fill(this.leftPos, this.topPos, this.leftPos + IMAGE_WIDTH, this.topPos + IMAGE_HEIGHT, WINDOW_FILL);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, this.leftPos, this.topPos,
			0.0F, 0.0F, TEX_W, TEX_H, TEX_W, TEX_H);

		for (int i = 0; i < 6; i++) {
			int col = i % EQUIPPED_COLS;
			int row = i / EQUIPPED_COLS;
			drawWell(graphics, this.leftPos + EQUIPPED_X - 1 + col * 18, this.topPos + EQUIPPED_Y - 1 + row * 18);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, Component.translatable("screen.attuned.equipped"),
			EQUIPPED_X - 2, EQUIPPED_LABEL_Y, LABEL_TEXT, false);
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
		// '\0' cannot be typed in the name field, so joining with it (plus the count)
		// cannot collide the way a space-joined signature could ("A B" vs "A","B").
		StringBuilder builder = new StringBuilder().append(presets.size());
		for (FocusPreset preset : presets) {
			builder.append('\0').append(preset.name());
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
