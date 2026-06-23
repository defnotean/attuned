package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.attuned.api.focus.Affinity;
import dev.attuned.attunement.BudgetResolver;
import dev.attuned.attunement.FocusPreset;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class PresetMetadataResolverTest {
	@Test
	void factionDominanceRefinesTheInferredRole() {
		FocusPreset.SetupMetadata metadata = PresetMetadataResolver.infer(List.of(
			focus(Affinity.UMBRAL, "attuned:unseen", false),
			focus(Affinity.UMBRAL, "attuned:unseen", false),
			focus(Affinity.UMBRAL, "attuned:unseen", true)));

		assertEquals("Shade", metadata.role(),
			"Three active Unseen foci should refine the Umbral role into Shade.");
		assertEquals(List.of(), metadata.warnings(),
			"A committed role with an active ability should not produce setup warnings.");
	}

	@Test
	void discordAndNeutralBuildsInferNomad() {
		assertEquals("Nomad", PresetMetadataResolver.infer(List.of(
			focus(Affinity.FURY, "", false),
			focus(Affinity.BASTION, "", false),
			focus(Affinity.ZEPHYR, "", false),
			focus(Affinity.TIDE, "", true))).role(),
			"Four active affinity lanes should be labelled as a flexible Nomad setup.");

		assertEquals("Nomad", PresetMetadataResolver.infer(List.of(
			neutral(false),
			neutral(false),
			neutral(false),
			neutral(true))).role(),
			"Four active neutral foci should be labelled as a flexible Nomad setup.");
	}

	@Test
	void warningsCaptureDormancyMissingCommitmentAndMissingAbility() {
		FocusPreset.SetupMetadata metadata = PresetMetadataResolver.infer(List.of(
			focus(Affinity.FURY, "", false),
			focus(Affinity.BASTION, "", false),
			dormant(Affinity.FURY, BudgetResolver.DormantReason.NOT_ENOUGH_CAPACITY),
			dormant(Affinity.BASTION, BudgetResolver.DormantReason.DUPLICATE_UNIQUE)));

		assertEquals(List.of(
			"No committed affinity lane.",
			"Over capacity: dormant Foci will sleep at current cap.",
			"Duplicate unique Focus is dormant.",
			"No active Focus Ability."), metadata.warnings());
	}

	@Test
	void disabledSetupSuggestionsKeepRoleButSuppressInferredWarnings() {
		FocusPreset.SetupMetadata metadata = PresetMetadataResolver.infer(List.of(
			focus(Affinity.FURY, "", false),
			focus(Affinity.FURY, "", false),
			focus(Affinity.FURY, "", false),
			dormant(Affinity.FURY, BudgetResolver.DormantReason.NOT_ENOUGH_CAPACITY)), false);

		assertEquals("Vanguard", metadata.role(),
			"Disabling setup suggestions should keep non-gameplay role metadata.");
		assertEquals(List.of(), metadata.warnings(),
			"Disabling setup suggestions should suppress advisory warnings for freshly saved builds.");
	}

	@Test
	void roleOnlyInferenceIgnoresDormantFoci() {
		assertEquals("Current", PresetMetadataResolver.inferRole(List.of(
			focus(Affinity.TIDE, "", false),
			focus(Affinity.TIDE, "", false),
			focus(Affinity.TIDE, "", true),
			dormant(Affinity.FURY, BudgetResolver.DormantReason.NOT_ENOUGH_CAPACITY))),
			"Public role labels should use active resolved Foci without carrying saved-build warning metadata.");
	}

	@Test
	void disabledSetupSuggestionsStripImportedWarningsOnly() {
		FocusPreset preset = new FocusPreset("Support", List.of("attuned:votive_focus"),
			new FocusPreset.SetupMetadata(
				"Witness", "Carry reveal", 4,
				List.of("No Anchor"), List.of("attuned:votive_focus"),
				"1.21.11", "1.5.3"));

		FocusPreset stripped = PresetMetadataResolver.withWarningsEnabled(preset, false);

		assertEquals("Witness", stripped.role());
		assertEquals("Carry reveal", stripped.note());
		assertEquals(4, stripped.preferredPartySize());
		assertEquals(List.of(), stripped.warnings(),
			"Disabling setup suggestions should remove imported advisory warnings.");
		assertEquals(List.of("attuned:votive_focus"), stripped.requires(),
			"Requires metadata is import validation context, not a party suggestion.");
		assertEquals("1.21.11", stripped.minecraftVersion());
		assertEquals("1.5.3", stripped.attunedVersion());
	}

	private static PresetMetadataResolver.ResolvedFocus focus(Affinity affinity, String faction, boolean activeAbility) {
		return new PresetMetadataResolver.ResolvedFocus(
			Optional.of(affinity), ResourceLocation(faction), activeAbility, Optional.empty());
	}

	private static PresetMetadataResolver.ResolvedFocus neutral(boolean activeAbility) {
		return new PresetMetadataResolver.ResolvedFocus(
			Optional.empty(), Optional.empty(), activeAbility, Optional.empty());
	}

	private static PresetMetadataResolver.ResolvedFocus dormant(Affinity affinity,
			BudgetResolver.DormantReason reason) {
		return new PresetMetadataResolver.ResolvedFocus(
			Optional.of(affinity), Optional.empty(), false, Optional.of(reason));
	}

	private static Optional<ResourceLocation> ResourceLocation(String id) {
		return id.isBlank() ? Optional.empty() : Optional.of(new ResourceLocation(id));
	}
}
