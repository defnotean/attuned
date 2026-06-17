package dev.attuned.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for custom Focus visual effects. */
class FocusVisualEffectsContractTest {
	private static final Path ATTUNED_EFFECTS =
		Path.of("src/main/java/dev/attuned/effect/AttunedEffects.java");
	private static final Path FOCUS_VISUAL_EFFECTS =
		Path.of("src/main/java/dev/attuned/effect/FocusVisualEffects.java");
	private static final Path MANIFEST =
		Path.of("docs/superpowers/assets/focus-custom-effects/image-gen-manifest.json");
	private static final Path CONCEPT_SHEET =
		Path.of("docs/superpowers/assets/focus-custom-effects/image-gen-sources/focus-custom-effects-concept-sheet.jpg");

	@Test
	void attunedEffectsHooksCustomVisualsIntoExistingAuraTick() throws IOException {
		String source = read(ATTUNED_EFFECTS);

		assertTrue(source.contains("spawnAura(player, activeAffinities)"),
			"The existing affinity aura should remain intact.");
		assertTrue(source.contains("FocusVisualEffects.spawn(player, inv, currentActive, auraTick);"),
			"Custom Focus visuals should reuse the already-resolved active slots on the aura cadence.");
		assertEquals(1, countOccurrences(source, "Attunement.definitionFor(player, inv.get(slot))"),
			"Custom Focus visuals must not add another Focus definition lookup pass.");
	}

	@Test
	void customVisualsCoverTheGeneratedFirstConceptBatch() throws IOException {
		String source = read(FOCUS_VISUAL_EFFECTS);

		assertTrue(source.contains("public static void spawn(ServerPlayer player, AttunedInv inventory, Iterable<Integer> activeSlots, int tick)"),
			"The visual system should be callable from AttunedEffects with the active slot snapshot.");
		assertTrue(source.contains("BuiltInRegistries.ITEM.getKey(stack.getItem())"),
			"Visuals should key off real active Focus item ids, not behavior ids or tooltip text.");
		assertTrue(source.contains("SOFTSTEP_FOCUS") && source.contains("\"softstep_focus\""),
			"The Softstep/Umbral concept should have a concrete Focus id.");
		assertTrue(source.contains("AEGIS_FOCUS") && source.contains("\"aegis_focus\""),
			"The Aegis/Bastion concept should have a concrete Focus id.");
		assertTrue(source.contains("TIDE_FOCUS") && source.contains("\"tide_focus\""),
			"The Tide concept should have a concrete Focus id.");
		assertTrue(source.contains("CINDER_FOCUS") && source.contains("\"cinder_focus\""),
			"The Ember/Cinder concept should have a concrete Focus id.");

		assertTrue(source.contains("spawnSoftstep"), "Softstep should get a shadow-footstep effect routine.");
		assertTrue(source.contains("spawnAegis"), "Aegis should get a shield-ring effect routine.");
		assertTrue(source.contains("spawnTide"), "Tide should get a bubble/water effect routine.");
		assertTrue(source.contains("spawnCinder"), "Cinder should get an ember effect routine.");
	}

	@Test
	void visualEffectsTranslateTheConceptSheetIntoMinecraftParticles() throws IOException {
		String source = read(FOCUS_VISUAL_EFFECTS);

		assertTrue(source.contains("ParticleTypes.SCULK_SOUL"),
			"Softstep should use low smoky/sculk soul particles from the concept sheet.");
		assertTrue(source.contains("new DustParticleOptions(SOFTSTEP_PURPLE")
				|| source.contains("DustParticles.color(SOFTSTEP_PURPLE"),
			"Softstep should include a purple dust wisp color.");
		assertTrue(source.contains("new DustParticleOptions(AEGIS_GOLD")
				|| source.contains("DustParticles.color(AEGIS_GOLD"),
			"Aegis should use a gold dust shield ring.");
		assertTrue(source.contains("ParticleTypes.BUBBLE"),
			"Tide should use bubble particles.");
		assertTrue(source.contains("ParticleTypes.SPLASH"),
			"Tide should use splash/water motes.");
		assertTrue(source.contains("ParticleTypes.FLAME"),
			"Cinder should use flame/ember particles.");
		assertTrue(source.contains("ParticleTypes.LAVA"),
			"Cinder should use lava spark particles.");
	}

	@Test
	void imageGenerationProvenanceIsSavedWithTheFeature() throws IOException {
		String manifest = read(MANIFEST);
		assertTrue(Files.isRegularFile(CONCEPT_SHEET),
			"The concept sheet should be saved beside the feature notes.");
		assertTrue(manifest.contains("grok-imagine-image"),
			"The manifest should record the internal concept source model.");
		assertTrue(manifest.contains("softstep_focus") && manifest.contains("aegis_focus")
				&& manifest.contains("tide_focus") && manifest.contains("cinder_focus"),
			"The manifest should map the generated motifs to the first implemented Focus batch.");
		assertTrue(manifest.contains("The generated sheet included visible labels"),
			"The manifest should preserve the concept QA note about labels not being imported into game assets.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static int countOccurrences(String haystack, String needle) {
		int count = 0;
		int index = 0;
		while ((index = haystack.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
	}
}
