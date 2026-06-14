package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every faction's Foci must share at most one affinity (neutral Foci aside), so a
 * player can run a full faction loadout without being forced into Discord. A
 * faction whose Foci span two or more affinities would make its own intended
 * build discordant, which is the opposite of a coherent faction identity.
 */
class FactionAffinityCoherenceContractTest {
	private static final Path FOCUS_DATA_DIR =
		Path.of("src/main/resources/data/attuned/attuned/focus");

	@Test
	void everyFactionStaysAffinityCoherent() throws IOException {
		Map<String, Set<String>> affinitiesByFaction = new TreeMap<>();
		try (Stream<Path> paths = Files.list(FOCUS_DATA_DIR)) {
			for (Path file : paths
					.filter(p -> p.getFileName().toString().endsWith(".json"))
					.sorted()
					.toList()) {
				JsonObject root = JsonParser.parseString(
					Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
				if (!root.has("faction") || !root.has("affinity")) {
					continue;
				}
				affinitiesByFaction
					.computeIfAbsent(root.get("faction").getAsString(), key -> new TreeSet<>())
					.add(root.get("affinity").getAsString());
			}
		}

		assertTrue(!affinitiesByFaction.isEmpty(), "Expected to find faction-tagged Foci with affinities");
		for (Map.Entry<String, Set<String>> entry : affinitiesByFaction.entrySet()) {
			assertTrue(entry.getValue().size() <= 1,
				"Faction " + entry.getKey() + " spans multiple affinities " + entry.getValue()
					+ " — a full faction build would be forced into Discord. Give its affinity-bearing "
					+ "Foci a single shared affinity (neutral Foci are exempt).");
		}
	}
}
