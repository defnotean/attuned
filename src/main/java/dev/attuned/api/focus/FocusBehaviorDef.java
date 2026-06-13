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
 * {@code "type"} field. v1 ships a single type, {@code attuned:conditional_mob_effect}: it
 * keeps a mob effect refreshed on the wearer for as long as a {@link FocusCondition} holds.
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
		CONDITIONAL_MOB_EFFECT("attuned:conditional_mob_effect", ConditionalMobEffect.MAP_CODEC);

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
			+ " (palette types: attuned:conditional_mob_effect)");
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
}
