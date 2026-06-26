package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract coverage for the Forge-backed Fabric HUD callback shim. */
class ForgeHudBridgeContractTest {
	private static final Path HUD_CALLBACK =
		Path.of("src/client/java/net/fabricmc/fabric/api/client/rendering/v1/HudRenderCallback.java");

	@Test
	void hudRenderCallbackDispatchesRegisteredLayersFromForgeGuiEvent() throws IOException {
		String source = read(HUD_CALLBACK);

		assertTrue(source.contains("MinecraftForge.EVENT_BUS.addListener"),
			"Forge HUD shim should subscribe to the Forge event bus.");
		assertTrue(source.contains("RenderGuiEvent.Post"),
			"Forge HUD shim should dispatch after the whole vanilla HUD is available.");
		assertTrue(source.contains("for (Callback callback : List.copyOf(callbacks))"),
			"Forge HUD shim should dispatch every registered HUD callback safely.");
		assertTrue(source.contains("callback.onHudRender(event.getGuiGraphics(), event.getPartialTick())"),
			"Forge HUD shim should pass the Forge GuiGraphics and partial tick to Fabric-style callbacks.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
