package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Content sweep for the Umbral Eclipse Focus set: the five shadow Foci and the Total
 * Eclipse Confluence. Mirrors the faction-batch tests in {@code FocusDataConsistencyTest}
 * — it runs purely against the data and source files, never bootstrapping Minecraft.
 */
class UmbralEclipseContractTest {
	private static final Path FOCUS_DIR =
		Path.of("src/main/resources/data/attuned/attuned/focus");
	private static final Path SYNERGY_DIR =
		Path.of("src/main/resources/data/attuned/attuned/synergy");
	private static final Path ADVANCEMENT_DIR =
		Path.of("src/main/resources/data/attuned/advancement/attunement");
	private static final Path BEHAVIORS_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedFocusBehaviors.java");
	private static final Path CONTENT_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedContent.java");
	private static final Path COMBAT_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/AttunedCombat.java");
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	private static final String FACTION = "attuned:umbral";

	/** Each Umbral Focus and its expected affinity / cost / unique / behavior. */
	private record FocusSpec(String affinity, int cost, boolean unique, String behavior) {}

	private static final Map<String, FocusSpec> UMBRAL_FOCI = umbralFoci();

	private static Map<String, FocusSpec> umbralFoci() {
		Map<String, FocusSpec> foci = new LinkedHashMap<>();
		foci.put("gloomstride_focus", new FocusSpec("umbral", 4, false, "attuned:gloomstride"));
		foci.put("duskward_focus", new FocusSpec("umbral", 4, false, "attuned:duskward"));
		foci.put("shadowmeld_focus", new FocusSpec("umbral", 4, false, "attuned:shadowmeld"));
		foci.put("dreadfang_focus", new FocusSpec("umbral", 5, true, "attuned:dreadfang"));
		foci.put("eclipse_focus", new FocusSpec("umbral", 6, true, "attuned:eclipse"));
		return foci;
	}

	@Test
	void umbralFociDeclareTheirFactionAffinityCostAndBehavior() throws IOException {
		String behaviors = read(BEHAVIORS_SOURCE);
		String content = read(CONTENT_SOURCE);
		for (Map.Entry<String, FocusSpec> entry : UMBRAL_FOCI.entrySet()) {
			String name = entry.getKey();
			FocusSpec spec = entry.getValue();
			JsonObject root = focusDefinitionRoot(FOCUS_DIR.resolve(name + ".json"));

			assertEquals("attuned:" + name, root.get("item").getAsString(),
				name + ": item id should match its file name");
			assertEquals(FACTION, root.get("faction").getAsString(),
				name + ": Umbral Foci must declare the umbral faction");
			assertEquals(spec.affinity(), root.get("affinity").getAsString(),
				name + ": unexpected affinity");
			assertEquals(spec.cost(), root.get("cost").getAsInt(), name + ": unexpected cost");
			assertEquals(spec.unique(),
				root.has("unique") && root.get("unique").getAsBoolean(),
				name + ": unexpected unique flag");

			JsonElement behavior = root.get("behavior");
			assertTrue(behavior != null && behavior.isJsonPrimitive(),
				name + ": Umbral Foci must declare a behavior id");
			assertEquals(spec.behavior(), behavior.getAsString(), name + ": unexpected behavior id");

			String behaviorPath = spec.behavior().substring(spec.behavior().indexOf(':') + 1);
			assertTrue(behaviors.contains("register(\"" + behaviorPath + "\""),
				name + ": behavior " + spec.behavior() + " must be registered in AttunedFocusBehaviors");

			String field = name.toUpperCase();
			assertTrue(content.contains("registerFocus(\"" + name + "\")"),
				name + ": Focus item must be registered in AttunedContent");
			assertTrue(content.contains(field + " = registerFocus(\"" + name + "\");"),
				name + ": Focus field should follow the shipped naming convention");
		}
	}

	@Test
	void dreadfangProcIsWiredIntoTheCombatHook() throws IOException {
		String combat = read(COMBAT_SOURCE);
		assertTrue(combat.contains("DREADFANG_FOCUS"),
			"AttunedCombat should reference the Dreadfang Focus id");
		assertTrue(combat.contains("DreadfangBehavior.applyTo("),
			"Dreadfang's Darkness proc should fire from the combat after-damage hook");
		assertTrue(combat.contains("CombatTargets.isHostileOrPvpOpponent("),
			"Dreadfang should gate on the authoritative hostile/PvP target predicate");
	}

	@Test
	void umbralFactionIsTranslated() throws IOException {
		JsonObject lang = languageRoot();
		assertTrue(lang.has("faction.attuned.umbral"),
			"The umbral faction must have a lang entry");
		for (String name : UMBRAL_FOCI.keySet()) {
			assertTrue(lang.has("item.attuned." + name), name + ": missing item name lang key");
			assertTrue(lang.has("item.attuned." + name + ".lore"), name + ": missing lore lang key");
			assertTrue(lang.has("item.attuned." + name + ".lore2"), name + ": missing lore2 lang key");
			assertTrue(lang.has("item.attuned." + name + ".effect"), name + ": missing effect lang key");
		}
	}

	@Test
	void totalEclipseConfluenceBindsTheThreeUmbralFociAndPureModifiers() throws IOException {
		JsonObject root = focusDefinitionRoot(SYNERGY_DIR.resolve("total_eclipse.json"));
		JsonArray members = root.getAsJsonArray("members");
		List<String> memberIds = members.asList().stream().map(JsonElement::getAsString).toList();
		assertEquals(List.of(
				"attuned:eclipse_focus", "attuned:gloomstride_focus", "attuned:duskward_focus"),
			memberIds, "Total Eclipse must bind exactly its three Umbral members");

		assertFalse(root.has("behavior"),
			"Total Eclipse is a pure-modifier Confluence — it needs no behavior");
		JsonArray modifiers = root.getAsJsonArray("modifiers");
		assertEquals(2, modifiers.size(), "Total Eclipse should grant two attribute modifiers");

		Map<String, Double> byAttribute = new LinkedHashMap<>();
		for (JsonElement modifier : modifiers) {
			JsonObject object = modifier.getAsJsonObject();
			assertEquals("add_value", object.get("operation").getAsString(),
				"Total Eclipse modifiers should add flat values");
			byAttribute.put(normalizeAttributeId(object.get("attribute").getAsString()), object.get("amount").getAsDouble());
		}
		assertEquals(4.0D, byAttribute.get("minecraft:max_health"), 0.0001D,
			"Total Eclipse should grant +4 max health");
		assertEquals(1.0D, byAttribute.get("minecraft:attack_damage"), 0.0001D,
			"Total Eclipse should grant +1 attack damage");
	}

	@Test
	void totalEclipseHasLangAndAdvancement() throws IOException {
		JsonObject lang = languageRoot();
		assertTrue(lang.has("confluence.attuned.total_eclipse.name"),
			"Total Eclipse should have a name lang key");
		assertTrue(lang.has("confluence.attuned.total_eclipse.desc"),
			"Total Eclipse should have a desc lang key");
		assertTrue(Files.isRegularFile(ADVANCEMENT_DIR.resolve("confluence_total_eclipse.json")),
			"Total Eclipse should have a discovery advancement");
	}

	private static JsonObject focusDefinitionRoot(Path file) throws IOException {
		return JsonParser.parseString(read(file)).getAsJsonObject();
	}

	private static JsonObject languageRoot() throws IOException {
		return JsonParser.parseString(read(LANG_FILE)).getAsJsonObject();
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static String normalizeAttributeId(String id) {
		return id.replace("minecraft:generic.", "minecraft:");
	}
}
