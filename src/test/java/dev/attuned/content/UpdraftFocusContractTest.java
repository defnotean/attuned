package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract coverage for the Updraft Focus elytra lift behavior. */
class UpdraftFocusContractTest {
	private static final Path BEHAVIOR =
		Path.of("src/main/java/dev/attuned/content/behavior/UpdraftBehavior.java");
	private static final Path NETWORKING =
		Path.of("src/main/java/dev/attuned/network/AttunedNetworking.java");
	private static final Path CLIENT =
		Path.of("src/client/java/dev/attuned/client/UpdraftLiftClient.java");
	private static final Path FOCUS =
		Path.of("src/main/resources/data/attuned/attuned/focus/updraft_focus.json");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void updraftFocusIsWiredForElytraLift() throws IOException {
		String behavior = read(BEHAVIOR);
		assertTrue(behavior.contains("applyBoost("));
		assertTrue(behavior.contains("startFallFlying()"));
		assertTrue(behavior.contains("Items.ELYTRA"));
		assertTrue(behavior.contains("THRUST"));
		assertTrue(behavior.contains("MAX_SPEED"));

		String networking = read(NETWORKING);
		assertTrue(networking.contains("UpdraftLiftPayload.TYPE"));
		assertTrue(networking.contains("UpdraftBehavior.isActive(player)"));
		assertTrue(networking.contains("UpdraftBehavior.setLifting"));

		String client = read(CLIENT);
		assertTrue(client.contains("UpdraftLiftPayload"));
		assertTrue(client.contains("keyJump.isDown()"));
		assertTrue(client.contains("AttunedContent.UPDRAFT_FOCUS"));

		String focus = read(FOCUS);
		assertTrue(focus.contains("\"behavior\": \"attuned:updraft\""));
		assertTrue(focus.contains("\"affinity\": \"zephyr\""));
		assertTrue(focus.contains("\"unique\": true"));

		String lang = read(LANG);
		assertTrue(lang.contains("item.attuned.updraft_focus.effect"));
	}

	private static String read(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
