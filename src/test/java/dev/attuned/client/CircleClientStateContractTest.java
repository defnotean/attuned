package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.attuned.network.CircleInvitePromptPayload;
import dev.attuned.network.CirclePingClientPayload;
import dev.attuned.network.CircleSnapshotPayload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Source contracts for the client mirror consumed by the future party HUD. */
class CircleClientStateContractTest {
	private static final Path CLIENT_STATE =
		Path.of("src/client/java/dev/attuned/client/CircleClientState.java");
	private static final Path CLIENT_INIT =
		Path.of("src/client/java/dev/attuned/client/AttunedClient.java");

	@Test
	void clientCachesOnlyTheLatestServerSnapshot() throws IOException {
		String state = read(CLIENT_STATE);
		String client = read(CLIENT_INIT);

		assertTrue(client.contains("CircleClientState.init()"),
			"The client initializer should install the Circle snapshot receiver.");
		assertTrue(state.contains("ClientPlayNetworking.registerGlobalReceiver(CircleSnapshotPayload.TYPE"),
			"Circle snapshots should arrive through the server-owned clientbound payload.");
		assertTrue(state.contains("ClientPlayNetworking.registerGlobalReceiver(CircleInvitePromptPayload.TYPE"),
			"Circle invites should arrive through the server-owned clientbound prompt payload.");
		assertTrue(state.contains("ClientPlayNetworking.registerGlobalReceiver(CirclePingClientPayload.TYPE"),
			"Circle pings should arrive through the server-owned clientbound ping payload.");
		assertTrue(state.contains("private static CircleSnapshotPayload snapshot = CircleSnapshotPayload.EMPTY"),
			"The client cache should start empty.");
		assertTrue(state.contains("private static CircleInvitePromptPayload invitePrompt"),
			"The client cache should retain the latest bounded Circle invite prompt.");
		assertTrue(state.contains("private static CirclePingClientPayload ping"),
			"The client cache should retain the latest bounded Circle ping.");
		assertTrue(state.contains("snapshot = payload"),
			"The client cache should mirror the latest server snapshot.");
		assertTrue(state.contains("invitePrompt = payload"),
			"The client cache should mirror the latest server invite prompt.");
		assertTrue(state.contains("ping = payload"),
			"The client cache should mirror the latest server ping.");
		assertTrue(state.contains("client.level == null || client.player == null"),
			"The client cache should clear when no world/player is active.");
		assertTrue(state.contains("public static List<CircleSnapshotPayload.Member> members()"),
			"The party HUD should be able to read public member rows without touching networking.");
		assertTrue(state.contains("public static java.util.Optional<CircleInvitePromptPayload> invitePrompt()"),
			"HUD/toast UI should be able to read the latest invite prompt without touching networking.");
		assertTrue(state.contains("public static java.util.Optional<CirclePingClientPayload> ping()"),
			"HUD ping UI should be able to read the latest ping without touching networking.");
	}

	@Test
	void nullSnapshotClearsToEmptyPublicState() {
		CircleSnapshotPayload payload = new CircleSnapshotPayload("Ember Watch",
			List.of(new CircleSnapshotPayload.Member(
				UUID.fromString("00000000-0000-0000-0000-000000000701"), "Leader", true)));

		try {
			CircleClientState.accept(payload);
			CircleClientState.accept(null);

			assertEquals("", CircleClientState.name(),
				"Malformed snapshot handoffs should clear the party HUD name instead of leaving stale state.");
			assertEquals(List.of(), CircleClientState.members(),
				"Malformed snapshot handoffs should clear public party rows instead of crashing the HUD.");
		} finally {
			CircleClientState.accept(CircleSnapshotPayload.EMPTY);
		}
	}

	@Test
	void nullInvitePromptClearsToEmptyPromptState() {
		CircleInvitePromptPayload prompt = new CircleInvitePromptPayload(
			UUID.fromString("00000000-0000-0000-0000-000000000702"),
			"Leader", "Ember Watch", 1, 4, 200L);

		try {
			CircleClientState.acceptPrompt(prompt);
			CircleClientState.acceptPrompt(null);

			assertEquals(java.util.Optional.empty(), CircleClientState.invitePrompt(),
				"Malformed invite prompt handoffs should clear stale accept prompts.");
		} finally {
			CircleClientState.acceptPrompt(null);
		}
	}

	@Test
	void nullPingClearsToEmptyPingState() {
		CirclePingClientPayload ping = new CirclePingClientPayload(
			UUID.fromString("00000000-0000-0000-0000-000000000703"),
			"Leader", 1.0D, 64.0D, 2.0D, 200L);

		try {
			CircleClientState.acceptPing(ping);
			CircleClientState.acceptPing(null);

			assertEquals(java.util.Optional.empty(), CircleClientState.ping(),
				"Malformed ping handoffs should clear stale Circle ping markers.");
		} finally {
			CircleClientState.acceptPing(null);
		}
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
