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

	@Test
	void forgeHudCallbacksRegisterWithForgeGuiLayerEvent() throws IOException {
		String hud = read(HUD_CALLBACK);

		assertTrue(hud.contains("AddGuiOverlayLayersEvent"));
		assertTrue(hud.contains("FMLJavaModLoadingContext.get().getModEventBus()"));
		assertTrue(hud.contains("event.getLayeredDraw().add"));
		assertTrue(hud.contains("callback::onHudRender"));
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
