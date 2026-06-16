package dev.attuned;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-side configuration for Attuned, loaded once from
 * {@code config/attuned.json} during mod initialization.
 *
 * <p>Every key is optional: a missing one falls back to {@link #DEFAULT}, so the
 * file stays forward-compatible as new keys are added. A malformed file is
 * reported in the log and ignored rather than crashing the game. After a
 * successful load the file is rewritten in canonical form so it always reflects
 * the current schema.
 */
public record AttunedConfig(
		int startingCapacity,
		int capacityCap,
		int capacityPerShard,
		float focusLootChance,
		float lowLootMultiplier,
		float commonLootMultiplier,
		float richLootMultiplier,
		float treasureLootMultiplier,
		float shardFragmentLootMultiplier,
		int voidstepCooldownTicks,
		int gravebindCooldownTicks,
		boolean broadcastPactDeaths,
		int surgeIntervalTicks,
		int surgeDurationTicks,
		int surgeRadius,
		float advantageMultiplier,
		float disadvantageMultiplier,
		float discordDamageMultiplier,
		float resonanceHitEmpoweredGainPerDamage,
		float resonanceHitNeutralizedLoss,
		float resonanceKillEmpoweredGain,
		float resonanceDecayPerTick,
		int affinityLoomBaseShardCost,
		int affinityLoomMaxShardCost) {

	private static final int MIN_STARTING_CAPACITY = 0;
	private static final int MAX_STARTING_CAPACITY = 256;
	private static final int MIN_CAPACITY_CAP = 1;
	private static final int MAX_CAPACITY_CAP = 256;
	private static final int MIN_CAPACITY_PER_SHARD = 1;
	private static final int MAX_CAPACITY_PER_SHARD = 64;
	private static final float MIN_FOCUS_LOOT_CHANCE = 0.0F;
	private static final float MAX_FOCUS_LOOT_CHANCE = 1.0F;
	private static final float MIN_LOOT_MULTIPLIER = 0.0F;
	private static final float MAX_LOOT_MULTIPLIER = 128.0F;
	private static final int MIN_COOLDOWN_TICKS = 0;
	private static final int MAX_COOLDOWN_TICKS = 1728000;
	private static final int MIN_SURGE_INTERVAL_TICKS = 200;
	private static final int MAX_SURGE_INTERVAL_TICKS = 1728000;
	private static final int MIN_SURGE_DURATION_TICKS = 200;
	private static final int MAX_SURGE_DURATION_TICKS = 72000;
	private static final int MIN_SURGE_RADIUS = 4;
	private static final int MAX_SURGE_RADIUS = 64;
	private static final float MIN_ADVANTAGE_MULTIPLIER = 1.0F;
	private static final float MAX_ADVANTAGE_MULTIPLIER = 3.0F;
	private static final float MIN_DISADVANTAGE_MULTIPLIER = 0.1F;
	private static final float MAX_DISADVANTAGE_MULTIPLIER = 1.0F;
	private static final float MIN_DISCORD_DAMAGE_MULTIPLIER = 1.0F;
	private static final float MAX_DISCORD_DAMAGE_MULTIPLIER = 3.0F;
	private static final float MIN_RESONANCE_HIT_EMPOWERED_GAIN_PER_DAMAGE = 0.001F;
	private static final float MAX_RESONANCE_HIT_EMPOWERED_GAIN_PER_DAMAGE = 0.1F;
	private static final float MIN_RESONANCE_HIT_NEUTRALIZED_LOSS = 0.01F;
	private static final float MAX_RESONANCE_HIT_NEUTRALIZED_LOSS = 0.5F;
	private static final float MIN_RESONANCE_KILL_EMPOWERED_GAIN = 0.05F;
	private static final float MAX_RESONANCE_KILL_EMPOWERED_GAIN = 1.0F;
	private static final float MIN_RESONANCE_DECAY_PER_TICK = 0.00001F;
	private static final float MAX_RESONANCE_DECAY_PER_TICK = 0.01F;
	private static final int MIN_AFFINITY_LOOM_SHARD_COST = 1;
	private static final int MAX_AFFINITY_LOOM_SHARD_COST = 64;

	public AttunedConfig {
		startingCapacity = requireIntRange(
			"startingCapacity", startingCapacity, MIN_STARTING_CAPACITY, MAX_STARTING_CAPACITY);
		capacityCap = requireIntRange("capacityCap", capacityCap, MIN_CAPACITY_CAP, MAX_CAPACITY_CAP);
		capacityPerShard = requireIntRange(
			"capacityPerShard", capacityPerShard, MIN_CAPACITY_PER_SHARD, MAX_CAPACITY_PER_SHARD);
		focusLootChance = requireFloatRange(
			"focusLootChance", focusLootChance, MIN_FOCUS_LOOT_CHANCE, MAX_FOCUS_LOOT_CHANCE);
		lowLootMultiplier = requireLootMultiplier("lowLootMultiplier", lowLootMultiplier);
		commonLootMultiplier = requireLootMultiplier("commonLootMultiplier", commonLootMultiplier);
		richLootMultiplier = requireLootMultiplier("richLootMultiplier", richLootMultiplier);
		treasureLootMultiplier = requireLootMultiplier("treasureLootMultiplier", treasureLootMultiplier);
		shardFragmentLootMultiplier = requireLootMultiplier(
			"shardFragmentLootMultiplier", shardFragmentLootMultiplier);
		voidstepCooldownTicks = requireCooldownTicks("voidstepCooldownTicks", voidstepCooldownTicks);
		gravebindCooldownTicks = requireCooldownTicks("gravebindCooldownTicks", gravebindCooldownTicks);
		surgeIntervalTicks = requireIntRange(
			"surgeIntervalTicks", surgeIntervalTicks, MIN_SURGE_INTERVAL_TICKS, MAX_SURGE_INTERVAL_TICKS);
		surgeDurationTicks = requireIntRange(
			"surgeDurationTicks", surgeDurationTicks, MIN_SURGE_DURATION_TICKS, MAX_SURGE_DURATION_TICKS);
		surgeRadius = requireIntRange("surgeRadius", surgeRadius, MIN_SURGE_RADIUS, MAX_SURGE_RADIUS);
		advantageMultiplier = requireFloatRange(
			"advantageMultiplier", advantageMultiplier, MIN_ADVANTAGE_MULTIPLIER, MAX_ADVANTAGE_MULTIPLIER);
		disadvantageMultiplier = requireFloatRange(
			"disadvantageMultiplier", disadvantageMultiplier,
			MIN_DISADVANTAGE_MULTIPLIER, MAX_DISADVANTAGE_MULTIPLIER);
		discordDamageMultiplier = requireFloatRange(
			"discordDamageMultiplier", discordDamageMultiplier,
			MIN_DISCORD_DAMAGE_MULTIPLIER, MAX_DISCORD_DAMAGE_MULTIPLIER);
		resonanceHitEmpoweredGainPerDamage = requireFloatRange(
			"resonanceHitEmpoweredGainPerDamage", resonanceHitEmpoweredGainPerDamage,
			MIN_RESONANCE_HIT_EMPOWERED_GAIN_PER_DAMAGE, MAX_RESONANCE_HIT_EMPOWERED_GAIN_PER_DAMAGE);
		resonanceHitNeutralizedLoss = requireFloatRange(
			"resonanceHitNeutralizedLoss", resonanceHitNeutralizedLoss,
			MIN_RESONANCE_HIT_NEUTRALIZED_LOSS, MAX_RESONANCE_HIT_NEUTRALIZED_LOSS);
		resonanceKillEmpoweredGain = requireFloatRange(
			"resonanceKillEmpoweredGain", resonanceKillEmpoweredGain,
			MIN_RESONANCE_KILL_EMPOWERED_GAIN, MAX_RESONANCE_KILL_EMPOWERED_GAIN);
		resonanceDecayPerTick = requireFloatRange(
			"resonanceDecayPerTick", resonanceDecayPerTick,
			MIN_RESONANCE_DECAY_PER_TICK, MAX_RESONANCE_DECAY_PER_TICK);
		affinityLoomBaseShardCost = requireIntRange(
			"affinityLoomBaseShardCost", affinityLoomBaseShardCost,
			MIN_AFFINITY_LOOM_SHARD_COST, MAX_AFFINITY_LOOM_SHARD_COST);
		affinityLoomMaxShardCost = requireIntRange(
			"affinityLoomMaxShardCost", affinityLoomMaxShardCost,
			MIN_AFFINITY_LOOM_SHARD_COST, MAX_AFFINITY_LOOM_SHARD_COST);
		if (affinityLoomMaxShardCost < affinityLoomBaseShardCost) {
			throw new IllegalArgumentException(
				"affinityLoomMaxShardCost must be >= affinityLoomBaseShardCost");
		}
		startingCapacity = Math.min(startingCapacity, capacityCap);
	}

	/** The built-in defaults — also the fallback for any missing key. */
	public static final AttunedConfig DEFAULT =
		new AttunedConfig(
			4, 20, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200, true, 12000, 1200, 16,
			1.33F, 0.75F, 1.20F, 0.012F, 0.10F, 0.30F, 0.00025F, 1, 3);

	private static final Codec<Float> LOOT_MULTIPLIER =
		Codec.floatRange(MIN_LOOT_MULTIPLIER, MAX_LOOT_MULTIPLIER);

	/**
	 * Codec split across two helper records: {@code RecordCodecBuilder.group}
	 * accepts at most sixteen fields, and the full config has twenty-four.
	 * Nested codecs keep the on-disk JSON flat — all keys remain top-level.
	 */
	private record CoreCodecFields(
			int startingCapacity,
			int capacityCap,
			int capacityPerShard,
			float focusLootChance,
			float lowLootMultiplier,
			float commonLootMultiplier,
			float richLootMultiplier,
			float treasureLootMultiplier,
			float shardFragmentLootMultiplier,
			int voidstepCooldownTicks,
			int gravebindCooldownTicks,
			boolean broadcastPactDeaths,
			int surgeIntervalTicks,
			int surgeDurationTicks,
			int surgeRadius) {

		private static final MapCodec<CoreCodecFields> MAP_CODEC = RecordCodecBuilder.mapCodec(in -> in.group(
			Codec.intRange(MIN_STARTING_CAPACITY, MAX_STARTING_CAPACITY)
				.optionalFieldOf("starting_capacity", DEFAULT.startingCapacity())
				.forGetter(CoreCodecFields::startingCapacity),
			Codec.intRange(MIN_CAPACITY_CAP, MAX_CAPACITY_CAP)
				.optionalFieldOf("capacity_cap", DEFAULT.capacityCap())
				.forGetter(CoreCodecFields::capacityCap),
			Codec.intRange(MIN_CAPACITY_PER_SHARD, MAX_CAPACITY_PER_SHARD)
				.optionalFieldOf("capacity_per_shard", DEFAULT.capacityPerShard())
				.forGetter(CoreCodecFields::capacityPerShard),
			Codec.floatRange(MIN_FOCUS_LOOT_CHANCE, MAX_FOCUS_LOOT_CHANCE)
				.optionalFieldOf("focus_loot_chance", DEFAULT.focusLootChance())
				.forGetter(CoreCodecFields::focusLootChance),
			LOOT_MULTIPLIER.optionalFieldOf("low_loot_multiplier", DEFAULT.lowLootMultiplier())
				.forGetter(CoreCodecFields::lowLootMultiplier),
			LOOT_MULTIPLIER.optionalFieldOf("common_loot_multiplier", DEFAULT.commonLootMultiplier())
				.forGetter(CoreCodecFields::commonLootMultiplier),
			LOOT_MULTIPLIER.optionalFieldOf("rich_loot_multiplier", DEFAULT.richLootMultiplier())
				.forGetter(CoreCodecFields::richLootMultiplier),
			LOOT_MULTIPLIER.optionalFieldOf("treasure_loot_multiplier", DEFAULT.treasureLootMultiplier())
				.forGetter(CoreCodecFields::treasureLootMultiplier),
			LOOT_MULTIPLIER.optionalFieldOf("shard_fragment_loot_multiplier", DEFAULT.shardFragmentLootMultiplier())
				.forGetter(CoreCodecFields::shardFragmentLootMultiplier),
			Codec.intRange(MIN_COOLDOWN_TICKS, MAX_COOLDOWN_TICKS)
				.optionalFieldOf("voidstep_cooldown_ticks", DEFAULT.voidstepCooldownTicks())
				.forGetter(CoreCodecFields::voidstepCooldownTicks),
			Codec.intRange(MIN_COOLDOWN_TICKS, MAX_COOLDOWN_TICKS)
				.optionalFieldOf("gravebind_cooldown_ticks", DEFAULT.gravebindCooldownTicks())
				.forGetter(CoreCodecFields::gravebindCooldownTicks),
			Codec.BOOL.optionalFieldOf("broadcast_pact_deaths", DEFAULT.broadcastPactDeaths())
				.forGetter(CoreCodecFields::broadcastPactDeaths),
			Codec.intRange(MIN_SURGE_INTERVAL_TICKS, MAX_SURGE_INTERVAL_TICKS)
				.optionalFieldOf("surge_interval_ticks", DEFAULT.surgeIntervalTicks())
				.forGetter(CoreCodecFields::surgeIntervalTicks),
			Codec.intRange(MIN_SURGE_DURATION_TICKS, MAX_SURGE_DURATION_TICKS)
				.optionalFieldOf("surge_duration_ticks", DEFAULT.surgeDurationTicks())
				.forGetter(CoreCodecFields::surgeDurationTicks),
			Codec.intRange(MIN_SURGE_RADIUS, MAX_SURGE_RADIUS)
				.optionalFieldOf("surge_radius", DEFAULT.surgeRadius())
				.forGetter(CoreCodecFields::surgeRadius)
		).apply(in, CoreCodecFields::new));

		private static CoreCodecFields from(AttunedConfig config) {
			return new CoreCodecFields(
				config.startingCapacity(),
				config.capacityCap(),
				config.capacityPerShard(),
				config.focusLootChance(),
				config.lowLootMultiplier(),
				config.commonLootMultiplier(),
				config.richLootMultiplier(),
				config.treasureLootMultiplier(),
				config.shardFragmentLootMultiplier(),
				config.voidstepCooldownTicks(),
				config.gravebindCooldownTicks(),
				config.broadcastPactDeaths(),
				config.surgeIntervalTicks(),
				config.surgeDurationTicks(),
				config.surgeRadius());
		}
	}

	private record CombatCodecFields(
			float advantageMultiplier,
			float disadvantageMultiplier,
			float discordDamageMultiplier,
			float resonanceHitEmpoweredGainPerDamage,
			float resonanceHitNeutralizedLoss,
			float resonanceKillEmpoweredGain,
			float resonanceDecayPerTick) {

		private static final MapCodec<CombatCodecFields> MAP_CODEC = RecordCodecBuilder.mapCodec(in -> in.group(
			Codec.floatRange(MIN_ADVANTAGE_MULTIPLIER, MAX_ADVANTAGE_MULTIPLIER)
				.optionalFieldOf("advantage_multiplier", DEFAULT.advantageMultiplier())
				.forGetter(CombatCodecFields::advantageMultiplier),
			Codec.floatRange(MIN_DISADVANTAGE_MULTIPLIER, MAX_DISADVANTAGE_MULTIPLIER)
				.optionalFieldOf("disadvantage_multiplier", DEFAULT.disadvantageMultiplier())
				.forGetter(CombatCodecFields::disadvantageMultiplier),
			Codec.floatRange(MIN_DISCORD_DAMAGE_MULTIPLIER, MAX_DISCORD_DAMAGE_MULTIPLIER)
				.optionalFieldOf("discord_damage_multiplier", DEFAULT.discordDamageMultiplier())
				.forGetter(CombatCodecFields::discordDamageMultiplier),
			Codec.floatRange(MIN_RESONANCE_HIT_EMPOWERED_GAIN_PER_DAMAGE, MAX_RESONANCE_HIT_EMPOWERED_GAIN_PER_DAMAGE)
				.optionalFieldOf("resonance_hit_empowered_gain_per_damage", DEFAULT.resonanceHitEmpoweredGainPerDamage())
				.forGetter(CombatCodecFields::resonanceHitEmpoweredGainPerDamage),
			Codec.floatRange(MIN_RESONANCE_HIT_NEUTRALIZED_LOSS, MAX_RESONANCE_HIT_NEUTRALIZED_LOSS)
				.optionalFieldOf("resonance_hit_neutralized_loss", DEFAULT.resonanceHitNeutralizedLoss())
				.forGetter(CombatCodecFields::resonanceHitNeutralizedLoss),
			Codec.floatRange(MIN_RESONANCE_KILL_EMPOWERED_GAIN, MAX_RESONANCE_KILL_EMPOWERED_GAIN)
				.optionalFieldOf("resonance_kill_empowered_gain", DEFAULT.resonanceKillEmpoweredGain())
				.forGetter(CombatCodecFields::resonanceKillEmpoweredGain),
			Codec.floatRange(MIN_RESONANCE_DECAY_PER_TICK, MAX_RESONANCE_DECAY_PER_TICK)
				.optionalFieldOf("resonance_decay_per_tick", DEFAULT.resonanceDecayPerTick())
				.forGetter(CombatCodecFields::resonanceDecayPerTick)
		).apply(in, CombatCodecFields::new));

		private static CombatCodecFields from(AttunedConfig config) {
			return new CombatCodecFields(
				config.advantageMultiplier(),
				config.disadvantageMultiplier(),
				config.discordDamageMultiplier(),
				config.resonanceHitEmpoweredGainPerDamage(),
				config.resonanceHitNeutralizedLoss(),
				config.resonanceKillEmpoweredGain(),
				config.resonanceDecayPerTick());
		}
	}

	private record LoomCodecFields(int affinityLoomBaseShardCost, int affinityLoomMaxShardCost) {

		private static final MapCodec<LoomCodecFields> MAP_CODEC = RecordCodecBuilder.mapCodec(in -> in.group(
			Codec.intRange(MIN_AFFINITY_LOOM_SHARD_COST, MAX_AFFINITY_LOOM_SHARD_COST)
				.optionalFieldOf("affinity_loom_base_shard_cost", DEFAULT.affinityLoomBaseShardCost())
				.forGetter(LoomCodecFields::affinityLoomBaseShardCost),
			Codec.intRange(MIN_AFFINITY_LOOM_SHARD_COST, MAX_AFFINITY_LOOM_SHARD_COST)
				.optionalFieldOf("affinity_loom_max_shard_cost", DEFAULT.affinityLoomMaxShardCost())
				.forGetter(LoomCodecFields::affinityLoomMaxShardCost)
		).apply(in, LoomCodecFields::new));

		private static LoomCodecFields from(AttunedConfig config) {
			return new LoomCodecFields(
				config.affinityLoomBaseShardCost(),
				config.affinityLoomMaxShardCost());
		}
	}

	public static final Codec<AttunedConfig> CODEC = RecordCodecBuilder.create(in -> in.group(
		CoreCodecFields.MAP_CODEC.forGetter(CoreCodecFields::from),
		CombatCodecFields.MAP_CODEC.forGetter(CombatCodecFields::from),
		LoomCodecFields.MAP_CODEC.forGetter(LoomCodecFields::from)
	).apply(in, AttunedConfig::mergeCodecFields));

	private static AttunedConfig mergeCodecFields(
			CoreCodecFields core, CombatCodecFields combat, LoomCodecFields loom) {
		return new AttunedConfig(
		core.startingCapacity(),
		core.capacityCap(),
		core.capacityPerShard(),
		core.focusLootChance(),
		core.lowLootMultiplier(),
		core.commonLootMultiplier(),
		core.richLootMultiplier(),
		core.treasureLootMultiplier(),
		core.shardFragmentLootMultiplier(),
		core.voidstepCooldownTicks(),
		core.gravebindCooldownTicks(),
		core.broadcastPactDeaths(),
		core.surgeIntervalTicks(),
		core.surgeDurationTicks(),
		core.surgeRadius(),
		combat.advantageMultiplier(),
		combat.disadvantageMultiplier(),
		combat.discordDamageMultiplier(),
		combat.resonanceHitEmpoweredGainPerDamage(),
		combat.resonanceHitNeutralizedLoss(),
		combat.resonanceKillEmpoweredGain(),
		combat.resonanceDecayPerTick(),
		loom.affinityLoomBaseShardCost(),
		loom.affinityLoomMaxShardCost());
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static AttunedConfig current = DEFAULT;

	/** The active configuration — {@link #DEFAULT} until {@link #load()} runs. */
	public static AttunedConfig get() {
		return current;
	}

	/** Loads {@code config/attuned.json}, writing a fresh default file if none exists. */
	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try {
				JsonElement json = JsonParser.parseString(Files.readString(path));
				current = CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
			} catch (Exception e) {
				Attuned.LOGGER.error("Invalid {} — using defaults; fix or delete the file.", path, e);
				current = DEFAULT;
				return; // Leave the broken file untouched so the player can repair it.
			}
		} else {
			current = DEFAULT;
		}
		save();
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("attuned.json");
	}

	private static int requireCooldownTicks(String field, int value) {
		return requireIntRange(field, value, MIN_COOLDOWN_TICKS, MAX_COOLDOWN_TICKS);
	}

	private static int requireIntRange(String field, int value, int min, int max) {
		if (value < min || value > max) {
			throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
		}
		return value;
	}

	private static float requireLootMultiplier(String field, float value) {
		return requireFloatRange(field, value, MIN_LOOT_MULTIPLIER, MAX_LOOT_MULTIPLIER);
	}

	private static float requireFloatRange(String field, float value, float min, float max) {
		if (!Float.isFinite(value) || value < min || value > max) {
			throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
		}
		return value;
	}

	private static void save() {
		// Written field-by-field rather than through the CODEC: optionalFieldOf
		// omits any field still at its default when encoding, which would leave a
		// fresh config file empty ({}). Listing the keys keeps the file complete.
		JsonObject json = new JsonObject();
		json.addProperty("starting_capacity", current.startingCapacity());
		json.addProperty("capacity_cap", current.capacityCap());
		json.addProperty("capacity_per_shard", current.capacityPerShard());
		json.addProperty("focus_loot_chance", current.focusLootChance());
		json.addProperty("low_loot_multiplier", current.lowLootMultiplier());
		json.addProperty("common_loot_multiplier", current.commonLootMultiplier());
		json.addProperty("rich_loot_multiplier", current.richLootMultiplier());
		json.addProperty("treasure_loot_multiplier", current.treasureLootMultiplier());
		json.addProperty("shard_fragment_loot_multiplier", current.shardFragmentLootMultiplier());
		json.addProperty("voidstep_cooldown_ticks", current.voidstepCooldownTicks());
		json.addProperty("gravebind_cooldown_ticks", current.gravebindCooldownTicks());
		json.addProperty("broadcast_pact_deaths", current.broadcastPactDeaths());
		json.addProperty("surge_interval_ticks", current.surgeIntervalTicks());
		json.addProperty("surge_duration_ticks", current.surgeDurationTicks());
		json.addProperty("surge_radius", current.surgeRadius());
		json.addProperty("advantage_multiplier", current.advantageMultiplier());
		json.addProperty("disadvantage_multiplier", current.disadvantageMultiplier());
		json.addProperty("discord_damage_multiplier", current.discordDamageMultiplier());
		json.addProperty("resonance_hit_empowered_gain_per_damage", current.resonanceHitEmpoweredGainPerDamage());
		json.addProperty("resonance_hit_neutralized_loss", current.resonanceHitNeutralizedLoss());
		json.addProperty("resonance_kill_empowered_gain", current.resonanceKillEmpoweredGain());
		json.addProperty("resonance_decay_per_tick", current.resonanceDecayPerTick());
		json.addProperty("affinity_loom_base_shard_cost", current.affinityLoomBaseShardCost());
		json.addProperty("affinity_loom_max_shard_cost", current.affinityLoomMaxShardCost());
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(json));
		} catch (Exception e) {
			Attuned.LOGGER.error("Could not write {}", path, e);
		}
	}
}
