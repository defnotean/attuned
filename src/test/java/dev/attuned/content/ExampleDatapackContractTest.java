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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the shipped worked example datapack ({@code docs/example-pack/}) so the
 * authoring docs cannot rot: every Focus it ships references a real registered
 * item, every behavior id it uses is a shipped behavior, and every Focus carries
 * the name/lore/effect lang keys that keep it from being a raw-key footgun.
 *
 * <p>File-scan, Minecraft-free — it never bootstraps registries.
 */
class ExampleDatapackContractTest {
	private static final Path EXAMPLE_FOCUS_DIR =
		Path.of("docs/example-pack/data/example/attuned/focus");
	private static final Path EXAMPLE_BEHAVIOR_DIR =
		Path.of("docs/example-pack/data/example/attuned/focus_behavior");
	private static final Path EXAMPLE_LANG =
		Path.of("docs/example-pack/assets/example/lang/en_us.json");
	private static final Path EXAMPLE_README =
		Path.of("docs/example-pack/README.md");
	private static final Path CONTENT_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedContent.java");
	private static final Path SHIPPED_BEHAVIORS =
		Path.of("src/main/java/dev/attuned/content/AttunedFocusBehaviors.java");

	private static final Pattern REGISTERED_FOCUS = Pattern.compile(
		"registerFocus\\(\"([a-z0-9_]+)\"\\)");
	private static final Pattern REGISTERED_BEHAVIOR = Pattern.compile(
		"register\\(\\s*\"([a-z0-9_/.-]+)\"\\s*,\\s*new\\s+", Pattern.DOTALL);
	private static final Set<String> PALETTE_TYPES = Set.of(
		"attuned:conditional_mob_effect",
		"attuned:on_hit_effect",
		"attuned:periodic_effect",
		"attuned:attribute_while",
		"attuned:block_context_effect",
		"attuned:use_item_window",
		"attuned:party_assist",
		"attuned:marked_target",
		"attuned:navigation_hint");
	private static final Set<String> OPERATIONS = Set.of(
		"add_value", "add_multiplied_base", "add_multiplied_total");

	@Test
	void examplePackExistsAndShipsEightFoci() throws IOException {
		assertTrue(Files.isDirectory(EXAMPLE_FOCUS_DIR),
			"The worked example pack must ship its focus directory: " + EXAMPLE_FOCUS_DIR);
		assertTrue(Files.isRegularFile(EXAMPLE_README),
			"The worked example pack must ship a README telling authors how to install it.");
		int n = focusFiles().size();
		assertEquals(8, n,
			"The worked example pack ships eight foci so authors see attribute, shipped behavior, condition, block-context, item-use, party-assist, mark/consume, and navigation lanes.");
	}

	@Test
	void everyExampleFocusReferencesARealItem() throws IOException {
		Set<String> shippedItems = shippedFocusItemIds();
		for (Path file : focusFiles()) {
			String item = root(file).get("item").getAsString();
			assertTrue(shippedItems.contains(item) || item.startsWith("minecraft:"),
				"Every example Focus must reference a real registered item so the pack loads without a JAR change: "
					+ item + " in " + file);
		}
	}

	@Test
	void examplePackShowsBothTheAttributeLaneAndTheBehaviorLane() throws IOException {
		boolean sawAttributeOnly = false;
		boolean sawBehavior = false;
		Set<String> shippedBehaviors = shippedBehaviorIds();
		Set<String> paletteBehaviors = paletteBehaviorIds();

		for (Path file : focusFiles()) {
			JsonObject root = root(file);
			JsonElement modifiers = root.get("modifiers");
			if (modifiers != null && modifiers.isJsonArray() && !modifiers.getAsJsonArray().isEmpty()) {
				JsonObject modifier = modifiers.getAsJsonArray().get(0).getAsJsonObject();
				assertTrue(modifier.get("attribute").getAsString().startsWith("minecraft:"),
					"Example attribute modifiers must use a vanilla attribute id: " + file);
				assertTrue(OPERATIONS.contains(modifier.get("operation").getAsString()),
					"Example attribute modifiers must use a valid operation: " + file);
				if (!root.has("behavior")) {
					sawAttributeOnly = true;
				}
			}
			JsonElement behavior = root.get("behavior");
			if (behavior != null) {
				String behaviorId = behavior.getAsString();
				assertTrue(shippedBehaviors.contains(behaviorId) || paletteBehaviors.contains(behaviorId),
					"Every behavior the example pack references must be a shipped or in-pack palette behavior: "
						+ behaviorId + " in " + file);
				sawBehavior = true;
			}
		}

		assertTrue(sawAttributeOnly,
			"The example pack must show the attribute-only lane (a Focus with modifiers and no behavior).");
		assertTrue(sawBehavior,
			"The example pack must show the behavior lane (a Focus referencing a behavior id).");
	}

