package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source contracts for Forge owner-client Attuned state sync used by HUD resonance. */
class ForgeOwnerStateSyncContractTest {
	private static final Path ATTACHMENTS =
		Path.of("src/main/java/dev/attuned/attunement/AttunedAttachments.java");
	private static final Path JOURNAL_NETWORKING =
		Path.of("src/main/java/dev/attuned/network/JournalNetworking.java");
	private static final Path STATE_PAYLOAD =
		Path.of("src/main/java/dev/attuned/network/AttunementStatePayload.java");
	private static final Path STATE_CLIENT =
		Path.of("src/client/java/dev/attuned/client/AttunementStateClient.java");
	private static final Path CLIENT_INIT =
		Path.of("src/client/java/dev/attuned/client/AttunedClient.java");
	private static final Path READOUT =
		Path.of("src/client/java/dev/attuned/client/AttunementReadout.java");

	@Test
	void serverWritesSendOwnerStateMirror() throws IOException {
		String attachments = read(ATTACHMENTS);
		String setBody = methodBody(attachments, "public static <T> void set(Player player, AttachmentType<T> type, T value)");
		String syncBody = methodBody(attachments, "public static void syncToClient(Player player)");

		assertTrue(setBody.contains("syncToClient(player);"),
			"Attachment writes must push the owner mirror because syncWith is not a real Forge sync path here.");
		assertTrue(syncBody.contains("player instanceof ServerPlayer serverPlayer"),
			"Owner sync should only send from the logical server.");
		assertTrue(syncBody.contains("new AttunementStatePayload("),
			"Owner sync should send the explicit state payload.");
		assertTrue(syncBody.contains("getResonance(serverPlayer)"),
			"The payload must include resonance for the Combat/Foci HUD bars.");
	}

	@Test
	void statePayloadIsRegisteredAndClientReceiverInvalidatesHudReadout() throws IOException {
		String networking = read(JOURNAL_NETWORKING);
		String payload = read(STATE_PAYLOAD);
		String client = read(STATE_CLIENT);
		String clientInit = read(CLIENT_INIT);
		String readout = read(READOUT);

		assertTrue(networking.contains("register(AttunementStatePayload.TYPE, AttunementStatePayload.CODEC);"),
			"The owner state payload must be registered before server sends.");
		assertTrue(payload.contains("float resonance"),
			"The payload must carry resonance.");
		assertTrue(clientInit.contains("AttunementStateClient.init();"),
			"The client initializer must install the state receiver.");
		assertTrue(client.contains("AttunedAttachments.applySyncedState(local, payload);"),
			"The receiver must apply the mirrored owner state.");
		assertTrue(client.contains("AttunementReadout.invalidate(local);"),
			"Applying synced resonance must evict the same-tick HUD cache.");
		assertTrue(readout.contains("public static void invalidate(Player player)"),
			"The shared readout must expose player-scoped invalidation.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static String methodBody(String source, String signaturePrefix) {
		int signatureStart = source.indexOf(signaturePrefix);
		assertTrue(signatureStart >= 0, "Missing method signature: " + signaturePrefix);
		int bodyStart = source.indexOf('{', signatureStart);
		assertTrue(bodyStart >= 0, "Missing method body: " + signaturePrefix);
		int depth = 0;
		for (int index = bodyStart; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '{') {
				depth++;
			} else if (current == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(bodyStart, index + 1);
				}
			}
		}
		throw new AssertionError("Unterminated method body: " + signaturePrefix);
	}
}
