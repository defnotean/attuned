package dev.attuned.api.focus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;

/**
 * A datapack-defined, parameterized Focus behavior instance — the "behavior palette".
 * Loaded from the synced registry at {@code data/<ns>/attuned/focus_behavior/<id>.json}.
 *
 * <p>Each definition is one of a small set of palette {@link Type types}, dispatched on a
 * {@code "type"} field. The shipped palette is passive-only (no Focus Ability) and covers four
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
		ATTRIBUTE_WHILE("attuned:attribute_while", AttributeWhile.MAP_CODEC);

		private final String id;
		private final MapCodec<? extends FocusBehaviorDef> codec;

		Type(String id, MapCodec<? extends FocusBehaviorDef> codec) {
			this.id = id;
			this.codec = codec;
		}

		public String id() {
			return id;
		}

		public MapCodec<? extends FocusBehaviorDef> codec() {
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
			+ " (palette types: attuned:conditional_mob_effect, attuned:on_hit_effect,"
			+ " attuned:periodic_effect, attuned:attribute_while)");
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
			if (refreshTicks < MIN_REFRESH) {
				throw new IllegalArgumentException(
					"Conditional effect refresh_ticks must be at least " + MIN_REFRESH);
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
			if (refreshTicks < MIN_REFRESH) {
				throw new IllegalArgumentException(
					"Periodic effect refresh_ticks must be at least " + MIN_REFRESH);
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
}
