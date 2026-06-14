package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.attuned.api.focus.Affinity;
import dev.attuned.combat.Apex;
import dev.attuned.pacts.Pact;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Contract coverage for the Radiant/Holy affinity expansion. */
class RadiantExpansionContractTest {
	private static final Path FOCUS_DATA_DIR =
		Path.of("src/main/resources/data/attuned/attuned/focus");
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");
	private static final Path MOB_AFFINITIES_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/MobAffinities.java");
	private static final Path HOLY_MOB_TAG =
		Path.of("src/main/resources/data/attuned/tags/entity_type/holy_mobs.json");
	private static final Path ITEM_DEFINITION_DIR =
		Path.of("src/main/resources/assets/attuned/items");
	private static final Path ITEM_MODEL_DIR =
		Path.of("src/main/resources/assets/attuned/models/item");
	private static final Path ITEM_TEXTURE_DIR =
		Path.of("src/main/resources/assets/attuned/textures/item");
	private static final Path BEHAVIOR_REGISTRATION_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedFocusBehaviors.java");
	private static final Path RADIANT_BEHAVIORS_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/RadiantFocusBehaviors.java");
	private static final Path ROOTSTEP_BEHAVIOR_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/RootstepBehavior.java");
	private static final Path MOSSHEART_BEHAVIOR_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/MossheartBehavior.java");
	private static final Path RIVET_BEHAVIOR_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/RivetBehavior.java");
	private static final Path KILNWARD_BEHAVIOR_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/KilnwardBehavior.java");
	private static final Path TEMPER_BEHAVIOR_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/TemperBehavior.java");
	private static final Path ATTUNED_COMBAT_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/AttunedCombat.java");
	private static final Path LANTERN_BEHAVIOR_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/LanternBehavior.java");
	private static final Path MASK_BEHAVIOR_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/MaskBehavior.java");
	private static final Path WHISPER_BEHAVIOR_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/WhisperBehavior.java");
	private static final Path PACTS_SOURCE =
		Path.of("src/main/java/dev/attuned/pacts/Pacts.java");
	private static final Path BLOOM_BEHAVIOR_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/BloomBehavior.java");

	private static final Set<String> HOLY_FOCI = Set.of(
		"attuned:bellwether_focus",
		"attuned:censer_focus",
		"attuned:namesake_focus",
		"attuned:oathguard_focus",
		"attuned:sunlance_focus",
		"attuned:threshold_focus",
		"attuned:votive_focus");
	private static final Map<String, Set<String>> FACTION_FOCI = Map.of(
		"attuned:radiant", Set.of(
			"attuned:bellwether_focus",
			"attuned:oathguard_focus",
			"attuned:sunlance_focus",
			"attuned:votive_focus"),
		"attuned:reliquary", Set.of(
			"attuned:censer_focus",
			"attuned:namesake_focus",
			"attuned:threshold_focus"),
		"attuned:verdant_choir", Set.of(
			"attuned:bloom_focus",
			"attuned:mossheart_focus",
			"attuned:rootstep_focus"),
		"attuned:ashen_forge", Set.of(
			"attuned:kilnward_focus",
			"attuned:rivet_focus",
			"attuned:temper_focus"));
	private static final Set<String> FIRST_WAVE_FOCI = firstWaveFoci();

	@Test
	void holyKeepsItsOriginalRelationshipsInsideTheExpandedWheel() {
		Affinity holy = Affinity.valueOf("HOLY");

		assertEquals("holy", holy.getSerializedName());
		// The historic four-cycle still holds as a subset of the promoted 8-matrix.
		assertTrue(holy.beats(Affinity.FURY), "Holy should still pressure Fury");
		assertTrue(Affinity.FURY.beats(Affinity.BASTION), "Fury should still pressure Bastion");
		assertTrue(Affinity.BASTION.beats(Affinity.ZEPHYR), "Bastion should still pressure Zephyr");
		assertTrue(Affinity.ZEPHYR.beats(holy), "Zephyr should still counter Holy");
		assertTrue(!holy.beats(Affinity.BASTION), "Holy should not pressure Bastion");
		assertTrue(!Affinity.BASTION.beats(holy), "Bastion should not pressure Holy");
		// Promotion gave Holy a second strength and a second reciprocal weakness.
		assertTrue(holy.beats(Affinity.UMBRAL), "Holy should also pressure Umbral after promotion");
		assertTrue(Affinity.VERDANT.beats(holy), "Verdant should also counter Holy after promotion");
		assertEquals(2, holy.strongAgainst().size(), "Holy should counter exactly two affinities");
		assertEquals(2, holy.weakAgainst().size(), "Holy should be countered by exactly two affinities");
	}

	@Test
	void affinityMatchupsRejectMissingOpponentClearly() {
		NullPointerException missingOpponent = assertThrows(NullPointerException.class,
			() -> Affinity.FURY.beats(null));

		assertEquals("other", missingOpponent.getMessage());
	}

