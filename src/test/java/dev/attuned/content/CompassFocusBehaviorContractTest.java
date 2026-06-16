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
	private static final Path DRIFTGLASS_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/DriftglassBehavior.java");
	private static final Path WAYSTONE_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/WaystoneBehavior.java");
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void beaconCompassUsesVanillaLodestoneTrackerForNeedleAngle() throws IOException {
		String source = read(BEACON_SOURCE);

		assertTrue(source.contains("new LodestoneTracker(Optional.of(home), false)")
				|| source.contains("CompassTags.target(home)"),
			"Beacon should create an untracked lodestone target from the player's respawn point");
		assertTrue(source.contains("compass.set(DataComponents.LODESTONE_TRACKER, tracker)")
				|| source.contains("CompassTags.setLodestone(compass, tracker)"),
			"Beacon should write the vanilla tracker component that drives compass angle rendering");
		assertTrue(source.contains("compass.set(DataComponents.CUSTOM_NAME, BEACON_COMPASS_NAME)")
				|| source.contains("CompassTags.setCustomName(compass, BEACON_COMPASS_NAME)"),
			"Beacon should give redirected compasses a mod-specific display name");
		assertTrue(source.contains("compass.remove(DataComponents.LODESTONE_TRACKER)")
				|| source.contains("CompassTags.setLodestone(compass, null)"),
			"Beacon should remove its temporary tracker when restoring an ordinary compass");
		assertTrue(source.contains("compass.set(DataComponents.LODESTONE_TRACKER, snapshot.originalTracker)")
				|| source.contains("CompassTags.setLodestone(compass, snapshot.originalTracker)"),
			"Beacon should restore pre-existing lodestone bindings");
		assertTrue(source.contains("compass.remove(DataComponents.CUSTOM_NAME)")
				|| source.contains("CompassTags.setCustomName(compass, null)"),
			"Beacon should remove its temporary display name from ordinary compasses");
		assertTrue(source.contains("compass.set(DataComponents.CUSTOM_NAME, snapshot.originalName)")
				|| source.contains("CompassTags.setCustomName(compass, snapshot.originalName)"),
			"Beacon should restore player-supplied custom names");
		assertTrue(source.contains("held.is(Items.COMPASS)"),
			"Beacon should apply only to held vanilla compasses");
		assertTrue(source.contains("AttunedPlayerCleanup.onForgetPlayer(this::restorePlayer)"),
			"Beacon should restore changed compasses through the central player cleanup coordinator");
		assertTrue(source.contains("AttunedServerCleanup.onStop(this::restoreAllCompasses)"),
			"Beacon should restore any redirected compass stacks when the server stops");
		assertTrue(source.contains("private void restoreAllCompasses()"),
			"Beacon should centralize full compass restoration in a named helper");
		assertTrue(source.contains("changedCompasses.clear();"),
			"Beacon should drop remembered compass snapshots after full restoration");
		assertFalse(source.contains("ServerPlayConnectionEvents.DISCONNECT.register"),
			"Beacon should not register its own duplicate disconnect hook");
	}

	@Test
	void driftglassCompassUsesReturnPointAndRestoresOnServerStop() throws IOException {
		String source = read(DRIFTGLASS_SOURCE);

		assertTrue(source.contains("new LodestoneTracker(Optional.of(point), false)")
				|| source.contains("CompassTags.target(point)"),
			"Driftglass should create an untracked lodestone target from the latest return point");
		assertTrue(source.contains("compass.set(DataComponents.LODESTONE_TRACKER, tracker)")
				|| source.contains("CompassTags.setLodestone(compass, tracker)"),
			"Driftglass should write the vanilla tracker component that drives compass angle rendering");
		assertTrue(source.contains("compass.set(DataComponents.CUSTOM_NAME, DRIFTGLASS_COMPASS_NAME)")
				|| source.contains("CompassTags.setCustomName(compass, DRIFTGLASS_COMPASS_NAME)"),
			"Driftglass should give redirected compasses a mod-specific display name");
		assertTrue(source.contains("AttunedServerCleanup.onStop(this::restoreAllCompasses)"),
			"Driftglass should restore any redirected compass stacks when the server stops");
		assertTrue(source.contains("changedCompasses.clear();"),
			"Driftglass should drop remembered compass snapshots after full restoration");
		assertTrue(source.contains("points.clear();"),
			"Driftglass should drop saved return points when the server stops");
		assertFalse(source.contains("ServerPlayConnectionEvents.DISCONNECT.register"),
			"Driftglass should not register its own duplicate disconnect hook");
	}

	@Test
	void driftglassDefersToHigherPriorityCompassFoci() throws IOException {
		String source = read(DRIFTGLASS_SOURCE);

		assertTrue(source.contains("hasHigherPriorityCompassFocus(player)"),
			"Driftglass should not nest a temporary tracker over Beacon or Waystone");
		assertTrue(source.contains("restorePlayer(player);"),
			"Driftglass should restore its own redirected compasses while deferring");
		assertTrue(source.contains("BEACON_FOCUS.equals(id)"),
			"Beacon should have priority over Driftglass for compass control");
		assertTrue(source.contains("WAYSTONE_FOCUS.equals(id)"),
			"Waystone should have priority over Driftglass for compass control");
		assertTrue(source.contains("player.getRespawnConfig() != null")
				|| source.contains("player.getRespawnPosition() != null"),
			"Driftglass should defer to Beacon only when Beacon has a respawn target");
		assertTrue(source.contains("player.getLastDeathLocation().isPresent()"),
			"Driftglass should defer to Waystone only when Waystone has a death target");
	}

	@Test
	void waystoneCompassUsesLastDeathTrackerAndSupportsHeldStacks() throws IOException {
		String source = read(WAYSTONE_SOURCE);

		assertTrue(source.contains("new LodestoneTracker(death, false)")
				|| source.contains("CompassTags.target(death.get())"),
			"Waystone should create an untracked lodestone target from the player's last death location");
		assertTrue(source.contains("compass.set(DataComponents.LODESTONE_TRACKER, tracker)")
				|| source.contains("CompassTags.setLodestone(compass, tracker)"),
			"Waystone should write the vanilla tracker component that drives compass angle rendering");
		assertTrue(source.contains("compass.set(DataComponents.CUSTOM_NAME, WAYSTONE_COMPASS_NAME)")
				|| source.contains("CompassTags.setCustomName(compass, WAYSTONE_COMPASS_NAME)"),
			"Waystone should give redirected compasses a mod-specific display name");
		assertTrue(source.contains("compass.remove(DataComponents.LODESTONE_TRACKER)")
				|| source.contains("CompassTags.setLodestone(compass, null)"),
			"Waystone should remove its temporary tracker when restoring an ordinary compass");
		assertTrue(source.contains("compass.set(DataComponents.LODESTONE_TRACKER, snapshot.originalTracker)")
				|| source.contains("CompassTags.setLodestone(compass, snapshot.originalTracker)"),
			"Waystone should restore pre-existing lodestone bindings");
		assertTrue(source.contains("compass.remove(DataComponents.CUSTOM_NAME)")
				|| source.contains("CompassTags.setCustomName(compass, null)"),
			"Waystone should remove its temporary display name from ordinary compasses");
		assertTrue(source.contains("compass.set(DataComponents.CUSTOM_NAME, snapshot.originalName)")
				|| source.contains("CompassTags.setCustomName(compass, snapshot.originalName)"),
			"Waystone should restore player-supplied custom names");
		assertTrue(source.contains("return stack.is(Items.COMPASS);"),
			"Waystone should update any held compass stack, matching Beacon behavior");
		assertTrue(source.contains("AttunedServerCleanup.onStop(this::restoreAllCompasses)"),
			"Waystone should restore any redirected compass stacks when the server stops");
		assertTrue(source.contains("private void restoreAllCompasses()"),
			"Waystone should centralize full compass restoration in a named helper");
		assertTrue(source.contains("changedCompasses.clear();"),
			"Waystone should drop remembered compass snapshots after full restoration");
		assertFalse(source.contains("getCount() == 1"),
			"Waystone should not require the player to split a compass stack before the needle turns");
	}

	@Test
	void waystoneDefersToBeaconOnlyWhenBeaconHasTarget() throws IOException {
		String source = read(WAYSTONE_SOURCE);

		assertTrue(source.contains("hasActiveBeaconTarget(player)"),
			"Waystone should defer to Beacon only when Beacon can actually point home");
		assertTrue(source.contains("player.getRespawnConfig() == null")
				|| source.contains("player.getRespawnPosition() == null"),
			"Waystone should not let a targetless Beacon block last-death tracking");
		assertTrue(source.contains("BEACON_FOCUS.equals(id)"),
			"Waystone should still give Beacon priority when Beacon has a respawn target");
	}

	@Test
	void redirectedCompassNamesHaveTranslations() throws IOException {
		String lang = read(LANG_FILE);

		assertTrue(lang.contains("\"item.attuned.beacon_compass\": \"Homebound Compass\""),
			"Beacon's temporary compass name should resolve in English");
		assertTrue(lang.contains("\"item.attuned.waystone_compass\": \"Waystone Compass\""),
			"Waystone's temporary compass name should resolve in English");
		assertTrue(lang.contains("\"item.attuned.driftglass_compass\": \"Driftglass Compass\""),
			"Driftglass's temporary compass name should resolve in English");
	}

	private static String read(Path file) throws IOException {
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
