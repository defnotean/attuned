package dev.attuned.api.focus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

/**
 * A datapack-defined, parameterized Focus behavior instance — the "behavior palette".
 * Loaded from the synced registry at {@code data/<ns>/attuned/focus_behavior/<id>.json}.
 *
 * <p>Each definition is one of a small set of palette {@link Type types}, dispatched on a
 * {@code "type"} field. The shipped palette is passive-only (no Focus Ability) and covers nine
 * shapes:
 * <ul>
 *   <li>{@code attuned:conditional_mob_effect} — keep a mob effect refreshed on the wearer for
 *       as long as a {@link FocusCondition} holds.</li>
 *   <li>{@code attuned:on_hit_effect} — apply a mob effect to the victim (or self) on a charged,
 *       hostile-only melee hit.</li>
 *   <li>{@code attuned:periodic_effect} — keep a flat mob effect refreshed on a fixed cadence
 *       (an unconditional buff).</li>
 *   <li>{@code attuned:attribute_while} — apply an attribute modifier only while a
 *       {@link FocusCondition} holds.</li>
 *   <li>{@code attuned:block_context_effect} - keep a mob effect refreshed near tagged
 *       blocks on a restrained scan cadence.</li>
 *   <li>{@code attuned:use_item_window} - grant a short effect or modifier window after
 *       using a matching item.</li>
 *   <li>{@code attuned:party_assist} - assist a nearby eligible Circle member with a
 *       small beneficial effect.</li>
 *   <li>{@code attuned:marked_target} - prime and consume a short-lived mark through
 *       charged hostile/PvP melee hits.</li>
 *   <li>{@code attuned:navigation_hint} - give restrained feedback toward accepted
 *       party targets or explicit lodestone compass points.</li>
 * </ul>
 *
 * <p>This is purely declarative data. A runtime {@link FocusBehavior} is built from it by
 * {@code dev.attuned.content.behavior.DataFocusBehaviors}; the resolution funnel
 * ({@code AttunedRegistries.getBehavior}) prefers a code behavior of the same id, then falls
 * back to building one from this registry.
 */
public sealed interface FocusBehaviorDef {

	/** The dispatch tag written as the {@code "type"} field in JSON. */
	Type type();

	/** The serialized palette types, dispatched on the {@code "type"} field. */
	enum Type {
		CONDITIONAL_MOB_EFFECT("attuned:conditional_mob_effect", ConditionalMobEffect.MAP_CODEC),
		ON_HIT_EFFECT("attuned:on_hit_effect", OnHitEffect.MAP_CODEC),
		PERIODIC_EFFECT("attuned:periodic_effect", PeriodicEffect.MAP_CODEC),
		ATTRIBUTE_WHILE("attuned:attribute_while", AttributeWhile.MAP_CODEC),
		BLOCK_CONTEXT_EFFECT("attuned:block_context_effect", BlockContextEffect.MAP_CODEC),
		USE_ITEM_WINDOW("attuned:use_item_window", UseItemWindow.MAP_CODEC),
		PARTY_ASSIST("attuned:party_assist", PartyAssist.MAP_CODEC),
		MARKED_TARGET("attuned:marked_target", MarkedTarget.MAP_CODEC),
		NAVIGATION_HINT("attuned:navigation_hint", NavigationHint.MAP_CODEC);

		private final String id;
		private final Codec<? extends FocusBehaviorDef> codec;

		Type(String id, MapCodec<? extends FocusBehaviorDef> codec) {
			this.id = id;
			this.codec = codec.codec();
		}

		public String id() {
			return id;
		}

		public Codec<? extends FocusBehaviorDef> codec() {
			return codec;
		}
	}

	Codec<Type> TYPE_CODEC = Codec.STRING.flatXmap(FocusBehaviorDef::typeByName,
		type -> DataResult.success(type.id()));

	/** Dispatch codec keyed on the {@code "type"} field, one entry per palette {@link Type}. */
	Codec<FocusBehaviorDef> CODEC = TYPE_CODEC.dispatch("type", FocusBehaviorDef::type, Type::codec);

