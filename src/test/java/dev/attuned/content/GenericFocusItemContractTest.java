package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Contract coverage for the generic, resource-pack-skinnable Focus item pool
 * ({@code attuned:custom_focus_1..N}). These items intentionally ship NO bundled
 * {@code FocusDefinition} — a datapack author supplies that — so they need an
 * explicit creative-tab accept and are deliberately exempt from the shipped-Focus
 * consistency sweep. Source-grep / file-scan style, so it never bootstraps
 * Minecraft registries.
 */
class GenericFocusItemContractTest {
	private static final int POOL_SIZE = 8;

	private static final Path CONTENT_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedContent.java");
	private static final Path CREATIVE_TABS_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedCreativeTabs.java");
	private static final Path ITEM_DEFINITION_DIR =
		Path.of("src/main/resources/assets/attuned/items");
	private static final Path ITEM_MODEL_DIR =
		Path.of("src/main/resources/assets/attuned/models/item");
	private static final Path ITEM_TEXTURE_DIR =
		Path.of("src/main/resources/assets/attuned/textures/item");
	private static final Path FOCUS_DATA_DIR =
		Path.of("src/main/resources/data/attuned/attuned/focus");
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void poolRegistersGenericFociViaRegisterFocus() throws IOException {
		String source = Files.readString(CONTENT_SOURCE, StandardCharsets.UTF_8);
		// The pool registers in a loop over the registerFocus idiom so each id lands in
		// REGISTERED_FOCI (and therefore isFocus()), without N unrolled fields.
		assertTrue(source.contains("registerFocus(\"custom_focus_\" + n)"),
			"The generic Focus pool must register each custom_focus_N via the registerFocus idiom");
		assertTrue(source.contains("for (int n = 1; n <= " + POOL_SIZE + "; n++)"),
			"The generic Focus pool must register exactly " + POOL_SIZE + " custom_focus items");
	}

	@Test
	void poolIsSnapshotBeforeTheFociListFreezes() throws IOException {
		String source = Files.readString(CONTENT_SOURCE, StandardCharsets.UTF_8);
		int poolIndex = source.indexOf("registerCustomFocusPool()");
		int fociFreezeIndex = source.indexOf("public static final List<DeferredItem<Item>> FOCI = List.copyOf(REGISTERED_FOCI);");
		assertTrue(poolIndex >= 0, "The generic Focus pool must be registered via registerCustomFocusPool()");
		assertTrue(fociFreezeIndex >= 0, "AttunedContent must keep the FOCI snapshot");
		assertTrue(poolIndex < fociFreezeIndex,
			"The generic Focus pool must register before the FOCI/FOCUS_IDS snapshots freeze so isFocus() includes them");
	}

	@Test
	void genericFociAreExposedInACreativeTab() throws IOException {
		String source = Files.readString(CREATIVE_TABS_SOURCE, StandardCharsets.UTF_8);
		assertTrue(source.contains("AttunedContent.CUSTOM_FOCI"),
			"The generic Focus pool must be accepted into a creative tab; they have no FocusDefinition "
				+ "so fociInDisplayOrder will not surface them");
	}

	@Test
	void genericFociHaveItemDefinitionModelAndTextureAssets() {
		for (int n = 1; n <= POOL_SIZE; n++) {
			String name = "custom_focus_" + n;
			assertTrue(Files.isRegularFile(ITEM_DEFINITION_DIR.resolve(name + ".json")),
				"Generic Focus should have an item definition asset: " + name);
			assertTrue(Files.isRegularFile(ITEM_MODEL_DIR.resolve(name + ".json")),
				"Generic Focus should have an item model asset: " + name);
			assertTrue(Files.isRegularFile(ITEM_TEXTURE_DIR.resolve(name + ".png")),
				"Generic Focus should have an item texture asset: " + name);
		}
	}

