package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.attuned.api.focus.Affinity;
import dev.attuned.combat.Apex;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Contract coverage for single-affinity, Discord, and neutral Apex resolution. */
class ApexCapstoneContractTest {

	@Test
	void singleAffinityApexesStillResolveToTheirOriginalCapstones() {
		assertEquals(Apex.Capstone.EXECUTE, Apex.resolveCapstone(
			List.of(focus(Affinity.FURY), focus(Affinity.FURY), focus(Affinity.FURY), focus(Affinity.FURY)),
			11, 12).orElseThrow());
		assertEquals(Apex.Capstone.UNYIELDING, Apex.resolveCapstone(
			List.of(focus(Affinity.BASTION), focus(Affinity.BASTION), focus(Affinity.BASTION), focus(Affinity.BASTION)),
			11, 12).orElseThrow());
		assertEquals(Apex.Capstone.UNTOUCHABLE, Apex.resolveCapstone(
			List.of(focus(Affinity.ZEPHYR), focus(Affinity.ZEPHYR), focus(Affinity.ZEPHYR), focus(Affinity.ZEPHYR)),
			11, 12).orElseThrow());
		assertEquals(Apex.Capstone.JUDGMENT, Apex.resolveCapstone(
			List.of(focus(Affinity.HOLY), focus(Affinity.HOLY), focus(Affinity.HOLY), focus(Affinity.HOLY)),
			11, 12).orElseThrow());
	}

	@Test
	void maelstromRequiresEveryShippedAffinityAndAllowsNeutralFoci() {
		assertEquals(Apex.Capstone.MAELSTROM, Apex.resolveCapstone(
			List.of(
				focus(Affinity.FURY),
				focus(Affinity.BASTION),
				focus(Affinity.ZEPHYR),
				focus(Affinity.HOLY),
				neutral()),
			14, 15).orElseThrow());

		assertTrue(Apex.resolveCapstone(
			List.of(focus(Affinity.FURY), focus(Affinity.BASTION), focus(Affinity.ZEPHYR), neutral()),
			11, 12).isEmpty(), "Holy cannot be skipped now that it is a shipped affinity");
	}

	@Test
	void stillpointRequiresAnAllNeutralBuild() {
		assertEquals(Apex.Capstone.STILLPOINT, Apex.resolveCapstone(
			List.of(neutral(), neutral(), neutral(), neutral()),
			11, 12).orElseThrow());

		assertTrue(Apex.resolveCapstone(
			List.of(neutral(), neutral(), neutral(), focus(Affinity.FURY)),
			11, 12).isEmpty(), "Any active affinity Focus should break Stillpoint");
	}

	@Test
	void apexStillRequiresFourActiveFociAndNearFullCapacity() {
		assertTrue(Apex.resolveCapstone(
			List.of(focus(Affinity.FURY), focus(Affinity.FURY), focus(Affinity.FURY)),
			9, 10).isEmpty());
		assertTrue(Apex.resolveCapstone(
			List.of(focus(Affinity.FURY), focus(Affinity.FURY), focus(Affinity.FURY), focus(Affinity.FURY)),
			9, 12).isEmpty());
		assertTrue(Apex.resolveCapstone(
			List.of(focus(Affinity.FURY), focus(Affinity.FURY), focus(Affinity.FURY), focus(Affinity.FURY)),
			13, 12).isEmpty());
	}

	@Test
	void nearFullCapacityGateAllowsExactlyOneUnusedBudgetPoint() {
		assertEquals(Apex.Capstone.EXECUTE, Apex.resolveCapstone(
			List.of(focus(Affinity.FURY), focus(Affinity.FURY), focus(Affinity.FURY), focus(Affinity.FURY)),
			11, 12).orElseThrow());
	}

	@Test
	void neutralFociDoNotCompleteSingleAffinityCapstones() {
		assertTrue(Apex.resolveCapstone(
			List.of(focus(Affinity.FURY), focus(Affinity.FURY), focus(Affinity.FURY), neutral()),
			11, 12).isEmpty());
	}

	@Test
	void newCapstonesExposePlayerFacingNamesAndDescriptions() {
		assertEquals("Maelstrom", Apex.Capstone.MAELSTROM.displayName());
		assertEquals("Stillpoint", Apex.Capstone.STILLPOINT.displayName());
		assertTrue(Apex.Capstone.MAELSTROM.description().contains("scramble"));
		assertTrue(Apex.Capstone.STILLPOINT.description().contains("Absorption"));
		assertTrue(Apex.Capstone.MAELSTROM.affinity().isEmpty());
		assertTrue(Apex.Capstone.STILLPOINT.affinity().isEmpty());
	}

	private static Optional<Affinity> focus(Affinity affinity) {
		return Optional.of(affinity);
	}

	private static Optional<Affinity> neutral() {
		return Optional.empty();
	}
}
