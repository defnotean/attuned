package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for the inventory Focus panel's per-frame state reads. */
class FocusPanelContractTest {
	private static final Path FOCUS_PANEL =
		Path.of("src/client/java/dev/attuned/client/FocusPanel.java");

	@Test
	void focusPanelReusesCachedAttunementStateForApexAndResonance() throws IOException {
		String source = Files.readString(FOCUS_PANEL, StandardCharsets.UTF_8);

		assertEquals(1, countOccurrences(source, "AttunementReadout.cached(player)"),
			"The Focus panel should reuse the local-player readout snapshot once per draw.");
		assertEquals(0, countOccurrences(source, "Attunement.activeSlots(player)"),
			"The Focus panel should not own active slot resolution after the shared readout cache is available.");
		assertEquals(0, countOccurrences(source, "Attunement.used(player)"),
			"The Focus panel should derive used budget from cached active Focus definitions.");
		assertEquals(0, countOccurrences(source, "Apex.capstoneOf(player)"),
			"The Focus panel should derive Apex from cached active Focus definitions.");
		assertTrue(source.contains("List<Integer> activeSlotsList = readout.activeSlots()"),
			"The Focus panel should take active slots from the shared readout.");
		assertTrue(source.contains("int affinityColor = readout.stanceArgb()"),
			"The Focus panel should take stance colour from the shared readout.");
		assertEquals(0, countOccurrences(source, "Apex.resolveCapstone("),
			"The Focus panel should not duplicate Apex resolution after the shared readout cache is available.");
		assertEquals(0, countOccurrences(source, "Resonance.get(player)"),
			"The Focus panel should not reread resonance after the shared readout cache is available.");
		assertEquals(0, countOccurrences(source, "Resonance.atApex(player)"),
			"The Focus panel should derive Apex readiness from the cached resonance value.");
		assertTrue(source.contains("readout.atApex()"),
			"The Focus panel should brighten Apex resonance only when the shared readout says Apex is active.");
		assertTrue(source.contains("drawResonanceRing(graphics, gemX0, y0, affinityColor, resonance, readout.atApex())"),
			"The resonance ring should reuse the frame's cached resonance state.");
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