	@Test
	void radiantCovenantAndJudgmentAreFirstClassAffinityRewards() {
		Affinity holy = Affinity.valueOf("HOLY");

		assertEquals(Pact.valueOf("RADIANT_COVENANT"), Pact.ofAffinity(holy));
		assertEquals("Judgment", Apex.capstoneName(holy));
		assertTrue(Apex.capstoneDescription(holy).contains("judgment")
				|| Apex.capstoneDescription(holy).contains("Judgment"));
	}

	@Test
	void radiantFirstWaveFociAndFactionsShipTogether() throws IOException {
		Map<String, JsonObject> foci = focusRootsByItem();
		Set<String> holyFoci = new TreeSet<>();
		Map<String, Set<String>> factionItems = new TreeMap<>();
		for (Map.Entry<String, JsonObject> entry : foci.entrySet()) {
			String item = entry.getKey();
			JsonObject root = entry.getValue();
			if (hasString(root, "affinity", "holy")) {
				holyFoci.add(item);
			}
			JsonElement faction = root.get("faction");
			if (faction != null) {
				factionItems.computeIfAbsent(faction.getAsString(), ignored -> new TreeSet<>()).add(item);
			}
		}

		assertEquals(HOLY_FOCI, holyFoci, "Shipped Holy Foci should include the Radiant and Reliquary Foci");
		for (Map.Entry<String, Set<String>> expected : FACTION_FOCI.entrySet()) {
			assertEquals(expected.getValue(), factionItems.get(expected.getKey()),
				expected.getKey() + " should ship its planned first-wave Foci");
		}
	}

	@Test
	void radiantFactionsAndHolyMobTagAreTranslatedAndWired() throws IOException {
		String lang = Files.readString(LANG_FILE, StandardCharsets.UTF_8);
		for (String faction : FACTION_FOCI.keySet()) {
			String key = "faction." + faction.replaceFirst(":", ".");
			assertTrue(lang.contains("\"" + key + "\""), "Missing faction translation: " + key);
		}

		assertTrue(Files.isRegularFile(HOLY_MOB_TAG), "Holy mob affinity tag should ship");
		String source = Files.readString(MOB_AFFINITIES_SOURCE, StandardCharsets.UTF_8);
		assertTrue(source.contains("HOLY_MOBS"), "MobAffinities should read the Holy mob tag");
		assertTrue(source.contains("Affinity.HOLY"), "MobAffinities should return the Holy affinity");
	}

	@Test
	void votiveAbsorptionTriggersFromHostileHitsInLightInsteadOfPassivePulses() throws IOException {
		JsonObject root = focusRootsByItem().get("attuned:votive_focus");
		assertTrue(root != null, "Votive Focus data should ship");
		assertEquals("attuned:votive", root.get("behavior").getAsString(),
			"Votive should delegate its reactive shield to a runtime behavior");

		String behavior = Files.readString(RADIANT_BEHAVIORS_SOURCE, StandardCharsets.UTF_8);
		assertTrue(behavior.contains("class Votive implements FocusBehavior"),
			"Votive should remain a normal server-side Focus behavior");
		assertTrue(behavior.contains("ServerLivingEntityEvents.AFTER_DAMAGE.register(Votive::afterDamage)"),
			"Votive should react to actual hostile hits");
		assertTrue(behavior.contains("CombatTargets.isHostileOrPvpOpponent"),
			"Votive should only trigger from hostile mobs or valid PvP opponents");
		assertTrue(behavior.contains("AttunedCombat.attackerOf(source)"),
			"Votive should resolve the real living attacker from the damage source");
		assertTrue(behavior.contains("isBrightOrNearCandle(player)")
				&& behavior.contains("MobEffects.ABSORPTION")
				&& behavior.contains("ABSORPTION_TICKS = 40")
				&& behavior.contains("COOLDOWN_TICKS = 240"),
			"Votive should keep the planned light/candle condition, shield duration, and cooldown");
		String onTick = methodBody(behavior, "public void onTick(ServerPlayer player, ItemStack focus)");
		assertTrue(!onTick.contains("MobEffects.ABSORPTION"),
			"Votive onTick should not grant passive periodic shields just for standing near light");

		String lang = Files.readString(LANG_FILE, StandardCharsets.UTF_8);
		assertTrue(lang.contains("item.attuned.votive_focus.effect")
				&& lang.contains("hostile hit")
				&& lang.contains("light"),
			"Votive tooltip should explain the hostile-hit light/candle trigger");
	}

