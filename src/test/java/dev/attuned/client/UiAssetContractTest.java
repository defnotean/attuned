package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * File-level coverage for the custom UI art pass. These tests avoid booting the
 * client while still catching missing assets, accidental resize drift, and
 * renderers that stop referencing the shipped textures.
 */
class UiAssetContractTest {
	private static final Path TEXTURE_ROOT = Path.of("src/main/resources/assets/attuned/textures");
	private static final Path ALTAR_SCREEN_SOURCE =
		Path.of("src/client/java/dev/attuned/client/screen/AltarScreen.java");
	private static final Path REWEAVING_SCREEN_SOURCE =
		Path.of("src/client/java/dev/attuned/client/screen/ReweavingScreen.java");
	private static final Path ALTAR_MENU_SOURCE =
		Path.of("src/main/java/dev/attuned/menu/AltarMenu.java");
	private static final Path REWEAVING_MENU_SOURCE =
		Path.of("src/main/java/dev/attuned/menu/ReweavingMenu.java");
	private static final Path LANG_SOURCE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");
	private static final Path COMMAND_SOURCE =
		Path.of("src/main/java/dev/attuned/command/AttunedCommands.java");
	private static final Path FOCUS_PANEL_SOURCE =
		Path.of("src/client/java/dev/attuned/client/FocusPanel.java");
	private static final Path COMBAT_HUD_SOURCE =
		Path.of("src/client/java/dev/attuned/client/hud/CombatHud.java");
	private static final Path FOCI_HUD_SOURCE =
		Path.of("src/client/java/dev/attuned/client/hud/FociHud.java");

	@Test
	void customUiTexturesHaveStableMinecraftGuiDimensions() throws IOException {
		assertPngSize("gui/altar.png", 216, 190);
		assertPngSize("gui/altar_of_reweaving.png", 216, 190);
		assertPngSize("gui/focus_panel.png", 28, 124);
		assertPngSize("gui/foci_hud.png", 64, 96);
		assertPngSize("gui/hud_backplate.png", 50, 24);
		assertPngSize("gui/attunement_journal.png", 336, 214);
		assertPngSize("item/attunement_journal.png", 64, 64);
	}

	@Test
	void customUiTexturesStayWiredIntoTheirRenderers() throws IOException {
		assertSourceContains(ALTAR_SCREEN_SOURCE, "textures/gui/altar.png");
		assertSourceContains(REWEAVING_SCREEN_SOURCE, "textures/gui/altar_of_reweaving.png");
		assertSourceContains(FOCUS_PANEL_SOURCE, "textures/gui/focus_panel.png");
		assertSourceContains(FOCI_HUD_SOURCE, "textures/gui/foci_hud.png");
		assertSourceContains(COMBAT_HUD_SOURCE, "textures/gui/hud_backplate.png");
	}

	@Test
	void altarScreensUseTranslucentBackdropsBehindGeneratedPanels() throws IOException {
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int SCREEN_BACKDROP = 0xB0101218");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "graphics.fill(0, 0, this.width, this.height, SCREEN_BACKDROP)");
		assertSourceContains(REWEAVING_SCREEN_SOURCE, "private static final int SCREEN_BACKDROP = 0xB0101218");
		assertSourceContains(REWEAVING_SCREEN_SOURCE, "graphics.fill(0, 0, this.width, this.height, SCREEN_BACKDROP)");
	}

