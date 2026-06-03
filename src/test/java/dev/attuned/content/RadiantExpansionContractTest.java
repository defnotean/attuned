package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	private static final Set<String> HOLY_FOCI = Set.of(
		"attuned:bellwether_focus",
		"attuned:censer_focus",
		"attuned:namesake_focus",
		"attuned:oathguard_focus",
		"attuned:sunlance_focus",
		"attuned:threshold_focus",
		"attuned:votive_focus",
		"attuned:last_rites_focus");
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
	void holyCompletesTheFourAffinityWheel() {
		Affinity holy = Affinity.valueOf("HOLY");

		assertEquals("holy", holy.getSerializedName());
		assertTrue(holy.beats(Affinity.FURY), "Holy should pressure Fury");
		assertTrue(Affinity.FURY.beats(Affinity.BASTION), "Fury should still pressure Bastion");
		assertTrue(Affinity.BASTION.beats(Affinity.ZEPHYR), "Bastion should still pressure Zephyr");
		assertTrue(Affinity.ZEPHYR.beats(holy), "Zephyr should counter Holy");
		assertTrue(!holy.beats(Affinity.BASTION), "Holy and Bastion should be neutral opposites");
		assertTrue(!Affinity.BASTION.beats(holy), "Bastion and Holy should be neutral opposites");
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

		assertEquals(HOLY_FOCI, holyFoci, "Shipped Holy Foci should include Radiant, Reliquary, and Revenant rites");
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
