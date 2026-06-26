package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.attuned.AttunedConfig;
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
	private static final Path LOOT_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedLoot.java");
	private static final Path FOCUS_DATA_DIR =
		Path.of("src/main/resources/data/attuned/attuned/focus");
	private static final Path NEOFORGE_MODS_TOML =
		Path.of("src/main/resources/META-INF/neoforge.mods.toml");
	private static final float EPSILON = 0.00001F;

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
	void lootFocusUniverseComesFromFocusDefinitionRegistry() throws IOException {
		String source = Files.readString(LOOT_SOURCE, StandardCharsets.UTF_8);

		assertTrue(source.contains("lookup.listElements()"),
			"Loot injection should build Focus candidates from the synced FocusDefinition registry.");
		assertTrue(!source.contains("AttunedContent.FOCI"),
			"Loot injection should not cap drops to the static shipped-Foci list.");
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
	void defaultLootConfigPreservesAppendedPoolRollChances() {
		assertEquals(0.0875F, AttunedLoot.focusChance(AttunedConfig.DEFAULT, drop(AttunedLoot.Tier.LOW)), EPSILON);
		assertEquals(0.175F, AttunedLoot.focusChance(AttunedConfig.DEFAULT, drop(AttunedLoot.Tier.COMMON)), EPSILON);
		assertEquals(0.25F, AttunedLoot.focusChance(AttunedConfig.DEFAULT, drop(AttunedLoot.Tier.RICH)), EPSILON);
		assertEquals(0.45F, AttunedLoot.focusChance(AttunedConfig.DEFAULT, drop(AttunedLoot.Tier.TREASURE)), EPSILON);

		assertEquals(0.175F, AttunedLoot.fragmentChance(AttunedConfig.DEFAULT, 0.0875F), EPSILON);
		assertEquals(0.35F, AttunedLoot.fragmentChance(AttunedConfig.DEFAULT, 0.175F), EPSILON);
		assertEquals(0.5F, AttunedLoot.fragmentChance(AttunedConfig.DEFAULT, 0.25F), EPSILON);
		assertEquals(0.9F, AttunedLoot.fragmentChance(AttunedConfig.DEFAULT, 0.45F), EPSILON);
	}

	@Test
	void lootConfigMultipliersTuneTiersAndClampFinalChances() {
		AttunedConfig tuned = config(0.25F, 2.0F, 0.5F, 3.0F, 4.0F, 0.25F);

		assertEquals(0.175F, AttunedLoot.focusChance(tuned, drop(AttunedLoot.Tier.LOW)), EPSILON);
		assertEquals(0.0875F, AttunedLoot.focusChance(tuned, drop(AttunedLoot.Tier.COMMON)), EPSILON);
		assertEquals(0.75F, AttunedLoot.focusChance(tuned, drop(AttunedLoot.Tier.RICH)), EPSILON);
		assertEquals(1.0F, AttunedLoot.focusChance(tuned, drop(AttunedLoot.Tier.TREASURE)), EPSILON);
		assertEquals(0.375F, AttunedLoot.fragmentChance(tuned, 0.75F), EPSILON);
	}

	@Test
	void configCodecDefaultsLootMultipliersAndRejectsMalformedValues() {
		JsonObject legacy = new JsonObject();
		legacy.addProperty("focus_loot_chance", 0.25F);

		AttunedConfig parsed = AttunedConfig.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
		assertEquals(AttunedConfig.DEFAULT.lowLootMultiplier(), parsed.lowLootMultiplier(), EPSILON);
		assertEquals(AttunedConfig.DEFAULT.commonLootMultiplier(), parsed.commonLootMultiplier(), EPSILON);
		assertEquals(AttunedConfig.DEFAULT.richLootMultiplier(), parsed.richLootMultiplier(), EPSILON);
		assertEquals(AttunedConfig.DEFAULT.treasureLootMultiplier(), parsed.treasureLootMultiplier(), EPSILON);
		assertEquals(AttunedConfig.DEFAULT.shardFragmentLootMultiplier(), parsed.shardFragmentLootMultiplier(), EPSILON);

		JsonObject malformed = new JsonObject();
		malformed.addProperty("common_loot_multiplier", -1.0F);
		assertTrue(AttunedConfig.CODEC.parse(JsonOps.INSTANCE, malformed).result().isEmpty(),
			"Malformed loot multipliers should still fail config parsing so load() falls back to defaults");
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

		String metadata = Files.readString(NEOFORGE_MODS_TOML, StandardCharsets.UTF_8);
		assertTrue(metadata.contains("modId=\"lootr\""),
			"Lootr should stay listed for modpack discovery.");
		assertTrue(metadata.contains("type=\"optional\""),
			"Lootr should remain optional because Attuned uses vanilla loot-table injection.");
	}

	@Test
	void fishingTreasureBiasesSeafarersWithoutAddingCombatWeight() throws IOException {
		Map<String, FocusData> foci = focusDataByItemId();
		AttunedLoot.Drop fishing = AttunedLoot.targetDrops()
			.get(Identifier.fromNamespaceAndPath("minecraft", "gameplay/fishing/treasure"));
		assertTrue(fishing != null && fishing.fishingTheme(),
			"Fishing treasure should use the Seafarers-themed drop");

		FocusData seafarer = foci.get("attuned:linecast_focus");
		FocusData neutral = foci.get("attuned:forager_focus");
		assertTrue(seafarer != null, "Expected a Seafarers Focus fixture");
		assertTrue(neutral != null, "Expected a neutral non-faction Focus fixture");

		int seafarerWeight = AttunedLoot.weightForMeta(seafarer.affinity(), seafarer.faction(), fishing);
		int neutralWeight = AttunedLoot.weightForMeta(neutral.affinity(), neutral.faction(), fishing);
		assertTrue(seafarerWeight > neutralWeight,
			"Fishing treasure should gently bias Seafarers Foci without excluding other Foci");
	}

	@Test
	void dropRecordsRejectMissingTier() {
		NullPointerException missingTier = assertThrows(NullPointerException.class,
			() -> new AttunedLoot.Drop(null, null, false, false));
		assertEquals("tier", missingTier.getMessage());
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

	private static AttunedLoot.Drop drop(AttunedLoot.Tier tier) {
		return new AttunedLoot.Drop(tier, null, false, false);
	}

	private static AttunedConfig config(
			float focusLootChance,
			float lowLootMultiplier,
			float commonLootMultiplier,
			float richLootMultiplier,
			float treasureLootMultiplier,
			float shardFragmentLootMultiplier) {
		return new AttunedConfig(
			AttunedConfig.DEFAULT.startingCapacity(),
			AttunedConfig.DEFAULT.capacityCap(),
			AttunedConfig.DEFAULT.capacityPerShard(),
			focusLootChance,
			lowLootMultiplier,
			commonLootMultiplier,
			richLootMultiplier,
			treasureLootMultiplier,
			shardFragmentLootMultiplier,
			AttunedConfig.DEFAULT.voidstepCooldownTicks(),
			AttunedConfig.DEFAULT.gravebindCooldownTicks(),
			AttunedConfig.DEFAULT.broadcastPactDeaths(),
			AttunedConfig.DEFAULT.surgeIntervalTicks(),
			AttunedConfig.DEFAULT.surgeDurationTicks(),
			AttunedConfig.DEFAULT.surgeRadius(),
			AttunedConfig.DEFAULT.advantageMultiplier(),
			AttunedConfig.DEFAULT.disadvantageMultiplier(),
			AttunedConfig.DEFAULT.discordDamageMultiplier(),
			AttunedConfig.DEFAULT.resonanceHitEmpoweredGainPerDamage(),
			AttunedConfig.DEFAULT.resonanceHitNeutralizedLoss(),
			AttunedConfig.DEFAULT.resonanceKillEmpoweredGain(),
			AttunedConfig.DEFAULT.resonanceDecayPerTick(),
			AttunedConfig.DEFAULT.affinityLoomBaseShardCost(),
			AttunedConfig.DEFAULT.affinityLoomMaxShardCost());
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