	@Test
	void oathguardAbsorptionTriggersFromBlockingOrHostileHits() throws IOException {
		JsonObject root = focusRootsByItem().get("attuned:oathguard_focus");
		assertTrue(root != null, "Oathguard Focus data should ship");
		assertEquals("attuned:oathguard", root.get("behavior").getAsString(),
			"Oathguard should delegate its defensive shield to a runtime behavior");

		String behavior = Files.readString(RADIANT_BEHAVIORS_SOURCE, StandardCharsets.UTF_8);
		assertTrue(behavior.contains("class Oathguard implements FocusBehavior"),
			"Oathguard should remain a normal server-side Focus behavior");
		assertTrue(behavior.contains("ServerLivingEntityEvents.AFTER_DAMAGE.register(this::afterDamage)"),
			"Oathguard should react to hostile hits as well as blocking");
		assertTrue(behavior.contains("private void afterDamage(LivingEntity defender, DamageSource source,"),
			"Oathguard should own a damage event handler");
		assertTrue(behavior.contains("AttunedCombat.attackerOf(source)")
				&& behavior.contains("CombatTargets.isHostileOrPvpOpponent"),
			"Oathguard should resolve and validate hostile or PvP attackers");
		assertTrue(behavior.contains("player.isBlocking()")
				&& behavior.contains("MobEffects.ABSORPTION")
				&& behavior.contains("ABSORPTION_TICKS = 60")
				&& behavior.contains("COOLDOWN_TICKS = 240"),
			"Oathguard should keep blocking, Absorption duration, and cooldown behavior");

		String lang = Files.readString(LANG_FILE, StandardCharsets.UTF_8);
		assertTrue(lang.contains("item.attuned.oathguard_focus.effect")
				&& lang.contains("Blocking")
				&& lang.contains("hostile hit"),
			"Oathguard tooltip should explain both defensive triggers");
	}

	@Test
	void bellwetherRevealsAfterRingingOrStandingWithinEightBlocksOfBell() throws IOException {
		JsonObject root = focusRootsByItem().get("attuned:bellwether_focus");
		assertTrue(root != null, "Bellwether Focus data should ship");
		assertEquals("attuned:bellwether", root.get("behavior").getAsString(),
			"Bellwether should delegate bell reveal to a runtime behavior");

		String behavior = Files.readString(RADIANT_BEHAVIORS_SOURCE, StandardCharsets.UTF_8);
		assertTrue(behavior.contains("class Bellwether implements FocusBehavior"),
			"Bellwether should remain a normal server-side Focus behavior");
		assertTrue(behavior.contains("BELL_RADIUS = 8"),
			"Bellwether should use the planned 8-block bell range");
		assertTrue(behavior.contains("UseBlockCallback.EVENT.register(this::useBlock)")
				&& behavior.contains("private InteractionResult useBlock(")
				&& behavior.contains("Blocks.BELL"),
			"Bellwether should also trigger when an active wearer rings a bell");
		assertTrue(behavior.contains("GLOW_TICKS = 80")
				&& behavior.contains("COOLDOWN_TICKS = 200")
				&& behavior.contains("CombatTargets.isHostileOrPvpOpponent")
				&& behavior.contains("MaskBehavior.resistsReveal"),
			"Bellwether should keep its planned reveal duration, cooldown, target filtering, and Mask counterplay");

		String lang = Files.readString(LANG_FILE, StandardCharsets.UTF_8);
		assertTrue(lang.contains("item.attuned.bellwether_focus.effect")
				&& lang.contains("Ringing")
				&& lang.contains("8 blocks"),
			"Bellwether tooltip should explain ringing or standing within 8 blocks of a bell");
	}

	@Test
	void firstWaveFocusAssetsAreAnimatedAndModelBacked() throws IOException {
		for (String item : FIRST_WAVE_FOCI) {
			String path = item.substring("attuned:".length());
			assertTrue(Files.isRegularFile(ITEM_DEFINITION_DIR.resolve(path + ".json")),
				"Missing item definition for " + item);
			assertTrue(Files.isRegularFile(ITEM_MODEL_DIR.resolve(path + ".json")),
				"Missing item model for " + item);
			Path texture = ITEM_TEXTURE_DIR.resolve(path + ".png");
			Path animation = ITEM_TEXTURE_DIR.resolve(path + ".png.mcmeta");
			assertTrue(Files.isRegularFile(texture), "Missing texture for " + item);
			assertTrue(Files.isRegularFile(animation), "Missing animation metadata for " + item);
			assertTrue(Files.readString(animation, StandardCharsets.UTF_8).contains("\"animation\""),
				"Missing animated texture metadata for " + item);
			BufferedImage image = ImageIO.read(texture.toFile());
			assertTrue(image != null, "Texture should be readable for " + item);
			assertEquals(64, image.getWidth(), item + " should match existing Focus texture width");
			assertTrue(image.getHeight() >= 128 && image.getHeight() % image.getWidth() == 0,
				item + " should be a vertical animated frame strip");
		}
	}

