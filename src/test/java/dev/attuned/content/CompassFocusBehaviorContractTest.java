package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-level guardrails for Focus-driven compass behavior. The vanilla client
 * compass angle model reads {@code DataComponents.LODESTONE_TRACKER.target()},
 * so these tests keep Beacon and Waystone on that vanilla turning path.
 */
class CompassFocusBehaviorContractTest {
	private static final Path BEACON_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/BeaconBehavior.java");
	private static final Path WAYSTONE_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/WaystoneBehavior.java");

	@Test
	void beaconCompassUsesVanillaLodestoneTrackerForNeedleAngle() throws IOException {
		String source = read(BEACON_SOURCE);

		assertTrue(source.contains("new LodestoneTracker(Optional.of(home), false)"),
			"Beacon should create an untracked lodestone target from the player's respawn point");
		assertTrue(source.contains("compass.set(DataComponents.LODESTONE_TRACKER, tracker)"),
			"Beacon should write the vanilla tracker component that drives compass angle rendering");
		assertTrue(source.contains("compass.remove(DataComponents.LODESTONE_TRACKER)"),
			"Beacon should remove its temporary tracker when restoring an ordinary compass");
		assertTrue(source.contains("compass.set(DataComponents.LODESTONE_TRACKER, snapshot.originalTracker)"),
			"Beacon should restore pre-existing lodestone bindings");
		assertTrue(source.contains("held.is(Items.COMPASS)"),
			"Beacon should apply only to held vanilla compasses");
	}

	@Test
	void waystoneCompassUsesLastDeathTrackerAndSupportsHeldStacks() throws IOException {
		String source = read(WAYSTONE_SOURCE);

		assertTrue(source.contains("new LodestoneTracker(death, false)"),
			"Waystone should create an untracked lodestone target from the player's last death location");
		assertTrue(source.contains("compass.set(DataComponents.LODESTONE_TRACKER, tracker)"),
			"Waystone should write the vanilla tracker component that drives compass angle rendering");
		assertTrue(source.contains("compass.remove(DataComponents.LODESTONE_TRACKER)"),
			"Waystone should remove its temporary tracker when restoring an ordinary compass");
		assertTrue(source.contains("compass.set(DataComponents.LODESTONE_TRACKER, snapshot.originalTracker)"),
			"Waystone should restore pre-existing lodestone bindings");
		assertTrue(source.contains("return stack.is(Items.COMPASS);"),
			"Waystone should update any held compass stack, matching Beacon behavior");
		assertFalse(source.contains("getCount() == 1"),
			"Waystone should not require the player to split a compass stack before the needle turns");
	}

	private static String read(Path file) throws IOException {
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
