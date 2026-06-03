package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Client contract coverage for Focus Ability cooldown HUD state. */
class FociHudCooldownContractTest {
	private static final Path CLIENT_STATE =
		Path.of("src/client/java/dev/attuned/client/FocusAbilityClientState.java");
	private static final Path FOCI_HUD =
		Path.of("src/client/java/dev/attuned/client/hud/FociHud.java");
	private static final Path CLIENT_INIT =
		Path.of("src/client/java/dev/attuned/client/AttunedClient.java");

	@Test
	void clientReceivesCooldownStateAndFociHudDrawsTheRing() throws IOException {
		String state = read(CLIENT_STATE);
		String hud = read(FOCI_HUD);
		String client = read(CLIENT_INIT);

		assertTrue(state.contains("ClientPlayNetworking.registerGlobalReceiver(FocusAbilityStatusPayload.TYPE"),
			"The client should receive server-authoritative ability cooldown status.");
		assertTrue(state.contains("remainingTicks") && state.contains("totalTicks"),
			"The client should keep both remaining and total cooldown ticks.");
		assertTrue(state.contains("tick()"),
			"The client should locally count down between server sync packets.");
		assertTrue(hud.contains("FocusAbilityClientState"),
			"The Foci HUD should read the client cooldown state.");
		assertTrue(hud.contains("drawCooldownRing"),
			"The Foci HUD should draw a ring around the active ability Focus when it is down.");
		assertTrue(hud.contains("remainingTicks()"),
			"The ring should be driven by the remaining cooldown ticks.");
		assertTrue(client.contains("FocusAbilityClientState.init()"),
			"The client initializer should install the cooldown status receiver.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