	@Test
	void namesakeLuckIsConditionalOnNamedThingsInsteadOfAlwaysOn() throws IOException {
		JsonObject root = focusRootsByItem().get("attuned:namesake_focus");
		assertTrue(root != null, "Namesake Focus data should ship");
		assertTrue(!root.has("modifiers"),
			"Namesake should not grant unconditional Luck through static modifiers");
		assertEquals("attuned:namesake", root.get("behavior").getAsString(),
			"Namesake should delegate its conditional Luck to a runtime behavior");

		String registrations = Files.readString(BEHAVIOR_REGISTRATION_SOURCE, StandardCharsets.UTF_8);
		assertTrue(registrations.contains("register(\"namesake\", new RadiantFocusBehaviors.Namesake())"),
			"Namesake behavior should be registered");

		String behavior = Files.readString(RADIANT_BEHAVIORS_SOURCE, StandardCharsets.UTF_8);
		assertTrue(behavior.contains("class Namesake implements FocusBehavior"),
			"Namesake should be a normal server-side Focus behavior");
		assertTrue(behavior.contains("DataComponents.CUSTOM_NAME"),
			"Namesake should detect custom-named carried items");
		assertTrue(behavior.contains("Attributes.LUCK"),
			"Namesake should apply a real vanilla Luck attribute modifier");
		assertTrue(behavior.contains("getEntitiesOfClass(LivingEntity.class"),
			"Namesake should scan nearby living entities for named non-player mobs");
		assertTrue(behavior.contains("!(target instanceof Player)"),
			"Namesake should not let nearby custom-named players activate the relic");

		String lang = Files.readString(LANG_FILE, StandardCharsets.UTF_8);
		assertTrue(lang.contains("item.attuned.namesake_focus.effect")
				&& lang.contains("custom-named"),
			"Namesake tooltip should explain that its Luck is conditional");
	}

	@Test
	void rootstepBonusesAreConditionalOnNaturalFootingInsteadOfAlwaysOn() throws IOException {
		JsonObject root = focusRootsByItem().get("attuned:rootstep_focus");
		assertTrue(root != null, "Rootstep Focus data should ship");
		assertTrue(!root.has("modifiers"),
			"Rootstep should not grant unconditional movement and fall bonuses through static modifiers");
		assertEquals("attuned:rootstep", root.get("behavior").getAsString(),
			"Rootstep should delegate its natural-ground bonuses to a runtime behavior");

		String registrations = Files.readString(BEHAVIOR_REGISTRATION_SOURCE, StandardCharsets.UTF_8);
		assertTrue(registrations.contains("import dev.attuned.content.behavior.RootstepBehavior;"),
			"Rootstep behavior should be imported for registration");
		assertTrue(registrations.contains("register(\"rootstep\", new RootstepBehavior())"),
			"Rootstep behavior should be registered");

		String behavior = Files.readString(ROOTSTEP_BEHAVIOR_SOURCE, StandardCharsets.UTF_8);
		assertTrue(behavior.contains("class RootstepBehavior implements FocusBehavior"),
			"Rootstep should be a normal server-side Focus behavior");
		assertTrue(behavior.contains("Attributes.MOVEMENT_SPEED")
				&& behavior.contains("Attributes.FALL_DAMAGE_MULTIPLIER"),
			"Rootstep should apply real vanilla movement and fall-damage attributes");
		assertTrue(behavior.contains("BlockTags.LEAVES")
				&& behavior.contains("Blocks.GRASS_BLOCK")
				&& behavior.contains("Blocks.MOSS_BLOCK"),
			"Rootstep should check natural footing instead of applying everywhere");
		assertTrue(behavior.contains("onDeactivate")
				&& behavior.contains("removeModifier"),
			"Rootstep should remove transient modifiers when the condition or Focus ends");

		String lang = Files.readString(LANG_FILE, StandardCharsets.UTF_8);
		assertTrue(lang.contains("item.attuned.rootstep_focus.effect")
				&& lang.contains("natural"),
			"Rootstep tooltip should explain that its bonuses need natural footing");
	}

