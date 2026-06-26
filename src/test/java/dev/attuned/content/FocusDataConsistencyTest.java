package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * File-level consistency coverage for shipped Foci that runs without
 * bootstrapping Minecraft registries.
 */
class FocusDataConsistencyTest {
	private static final Path CONTENT_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedContent.java");
	private static final Path REGISTRIES_SOURCE =
		Path.of("src/main/java/dev/attuned/AttunedRegistries.java");
	private static final Path BEHAVIOR_REGISTRATION_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedFocusBehaviors.java");
	private static final Path CREATIVE_TABS_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedCreativeTabs.java");
	private static final Path COMMANDS_SOURCE =
		Path.of("src/main/java/dev/attuned/command/AttunedCommands.java");
	private static final Path FOCUS_LOOKUP_SOURCE =
		Path.of("src/main/java/dev/attuned/attunement/FocusLookup.java");
	private static final Path REFERENCE_DOC =
		Path.of("docs/reference.md");
	private static final Path BLACKOUT_BEHAVIOR_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/BlackoutBehavior.java");
	private static final List<Path> DIRECT_FOCUS_HOOK_SOURCES = List.of(
		Path.of("src/main/java/dev/attuned/combat/AttunedCombat.java"),
		Path.of("src/main/java/dev/attuned/combat/RevenantCombat.java"),
		Path.of("src/main/java/dev/attuned/combat/GravebindSave.java"),
		Path.of("src/main/java/dev/attuned/combat/UnseenCombat.java"));
	private static final Path FOCUS_DATA_DIR =
		Path.of("src/main/resources/data/attuned/attuned/focus");
	private static final Path FOCUS_BEHAVIOR_DIR =
		Path.of("src/main/resources/data/attuned/attuned/focus_behavior");
	private static final Path SYNERGY_DATA_DIR =
		Path.of("src/main/resources/data/attuned/attuned/synergy");
	private static final Path ITEM_DEFINITION_DIR =
		Path.of("src/main/resources/assets/attuned/items");
	private static final Path ITEM_MODEL_DIR =
		Path.of("src/main/resources/assets/attuned/models/item");
	private static final Path ITEM_TEXTURE_DIR =
		Path.of("src/main/resources/assets/attuned/textures/item");
	private static final Path BLOCK_MODEL_DIR =
		Path.of("src/main/resources/assets/attuned/models/block");
	private static final Path BLOCK_TEXTURE_DIR =
		Path.of("src/main/resources/assets/attuned/textures/block");
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	private static final Pattern REGISTERED_FOCUS = Pattern.compile(
		"public\\s+static\\s+final\\s+DeferredItem<Item>\\s+([A-Z0-9_]+_FOCUS)\\s*=\\s*registerFocus\\(\"([a-z0-9_]+_focus)\"\\);");
	private static final Pattern REGISTERED_BEHAVIOR = Pattern.compile(
		"register\\(\\s*\"([a-z0-9_/.-]+)\"\\s*,\\s*new\\s+",
		Pattern.DOTALL);
	private static final Pattern NAMESPACED_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_/.-]+");
	private static final Set<String> UNSEEN_FOCUS_ITEMS = Set.of(
		"attuned:blackout_focus",
		"attuned:mask_focus",
		"attuned:needle_focus",
		"attuned:smoke_focus",
		"attuned:softstep_focus",
		"attuned:veil_focus",
		"attuned:whisper_focus");
	private static final Set<String> SEAFARERS_FOCUS_ITEMS = Set.of(
		"attuned:driftglass_focus",
		"attuned:harborlight_focus",
		"attuned:linecast_focus",
		"attuned:netmender_focus");
	private static final Set<String> OFFSHORE_FOCUS_ITEMS = Set.of(
		"attuned:harpoon_focus");
	private static final Set<String> MINECRAFT_121_BARE_ATTRIBUTE_IDS = Set.of(
		"minecraft:armor",
		"minecraft:armor_toughness",
		"minecraft:attack_damage",
		"minecraft:attack_speed",
		"minecraft:fall_damage_multiplier",
		"minecraft:jump_strength",
		"minecraft:knockback_resistance",
		"minecraft:luck",
		"minecraft:max_health",
		"minecraft:movement_speed",
		"minecraft:safe_fall_distance",
		"minecraft:sneaking_speed",
		"minecraft:water_movement_efficiency");
	private static final Set<String> REVENANT_FOCUS_ITEMS = Set.of(
		"attuned:ashen_debt_focus",
		"attuned:bonechill_focus",
		"attuned:epitaph_focus",
		"attuned:gravebind_focus",
		"attuned:hollowstep_focus",
		"attuned:last_rites_focus");
	private static final Set<String> HOLY_FOCUS_ITEMS = Set.of(
		"attuned:bellwether_focus",
		"attuned:censer_focus",
		"attuned:namesake_focus",
		"attuned:oathguard_focus",
		"attuned:sunlance_focus",
		"attuned:threshold_focus",
		"attuned:votive_focus");
	private static final Map<String, String> WHEEL_COUNTER_FOCUS_AFFINITIES = Map.ofEntries(
		Map.entry("attuned:undertow_focus", "tide"),
		Map.entry("attuned:riptide_heart_focus", "tide"),
		Map.entry("attuned:pearlguard_focus", "tide"),
		Map.entry("attuned:slagbrand_focus", "forge"),
		Map.entry("attuned:anvilheart_focus", "forge"),
		Map.entry("attuned:sparkweld_focus", "forge"),
		Map.entry("attuned:thornwake_focus", "verdant"),
		Map.entry("attuned:seedcall_focus", "verdant"),
		Map.entry("attuned:bramblegate_focus", "verdant"),
		Map.entry("attuned:nullveil_focus", "umbral"),
		Map.entry("attuned:cinderthief_focus", "umbral"),
		Map.entry("attuned:snaremoon_focus", "umbral"));
	private static final Map<String, Double> SEAFARERS_LUCK_AMOUNTS = Map.of(
		"attuned:driftglass_focus", 1.0D,
		"attuned:harborlight_focus", 1.0D,
		"attuned:linecast_focus", 1.0D,
		"attuned:netmender_focus", 1.0D);
	private static final Path REVENANT_SOURCE =
		Path.of("docs/superpowers/assets/revenant-foci/revenant-foci-concept-source.png");
	private static final Path REVENANT_REPORT =
		Path.of("docs/superpowers/assets/revenant-foci/revenant-foci-report.json");

	@Test
	void shippedFocusRegistrationsListAndDefinitionsStayInStep() throws IOException {
		String source = Files.readString(CONTENT_SOURCE, StandardCharsets.UTF_8);

		Map<String, String> registeredItemsByField = registeredFocusItemsByField(source);
		Set<String> registeredItems = new TreeSet<>(registeredItemsByField.values());
		Set<String> definitionItems = focusDefinitionItems();

		assertEquals(registeredItemsByField.size(), registeredItems.size(),
			"Registered shipped Focus item ids should not be duplicated");
		assertTrue(source.contains("public static final List<DeferredItem<Item>> FOCI = List.copyOf(REGISTERED_FOCI);"),
			"AttunedContent.FOCI should be derived from registerFocus order, not manually duplicated");
		assertTrue(source.contains("private static final Set<Identifier> FOCUS_IDS = REGISTERED_FOCI.stream()"),
			"AttunedContent should keep constant-time Focus id membership alongside the ordered list");
		assertTrue(source.contains("REGISTERED_FOCI.add(item);"),
			"registerFocus should append every Focus to the public FOCI snapshot");
		assertTrue(source.contains("public static boolean isFocus(Item item)"),
			"AttunedContent should expose the canonical Focus item membership check");
		assertTrue(source.contains("return item != null && FOCUS_IDS.contains(BuiltInRegistries.ITEM.getKey(item));"),
			"Focus item membership should use the id-backed lookup and reject null safely");
		assertTrue(source.contains("public static boolean isFocus(ItemStack stack)"),
			"AttunedContent should expose the canonical Focus stack membership check");
		assertTrue(!source.contains("FOCI = List.of("),
			"AttunedContent.FOCI should not go back to a hand-maintained List.of");
		assertEquals(registeredItems, definitionItems,
			"Registered shipped Focus items should match datapack FocusDefinition item ids");
	}

	@Test
	void registeredShippedFociHaveClientAssetsAndLanguage() throws IOException {
		String source = Files.readString(CONTENT_SOURCE, StandardCharsets.UTF_8);
		Set<String> registeredItems = new TreeSet<>(registeredFocusItemsByField(source).values());
		JsonObject lang = languageRoot();

		for (String itemId : registeredItems) {
			String name = attunedPath(itemId);
			assertTrue(Files.isRegularFile(ITEM_DEFINITION_DIR.resolve(name + ".json")),
				"Registered Focus should have an item definition asset: " + itemId);
			assertTrue(Files.isRegularFile(ITEM_MODEL_DIR.resolve(name + ".json")),
				"Registered Focus should have an item model asset: " + itemId);
			assertTrue(Files.isRegularFile(ITEM_TEXTURE_DIR.resolve(name + ".png")),
				"Registered Focus should have an item texture asset: " + itemId);
			assertTrue(Files.isRegularFile(ITEM_TEXTURE_DIR.resolve(name + ".png.mcmeta")),
				"Registered Focus should have animated item texture metadata: " + itemId);
			assertAnimatedFocusTexture(name);
			if (SEAFARERS_FOCUS_ITEMS.contains(itemId)) {
				assertReadableSeafarersAnimation(name);
			}
			if (HOLY_FOCUS_ITEMS.contains(itemId)) {
				assertReadableHolyAnimation(name);
			}
			assertLanguageKey(lang, "item.attuned." + name);
			assertLanguageKey(lang, "item.attuned." + name + ".lore");
			assertLanguageKey(lang, "item.attuned." + name + ".lore2");
			assertLanguageKey(lang, "item.attuned." + name + ".effect");
		}
	}

	@Test
	void focusBehaviorIdsAreRegisteredInBehaviorRegistry() throws IOException {
		String source = Files.readString(BEHAVIOR_REGISTRATION_SOURCE, StandardCharsets.UTF_8);
		Set<String> referencedBehaviors = focusDefinitionBehaviorIds();
		Set<String> registeredBehaviors = registeredBehaviorIds(source);
		registeredBehaviors.addAll(dataBehaviorIds());

		Set<String> missingBehaviors = new TreeSet<>(referencedBehaviors);
		missingBehaviors.removeAll(registeredBehaviors);
		assertEquals(Set.of(), missingBehaviors,
			"Every FocusDefinition behavior id should be registered in AttunedFocusBehaviors");
	}

	@Test
	void focusBehaviorRegistryRejectsDuplicateIdsAndCoordinatorIsIdempotent() throws IOException {
		String registry = Files.readString(REGISTRIES_SOURCE, StandardCharsets.UTF_8);
		String registrations = Files.readString(BEHAVIOR_REGISTRATION_SOURCE, StandardCharsets.UTF_8);

		assertTrue(registry.contains("BEHAVIORS.putIfAbsent(id, behavior)"),
			"Focus behavior registration should fail fast instead of silently replacing duplicate ids");
		assertTrue(registry.contains("Duplicate Focus behavior id"),
			"Duplicate behavior ids should produce a clear startup error");
		assertTrue(registrations.contains("private static boolean initialized;"),
			"AttunedFocusBehaviors should guard against repeated init calls");
		assertTrue(registrations.contains("if (initialized)"),
			"AttunedFocusBehaviors should skip duplicate init work");
		assertTrue(registrations.contains("initialized = true;"),
			"AttunedFocusBehaviors should mark successful registration as complete");
		assertBefore(registrations, "initialized = true;", "register(\"tide\", new TideBehavior())");
	}

	@Test
	void focusLookupRejectsDuplicateDefinitionItemsInsteadOfReplacingThem() throws IOException {
		String source = Files.readString(FOCUS_LOOKUP_SOURCE, StandardCharsets.UTF_8);

		assertTrue(source.contains("putIfAbsent("),
			"FocusLookup should detect duplicate FocusDefinition item keys before caching lookups");
		assertTrue(source.contains("Duplicate FocusDefinition item"),
			"Duplicate FocusDefinition item keys should produce a clear startup error");
	}

	@Test
	void shippedFociDeclareGameplayEffectsOrDirectRuntimeHooks() throws IOException {
		Map<String, String> registeredFieldsByItem = new TreeMap<>();
		registeredFocusItemsByField(Files.readString(CONTENT_SOURCE, StandardCharsets.UTF_8))
			.forEach((field, item) -> registeredFieldsByItem.put(item, field));
		String directHookSources = directFocusHookSources();
		Set<String> inertItems = new TreeSet<>();
		try (Stream<Path> paths = Files.list(FOCUS_DATA_DIR)) {
			for (Path file : paths
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()
					.toList()) {
				JsonObject root = focusDefinitionRoot(file);
				String itemId = root.get("item").getAsString();
				String directField = registeredFieldsByItem.get(itemId);
				if (hasNonEmptyArray(root, "modifiers")
						|| root.has("behavior")
						|| directHookSources.contains(attunedPath(itemId))
						|| (directField != null && directHookSources.contains(directField))) {
					continue;
				}
				inertItems.add(itemId);
			}
		}
		assertEquals(Set.of(), inertItems,
			"Every shipped Focus should have modifiers, a behavior id, or a direct gameplay hook");
	}

	@Test
	void minecraft2612FocusAndSynergyModifiersUseCurrentAttributeRegistryIds() throws IOException {
		Set<String> invalidAttributes = new TreeSet<>();
		collectDottedGenericAttributes(FOCUS_DATA_DIR, invalidAttributes);
		collectDottedGenericAttributes(SYNERGY_DATA_DIR, invalidAttributes);

		assertEquals(Set.of(), invalidAttributes,
			"Minecraft 26.1.2 data in this branch uses current bare vanilla attribute ids such as minecraft:attack_damage");
	}

	@Test
	void blackoutFocusStaysAWeakerSmokeAbility() throws IOException {
		JsonObject root = focusDefinitionRoot(FOCUS_DATA_DIR.resolve("blackout_focus.json"));
		String registrations = Files.readString(BEHAVIOR_REGISTRATION_SOURCE, StandardCharsets.UTF_8);

		assertEquals(3, root.get("cost").getAsInt(), "Blackout should stay a low-cost Unseen option");
		assertTrue(!root.has("affinity"), "Blackout should stay neutral for mixed builds");
		assertEquals("attuned:unseen", root.get("faction").getAsString());
		JsonElement behaviorId = root.get("behavior");
		assertNotNull(behaviorId, "Blackout should declare its behavior id");
		assertEquals("attuned:blackout", behaviorId.getAsString(),
			"Blackout should use its own weaker smoke ability");
		assertTrue(registrations.contains("import dev.attuned.content.behavior.BlackoutBehavior;"),
			"Blackout behavior should be registered with the other shipped behavior ids");
		assertTrue(registrations.contains("register(\"blackout\", new BlackoutBehavior())"),
			"Blackout should not silently share Smoke's stronger behavior");

		assertTrue(Files.isRegularFile(BLACKOUT_BEHAVIOR_SOURCE),
			"Blackout behavior source should exist");
		String behavior = Files.readString(BLACKOUT_BEHAVIOR_SOURCE, StandardCharsets.UTF_8);
		assertTrue(behavior.contains("BLACKOUT_TICKS = 40"),
			"Blackout should disrupt sight for the designed 40-tick window");
		assertTrue(behavior.contains("RADIUS = 3.5D"),
			"Blackout should stay smaller than Smoke's 6-block radius");
		assertTrue(behavior.contains("MobEffects.BLINDNESS"),
			"Blackout should apply a real short sight disruption");
		assertTrue(behavior.contains("mob.setTarget(null)"),
			"Blackout should clear disrupted mob targets");
	}

	@Test
	void referenceDocsListEveryRegisteredFocusBehavior() throws IOException {
		String registrations = Files.readString(BEHAVIOR_REGISTRATION_SOURCE, StandardCharsets.UTF_8);
		String reference = Files.readString(REFERENCE_DOC, StandardCharsets.UTF_8);
		Set<String> missing = new TreeSet<>();
		for (String behaviorId : registeredBehaviorIds(registrations)) {
			if (!reference.contains("`" + behaviorId + "`")) {
				missing.add(behaviorId);
			}
		}

		assertEquals(Set.of(), missing,
			"docs/reference.md should list every shipped Focus behavior id");
	}

	@Test
	void validateCommandReportsDuplicateDefinitionItemsInsteadOfReplacingThem() throws IOException {
		String source = Files.readString(COMMANDS_SOURCE, StandardCharsets.UTF_8);
		String validateContent = methodRegion(source, "private static int validateContent", "/** Dumps");

		assertTrue(validateContent.contains("byItem.putIfAbsent("),
			"/attuned validate should detect duplicate FocusDefinition item keys before reporting success");
		assertTrue(validateContent.contains("Duplicate FocusDefinition item"),
			"/attuned validate should report duplicate FocusDefinition item keys clearly");
		assertTrue(!validateContent.contains("byItem.put(def.item().value(), def);"),
			"/attuned validate should not silently overwrite duplicate FocusDefinition item keys");
		assertTrue(!validateContent.contains("AttunedContent.FOCI"),
			"/attuned validate should not reject datapack FocusDefinitions outside the static shipped-Foci list");
		assertTrue(!validateContent.contains("AttunedContent.isFocus("),
			"/attuned validate should not use static Focus item membership as registry authority");
	}

	@Test
	void bloodrushTooltipSeparatesFlatAttackSpeedFromPercentMovementSpeed() throws IOException {
		JsonObject root = focusDefinitionRoot(FOCUS_DATA_DIR.resolve("bloodrush_focus.json"));
		JsonObject attackSpeed = modifierFor(root, "minecraft:attack_speed");
		JsonObject movementSpeed = modifierFor(root, "minecraft:movement_speed");
		String effect = languageRoot().get("item.attuned.bloodrush_focus.effect").getAsString();

		assertEquals("add_value", attackSpeed.get("operation").getAsString(),
			"Bloodrush attack speed is a flat vanilla attribute bonus.");
		assertEquals(0.35D, attackSpeed.get("amount").getAsDouble(), 0.0001D,
			"Bloodrush attack speed amount should stay in sync with tooltip wording.");
		assertEquals("add_multiplied_base", movementSpeed.get("operation").getAsString(),
			"Bloodrush movement speed is the percentage modifier.");
		assertEquals(0.1D, movementSpeed.get("amount").getAsDouble(), 0.0001D,
			"Bloodrush movement speed amount should stay in sync with tooltip wording.");
		assertTrue(effect.contains("+0.35 attack speed"),
			"Bloodrush tooltip should not describe its flat attack-speed modifier as a percentage.");
		assertTrue(effect.contains("10% movement speed"),
			"Bloodrush tooltip should still name the actual movement-speed percentage.");
	}

	@Test
	void creativeInventorySplitsAffinityAndUtilityContentIntoReadableTabs() throws IOException {
		String content = Files.readString(CONTENT_SOURCE, StandardCharsets.UTF_8);
		String source = Files.readString(CREATIVE_TABS_SOURCE, StandardCharsets.UTF_8);
		JsonObject lang = languageRoot();

		assertTrue(content.contains("AttunedCreativeTabs.init()"),
			"AttunedContent.init should register the creative tabs");
		assertTrue(source.contains("\"attuned\""),
			"Creative inventory should keep a stable first Attuned tab id");
		assertTrue(source.contains("\"attuned_zephyr_holy\""),
			"Creative inventory should split the original Zephyr/Holy lanes away from Fury/Bastion");
		assertTrue(source.contains("\"attuned_tide_forge\""),
			"Creative inventory should split Tide/Forge Foci into their own readable tab");
		assertTrue(source.contains("\"attuned_verdant_umbral\""),
			"Creative inventory should split Verdant/Umbral Foci into their own readable tab");
		assertTrue(source.contains("\"attuned_utility\""),
			"Creative inventory should include a utility tab for neutral Foci and tools");
		assertTrue(source.contains("Set.of(Affinity.FURY, Affinity.BASTION)"),
			"First Focus tab should group Fury and Bastion side-by-side");
		assertTrue(source.contains("Set.of(Affinity.ZEPHYR, Affinity.HOLY)"),
			"Second Focus tab should group Zephyr and Holy side-by-side");
		assertTrue(source.contains("Set.of(Affinity.TIDE, Affinity.FORGE)"),
			"Third Focus tab should group Tide and Forge side-by-side");
		assertTrue(source.contains("Set.of(Affinity.VERDANT, Affinity.UMBRAL)"),
			"Fourth Focus tab should group Verdant and Umbral side-by-side");
		assertTrue(source.contains("definition.faction()"),
			"Creative tab order should keep named Focus families together within each affinity lane");
		assertTrue(source.contains("factionKey(definition.faction())"),
			"Creative tab sorting should use a stable faction key between affinity and cost");
		assertTrue(source.contains("ALTAR_OF_REWEAVING"),
			"Utility tab should keep the Altar of Reweaving reachable");
		assertTrue(!source.contains("AttunedContent.FOCI"),
			"Creative tabs should display Foci from the synced FocusDefinition registry, not the static shipped-Foci list");
		assertTrue(!source.contains("definition == null"),
			"Creative tab filters should only evaluate real FocusDefinition entries from the registry");
		assertLanguageKey(lang, "itemGroup.attuned.fury_bastion_foci");
		assertLanguageKey(lang, "itemGroup.attuned.zephyr_holy_foci");
		assertLanguageKey(lang, "itemGroup.attuned.tide_forge_foci");
		assertLanguageKey(lang, "itemGroup.attuned.verdant_umbral_foci");
		assertLanguageKey(lang, "itemGroup.attuned.utility_foci");
	}

	@Test
	void factionFieldsStayNamespacedTranslatedAndAppliedToUnseenFoci() throws IOException {
		Set<String> translatedFactions = translatedFactionIds();
		Set<String> unseenItems = new TreeSet<>();
		try (Stream<Path> paths = Files.list(FOCUS_DATA_DIR)) {
			for (Path file : paths
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()
					.toList()) {
				JsonObject root = focusDefinitionRoot(file);
				JsonElement faction = root.get("faction");
				if (faction == null) {
					continue;
				}
				assertTrue(faction.isJsonPrimitive(),
					"FocusDefinition faction should be a string id: " + file);
				String factionId = faction.getAsString();
				assertTrue(NAMESPACED_ID.matcher(factionId).matches(),
					"FocusDefinition faction should be a namespaced id: " + file);
				assertTrue(translatedFactions.contains(factionId),
					"FocusDefinition faction should have a lang entry: " + factionId);
				if ("attuned:unseen".equals(factionId)) {
					unseenItems.add(root.get("item").getAsString());
				}
			}
		}
		assertEquals(UNSEEN_FOCUS_ITEMS, unseenItems,
			"The Unseen batch should consistently declare faction metadata");
	}

	@Test
	void seafarersFociStayNeutralTranslatedAndGrantOnlyLuckUtility() throws IOException {
		Set<String> seafarersItems = new TreeSet<>();
		try (Stream<Path> paths = Files.list(FOCUS_DATA_DIR)) {
			for (Path file : paths
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()
					.toList()) {
				JsonObject root = focusDefinitionRoot(file);
				JsonElement faction = root.get("faction");
				if (faction == null || !"attuned:seafarers".equals(faction.getAsString())) {
					continue;
				}
				String itemId = root.get("item").getAsString();
				seafarersItems.add(itemId);
				assertTrue(!root.has("affinity"), "Seafarers Foci must stay neutral: " + file);
				assertSeafarersLuckModifier(root, itemId, file);
			}
		}
		assertEquals(SEAFARERS_FOCUS_ITEMS, seafarersItems);
	}

	@Test
	void offshoreHarpoonFocusStaysNeutralTranslatedAndTemporaryOnly() throws IOException {
		Set<String> offshoreItems = new TreeSet<>();
		try (Stream<Path> paths = Files.list(FOCUS_DATA_DIR)) {
			for (Path file : paths
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()
					.toList()) {
				JsonObject root = focusDefinitionRoot(file);
				JsonElement faction = root.get("faction");
				if (faction == null || !"attuned:offshore".equals(faction.getAsString())) {
					continue;
				}
				String itemId = root.get("item").getAsString();
				offshoreItems.add(itemId);
				assertTrue(!root.has("affinity"), "Offshore Foci must stay neutral: " + file);
				assertTrue(root.has("unique") && root.get("unique").getAsBoolean(),
					"Offshore Harpoon should be unique while active: " + file);
			}
		}

		JsonObject lang = languageRoot();
		assertEquals(OFFSHORE_FOCUS_ITEMS, offshoreItems,
			"The first Offshore release should ship only Harpoon Focus");
		assertLanguageKey(lang, "faction.attuned.offshore");
		assertLanguageKey(lang, "item.attuned.offshore_harpoon");
		assertLanguageKey(lang, "item.attuned.ocean_relic_trident");

		String source = Files.readString(CONTENT_SOURCE, StandardCharsets.UTF_8);
		assertTrue(!source.contains("OFFSHORE_HARPOON"),
			"Offshore Harpoon should not be registered as a permanent craftable item");
		assertTrue(!Files.isRegularFile(Path.of("src/main/resources/data/attuned/recipe/offshore_harpoon.json")),
			"Offshore Harpoon should not have a recipe");
		assertTrue(!Files.isRegularFile(Path.of("src/main/resources/data/attuned/recipe/ocean_relic_trident.json")),
			"Ocean Relic Trident should not have a recipe");
	}

	@Test
	void revenantFociShipAsARealFactionWithAnimatedArt() throws IOException {
		Set<String> revenantItems = new TreeSet<>();
		try (Stream<Path> paths = Files.list(FOCUS_DATA_DIR)) {
			for (Path file : paths
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()
					.toList()) {
				JsonObject root = focusDefinitionRoot(file);
				JsonElement faction = root.get("faction");
				if (faction == null || !"attuned:revenant".equals(faction.getAsString())) {
					continue;
				}
				String itemId = root.get("item").getAsString();
				revenantItems.add(itemId);
				if (!"attuned:gravebind_focus".equals(itemId) && !"attuned:epitaph_focus".equals(itemId)) {
					assertTrue(root.has("unique") && root.get("unique").getAsBoolean(),
						"Combat-facing Revenant Foci should be unique while active: " + file);
				}
			}
		}

		JsonObject lang = languageRoot();
		assertEquals(REVENANT_FOCUS_ITEMS, revenantItems,
			"The Revenant release should declare its planned Foci and Gravebind anchor");
		assertLanguageKey(lang, "faction.attuned.revenant");
		assertTrue(Files.isRegularFile(REVENANT_SOURCE),
			"Revenant art should keep its concept source sheet");
		assertTrue(Files.isRegularFile(REVENANT_REPORT),
			"Revenant art should keep a reproducible texture build report");
		String report = Files.readString(REVENANT_REPORT, StandardCharsets.UTF_8);
		for (String item : REVENANT_FOCUS_ITEMS) {
			String name = attunedPath(item);
			if (!"gravebind_focus".equals(name)) {
				assertTrue(report.contains(name),
					"Revenant report should list each generated texture: " + name);
			}
		}
	}

	@Test
	void wheelCounterFociShipAsARealContentBatch() throws IOException {
		JsonObject lang = languageRoot();
		Set<String> actualItems = new TreeSet<>();
		Map<String, Integer> countsByAffinity = new TreeMap<>();

		for (Map.Entry<String, String> entry : WHEEL_COUNTER_FOCUS_AFFINITIES.entrySet()) {
			String itemId = entry.getKey();
			String affinityId = entry.getValue();
			String name = attunedPath(itemId);
			Path file = FOCUS_DATA_DIR.resolve(name + ".json");
			assertTrue(Files.isRegularFile(file), "Wheel counter Focus should have FocusDefinition data: " + itemId);
			JsonObject root = focusDefinitionRoot(file);

			assertEquals(itemId, root.get("item").getAsString(),
				"Wheel counter Focus definition item should match file name: " + file);
			assertTrue(!root.has("aspect"),
				"Wheel counter Foci should no longer carry the removed aspect field: " + file);
			assertTrue(root.has("affinity"),
				"Wheel counter Focus should declare its counter identity via affinity: " + file);
			assertEquals(affinityId, root.get("affinity").getAsString(),
				"Wheel counter Focus should declare the planned affinity: " + itemId);
			assertTrue(root.has("unique") && root.get("unique").getAsBoolean(),
				"Wheel counter Foci should be unique to prevent passive stacking: " + file);
			assertTrue(hasNonEmptyArray(root, "modifiers") || root.has("behavior"),
				"Wheel counter Focus should have a real gameplay effect, not only lore metadata: " + file);

			actualItems.add(itemId);
			countsByAffinity.merge(affinityId, 1, Integer::sum);
			assertLanguageKey(lang, "item.attuned." + name);
			assertLanguageKey(lang, "item.attuned." + name + ".lore");
			assertLanguageKey(lang, "item.attuned." + name + ".lore2");
			assertLanguageKey(lang, "item.attuned." + name + ".effect");
		}

		assertEquals(new TreeSet<>(WHEEL_COUNTER_FOCUS_AFFINITIES.keySet()), actualItems,
			"The first wheel-counter batch should ship exactly the planned 12 new Foci");
		assertEquals(Map.of(
			"tide", 3,
			"forge", 3,
			"verdant", 3,
			"umbral", 3), countsByAffinity,
			"The starter batch should ship three Foci for each new non-core affinity");
	}

	@Test
	void altarBlockAssetsKeepClosedUndersidesAndReadableReweavingTextures() throws IOException {
		assertNoDownCullface(BLOCK_MODEL_DIR.resolve("attunement_altar.json"));
		assertNoDownCullface(BLOCK_MODEL_DIR.resolve("attunement_altar_none.json"));
		assertNoDownCullface(BLOCK_MODEL_DIR.resolve("attunement_altar_fury.json"));
		assertNoDownCullface(BLOCK_MODEL_DIR.resolve("attunement_altar_bastion.json"));
		assertNoDownCullface(BLOCK_MODEL_DIR.resolve("attunement_altar_zephyr.json"));
		assertNoDownCullface(BLOCK_MODEL_DIR.resolve("attunement_altar_holy.json"));
		assertNoDownCullface(BLOCK_MODEL_DIR.resolve("attunement_altar_tide.json"));
		assertNoDownCullface(BLOCK_MODEL_DIR.resolve("attunement_altar_forge.json"));
		assertNoDownCullface(BLOCK_MODEL_DIR.resolve("attunement_altar_verdant.json"));
		assertNoDownCullface(BLOCK_MODEL_DIR.resolve("attunement_altar_umbral.json"));
		assertNoDownCullface(BLOCK_MODEL_DIR.resolve("altar_of_reweaving.json"));
		assertTopPlateHasBottomFace(BLOCK_MODEL_DIR.resolve("attunement_altar.json"));
		assertBlockTextureSize("altar_of_reweaving_base.png", 64, 64);
		assertBlockTextureSize("altar_of_reweaving_gem.png", 64, 64);
		assertBlockTextureSize("altar_of_reweaving_top.png", 64, 64);
	}

	private static JsonObject modifierFor(JsonObject root, String attribute) {
		for (JsonElement element : root.get("modifiers").getAsJsonArray()) {
			JsonObject modifier = element.getAsJsonObject();
			if (attribute.equals(modifier.get("attribute").getAsString())) {
				return modifier;
			}
		}
		throw new AssertionError("Missing Focus modifier for " + attribute + ": " + root);
	}

	private static void assertSeafarersLuckModifier(JsonObject root, String itemId, Path file) {
		JsonElement modifiers = root.get("modifiers");
		assertNotNull(modifiers, "Seafarers Foci should grant real player Luck while active: " + file);
		assertTrue(modifiers.isJsonArray(), "Seafarers modifiers should be a JSON array: " + file);
		assertEquals(1, modifiers.getAsJsonArray().size(),
			"Seafarers should only carry their non-combat Luck modifier: " + file);

		JsonObject modifier = modifiers.getAsJsonArray().get(0).getAsJsonObject();
		assertEquals("minecraft:luck", modifier.get("attribute").getAsString(),
			"Seafarers modifier should affect vanilla player Luck: " + file);
		assertEquals(SEAFARERS_LUCK_AMOUNTS.get(itemId), modifier.get("amount").getAsDouble(), 0.0001D,
			"Seafarers Luck amount should stay significant but vanilla-friendly: " + file);
		assertEquals("add_value", modifier.get("operation").getAsString(),
			"Seafarers Luck should add a flat utility bonus: " + file);
	}

	private static void assertNoDownCullface(Path file) throws IOException {
		String source = Files.readString(file, StandardCharsets.UTF_8);
		assertTrue(!source.contains("\"cullface\": \"down\""),
			"Altar models should not cull their underside faces: " + file);
	}

	private static void assertTopPlateHasBottomFace(Path file) throws IOException {
		JsonObject model = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
		for (JsonElement element : model.getAsJsonArray("elements")) {
			JsonObject object = element.getAsJsonObject();
			if (!"top_plate".equals(object.get("name").getAsString())) {
				continue;
			}
			assertTrue(object.getAsJsonObject("faces").has("down"),
				"Attunement Altar top plate should draw its underside: " + file);
			return;
		}
		assertTrue(false, "Attunement Altar model should contain a named top_plate element: " + file);
	}

	private static void assertBlockTextureSize(String name, int expectedWidth, int expectedHeight) throws IOException {
		Path texture = BLOCK_TEXTURE_DIR.resolve(name);
		BufferedImage image = ImageIO.read(texture.toFile());
		assertNotNull(image, "Block texture should be a readable PNG: " + texture);
		assertEquals(expectedWidth, image.getWidth(), "Block texture width should be upgraded: " + texture);
		assertEquals(expectedHeight, image.getHeight(), "Block texture height should be upgraded: " + texture);
	}

	private static void assertBefore(String source, String earlier, String later) {
		int earlierIndex = source.indexOf(earlier);
		int laterIndex = source.indexOf(later);
		assertTrue(earlierIndex >= 0, "Expected source to contain: " + earlier);
		assertTrue(laterIndex >= 0, "Expected source to contain: " + later);
		assertTrue(earlierIndex < laterIndex, "Expected " + earlier + " before " + later);
	}

	private static String methodRegion(String source, String start, String end) {
		int startIndex = source.indexOf(start);
		int endIndex = source.indexOf(end);
		assertTrue(startIndex >= 0, "Expected source to contain: " + start);
		assertTrue(endIndex > startIndex, "Expected " + end + " after " + start);
		return source.substring(startIndex, endIndex);
	}

	private static Map<String, String> registeredFocusItemsByField(String source) {
		Matcher matcher = REGISTERED_FOCUS.matcher(source);
		Map<String, String> itemsByField = new TreeMap<>();
		while (matcher.find()) {
			itemsByField.put(matcher.group(1), "attuned:" + matcher.group(2));
		}
		assertTrue(!itemsByField.isEmpty(), "Could not find registered Focus item fields in AttunedContent");
		return itemsByField;
	}

	private static Set<String> registeredBehaviorIds(String source) {
		Matcher matcher = REGISTERED_BEHAVIOR.matcher(source);
		Set<String> behaviorIds = new TreeSet<>();
		while (matcher.find()) {
			behaviorIds.add("attuned:" + matcher.group(1));
		}
		assertTrue(!behaviorIds.isEmpty(), "Could not find registered Focus behaviors in AttunedFocusBehaviors");
		return behaviorIds;
	}

	private static Set<String> dataBehaviorIds() throws IOException {
		assertTrue(Files.isDirectory(FOCUS_BEHAVIOR_DIR), "Could not find FocusBehaviorDef data directory");
		try (Stream<Path> paths = Files.list(FOCUS_BEHAVIOR_DIR)) {
			Set<String> behaviorIds = new TreeSet<>();
			for (Path file : paths
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()
					.toList()) {
				behaviorIds.add("attuned:" + file.getFileName().toString().replaceFirst("\\.json$", ""));
			}
			assertTrue(!behaviorIds.isEmpty(), "Could not find any datapack FocusBehaviorDef JSON files");
			return behaviorIds;
		}
	}

	private static Set<String> focusDefinitionItems() throws IOException {
		assertTrue(Files.isDirectory(FOCUS_DATA_DIR), "Could not find FocusDefinition data directory");
		try (Stream<Path> paths = Files.list(FOCUS_DATA_DIR)) {
			List<Path> files = paths
				.filter(path -> path.getFileName().toString().endsWith(".json"))
				.sorted()
				.toList();
			assertTrue(!files.isEmpty(), "Could not find FocusDefinition JSON files");

			Set<String> items = new TreeSet<>();
			for (Path file : files) {
				String itemId = focusDefinitionItem(file);
				String expectedItemId = "attuned:" + file.getFileName().toString().replaceFirst("\\.json$", "");
				assertEquals(expectedItemId, itemId,
					"FocusDefinition file name should match its declared item id");
				items.add(itemId);
			}
			assertEquals(files.size(), items.size(), "FocusDefinition JSON files should not duplicate item ids");
			return items;
		}
	}

	private static String focusDefinitionItem(Path file) throws IOException {
		JsonObject root = focusDefinitionRoot(file);
		JsonElement item = root.get("item");
		assertTrue(item != null && item.isJsonPrimitive(),
			"FocusDefinition JSON should declare a string item id: " + file);
		return item.getAsString();
	}

	private static Set<String> focusDefinitionBehaviorIds() throws IOException {
		assertTrue(Files.isDirectory(FOCUS_DATA_DIR), "Could not find FocusDefinition data directory");
		try (Stream<Path> paths = Files.list(FOCUS_DATA_DIR)) {
			List<Path> files = paths
				.filter(path -> path.getFileName().toString().endsWith(".json"))
				.sorted()
				.toList();
			assertTrue(!files.isEmpty(), "Could not find FocusDefinition JSON files");

			Set<String> behaviorIds = new TreeSet<>();
			for (Path file : files) {
				JsonObject root = focusDefinitionRoot(file);
				JsonElement behavior = root.get("behavior");
				if (behavior == null) {
					continue;
				}
				assertTrue(behavior.isJsonPrimitive(),
					"FocusDefinition behavior should be a string id: " + file);
				String behaviorId = behavior.getAsString();
				assertTrue(NAMESPACED_ID.matcher(behaviorId).matches(),
					"FocusDefinition behavior should be a namespaced id: " + file);
				behaviorIds.add(behaviorId);
			}
			assertTrue(!behaviorIds.isEmpty(), "Could not find any FocusDefinition behavior ids");
			return behaviorIds;
		}
	}

	private static String directFocusHookSources() throws IOException {
		StringBuilder sources = new StringBuilder();
		for (Path source : DIRECT_FOCUS_HOOK_SOURCES) {
			assertTrue(Files.isRegularFile(source), "Expected direct Focus hook source: " + source);
			sources.append(Files.readString(source, StandardCharsets.UTF_8)).append('\n');
		}
		return sources.toString();
	}

	private static boolean hasNonEmptyArray(JsonObject root, String key) {
		JsonElement element = root.get(key);
		return element != null && element.isJsonArray() && !element.getAsJsonArray().isEmpty();
	}

	private static JsonObject focusDefinitionRoot(Path file) throws IOException {
		return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static void collectBareMinecraft121Attributes(Path directory, Set<String> invalidAttributes)
			throws IOException {
		assertTrue(Files.isDirectory(directory), "Could not find data directory: " + directory);
		try (Stream<Path> paths = Files.list(directory)) {
			for (Path file : paths
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()
					.toList()) {
				JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
					.getAsJsonObject();
				if (!hasNonEmptyArray(root, "modifiers")) {
					continue;
				}
				for (JsonElement element : root.getAsJsonArray("modifiers")) {
					JsonObject modifier = element.getAsJsonObject();
					String attribute = modifier.get("attribute").getAsString();
					if (MINECRAFT_121_BARE_ATTRIBUTE_IDS.contains(attribute)) {
						invalidAttributes.add(file + " -> " + attribute);
					}
				}
			}
		}
	}

	private static void collectDottedGenericAttributes(Path directory, Set<String> invalidAttributes)
			throws IOException {
		assertTrue(Files.isDirectory(directory), "Could not find data directory: " + directory);
		try (Stream<Path> paths = Files.list(directory)) {
			for (Path file : paths
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()
					.toList()) {
				JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
					.getAsJsonObject();
				if (!hasNonEmptyArray(root, "modifiers")) {
					continue;
				}
				for (JsonElement element : root.getAsJsonArray("modifiers")) {
					JsonObject modifier = element.getAsJsonObject();
					String attribute = modifier.get("attribute").getAsString();
					if (attribute.startsWith("minecraft:generic.")) {
						invalidAttributes.add(file + " -> " + attribute);
					}
				}
			}
		}
	}

	private static Set<String> translatedFactionIds() throws IOException {
		JsonObject lang = languageRoot();
		Set<String> factions = new TreeSet<>();
		for (String key : lang.keySet()) {
			if (key.startsWith("faction.")) {
				String id = key.substring("faction.".length()).replaceFirst("\\.", ":");
				factions.add(id);
			}
		}
		return factions;
	}

	private static JsonObject languageRoot() throws IOException {
		return JsonParser.parseString(Files.readString(LANG_FILE, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static void assertAnimatedFocusTexture(String name) throws IOException {
		Path texture = ITEM_TEXTURE_DIR.resolve(name + ".png");
		BufferedImage image = ImageIO.read(texture.toFile());
		assertNotNull(image, "Focus texture should be a readable PNG: " + texture);
		assertEquals(64, image.getWidth(), "Focus texture width should stay standardized: " + texture);
		assertEquals(512, image.getHeight(), "Focus texture should contain eight 64px frames: " + texture);
		assertAnimatedFramesDiffer(image, texture);

		Path metadata = ITEM_TEXTURE_DIR.resolve(name + ".png.mcmeta");
		JsonObject animation = JsonParser.parseString(Files.readString(metadata, StandardCharsets.UTF_8))
			.getAsJsonObject()
			.getAsJsonObject("animation");
		assertNotNull(animation, "Focus texture metadata should declare animation settings: " + metadata);
		assertEquals(2, animation.get("frametime").getAsInt(),
			"Focus texture animation frametime should stay consistent: " + metadata);
		assertTrue(animation.get("interpolate").getAsBoolean(),
			"Focus texture animation should stay interpolated: " + metadata);
	}

	private static void assertAnimatedFramesDiffer(BufferedImage image, Path texture) {
		int changedFrames = 0;
		for (int frame = 1; frame < 8; frame++) {
			if (frameDiffersFromFirst(image, frame)) {
				changedFrames++;
			}
		}
		assertEquals(7, changedFrames,
			"Focus texture should have visible frame-to-frame animation: " + texture);
	}

	private static boolean frameDiffersFromFirst(BufferedImage image, int frame) {
		int changedPixels = 0;
		int frameY = frame * 64;
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				if (image.getRGB(x, y) != image.getRGB(x, frameY + y)) {
					changedPixels++;
					if (changedPixels >= 16) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static void assertReadableSeafarersAnimation(String name) throws IOException {
		Path texture = ITEM_TEXTURE_DIR.resolve(name + ".png");
		BufferedImage image = ImageIO.read(texture.toFile());
		assertNotNull(image, "Seafarers Focus texture should be readable: " + texture);

		double strongestFrameDelta = 0.0D;
		for (int frame = 1; frame < 8; frame++) {
			strongestFrameDelta = Math.max(strongestFrameDelta, averageFrameDeltaFromFirst(image, frame));
		}
		assertTrue(strongestFrameDelta >= 18.0D,
			"Seafarers Focus animation should be visibly readable in-game: " + texture);
	}

	private static void assertReadableHolyAnimation(String name) throws IOException {
		Path texture = ITEM_TEXTURE_DIR.resolve(name + ".png");
		BufferedImage image = ImageIO.read(texture.toFile());
		assertNotNull(image, "Holy Focus texture should be readable: " + texture);
		assertFrameFootprint(image, texture);

		double totalDelta = 0.0D;
		for (int frame = 1; frame < 8; frame++) {
			totalDelta += averageFrameDeltaFromFirst(image, frame);
		}
		assertTrue(totalDelta / 7.0D >= 38.0D,
			"Holy Focus animation should be as visible as the older animated Foci: " + texture);
	}

	private static void assertFrameFootprint(BufferedImage image, Path texture) {
		int minX = 64;
		int minY = 64;
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				if (((image.getRGB(x, y) >>> 24) & 0xFF) > 24) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		int width = maxX - minX + 1;
		int height = maxY - minY + 1;
		assertTrue(width >= 56 && height >= 60,
			"Holy Focus first frame should fill the same medallion footprint as older Foci: " + texture);
	}

	private static double averageFrameDeltaFromFirst(BufferedImage image, int frame) {
		long totalDelta = 0L;
		int frameY = frame * 64;
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				int first = image.getRGB(x, y);
				int current = image.getRGB(x, frameY + y);
				totalDelta += Math.abs(((first >>> 24) & 0xFF) - ((current >>> 24) & 0xFF));
				totalDelta += Math.abs(((first >>> 16) & 0xFF) - ((current >>> 16) & 0xFF));
				totalDelta += Math.abs(((first >>> 8) & 0xFF) - ((current >>> 8) & 0xFF));
				totalDelta += Math.abs((first & 0xFF) - (current & 0xFF));
			}
		}
		return totalDelta / (double) (64 * 64);
	}

	private static void assertLanguageKey(JsonObject lang, String key) {
		assertTrue(lang.has(key), "Missing language key: " + key);
	}

	private static String attunedPath(String itemId) {
		assertTrue(itemId.startsWith("attuned:"), "Expected attuned item id: " + itemId);
		return itemId.substring("attuned:".length());
	}
}
