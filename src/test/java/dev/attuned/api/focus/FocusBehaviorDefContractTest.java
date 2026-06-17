package dev.attuned.api.focus;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-grep contract for the behavior-palette records + dispatch codec. Every palette type pins a
 * {@code BuiltInRegistries.MOB_EFFECT.holderByNameCodec()} / {@code ModifierEntry.CODEC} field, which
 * cannot be round-tripped without a bootstrapped Minecraft, so the wiring is pinned by literal source
 * assertions rather than an encode/decode test.
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
			"The palette should register the attuned:conditional_mob_effect type.");
		assertTrue(source.contains("ON_HIT_EFFECT(\"attuned:on_hit_effect\", OnHitEffect.MAP_CODEC)"),
			"The palette should register the attuned:on_hit_effect type.");
		assertTrue(source.contains("PERIODIC_EFFECT(\"attuned:periodic_effect\", PeriodicEffect.MAP_CODEC)"),
			"The palette should register the attuned:periodic_effect type.");
		assertTrue(source.contains("ATTRIBUTE_WHILE(\"attuned:attribute_while\", AttributeWhile.MAP_CODEC)"),
			"The palette should register the attuned:attribute_while type.");
		assertTrue(source.contains("return DataResult.error(() -> \"Unknown Focus behavior type: \" + name")
				|| source.contains("return DataResult.error(\"Unknown Focus behavior type: \" + name"),
			"An unknown palette type id should produce a structured decode error.");
		assertTrue(source.contains("attuned:on_hit_effect")
				&& source.contains("attuned:periodic_effect")
				&& source.contains("attuned:attribute_while"),
			"The unknown-type error should enumerate the new palette types so authors see them.");
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

	@Test
	void onHitEffectPinsItsParameterizedFields() throws IOException {
		String source = read(FOCUS_BEHAVIOR_DEF);

		assertTrue(source.contains("BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf(\"effect\").forGetter(OnHitEffect::effect)"),
			"on_hit_effect should read its effect from the mob-effect registry by id.");
		assertTrue(source.contains(".optionalFieldOf(\"charge_threshold\""),
			"on_hit_effect should expose a charge_threshold field so authors gate on a deliberate swing.");
		assertTrue(source.contains(".optionalFieldOf(\"target_self\", false)"),
			"on_hit_effect should let the effect target the attacker, defaulting to the victim.");
		assertTrue(source.contains(".optionalFieldOf(\"hostile_only\", true)"),
			"on_hit_effect should default to hostile-only, matching the code combat guards.");
		assertTrue(source.contains("On-hit effect charge_threshold must be between"),
			"on_hit_effect should bound its charge threshold.");
	}

	@Test
	void periodicEffectPinsItsParameterizedFields() throws IOException {
		String source = read(FOCUS_BEHAVIOR_DEF);

		assertTrue(source.contains("BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf(\"effect\").forGetter(PeriodicEffect::effect)"),
			"periodic_effect should read its effect from the mob-effect registry by id.");
		assertTrue(source.contains(".optionalFieldOf(\"duration_ticks\", 80).forGetter(PeriodicEffect::durationTicks)"),
			"periodic_effect should expose a duration_ticks field.");
		assertTrue(source.contains(".optionalFieldOf(\"refresh_ticks\", 40).forGetter(PeriodicEffect::refreshTicks)"),
			"periodic_effect should expose a refresh_ticks cadence.");
		assertTrue(source.contains("Periodic effect refresh_ticks must be at least"),
			"periodic_effect should require a positive refresh interval.");
	}

	@Test
	void attributeWhileReusesTheModifierAndConditionCodecs() throws IOException {
		String source = read(FOCUS_BEHAVIOR_DEF);

		assertTrue(source.contains("ModifierEntry.CODEC.fieldOf(\"modifier\").forGetter(AttributeWhile::modifier)"),
			"attribute_while should reuse ModifierEntry.CODEC for its registry-bound attribute modifier.");
		assertTrue(source.contains("FocusCondition.CODEC.fieldOf(\"condition\").forGetter(AttributeWhile::condition)"),
			"attribute_while should gate on a composable FocusCondition.");
		assertTrue(source.contains("modifier = Objects.requireNonNull(modifier, \"modifier\");"),
			"attribute_while should reject a null modifier.");
		assertTrue(source.contains("condition = Objects.requireNonNull(condition, \"condition\");"),
			"attribute_while should reject a null condition.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
