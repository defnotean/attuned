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

/** Source and asset guardrails for the dedicated equipped-Foci gameplay HUD. */
class FociHudContractTest {
	private static final Path CONFIG =
		Path.of("src/client/java/dev/attuned/client/AttunedClientConfig.java");
	private static final Path KEYBINDS =
		Path.of("src/client/java/dev/attuned/client/AttunedKeybinds.java");
	private static final Path CLIENT_INIT =
		Path.of("src/client/java/dev/attuned/client/AttunedClient.java");
	private static final Path FOCI_HUD =
		Path.of("src/client/java/dev/attuned/client/hud/FociHud.java");
	private static final Path COMBAT_HUD =
		Path.of("src/client/java/dev/attuned/client/hud/CombatHud.java");
	private static final Path HUD_ANCHOR =
		Path.of("src/client/java/dev/attuned/client/hud/HudAnchor.java");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");
	private static final Path FOCI_HUD_TEXTURE =
		Path.of("src/main/resources/assets/attuned/textures/gui/foci_hud.png");

	@Test
	void fociHudHasSeparateToggleAndClientRegistration() throws IOException {
		String config = read(CONFIG);
		String keybinds = read(KEYBINDS);
		String client = read(CLIENT_INIT);
		String lang = read(LANG);

		assertTrue(config.contains("boolean showFociHud"),
			"The Foci HUD should have its own client config field.");
		assertTrue(config.contains("show_foci_hud"),
			"The Foci HUD config should persist under its own JSON key.");
		assertTrue(config.contains("toggleFociHud()"),
			"The Foci HUD should have its own config toggle method.");
		assertTrue(keybinds.contains("toggleFociHudKey"),
			"The Foci HUD should have its own keybind.");
		assertTrue(keybinds.contains("AttunedClientConfig.toggleFociHud()"),
			"The Foci HUD keybind should toggle only the Foci HUD setting.");
		assertTrue(lang.contains("\"key.attuned.toggle_foci_hud\": \"Toggle Foci HUD\""),
			"The Foci HUD keybind should have player-facing text.");
		assertTrue(client.contains("FociHud.init()"),
			"The client initializer should register the Foci HUD layer.");
	}

	@Test
	void fociHudDrawsActualEquippedFocusStacksAndDormantState() throws IOException {
		String hud = read(FOCI_HUD);

		assertTrue(hud.contains("HudElementRegistry.attachElementAfter"),
			"The Foci HUD should register as its own HUD layer.");
		assertTrue(hud.contains("VanillaHudElements.HOTBAR"),
			"The Foci HUD should anchor near the hotbar.");
		assertTrue(hud.contains("textures/gui/foci_hud.png"),
			"The Foci HUD should use its dedicated frame art.");
		assertTrue(hud.contains("AttunedAttachments.getInventory(player)"),
			"The HUD should read the equipped Focus inventory.");
		assertTrue(hud.contains("graphics.item("),
			"The HUD should render actual equipped item stacks.");
		assertTrue(hud.contains("dormantReasons"),
			"The HUD should distinguish active and dormant equipped Foci.");
		assertTrue(hud.contains("drawDormantOverlay"),
			"Dormant or over-budget Foci should be visibly dimmed.");
		assertTrue(hud.contains("drawApexBar"),
			"The HUD should include a readable Apex/resonance bar.");
		assertTrue(hud.contains("FOCUS_GRID_COLUMNS = 2"),
			"The Foci HUD should use a compact two-column grid instead of a tall slot column.");
		assertTrue(hud.contains("APEX_BAR_W = 54"),
			"The resonance bar should be wide enough to read at normal GUI scales.");
		assertTrue(hud.contains("BAR_EMPTY_FILL"),
			"The resonance bar should remain visible even while empty.");
		assertTrue(hud.contains("return AttunedClientConfig.get().showFociHud();"),
			"The HUD frame should remain visible when toggled on, even before any Focus is equipped.");
		assertTrue(hud.contains("readout.activeConfluences()"),
			"The Foci HUD should read the active-confluence set from the shared readout.");
		assertTrue(hud.contains("drawConfluenceChip"),
			"The Foci HUD should paint a confluence count chip.");
	}

