package dev.attuned.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.attuned.Attuned;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.FocusPreset;
import dev.attuned.client.ClientNetworkPackets;
import dev.attuned.content.AttunedComponents;
import dev.attuned.menu.ApplyPresetPayload;
import dev.attuned.menu.BuildShareCodec;
import dev.attuned.menu.BuildPreviewResolver;
import dev.attuned.menu.DeletePresetPayload;
import dev.attuned.menu.FocusSlot;
import dev.attuned.menu.ImportPresetPayload;
import dev.attuned.menu.SatchelMenu;
import dev.attuned.menu.SavePresetPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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
	private static final ResourceLocation BACKGROUND_TEXTURE =
		new ResourceLocation(Attuned.MOD_ID, "textures/gui/satchel.png");
	// Logical window encloses the leather texture (left) plus the equipped/builds panel (right),
	// so the whole UI is centred and on-screen and no slot sits outside the click bounds. The
	// height grows with the reliquary's grid rows so the larger Grand tier stays inside bounds.
	private static final int IMAGE_WIDTH = 252;
	private static final int IMAGE_HEIGHT = 200;
	private static final int TEX_W = 176;
	private static final int TEX_H = 166;
	// Reliquary grid rows the leather texture already bakes wells for (the small tier).
	private static final int TEXTURE_GRID_ROWS = 3;
	// Texture v-extent through the header + 3 grid-row panel (the panel ends at y=73);
	// the Grand tier blits only this strip and panel-fills the taller body beneath it.
	private static final int TEX_GRID_BOTTOM = 74;
	private static final int GRID_X = 8;
	private static final int GRID_Y = 18;
	private static final int LABEL_TEXT = 0xFFB8ACC8;
	private static final int SELECTED_TEXT = 0xFFFFD37A;
	private static final int SCREEN_BACKDROP = 0xB0101218;
	private static final int WINDOW_FILL = 0xE01A1622;
	private static final int WELL_FILL = 0xFF0E0B14;
	private static final int WELL_EDGE = 0xFF3A3346;
	private static final int BUTTON_HOVER_ARGB = 0xC0FFFFFF;
	private static final int SELECTED_FILL_ARGB = 0x40FFD37A;
	private static final int NAME_FIELD_TEXT = 0xFFE3D8F5;
	// Greys a build-preview icon whose Focus cannot be sourced for an Apply.
	private static final int PREVIEW_MISSING_OVERLAY = 0xC81A1622;

	// Hover preview: the six saved Foci as item icons in a single row inside the
	// logical window, drawn to the LEFT of the hovered build button so the row
	// never escapes the 252x200 bounds the way the 70px-wide builds panel would.
	private static final int PREVIEW_SLOTS = AttunedInv.SIZE;
	private static final int PREVIEW_CELL = 18;
	private static final int PREVIEW_W = PREVIEW_SLOTS * PREVIEW_CELL;
	private static final int PREVIEW_PAD = 2;
	private static final int PREVIEW_GAP = 4;
	private static final int PREVIEW_PANEL_FILL = 0xF0120E1A;

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
	private static final int SHARE_IMPORT_Y = 94;
	private static final int BUILDS_LIST_Y = 106;
	private static final int BUILD_ROW_H = 10;
	private static final int BUILD_ROW_INNER_H = 9;
	private static final int ACTION_ROW_Y = 189;
	private static final int ACTION_H = 10;
	private static final int HALF_W = 34;

	private EditBox nameField;
	private Button saveButton;
	private Button shareButton;
	private Button importButton;
	private Button applyButton;
	private Button deleteButton;
	private final List<Button> buildButtons = new ArrayList<>();
	private String buildSignature = "";
	private int selectedIndex = -1;

	public SatchelScreen(SatchelMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = IMAGE_WIDTH;
		this.imageHeight = logicalHeight(menu);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelY = menu.inventoryY() - 10;
	}

	/**
	 * Logical window height: the small tier keeps its pinned 200px window; a larger
	 * grid (the Grand Reliquary's six rows) extends the window so the relocated player
	 * inventory and hotbar still sit inside the click/draw bounds.
	 */
	private static int logicalHeight(SatchelMenu menu) {
		// Player inventory (3 rows) + a gap + hotbar (1 row) below the reliquary grid,
		// then a small margin so the bottom slot stays inside the window.
		int inventoryBottom = menu.inventoryY() + 3 * 18 + 4 + 18;
		return Math.max(IMAGE_HEIGHT, inventoryBottom + 8);
	}

	@Override
	protected void init() {
		super.init();
		// The equipped Focus slots share FocusSlot's suppression flag with the survival
		// inventory panel; make sure they are active (clickable/drawn) in this screen.
		FocusSlot.setSuppressed(false);

		this.nameField = new EditBox(this.font, this.leftPos + BUILDS_X, this.topPos + NAME_Y, BUILDS_W, FIELD_H,
			new net.minecraft.network.chat.TranslatableComponent("screen.attuned.builds.name"));
		this.nameField.setMaxLength(32);
		this.nameField.setTextColor(NAME_FIELD_TEXT);
		this.nameField.setSuggestion(I18n.get("screen.attuned.builds.name_hint"));
		this.addRenderableWidget(this.nameField);

		this.saveButton = new PresetButton(this.leftPos + BUILDS_X, this.topPos + SAVE_Y, BUILDS_W, FIELD_H,
			new net.minecraft.network.chat.TranslatableComponent("screen.attuned.preset.save"), button -> saveBuild());
		this.shareButton = new PresetButton(this.leftPos + BUILDS_X, this.topPos + SHARE_IMPORT_Y, HALF_W, ACTION_H,
			new net.minecraft.network.chat.TranslatableComponent("screen.attuned.preset.share"), button -> shareBuild());
		this.importButton = new PresetButton(this.leftPos + BUILDS_X + HALF_W + 2, this.topPos + SHARE_IMPORT_Y, HALF_W, ACTION_H,
			new net.minecraft.network.chat.TranslatableComponent("screen.attuned.preset.import"), button -> importBuild());
		this.applyButton = new PresetButton(this.leftPos + BUILDS_X, this.topPos + ACTION_ROW_Y, HALF_W, ACTION_H,
			new net.minecraft.network.chat.TranslatableComponent("screen.attuned.preset.apply"), button -> applySelectedBuild());
		this.deleteButton = new PresetButton(this.leftPos + BUILDS_X + HALF_W + 2, this.topPos + ACTION_ROW_Y, HALF_W, ACTION_H,
			new net.minecraft.network.chat.TranslatableComponent("screen.attuned.preset.delete"), button -> deleteSelectedBuild());
		this.addRenderableWidget(this.saveButton);
		this.addRenderableWidget(this.shareButton);
		this.addRenderableWidget(this.importButton);
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
			ClientNetworkPackets.send(new ApplyPresetPayload(selectedIndex));
		}
	}

	private void deleteSelectedBuild() {
		List<FocusPreset> presets = presets();
		if (selectedIndex >= 0 && selectedIndex < presets.size()) {
			ClientNetworkPackets.send(new DeletePresetPayload(selectedIndex, presets.get(selectedIndex).name()));
		}
	}

	private void saveBuild() {
		String typed = this.nameField.getValue().trim();
		String name = typed.isEmpty() ? nextPresetName() : typed;
		ClientNetworkPackets.send(new SavePresetPayload(name));
		this.nameField.setValue("");
	}

	private void shareBuild() {
		List<FocusPreset> presets = presets();
		if (selectedIndex < 0 || selectedIndex >= presets.size()) {
			return;
		}
		FocusPreset preset = presets.get(selectedIndex);
		Minecraft minecraft = this.minecraft;
		if (minecraft == null) {
			return;
		}
		minecraft.keyboardHandler.setClipboard(BuildShareCodec.encode(preset));
		minecraft.gui.setOverlayMessage(new net.minecraft.network.chat.TranslatableComponent("screen.attuned.preset.shared", preset.name()), false);
	}

	private void importBuild() {
		Minecraft minecraft = this.minecraft;
		if (minecraft == null) {
			return;
		}
		Optional<FocusPreset> decoded = BuildShareCodec.decode(minecraft.keyboardHandler.getClipboard());
		if (decoded.isEmpty()) {
			minecraft.gui.setOverlayMessage(new net.minecraft.network.chat.TranslatableComponent("screen.attuned.preset.import_failed"), false);
			return;
		}
		FocusPreset preset = decoded.get();
		this.nameField.setValue(preset.name());
		ClientNetworkPackets.send(new ImportPresetPayload(preset));
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// ESC must always close the screen, even while the name field is focused.
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			return super.keyPressed(keyCode, scanCode, modifiers);
		}
		// Give the name field keyboard priority so typing (e.g. the inventory key 'e'
		// or a number) edits the build name instead of closing the screen or swapping hotbar.
		if (this.nameField != null
				&& (this.nameField.keyPressed(keyCode, scanCode, modifiers) || this.nameField.canConsumeInput())) {
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
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
				BUILDS_W, BUILD_ROW_INNER_H, new net.minecraft.network.chat.TextComponent(label),
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
		if (this.shareButton != null) {
			this.shareButton.active = hasSelection;
		}
		if (this.importButton != null) {
			this.importButton.active = presets.size() < AttunedAttachments.MAX_PRESETS;
		}
		if (this.applyButton != null) {
			this.applyButton.active = hasSelection;
		}
		if (this.deleteButton != null) {
			this.deleteButton.active = hasSelection;
		}
	}

	@Override
	protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
		GuiGraphics graphics = new GuiGraphics(Minecraft.getInstance(), poseStack);
		graphics.fill(0, 0, this.width, this.height, SCREEN_BACKDROP);
		// Solid window base so the right-hand panel reads as part of the window, then the
		// leather reliquary texture over the grid/inventory in the left half. The window
		// uses the actual (possibly taller) logical size for the Grand tier.
		graphics.fill(this.leftPos, this.topPos, this.leftPos + this.imageWidth, this.topPos + this.imageHeight, WINDOW_FILL);

		int rows = this.menu.satchelRows();
		boolean grand = rows > TEXTURE_GRID_ROWS;
		if (grand) {
			// The leather texture bakes wells for only a 3-row grid + a fixed inventory at
			// y=84. For the taller Grand grid those baked wells would ghost through, so blit
			// just the header + first 3 grid rows and panel-fill the rest, drawing every well
			// below the strip programmatically at the real (relocated) slot positions.
			graphics.blit(BACKGROUND_TEXTURE, this.leftPos, this.topPos,
				0.0F, 0.0F, TEX_W, TEX_GRID_BOTTOM, TEX_W, TEX_H);
			graphics.fill(this.leftPos, this.topPos + TEX_GRID_BOTTOM,
				this.leftPos + TEX_W, this.topPos + this.imageHeight, WINDOW_FILL);
		} else {
			graphics.blit(BACKGROUND_TEXTURE, this.leftPos, this.topPos,
				0.0F, 0.0F, TEX_W, TEX_H, TEX_W, TEX_H);
		}

		// Grid rows the leather texture does not bake wells for (the Grand tier's extra
		// rows below the small-satchel grid), drawn programmatically like the equipped grid.
		for (int row = TEXTURE_GRID_ROWS; row < rows; row++) {
			for (int col = 0; col < 9; col++) {
				drawWell(graphics, this.leftPos + GRID_X - 1 + col * 18, this.topPos + GRID_Y - 1 + row * 18);
			}
		}

		// The Grand tier relocates the player inventory + hotbar below its taller grid, so
		// the baked texture wells no longer line up — draw fresh wells at the real positions.
		if (grand) {
			int invY = this.menu.inventoryY();
			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 9; col++) {
					drawWell(graphics, this.leftPos + GRID_X - 1 + col * 18, this.topPos + invY - 1 + row * 18);
				}
			}
			int hotbarY = invY + 3 * 18 + 4;
			for (int col = 0; col < 9; col++) {
				drawWell(graphics, this.leftPos + GRID_X - 1 + col * 18, this.topPos + hotbarY - 1);
			}
		}

		for (int i = 0; i < 6; i++) {
			int col = i % EQUIPPED_COLS;
			int row = i / EQUIPPED_COLS;
			drawWell(graphics, this.leftPos + EQUIPPED_X - 1 + col * 18, this.topPos + EQUIPPED_Y - 1 + row * 18);
		}
	}

	@Override
	protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
		GuiGraphics graphics = new GuiGraphics(Minecraft.getInstance(), poseStack);
		graphics.drawString(this.font, new net.minecraft.network.chat.TranslatableComponent("screen.attuned.equipped"),
			EQUIPPED_X - 2, EQUIPPED_LABEL_Y, LABEL_TEXT, false);
		graphics.drawString(this.font, new net.minecraft.network.chat.TranslatableComponent("screen.attuned.builds"),
			BUILDS_X, BUILDS_LABEL_Y, LABEL_TEXT, false);
		if (presets().isEmpty()) {
			graphics.drawString(this.font, new net.minecraft.network.chat.TranslatableComponent("screen.attuned.builds.empty"),
				BUILDS_X, BUILDS_LIST_Y, LABEL_TEXT, false);
		}
		drawBuildPreview(graphics, mouseX, mouseY);
		drawBuildMetadataTooltip(graphics, mouseX, mouseY);
	}

	/**
	 * When a saved build name is hovered, paints its six Foci as item icons in a
	 * row to the LEFT of that build button, greying any slot whose Focus cannot be
	 * sourced for an Apply (same multiset rule as {@link BuildPreviewResolver}).
	 * Coordinates here are window-relative (the labels hook is pre-translated to
	 * {@code leftPos/topPos}); the row is clamped to the logical window so it never
	 * leaves the click/draw bounds.
	 */
	private void drawBuildPreview(GuiGraphics graphics, int mouseX, int mouseY) {
		int hovered = hoveredBuildIndex(mouseX, mouseY);
		if (hovered < 0) {
			return;
		}
		List<FocusPreset> presets = presets();
		if (hovered >= presets.size()) {
			return;
		}
		List<String> slots = presets.get(hovered).slots();
		List<BuildPreviewResolver.Availability> availability = BuildPreviewResolver.availability(
			slots, equippedIds(), satchelIds(), inventoryFocusCounts(), registeredFocusIds());

		int rowW = PREVIEW_W + PREVIEW_PAD * 2;
		int rowH = PREVIEW_CELL + PREVIEW_PAD * 2;
		// Anchor to the LEFT of the builds panel, vertically centred on the hovered
		// row, then clamp inside the window so the whole strip stays on-screen.
		int x = BUILDS_X - PREVIEW_GAP - rowW;
		int rowCentreY = BUILDS_LIST_Y + hovered * BUILD_ROW_H + BUILD_ROW_INNER_H / 2;
		int y = rowCentreY - rowH / 2;
		x = Math.max(2, Math.min(x, IMAGE_WIDTH - rowW - 2));
		y = Math.max(2, Math.min(y, IMAGE_HEIGHT - rowH - 2));

		graphics.fill(x, y, x + rowW, y + rowH, PREVIEW_PANEL_FILL);
		for (int slot = 0; slot < PREVIEW_SLOTS; slot++) {
			int cx = x + PREVIEW_PAD + slot * PREVIEW_CELL;
			int cy = y + PREVIEW_PAD;
			graphics.fill(cx, cy, cx + PREVIEW_CELL, cy + PREVIEW_CELL, WELL_EDGE);
			graphics.fill(cx + 1, cy + 1, cx + PREVIEW_CELL - 1, cy + PREVIEW_CELL - 1, WELL_FILL);

			String id = slot < slots.size() ? slots.get(slot) : "";
			if (id == null || id.isBlank()) {
				continue;
			}
			ItemStack stack = stackFor(id);
			if (stack.isEmpty()) {
				continue;
			}
			graphics.renderItem(stack, cx + 1, cy + 1);
			if (slot < availability.size()
					&& availability.get(slot) == BuildPreviewResolver.Availability.MISSING) {
				graphics.fill(cx + 1, cy + 1, cx + PREVIEW_CELL - 1, cy + PREVIEW_CELL - 1, PREVIEW_MISSING_OVERLAY);
			}
		}
	}

	/** Index of the build button currently under the cursor, or -1. */
	private int hoveredBuildIndex(int mouseX, int mouseY) {
		for (int i = 0; i < buildButtons.size(); i++) {
			Button button = buildButtons.get(i);
			if (button.active && button.isMouseOver(mouseX, mouseY)) {
				return i;
			}
		}
		return -1;
	}

	/** Resolves a saved Focus id to its item stack for the preview (client registry). */
	private static ItemStack stackFor(String id) {
		return BuiltInRegistries.ITEM.get(new ResourceLocation(FocusPreset.slotId(id))).getDefaultInstance();
	}

	private void drawBuildMetadataTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
		int hovered = hoveredBuildIndex(mouseX, mouseY);
		if (hovered < 0) {
			return;
		}
		List<FocusPreset> presets = presets();
		if (hovered >= presets.size()) {
			return;
		}
		List<Component> lines = buildMetadataTooltip(presets.get(hovered));
		if (!lines.isEmpty()) {
			List<FormattedCharSequence> visualLines =
				lines.stream().map(Component::getVisualOrderText).toList();
			this.renderTooltip(graphics.pose(), visualLines, mouseX - this.leftPos, mouseY - this.topPos);
		}
	}

	private static List<Component> buildMetadataTooltip(FocusPreset preset) {
		List<Component> lines = new ArrayList<>();
		if (!preset.role().isBlank()) {
			lines.add(new net.minecraft.network.chat.TranslatableComponent("screen.attuned.preset.metadata.role", preset.role()));
		}
		if (!preset.note().isBlank()) {
			lines.add(new net.minecraft.network.chat.TranslatableComponent("screen.attuned.preset.metadata.note", preset.note()));
		}
		if (preset.preferredPartySize() > 0) {
			lines.add(new net.minecraft.network.chat.TranslatableComponent("screen.attuned.preset.metadata.party_size", preset.preferredPartySize()));
		}
		for (String warning : preset.warnings()) {
			lines.add(new net.minecraft.network.chat.TranslatableComponent("screen.attuned.preset.metadata.warning", warning)
				.withStyle(ChatFormatting.YELLOW));
		}
		for (String required : preset.requires()) {
			lines.add(new net.minecraft.network.chat.TranslatableComponent("screen.attuned.preset.metadata.requires", required)
				.withStyle(ChatFormatting.YELLOW));
		}
		return lines;
	}

	private void drawWell(GuiGraphics graphics, int x, int y) {
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

	// Client-side preview sourcing reads the menu's synced slots so the pool exactly
	// matches what a server-side Apply would draw from: the six equipped slots, the
	// reliquary grid, and loose inventory Foci.
	private List<String> equippedIds() {
		List<String> ids = new ArrayList<>(AttunedInv.SIZE);
		for (int i = 0; i < AttunedInv.SIZE; i++) {
			ids.add(slotId(this.menu.equippedStart() + i));
		}
		return ids;
	}

	private List<String> satchelIds() {
		int size = this.menu.satchelSize();
		List<String> ids = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			ids.add(slotId(i));
		}
		return ids;
	}

	private Map<String, Integer> inventoryFocusCounts() {
		Map<String, Integer> counts = new HashMap<>();
		for (int index = this.menu.equippedEnd(); index < this.menu.slots.size(); index++) {
			Slot slot = this.menu.slots.get(index);
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String id = keyFor(stack);
			if (!id.isEmpty()) {
				counts.merge(id, stack.getCount(), Integer::sum);
			}
		}
		return counts;
	}

	private String slotId(int index) {
		if (index < 0 || index >= this.menu.slots.size()) {
			return "";
		}
		return keyFor(this.menu.slots.get(index).getItem());
	}

	private static String idFor(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static String keyFor(ItemStack stack) {
		return FocusPreset.slotKey(idFor(stack), AttunedComponents.isTempered(stack));
	}

	private static Set<String> registeredFocusIds() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft == null || minecraft.level == null) {
			return Set.of();
		}
		Registry<FocusDefinition> registry =
			minecraft.level.registryAccess().registryOrThrow(AttunedRegistries.FOCUS_DEFINITIONS);
		Set<String> ids = new HashSet<>();
		registry.stream()
			.map(definition -> BuiltInRegistries.ITEM.getKey(definition.item().value()).toString())
			.forEach(ids::add);
		return ids;
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

	private static void drawButtonOutline(GuiGraphics graphics, int x0, int y0, int x1, int y1, int argb) {
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
			super(x, y, width, height, message, onPress);
			this.selected = selected;
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
			} else if (this.selected.getAsBoolean()) {
				graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, SELECTED_FILL_ARGB);
				drawButtonOutline(graphics, x0, y0, x1, y1, SELECTED_TEXT);
			} else if (isHoveredOrFocused()) {
				drawButtonOutline(graphics, x0, y0, x1, y1, BUTTON_HOVER_ARGB);
			}
			GuiComponent.drawCenteredString(poseStack, Minecraft.getInstance().font, getMessage(),
				x0 + getWidth() / 2, y0 + (getHeight() - 8) / 2, LABEL_TEXT);
		}
	}
}
