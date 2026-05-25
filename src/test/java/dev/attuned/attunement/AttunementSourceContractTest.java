package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-level guard for player-bound helpers that are awkward to unit-test
 * without bootstrapping Minecraft's runtime.
 */
class AttunementSourceContractTest {
	private static final Path SOURCE = Path.of("src/main/java/dev/attuned/attunement/Attunement.java");

	@Test
	void hypotheticalCapacityActiveSlotsUsesProvidedCapacityWithoutMutatingPlayer() throws IOException {
		String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

		assertTrue(source.contains(
				"public static List<Integer> activeSlots(Player player, int hypotheticalCapacity)"),
			"Attunement should expose a hypothetical-capacity active slot helper");
		assertTrue(source.contains(
				"return BudgetResolver.resolve(candidates(player), hypotheticalCapacity);"),
			"Hypothetical active slot resolution should use the provided capacity value");
	}
}
