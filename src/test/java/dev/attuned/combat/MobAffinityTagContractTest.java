package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.attuned.api.focus.Affinity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Contract coverage for the mob-affinity tag system after the eight-affinity
 * PvE rebalance. Every {@link Affinity} now ships a {@code attuned:*_mobs}
 * entity-type tag, and {@link MobAffinities#of} wires each one. The tags must
 * stay pairwise disjoint so the first-match lookup is unambiguous, and each
 * must stay non-empty so no affinity is left without hostile representatives.
 *
 * <p>These assertions are static (file + source inspection) because the test
 * harness cannot Bootstrap Minecraft to load the registry-backed tags.
 */
class MobAffinityTagContractTest {
	private static final Path TAG_DIR =
		Path.of("src/main/resources/data/attuned/tags/entity_type");
	private static final Path MOB_AFFINITIES_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/MobAffinities.java");

	@Test
	void everyAffinityShipsANonEmptyMobTag() throws IOException {
		for (Affinity affinity : Affinity.values()) {
			Set<String> values = tagValues(affinity);
			assertFalse(values.isEmpty(),
				affinity + " should ship a non-empty " + tagFileName(affinity));
		}
	}

	@Test
	void mobTagsArePairwiseDisjoint() throws IOException {
		Map<String, Affinity> owner = new LinkedHashMap<>();
		for (Affinity affinity : Affinity.values()) {
			for (String entity : tagValues(affinity)) {
				Affinity previous = owner.put(entity, affinity);
				assertTrue(previous == null,
					entity + " appears in both " + previous + " and " + affinity
						+ " mob tags; tags must be pairwise disjoint so MobAffinities.of() is unambiguous");
			}
		}
	}

	@Test
	void mobAffinitiesWiresAllEightAffinities() throws IOException {
		String source = Files.readString(MOB_AFFINITIES_SOURCE, StandardCharsets.UTF_8);
		for (Affinity affinity : Affinity.values()) {
			String constant = affinity.name() + "_MOBS";
			String tagName = affinity.getSerializedName() + "_mobs";
			assertTrue(source.contains(constant),
				"MobAffinities should declare the " + constant + " tag constant");
			assertTrue(source.contains("\"" + tagName + "\""),
				"MobAffinities should bind the " + tagName + " tag id");
			assertTrue(source.contains("Affinity." + affinity.name()),
				"MobAffinities.of() should return Affinity." + affinity.name());
		}
	}

	private static Set<String> tagValues(Affinity affinity) throws IOException {
		Path file = TAG_DIR.resolve(tagFileName(affinity));
		assertTrue(Files.isRegularFile(file), "Missing mob tag file: " + file);
		JsonObject root = JsonParser.parseString(
			Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
		JsonArray array = root.getAsJsonArray("values");
		Set<String> values = new TreeSet<>();
		for (JsonElement element : array) {
			values.add(element.getAsString());
		}
		assertEquals(array.size(), values.size(),
			tagFileName(affinity) + " should not list a mob twice");
		// Touch a stable set to keep ordering deterministic across JVMs.
		return new LinkedHashSet<>(values);
	}

	private static String tagFileName(Affinity affinity) {
		return affinity.name().toLowerCase(Locale.ROOT) + "_mobs.json";
	}
}
