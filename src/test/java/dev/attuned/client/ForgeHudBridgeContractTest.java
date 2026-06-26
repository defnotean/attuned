package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgeHudBridgeContractTest {
	private static final Path HUD_CALLBACK =
		Path.of("src/client/java/net/fabricmc/fabric/api/client/rendering/v1/HudRenderCallback.java");
	private static final Path CLIENT_SYNC =
		Path.of("src/client/java/dev/attuned/client/AttunedStateClientSync.java");
	private static final Path READOUT =
		Path.of("src/client/java/dev/attuned/client/AttunementReadout.java");

	@Test
	void forgeHudCallbacksDispatchAndStateSyncInvalidatesReadout() throws IOException {
		String hud = read(HUD_CALLBACK);
		String sync = read(CLIENT_SYNC);
		String readout = read(READOUT);

		assertTrue(hud.contains("MinecraftForge.EVENT_BUS.addListener"));
		assertTrue(hud.contains("RenderGameOverlayEvent.Post"));
		assertTrue(hud.contains("RenderGameOverlayEvent.ElementType.ALL"));
		assertTrue(hud.contains("callback.onHudRender(event.getMatrixStack(), event.getPartialTicks())"));
		assertTrue(sync.contains("AttunedAttachments.applySync(local, payload.tag());"));
		assertTrue(sync.contains("AttunementReadout.invalidate(local);"));
		assertTrue(readout.contains("public static void invalidate(Player player)"));
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
