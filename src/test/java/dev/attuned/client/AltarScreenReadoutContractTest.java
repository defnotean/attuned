package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for the Altar screen's client readout state usage. */
class AltarScreenReadoutContractTest {
	private static final Path ALTAR_SCREEN =
		Path.of("src/client/java/dev/attuned/client/screen/AltarScreen.java");

	@Test
	void altarScreenConsumesReadoutSnapshotForBudgetStanceDormantAndApex() throws IOException {
		String source = Files.readString(ALTAR_SCREEN, StandardCharsets.UTF_8);

		assertEquals(2, countOccurrences(source, "AttunementReadout.cached(player)"),
			"Background and label passes should each reuse the shared local-player readout snapshot.");
		assertEquals(0, countOccurrences(source, "AttunementReadout.snapshot(player)"),
			"The altar should not rebuild local-player readouts inside both render passes.");
		assertTrue(source.contains("AttunementReadout.budgetFillWidth(BAR_W, used, capacity)"),
			"The altar budget bar should use the pre-resolved readout budget.");
		assertTrue(source.contains("CombatHud.drawPlayerGem(graphics, stanceGemX, stanceGemY, stanceGemSize"),
			"The altar should still draw the stance gem.");
		assertTrue(source.contains("readout.capstone().orElse(null)") && source.contains("readout.atApex()"),
			"The altar stance gem should use pre-resolved Apex state.");
		assertTrue(source.contains("detailLine(readout)"),
			"The detail line should consume pre-resolved dormant, Pact, and Apex state.");
		assertTrue(source.contains("AttunementReadout.stanceLabel(readout)"),
			"The stance label should consume pre-resolved stance state.");
		assertTrue(!source.contains("dormantFocusCount(Player player)"),
			"Dormant Focus count should come from the readout snapshot.");
		assertEquals(0, countOccurrences(source, "Attunement.used(player)"),
			"The altar render path should not recompute used budget.");
		assertEquals(0, countOccurrences(source, "Attunement.activeSlots(player)"),
			"The altar render path should not recompute active slots.");
		assertEquals(0, countOccurrences(source, "Attunement.committedAffinity(player)"),
			"The altar render path should not recompute committed affinity.");
		assertEquals(0, countOccurrences(source, "Attunement.isDiscord(player)"),
			"The altar render path should not recompute Discord.");
		assertEquals(0, countOccurrences(source, "Apex.capstoneOf(player)"),
			"The altar render path should not recompute Apex.");
		assertEquals(0, countOccurrences(source, "Pacts.activeOf(player)"),
			"The altar render path should not recompute Pact state.");
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
