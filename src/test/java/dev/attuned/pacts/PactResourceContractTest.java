package dev.attuned.pacts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Every shipped {@link Pact} must carry its full resource set: name + description
 * + PvP death-message lang keys and an awakening advancement. The death message is
 * built dynamically and broadcast to every player ({@code PactDeathMessages.broadcast}),
 * so a missing key leaks a raw translation key in chat; the advancement award is a
 * silent no-op when the JSON is absent. This pins both so a new Pact cannot ship with
 * either gap (as the four expansion pacts initially did).
 */
class PactResourceContractTest {
	private static final Path PACT_SOURCE =
		Path.of("src/main/java/dev/attuned/pacts/Pact.java");
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");
	private static final Path ADVANCEMENT_DIR =
		Path.of("src/main/resources/data/attuned/advancement/attunement");

	// Matches each enum constant declaration, capturing its serialized id, e.g.
	//   PYRESWORN("pyresworn", Optional.of(Affinity.FURY), ChatFormatting.RED),
	private static final Pattern PACT_DECL =
		Pattern.compile("[A-Z_]+\\(\"([a-z_]+)\",\\s*Optional\\.");

	@Test
	void everyShippedPactHasNameDescriptionDeathAndAdvancementResources() throws IOException {
		Set<String> pactIds = pactIds();
		JsonObject lang = languageRoot();

		for (String id : pactIds) {
			assertLanguageKey(lang, "pact.attuned." + id + ".name");
			assertLanguageKey(lang, "pact.attuned." + id + ".description");
			assertLanguageKey(lang, "pact.attuned.death." + id);
			Path advancement = ADVANCEMENT_DIR.resolve("pact_" + id + ".json");
			assertTrue(Files.isRegularFile(advancement),
				"Pact should have an awakening advancement so its toast fires: " + advancement);
		}
	}

	@Test
	void deathMessagesCarryBothPositionalArguments() throws IOException {
		// broadcast() passes (victim, pactName); the message must reference both as
		// %1$s / %2$s, so the args are never silently dropped.
		Set<String> pactIds = pactIds();
		JsonObject lang = languageRoot();
		for (String id : pactIds) {
			String message = lang.get("pact.attuned.death." + id).getAsString();
			assertTrue(message.contains("%1$s") && message.contains("%2$s"),
				"Pact death message should reference victim (%1$s) and pact (%2$s): " + id);
		}
	}

	private static Set<String> pactIds() throws IOException {
		String source = Files.readString(PACT_SOURCE, StandardCharsets.UTF_8);
		Matcher matcher = PACT_DECL.matcher(source);
		Set<String> ids = new LinkedHashSet<>();
		while (matcher.find()) {
			ids.add(matcher.group(1));
		}
		assertFalse(ids.isEmpty(), "Could not parse any Pact ids from Pact.java");
		assertTrue(ids.contains("nightsworn") && ids.contains("pyresworn"),
			"Pact id parsing should find both the original and expansion pacts");
		return ids;
	}

	private static JsonObject languageRoot() throws IOException {
		return JsonParser.parseString(Files.readString(LANG_FILE, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	private static void assertLanguageKey(JsonObject lang, String key) {
		assertTrue(lang.has(key), "Missing language key: " + key);
	}
}
