package dev.attuned.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AttunedCommandsContractTest {
	private static final Path COMMANDS =
		Path.of("src/main/java/dev/attuned/command/AttunedCommands.java");

	@Test
	void commandTreeDoesNotShipGuiPreviewDebugCommands() throws IOException {
		String source = read(COMMANDS);

		assertTrue(!source.contains("Commands.literal(\"gui\")"),
			"GUI preview/debug commands should not ship; use tools/render_gui_previews.py and the asset customizer.");
		assertTrue(!source.contains("openAltarGuiPreview"),
			"Attunement Altar GUI previews should stay in the out-of-game tooling.");
		assertTrue(!source.contains("openReweavingGuiPreview"),
			"Reweaving GUI previews should stay in the out-of-game tooling.");
		assertTrue(!source.contains("previewAltarInput"),
			"Preview-only inventory setup should not live in the runtime command class.");
		assertTrue(!source.contains("previewReweavingContainer"),
			"Preview-only inventory setup should not live in the runtime command class.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