	@Test
	void mossheartResistanceTriggersFromHostileHitsOnGreenFootingInsteadOfAlwaysOnArmor() throws IOException {
		JsonObject root = focusRootsByItem().get("attuned:mossheart_focus");
		assertTrue(root != null, "Mossheart Focus data should ship");
		assertTrue(!root.has("modifiers"),
			"Mossheart should not grant unconditional armor through static modifiers");
		assertEquals("attuned:mossheart", root.get("behavior").getAsString(),
			"Mossheart should delegate its reactive Resistance to a runtime behavior");

		String registrations = Files.readString(BEHAVIOR_REGISTRATION_SOURCE, StandardCharsets.UTF_8);
		assertTrue(registrations.contains("import dev.attuned.content.behavior.MossheartBehavior;"),
			"Mossheart behavior should be imported for registration");
		assertTrue(registrations.contains("register(\"mossheart\", new MossheartBehavior())"),
			"Mossheart behavior should be registered");

		String behavior = Files.readString(MOSSHEART_BEHAVIOR_SOURCE, StandardCharsets.UTF_8);
		assertTrue(behavior.contains("class MossheartBehavior implements FocusBehavior"),
			"Mossheart should be a normal server-side Focus behavior");
		assertTrue(behavior.contains("ServerLivingEntityEvents.AFTER_DAMAGE.register(MossheartBehavior::afterDamage)"),
			"Mossheart should react to actual hostile hits");
		assertTrue(behavior.contains("CombatTargets.isHostileOrPvpOpponent"),
			"Mossheart should only trigger from hostile mobs or valid PvP opponents");
		assertTrue(behavior.contains("MobEffects.RESISTANCE")
				&& behavior.contains("RESISTANCE_TICKS = 60")
				&& behavior.contains("COOLDOWN_TICKS = 240"),
			"Mossheart should grant Resistance I for 60 ticks on a 240-tick cooldown");
		assertTrue(behavior.contains("BlockTags.LEAVES")
				&& behavior.contains("Blocks.GRASS_BLOCK")
				&& behavior.contains("Blocks.MOSS_BLOCK"),
			"Mossheart should require moss, grass, or leaves underfoot");
		assertTrue(behavior.contains("hasActiveMossheart")
				&& behavior.contains("AttunedPlayerCleanup.onForget")
				&& behavior.contains("AttunedServerCleanup.onStop"),
			"Mossheart should require an active Focus and clean up cooldown state");

		String lang = Files.readString(LANG_FILE, StandardCharsets.UTF_8);
		assertTrue(lang.contains("item.attuned.mossheart_focus.effect")
				&& lang.contains("Resistance")
				&& lang.contains("moss"),
			"Mossheart tooltip should explain its conditional Resistance trigger");
	}

	@Test
	void rivetResistanceIsConditionalOnBracingOrMetalFootingInsteadOfAlwaysOn() throws IOException {
		JsonObject root = focusRootsByItem().get("attuned:rivet_focus");
		assertTrue(root != null, "Rivet Focus data should ship");
		assertTrue(!root.has("modifiers"),
			"Rivet should not grant unconditional knockback resistance through static modifiers");
		assertEquals("attuned:rivet", root.get("behavior").getAsString(),
			"Rivet should delegate its braced knockback resistance to a runtime behavior");

		String registrations = Files.readString(BEHAVIOR_REGISTRATION_SOURCE, StandardCharsets.UTF_8);
		assertTrue(registrations.contains("import dev.attuned.content.behavior.RivetBehavior;"),
			"Rivet behavior should be imported for registration");
		assertTrue(registrations.contains("register(\"rivet\", new RivetBehavior())"),
			"Rivet behavior should be registered");

		String behavior = Files.readString(RIVET_BEHAVIOR_SOURCE, StandardCharsets.UTF_8);
		assertTrue(behavior.contains("class RivetBehavior implements FocusBehavior"),
			"Rivet should be a normal server-side Focus behavior");
		assertTrue(behavior.contains("Attributes.KNOCKBACK_RESISTANCE"),
			"Rivet should apply a real vanilla knockback-resistance attribute");
		assertTrue(behavior.contains("player.isCrouching()")
				&& behavior.contains("player.isBlocking()"),
			"Rivet should activate while the player braces by crouching or blocking");
		assertTrue(behavior.contains("Blocks.IRON_BLOCK")
				&& behavior.contains("Blocks.COPPER_BLOCK"),
			"Rivet should also activate while standing on metal blocks");
		assertTrue(behavior.contains("onDeactivate")
				&& behavior.contains("removeModifier"),
			"Rivet should remove transient modifiers when the condition or Focus ends");

		String lang = Files.readString(LANG_FILE, StandardCharsets.UTF_8);
		assertTrue(lang.contains("item.attuned.rivet_focus.effect")
				&& lang.contains("crouching")
				&& lang.contains("metal"),
			"Rivet tooltip should explain that its resistance needs bracing or metal footing");
	}

