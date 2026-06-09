package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SatchelScreenContractTest {
	private static final Path SCREEN = Path.of("src/client/java/dev/attuned/client/screen/SatchelScreen.java");
	private static final Path SCREENS = Path.of("src/client/java/dev/attuned/client/screen/AltarScreens.java");
	private static final Path TEXTURE = Path.of("src/main/resources/assets/attuned/textures/gui/satchel.png");
	private static final Path LANG = Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void satchelScreenUsesForkRenderHooksAndTexture() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("extends AbstractContainerScreen<SatchelMenu>"),
			"SatchelScreen should be a container screen over the satchel menu.");
		assertTrue(screen.contains("extractBackground(GuiGraphicsExtractor"),
			"SatchelScreen must use the fork background render hook, not vanilla renderBg.");
		assertTrue(screen.contains("extractLabels(GuiGraphicsExtractor"),
			"SatchelScreen must use the fork label render hook.");
		assertTrue(screen.contains("textures/gui/satchel.png"),
			"SatchelScreen must reference its background texture.");
		assertTrue(read(SCREENS).contains("MenuScreens.register(SatchelMenuType.TYPE, SatchelScreen::new)"),
			"The satchel screen must be registered against its menu type.");
	}

	@Test
	void satchelScreenExposesPresetActionsAndReadsSyncedPresets() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("new SavePresetPayload"), "Save button should send a SavePresetPayload.");
		assertTrue(screen.contains("new ApplyPresetPayload"), "Apply button should send an ApplyPresetPayload.");
		assertTrue(screen.contains("new DeletePresetPayload"), "Delete button should send a DeletePresetPayload.");
		assertTrue(screen.contains("ClientPlayNetworking.send"), "Preset buttons should send over client networking.");
		assertTrue(screen.contains("AttunedAttachments.getPresets"),
			"The screen should read presets from the synced attachment each frame (no caching).");
	}

	@Test
	void satchelScreenCanSendClickMovesToAndFromTheSelectedEquippedSlot() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("import dev.attuned.menu.MoveFocusPayload;"),
			"The satchel screen should wire the move payload it already has server support for.");
		assertTrue(screen.contains("private int selectedEquippedSlot = 0"),
			"The click mover should track which equipped Focus slot is targeted.");
		assertTrue(screen.contains("selectEquippedSlot("),
			"The screen should expose clickable equipped-slot selectors.");
		assertTrue(screen.contains("satchelSlotAt("),
			"Satchel clicks should resolve the clicked satchel slot before sending a move.");
		assertTrue(screen.contains("new MoveFocusPayload(0, satchelSlot, selectedEquippedSlot)"),
			"Primary-clicking a satchel slot should ask the server to equip that Focus.");
		assertTrue(screen.contains("new MoveFocusPayload(1, satchelSlot, selectedEquippedSlot)"),
			"Secondary-clicking a satchel slot should ask the server to store the selected equipped Focus.");
	}

	@Test
	void satchelSaveCreatesDistinctDefaultNamesUntilThePresetCap() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("new SavePresetPayload(nextPresetName())"),
			"Save should choose a live, unused default name instead of replacing the same preset every time.");
		assertTrue(screen.contains("private String nextPresetName()"),
			"Default preset naming should live in a small helper for contract coverage.");
		assertTrue(screen.contains("Set<String> usedNames"),
			"Preset naming should compare against the synced preset names.");
		assertTrue(screen.contains("AttunedAttachments.MAX_PRESETS"),
			"Preset naming should honor the shared preset cap.");
		assertTrue(screen.contains("\"Preset \" + i"),
			"Additional default presets should get numbered names.");
		assertFalse(screen.contains("new SavePresetPayload(\"Preset\")"),
			"The client should not always send the same preset name.");
	}

	@Test
	void satchelPresetControlsCanCycleAndDisplayTheSelectedPreset() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("cyclePreset(-1)"), "The screen should expose a previous-preset control.");
		assertTrue(screen.contains("cyclePreset(1)"), "The screen should expose a next-preset control.");
		assertTrue(screen.contains("Math.floorMod(selectedIndex + delta, presets.size())"),
			"Preset selection should wrap through every synced preset.");
		assertTrue(screen.contains("FocusPreset preset = presets.get(selectedIndex)"),
			"The compact readout should draw the selected preset, not always index zero.");
		assertTrue(screen.contains("selectedIndex + 1") && screen.contains("presets.size()"),
			"The readout should show which preset index is selected.");
	}

	@Test
	void presetReadoutStaysInSeparatorStripBelowSatchelSlots() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("private static final int PRESET_Y = 74"),
			"Preset text should sit in the separator strip, below the satchel slot rows.");
		assertTrue(screen.contains("int rows = Math.min(1, presets.size())"),
			"Compact satchel GUI should draw one selected preset row so it does not collide with inventory slots.");
	}

	@Test
	void topActionRowDoesNotAlsoPaintTheScreenTitle() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("private static final int BUTTON_Y = 4"),
			"The compact satchel texture reserves the top strip for preset action buttons.");
		assertFalse(screen.contains("graphics.text(this.font, this.title"),
			"The satchel title should not paint underneath the Save/Apply/Delete buttons in the compact top strip.");
	}

	@Test
	void satchelTextureHasPinnedSize() throws IOException {
		assertTrue(Files.isRegularFile(TEXTURE), "Satchel GUI texture must exist.");
		BufferedImage image = ImageIO.read(TEXTURE.toFile());
		assertNotNull(image, "Satchel GUI texture must be a readable PNG.");
		assertEquals(176, image.getWidth(), "Satchel texture width must be pinned.");
		assertEquals(166, image.getHeight(), "Satchel texture height must be pinned.");
	}

	@Test
	void presetLangKeysExist() throws IOException {
		JsonObject lang = JsonParser.parseString(read(LANG)).getAsJsonObject();
		assertTrue(lang.has("screen.attuned.preset.save"), "Save label.");
		assertTrue(lang.has("screen.attuned.preset.apply"), "Apply label.");
		assertTrue(lang.has("screen.attuned.preset.delete"), "Delete label.");
		assertTrue(lang.has("screen.attuned.preset.missing"), "Missing-focus feedback string.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
