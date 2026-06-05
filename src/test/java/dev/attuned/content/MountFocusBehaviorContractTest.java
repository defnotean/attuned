package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract coverage for Focus behaviours that temporarily mutate mounts. */
class MountFocusBehaviorContractTest {
	private static final Path GALESPUR_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/GalespurBehavior.java");

	@Test
	void galespurRestoresMountSpeedOnPlayerCleanupAndServerStop() throws IOException {
		String source = read(GALESPUR_SOURCE);

		assertTrue(source.contains("AttunedPlayerCleanup.onForgetPlayer(GalespurBehavior::forgetPlayer)"),
			"Galespur should release a player's mount boost when that player disconnects");
		assertTrue(source.contains("AttunedServerCleanup.onStop(GalespurBehavior::removeAllBoosts)"),
			"Galespur should remove transient mount modifiers when the server stops");
		assertTrue(!source.contains("ServerLifecycleEvents.SERVER_STOPPED.register"),
			"Galespur should use the central server cleanup coordinator");
		assertTrue(source.contains("private static void removeAllBoosts()"),
			"Galespur should centralize full cleanup in a named helper");
		assertTrue(source.contains("removeBoost(mount);"),
			"Galespur full cleanup should remove transient attribute modifiers from retained mounts");
		assertTrue(source.contains("MOUNT_BY_PLAYER.clear();"),
			"Galespur full cleanup should drop player-to-mount references");
		assertTrue(source.contains("BOOSTERS_BY_MOUNT.clear();"),
			"Galespur full cleanup should drop mount reference counts");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