	@Test
	void kilnwardResistanceTriggersFromHostileHitsNearHeatInsteadOfAlwaysOnArmor() throws IOException {
		JsonObject root = focusRootsByItem().get("attuned:kilnward_focus");
		assertTrue(root != null, "Kilnward Focus data should ship");
		assertTrue(!root.has("modifiers"),
			"Kilnward should not grant unconditional armor through static modifiers");
		assertEquals("attuned:kilnward", root.get("behavior").getAsString(),
			"Kilnward should delegate its heat-adjacent Resistance to a runtime behavior");

		String registrations = Files.readString(BEHAVIOR_REGISTRATION_SOURCE, StandardCharsets.UTF_8);
		assertTrue(registrations.contains("import dev.attuned.content.behavior.KilnwardBehavior;"),
			"Kilnward behavior should be imported for registration");
		assertTrue(registrations.contains("register(\"kilnward\", new KilnwardBehavior())"),
			"Kilnward behavior should be registered");

		String behavior = Files.readString(KILNWARD_BEHAVIOR_SOURCE, StandardCharsets.UTF_8);
		assertTrue(behavior.contains("class KilnwardBehavior implements FocusBehavior"),
			"Kilnward should be a normal server-side Focus behavior");
		assertTrue(behavior.contains("ServerLivingEntityEvents.AFTER_DAMAGE.register(KilnwardBehavior::afterDamage)"),
			"Kilnward should react to actual hostile hits");
		assertTrue(behavior.contains("CombatTargets.isHostileOrPvpOpponent"),
			"Kilnward should only trigger from hostile mobs or valid PvP opponents");
		assertTrue(behavior.contains("MobEffects.RESISTANCE")
				&& behavior.contains("RESISTANCE_TICKS = 60")
				&& behavior.contains("COOLDOWN_TICKS = 240"),
			"Kilnward should grant Resistance I for 60 ticks on a 240-tick cooldown");
		assertTrue(behavior.contains("AbstractFurnaceBlock.LIT")
				&& behavior.contains("Blocks.MAGMA_BLOCK")
				&& behavior.contains("Blocks.LAVA"),
			"Kilnward should require lit furnaces, magma, or lava nearby");
		assertTrue(behavior.contains("hasActiveKilnward")
				&& behavior.contains("AttunedPlayerCleanup.onForget")
				&& behavior.contains("AttunedServerCleanup.onStop"),
			"Kilnward should require an active Focus and clean up cooldown state");

		String lang = Files.readString(LANG_FILE, StandardCharsets.UTF_8);
		assertTrue(lang.contains("item.attuned.kilnward_focus.effect")
				&& lang.contains("Resistance")
				&& lang.contains("heat")
				&& lang.contains("not fire immunity"),
			"Kilnward tooltip should explain the conditional Resistance and no-fire-immunity caveat");
	}

	@Test
	void temperBonusIsTemporaryAfterUsingForgeBlocksInsteadOfAlwaysOnAttackDamage() throws IOException {
		JsonObject root = focusRootsByItem().get("attuned:temper_focus");
		assertTrue(root != null, "Temper Focus data should ship");
		assertTrue(!root.has("modifiers"),
			"Temper should not grant unconditional attack damage through static modifiers");
		assertEquals("attuned:temper", root.get("behavior").getAsString(),
			"Temper should delegate its forge-window state to a runtime behavior");

		String registrations = Files.readString(BEHAVIOR_REGISTRATION_SOURCE, StandardCharsets.UTF_8);
		assertTrue(registrations.contains("import dev.attuned.content.behavior.TemperBehavior;"),
			"Temper behavior should be imported for registration");
		assertTrue(registrations.contains("register(\"temper\", new TemperBehavior())"),
			"Temper behavior should be registered");

		String behavior = Files.readString(TEMPER_BEHAVIOR_SOURCE, StandardCharsets.UTF_8);
		assertTrue(behavior.contains("class TemperBehavior implements FocusBehavior"),
			"Temper should be a normal server-side Focus behavior");
		assertTrue(behavior.contains("UseBlockCallback.EVENT.register(TemperBehavior::useBlock)"),
			"Temper should arm its damage window after block use");
		assertTrue(behavior.contains("WINDOW_TICKS = 200")
				&& behavior.contains("isTempered")
				&& behavior.contains("AttunedPlayerCleanup.onForget")
				&& behavior.contains("AttunedServerCleanup.onStop"),
			"Temper should keep a cleaned-up 200-tick per-player forge window");
		assertTrue(behavior.contains("Blocks.FURNACE")
				&& behavior.contains("Blocks.BLAST_FURNACE")
				&& behavior.contains("Blocks.SMITHING_TABLE")
				&& behavior.contains("Blocks.ANVIL"),
			"Temper should arm from furnace, blast furnace, smithing table, or anvil use");

		String combat = Files.readString(ATTUNED_COMBAT_SOURCE, StandardCharsets.UTF_8);
		assertTrue(combat.contains("TEMPER_BONUS = 0.08F"),
			"Temper should use the planned 8% damage bonus");
		assertTrue(combat.contains("TemperBehavior.applies(player)")
				&& combat.contains("isChargedDirectMelee(player, defender, source"),
			"Temper should apply only to direct fully charged melee hits using the pre-reset charge snapshot");

		String lang = Files.readString(LANG_FILE, StandardCharsets.UTF_8);
		assertTrue(lang.contains("item.attuned.temper_focus.effect")
				&& lang.contains("forge")
				&& lang.contains("fully charged")
				&& lang.contains("8%"),
			"Temper tooltip should explain the temporary forge-window damage bonus");
	}

