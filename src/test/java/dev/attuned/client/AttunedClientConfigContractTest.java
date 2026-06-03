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
		assertTrue(source.contains("resolve(\"attuned-client.json\")"), "Client config should load attuned-client.json.");
		assertTrue(source.contains("DEFAULT = new AttunedClientConfig(true, true, true)"),
			"Client config should default all HUD elements on.");
		assertTrue(source.contains("save();"), "Client config should write normalized/default settings.");
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
	}

	private static String read(Path file) throws IOException {
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