	@Test
	void everyInPackPaletteBehaviorIsAKnownPaletteType() throws IOException {
		if (!Files.isDirectory(EXAMPLE_BEHAVIOR_DIR)) {
			return;
		}
		for (Path file : behaviorFiles()) {
			String type = root(file).get("type").getAsString();
			assertTrue(PALETTE_TYPES.contains(type),
				"Every example palette behavior must name a known palette type: " + type + " in " + file);
		}
	}

	@Test
	void examplePackValidatorRecognizesEveryShippedPaletteType() {
		assertTrue(PALETTE_TYPES.contains("attuned:conditional_mob_effect"),
			"The example-pack validator should recognize conditional_mob_effect palette files.");
		assertTrue(PALETTE_TYPES.contains("attuned:on_hit_effect"),
			"The example-pack validator should recognize on_hit_effect palette files.");
		assertTrue(PALETTE_TYPES.contains("attuned:periodic_effect"),
			"The example-pack validator should recognize periodic_effect palette files.");
		assertTrue(PALETTE_TYPES.contains("attuned:attribute_while"),
			"The example-pack validator should recognize attribute_while palette files.");
		assertTrue(PALETTE_TYPES.contains("attuned:block_context_effect"),
			"The example-pack validator should recognize block_context_effect palette files.");
		assertTrue(PALETTE_TYPES.contains("attuned:use_item_window"),
			"The example-pack validator should recognize use_item_window palette files.");
		assertTrue(PALETTE_TYPES.contains("attuned:party_assist"),
			"The example-pack validator should recognize party_assist palette files.");
		assertTrue(PALETTE_TYPES.contains("attuned:marked_target"),
			"The example-pack validator should recognize marked_target palette files.");
		assertTrue(PALETTE_TYPES.contains("attuned:navigation_hint"),
			"The example-pack validator should recognize navigation_hint palette files.");
	}

	@Test
	void everyExampleFocusShipsNameLoreLore2AndEffect() throws IOException {
		JsonObject lang = JsonParser.parseString(Files.readString(EXAMPLE_LANG, StandardCharsets.UTF_8))
			.getAsJsonObject();
		for (Path file : focusFiles()) {
			String base = file.getFileName().toString().replaceFirst("\\.json$", "");
			String key = "item.example." + base;
			assertTrue(lang.has(key),
				"The example pack must ship a display name so it has no raw-key footgun: " + key);
			assertTrue(lang.has(key + ".lore"),
				"The example pack must ship a lore line: " + key + ".lore");
			assertTrue(lang.has(key + ".lore2"),
				"The example pack must ship a second lore line: " + key + ".lore2");
			assertTrue(lang.has(key + ".effect"),
				"The example pack must ship an effect line: " + key + ".effect");
		}
	}

	private static List<Path> focusFiles() throws IOException {
		assertTrue(Files.isDirectory(EXAMPLE_FOCUS_DIR),
			"Could not find example focus directory: " + EXAMPLE_FOCUS_DIR);
		try (Stream<Path> paths = Files.list(EXAMPLE_FOCUS_DIR)) {
			return paths.filter(p -> p.toString().endsWith(".json")).sorted().toList();
		}
	}

	private static List<Path> behaviorFiles() throws IOException {
		try (Stream<Path> paths = Files.list(EXAMPLE_BEHAVIOR_DIR)) {
			return paths.filter(p -> p.toString().endsWith(".json")).sorted().toList();
		}
	}

	/** Palette behavior ids the pack itself defines, named {@code example:<file_basename>}. */
	private static Set<String> paletteBehaviorIds() throws IOException {
		if (!Files.isDirectory(EXAMPLE_BEHAVIOR_DIR)) {
			return Set.of();
		}
		Set<String> ids = new TreeSet<>();
		for (Path file : behaviorFiles()) {
			String base = file.getFileName().toString().replaceFirst("\\.json$", "");
			ids.add("example:" + base);
		}
		return ids;
	}

	private static Set<String> shippedFocusItemIds() throws IOException {
		String source = Files.readString(CONTENT_SOURCE, StandardCharsets.UTF_8);
		Matcher matcher = REGISTERED_FOCUS.matcher(source);
		Set<String> ids = new TreeSet<>();
		while (matcher.find()) {
			ids.add("attuned:" + matcher.group(1));
		}
		assertTrue(!ids.isEmpty(), "Could not find any registered Focus items in AttunedContent");
		return ids;
	}

	private static Set<String> shippedBehaviorIds() throws IOException {
		String source = Files.readString(SHIPPED_BEHAVIORS, StandardCharsets.UTF_8);
		Matcher matcher = REGISTERED_BEHAVIOR.matcher(source);
		Set<String> ids = new TreeSet<>();
		while (matcher.find()) {
			ids.add("attuned:" + matcher.group(1));
		}
		assertTrue(!ids.isEmpty(), "Could not find any registered behaviors in AttunedFocusBehaviors");
		return ids;
	}

	private static JsonObject root(Path file) throws IOException {
		return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
	}
}
