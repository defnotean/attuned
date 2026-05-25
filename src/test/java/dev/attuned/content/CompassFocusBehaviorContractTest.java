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
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void beaconCompassUsesVanillaLodestoneTrackerForNeedleAngle() throws IOException {
		String source = read(BEACON_SOURCE);

		assertTrue(source.contains("new LodestoneTracker(Optional.of(home), false)"),
			"Beacon should create an untracked lodestone target from the player's respawn point");
		assertTrue(source.contains("compass.set(DataComponents.LODESTONE_TRACKER, tracker)"),
			"Beacon should write the vanilla tracker component that drives compass angle rendering");
		assertTrue(source.contains("compass.set(DataComponents.CUSTOM_NAME, BEACON_COMPASS_NAME)"),
			"Beacon should give redirected compasses a mod-specific display name");
		assertTrue(source.contains("compass.remove(DataComponents.LODESTONE_TRACKER)"),
			"Beacon should remove its temporary tracker when restoring an ordinary compass");
		assertTrue(source.contains("compass.set(DataComponents.LODESTONE_TRACKER, snapshot.originalTracker)"),
			"Beacon should restore pre-existing lodestone bindings");
		assertTrue(source.contains("compass.remove(DataComponents.CUSTOM_NAME)"),
			"Beacon should remove its temporary display name from ordinary compasses");
		assertTrue(source.contains("compass.set(DataComponents.CUSTOM_NAME, snapshot.originalName)"),
			"Beacon should restore player-supplied custom names");
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
		assertTrue(source.contains("compass.set(DataComponents.CUSTOM_NAME, WAYSTONE_COMPASS_NAME)"),
			"Waystone should give redirected compasses a mod-specific display name");
		assertTrue(source.contains("compass.remove(DataComponents.LODESTONE_TRACKER)"),
			"Waystone should remove its temporary tracker when restoring an ordinary compass");
		assertTrue(source.contains("compass.set(DataComponents.LODESTONE_TRACKER, snapshot.originalTracker)"),
			"Waystone should restore pre-existing lodestone bindings");
		assertTrue(source.contains("compass.remove(DataComponents.CUSTOM_NAME)"),
			"Waystone should remove its temporary display name from ordinary compasses");
		assertTrue(source.contains("compass.set(DataComponents.CUSTOM_NAME, snapshot.originalName)"),
			"Waystone should restore player-supplied custom names");
		assertTrue(source.contains("return stack.is(Items.COMPASS);"),
			"Waystone should update any held compass stack, matching Beacon behavior");
		assertFalse(source.contains("getCount() == 1"),
			"Waystone should not require the player to split a compass stack before the needle turns");
	}

	@Test
	void redirectedCompassNamesHaveTranslations() throws IOException {
		String lang = read(LANG_FILE);

		assertTrue(lang.contains("\"item.attuned.beacon_compass\": \"Homebound Compass\""),
			"Beacon's temporary compass name should resolve in English");
		assertTrue(lang.contains("\"item.attuned.waystone_compass\": \"Waystone Compass\""),
			"Waystone's temporary compass name should resolve in English");
	}

	private static String read(Path file) throws IOException {
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
