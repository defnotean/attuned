package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract coverage for blink-style Focus abilities that must not cross walls. */
class MovementAbilityPathContractTest {
	private static final Path VOIDSTEP =
		Path.of("src/main/java/dev/attuned/content/behavior/VoidstepBehavior.java");
	private static final Path REVENANT_BEHAVIORS =
		Path.of("src/main/java/dev/attuned/content/behavior/RevenantFocusBehaviors.java");

	@Test
	void blinkAbilitiesCheckBlockPathBeforeTeleporting() throws IOException {
		assertBlocksObstructTeleportPath(read(VOIDSTEP), "Voidstep");
		assertBlocksObstructTeleportPath(read(REVENANT_BEHAVIORS), "Hollowstep");
	}

	private static void assertBlocksObstructTeleportPath(String source, String abilityName) {
		assertTrue(source.contains("level.clip("),
			abilityName + " should ray-check blocks between origin and destination, not only the destination box.");
		assertTrue(source.contains("ClipContext.Block.COLLIDER"),
			abilityName + " should use solid block collision for the path check.");
		assertTrue(source.contains("HitResult.Type.BLOCK"),
			abilityName + " should reject candidates when the block path is obstructed.");
		assertTrue(source.contains("level.noCollision(player, box)"),
			abilityName + " should still verify the player fits at the destination.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected source file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
