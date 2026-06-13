package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Behavioral coverage for the pure faction set-bonus resolver. Minecraft-free:
 * the input is the faction id (or {@code ""} for none) of each ACTIVE Focus and
 * the output is the set of factions reaching the threshold.
 */
class FactionSetBonusResolverTest {

	@Test
	void exactlyThreeOfAFactionWakesTheSetBonus() {
		Set<String> active = FactionSetBonusResolver.activeSetFactions(
			List.of("attuned:seafarers", "attuned:seafarers", "attuned:seafarers"), 3);
		assertEquals(Set.of("attuned:seafarers"), active,
			"Three active Foci sharing a faction reach the default threshold.");
	}

	@Test
	void twoOfAFactionIsNotEnough() {
		Set<String> active = FactionSetBonusResolver.activeSetFactions(
			List.of("attuned:seafarers", "attuned:seafarers"), 3);
		assertTrue(active.isEmpty(),
			"Two active Foci of a faction stay below the threshold.");
	}

	@Test
	void mixedFactionsBothAtThresholdBothWake() {
		Set<String> active = FactionSetBonusResolver.activeSetFactions(
			List.of(
				"attuned:seafarers", "attuned:seafarers", "attuned:seafarers",
				"attuned:radiant", "attuned:radiant", "attuned:radiant"),
			3);
		assertEquals(Set.of("attuned:seafarers", "attuned:radiant"), active,
			"Two factions that each reach the threshold both wake.");
	}

	@Test
	void onlyTheFactionAtThresholdWakesInAMixedLoadout() {
		Set<String> active = FactionSetBonusResolver.activeSetFactions(
			List.of(
				"attuned:seafarers", "attuned:seafarers", "attuned:seafarers",
				"attuned:radiant", "attuned:radiant"),
			3);
		assertEquals(Set.of("attuned:seafarers"), active,
			"A faction below the threshold does not wake even when another faction reaches it.");
	}

	@Test
	void emptyStringsAreIgnored() {
		Set<String> active = FactionSetBonusResolver.activeSetFactions(
			List.of("", "", "", "attuned:revenant", "attuned:revenant", "attuned:revenant"), 3);
		assertEquals(Set.of("attuned:revenant"), active,
			"Foci with no faction ('') never count toward any set bonus.");
	}

	@Test
	void emptyInputWakesNothing() {
		assertTrue(FactionSetBonusResolver.activeSetFactions(List.of(), 3).isEmpty(),
			"No active Foci means no set bonuses.");
	}

	@Test
	void allEmptyStringsWakeNothing() {
		assertTrue(FactionSetBonusResolver.activeSetFactions(List.of("", "", "", ""), 3).isEmpty(),
			"A loadout of factionless Foci wakes nothing regardless of count.");
	}

	@Test
	void thresholdIsHonouredWhenItIsNotThree() {
		assertEquals(Set.of("attuned:unseen"),
			FactionSetBonusResolver.activeSetFactions(
				List.of("attuned:unseen", "attuned:unseen"), 2),
			"A lower threshold wakes a faction with fewer matching Foci.");
		assertTrue(
			FactionSetBonusResolver.activeSetFactions(
					List.of("attuned:unseen", "attuned:unseen", "attuned:unseen"), 4)
				.isEmpty(),
			"A higher threshold withholds a faction that has fewer than the threshold.");
	}

	@Test
	void countOnAtOrAboveThresholdStillWakes() {
		assertEquals(Set.of("attuned:ashen_forge"),
			FactionSetBonusResolver.activeSetFactions(
				List.of("attuned:ashen_forge", "attuned:ashen_forge",
					"attuned:ashen_forge", "attuned:ashen_forge"),
				3),
			"More than the threshold still wakes the faction (count >= threshold).");
	}
}
