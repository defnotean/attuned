package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.attuned.attunement.FocusPreset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PresetImportValidatorTest {
	@Test
	void importedBuildReportsUnknownFocusIdsBeforeItIsSaved() {
		String missingTempered = FocusPreset.slotKey("attuned:deleted_focus", true);

		PresetImportValidator.Result result = PresetImportValidator.validate(
			List.of("", "attuned:edge_focus", missingTempered, "attuned:missing_focus", "attuned:missing_focus"),
			Set.of("attuned:edge_focus"));

		assertEquals(List.of(missingTempered, "attuned:missing_focus"), result.missing(),
			"Unknown imported ids should be reported once, preserving the saved slot key the player needs to fix.");
	}

	@Test
	void importedBuildWithOnlyRegisteredOrBlankSlotsIsAccepted() {
		PresetImportValidator.Result result = PresetImportValidator.validate(
			List.of("", "attuned:edge_focus", FocusPreset.slotKey("attuned:edge_focus", true)),
			Set.of("attuned:edge_focus"));

		assertTrue(result.missing().isEmpty());
	}

	@Test
	void importedBuildReportsUnknownRequiresMetadataBeforeItIsSaved() {
		FocusPreset preset = new FocusPreset("Road",
			List.of("attuned:edge_focus"),
			new FocusPreset.SetupMetadata(
				"Support",
				"Carry reveal",
				4,
				List.of(),
				List.of("attuned:edge_focus", "attuned:missing_support", "attuned:missing_support"),
				"1.21.11",
				"1.5.4"));

		PresetImportValidator.Result result = PresetImportValidator.validate(preset, Set.of("attuned:edge_focus"));

		assertEquals(List.of("attuned:missing_support"), result.missing(),
			"Build-code requires metadata should produce one missing-content warning without adding item grants.");
	}
}
