package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for the shared client attunement presentation snapshot. */
class AttunementReadoutContractTest {
	private static final Path READOUT =
		Path.of("src/client/java/dev/attuned/client/AttunementReadout.java");

	@Test
	void snapshotResolvesBudgetStancePactAndApexFromOneCurrentResolution() throws IOException {
		String source = read();

		assertTrue(source.contains("public record Snapshot("),
			"Readout state should have a shared snapshot value.");
		assertTrue(source.contains("public static Snapshot snapshot(Player player)"),
			"Player-bound readout state should be resolved in one snapshot builder.");
		assertEquals(1, countOccurrences(source, "Attunement.resolution(player)"),
			"The readout snapshot should resolve current budget state once.");
		assertTrue(source.contains("List<Integer> activeSlots = resolution.activeSlots()"),
			"The snapshot should reuse the resolution's active slots.");
		assertTrue(source.contains("Map<Integer, BudgetResolver.DormantReason> dormantReasons = resolution.dormantReasons()"),
			"The snapshot should retain the resolution's dormant reasons.");
		assertTrue(source.contains("public int dormant()"),
			"The snapshot should expose a dormant count from its dormant reasons.");
		assertEquals(0, countOccurrences(source, "Attunement.activeSlots(player)"),
			"Readout presentation should not recompute active slots outside the snapshot.");
		assertEquals(0, countOccurrences(source, "Attunement.used(player)"),
			"Readout presentation should derive used budget from cached active definitions.");
		assertEquals(0, countOccurrences(source, "Attunement.dormantReasons(player)"),
			"Readout presentation should use the snapshot dormant count.");
		assertEquals(0, countOccurrences(source, "Attunement.committedAffinity(player)"),
			"Readout presentation should derive committed affinity from cached active definitions.");
		assertEquals(0, countOccurrences(source, "Attunement.isDiscord(player)"),
			"Readout presentation should derive Discord from cached active definitions.");
		assertEquals(0, countOccurrences(source, "Apex.capstoneOf(player)"),
			"Readout presentation should derive Apex from cached ordered active affinities.");
		assertEquals(0, countOccurrences(source, "Pacts.activeOf(player)"),
			"Readout presentation should derive active Pact from cached affinity counts.");
		assertEquals(0, countOccurrences(source, "Pacts.previewOf(player)"),
			"Readout presentation should derive Pact preview from cached affinity counts.");
		assertTrue(source.contains("Apex.resolveCapstone(orderedActiveAffinities, used, capacity)"),
			"Apex should resolve from ordered active affinities and cached budget.");
		assertTrue(source.contains("Optional<Pact> pact = Pacts.activeOf(activeAffinityCounts)"),
			"Pact should resolve through the shared pure Pact helper from cached affinity counts.");
		assertTrue(source.contains("Pacts.previewOf(player, pact, discord, activeAffinityCounts, remaining)"),
			"Pact preview should reuse the shared pure Pact helper with cached readout state.");
		assertTrue(!source.contains("SINGLE_AFFINITY_PACT_THRESHOLD"),
			"Readout presentation should not duplicate Pact thresholds.");
	}

	@Test
	void titleTooltipAndColorsConsumeSnapshot() throws IOException {
		String source = read();

		assertTrue(source.contains("return title(snapshot(player));"),
			"Player-bound title should delegate to the shared snapshot.");
		assertTrue(source.contains("return tooltip(snapshot(player));"),
			"Player-bound tooltip should delegate to the shared snapshot.");
		assertTrue(source.contains("public static MutableComponent title(Snapshot snapshot)"),
			"Title formatting should accept pre-resolved readout state.");
		assertTrue(source.contains("public static List<Component> tooltip(Snapshot snapshot)"),
			"Tooltip formatting should accept pre-resolved readout state.");
		assertTrue(source.contains("public static int apexAwareStanceArgb(Snapshot snapshot)"),
			"Apex-aware colour should accept pre-resolved readout state.");
	}

	@Test
	void snapshotExposesActiveConfluencesAndPreview() throws IOException {
		String source = read();

		assertTrue(source.contains("Set<String> activeConfluences"),
			"Snapshot must carry the active-confluence id set for the HUD.");
		assertTrue(source.contains("activeConfluences = Set.copyOf(activeConfluences)"),
			"Snapshot must defensively copy the confluence set like activeSlots/dormantReasons.");
		assertTrue(source.contains("SynergyResolver.activeConfluences("),
			"snapshot() must compute active confluences via the Minecraft-free resolver.");
		assertTrue(source.contains("SynergyResolver.previewOf("),
			"snapshot() must compute the discovered-only preview via the resolver policy.");
	}

	private static String read() throws IOException {
		return Files.readString(READOUT, StandardCharsets.UTF_8);
	}

	private static int countOccurrences(String value, String needle) {
		int count = 0;
		int index = 0;
		while ((index = value.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
	}
}
