package dev.attuned.api.focus;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-grep contract for the behavior-palette record + codec. The
 * {@code conditional_mob_effect} type pins a {@code BuiltInRegistries.MOB_EFFECT.holderByNameCodec()}
 * field, which cannot be round-tripped without a bootstrapped Minecraft, so its wiring is pinned
 * by literal source assertions rather than an encode/decode test.
 */
class FocusBehaviorDefContractTest {
	private static final Path FOCUS_BEHAVIOR_DEF =
		Path.of("src/main/java/dev/attuned/api/focus/FocusBehaviorDef.java");

	@Test
	void paletteDispatchesOnTheTypeField() throws IOException {
		String source = read(FOCUS_BEHAVIOR_DEF);

		assertTrue(source.contains("public sealed interface FocusBehaviorDef"),
			"The palette should be a sealed dispatch hierarchy.");
		assertTrue(source.contains("Codec<FocusBehaviorDef> CODEC = TYPE_CODEC.dispatch(\"type\", FocusBehaviorDef::type, Type::codec);"),
			"The palette codec should dispatch on the \"type\" field, one entry per palette Type.");
		assertTrue(source.contains("CONDITIONAL_MOB_EFFECT(\"attuned:conditional_mob_effect\", ConditionalMobEffect.MAP_CODEC)"),
			"The v1 palette should register the attuned:conditional_mob_effect type.");
		assertTrue(source.contains("return DataResult.error(() -> \"Unknown Focus behavior type: \" + name"),
			"An unknown palette type id should produce a structured decode error.");
	}

	@Test
	void conditionalMobEffectPinsItsParameterizedFields() throws IOException {
		String source = read(FOCUS_BEHAVIOR_DEF);

		assertTrue(source.contains("BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf(\"effect\")"),
			"conditional_mob_effect should read its effect from the mob-effect registry by id.");
		assertTrue(source.contains(".optionalFieldOf(\"amplifier\", 0)"),
			"conditional_mob_effect should expose an amplifier field.");
		assertTrue(source.contains(".optionalFieldOf(\"duration_ticks\", 40)"),
			"conditional_mob_effect should expose a duration_ticks field.");
		assertTrue(source.contains(".optionalFieldOf(\"refresh_ticks\", 20)"),
			"conditional_mob_effect should expose a refresh_ticks field.");
		assertTrue(source.contains("FocusCondition.CODEC.fieldOf(\"condition\")"),
			"conditional_mob_effect should gate on a composable FocusCondition.");
	}

	@Test
	void conditionalMobEffectValidatesProgrammaticInputs() throws IOException {
		String source = read(FOCUS_BEHAVIOR_DEF);

		assertTrue(source.contains("effect = Objects.requireNonNull(effect, \"effect\");"),
			"conditional_mob_effect should reject a null effect holder.");
		assertTrue(source.contains("condition = Objects.requireNonNull(condition, \"condition\");"),
			"conditional_mob_effect should reject a null condition.");
		assertTrue(source.contains("Conditional effect amplifier must be between"),
			"conditional_mob_effect should bound its amplifier.");
		assertTrue(source.contains("Conditional effect duration_ticks must be between"),
			"conditional_mob_effect should bound its duration.");
		assertTrue(source.contains("Conditional effect refresh_ticks must be at least"),
			"conditional_mob_effect should require a positive refresh interval.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