	@Test
	void genericFocusModelPointsAtItsOwnTexture() throws IOException {
		for (int n = 1; n <= POOL_SIZE; n++) {
			String name = "custom_focus_" + n;
			JsonObject model = JsonParser.parseString(
					Files.readString(ITEM_MODEL_DIR.resolve(name + ".json"), StandardCharsets.UTF_8))
				.getAsJsonObject();
			assertEquals("minecraft:item/generated", model.get("parent").getAsString(),
				"Generic Focus model should use the generated item parent: " + name);
			assertEquals("attuned:item/" + name,
				model.getAsJsonObject("textures").get("layer0").getAsString(),
				"Generic Focus model must reference its own per-item texture so a resource pack "
					+ "can override it independently: " + name);
		}
	}

	@Test
	void genericFocusItemDefinitionPointsAtItsOwnModel() throws IOException {
		for (int n = 1; n <= POOL_SIZE; n++) {
			String name = "custom_focus_" + n;
			JsonObject definition = JsonParser.parseString(
					Files.readString(ITEM_DEFINITION_DIR.resolve(name + ".json"), StandardCharsets.UTF_8))
				.getAsJsonObject();
			JsonObject modelEntry = definition.getAsJsonObject("model");
			assertEquals("minecraft:model", modelEntry.get("type").getAsString(),
				"Generic Focus item definition must use the model type: " + name);
			assertEquals("attuned:item/" + name, modelEntry.get("model").getAsString(),
				"Generic Focus item definition must point at its own model: " + name);
		}
	}

	@Test
	void genericFociShipDefaultLangSoTheyAreNotRawKeys() throws IOException {
		JsonObject lang = JsonParser.parseString(Files.readString(LANG_FILE, StandardCharsets.UTF_8))
			.getAsJsonObject();
		for (int n = 1; n <= POOL_SIZE; n++) {
			String key = "item.attuned.custom_focus_" + n;
			assertTrue(lang.has(key),
				"Generic Focus must ship a default name so it is not a raw translation key: " + key);
			assertTrue(lang.has(key + ".lore"),
				"Generic Focus must ship default lore: " + key + ".lore");
			assertTrue(lang.has(key + ".lore2"),
				"Generic Focus must ship default lore2: " + key + ".lore2");
			assertTrue(lang.has(key + ".effect"),
				"Generic Focus must ship a default effect line: " + key + ".effect");
		}
	}

	@Test
	void genericFociShipNoBundledFocusDefinition() {
		for (int n = 1; n <= POOL_SIZE; n++) {
			Path definition = FOCUS_DATA_DIR.resolve("custom_focus_" + n + ".json");
			assertFalse(Files.exists(definition),
				"Generic Focus items must NOT ship a bundled FocusDefinition; authors supply it, and a "
					+ "shipped one would inflate the README Focus-count gate: " + definition);
		}
	}

	@Test
	void genericFociAreExemptFromTheShippedFocusConsistencySweep() throws IOException {
		// The pool's exemption is structural: FocusDataConsistencyTest's REGISTERED_FOCUS regex
		// requires a `*_FOCUS` field assigned from `registerFocus("*_focus")`. The pool ids end
		// in a digit (custom_focus_N) and register via a loop, so they never match that sweep and
		// are not forced to ship a FocusDefinition or animated 64x512 texture. Pin both halves so a
		// future rename cannot silently drag the pool back into the shipped-Focus gate.
		Pattern shippedFocus = Pattern.compile(
			"public\\s+static\\s+final\\s+DeferredItem<Item>\\s+([A-Z0-9_]+_FOCUS)\\s*=\\s*registerFocus\\(\"([a-z0-9_]+_focus)\"\\);");
		String source = Files.readString(CONTENT_SOURCE, StandardCharsets.UTF_8);
		Matcher matcher = shippedFocus.matcher(source);
		while (matcher.find()) {
			assertFalse(matcher.group(2).startsWith("custom_focus_"),
				"Generic Focus ids must not match the shipped-Focus consistency sweep regex: " + matcher.group(2));
		}
		for (int n = 1; n <= POOL_SIZE; n++) {
			assertFalse(("custom_focus_" + n).matches("[a-z0-9_]+_focus"),
				"custom_focus_" + n + " must not satisfy the *_focus suffix the shipped-Focus sweep keys on");
		}
	}
}
