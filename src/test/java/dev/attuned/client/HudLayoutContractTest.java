package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * HUD layout config: the Foci HUD anchors through the shared {@code HudAnchor}
 * helper driven by {@code attuned-client.json}. The default layout
 * (bottom_right, no offset, scale 1) must reproduce the historical hard-coded
 * placement exactly so existing installs see no movement.
 */
class HudLayoutContractTest {
	private static final Path CONFIG =
		Path.of("src/client/java/dev/attuned/client/AttunedClientConfig.java");
	private static final Path ANCHOR =
		Path.of("src/client/java/dev/attuned/client/hud/HudAnchor.java");
	private static final Path FOCI_HUD =
		Path.of("src/client/java/dev/attuned/client/hud/FociHud.java");
	private static final Path COMBAT_HUD =
		Path.of("src/client/java/dev/attuned/client/hud/CombatHud.java");

	@Test
	void anchorHelperReproducesTheHistoricalDefaultPlacement() throws IOException {
		String anchor = read(ANCHOR);
		assertTrue(anchor.contains("SCREEN_MARGIN = 4"),
			"The anchor margin must match the historical 4px screen margin.");
		assertTrue(anchor.contains("screenW - width - SCREEN_MARGIN"),
			"A right anchor must resolve to the historical right-edge expression.");
		assertTrue(anchor.contains("screenH - height - SCREEN_MARGIN"),
			"A bottom anchor must resolve to the historical bottom-edge expression.");
		assertTrue(anchor.contains("layout.anchor().left()") && anchor.contains("layout.anchor().top()"),
			"Left/top anchors should flip the base corner.");
		assertTrue(anchor.contains("Math.max(SCREEN_MARGIN, Math.min(max, position))"),
			"Offsets must clamp so the HUD can never leave the screen.");
	}

	@Test
	void fociHudResolvesPlacementAndScaleThroughTheLayout() throws IOException {
		String hud = read(FOCI_HUD);
		assertTrue(hud.contains("HudAnchor.layout()"),
			"The Foci HUD should read the configured layout.");
		assertTrue((hud.contains("graphics.pose().pushMatrix()") && hud.contains("graphics.pose().scale(scale, scale)"))
				|| (hud.contains("graphics.pose().pushPose()") && hud.contains("graphics.pose().scale(scale, scale, 1.0F)")),
			"A non-default scale should wrap the draw in a pose scale.");
		assertTrue(hud.contains("graphics.pose().popMatrix()")
				|| hud.contains("graphics.pose().popPose()"),
			"The scale transform must be popped after the draw.");
		assertTrue(hud.contains("Math.round(graphics.guiWidth() / scale)"),
			"Scaled drawing must position in scaled coordinate space so the anchored corner stays put.");
		assertTrue(hud.contains("boolean scaled = scale != 1.0F"),
			"The default scale of 1 should skip the transform entirely.");
	}

	@Test
	void combatHudOnlyDodgesTheFociHudInItsDefaultCorner() throws IOException {
		String combat = read(COMBAT_HUD);
		assertTrue(combat.contains("AttunedClientConfig.HudAnchor.BOTTOM_RIGHT"),
			"The Combat HUD should only yield the sidecar while the Foci HUD is docked bottom-right.");
		assertTrue(combat.contains("FociHud.isVisible(player)"),
			"The Combat HUD still respects the Foci HUD visibility toggle.");
	}

	@Test
	void configToleratesMissingOrInvalidLayoutKeys() throws IOException {
		String config = read(CONFIG);
		assertTrue(config.contains("HudAnchor.fromKey(stringOr(json, \"hud_anchor\""),
			"An unknown anchor key should fall back instead of crashing.");
		assertTrue(config.contains("intOr(json, \"hud_offset_x\"") && config.contains("intOr(json, \"hud_offset_y\""),
			"Offsets should tolerate missing or non-numeric values.");
		assertTrue(config.contains("floatOr(json, \"hud_scale\""),
			"Scale should tolerate missing or non-numeric values.");
		assertTrue(config.contains("return BOTTOM_RIGHT;"),
			"Unknown anchor strings should resolve to the default corner.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
