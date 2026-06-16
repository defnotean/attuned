package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		assertEquals("Press the Focus Ability key to pulse blackout smoke. Nearby mobs are briefly blinded and drop target.",
			lang.get("item.attuned.blackout_focus.effect").getAsString());
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
			String tooltipAttribute = tooltipAttributePath(attribute);
			assertTrue(lang.has("tooltip.attuned.modifier.attribute." + tooltipAttribute),
				"Modifier attribute should have a tooltip label: " + tooltipAttribute);
		}
	}

	@Test
	void focusTooltipsShowAffinityIdentityButLeaveMatchupsToJournal() throws IOException {
		String source = Files.readString(TOOLTIP_SOURCE, StandardCharsets.UTF_8);
		JsonObject lang = languageRoot();

		assertTrue(source.contains("Component.literal(\"Affinity \")"),
			"Tooltip should render the Focus's affinity identity line.");
		assertTrue(source.contains("affinityName(affinity)"),
			"Tooltip should display the affinity's own name.");
		assertFalse(source.contains("aspect"),
			"The removed Aspect layer should leave no trace in the tooltip code.");
		assertFalse(source.contains("affinity.strongAgainst()"),
			"Focus item tooltips should not derive or display strength matchup names.");
		assertFalse(source.contains("affinity.weakAgainst()"),
			"Focus item tooltips should not derive or display weakness matchup names.");
		assertFalse(lang.has("tooltip.attuned.aspect.matchups"),
			"Unused matchup tooltip text should be removed so item descriptions stay clean.");
		assertFalse(lang.has("tooltip.attuned.aspect.line"),
			"The removed Aspect identity line should leave no orphaned lang key.");
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

	private static String tooltipAttributePath(String path) {
		int dot = path.indexOf('.');
		return dot >= 0 ? path.substring(dot + 1) : path;
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
