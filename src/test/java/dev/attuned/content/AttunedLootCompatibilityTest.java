package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.attuned.api.focus.Affinity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * Guards the loot-table contract that keeps Attuned rewards compatible with
 * per-player chest mods such as Lootr.
 */
class AttunedLootCompatibilityTest {
	private static final Path FOCUS_DATA_DIR =
		Path.of("src/main/resources/data/attuned/attuned/focus");
	private static final Path FABRIC_MOD_JSON =
		Path.of("src/main/resources/fabric.mod.json");

	@Test
	void everyShippedFocusCanRollInEveryTargetedVanillaTable() throws IOException {
		Map<String, FocusData> foci = focusDataByItemId();

		for (Map.Entry<Identifier, AttunedLoot.Drop> target : AttunedLoot.targetDrops().entrySet()) {
			Identifier table = target.getKey();
			assertEquals("minecraft", table.getNamespace(),
				"Attuned loot should target vanilla tables so Lootr can resolve them: " + table);
			assertTrue(isSupportedVanillaLootPath(table.getPath()),
				"Attuned loot should target reviewed vanilla loot tables: " + table);

			for (Map.Entry<String, FocusData> focus : foci.entrySet()) {
				assertTrue(AttunedLoot.weightForMeta(focus.getValue().affinity(), focus.getValue().faction(), target.getValue()) > 0,
					focus.getKey() + " should stay eligible in " + table);
			}
		}
	}

	@Test
	void worldIntegrationTargetsUseReviewedVanillaTables() {
		Set<String> targets = AttunedLoot.targetDrops().keySet().stream()
			.map(Identifier::getPath)
			.collect(java.util.stream.Collectors.toSet());

		assertTrue(targets.contains("gameplay/fishing/treasure"),
			"Fishing integration should target the reviewed treasure table");
		assertTrue(targets.containsAll(Set.of(
			"archaeology/desert_pyramid",
			"archaeology/desert_well",
			"archaeology/trail_ruins_common",
			"archaeology/trail_ruins_rare",
			"archaeology/ocean_ruin_warm",
			"archaeology/ocean_ruin_cold"
		)), "Archaeology integration should cover the reviewed brushable loot tables");
		assertTrue(targets.containsAll(Set.of(
			"chests/trial_chambers/reward_common",
			"chests/trial_chambers/reward_rare",
			"chests/trial_chambers/reward_unique",
			"chests/trial_chambers/reward_ominous_common",
			"chests/trial_chambers/reward_ominous_rare",
			"chests/trial_chambers/reward_ominous_unique"
		)), "Trial integration should target child reward tables");
		assertTrue(!targets.contains("chests/trial_chambers/reward"),
			"Do not inject into the regular trial parent table and its children");
		assertTrue(!targets.contains("chests/trial_chambers/reward_ominous"),
			"Do not inject into the ominous trial parent table and its children");
	}

	@Test
	void archaeologyTargetsModifyExistingPoolsInsteadOfAppendingExtraPools() {
		for (Identifier table : AttunedLoot.targetDrops().keySet()) {
			if (table.getPath().startsWith("archaeology/")) {
				assertTrue(AttunedLoot.modifiesExistingPools(table),
					"Archaeology should preserve brushable block single-stack generation: " + table);
			} else {
				assertTrue(!AttunedLoot.modifiesExistingPools(table),
					"Only archaeology tables need single-pool injection: " + table);
			}
		}
	}

	@Test
	void unseenThemedTablesBiasUnseenFociWithoutMakingLootrRequired() throws IOException {
		Map<String, FocusData> foci = focusDataByItemId();
		FocusData unseenFocus = foci.get("attuned:veil_focus");
		assertTrue(unseenFocus != null, "Expected an Unseen Focus fixture");

		for (AttunedLoot.Drop drop : AttunedLoot.targetDrops().values()) {
			if (!drop.unseenTheme()) {
				continue;
			}
			int unseenWeight = AttunedLoot.weightForMeta(unseenFocus.affinity(), unseenFocus.faction(), drop);
			int nonFactionWeight = AttunedLoot.weightForMeta(unseenFocus.affinity(), null, drop);
			assertTrue(unseenWeight > nonFactionWeight,
				"Unseen-themed tables should bias Unseen Foci without excluding other Foci");
		}

		JsonObject manifest = JsonParser.parseString(Files.readString(FABRIC_MOD_JSON, StandardCharsets.UTF_8))
			.getAsJsonObject();
		assertTrue(!manifest.getAsJsonObject("depends").has("lootr"),
			"Lootr should remain optional because Attuned uses vanilla loot-table injection");
		assertEquals("*", manifest.getAsJsonObject("suggests").get("lootr").getAsString(),
			"Lootr should stay suggested for modpack discovery");
	}

	private static Map<String, FocusData> focusDataByItemId() throws IOException {
		assertTrue(Files.isDirectory(FOCUS_DATA_DIR), "Could not find FocusDefinition data directory");
		Map<String, FocusData> foci = new TreeMap<>();
		try (Stream<Path> paths = Files.list(FOCUS_DATA_DIR)) {
			for (Path file : paths
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()
					.toList()) {
				JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
					.getAsJsonObject();
				String item = requiredString(root, "item", file);
				foci.put(item, new FocusData(
					optionalAffinity(root.get("affinity"), file),
					optionalIdentifier(root.get("faction"), file)));
			}
		}
		assertTrue(!foci.isEmpty(), "Expected shipped FocusDefinition fixtures");
		return foci;
	}

	private static boolean isSupportedVanillaLootPath(String path) {
		return path.startsWith("chests/")
			|| path.equals("gameplay/fishing/treasure")
			|| path.startsWith("archaeology/");
	}

	private static String requiredString(JsonObject root, String field, Path file) {
		JsonElement element = root.get(field);
		assertTrue(element != null && element.isJsonPrimitive(),
			"FocusDefinition should declare a string " + field + ": " + file);
		return element.getAsString();
	}

	private static Affinity optionalAffinity(JsonElement element, Path file) {
		if (element == null) {
			return null;
		}
		assertTrue(element.isJsonPrimitive(), "FocusDefinition affinity should be a string: " + file);
		return Affinity.valueOf(element.getAsString().toUpperCase());
	}

	private static Identifier optionalIdentifier(JsonElement element, Path file) {
		if (element == null) {
			return null;
		}
		assertTrue(element.isJsonPrimitive(), "FocusDefinition faction should be a string id: " + file);
		String[] parts = element.getAsString().split(":", 2);
		assertEquals(2, parts.length, "FocusDefinition faction should be namespaced: " + file);
		return Identifier.fromNamespaceAndPath(parts[0], parts[1]);
	}

	private record FocusData(Affinity affinity, Identifier faction) {}
}
