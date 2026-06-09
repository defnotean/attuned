package dev.attuned.client.screen;

import dev.attuned.Attuned;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.FocusPreset;
import dev.attuned.content.AttunedComponents;
import dev.attuned.menu.ApplyPresetPayload;
import dev.attuned.menu.DeletePresetPayload;
import dev.attuned.menu.MoveFocusPayload;
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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

/** Screen for the Satchel of Foci. */
public class SatchelScreen extends AbstractContainerScreen<SatchelMenu> {
	private static final Identifier BACKGROUND_TEXTURE =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "textures/gui/satchel.png");
	private static final int IMAGE_WIDTH = 176;
	private static final int IMAGE_HEIGHT = 166;
	private static final int TITLE_TEXT = 0xFFEDE6FF;
	private static final int BODY_TEXT = 0xFFE3D8F5;
	private static final int MUTED_TEXT = 0xFFB8ACC8;
	private static final int SELECTED_TEXT = 0xFFFFD37A;
	private static final int SCREEN_BACKDROP = 0xB0101218;
	private static final int BUTTON_HOVER_ARGB = 0xC0FFFFFF;
	private static final int SELECTED_SLOT_OUTLINE_ARGB = 0xFFFFD37A;
	private static final int SELECTED_SLOT_FILL_ARGB = 0x40FFD37A;
	private static final int BUTTON_Y = 4;
	private static final int BUTTON_W = 48;
	private static final int BUTTON_H = 14;
	private static final int PRESET_X = 8;
	private static final int PRESET_Y = 74;
	private static final int PRESET_ROW_H = 10;
	private static final int PRESET_NAV_Y = PRESET_Y - 2;
	private static final int PRESET_NAV_W = 14;
	private static final int PRESET_NAV_H = 12;
	private static final int EQUIPPED_SELECTOR_X = 24;
	private static final int EQUIPPED_SELECTOR_W = 10;
	private static final int EQUIPPED_SELECTOR_H = 12;
	private static final int EQUIPPED_SELECTOR_GAP = 1;
	private static final int PRESET_TEXT_X = 94;
	private static final int PRESET_INDEX_X = 132;
	private static final int PRESET_NEXT_X = 154;
	private static final int PRESET_MAX_WIDTH = 36;

	private Button saveButton;
	private Button applyButton;
	private Button deleteButton;
	private Button previousPresetButton;
	private Button nextPresetButton;
	private final List<Button> equippedSlotButtons = new ArrayList<>();
	private int selectedIndex = -1;
	private int selectedEquippedSlot = 0;

	public SatchelScreen(SatchelMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelY = SatchelMenu.INVENTORY_Y - 10;
	}

	@Override
	protected void init() {
		super.init();
		this.saveButton = new PresetButton(this.leftPos + 24, this.topPos + BUTTON_Y,
			Component.translatable("screen.attuned.preset.save"),
			button -> ClientPlayNetworking.send(new SavePresetPayload(nextPresetName())));
		this.applyButton = new PresetButton(this.leftPos + 74, this.topPos + BUTTON_Y,
			Component.translatable("screen.attuned.preset.apply"),
			button -> ClientPlayNetworking.send(new ApplyPresetPayload(selectedIndex)));
		this.deleteButton = new PresetButton(this.leftPos + 124, this.topPos + BUTTON_Y,
			Component.translatable("screen.attuned.preset.delete"),
			button -> ClientPlayNetworking.send(new DeletePresetPayload(selectedIndex)));
		this.previousPresetButton = new PresetButton(this.leftPos + PRESET_X, this.topPos + PRESET_NAV_Y,
			PRESET_NAV_W, PRESET_NAV_H, Component.literal("<"), button -> cyclePreset(-1));
		this.nextPresetButton = new PresetButton(this.leftPos + PRESET_NEXT_X, this.topPos + PRESET_NAV_Y,
			PRESET_NAV_W, PRESET_NAV_H, Component.literal(">"), button -> cyclePreset(1));
		this.addRenderableWidget(this.saveButton);
		this.addRenderableWidget(this.applyButton);
		this.addRenderableWidget(this.deleteButton);
		this.addRenderableWidget(this.previousPresetButton);
		this.addRenderableWidget(this.nextPresetButton);
		this.equippedSlotButtons.clear();
		for (int slot = 0; slot < AttunedInv.SIZE; slot++) {
			int targetSlot = slot;
			Button selector = new PresetButton(
				this.leftPos + EQUIPPED_SELECTOR_X + slot * (EQUIPPED_SELECTOR_W + EQUIPPED_SELECTOR_GAP),
				this.topPos + PRESET_NAV_Y,
				EQUIPPED_SELECTOR_W,
				EQUIPPED_SELECTOR_H,
				Component.literal(Integer.toString(slot + 1)),
				button -> selectEquippedSlot(targetSlot),
					() -> selectedEquippedSlot == targetSlot);
			this.equippedSlotButtons.add(selector);
			this.addRenderableWidget(selector);
		}
		refreshPresetState();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		refreshPresetState();
	}

	private void refreshPresetState() {
		List<FocusPreset> presets = presets();
		if (presets.isEmpty()) {
			selectedIndex = -1;
		} else if (selectedIndex < 0 || selectedIndex >= presets.size()) {
			selectedIndex = 0;
		}
		if (this.saveButton != null) {
			this.saveButton.active = presets.size() < AttunedAttachments.MAX_PRESETS;
		}
		boolean hasSelection = selectedIndex >= 0 && selectedIndex < presets.size();
		if (this.applyButton != null) {
			this.applyButton.active = hasSelection;
		}
		if (this.deleteButton != null) {
			this.deleteButton.active = hasSelection;
		}
		boolean canCycle = presets.size() > 1;
		if (this.previousPresetButton != null) {
			this.previousPresetButton.active = canCycle;
		}
		if (this.nextPresetButton != null) {
			this.nextPresetButton.active = canCycle;
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, SCREEN_BACKDROP);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, this.leftPos, this.topPos,
			0.0F, 0.0F, IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();
		int satchelSlot = satchelSlotAt(mouseX, mouseY);
		if (satchelSlot >= 0) {
			if (button == 0 && (event.modifiers() & GLFW.GLFW_MOD_SHIFT) == 0) {
				ClientPlayNetworking.send(new MoveFocusPayload(0, satchelSlot, selectedEquippedSlot));
				return true;
			}
			if (button == 1) {
				ClientPlayNetworking.send(new MoveFocusPayload(1, satchelSlot, selectedEquippedSlot));
				return true;
			}
			// Shift+left-click is intentionally not intercepted: it falls through to the
			// vanilla quick-move path (SatchelMenu.quickMoveStack), which returns the
			// focus from the satchel back to the player's regular inventory.
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<FocusPreset> presets = presets();
		if (presets.isEmpty()) {
			drawTrimmedText(graphics, Component.translatable("screen.attuned.satchel.empty"),
				PRESET_TEXT_X, PRESET_Y, PRESET_MAX_WIDTH, MUTED_TEXT);
			return;
		}
		if (selectedIndex < 0 || selectedIndex >= presets.size()) {
			selectedIndex = 0;
		}
		int rows = Math.min(1, presets.size());
		if (rows > 0) {
			FocusPreset preset = presets.get(selectedIndex);
			drawTrimmedText(graphics, Component.literal(preset.name()),
				PRESET_TEXT_X, PRESET_Y, PRESET_MAX_WIDTH, SELECTED_TEXT);
			graphics.text(this.font, Component.literal((selectedIndex + 1) + "/" + presets.size()),
				PRESET_INDEX_X, PRESET_Y, BODY_TEXT, false);
		}
	}

	private void selectEquippedSlot(int slot) {
		selectedEquippedSlot = Math.max(0, Math.min(AttunedInv.SIZE - 1, slot));
	}

	private int satchelSlotAt(double mouseX, double mouseY) {
		int limit = Math.min(AttunedComponents.SATCHEL_SIZE, this.menu.slots.size());
		for (int i = 0; i < limit; i++) {
			Slot slot = this.menu.slots.get(i);
			int x = this.leftPos + slot.x;
			int y = this.topPos + slot.y;
			if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
				return i;
			}
		}
		return -1;
	}

	private void cyclePreset(int delta) {
		List<FocusPreset> presets = presets();
		if (presets.isEmpty()) {
			selectedIndex = -1;
			refreshPresetState();
			return;
		}
		if (selectedIndex < 0 || selectedIndex >= presets.size()) {
			selectedIndex = 0;
		}
		selectedIndex = Math.floorMod(selectedIndex + delta, presets.size());
		refreshPresetState();
	}

	private String nextPresetName() {
		Set<String> usedNames = new HashSet<>();
		for (FocusPreset preset : presets()) {
			usedNames.add(preset.name());
		}
		for (int i = 1; i <= AttunedAttachments.MAX_PRESETS; i++) {
			String name = i == 1 ? "Preset" : "Preset " + i;
			if (!usedNames.contains(name)) {
				return name;
			}
		}
		return "Preset";
	}

	private List<FocusPreset> presets() {
		if (this.minecraft == null || this.minecraft.player == null) {
			return List.of();
		}
		return AttunedAttachments.getPresets(this.minecraft.player);
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

	private static final class PresetButton extends Button {
		private final BooleanSupplier selected;

		private PresetButton(int x, int y, Component message, OnPress onPress) {
			this(x, y, BUTTON_W, BUTTON_H, message, onPress, () -> false);
		}

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
				graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, SELECTED_SLOT_FILL_ARGB);
				drawButtonOutline(graphics, x0, y0, x1, y1, SELECTED_SLOT_OUTLINE_ARGB);
			} else if (isHoveredOrFocused()) {
				drawButtonOutline(graphics, x0, y0, x1, y1, BUTTON_HOVER_ARGB);
			}
			extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
		}
	}
}
