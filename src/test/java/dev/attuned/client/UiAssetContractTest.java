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
	private static final Path FOCUS_PANEL_SOURCE =
		Path.of("src/client/java/dev/attuned/client/FocusPanel.java");
	private static final Path COMBAT_HUD_SOURCE =
		Path.of("src/client/java/dev/attuned/client/hud/CombatHud.java");

	@Test
	void customUiTexturesHaveStableMinecraftGuiDimensions() throws IOException {
		assertPngSize("gui/altar.png", 216, 190);
		assertPngSize("gui/focus_panel.png", 28, 124);
		assertPngSize("gui/hud_backplate.png", 50, 24);
		assertPngSize("item/attunement_journal.png", 16, 16);
	}

	@Test
	void customUiTexturesStayWiredIntoTheirRenderers() throws IOException {
		assertSourceContains(ALTAR_SCREEN_SOURCE, "textures/gui/altar.png");
		assertSourceContains(FOCUS_PANEL_SOURCE, "textures/gui/focus_panel.png");
		assertSourceContains(COMBAT_HUD_SOURCE, "textures/gui/hud_backplate.png");
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
