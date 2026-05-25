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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * File-level consistency coverage for shipped Foci that runs without
 * bootstrapping Minecraft registries.
 */
class FocusDataConsistencyTest {
	private static final Path CONTENT_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedContent.java");
	private static final Path FOCUS_DATA_DIR =
		Path.of("src/main/resources/data/attuned/attuned/focus");
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	private static final Pattern REGISTERED_FOCUS = Pattern.compile(
		"public\\s+static\\s+final\\s+Item\\s+([A-Z0-9_]+_FOCUS)\\s*=\\s*register\\(\"([a-z0-9_]+_focus)\"\\);");
	private static final Pattern FOCI_LIST = Pattern.compile(
		"public\\s+static\\s+final\\s+List<Item>\\s+FOCI\\s*=\\s*List\\.of\\((.*?)\\);",
		Pattern.DOTALL);
	private static final Pattern FOCI_ENTRY = Pattern.compile("\\b([A-Z0-9_]+_FOCUS)\\b");
	private static final Pattern NAMESPACED_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_/.-]+");
	private static final Set<String> UNSEEN_FOCUS_ITEMS = Set.of(
		"attuned:needle_focus",
		"attuned:smoke_focus",
		"attuned:softstep_focus",
		"attuned:veil_focus");

	@Test
	void shippedFocusRegistrationsListAndDefinitionsStayInStep() throws IOException {
		String source = Files.readString(CONTENT_SOURCE, StandardCharsets.UTF_8);

		Map<String, String> registeredItemsByField = registeredFocusItemsByField(source);
		Set<String> registeredItems = new TreeSet<>(registeredItemsByField.values());
		Set<String> listedItems = focusListItems(source, registeredItemsByField);
		Set<String> definitionItems = focusDefinitionItems();

		assertEquals(registeredItems, listedItems,
			"AttunedContent.FOCI should include every registered shipped Focus item");
		assertEquals(registeredItems, definitionItems,
			"Registered shipped Focus items should match datapack FocusDefinition item ids");
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

	private static Map<String, String> registeredFocusItemsByField(String source) {
		Matcher matcher = REGISTERED_FOCUS.matcher(source);
		Map<String, String> itemsByField = new TreeMap<>();
		while (matcher.find()) {
			itemsByField.put(matcher.group(1), "attuned:" + matcher.group(2));
		}
		assertTrue(!itemsByField.isEmpty(), "Could not find registered Focus item fields in AttunedContent");
		return itemsByField;
	}

	private static Set<String> focusListItems(String source, Map<String, String> registeredItemsByField) {
		Matcher listMatcher = FOCI_LIST.matcher(source);
		assertTrue(listMatcher.find(), "Could not find AttunedContent.FOCI");

		Matcher entryMatcher = FOCI_ENTRY.matcher(listMatcher.group(1));
		List<String> unknownFields = new ArrayList<>();
		Set<String> items = new TreeSet<>();
		int entries = 0;
		while (entryMatcher.find()) {
			entries++;
			String field = entryMatcher.group(1);
			String item = registeredItemsByField.get(field);
			if (item == null) {
				unknownFields.add(field);
			} else {
				items.add(item);
			}
		}

		assertEquals(List.of(), unknownFields,
			"Every AttunedContent.FOCI entry should refer to a registered Focus item field");
		assertTrue(entries > 0, "AttunedContent.FOCI should list at least one Focus item");
		assertEquals(entries, items.size(), "AttunedContent.FOCI should not list duplicate Focus items");
		return items;
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

	private static JsonObject focusDefinitionRoot(Path file) throws IOException {
		return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static Set<String> translatedFactionIds() throws IOException {
		JsonObject lang = JsonParser.parseString(Files.readString(LANG_FILE, StandardCharsets.UTF_8)).getAsJsonObject();
		Set<String> factions = new TreeSet<>();
		for (String key : lang.keySet()) {
			if (key.startsWith("faction.")) {
				String id = key.substring("faction.".length()).replaceFirst("\\.", ":");
				factions.add(id);
			}
		}
		return factions;
	}
}