	@Test
	void maskResistsRevealAfterCrouchingInLowLightInsteadOfGrantingSneakSpeed() throws IOException {
		JsonObject root = focusRootsByItem().get("attuned:mask_focus");
		assertTrue(root != null, "Mask Focus data should ship");
		assertTrue(!root.has("modifiers"),
			"Mask should not grant unconditional sneaking speed through static modifiers");
		assertEquals("attuned:mask", root.get("behavior").getAsString(),
			"Mask should delegate reveal resistance to a runtime behavior");

		String registrations = Files.readString(BEHAVIOR_REGISTRATION_SOURCE, StandardCharsets.UTF_8);
		assertTrue(registrations.contains("import dev.attuned.content.behavior.MaskBehavior;"),
			"Mask behavior should be imported for registration");
		assertTrue(registrations.contains("register(\"mask\", new MaskBehavior())"),
			"Mask behavior should be registered");

		String behavior = Files.readString(MASK_BEHAVIOR_SOURCE, StandardCharsets.UTF_8);
		assertTrue(behavior.contains("class MaskBehavior implements FocusBehavior"),
			"Mask should be a normal server-side Focus behavior");
		assertTrue(behavior.contains("CHARGE_TICKS = 40")
				&& behavior.contains("RESIST_TICKS = 100")
				&& behavior.contains("MAX_LIGHT = 7"),
			"Mask should use the planned 40-tick low-light charge and 100-tick resistance window");
		assertTrue(behavior.contains("player.isCrouching()")
				&& behavior.contains("getMaxLocalRawBrightness"),
			"Mask should charge from crouching in low light");
		assertTrue(behavior.contains("MobEffects.GLOWING")
				&& behavior.contains("removeEffect")
				&& behavior.contains("resistsReveal"),
			"Mask should actively resist Glowing/reveal effects");
		assertTrue(behavior.contains("AttunedPlayerCleanup.onForget")
				&& behavior.contains("AttunedServerCleanup.onStop"),
			"Mask should clean up per-player reveal-resistance state");

		String lantern = Files.readString(LANTERN_BEHAVIOR_SOURCE, StandardCharsets.UTF_8);
		assertTrue(lantern.contains("!MaskBehavior.resistsReveal(target)"),
			"Lantern reveal should respect Mask reveal resistance");

		String radiant = Files.readString(RADIANT_BEHAVIORS_SOURCE, StandardCharsets.UTF_8);
		assertTrue(radiant.contains("!MaskBehavior.resistsReveal(target)"),
			"Bellwether reveal should respect Mask reveal resistance");

		String pacts = Files.readString(PACTS_SOURCE, StandardCharsets.UTF_8);
		assertTrue(pacts.contains("MaskBehavior.resistsReveal(defender)"),
			"Radiant Covenant reveal should respect Mask reveal resistance");

		String lang = Files.readString(LANG_FILE, StandardCharsets.UTF_8);
		assertTrue(lang.contains("item.attuned.mask_focus.effect")
				&& lang.contains("crouching")
				&& lang.contains("low light")
				&& lang.contains("reveal"),
			"Mask tooltip should explain its low-light reveal resistance");
	}

	@Test
	void whisperIsAbilityKeyDetectionSofteningInsteadOfAlwaysOnSneakSpeed() throws IOException {
		JsonObject root = focusRootsByItem().get("attuned:whisper_focus");
		assertTrue(root != null, "Whisper Focus data should ship");
		assertTrue(!root.has("affinity"),
			"Whisper should remain its planned neutral Unseen Focus");
		assertTrue(!root.has("modifiers"),
			"Whisper should not grant unconditional sneaking speed through static modifiers");
		assertEquals("attuned:whisper", root.get("behavior").getAsString(),
			"Whisper should delegate its sound and detection softening to a runtime behavior");

		String registrations = Files.readString(BEHAVIOR_REGISTRATION_SOURCE, StandardCharsets.UTF_8);
		assertTrue(registrations.contains("import dev.attuned.content.behavior.WhisperBehavior;"),
			"Whisper behavior should be imported for registration");
		assertTrue(registrations.contains("register(\"whisper\", new WhisperBehavior())"),
			"Whisper behavior should be registered");

		String behavior = Files.readString(WHISPER_BEHAVIOR_SOURCE, StandardCharsets.UTF_8);
		assertTrue(behavior.contains("class WhisperBehavior implements FocusBehavior"),
			"Whisper should be a normal server-side Focus behavior");
		assertTrue(behavior.contains("hasActiveAbility()")
				&& behavior.contains("onAbility(ServerPlayer player, ItemStack focus)")
				&& behavior.contains("abilityCooldownTicks()"),
			"Whisper should use the shared Focus Ability key path");
		assertTrue(behavior.contains("SOFTEN_TICKS = 80")
				&& behavior.contains("COOLDOWN_TICKS = 300"),
			"Whisper should use the planned 80-tick softening window and 300-tick cooldown");
		assertTrue(behavior.contains("mob.getTarget() == player")
				&& behavior.contains("!mob.hasLineOfSight(player)")
				&& behavior.contains("mob.setTarget(null)"),
			"Whisper should soften detection by dropping broken-sight mob targets");
		assertTrue(behavior.contains("immuneToMisdirection")
				&& behavior.contains("AttunedPlayerCleanup.onForget")
				&& behavior.contains("AttunedServerCleanup.onStop"),
			"Whisper should avoid boss disruption and clean up per-player state");

		String lang = Files.readString(LANG_FILE, StandardCharsets.UTF_8);
		assertTrue(lang.contains("item.attuned.whisper_focus.effect")
				&& lang.contains("Focus Ability key")
				&& lang.contains("soften")
				&& lang.contains("detection"),
			"Whisper tooltip should explain the ability-key detection softening");
	}