	@Test
	void generatedAltarScreensKeepInteractiveControlsInsidePaintedWells() throws IOException {
		assertSourceContains(ALTAR_SCREEN_SOURCE, "DETAIL_MAX_WIDTH = STATUS_MAX_WIDTH");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "Component.literal(dormant + \" dormant\")");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int READOUT_X = 24");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int TEXT_BOX_X = 18");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int HINT_Y = 88");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "drawTrimmedText(graphics, hint, TEXT_BOX_X, HINT_Y, HINT_MAX_WIDTH, hintColor)");
		assertSourceContains(ALTAR_MENU_SOURCE, "public static final int INPUT_WELL_WIDTH = 28");
		assertSourceContains(ALTAR_MENU_SOURCE, "public static final int INPUT_WELL_HEIGHT = 28");
		assertSourceContains(ALTAR_MENU_SOURCE, "public static final int INPUT_SLOT_VISUAL_OFFSET_Y = 2");
		assertSourceContains(ALTAR_MENU_SOURCE,
			"public static final int INPUT_SLOT_X = INPUT_WELL_X + (INPUT_WELL_WIDTH - SLOT_ITEM_SIZE) / 2");
		assertSourceContains(ALTAR_MENU_SOURCE,
			"public static final int INPUT_SLOT_Y = INPUT_WELL_Y + (INPUT_WELL_HEIGHT - SLOT_ITEM_SIZE) / 2 + INPUT_SLOT_VISUAL_OFFSET_Y");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int SLOT_WELL_X = AltarMenu.INPUT_WELL_X");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int SLOT_WELL_Y = AltarMenu.INPUT_WELL_Y");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int BUTTON_X = 147");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int BUTTON_Y = 42");
		assertSourceContains(REWEAVING_MENU_SOURCE, "private static final int PAINTED_INPUT_WELL_SIZE = 24");
		assertSourceContains(REWEAVING_MENU_SOURCE, "private static final int[] FOCUS_WELL_X = {26, 53, 80}");
		assertSourceContains(REWEAVING_MENU_SOURCE, "private static final int SLOT_VISUAL_OFFSET_Y = 2");
		assertSourceContains(REWEAVING_MENU_SOURCE, "private static final int OUTPUT_VISUAL_OFFSET_X = 2");
		assertSourceContains(REWEAVING_MENU_SOURCE, "private static final int OUTPUT_VISUAL_OFFSET_Y = 1");
		assertSourceContains(REWEAVING_MENU_SOURCE, "FOCUS_WELL_X[0] + SLOT_INSET");
		assertSourceContains(REWEAVING_MENU_SOURCE, "private static final int FOCUS_SLOT_Y = FOCUS_WELL_Y + SLOT_INSET + SLOT_VISUAL_OFFSET_Y");
		assertSourceContains(REWEAVING_MENU_SOURCE, "private static final int CATALYST_SLOT_X = CATALYST_WELL_X + SLOT_INSET");
		assertSourceContains(REWEAVING_MENU_SOURCE, "private static final int CATALYST_SLOT_Y = CATALYST_WELL_Y + SLOT_INSET + SLOT_VISUAL_OFFSET_Y");
		assertSourceContains(REWEAVING_MENU_SOURCE, "private static final int OUTPUT_SLOT_X = OUTPUT_WELL_X + OUTPUT_SLOT_INSET + OUTPUT_VISUAL_OFFSET_X");
		assertSourceContains(REWEAVING_MENU_SOURCE, "private static final int OUTPUT_SLOT_Y = OUTPUT_WELL_Y + OUTPUT_SLOT_INSET + OUTPUT_VISUAL_OFFSET_Y");
		assertSourceContains(REWEAVING_SCREEN_SOURCE, "private static final int BUTTON_X = 134");
		assertSourceContains(REWEAVING_SCREEN_SOURCE, "private static final int BUTTON_Y = 83");
	}

	@Test
	void reweavingReadyHintStaysCompactBesideTheButton() throws IOException {
		assertSourceContains(LANG_SOURCE, "\"screen.attuned.reweaving_altar.hint.ready\": \"Ready to reweave.\"");
	}

	@Test
	void attunedGuiPreviewCommandOpensRealMenusWithoutBlockSetup() throws IOException {
		assertSourceContains(COMMAND_SOURCE, "Commands.literal(\"gui\")");
		assertSourceContains(COMMAND_SOURCE, ".requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))");
		assertSourceContains(COMMAND_SOURCE, "Commands.literal(\"altar\")");
		assertSourceContains(COMMAND_SOURCE, "Commands.literal(\"reweaving\")");
		assertSourceContains(COMMAND_SOURCE, "openAltarGuiPreview");
		assertSourceContains(COMMAND_SOURCE, "openReweavingGuiPreview");
		assertSourceContains(COMMAND_SOURCE, "ContainerLevelAccess.NULL");
		assertSourceContains(COMMAND_SOURCE, "new AltarMenu(containerId, inventory, previewAltarInput(), ContainerLevelAccess.NULL)");
		assertSourceContains(COMMAND_SOURCE, "new ReweavingMenu(containerId, inventory, previewReweavingContainer(), ContainerLevelAccess.NULL)");
	}

	private static void assertPngSize(String relativePath, int width, int height) throws IOException {
		Path file = TEXTURE_ROOT.resolve(relativePath);
		assertTrue(Files.isRegularFile(file), "Missing UI texture: " + file);
		BufferedImage image = ImageIO.read(file.toFile());
		assertNotNull(image, "Texture should be a readable PNG: " + file);
		assertEquals(width, image.getWidth(), "Unexpected texture width: " + file);
		assertEquals(height, image.getHeight(), "Unexpected texture height: " + file);
	}

	private static void assertSourceContains(Path file, String needle) throws IOException {
		String source = Files.readString(file, StandardCharsets.UTF_8);
		assertTrue(source.contains(needle), file + " should reference " + needle);
	}
}
