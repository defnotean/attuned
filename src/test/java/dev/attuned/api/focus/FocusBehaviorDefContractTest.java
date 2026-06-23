package dev.attuned.api.focus;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
	private static final Path DATA_FOCUS_BEHAVIORS =
		Path.of("src/main/java/dev/attuned/content/behavior/DataFocusBehaviors.java");
	private static final String[] PALETTE_TYPES = {
		"attuned:conditional_mob_effect",
		"attuned:on_hit_effect",
		"attuned:periodic_effect",
		"attuned:attribute_while",
		"attuned:block_context_effect",
		"attuned:use_item_window",
		"attuned:party_assist",
		"attuned:marked_target",
		"attuned:navigation_hint"
	};

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
		assertTrue(source.contains("BLOCK_CONTEXT_EFFECT(\"attuned:block_context_effect\", BlockContextEffect.MAP_CODEC)"),
			"The palette should register the attuned:block_context_effect type.");
		assertTrue(source.contains("USE_ITEM_WINDOW(\"attuned:use_item_window\", UseItemWindow.MAP_CODEC)"),
			"The palette should register the attuned:use_item_window type.");
		assertTrue(source.contains("PARTY_ASSIST(\"attuned:party_assist\", PartyAssist.MAP_CODEC)"),
			"The palette should register the attuned:party_assist type.");
		assertTrue(source.contains("MARKED_TARGET(\"attuned:marked_target\", MarkedTarget.MAP_CODEC)"),
			"The palette should register the attuned:marked_target type.");
		assertTrue(source.contains("NAVIGATION_HINT(\"attuned:navigation_hint\", NavigationHint.MAP_CODEC)"),
			"The palette should register the attuned:navigation_hint type.");
		assertTrue(source.contains("return DataResult.error(() -> \"Unknown Focus behavior type: \" + name"),
			"An unknown palette type id should produce a structured decode error.");
		assertTrue(source.contains("attuned:on_hit_effect")
				&& source.contains("attuned:periodic_effect")
				&& source.contains("attuned:attribute_while")
				&& source.contains("attuned:block_context_effect")
				&& source.contains("attuned:use_item_window")
				&& source.contains("attuned:party_assist")
				&& source.contains("attuned:marked_target")
				&& source.contains("attuned:navigation_hint"),
			"The unknown-type error should enumerate the new palette types so authors see them.");
	}

	@Test
	void paletteClassDocsTrackTheLiveNineTypeRoster() throws IOException {
		String apiDocs = classJavadoc(read(FOCUS_BEHAVIOR_DEF), "public sealed interface FocusBehaviorDef");
		String runtimeDocs = classJavadoc(read(DATA_FOCUS_BEHAVIORS), "public final class DataFocusBehaviors");

		assertFalse(apiDocs.contains("covers four"),
			"FocusBehaviorDef docs should not describe the nine-type palette as four shapes.");
		assertFalse(runtimeDocs.contains("covers four"),
			"DataFocusBehaviors docs should not describe the nine-type palette as four shapes.");
		for (String paletteType : PALETTE_TYPES) {
			assertTrue(apiDocs.contains(paletteType),
				"FocusBehaviorDef class docs should mention the live palette type " + paletteType);
			assertTrue(runtimeDocs.contains(paletteType),
				"DataFocusBehaviors class docs should mention the live palette type " + paletteType);
		}
	}

	@Test
	void unknownTypeErrorEnumeratesPaletteIdsFromTheLiveEnum() throws IOException {
		String source = read(FOCUS_BEHAVIOR_DEF);

		assertTrue(source.contains("private static String paletteTypeList()"),
			"The unknown-type diagnostic should be produced by one helper shared by every palette decode failure.");
		assertTrue(source.contains("Arrays.stream(Type.values())"),
			"The unknown-type diagnostic should enumerate the live Type enum instead of a hand-maintained string.");
		assertTrue(source.contains(".map(Type::id)"),
			"The palette diagnostic should list serialized ids, not Java enum names.");
		assertTrue(source.contains(".collect(Collectors.joining(\", \"))"),
			"Palette ids should be rendered as a readable comma-separated list.");
		assertTrue(source.contains("+ paletteTypeList() + \")\""),
			"The decode error should call the live palette list helper.");
		assertFalse(source.contains("palette types: attuned:conditional_mob_effect, attuned:on_hit_effect"),
			"The unknown-type error should not hard-code the current palette ids by hand.");
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
		assertTrue(source.contains("Conditional effect refresh_ticks must be between"),
			"conditional_mob_effect should bound its refresh interval.");
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
		assertTrue(source.contains("Periodic effect refresh_ticks must be between"),
			"periodic_effect should bound its refresh interval.");
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

	@Test
	void blockContextEffectPinsItsBoundedFields() throws IOException {
		String source = read(FOCUS_BEHAVIOR_DEF);

		assertTrue(source.contains("Identifier blockTag"),
			"block_context_effect should carry the block tag id it scans for.");
		assertTrue(source.contains("Identifier.CODEC.fieldOf(\"block_tag\").forGetter(BlockContextEffect::blockTag)"),
			"block_context_effect should decode its block_tag as a resource id.");
		assertTrue(source.contains("Codec.intRange(MIN_RADIUS, MAX_RADIUS).optionalFieldOf(\"radius\", 2).forGetter(BlockContextEffect::radius)"),
			"block_context_effect should expose a bounded small radius with a conservative default.");
		assertTrue(source.contains("Codec.BOOL.optionalFieldOf(\"requires_lit\", false).forGetter(BlockContextEffect::requiresLit)"),
			"block_context_effect should optionally require lit block states for forge/campfire contexts.");
		assertTrue(source.contains("BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf(\"effect\").forGetter(BlockContextEffect::effect)"),
			"block_context_effect should read its effect from the mob-effect registry by id.");
		assertTrue(source.contains("Codec.intRange(MIN_REFRESH, MAX_DURATION).optionalFieldOf(\"refresh_ticks\", 20).forGetter(BlockContextEffect::refreshTicks)"),
			"block_context_effect should throttle scans with a bounded refresh_ticks field.");
		assertTrue(source.contains("Block-context effect radius must be between"),
			"block_context_effect should bound its scan radius in the record constructor.");
		assertTrue(source.contains("Block-context effect refresh_ticks must be between"),
			"block_context_effect should bound its scan cadence in the record constructor.");
	}

	@Test
	void useItemWindowPinsItsSelectorOutputCooldownAndOptionalConditionFields() throws IOException {
		String source = read(FOCUS_BEHAVIOR_DEF);

		assertTrue(source.contains("record UseItemWindow("),
			"use_item_window should be a sealed palette record.");
		assertTrue(source.contains("Optional<Identifier> item"),
			"use_item_window should optionally match one item id.");
		assertTrue(source.contains("Optional<Identifier> itemTag"),
			"use_item_window should optionally match one item tag id.");
		assertTrue(source.contains("Identifier.CODEC.optionalFieldOf(\"item\").forGetter(UseItemWindow::item)"),
			"use_item_window should decode its item selector as a resource id.");
		assertTrue(source.contains("Identifier.CODEC.optionalFieldOf(\"item_tag\").forGetter(UseItemWindow::itemTag)"),
			"use_item_window should decode its item_tag selector as a resource id.");
		assertTrue(source.contains("BuiltInRegistries.MOB_EFFECT.holderByNameCodec().optionalFieldOf(\"effect\").forGetter(UseItemWindow::effect)"),
			"use_item_window should optionally apply a mob effect.");
		assertTrue(source.contains("ModifierEntry.CODEC.optionalFieldOf(\"modifier\").forGetter(UseItemWindow::modifier)"),
			"use_item_window should optionally apply a transient attribute modifier.");
		assertTrue(source.contains("FocusCondition.CODEC.optionalFieldOf(\"condition\").forGetter(UseItemWindow::condition)"),
			"use_item_window should optionally gate on a FocusCondition.");
		assertTrue(source.contains("Codec.intRange(MIN_DURATION, MAX_DURATION).fieldOf(\"duration_ticks\").forGetter(UseItemWindow::durationTicks)"),
			"use_item_window should require a bounded duration_ticks field.");
		assertTrue(source.contains("Codec.intRange(MIN_COOLDOWN, MAX_DURATION).fieldOf(\"cooldown_ticks\").forGetter(UseItemWindow::cooldownTicks)"),
			"use_item_window should require a bounded cooldown_ticks field.");
		assertTrue(source.contains("Use-item window must define exactly one of item or item_tag"),
			"use_item_window should reject ambiguous or missing item selectors.");
		assertTrue(source.contains("Use-item window must define at least one of effect or modifier"),
			"use_item_window should reject no-op definitions.");
	}

	@Test
	void markedTargetPinsItsTriggerMarkCooldownAndConsumeEffectFields() throws IOException {
		String source = read(FOCUS_BEHAVIOR_DEF);

		assertTrue(source.contains("record MarkedTarget("),
			"marked_target should be a sealed palette record.");
		assertTrue(source.contains("enum MarkedTargetTrigger"),
			"marked_target should expose a trigger enum rather than free-form trigger strings.");
		assertTrue(source.contains("CHARGED_MELEE_HIT(\"charged_melee_hit\")"),
			"marked_target should support charged direct melee hit triggers.");
		assertTrue(source.contains("MarkedTargetTrigger.CODEC.fieldOf(\"prime_trigger\").forGetter(MarkedTarget::primeTrigger)"),
			"marked_target should decode its prime_trigger through the trigger codec.");
		assertTrue(source.contains("MarkedTargetTrigger.CODEC.fieldOf(\"consume_trigger\").forGetter(MarkedTarget::consumeTrigger)"),
			"marked_target should decode its consume_trigger through the trigger codec.");
		assertTrue(source.contains("Codec.intRange(MIN_DURATION, MAX_DURATION).fieldOf(\"mark_duration_ticks\").forGetter(MarkedTarget::markDurationTicks)"),
			"marked_target should require a bounded mark_duration_ticks field.");
		assertTrue(source.contains("Codec.intRange(MIN_COOLDOWN, MAX_DURATION).fieldOf(\"cooldown_ticks\").forGetter(MarkedTarget::cooldownTicks)"),
			"marked_target should require a bounded cooldown_ticks field.");
		assertTrue(source.contains("EffectOnConsume.CODEC.fieldOf(\"effect_on_consume\").forGetter(MarkedTarget::effectOnConsume)"),
			"marked_target should decode its consume effect as a structured object.");
		assertTrue(source.contains("record EffectOnConsume("),
			"marked_target effect_on_consume should carry effect, duration, amplifier, and target_self.");
		assertTrue(source.contains("Codec.BOOL.optionalFieldOf(\"target_self\", false).forGetter(EffectOnConsume::targetSelf)"),
			"marked_target should let consume effects target the attacker when desired.");
		assertTrue(source.contains("Marked target mark_duration_ticks must be between"),
			"marked_target should bound mark duration in the record constructor.");
		assertTrue(source.contains("Marked target cooldown_ticks must be between"),
			"marked_target should bound cooldown in the record constructor.");
	}

	@Test
	void partyAssistPinsItsCircleTriggerTargetCooldownAndEffectFields() throws IOException {
		String source = read(FOCUS_BEHAVIOR_DEF);

		assertTrue(source.contains("record PartyAssist("),
			"party_assist should be a sealed palette record.");
		assertTrue(source.contains("enum PartyAssistTrigger"),
			"party_assist should expose a trigger enum rather than free-form strings.");
		assertTrue(source.contains("PERIODIC_SCAN(\"periodic_scan\")"),
			"party_assist should support a conservative passive periodic scan trigger.");
		assertTrue(source.contains("enum PartyAssistTargetFilter"),
			"party_assist should expose target filters rather than free-form strings.");
		assertTrue(source.contains("NEARBY_MEMBERS(\"nearby_members\")"),
			"party_assist should support generic nearby member assists.");
		assertTrue(source.contains("WOUNDED_MEMBERS(\"wounded_members\")"),
			"party_assist should support Vowplate-style low-health ally assists.");
		assertTrue(source.contains("DROWNING_MEMBERS(\"drowning_members\")"),
			"party_assist should support Rescueflame-style drowning ally assists.");
		assertTrue(source.contains("PartyAssistTrigger.CODEC.fieldOf(\"trigger\").forGetter(PartyAssist::trigger)"),
			"party_assist should decode its trigger through the trigger codec.");
		assertTrue(source.contains("Codec.intRange(MIN_RADIUS, MAX_RADIUS).fieldOf(\"radius\").forGetter(PartyAssist::radius)"),
			"party_assist should require a bounded data-defined radius.");
		assertTrue(source.contains("Codec.intRange(MIN_COOLDOWN, MAX_DURATION).fieldOf(\"cooldown_ticks\").forGetter(PartyAssist::cooldownTicks)"),
			"party_assist should require a bounded per-Circle cooldown.");
		assertTrue(source.contains("PartyAssistTargetFilter.CODEC.fieldOf(\"target_filter\").forGetter(PartyAssist::targetFilter)"),
			"party_assist should decode target_filter through the target-filter codec.");
		assertTrue(source.contains("PartyAssistEffect.CODEC.fieldOf(\"effect\").forGetter(PartyAssist::effect)"),
			"party_assist should decode its assist effect as a structured object.");
		assertTrue(source.contains("Codec.STRING.fieldOf(\"message_key\").forGetter(PartyAssist::messageKey)"),
			"party_assist should carry a translatable message key for pack-authored feedback.");
		assertTrue(source.contains("record PartyAssistEffect("),
			"party_assist effect should carry effect, duration, and amplifier fields.");
		assertTrue(source.contains("BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf(\"effect\").forGetter(PartyAssistEffect::effect)"),
			"party_assist effect should read its mob effect from the registry by id.");
		assertTrue(source.contains("Party assist radius must be between"),
			"party_assist should bound radius in the record constructor.");
		assertTrue(source.contains("Party assist cooldown_ticks must be between"),
			"party_assist should bound cooldown in the record constructor.");
		assertTrue(source.contains("Party assist message_key must not be blank"),
			"party_assist should reject blank feedback keys.");
	}

	@Test
	void navigationHintPinsItsTargetAgeDimensionAndCadenceFields() throws IOException {
		String source = read(FOCUS_BEHAVIOR_DEF);

		assertTrue(source.contains("record NavigationHint("),
			"navigation_hint should be a sealed palette record.");
		assertTrue(source.contains("enum NavigationTargetKind"),
			"navigation_hint should expose a target-kind enum rather than free-form strings.");
		assertTrue(source.contains("CIRCLE_PING(\"circle_ping\")"),
			"navigation_hint should support accepted Circle ping targets.");
		assertTrue(source.contains("HELD_LODESTONE_COMPASS(\"held_lodestone_compass\")"),
			"navigation_hint should support explicit vanilla lodestone compass targets.");
		assertTrue(source.contains("NavigationTargetKind.CODEC.fieldOf(\"target_kind\").forGetter(NavigationHint::targetKind)"),
			"navigation_hint should decode target_kind through the target-kind codec.");
		assertTrue(source.contains("Codec.intRange(MIN_AGE, MAX_AGE).fieldOf(\"max_age_ticks\").forGetter(NavigationHint::maxAgeTicks)"),
			"navigation_hint should require a bounded max_age_ticks field.");
		assertTrue(source.contains("Codec.BOOL.fieldOf(\"same_dimension_only\").forGetter(NavigationHint::sameDimensionOnly)"),
			"navigation_hint should require an explicit same_dimension_only guard.");
		assertTrue(source.contains("Codec.intRange(MIN_CADENCE, MAX_AGE).fieldOf(\"feedback_cadence_ticks\").forGetter(NavigationHint::feedbackCadenceTicks)"),
			"navigation_hint should require a restrained feedback cadence field.");
		assertTrue(source.contains("Navigation hint max_age_ticks must be between"),
			"navigation_hint should bound max target age in the record constructor.");
		assertTrue(source.contains("Navigation hint feedback_cadence_ticks must be between"),
			"navigation_hint should bound feedback cadence in the record constructor.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static String classJavadoc(String source, String declaration) {
		int declarationIndex = source.indexOf(declaration);
		assertTrue(declarationIndex >= 0, "Missing declaration: " + declaration);
		int start = source.lastIndexOf("/**", declarationIndex);
		assertTrue(start >= 0, "Missing class JavaDoc before: " + declaration);
		return source.substring(start, declarationIndex);
	}
}
