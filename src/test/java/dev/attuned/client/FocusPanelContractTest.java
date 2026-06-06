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

		assertEquals(1, countOccurrences(source, "Attunement.activeSlots(player)"),
			"The Focus panel should resolve active slots once per draw.");
		assertEquals(0, countOccurrences(source, "Attunement.used(player)"),
			"The Focus panel should derive used budget from cached active Focus definitions.");
		assertEquals(0, countOccurrences(source, "Apex.capstoneOf(player)"),
			"The Focus panel should derive Apex from cached active Focus definitions.");
		assertTrue(source.contains("List<Optional<Affinity>> orderedActiveAffinities"),
			"Apex resolution should preserve the active slot order and neutral Focus entries.");
		assertTrue(source.contains("orderedActiveAffinities.add(Optional.empty())")
				&& source.contains("orderedActiveAffinities.add(affinity)"),
			"Neutral active Foci should remain Optional.empty() entries for Stillpoint resolution.");
		assertTrue(source.contains("Apex.resolveCapstone(orderedActiveAffinities, used, capacity)"),
			"The Focus panel should resolve Apex from cached ordered affinities and used budget.");
		assertEquals(1, countOccurrences(source, "Resonance.get(player)"),
			"The Focus panel should read resonance once per draw.");
		assertEquals(0, countOccurrences(source, "Resonance.atApex(player)"),
			"The Focus panel should derive Apex readiness from the cached resonance value.");
		assertTrue(source.contains("boolean atApex = capstone.isPresent() && resonance >= Resonance.APEX_THRESHOLD;"),
			"The Focus panel should brighten Apex resonance only when a capstone is actually available.");
		assertTrue(source.contains("drawResonanceRing(graphics, gemX0, y0, affinityColor, resonance, atApex)"),
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
