package dev.attuned.content.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-grep contract for the runtime half of the broadened behavior palette: the three new
 * passive factories built by {@link DataFocusBehaviors} and the no-mixin on-hit dispatch wired
 * into the existing combat {@code AFTER_DAMAGE} handler. The factories touch registry-bound APIs
 * (effect/attribute holders), so their wiring is pinned by literal source assertions rather than a
 * bootstrapped round-trip.
 */
class PaletteBreadthContractTest {
	private static final Path DATA_BEHAVIORS =
		Path.of("src/main/java/dev/attuned/content/behavior/DataFocusBehaviors.java");
	private static final Path PALETTE_COMBAT =
		Path.of("src/main/java/dev/attuned/combat/PaletteCombat.java");
	private static final Path ATTUNED_COMBAT =
		Path.of("src/main/java/dev/attuned/combat/AttunedCombat.java");

	@Test
	void buildDispatchesEveryPaletteTypeExhaustively() throws IOException {
		String source = read(DATA_BEHAVIORS);

		assertTrue(source.contains("definition instanceof FocusBehaviorDef.ConditionalMobEffect effect")
				&& source.contains("return new ConditionalMobEffectBehavior(effect);"),
			"build should still construct ConditionalMobEffectBehavior.");
		assertTrue(source.contains("definition instanceof FocusBehaviorDef.OnHitEffect onHit")
				&& source.contains("return new OnHitEffectBehavior(onHit);"),
			"build should construct OnHitEffectBehavior for the on_hit_effect type.");
		assertTrue(source.contains("definition instanceof FocusBehaviorDef.PeriodicEffect periodic")
				&& source.contains("return new PeriodicEffectBehavior(periodic);"),
			"build should construct PeriodicEffectBehavior for the periodic_effect type.");
		assertTrue(source.contains("definition instanceof FocusBehaviorDef.AttributeWhile attributeWhile")
				&& source.contains("return new AttributeWhileBehavior(behaviorId, attributeWhile);"),
			"build should construct AttributeWhileBehavior for the attribute_while type, passing the behaviour id.");
		assertTrue(source.contains("throw new IllegalArgumentException(\"Unknown focus behavior definition: \" + definition);"),
			"build should reject unknown palette definitions if the hierarchy expands.");
	}

	@Test
	void periodicEffectReusesTheSharedRefresher() throws IOException {
		String source = read(DATA_BEHAVIORS);

		assertTrue(source.contains("package dev.attuned.content.behavior;"),
			"PeriodicEffectBehavior must live where the package-private PassiveEffectRefresher is reachable.");
		assertTrue(source.contains("PassiveEffectRefresher.refresh("),
			"periodic_effect should refresh through the shared PassiveEffectRefresher.");
		assertTrue(source.contains("player.tickCount % def.refreshTicks()"),
			"periodic_effect should refresh on a fixed tick cadence.");
	}

	@Test
	void attributeWhileTogglesATransientModifierUnderADistinctStableId() throws IOException {
		String source = read(DATA_BEHAVIORS);

		assertTrue(source.contains("\"palette_attr_while\""),
			"attribute_while must use a stable, distinct modifier-id prefix.");
		assertFalse(source.contains("\"slot_\""),
			"attribute_while must use a distinct modifier-id prefix (palette_attr_while) so it never "
				+ "collides with AttunedEffects' slot_N_mod_N scheme.");
		assertTrue(source.contains("addTransientModifier(") && source.contains("removeModifier("),
			"attribute_while should add and remove a transient modifier as the condition flips.");
		assertTrue(source.contains("def.condition().test("),
			"attribute_while should gate the modifier on its FocusCondition.");
		assertTrue(methodBody(source, "public void onDeactivate(ServerPlayer player, ItemStack focus)")
				.contains("removeModifier("),
			"attribute_while should strip its modifier on deactivation so unequipping never strands it.");
	}

	@Test
	void attributeWhileQualifiesItsModifierIdPerBehaviorSoTwoFociDoNotCollide() throws IOException {
		String source = read(DATA_BEHAVIORS);

		// The modifier id is derived from the behaviour's registry id, not one shared constant, so
		// two different attribute_while Foci on the same attribute install independent transient
		// modifiers and stack instead of one silently dropping the other.
		assertTrue(source.contains("ResourceLocation attributeWhileModifierId(ResourceLocation behaviorId)"),
			"attribute_while modifier ids must be derived from the behaviour id.");
		assertTrue(source.contains("behaviorId.getNamespace()") && source.contains("behaviorId.getPath()"),
			"the per-behaviour modifier id must incorporate the behaviour's registry id so two "
				+ "distinct attribute_while behaviours never share one id.");
		assertTrue(source.contains(
				"AttributeWhileBehavior(ResourceLocation behaviorId, FocusBehaviorDef.AttributeWhile def)"),
			"AttributeWhileBehavior must receive the behaviour id to qualify its modifier id.");
		assertTrue(source.contains("this.modifierId = attributeWhileModifierId(behaviorId)"),
			"AttributeWhileBehavior must hold a per-instance modifier id.");
		assertFalse(source.contains("ATTRIBUTE_WHILE_MODIFIER_ID"),
			"the single shared modifier-id constant must be replaced by the per-behaviour derivation.");
	}

	@Test
	void onHitEffectRunsThroughTheLiveCombatGuardsWithNoMixin() throws IOException {
		String palette = read(PALETTE_COMBAT);
		String combat = read(ATTUNED_COMBAT);

		assertTrue(palette.contains("AttunedCombat.isChargedDirectMelee("),
			"on_hit dispatch should reuse the shared charged-melee guard.");
		assertTrue(palette.contains("CombatTargets.isHostileOrPvpOpponent("),
			"on_hit dispatch should reuse the shared hostile/PvP target guard.");
		assertFalse(palette.contains("@At"), "on_hit must not introduce a mixin injection point.");
		assertFalse(palette.contains("Mixin"), "on_hit must run through the existing event, not a mixin.");

		assertTrue(methodBody(combat,
				"private static void afterDamage(LivingEntity defender, DamageSource source,")
				.contains("PaletteCombat.onMeleeHit("),
			"on_hit palette behaviors must proc from the existing AFTER_DAMAGE handler.");
	}

	@Test
	void paletteFactoriesStayPassiveOnly() throws IOException {
		String source = read(DATA_BEHAVIORS);

		assertEquals(0, count(source, "hasActiveAbility"),
			"Palette behaviors are passive-only (v2 defers active-ability authoring).");
		assertEquals(0, count(source, "onAbility"),
			"Palette behaviors are passive-only (v2 defers active-ability authoring).");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected source file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static int count(String value, String needle) {
		int total = 0;
		int index = 0;
		while ((index = value.indexOf(needle, index)) >= 0) {
			total++;
			index += needle.length();
		}
		return total;
	}

	private static String methodBody(String source, String signaturePrefix) {
		int signatureStart = source.indexOf(signaturePrefix);
		assertTrue(signatureStart >= 0, "Missing method signature: " + signaturePrefix);
		int bodyStart = source.indexOf('{', signatureStart);
		assertTrue(bodyStart >= 0, "Missing method body: " + signaturePrefix);
		int depth = 0;
		for (int index = bodyStart; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '{') {
				depth++;
			} else if (current == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(bodyStart, index + 1);
				}
			}
		}
		throw new AssertionError("Unterminated method body: " + signaturePrefix);
	}
}
