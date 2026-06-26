package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source contracts for the 1.20.6 Forge state-sync bridge that feeds HUD resonance. */
class ForgeStateSyncHudContractTest {
	private static final Path STATE_CLIENT =
		Path.of("src/client/java/dev/attuned/client/AttunementStateClient.java");
	private static final Path READOUT =
		Path.of("src/client/java/dev/attuned/client/AttunementReadout.java");
	private static final Path HUD_CALLBACK =
		Path.of("src/client/java/net/fabricmc/fabric/api/client/rendering/v1/HudRenderCallback.java");

	@Test
	void statePayloadRefreshesHudReadoutCache() throws IOException {
		String stateClient = read(STATE_CLIENT);
		String body = methodBody(stateClient, "public static void init()");

		assertTrue(body.contains("var local = context.client().player;"),
			"The receiver should capture the local player once inside the client thread.");
		assertTrue(body.contains("AttunedAttachments.applySyncedState(local, payload);"),
			"The 1.20.6 mirror payload must still apply server-owned attachment state.");
		assertTrue(body.contains("AttunementReadout.invalidate(local);"),
			"Applying synced resonance must evict the same-tick HUD readout cache.");
	}

	@Test
	void readoutExposesPlayerScopedInvalidation() throws IOException {
		String readout = read(READOUT);
		String body = methodBody(readout, "public static void invalidate(Player player)");

		assertTrue(body.contains("player != null"),
			"Invalidation should tolerate disconnect races.");
		assertTrue(body.contains("player.getUUID().equals(cachedPlayerId)"),
			"Invalidation must be scoped to the player whose sync payload arrived.");
		assertTrue(body.contains("cachedSnapshot = null"),
			"Invalidation must force the next HUD frame to rebuild the snapshot.");
	}

	@Test
	void hudCallbacksAreRegisteredThroughForgeOverlayLayers() throws IOException {
		String callback = read(HUD_CALLBACK);

		assertTrue(callback.contains("AddGuiOverlayLayersEvent"),
			"The Fabric-style HUD callback shim should bridge into Forge's overlay layer event.");
		assertTrue(callback.contains("event.getLayeredDraw().add"),
			"Registered HUD callbacks must be invoked by Forge's layered HUD draw.");
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