	@Test
	void fociHudReusesOneAttunementResolutionPerFrame() throws IOException {
		String hud = read(FOCI_HUD);

		assertEquals(1, countOccurrences(hud, "AttunementReadout.cached(player)"),
			"The HUD should reuse the local-player readout snapshot once per rendered frame.");
		assertEquals(0, countOccurrences(hud, "Attunement.resolution(player)"),
			"The HUD should not own budget resolution after the shared readout cache is available.");
		assertEquals(0, countOccurrences(hud, "Attunement.activeSlots(player)"),
			"The HUD should not recompute active slots after resolving the frame budget.");
		assertEquals(0, countOccurrences(hud, "Attunement.dormantReasons(player)"),
			"The HUD should not recompute dormant reasons after resolving the frame budget.");
		assertTrue(hud.contains("List<Integer> activeSlots = readout.activeSlots()"),
			"The HUD should take active slots from the shared readout.");
		assertTrue(hud.contains("drawAbilityWell(graphics, player, inv, activeSlots,"),
			"The ability well should reuse the frame's resolved active slots.");
		assertTrue(hud.contains("selectedAbilitySlot(Player player, AttunedInv inv, List<Integer> activeSlots,"),
			"Ability fallback selection should receive resolved active slots instead of querying again.");
		assertTrue(hud.contains("for (int slot : activeSlots)"),
			"Ability fallback selection should iterate the already-resolved active slots.");
		assertEquals(1, countOccurrences(hud, "Attunement.definitionFor(player,"),
			"The HUD should cache active slot Focus definitions for ability, color, and Apex rendering.");
		assertEquals(0, countOccurrences(hud, "Apex.capstoneOf(player)"),
			"The HUD should derive Apex state from cached active slot definitions.");
		assertEquals(0, countOccurrences(hud, "Attunement.committedAffinity(player)"),
			"The HUD should derive committed affinity from cached active slot definitions.");
		assertEquals(0, countOccurrences(hud, "Attunement.isDiscord(player)"),
			"The HUD should derive Discord state from cached active slot definitions.");
		assertTrue(hud.contains("drawApexBar(graphics, readout,"),
			"The HUD should draw Apex and resonance state from the shared readout.");
	}

	@Test
	void fociHudAndCombatHudDoNotShareTheSameSidecarSlot() throws IOException {
		String fociHud = read(FOCI_HUD);
		String combatHud = read(COMBAT_HUD);

		assertTrue(fociHud.contains("primarySidecarX"),
			"The Foci HUD should own the primary hotbar sidecar.");
		assertTrue(read(HUD_ANCHOR).contains("screenW - width - SCREEN_MARGIN"),
			"The default anchor should pin the Foci HUD to the far right edge instead of hugging the hotbar.");
		assertTrue(fociHud.contains("HudAnchor.x(screenW, hudWidth, HudAnchor.layout())"),
			"The Foci HUD should resolve its corner through the shared anchor helper.");
		assertTrue(combatHud.contains("FociHud.isVisible(player)")
				&& combatHud.contains("secondarySidecarX"),
			"The Combat HUD should avoid the Foci HUD sidecar when both are visible.");
		assertTrue(combatHud.contains("int fociY = Math.max(SCREEN_MARGIN, screenH - FOCI_HUD_HEIGHT - SCREEN_MARGIN);"),
			"Narrow screens should stack Combat HUD above the bottom-anchored Foci HUD.");
		assertTrue(combatHud.contains("return Math.max(SCREEN_MARGIN, fociY - HUD_BACKPLATE_H - SCREEN_MARGIN);"),
			"The no-sidecar fallback should leave a margin between the two HUD rectangles.");
		assertTrue(!combatHud.contains("SCREEN_MARGIN + FOCI_HUD_HEIGHT + SCREEN_MARGIN"),
			"The no-sidecar fallback should not stack from the top edge while Foci HUD is bottom-anchored.");
	}

	@Test
	void fociHudFrameHasStableGameplayDimensions() throws IOException {
		assertPngSize(FOCI_HUD_TEXTURE, 64, 96);
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static int countOccurrences(String value, String needle) {
		int count = 0;
		int index = 0;
		while ((index = value.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
	}

	private static void assertPngSize(Path path, int width, int height) throws IOException {
		assertTrue(Files.isRegularFile(path), "Missing HUD texture: " + path);
		BufferedImage image = ImageIO.read(path.toFile());
		assertNotNull(image, "HUD texture should be a readable PNG: " + path);
		assertEquals(width, image.getWidth(), "Unexpected HUD texture width: " + path);
		assertEquals(height, image.getHeight(), "Unexpected HUD texture height: " + path);
	}
}
