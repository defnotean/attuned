package dev.attuned.client;

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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FocusPresentationConsistencyTest {
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");
	private static final Path FOCUS_DATA_DIR =
		Path.of("src/main/resources/data/attuned/attuned/focus");
	private static final Path TOOLTIP_SOURCE =
		Path.of("src/client/java/dev/attuned/client/AttunedTooltips.java");

	@Test
	void focusAbilityKeyWordingUsesOnePlayerFacingPhrase() throws IOException {
		JsonObject lang = languageRoot();

		assertEquals("Use Focus Ability", lang.get("key.attuned.ability").getAsString());
		assertEquals("Press the Focus Ability key to blink 8 blocks forward.",
			lang.get("item.attuned.voidstep_focus.effect").getAsString());
		assertEquals("Press the Focus Ability key to release smoke. Mobs that have lost sight may drop target.",
			lang.get("item.attuned.smoke_focus.effect").getAsString());
		assertEquals("Press the Focus Ability key to phase 5 blocks forward through entities, not walls.",
			lang.get("item.attuned.hollowstep_focus.effect").getAsString());
		assertEquals("No active Focus Ability. Equip an awake ability Focus.",
			lang.get("item.attuned.focus_ability.none").getAsString());

		List<String> staleAbilityLines = lang.entrySet().stream()
			.filter(entry -> entry.getValue().isJsonPrimitive())
			.filter(entry -> entry.getValue().getAsString().contains("Focus-ability")
				|| entry.getValue().getAsString().contains("Foci Ability"))
			.map(MapEntry::format)
			.sorted()
			.toList();
		assertEquals(List.of(), staleAbilityLines,
			"Ability text should consistently say Focus Ability key.");
	}

	@Test
	void focusTooltipsGenerateBuffLinesFromRealModifiers() throws IOException {
		String source = Files.readString(TOOLTIP_SOURCE, StandardCharsets.UTF_8);

		assertTrue(source.contains("tooltip.attuned.modifier.header"),
			"Tooltip should label generated modifier lines as real active buffs.");
		assertTrue(source.contains("for (ModifierEntry modifier : definition.modifiers())"),
			"Tooltip should render every modifier declared by the FocusDefinition.");
		assertTrue(source.contains("modifierSummary(modifier)"),
			"Modifier display should be generated from data instead of copied by hand.");
	}

	@Test
	void focusTooltipsMatchDefensiveInventoryStackCopies() throws IOException {
		String source = Files.readString(TOOLTIP_SOURCE, StandardCharsets.UTF_8);

		assertTrue(source.contains("ItemStack.matches(inv.get(slot), stack)"),
			"Tooltip equipped detection should work when AttunedInv returns defensive stack copies.");
		assertTrue(!source.contains("inv.get(slot) == stack"),
			"Tooltip equipped detection should not rely on mutable stack identity from AttunedInv.");
	}

	@Test
	void everyShippedModifierAttributeHasTooltipName() throws IOException {
		JsonObject lang = languageRoot();
		Set<String> attributes = modifierAttributePaths();
		assertTrue(!attributes.isEmpty(), "Expected shipped Focus modifier attributes.");

		for (String attribute : attributes) {
			assertTrue(lang.has("tooltip.attuned.modifier.attribute." + attribute),
				"Modifier attribute should have a tooltip label: " + attribute);
		}
	}

	private static Set<String> modifierAttributePaths() throws IOException {
		Set<String> attributes = new TreeSet<>();
		try (Stream<Path> paths = Files.list(FOCUS_DATA_DIR)) {
			for (Path file : paths
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()
					.toList()) {
				JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
				JsonElement modifiers = root.get("modifiers");
				if (modifiers == null) {
					continue;
				}
				for (JsonElement modifier : modifiers.getAsJsonArray()) {
					String attributeId = modifier.getAsJsonObject().get("attribute").getAsString();
					attributes.add(attributeId.substring(attributeId.indexOf(':') + 1));
				}
			}
		}
		return attributes;
	}

	private static JsonObject languageRoot() throws IOException {
		return JsonParser.parseString(Files.readString(LANG_FILE, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static final class MapEntry {
		private MapEntry() {}

		private static String format(java.util.Map.Entry<String, JsonElement> entry) {
			return entry.getKey() + "=" + entry.getValue().getAsString();
		}
	}
}