	@Test
	void bloomRewardsPlantGatheringInsteadOfGrantingAlwaysOnLuck() throws IOException {
		JsonObject root = focusRootsByItem().get("attuned:bloom_focus");
		assertTrue(root != null, "Bloom Focus data should ship");
		assertTrue(!root.has("modifiers"),
			"Bloom should not grant unconditional Luck through static modifiers");
		assertEquals("attuned:bloom", root.get("behavior").getAsString(),
			"Bloom should delegate its plant-gathering rewards to a runtime behavior");

		String registrations = Files.readString(BEHAVIOR_REGISTRATION_SOURCE, StandardCharsets.UTF_8);
		assertTrue(registrations.contains("import dev.attuned.content.behavior.BloomBehavior;"),
			"Bloom behavior should be imported for registration");
		assertTrue(registrations.contains("register(\"bloom\", new BloomBehavior())"),
			"Bloom behavior should be registered");

		String behavior = Files.readString(BLOOM_BEHAVIOR_SOURCE, StandardCharsets.UTF_8);
		assertTrue(behavior.contains("class BloomBehavior implements FocusBehavior"),
			"Bloom should be a normal server-side Focus behavior");
		assertTrue(behavior.contains("PlayerBlockBreakEvents.AFTER.register(BloomBehavior::afterBlockBreak)"),
			"Bloom should hook vanilla plant gathering");
		assertTrue(behavior.contains("hasActiveBloom"),
			"Bloom should only reward players with an active Bloom Focus");
		assertTrue(behavior.contains("BlockTags.FLOWERS")
				&& behavior.contains("Items.HONEYCOMB")
				&& behavior.contains("Items.WHEAT_SEEDS"),
			"Bloom should award rare flower, bee-adjacent, or seed rewards");

		String lang = Files.readString(LANG_FILE, StandardCharsets.UTF_8);
		assertTrue(lang.contains("item.attuned.bloom_focus.effect")
				&& lang.contains("gathering plants"),
			"Bloom tooltip should explain its plant-gathering reward");
	}

	private static Map<String, JsonObject> focusRootsByItem() throws IOException {
		Map<String, JsonObject> foci = new TreeMap<>();
		try (Stream<Path> paths = Files.list(FOCUS_DATA_DIR)) {
			for (Path file : paths.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
				JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
					.getAsJsonObject();
				foci.put(root.get("item").getAsString(), root);
			}
		}
		return foci;
	}

	private static boolean hasString(JsonObject root, String key, String value) {
		JsonElement element = root.get(key);
		return element != null && element.isJsonPrimitive() && value.equals(element.getAsString());
	}

	private static String methodBody(String source, String signaturePrefix) {
		int signature = source.indexOf(signaturePrefix);
		assertTrue(signature >= 0, "Missing method: " + signaturePrefix);
		int bodyStart = source.indexOf('{', signature);
		assertTrue(bodyStart >= 0, "Missing method body: " + signaturePrefix);
		int depth = 0;
		for (int index = bodyStart; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '{') {
				depth++;
			} else if (current == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(bodyStart, index + 1);
				}
			}
		}
		throw new AssertionError("Unclosed method body: " + signaturePrefix);
	}

	private static Set<String> firstWaveFoci() {
		Set<String> items = new HashSet<>(HOLY_FOCI);
		for (Set<String> factionItems : FACTION_FOCI.values()) {
			items.addAll(factionItems);
		}
		items.add("attuned:mask_focus");
		items.add("attuned:whisper_focus");
		items.add("attuned:blackout_focus");
		return Set.copyOf(items);
	}
}
