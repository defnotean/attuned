package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AttunedClientConfigContractTest {
	private static final Path CLIENT_CONFIG_SOURCE =
		Path.of("src/client/java/dev/attuned/client/AttunedClientConfig.java");
	private static final Path PLATFORM_SERVICES_SOURCE =
		Path.of("src/main/java/dev/attuned/platform/AttunedPlatformServices.java");
	private static final Path CLIENT_SOURCE =
		Path.of("src/client/java/dev/attuned/client/AttunedClient.java");
	private static final Path KEYBINDS_SOURCE =
		Path.of("src/client/java/dev/attuned/client/AttunedKeybinds.java");
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void clientConfigPersistsHudToggleSettings() throws IOException {
		String source = read(CLIENT_CONFIG_SOURCE);

		assertTrue(source.contains("show_own_affinity_hud"), "Client config should persist the own HUD key.");
		assertTrue(source.contains("show_enemy_affinity_hud"), "Client config should persist the enemy HUD key.");
		assertTrue(source.contains("show_foci_hud"), "Client config should persist the Foci HUD key.");
		assertTrue(source.contains("show_party_hud"), "Client config should persist the party HUD key.");
		assertTrue(source.contains("AttunedPlatform.services().clientConfigPath()"),
			"Client config should load through the loader-neutral platform service.");
		assertTrue(read(PLATFORM_SERVICES_SOURCE).contains("resolve(\"attuned-client.json\")"),
			"The platform service should own the attuned-client.json path.");
		assertTrue(source.contains("public static final AttunedClientConfig DEFAULT")
				&& source.contains("new AttunedClientConfig(true, true, true, true, HudLayout.DEFAULT, HudLayout.PARTY_DEFAULT)"),
			"Client config should default all HUD elements on with stock Foci and party layouts.");
		assertTrue(source.contains("save();"), "Client config should write normalized/default settings.");
	}

	@Test
	void clientConfigPersistsHudLayoutSettings() throws IOException {
		String source = read(CLIENT_CONFIG_SOURCE);

		assertTrue(source.contains("hud_anchor"), "Client config should persist the HUD anchor key.");
		assertTrue(source.contains("hud_offset_x"), "Client config should persist the HUD x offset key.");
		assertTrue(source.contains("hud_offset_y"), "Client config should persist the HUD y offset key.");
		assertTrue(source.contains("hud_scale"), "Client config should persist the HUD scale key.");
		assertTrue(source.contains("party_hud_anchor"), "Client config should persist the party HUD anchor key.");
		assertTrue(source.contains("party_hud_offset_x"), "Client config should persist the party HUD x offset key.");
		assertTrue(source.contains("party_hud_offset_y"), "Client config should persist the party HUD y offset key.");
		assertTrue(source.contains("party_hud_scale"), "Client config should persist the party HUD scale key.");
		assertTrue(source.contains("new HudLayout(HudAnchor.BOTTOM_RIGHT, 0, 0, 1.0F)"),
			"The default HUD layout must reproduce the historical bottom-right docking exactly.");
		assertTrue(source.contains("private static final int PARTY_BOSS_BAR_CLEARANCE_Y = 36"),
			"The default party HUD layout should name its vanilla boss-bar clearance.");
		assertTrue(source.contains("new HudLayout(HudAnchor.TOP_LEFT, 0, PARTY_BOSS_BAR_CLEARANCE_Y, 1.0F)"),
			"The default party HUD layout must stay top-left while clearing vanilla boss bars.");
		assertTrue(source.contains("Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale))"),
			"HUD scale should clamp to the supported range.");
		assertTrue(source.contains("Math.round(clamped * 4.0F) / 4.0F"),
			"HUD scale should snap to quarter steps so layouts stay pixel-crisp.");
	}

	@Test
	void invalidHudLayoutRepairsLogOnce() throws IOException {
		String source = read(CLIENT_CONFIG_SOURCE);

		assertTrue(source.contains("private static boolean repairLogged"),
			"Invalid HUD config repairs should only log once per client session.");
		assertTrue(source.contains("logRepairOnce(path);"),
			"Normalized invalid HUD config should emit the one-shot repair log before saving.");
		assertTrue(source.contains("Repaired invalid Attuned HUD config"),
			"The repair log should be concise and specific to the HUD config repair.");
		assertTrue(source.contains("private static HudAnchor anchorOr("),
			"HUD anchor parsing should distinguish invalid anchors from missing anchors.");
		assertTrue(source.contains("private static float scaleOr("),
			"HUD scale parsing should detect invalid values before the layout clamps them.");
		assertTrue(source.contains("new ReadResult(DEFAULT, true)"),
			"A non-object config should be treated as a repaired config instead of failing silently.");
	}

	@Test
	void clientInitializerLoadsConfigBeforeClientSystems() throws IOException {
		String source = read(CLIENT_SOURCE);

		assertTrue(source.contains("AttunedClientConfig.load();"),
			"Client initializer should load the config before registering HUD/keybind systems.");
	}

	@Test
	void keybindsRegisterUnboundHudToggles() throws IOException {
		String source = read(KEYBINDS_SOURCE);

		assertTrue(source.contains("key.attuned.toggle_own_affinity_hud"),
			"Own HUD toggle keybind should be registered.");
		assertTrue(source.contains("key.attuned.toggle_enemy_affinity_hud"),
			"Enemy HUD toggle keybind should be registered.");
		assertTrue(source.contains("key.attuned.toggle_foci_hud"),
			"Foci HUD toggle keybind should be registered.");
		assertTrue(source.contains("key.attuned.toggle_party_hud"),
			"Party HUD toggle keybind should be registered.");
		assertTrue(source.contains("AttunedClientConfig.togglePartyHud()"),
			"Party HUD toggle keybind should toggle only the party HUD setting.");
		assertTrue(source.contains("InputConstants.UNKNOWN"), "HUD toggle keybinds should be unbound by default.");
	}

	@Test
	void langDefinesHudToggleKeybinds() throws IOException {
		String source = read(LANG_FILE);

		assertTrue(source.contains("\"key.attuned.toggle_own_affinity_hud\""),
			"Own HUD toggle keybind should have a translation.");
		assertTrue(source.contains("\"key.attuned.toggle_enemy_affinity_hud\""),
			"Enemy HUD toggle keybind should have a translation.");
		assertTrue(source.contains("\"key.attuned.toggle_foci_hud\""),
			"Foci HUD toggle keybind should have a translation.");
		assertTrue(source.contains("\"key.attuned.toggle_party_hud\""),
			"Party HUD toggle keybind should have a translation.");
	}

	private static String read(Path file) throws IOException {
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
