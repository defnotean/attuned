package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.attuned.api.focus.Affinity;
import dev.attuned.combat.Apex;
import java.util.ArrayList;
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
	void maelstromWakesFromADiverseManifoldSpreadAndAllowsNeutralFoci() {
		// New rule: four or more distinct active affinities, none stacked 3+ deep.
		assertEquals(Apex.Capstone.MAELSTROM, Apex.resolveCapstone(
			List.of(
				focus(Affinity.FURY),
				focus(Affinity.BASTION),
				focus(Affinity.ZEPHYR),
				focus(Affinity.HOLY),
				neutral()),
			14, 15).orElseThrow());

		// A diverse spread drawn from the promoted affinities also qualifies.
		assertEquals(Apex.Capstone.MAELSTROM, Apex.resolveCapstone(
			List.of(
				focus(Affinity.TIDE),
				focus(Affinity.FORGE),
				focus(Affinity.VERDANT),
				focus(Affinity.UMBRAL)),
			11, 12).orElseThrow());
	}

	@Test
	void maelstromNeedsFourDistinctAffinitiesWithNoSingleLaneStackedDeep() {
		// Only three distinct active affinities falls short of the diversity gate.
		assertTrue(Apex.resolveCapstone(
			List.of(focus(Affinity.FURY), focus(Affinity.BASTION), focus(Affinity.ZEPHYR), neutral()),
			11, 12).isEmpty(), "Three distinct affinities should not wake Maelstrom");

		// Four distinct, but one affinity is stacked three deep — reads as a
		// committed lane, not a Manifold spread, so no Maelstrom.
		assertTrue(Apex.resolveCapstone(
			List.of(
				focus(Affinity.FURY),
				focus(Affinity.FURY),
				focus(Affinity.FURY),
				focus(Affinity.BASTION),
				focus(Affinity.ZEPHYR),
				focus(Affinity.HOLY)),
			11, 12).isEmpty(), "A single affinity stacked 3+ deep should block Maelstrom");
	}

	@Test
	void promotedSingleAffinityCommitmentResolvesToNoCapstone() {
		// Tide/Forge/Verdant/Umbral own no Apex capstone yet, so a pure commitment
		// to one of them resolves to nothing rather than a placeholder capstone.
		for (Affinity promoted : List.of(Affinity.TIDE, Affinity.FORGE, Affinity.VERDANT, Affinity.UMBRAL)) {
			assertTrue(Apex.resolveCapstone(
				List.of(focus(promoted), focus(promoted), focus(promoted), focus(promoted)),
				11, 12).isEmpty(), promoted + " should resolve to no capstone");
		}
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

	@Test
	void apexResolverRejectsNullAffinityInputsClearly() {
		NullPointerException missingList = assertThrows(NullPointerException.class,
			() -> Apex.resolveCapstone(null, 11, 12));
		assertEquals("activeAffinities", missingList.getMessage());

		List<Optional<Affinity>> activeAffinities = new ArrayList<>();
		activeAffinities.add(focus(Affinity.FURY));
		activeAffinities.add(focus(Affinity.FURY));
		activeAffinities.add(null);
		activeAffinities.add(focus(Affinity.FURY));

		NullPointerException missingAffinity = assertThrows(NullPointerException.class,
			() -> Apex.resolveCapstone(activeAffinities, 11, 12));
		assertEquals("affinity", missingAffinity.getMessage());

		List<Optional<Affinity>> shortMalformedBuild = new ArrayList<>();
		shortMalformedBuild.add(null);

		NullPointerException shortMissingAffinity = assertThrows(NullPointerException.class,
			() -> Apex.resolveCapstone(shortMalformedBuild, 0, 0));
		assertEquals("affinity", shortMissingAffinity.getMessage());
	}

	@Test
	void apexResolverRejectsNegativeBudgetInputsClearly() {
		List<Optional<Affinity>> activeAffinities =
			List.of(focus(Affinity.FURY), focus(Affinity.FURY), focus(Affinity.FURY), focus(Affinity.FURY));

		IllegalArgumentException negativeUsed = assertThrows(IllegalArgumentException.class,
			() -> Apex.resolveCapstone(activeAffinities, -1, 12));
		assertEquals("used budget must be non-negative", negativeUsed.getMessage());

		IllegalArgumentException negativeCapacity = assertThrows(IllegalArgumentException.class,
			() -> Apex.resolveCapstone(activeAffinities, 0, -1));
		assertEquals("capacity must be non-negative", negativeCapacity.getMessage());
	}

	private static Optional<Affinity> focus(Affinity affinity) {
		return Optional.of(affinity);
	}

	private static Optional<Affinity> neutral() {
		return Optional.empty();
	}
}
