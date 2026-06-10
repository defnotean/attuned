package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Data guardrails for Focus effects where duplicate active copies cannot stack meaningfully. */
class FocusUniquenessContractTest {
	private static final Path FOCUS_DATA_DIR =
		Path.of("src/main/resources/data/attuned/attuned/focus");

	private static final Set<String> NON_STACKING_BEHAVIOR_FOCI = Set.of(
		"aegis_focus",
		"anchor_focus",
		"beacon_focus",
		"blackout_focus",
		"bloodfury_focus",
		"bloom_focus",
		"cinder_focus",
		"delver_focus",
		"emberward_focus",
		"epitaph_focus",
		"forager_focus",
		"gravebind_focus",
		"harvest_focus",
		"hearth_focus",
		"kilnward_focus",
		"lantern_focus",
		"leech_focus",
		"lodestone_focus",
		"mask_focus",
		"mossheart_focus",
		"nightgaze_focus",
		"rainstep_focus",
		"rivet_focus",
		"rootstep_focus",
		"softstep_focus",
		"stormcall_focus",
		"temper_focus",
		"thornward_focus",
		"tide_focus",
		"tremor_focus",
		"voidstep_focus",
		"waystone_focus",
		"whisper_focus");

	@Test
	void nonStackingRuntimeFociAreUniqueSoDuplicateCopiesDoNotWasteBudget() throws IOException {
		Set<String> notUnique = new TreeSet<>();
		for (String focus : NON_STACKING_BEHAVIOR_FOCI) {
			JsonObject root = focusDefinitionRoot(FOCUS_DATA_DIR.resolve(focus + ".json"));
			if (!root.has("unique") || !root.get("unique").getAsBoolean()) {
				notUnique.add(focus);
			}
		}

		assertTrue(notUnique.isEmpty(),
			"Non-stacking runtime Foci should mark duplicate active copies dormant: " + notUnique);
	}

	private static JsonObject focusDefinitionRoot(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected FocusDefinition JSON: " + file);
		return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
	}
}
