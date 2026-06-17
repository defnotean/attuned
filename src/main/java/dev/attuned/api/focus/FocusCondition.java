package dev.attuned.api.focus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

/**
 * A small, composable predicate over a player's situation, used by datapack-defined
 * Focus behaviors (the behavior palette) to gate when a parameterized effect applies.
 *
 * <p>Every variant is a {@link Type} with its own parameters and a {@code MapCodec}.
 * The condition codec dispatches on a {@code "condition"} string field, so JSON looks
 * like {@code {"condition": "low_light", "max_light": 7}}. The decision logic for each
 * variant is split into a pure static helper ({@code lowLightHolds}, {@code blockTagHolds},
 * …) so the truth tables can be exercised without a running Minecraft.
 *
 * <p>Conditions never throw; an undecidable variant evaluates to {@code false}.
 */
public sealed interface FocusCondition {

	/** Evaluates this condition for the given active-Focus player on the server. */
	boolean test(ServerPlayer player);

	/** The dispatch key written as the {@code "condition"} field in JSON. */
	Type type();

	/** The serialized variant tags, dispatched on the {@code "condition"} field. */
	enum Type {
		IN_RAIN("in_rain", InRain.MAP_CODEC),
		UNDERWATER("underwater", Underwater.MAP_CODEC),
		LOW_LIGHT("low_light", LowLight.MAP_CODEC),
		SNEAKING("sneaking", Sneaking.MAP_CODEC),
		ON_BLOCK_TAG("on_block_tag", OnBlockTag.MAP_CODEC),
		IN_BIOME_TAG("in_biome_tag", InBiomeTag.MAP_CODEC);

		private final String id;
		private final Codec<? extends FocusCondition> codec;

		Type(String id, MapCodec<? extends FocusCondition> codec) {
			this.id = id;
			this.codec = codec.codec();
		}

		public String id() {
			return id;
		}

		public Codec<? extends FocusCondition> codec() {
			return codec;
		}
	}

	/** Default light level at or below which {@link LowLight} holds. */
	int DEFAULT_MAX_LIGHT = 7;

	Codec<Type> TYPE_CODEC = Codec.STRING.flatXmap(FocusCondition::typeByName, type -> com.mojang.serialization.DataResult.success(type.id()));

	/** Dispatch codec keyed on the {@code "condition"} field, one entry per {@link Type}. */
	Codec<FocusCondition> CODEC = TYPE_CODEC.dispatch("condition", FocusCondition::type, Type::codec);

	private static com.mojang.serialization.DataResult<Type> typeByName(String name) {
		for (Type type : Type.values()) {
			if (type.id().equals(name)) {
				return com.mojang.serialization.DataResult.success(type);
			}
		}
		return com.mojang.serialization.DataResult.error(() -> "Unknown Focus condition: " + name);
	}

	// ---- Variants ---------------------------------------------------------

	/** Holds while the player is standing in rain (or otherwise wet from the sky). */
	record InRain() implements FocusCondition {
		static final MapCodec<InRain> MAP_CODEC = MapCodec.unit(InRain::new);

		@Override
		public boolean test(ServerPlayer player) {
			var level = player.level();
			return level.isRainingAt(player.blockPosition()) || player.isInWaterOrRain();
		}

		@Override
		public Type type() {
			return Type.IN_RAIN;
		}
	}

	/** Holds while the player's head is submerged. */
	record Underwater() implements FocusCondition {
		static final MapCodec<Underwater> MAP_CODEC = MapCodec.unit(Underwater::new);

		@Override
		public boolean test(ServerPlayer player) {
			return player.isUnderWater();
		}

		@Override
		public Type type() {
			return Type.UNDERWATER;
		}
	}

	/** Holds while the local light level is at or below {@code maxLight}. */
	record LowLight(int maxLight) implements FocusCondition {
		static final MapCodec<LowLight> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(0, 15).optionalFieldOf("max_light", DEFAULT_MAX_LIGHT).forGetter(LowLight::maxLight)
		).apply(instance, LowLight::new));

		@Override
		public boolean test(ServerPlayer player) {
			return lowLightHolds(player.level().getMaxLocalRawBrightness(player.blockPosition()), maxLight);
		}

		@Override
		public Type type() {
			return Type.LOW_LIGHT;
		}
	}

	/** Holds while the player is crouching. */
	record Sneaking() implements FocusCondition {
		static final MapCodec<Sneaking> MAP_CODEC = MapCodec.unit(Sneaking::new);

		@Override
		public boolean test(ServerPlayer player) {
			return player.isCrouching();
		}

		@Override
		public Type type() {
			return Type.SNEAKING;
		}
	}

	/** Holds while the block at the player's feet matches the given block tag. */
	record OnBlockTag(ResourceLocation tag) implements FocusCondition {
		static final MapCodec<OnBlockTag> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ResourceLocation.CODEC.fieldOf("tag").forGetter(OnBlockTag::tag)
		).apply(instance, OnBlockTag::new));

		public OnBlockTag {
			tag = Objects.requireNonNull(tag, "tag");
		}

		@Override
		public boolean test(ServerPlayer player) {
			TagKey<Block> key = TagKey.create(Registries.BLOCK, tag);
			return blockTagHolds(player.level().getBlockState(player.blockPosition()).is(key));
		}

		@Override
		public Type type() {
			return Type.ON_BLOCK_TAG;
		}
	}

	/** Holds while the player stands in a biome that matches the given biome tag. */
	record InBiomeTag(ResourceLocation tag) implements FocusCondition {
		static final MapCodec<InBiomeTag> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ResourceLocation.CODEC.fieldOf("tag").forGetter(InBiomeTag::tag)
		).apply(instance, InBiomeTag::new));

		public InBiomeTag {
			tag = Objects.requireNonNull(tag, "tag");
		}

		@Override
		public boolean test(ServerPlayer player) {
			TagKey<Biome> key = TagKey.create(Registries.BIOME, tag);
			return biomeTagHolds(player.level().getBiome(player.blockPosition()).is(key));
		}

		@Override
		public Type type() {
			return Type.IN_BIOME_TAG;
		}
	}

	// ---- Pure decision helpers (truth-table testable, Minecraft-free) ------

	/** True when {@code lightLevel} is at or below the configured {@code maxLight}. */
	static boolean lowLightHolds(int lightLevel, int maxLight) {
		return lightLevel <= maxLight;
	}

	/** Identity wrapper so the block-tag decision is named and testable. */
	static boolean blockTagHolds(boolean footBlockMatchesTag) {
		return footBlockMatchesTag;
	}

	/** Identity wrapper so the biome-tag decision is named and testable. */
	static boolean biomeTagHolds(boolean biomeMatchesTag) {
		return biomeMatchesTag;
	}
}