	private static DataResult<Type> typeByName(String name) {
		for (Type type : Type.values()) {
			if (type.id().equals(name)) {
				return DataResult.success(type);
			}
		}
		return DataResult.error(() -> "Unknown Focus behavior type: " + name
			+ " (palette types: " + paletteTypeList() + ")");
	}

	private static String paletteTypeList() {
		return Arrays.stream(Type.values())
			.map(Type::id)
			.collect(Collectors.joining(", "));
	}

	// ---- Palette types ----------------------------------------------------

	/**
	 * Keeps {@code effect} refreshed on the wearer (amplifier/duration as given) for as
	 * long as {@code condition} holds, re-applying no more often than every
	 * {@code refreshTicks}. A passive-only behavior: it owns no Focus Ability.
	 */
	record ConditionalMobEffect(
			Holder<MobEffect> effect,
			int amplifier,
			int durationTicks,
			int refreshTicks,
			FocusCondition condition) implements FocusBehaviorDef {

		private static final int MIN_AMPLIFIER = 0;
		private static final int MAX_AMPLIFIER = 255;
		private static final int MIN_DURATION = 1;
		private static final int MAX_DURATION = 1_000_000;
		private static final int MIN_REFRESH = 1;

		public ConditionalMobEffect {
			effect = Objects.requireNonNull(effect, "effect");
			condition = Objects.requireNonNull(condition, "condition");
			if (amplifier < MIN_AMPLIFIER || amplifier > MAX_AMPLIFIER) {
				throw new IllegalArgumentException(
					"Conditional effect amplifier must be between " + MIN_AMPLIFIER + " and " + MAX_AMPLIFIER);
			}
			if (durationTicks < MIN_DURATION || durationTicks > MAX_DURATION) {
				throw new IllegalArgumentException(
					"Conditional effect duration_ticks must be between " + MIN_DURATION + " and " + MAX_DURATION);
			}
			if (refreshTicks < MIN_REFRESH || refreshTicks > MAX_DURATION) {
				throw new IllegalArgumentException(
					"Conditional effect refresh_ticks must be between " + MIN_REFRESH + " and " + MAX_DURATION);
			}
		}

		static final MapCodec<ConditionalMobEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(ConditionalMobEffect::effect),
			Codec.intRange(MIN_AMPLIFIER, MAX_AMPLIFIER).optionalFieldOf("amplifier", 0).forGetter(ConditionalMobEffect::amplifier),
			Codec.intRange(MIN_DURATION, MAX_DURATION).optionalFieldOf("duration_ticks", 40).forGetter(ConditionalMobEffect::durationTicks),
			Codec.intRange(MIN_REFRESH, MAX_DURATION).optionalFieldOf("refresh_ticks", 20).forGetter(ConditionalMobEffect::refreshTicks),
			FocusCondition.CODEC.fieldOf("condition").forGetter(ConditionalMobEffect::condition)
		).apply(instance, ConditionalMobEffect::new));

		@Override
		public Type type() {
			return Type.CONDITIONAL_MOB_EFFECT;
		}
	}

	/**
	 * Applies {@code effect} to the victim — or to the attacker, when {@code targetSelf} —
	 * on a charged, hostile-only direct-melee hit. The charge gate reuses
	 * {@code AttunedCombat.isChargedDirectMelee} (a deliberate, fully-charged swing rather than
	 * spam pressure) and the target gate reuses {@code CombatTargets.isHostileOrPvpOpponent}.
	 * A passive-only behavior: it owns no Focus Ability and runs through the existing combat
	 * {@code AFTER_DAMAGE} hook, not a mixin.
	 */
	record OnHitEffect(
			Holder<MobEffect> effect,
			int amplifier,
			int durationTicks,
			float chargeThreshold,
			boolean targetSelf,
			boolean hostileOnly) implements FocusBehaviorDef {

		private static final int MIN_AMPLIFIER = 0;
		private static final int MAX_AMPLIFIER = 255;
		private static final int MIN_DURATION = 1;
		private static final int MAX_DURATION = 1_000_000;
		private static final float MIN_CHARGE = 0.0F;
		private static final float MAX_CHARGE = 1.0F;
		private static final float DEFAULT_CHARGE = 0.9F;

		public OnHitEffect {
			effect = Objects.requireNonNull(effect, "effect");
			if (amplifier < MIN_AMPLIFIER || amplifier > MAX_AMPLIFIER) {
				throw new IllegalArgumentException(
					"On-hit effect amplifier must be between " + MIN_AMPLIFIER + " and " + MAX_AMPLIFIER);
			}
			if (durationTicks < MIN_DURATION || durationTicks > MAX_DURATION) {
				throw new IllegalArgumentException(
					"On-hit effect duration_ticks must be between " + MIN_DURATION + " and " + MAX_DURATION);
			}
			if (!Float.isFinite(chargeThreshold) || chargeThreshold < MIN_CHARGE || chargeThreshold > MAX_CHARGE) {
				throw new IllegalArgumentException(
					"On-hit effect charge_threshold must be between " + MIN_CHARGE + " and " + MAX_CHARGE);
			}
		}

		static final MapCodec<OnHitEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(OnHitEffect::effect),
			Codec.intRange(MIN_AMPLIFIER, MAX_AMPLIFIER).optionalFieldOf("amplifier", 0).forGetter(OnHitEffect::amplifier),
			Codec.intRange(MIN_DURATION, MAX_DURATION).optionalFieldOf("duration_ticks", 60).forGetter(OnHitEffect::durationTicks),
			Codec.floatRange(MIN_CHARGE, MAX_CHARGE).optionalFieldOf("charge_threshold", DEFAULT_CHARGE).forGetter(OnHitEffect::chargeThreshold),
			Codec.BOOL.optionalFieldOf("target_self", false).forGetter(OnHitEffect::targetSelf),
			Codec.BOOL.optionalFieldOf("hostile_only", true).forGetter(OnHitEffect::hostileOnly)
		).apply(instance, OnHitEffect::new));

		@Override
		public Type type() {
			return Type.ON_HIT_EFFECT;
		}
	}

	/**
	 * Keeps {@code effect} refreshed on the wearer on a fixed cadence — an unconditional buff,
	 * like {@link ConditionalMobEffect} but with no gating condition. A passive-only behavior:
	 * it owns no Focus Ability.
	 */
	record PeriodicEffect(
			Holder<MobEffect> effect,
			int amplifier,
			int durationTicks,
			int refreshTicks) implements FocusBehaviorDef {

		private static final int MIN_AMPLIFIER = 0;
		private static final int MAX_AMPLIFIER = 255;
		private static final int MIN_DURATION = 1;
		private static final int MAX_DURATION = 1_000_000;
		private static final int MIN_REFRESH = 1;

		public PeriodicEffect {
			effect = Objects.requireNonNull(effect, "effect");
			if (amplifier < MIN_AMPLIFIER || amplifier > MAX_AMPLIFIER) {
				throw new IllegalArgumentException(
					"Periodic effect amplifier must be between " + MIN_AMPLIFIER + " and " + MAX_AMPLIFIER);
			}
			if (durationTicks < MIN_DURATION || durationTicks > MAX_DURATION) {
				throw new IllegalArgumentException(
					"Periodic effect duration_ticks must be between " + MIN_DURATION + " and " + MAX_DURATION);
			}
			if (refreshTicks < MIN_REFRESH || refreshTicks > MAX_DURATION) {
				throw new IllegalArgumentException(
					"Periodic effect refresh_ticks must be between " + MIN_REFRESH + " and " + MAX_DURATION);
			}
		}

		static final MapCodec<PeriodicEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(PeriodicEffect::effect),
			Codec.intRange(MIN_AMPLIFIER, MAX_AMPLIFIER).optionalFieldOf("amplifier", 0).forGetter(PeriodicEffect::amplifier),
			Codec.intRange(MIN_DURATION, MAX_DURATION).optionalFieldOf("duration_ticks", 80).forGetter(PeriodicEffect::durationTicks),
			Codec.intRange(MIN_REFRESH, MAX_DURATION).optionalFieldOf("refresh_ticks", 40).forGetter(PeriodicEffect::refreshTicks)
		).apply(instance, PeriodicEffect::new));

		@Override
		public Type type() {
			return Type.PERIODIC_EFFECT;
		}
	}

	/**
	 * Applies a transient attribute {@code modifier} to the wearer only while {@code condition}
	 * holds, adding it as the condition becomes true and removing it as the condition becomes
	 * false (and on deactivation). A passive-only behavior: it owns no Focus Ability. This is the
	 * conditional twin of a declarative {@link ModifierEntry}.
	 */
	record AttributeWhile(
			ModifierEntry modifier,
			FocusCondition condition) implements FocusBehaviorDef {

		public AttributeWhile {
			modifier = Objects.requireNonNull(modifier, "modifier");
			condition = Objects.requireNonNull(condition, "condition");
		}

		static final MapCodec<AttributeWhile> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ModifierEntry.CODEC.fieldOf("modifier").forGetter(AttributeWhile::modifier),
			FocusCondition.CODEC.fieldOf("condition").forGetter(AttributeWhile::condition)
		).apply(instance, AttributeWhile::new));

		@Override
		public Type type() {
			return Type.ATTRIBUTE_WHILE;
		}
	}

	/**
	 * Keeps {@code effect} refreshed while the wearer is on or near a small, bounded set of
	 * blocks matching {@code blockTag}. Runtime scans are intentionally throttled by
	 * {@code refreshTicks} and capped by {@code radius} so pack-authored contextual buffs cannot
	 * turn into broad per-tick chunk pressure.
	 */
	record BlockContextEffect(
			ResourceLocation blockTag,
			int radius,
			boolean requiresLit,
			Holder<MobEffect> effect,
			int amplifier,
			int durationTicks,
			int refreshTicks) implements FocusBehaviorDef {

		private static final int MIN_RADIUS = 0;
		private static final int MAX_RADIUS = 5;
		private static final int MIN_AMPLIFIER = 0;
		private static final int MAX_AMPLIFIER = 255;
		private static final int MIN_DURATION = 1;
		private static final int MAX_DURATION = 1_000_000;
		private static final int MIN_REFRESH = 10;

		public BlockContextEffect {
			blockTag = Objects.requireNonNull(blockTag, "blockTag");
			effect = Objects.requireNonNull(effect, "effect");
			if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
				throw new IllegalArgumentException(
					"Block-context effect radius must be between " + MIN_RADIUS + " and " + MAX_RADIUS);
			}
			if (amplifier < MIN_AMPLIFIER || amplifier > MAX_AMPLIFIER) {
				throw new IllegalArgumentException(
					"Block-context effect amplifier must be between " + MIN_AMPLIFIER + " and " + MAX_AMPLIFIER);
			}
			if (durationTicks < MIN_DURATION || durationTicks > MAX_DURATION) {
				throw new IllegalArgumentException(
					"Block-context effect duration_ticks must be between " + MIN_DURATION + " and " + MAX_DURATION);
			}
			if (refreshTicks < MIN_REFRESH || refreshTicks > MAX_DURATION) {
				throw new IllegalArgumentException(
					"Block-context effect refresh_ticks must be between " + MIN_REFRESH + " and " + MAX_DURATION);
			}
		}

		static final MapCodec<BlockContextEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ResourceLocation.CODEC.fieldOf("block_tag").forGetter(BlockContextEffect::blockTag),
			Codec.intRange(MIN_RADIUS, MAX_RADIUS).optionalFieldOf("radius", 2).forGetter(BlockContextEffect::radius),
			Codec.BOOL.optionalFieldOf("requires_lit", false).forGetter(BlockContextEffect::requiresLit),
			BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(BlockContextEffect::effect),
			Codec.intRange(MIN_AMPLIFIER, MAX_AMPLIFIER).optionalFieldOf("amplifier", 0).forGetter(BlockContextEffect::amplifier),
			Codec.intRange(MIN_DURATION, MAX_DURATION).optionalFieldOf("duration_ticks", 80).forGetter(BlockContextEffect::durationTicks),
			Codec.intRange(MIN_REFRESH, MAX_DURATION).optionalFieldOf("refresh_ticks", 20).forGetter(BlockContextEffect::refreshTicks)
		).apply(instance, BlockContextEffect::new));

		@Override
		public Type type() {
			return Type.BLOCK_CONTEXT_EFFECT;
		}
	}

	/**
	 * Grants a short effect and/or transient attribute window after the player uses a matching
	 * item. Runtime dispatch is event-only: the item selector is checked from the server item-use
	 * callback, never by scanning inventories every tick.
	 */
	record UseItemWindow(
			Optional<ResourceLocation> item,
			Optional<ResourceLocation> itemTag,
			int durationTicks,
			int cooldownTicks,
			Optional<Holder<MobEffect>> effect,
			int amplifier,
			Optional<ModifierEntry> modifier,
			Optional<FocusCondition> condition) implements FocusBehaviorDef {

		private static final int MIN_DURATION = 1;
		private static final int MAX_DURATION = 1_000_000;
		private static final int MIN_COOLDOWN = 1;
		private static final int MIN_AMPLIFIER = 0;
		private static final int MAX_AMPLIFIER = 255;

		public UseItemWindow {
			item = Objects.requireNonNull(item, "item");
			itemTag = Objects.requireNonNull(itemTag, "itemTag");
			effect = Objects.requireNonNull(effect, "effect");
			modifier = Objects.requireNonNull(modifier, "modifier");
			condition = Objects.requireNonNull(condition, "condition");
			if (item.isPresent() == itemTag.isPresent()) {
				throw new IllegalArgumentException("Use-item window must define exactly one of item or item_tag");
			}
			if (effect.isEmpty() && modifier.isEmpty()) {
				throw new IllegalArgumentException("Use-item window must define at least one of effect or modifier");
			}
			if (durationTicks < MIN_DURATION || durationTicks > MAX_DURATION) {
				throw new IllegalArgumentException(
					"Use-item window duration_ticks must be between " + MIN_DURATION + " and " + MAX_DURATION);
			}
			if (cooldownTicks < MIN_COOLDOWN || cooldownTicks > MAX_DURATION) {
				throw new IllegalArgumentException(
					"Use-item window cooldown_ticks must be between " + MIN_COOLDOWN + " and " + MAX_DURATION);
			}
			if (amplifier < MIN_AMPLIFIER || amplifier > MAX_AMPLIFIER) {
				throw new IllegalArgumentException(
					"Use-item window amplifier must be between " + MIN_AMPLIFIER + " and " + MAX_AMPLIFIER);
			}
		}

		static final MapCodec<UseItemWindow> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ResourceLocation.CODEC.optionalFieldOf("item").forGetter(UseItemWindow::item),
			ResourceLocation.CODEC.optionalFieldOf("item_tag").forGetter(UseItemWindow::itemTag),
			Codec.intRange(MIN_DURATION, MAX_DURATION).fieldOf("duration_ticks").forGetter(UseItemWindow::durationTicks),
			Codec.intRange(MIN_COOLDOWN, MAX_DURATION).fieldOf("cooldown_ticks").forGetter(UseItemWindow::cooldownTicks),
			BuiltInRegistries.MOB_EFFECT.holderByNameCodec().optionalFieldOf("effect").forGetter(UseItemWindow::effect),
			Codec.intRange(MIN_AMPLIFIER, MAX_AMPLIFIER).optionalFieldOf("amplifier", 0).forGetter(UseItemWindow::amplifier),
			ModifierEntry.CODEC.optionalFieldOf("modifier").forGetter(UseItemWindow::modifier),
			FocusCondition.CODEC.optionalFieldOf("condition").forGetter(UseItemWindow::condition)
		).apply(instance, UseItemWindow::new));

		@Override
		public Type type() {
			return Type.USE_ITEM_WINDOW;
		}
	}

	enum PartyAssistTrigger {
		PERIODIC_SCAN("periodic_scan");

		private final String id;

		PartyAssistTrigger(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		static final Codec<PartyAssistTrigger> CODEC = Codec.STRING.flatXmap(
			PartyAssistTrigger::byName, trigger -> DataResult.success(trigger.id()));

		private static DataResult<PartyAssistTrigger> byName(String name) {
			for (PartyAssistTrigger trigger : values()) {
				if (trigger.id().equals(name)) {
					return DataResult.success(trigger);
				}
			}
			return DataResult.error(() -> "Unknown party_assist trigger: " + name
				+ " (triggers: " + Arrays.stream(values())
					.map(PartyAssistTrigger::id)
					.collect(Collectors.joining(", ")) + ")");
		}
	}

	enum PartyAssistTargetFilter {
		NEARBY_MEMBERS("nearby_members"),
		WOUNDED_MEMBERS("wounded_members"),
		DROWNING_MEMBERS("drowning_members");

		private final String id;

		PartyAssistTargetFilter(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		static final Codec<PartyAssistTargetFilter> CODEC = Codec.STRING.flatXmap(
			PartyAssistTargetFilter::byName, filter -> DataResult.success(filter.id()));

		private static DataResult<PartyAssistTargetFilter> byName(String name) {
			for (PartyAssistTargetFilter filter : values()) {
				if (filter.id().equals(name)) {
					return DataResult.success(filter);
				}
			}
			return DataResult.error(() -> "Unknown party_assist target_filter: " + name
				+ " (target filters: " + Arrays.stream(values())
					.map(PartyAssistTargetFilter::id)
					.collect(Collectors.joining(", ")) + ")");
		}
	}

	record PartyAssistEffect(
			Holder<MobEffect> effect,
			int durationTicks,
			int amplifier) {

		private static final int MIN_DURATION = 1;
		private static final int MAX_DURATION = 1_000_000;
		private static final int MIN_AMPLIFIER = 0;
		private static final int MAX_AMPLIFIER = 255;

		public PartyAssistEffect {
			effect = Objects.requireNonNull(effect, "effect");
			if (durationTicks < MIN_DURATION || durationTicks > MAX_DURATION) {
				throw new IllegalArgumentException(
					"Party assist effect duration_ticks must be between " + MIN_DURATION + " and " + MAX_DURATION);
			}
			if (amplifier < MIN_AMPLIFIER || amplifier > MAX_AMPLIFIER) {
				throw new IllegalArgumentException(
					"Party assist effect amplifier must be between " + MIN_AMPLIFIER + " and " + MAX_AMPLIFIER);
			}
		}

		static final Codec<PartyAssistEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(PartyAssistEffect::effect),
			Codec.intRange(MIN_DURATION, MAX_DURATION).fieldOf("duration_ticks").forGetter(PartyAssistEffect::durationTicks),
			Codec.intRange(MIN_AMPLIFIER, MAX_AMPLIFIER).optionalFieldOf("amplifier", 0).forGetter(PartyAssistEffect::amplifier)
		).apply(instance, PartyAssistEffect::new));
	}

	/**
	 * Assists nearby, recently contributing Circle members with a small beneficial effect. It is
	 * same-dimension only and cooldowns are scoped to the Circle/behavior so party assists cannot
	 * be spammed by swapping Foci or reforming groups.
	 */
	record PartyAssist(
			PartyAssistTrigger trigger,
			int radius,
			int cooldownTicks,
			PartyAssistTargetFilter targetFilter,
			PartyAssistEffect effect,
			String messageKey) implements FocusBehaviorDef {

		private static final int MIN_RADIUS = 1;
		private static final int MAX_RADIUS = 64;
		private static final int MIN_COOLDOWN = 1;
		private static final int MAX_DURATION = 1_000_000;

		public PartyAssist {
			trigger = Objects.requireNonNull(trigger, "trigger");
			targetFilter = Objects.requireNonNull(targetFilter, "targetFilter");
			effect = Objects.requireNonNull(effect, "effect");
			messageKey = Objects.requireNonNull(messageKey, "messageKey");
			if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
				throw new IllegalArgumentException(
					"Party assist radius must be between " + MIN_RADIUS + " and " + MAX_RADIUS);
			}
			if (cooldownTicks < MIN_COOLDOWN || cooldownTicks > MAX_DURATION) {
				throw new IllegalArgumentException(
					"Party assist cooldown_ticks must be between " + MIN_COOLDOWN + " and " + MAX_DURATION);
			}
			if (messageKey.isBlank()) {
				throw new IllegalArgumentException("Party assist message_key must not be blank");
			}
		}

		static final MapCodec<PartyAssist> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			PartyAssistTrigger.CODEC.fieldOf("trigger").forGetter(PartyAssist::trigger),
			Codec.intRange(MIN_RADIUS, MAX_RADIUS).fieldOf("radius").forGetter(PartyAssist::radius),
			Codec.intRange(MIN_COOLDOWN, MAX_DURATION).fieldOf("cooldown_ticks").forGetter(PartyAssist::cooldownTicks),
			PartyAssistTargetFilter.CODEC.fieldOf("target_filter").forGetter(PartyAssist::targetFilter),
			PartyAssistEffect.CODEC.fieldOf("effect").forGetter(PartyAssist::effect),
			Codec.STRING.fieldOf("message_key").forGetter(PartyAssist::messageKey)
		).apply(instance, PartyAssist::new));

		@Override
		public Type type() {
			return Type.PARTY_ASSIST;
		}
	}

	enum NavigationTargetKind {
		CIRCLE_PING("circle_ping"),
		CIRCLE_LEADER("circle_leader"),
		HELD_LODESTONE_COMPASS("held_lodestone_compass");

		private final String id;

		NavigationTargetKind(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		static final Codec<NavigationTargetKind> CODEC = Codec.STRING.flatXmap(
			NavigationTargetKind::byName, targetKind -> DataResult.success(targetKind.id()));

		private static DataResult<NavigationTargetKind> byName(String name) {
			for (NavigationTargetKind targetKind : values()) {
				if (targetKind.id().equals(name)) {
					return DataResult.success(targetKind);
				}
			}
			return DataResult.error(() -> "Unknown navigation_hint target_kind: " + name
				+ " (target kinds: " + Arrays.stream(values())
					.map(NavigationTargetKind::id)
					.collect(Collectors.joining(", ")) + ")");
		}
	}

	/**
	 * Gives restrained directional feedback toward a server-owned party target or a player-held
	 * vanilla stored point. It never searches the world for structures: targets come from accepted
	 * Circle pings/leaders or an explicit lodestone compass binding.
	 */
	record NavigationHint(
			NavigationTargetKind targetKind,
			int maxAgeTicks,
			boolean sameDimensionOnly,
			int feedbackCadenceTicks) implements FocusBehaviorDef {

		private static final int MIN_AGE = 1;
		private static final int MAX_AGE = 1_000_000;
		private static final int MIN_CADENCE = 20;

		public NavigationHint {
			targetKind = Objects.requireNonNull(targetKind, "targetKind");
			if (maxAgeTicks < MIN_AGE || maxAgeTicks > MAX_AGE) {
				throw new IllegalArgumentException(
					"Navigation hint max_age_ticks must be between " + MIN_AGE + " and " + MAX_AGE);
			}
			if (feedbackCadenceTicks < MIN_CADENCE || feedbackCadenceTicks > MAX_AGE) {
				throw new IllegalArgumentException(
					"Navigation hint feedback_cadence_ticks must be between " + MIN_CADENCE + " and " + MAX_AGE);
			}
		}

		static final MapCodec<NavigationHint> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			NavigationTargetKind.CODEC.fieldOf("target_kind").forGetter(NavigationHint::targetKind),
			Codec.intRange(MIN_AGE, MAX_AGE).fieldOf("max_age_ticks").forGetter(NavigationHint::maxAgeTicks),
			Codec.BOOL.fieldOf("same_dimension_only").forGetter(NavigationHint::sameDimensionOnly),
			Codec.intRange(MIN_CADENCE, MAX_AGE).fieldOf("feedback_cadence_ticks").forGetter(NavigationHint::feedbackCadenceTicks)
		).apply(instance, NavigationHint::new));

		@Override
		public Type type() {
			return Type.NAVIGATION_HINT;
		}
	}

	enum MarkedTargetTrigger {
		CHARGED_MELEE_HIT("charged_melee_hit");

		private final String id;

		MarkedTargetTrigger(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		static final Codec<MarkedTargetTrigger> CODEC = Codec.STRING.flatXmap(
			MarkedTargetTrigger::byName, trigger -> DataResult.success(trigger.id()));

		private static DataResult<MarkedTargetTrigger> byName(String name) {
			for (MarkedTargetTrigger trigger : values()) {
				if (trigger.id().equals(name)) {
					return DataResult.success(trigger);
				}
			}
			return DataResult.error(() -> "Unknown marked_target trigger: " + name
				+ " (triggers: " + Arrays.stream(values())
					.map(MarkedTargetTrigger::id)
					.collect(Collectors.joining(", ")) + ")");
		}
	}

	record EffectOnConsume(
			Holder<MobEffect> effect,
			int amplifier,
			int durationTicks,
			boolean targetSelf) {

		private static final int MIN_AMPLIFIER = 0;
		private static final int MAX_AMPLIFIER = 255;
		private static final int MIN_DURATION = 1;
		private static final int MAX_DURATION = 1_000_000;

		public EffectOnConsume {
			effect = Objects.requireNonNull(effect, "effect");
			if (amplifier < MIN_AMPLIFIER || amplifier > MAX_AMPLIFIER) {
				throw new IllegalArgumentException(
					"Marked target consume amplifier must be between " + MIN_AMPLIFIER + " and " + MAX_AMPLIFIER);
			}
			if (durationTicks < MIN_DURATION || durationTicks > MAX_DURATION) {
				throw new IllegalArgumentException(
					"Marked target consume duration_ticks must be between " + MIN_DURATION + " and " + MAX_DURATION);
			}
		}

		static final Codec<EffectOnConsume> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(EffectOnConsume::effect),
			Codec.intRange(MIN_AMPLIFIER, MAX_AMPLIFIER).optionalFieldOf("amplifier", 0).forGetter(EffectOnConsume::amplifier),
			Codec.intRange(MIN_DURATION, MAX_DURATION).fieldOf("duration_ticks").forGetter(EffectOnConsume::durationTicks),
			Codec.BOOL.optionalFieldOf("target_self", false).forGetter(EffectOnConsume::targetSelf)
		).apply(instance, EffectOnConsume::new));
	}

	/**
	 * Primes a short-lived server-side mark on a valid combat trigger, then consumes it on another
	 * valid trigger to apply {@code effectOnConsume}. The runtime is keyed by behavior, attacker,
	 * and target so two marked-target behaviours never share marks or cooldowns.
	 */
	record MarkedTarget(
			MarkedTargetTrigger primeTrigger,
			MarkedTargetTrigger consumeTrigger,
			int markDurationTicks,
			int cooldownTicks,
			EffectOnConsume effectOnConsume) implements FocusBehaviorDef {

		private static final int MIN_DURATION = 1;
		private static final int MAX_DURATION = 1_000_000;
		private static final int MIN_COOLDOWN = 1;

		public MarkedTarget {
			primeTrigger = Objects.requireNonNull(primeTrigger, "primeTrigger");
			consumeTrigger = Objects.requireNonNull(consumeTrigger, "consumeTrigger");
			effectOnConsume = Objects.requireNonNull(effectOnConsume, "effectOnConsume");
			if (markDurationTicks < MIN_DURATION || markDurationTicks > MAX_DURATION) {
				throw new IllegalArgumentException(
					"Marked target mark_duration_ticks must be between " + MIN_DURATION + " and " + MAX_DURATION);
			}
			if (cooldownTicks < MIN_COOLDOWN || cooldownTicks > MAX_DURATION) {
				throw new IllegalArgumentException(
					"Marked target cooldown_ticks must be between " + MIN_COOLDOWN + " and " + MAX_DURATION);
			}
		}

		static final MapCodec<MarkedTarget> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			MarkedTargetTrigger.CODEC.fieldOf("prime_trigger").forGetter(MarkedTarget::primeTrigger),
			MarkedTargetTrigger.CODEC.fieldOf("consume_trigger").forGetter(MarkedTarget::consumeTrigger),
			Codec.intRange(MIN_DURATION, MAX_DURATION).fieldOf("mark_duration_ticks").forGetter(MarkedTarget::markDurationTicks),
			Codec.intRange(MIN_COOLDOWN, MAX_DURATION).fieldOf("cooldown_ticks").forGetter(MarkedTarget::cooldownTicks),
			EffectOnConsume.CODEC.fieldOf("effect_on_consume").forGetter(MarkedTarget::effectOnConsume)
		).apply(instance, MarkedTarget::new));

		@Override
		public Type type() {
			return Type.MARKED_TARGET;
		}
	}
}
