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
	private static final Path GUI_PREVIEW_RENDERER =
		Path.of("tools/render_gui_previews.py");
	private static final Path UI_ART_GENERATOR =
		Path.of("tools/generate_ui_art.py");
	private static final Path GUI_FIXTURES =
		Path.of("tools/asset_customizer/gui-fixtures.json");
	private static final Path FOCUS_PANEL_SOURCE =
		Path.of("src/client/java/dev/attuned/client/FocusPanel.java");
	private static final Path ATTUNEMENT_READOUT_SOURCE =
		Path.of("src/client/java/dev/attuned/client/AttunementReadout.java");
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
		assertSourceContains(ALTAR_SCREEN_SOURCE, "Component.literal(readout.dormant() + \" dormant\")");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int READOUT_X = 24");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int TEXT_BOX_X = 18");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int HINT_Y = 88");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "drawTrimmedText(graphics, hint, TEXT_BOX_X, HINT_Y, HINT_MAX_WIDTH, hintColor)");
		assertSourceContains(ALTAR_MENU_SOURCE, "public static final int INPUT_WELL_X = 127");
		assertSourceContains(ALTAR_MENU_SOURCE, "public static final int INPUT_WELL_Y = 33");
		assertSourceContains(ALTAR_MENU_SOURCE, "public static final int INPUT_WELL_WIDTH = 20");
		assertSourceContains(ALTAR_MENU_SOURCE, "public static final int INPUT_WELL_HEIGHT = 20");
		assertSourceContains(ALTAR_MENU_SOURCE,
			"public static final int INPUT_SLOT_X = INPUT_WELL_X + (INPUT_WELL_WIDTH - SLOT_ITEM_SIZE) / 2");
		assertSourceContains(ALTAR_MENU_SOURCE,
			"public static final int INPUT_SLOT_Y = INPUT_WELL_Y + (INPUT_WELL_HEIGHT - SLOT_ITEM_SIZE) / 2");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int SLOT_WELL_X = AltarMenu.INPUT_WELL_X");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int SLOT_WELL_Y = AltarMenu.INPUT_WELL_Y");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int BUTTON_X = 151");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int BUTTON_Y = 31");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int BUTTON_W = 50");
		assertSourceContains(ALTAR_SCREEN_SOURCE, "private static final int BUTTON_H = 20");
		assertSourceContains(GUI_FIXTURES,
			"{\"name\": \"Bind button\", \"x\": 151, \"y\": 31, \"w\": 50, \"h\": 20}");
		assertSourceContains(UI_ART_GENERATOR, "bevel(draw, 151, 31, 50, 20");
		assertSourceContains(REWEAVING_MENU_SOURCE, "private static final int PAINTED_INPUT_WELL_SIZE = 24");
		assertSourceContains(REWEAVING_MENU_SOURCE, "private static final int[] FOCUS_WELL_X = {26, 53, 80}");
		assertSourceContains(REWEAVING_MENU_SOURCE, "private static final int SLOT_VISUAL_OFFSET_Y = 0");
		assertSourceContains(REWEAVING_MENU_SOURCE, "private static final int OUTPUT_VISUAL_OFFSET_X = 0");
		assertSourceContains(REWEAVING_MENU_SOURCE, "private static final int OUTPUT_VISUAL_OFFSET_Y = 0");
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
	void budgetBarsClampFillInsideTheirTracks() throws IOException {
		String readout = Files.readString(ATTUNEMENT_READOUT_SOURCE, StandardCharsets.UTF_8);
		String focusPanel = Files.readString(FOCUS_PANEL_SOURCE, StandardCharsets.UTF_8);
		String altarScreen = Files.readString(ALTAR_SCREEN_SOURCE, StandardCharsets.UTF_8);

		assertTrue(readout.contains("public static int budgetFillWidth(int trackWidth, int used, int capacity)"),
			"Budget bar fill clamping should live in a shared presentation helper.");
		assertTrue(readout.contains("if (trackWidth <= 0 || used <= 0 || capacity <= 0)"),
			"Budget bar fill should reject empty tracks, unused budget, and zero capacity.");
		assertTrue(readout.contains(
			"Math.min(trackWidth, Math.max(1, Math.round(trackWidth * Math.min(1.0F, used / (float) capacity))))"),
			"Budget bar fill should never exceed its painted track width.");
		assertTrue(focusPanel.contains("AttunementReadout.budgetFillWidth(barX1 - barX0, used, capacity)"),
			"The Focus panel budget bar should use the shared clamped fill helper.");
		assertTrue(altarScreen.contains("AttunementReadout.budgetFillWidth(BAR_W, used, capacity)"),
			"The Altar screen budget bar should use the shared clamped fill helper.");
	}

	@Test
	void reweavingReadyHintStaysCompactBesideTheButton() throws IOException {
		assertSourceContains(LANG_SOURCE, "\"screen.attuned.reweaving_altar.hint.ready\": \"Ready to reweave.\"");
	}

	@Test
	void offlineGuiPreviewRendererCoversMenusWithoutLaunchingMinecraft() throws IOException {
		assertSourceContains(GUI_PREVIEW_RENDERER, "gui-fixtures.json");
		assertSourceContains(GUI_PREVIEW_RENDERER, "build/gui-previews");
		assertSourceContains(GUI_PREVIEW_RENDERER, "render_fixture");
		assertSourceContains(GUI_PREVIEW_RENDERER, "draw_slot_overlay");
		assertSourceContains(GUI_PREVIEW_RENDERER, "draw_sample_items");
	}

	@Test
	void uiArtGeneratorOwnsEveryGuiTextureUsedByOfflinePreviews() throws IOException {
		String generator = Files.readString(UI_ART_GENERATOR, StandardCharsets.UTF_8);
		assertTrue(generator.contains("def altar():"),
			"Generator should own the Attunement Altar GUI texture.");
		assertTrue(generator.contains("def reweaving_gui():"),
			"Generator should own the Altar of Reweaving GUI texture.");
		assertTrue(generator.contains("def satchel_gui():"),
			"Generator should own the Satchel of Foci GUI texture.");
		assertTrue(generator.contains("def journal_gui():"),
			"Generator should own the custom Attunement Journal GUI texture.");
		assertTrue(generator.contains("TEXTURES / \"gui/altar.png\""));
		assertTrue(generator.contains("TEXTURES / \"gui/altar_of_reweaving.png\""));
		assertTrue(generator.contains("TEXTURES / \"gui/satchel.png\""));
		assertTrue(generator.contains("TEXTURES / \"gui/attunement_journal.png\""));
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
