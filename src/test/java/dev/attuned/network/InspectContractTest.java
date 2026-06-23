package dev.attuned.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-grep contract for the affinity inspect feature: the serverbound
 * {@link InspectRequestPayload}, its registration + range/rate-limit/cleanup
 * guarded handler in {@link AttunedNetworking}, and the client hover watcher that
 * sends exactly one request per sustained crouch-and-look hover.
 */
class InspectContractTest {
	private static final Path PAYLOAD =
		Path.of("src/main/java/dev/attuned/network/InspectRequestPayload.java");
	private static final Path NET =
		Path.of("src/main/java/dev/attuned/network/AttunedNetworking.java");
	private static final Path CLIENT =
		Path.of("src/client/java/dev/attuned/client/AffinityInspectClient.java");
	private static final Path CLIENT_INIT =
		Path.of("src/client/java/dev/attuned/client/AttunedClient.java");

	@Test
	void payloadIsServerboundEntityIdRecordWithCastCodec() throws IOException {
		String payload = read(PAYLOAD);
		assertTrue(payload.contains("record InspectRequestPayload(int targetEntityId)"),
			"Inspect carries only the target entity id.");
		assertTrue(payload.contains("public static final Type<InspectRequestPayload> TYPE"),
			"Payload exposes a CustomPacketPayload TYPE.");
		assertTrue(payload.contains("StreamCodec.composite(ByteBufCodecs.VAR_INT")
				&& payload.contains("InspectRequestPayload::targetEntityId")
				&& payload.contains("InspectRequestPayload::new).cast()"),
			"Single-int composite codec must cast to the RegistryFriendlyByteBuf field type.");
	}

	@Test
	void networkingRegistersServerboundAndHandlesOnServerThread() throws IOException {
		String net = read(NET);
		assertTrue(net.contains("PayloadTypeRegistry.playC2S().register(InspectRequestPayload.TYPE, InspectRequestPayload.CODEC)"),
			"Inspect request is registered serverbound.");
		assertTrue(net.contains("ServerPlayNetworking.registerGlobalReceiver(InspectRequestPayload.TYPE"),
			"Inspect request has a server-side receiver.");
		assertTrue(net.contains("player.level().getServer().execute("),
			"Inspect handling hops to the server thread before touching world state.");
	}

	@Test
	void handlerValidatesRangeLineOfSightAndRateLimits() throws IOException {
		String net = read(NET);
		assertTrue(net.contains("instanceof ServerPlayer"),
			"Inspect only reports on real ServerPlayer targets.");
		assertTrue(net.contains("24"),
			"Inspect enforces a 24-block range between requester and target.");
		assertTrue(net.contains("hasLineOfSight"),
			"Inspect requires line of sight to the target.");
		assertTrue(net.contains("private static final Map<java.util.UUID, Long> LAST_INSPECT")
				|| net.contains("Map<UUID, Long> LAST_INSPECT"),
			"A per-requester rate-limit map throttles inspect requests.");
		assertTrue(net.contains("20"),
			"Inspect rate-limits requests to once per 20 ticks per requester.");
	}

	@Test
	void handlerRegistersCleanupForTheRateLimitMap() throws IOException {
		String net = read(NET);
		assertTrue(net.contains("AttunedPlayerCleanup.onForget"),
			"The rate-limit map drops a player's entry when they are forgotten.");
		assertTrue(net.contains("AttunedServerCleanup.onStop"),
			"The rate-limit map clears on server stop.");
	}

	@Test
	void handlerReportsCommittedDiscordAndApexState() throws IOException {
		String net = read(NET);
		assertTrue(net.contains("Attunement.committedAffinity") && net.contains("Attunement.isDiscord"),
			"Inspect derives committed affinity / Discord server-side from the target's attunement.");
		assertTrue(net.contains("Apex.capstoneOf") && net.contains("Resonance.atApex"),
			"Inspect reports the target's Apex capstone and whether it is armed.");
		assertTrue(net.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS"),
			"Inspect replies on the requester's action bar through the shared priority gate.");
		assertTrue(!net.contains("sendOverlayMessage(inspectLine(target))"),
			"Inspect should not bypass the action-bar priority gate.");
	}

	@Test
	void clientSendsExactlyOneRequestPerSustainedHover() throws IOException {
		String client = read(CLIENT);
		assertTrue(client.contains("isShiftKeyDown()"),
			"Inspect only accumulates while the player is crouching.");
		assertTrue(client.contains("crosshairPickEntity"),
			"Inspect reuses the crosshair-picked target like the Combat HUD.");
		assertTrue(client.contains("instanceof") && client.contains("Player"),
			"Inspect only targets other players, never mobs.");
		assertTrue(client.contains("private static int hoverTicks")
				|| client.contains("static int hoverTicks"),
			"The client accumulates hover ticks in a field so it does not re-send every frame.");
		assertTrue(client.contains("INSPECT_HOLD_TICKS = 30"),
			"The hover threshold is 30 ticks (~1.5s).");
		assertTrue(client.contains("hoverTicks == INSPECT_HOLD_TICKS"),
			"Exactly one request fires when the accumulator hits the hold threshold, never on repeat frames.");
		assertTrue(client.contains("ClientPlayNetworking.send(new InspectRequestPayload("),
			"The client sends the inspect request payload.");
		assertTrue(read(CLIENT_INIT).contains("AffinityInspectClient.init()"),
			"The inspect watcher is wired into the client initializer.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
