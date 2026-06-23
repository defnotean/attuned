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

		assertTrue(containsSoftstepSmokeParticle(source),
			"Softstep should use low smoky/sculk soul particles from the concept sheet.");
		assertTrue(containsDustColor(source, "SOFTSTEP_PURPLE"),
			"Softstep should include a purple dust wisp color.");
		assertTrue(containsDustColor(source, "AEGIS_GOLD"),
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

	private static boolean containsDustColor(String source, String colorConstant) {
		return source.contains("new DustParticleOptions(" + colorConstant)
			|| source.contains("ParticleCompat.dust(" + colorConstant)
			|| source.contains("DustParticles.color(" + colorConstant);
	}

	private static boolean containsSoftstepSmokeParticle(String source) {
		return source.contains("ParticleTypes.SCULK_SOUL")
			|| source.contains("ParticleTypes.SOUL");
	}
}
