package dev.attuned.synergy;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Content sweep for the shipped Confluences, modelled on {@code FocusDataConsistencyTest}.
 * Every {@code synergy/<name>.json} must reference real, registered member Foci; have its
 * {@code confluence.attuned.<name>.name}/{@code .desc} lang keys; have a
 * {@code confluence_<name>.json} advancement; register any declared behavior; and appear in
 * {@code docs/reference.md}.
 */
class SynergyContentContractTest {
	private static final Path SYNERGY_DIR = Path.of("src/main/resources/data/attuned/attuned/synergy");
	private static final Path FOCUS_DIR = Path.of("src/main/resources/data/attuned/attuned/focus");
	private static final Path LANG = Path.of("src/main/resources/assets/attuned/lang/en_us.json");
	private static final Path BEHAVIORS = Path.of("src/main/java/dev/attuned/content/AttunedFocusBehaviors.java");
	private static final Path REFERENCE = Path.of("docs/reference.md");
	private static final Path ADVANCEMENT_DIR = Path.of("src/main/resources/data/attuned/advancement/attunement");
	private static final Pattern NAMESPACED = Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

	@Test
	void everyConfluenceReferencesRealFociAndHasLangAdvancementAndDocs() throws IOException {
		assertTrue(Files.isDirectory(SYNERGY_DIR), "Synergy data dir must exist: " + SYNERGY_DIR);
		List<Path> files;
		try (Stream<Path> stream = Files.list(SYNERGY_DIR)) {
			files = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
		}
		assertFalse(files.isEmpty(), "At least one Confluence must ship.");

		Set<String> focusItemIds = focusItemIds();
		String lang = read(LANG);
		String behaviors = read(BEHAVIORS);
		String reference = read(REFERENCE);
		assertTrue(reference.contains("Confluences"), "reference.md must document Confluences.");

		List<String> problems = new ArrayList<>();
		for (Path file : files) {
			String name = file.getFileName().toString().replace(".json", "");
			JsonObject json = JsonParser.parseString(read(file)).getAsJsonObject();

			if (!json.has("members") || !json.get("members").isJsonArray()) {
				problems.add(name + ": missing members array");
				continue;
			}
			JsonArray members = json.getAsJsonArray("members");
			if (members.size() < 2 || members.size() > 3) {
				problems.add(name + ": members must number 2-3 (got " + members.size() + ")");
			}
			for (JsonElement member : members) {
				String id = member.getAsString();
				if (!NAMESPACED.matcher(id).matches()) {
					problems.add(name + ": member is not a namespaced id: " + id);
				} else if (!focusItemIds.contains(id)) {
					problems.add(name + ": member references a Focus that does not exist: " + id);
				}
			}

			if (!lang.contains("\"confluence.attuned." + name + ".name\"")) {
				problems.add(name + ": missing lang .name key");
			}
			if (!lang.contains("\"confluence.attuned." + name + ".desc\"")) {
				problems.add(name + ": missing lang .desc key");
			}

			Path advancement = ADVANCEMENT_DIR.resolve("confluence_" + name + ".json");
			if (!Files.isRegularFile(advancement)) {
				problems.add(name + ": missing advancement " + advancement);
			}

			if (json.has("behavior")) {
				String behaviorId = json.get("behavior").getAsString();
				String path = behaviorId.contains(":")
					? behaviorId.substring(behaviorId.indexOf(':') + 1) : behaviorId;
				if (!behaviors.contains("register(\"" + path + "\"")) {
					problems.add(name + ": behavior " + behaviorId + " not registered in AttunedFocusBehaviors");
				}
			}

			if (!reference.contains(name)) {
				problems.add(name + ": not documented in reference.md");
			}
		}
		assertTrue(problems.isEmpty(), "Confluence content problems: " + problems);
	}

	private static Set<String> focusItemIds() throws IOException {
		Set<String> ids = new HashSet<>();
		try (Stream<Path> stream = Files.list(FOCUS_DIR)) {
			for (Path file : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
				JsonObject json = JsonParser.parseString(read(file)).getAsJsonObject();
				if (json.has("item")) {
					ids.add(json.get("item").getAsString());
				}
			}
		}
		return ids;
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
