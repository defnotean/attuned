package dev.attuned.synergy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.attuned.api.focus.Affinity;
import dev.attuned.combat.Apex;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Guardrails for every first-class affinity owning full endgame identity coverage. */
class AffinityIdentityCoverageContractTest {
	private static final Path FOCUS_DIR = Path.of("src/main/resources/data/attuned/attuned/focus");
	private static final Path SYNERGY_DIR = Path.of("src/main/resources/data/attuned/attuned/synergy");

	@Test
	void everyAffinityResolvesToAUniqueApexCapstone() {
		Set<Apex.Capstone> capstones = EnumSet.noneOf(Apex.Capstone.class);
		for (Affinity affinity : Affinity.values()) {
			Optional<Apex.Capstone> resolved = Apex.Capstone.ofAffinity(affinity);
			assertTrue(resolved.isPresent(), affinity + " must resolve to a committed Apex capstone.");
			Apex.Capstone capstone = resolved.get();
			assertEquals(affinity, capstone.affinity().orElseThrow(),
				affinity + " capstone should point back at the same affinity.");
			assertFalse(capstone.displayName().isBlank(), affinity + " capstone needs a player-facing name.");
			assertFalse(capstone.description().isBlank(), affinity + " capstone needs player-facing rules text.");
			assertTrue(capstones.add(capstone), affinity + " should not share an Apex capstone with another affinity.");
		}
		assertEquals(Affinity.values().length, capstones.size(),
			"Every first-class affinity should own exactly one committed capstone.");
	}

	@Test
	void everyAffinityHasAtLeastOnePureAffinityConfluence() throws IOException {
		Map<String, Affinity> focusAffinities = focusAffinities();
		EnumMap<Affinity, List<String>> confluencesByAffinity = new EnumMap<>(Affinity.class);
		for (Affinity affinity : Affinity.values()) {
			confluencesByAffinity.put(affinity, new ArrayList<>());
		}

		try (Stream<Path> stream = Files.list(SYNERGY_DIR)) {
			for (Path file : stream.filter(path -> path.toString().endsWith(".json")).toList()) {
				String name = file.getFileName().toString().replace(".json", "");
				JsonObject json = JsonParser.parseString(read(file)).getAsJsonObject();
				JsonArray members = json.getAsJsonArray("members");
				if (members == null || members.size() < 2) {
					continue;
				}
				Affinity shared = null;
				boolean pure = true;
				for (int i = 0; i < members.size(); i++) {
					String member = members.get(i).getAsString();
					Affinity affinity = focusAffinities.get(member);
					if (affinity == null || (shared != null && affinity != shared)) {
						pure = false;
						break;
					}
					shared = affinity;
				}
				if (pure && shared != null) {
					confluencesByAffinity.get(shared).add(name);
				}
			}
		}

		List<String> missing = new ArrayList<>();
		for (Affinity affinity : Affinity.values()) {
			if (confluencesByAffinity.get(affinity).isEmpty()) {
				missing.add(affinity.getSerializedName());
			}
		}
		assertTrue(missing.isEmpty(),
			"Every first-class affinity needs at least one pure-affinity Confluence. Missing: " + missing
				+ "; existing coverage: " + confluencesByAffinity);
	}

	private static Map<String, Affinity> focusAffinities() throws IOException {
		Map<String, Affinity> affinities = new java.util.HashMap<>();
		try (Stream<Path> stream = Files.list(FOCUS_DIR)) {
			for (Path file : stream.filter(path -> path.toString().endsWith(".json")).toList()) {
				JsonObject json = JsonParser.parseString(read(file)).getAsJsonObject();
				if (!json.has("item") || !json.has("affinity")) {
					continue;
				}
				String item = json.get("item").getAsString();
				String serialized = json.get("affinity").getAsString();
				Affinity affinity = null;
				for (Affinity candidate : Affinity.values()) {
					if (candidate.getSerializedName().equals(serialized)) {
						affinity = candidate;
						break;
					}
				}
				if (affinity != null) {
					affinities.put(item, affinity);
				}
			}
		}
		return affinities;
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
