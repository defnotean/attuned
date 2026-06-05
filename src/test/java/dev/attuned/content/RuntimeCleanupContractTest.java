package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract coverage for transient runtime cleanup coordinators. */
class RuntimeCleanupContractTest {
	private static final Path PLAYER_CLEANUP =
		Path.of("src/main/java/dev/attuned/AttunedPlayerCleanup.java");

	@Test
	void playerCleanupRegistersDisconnectHookOnceAndRejectsNullCallbacks() throws IOException {
		String source = read(PLAYER_CLEANUP);

		assertTrue(source.contains("private static boolean initialized;"),
			"Player cleanup should guard against duplicate event registration");
		assertTrue(source.contains("if (initialized)"),
			"Player cleanup should skip repeated init calls");
		assertTrue(source.contains("initialized = true;"),
			"Player cleanup should mark the disconnect hook as registered");
		assertTrue(source.contains("ServerPlayConnectionEvents.DISCONNECT.register"),
			"Player cleanup should own the disconnect event hook");
		assertTrue(source.contains("Objects.requireNonNull(cleanup, \"cleanup\")"),
			"Player cleanup callback registration should reject null callbacks immediately");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
