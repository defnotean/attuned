package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Dossier contract coverage for the first Deep Lanterns content slice.
 */
class DeepLanternsContentContractTest {
	private static final Path CONTENT_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedContent.java");
	private static final Path JOURNAL_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunementJournalItem.java");
	private static final Path FOCUS_DATA =
		Path.of("src/main/resources/data/attuned/attuned/focus");
	private static final Path BEHAVIOR_DATA =
		Path.of("src/main/resources/data/attuned/attuned/focus_behavior");
	private static final Path LANTERN_TAG =
		Path.of("src/main/resources/data/attuned/tags/block/lanterns.json");
	private static final Path REFERENCE =
		Path.of("docs/reference.md");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");
	private static final Path ITEM_TEXTURE_DIR =
		Path.of("src/main/resources/assets/attuned/textures/item");

	@Test
	void deepLanternFocusItemsAreRegisteredInTheShippedRoster() throws IOException {
		String source = Files.readString(CONTENT_SOURCE, StandardCharsets.UTF_8);

		assertContains(source, "public static Item CAVEWICK_FOCUS;");
		assertContains(source, "registerFocus(\"cavewick_focus\", item -> CAVEWICK_FOCUS = item);");
		assertContains(source, "public static Item GLOWLINE_FOCUS;");
		assertContains(source, "registerFocus(\"glowline_focus\", item -> GLOWLINE_FOCUS = item);");
		assertContains(source, "public static Item RESCUEFLAME_FOCUS;");
		assertContains(source, "registerFocus(\"rescueflame_focus\", item -> RESCUEFLAME_FOCUS = item);");
		assertContains(source, "public static Item DEPTHGLASS_FOCUS;");
		assertContains(source, "registerFocus(\"depthglass_focus\", item -> DEPTHGLASS_FOCUS = item);");
		assertBefore(source, "registerFocus(\"cavewick_focus\"", "registerCustomFocusPool();",
			"Deep Lantern Foci should be scheduled before the custom Focus pool");
	}

	@Test
	void deepLanternFocusDefinitionsMatchTheDossierSlice() throws IOException {
		assertFocus("cavewick_focus", 2, null, "attuned:deep_lanterns", "attuned:cavewick");
		assertFocus("glowline_focus", 3, null, "attuned:deep_lanterns", "attuned:glowline");
		assertFocus("rescueflame_focus", 4, "holy", "attuned:deep_lanterns", "attuned:rescueflame");
		assertFocus("depthglass_focus", 3, null, "attuned:deep_lanterns", "attuned:depthglass");
	}

	@Test
	void deepLanternBehaviorsStayOnTheDataPalette() throws IOException {
		JsonObject cavewick = behavior("cavewick");
		assertEquals("attuned:block_context_effect", cavewick.get("type").getAsString());
		assertEquals("attuned:lanterns", cavewick.get("block_tag").getAsString());
		assertEquals(4, cavewick.get("radius").getAsInt());
		assertEquals("minecraft:night_vision", cavewick.get("effect").getAsString());

		JsonObject glowline = behavior("glowline");
		assertEquals("attuned:navigation_hint", glowline.get("type").getAsString());
		assertEquals("circle_ping", glowline.get("target_kind").getAsString());
		assertTrue(glowline.get("same_dimension_only").getAsBoolean(),
			"Glowline should not route to cross-dimensional pings");

		JsonObject rescueflame = behavior("rescueflame");
		assertEquals("attuned:party_assist", rescueflame.get("type").getAsString());
		assertEquals("drowning_members", rescueflame.get("target_filter").getAsString());
		assertEquals("minecraft:water_breathing", rescueflame.getAsJsonObject("effect").get("effect").getAsString());
		assertEquals("message.attuned.rescueflame_assist", rescueflame.get("message_key").getAsString());

		JsonObject depthglass = behavior("depthglass");
		assertEquals("attuned:navigation_hint", depthglass.get("type").getAsString());
		assertEquals("held_lodestone_compass", depthglass.get("target_kind").getAsString());
		assertTrue(depthglass.get("same_dimension_only").getAsBoolean(),
			"Depthglass should keep explicit marker routing dimension-local");
	}

	@Test
	void deepLanternsDeclareTheLanternBlockTagUsedByCavewickAndTheSetBonus() throws IOException {
		JsonObject tag = json(LANTERN_TAG);
		assertTrue(!tag.has("replace") || !tag.get("replace").getAsBoolean(),
			"Attuned's lantern tag should extend vanilla/resource-pack entries instead of replacing them");
		assertArrayContains(tag, "values", "minecraft:lantern");
		assertArrayContains(tag, "values", "minecraft:soul_lantern");
	}

	@Test
	void deepLanternLanguageDocsAndShippedArtArePresent() throws IOException {
		JsonObject lang = json(LANG);
		assertLanguage(lang, "faction.attuned.deep_lanterns");
		assertLanguage(lang, "faction.attuned.set_bonus.deep_lanterns");
		assertLanguage(lang, "message.attuned.rescueflame_assist");
		for (String focus : new String[] {
				"cavewick_focus", "glowline_focus", "rescueflame_focus", "depthglass_focus" }) {
			assertLanguage(lang, "item.attuned." + focus);
			assertLanguage(lang, "item.attuned." + focus + ".lore");
			assertLanguage(lang, "item.attuned." + focus + ".lore2");
			assertLanguage(lang, "item.attuned." + focus + ".effect");
		}

		String reference = Files.readString(REFERENCE, StandardCharsets.UTF_8);
		assertContains(reference, "`attuned:deep_lanterns`");
		assertContains(reference, "`attuned:cavewick`");
		assertContains(reference, "`attuned:glowline`");
		assertContains(reference, "`attuned:rescueflame`");
		assertContains(reference, "`attuned:depthglass`");

		for (String focus : new String[] {
				"cavewick_focus", "glowline_focus", "rescueflame_focus", "depthglass_focus" }) {
			assertTrue(Files.isRegularFile(ITEM_TEXTURE_DIR.resolve(focus + ".png")),
				"Deep Lantern Focus should ship a texture: " + focus);
			assertTrue(Files.isRegularFile(ITEM_TEXTURE_DIR.resolve(focus + ".png.mcmeta")),
				"Deep Lantern Focus should ship animation metadata: " + focus);
		}
	}

	@Test
	void deepLanternsHaveJournalParagraphAndWorldClueBeforeRelease() throws IOException {
		String journalSource = Files.readString(JOURNAL_SOURCE, StandardCharsets.UTF_8);
		JsonObject lang = json(LANG);
		String reference = Files.readString(REFERENCE, StandardCharsets.UTF_8);

		assertContains(journalSource, "\"journal.attuned.page52\"");
		assertLanguage(lang, "journal.attuned.page52");
		String page = lang.get("journal.attuned.page52").getAsString();
		assertContains(page, "Deep Lanterns");
		assertContains(page, "faction, not an affinity");
		assertContains(page, "Attunement Sanctum");
		assertContains(page, "Circle");
		assertContains(reference, "Deep Lanterns use the Attunement Sanctum as their first world clue");
	}

	private static void assertFocus(String name, int cost, String affinity, String faction, String behavior)
			throws IOException {
		JsonObject root = json(FOCUS_DATA.resolve(name + ".json"));
		assertEquals("attuned:" + name, root.get("item").getAsString());
		assertEquals(cost, root.get("cost").getAsInt());
		assertTrue(root.has("unique") && root.get("unique").getAsBoolean(),
			"Deep Lantern behavior-backed Foci should be unique: " + name);
		if (affinity == null) {
			assertTrue(!root.has("affinity"), "Neutral Deep Lantern Focus should omit affinity: " + name);
		} else {
			assertEquals(affinity, root.get("affinity").getAsString());
		}
		assertEquals(faction, root.get("faction").getAsString());
		assertEquals(behavior, root.get("behavior").getAsString());
	}

	private static JsonObject behavior(String name) throws IOException {
		return json(BEHAVIOR_DATA.resolve(name + ".json"));
	}

	private static JsonObject json(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static void assertLanguage(JsonObject lang, String key) {
		assertTrue(lang.has(key), "Missing language key: " + key);
	}

	private static void assertArrayContains(JsonObject root, String key, String value) {
		for (JsonElement element : root.getAsJsonArray(key)) {
			if (value.equals(element.getAsString())) {
				return;
			}
		}
		throw new AssertionError("Expected " + key + " to contain " + value + ": " + root);
	}

	private static void assertContains(String source, String needle) {
		assertTrue(source.contains(needle), "Expected source to contain: " + needle);
	}

	private static void assertBefore(String source, String earlier, String later, String message) {
		int earlierIndex = source.indexOf(earlier);
		int laterIndex = source.indexOf(later);
		assertTrue(earlierIndex >= 0, "Expected source to contain: " + earlier);
		assertTrue(laterIndex >= 0, "Expected source to contain: " + later);
		assertTrue(earlierIndex < laterIndex, message);
	}
}
