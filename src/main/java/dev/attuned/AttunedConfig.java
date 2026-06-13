package dev.attuned;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
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
		int surgeRadius) {

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
		startingCapacity = Math.min(startingCapacity, capacityCap);
	}

	/** The built-in defaults — also the fallback for any missing key. */
	public static final AttunedConfig DEFAULT =
		new AttunedConfig(4, 20, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200, true, 12000, 1200, 16);

	private static final Codec<Float> LOOT_MULTIPLIER =
		Codec.floatRange(MIN_LOOT_MULTIPLIER, MAX_LOOT_MULTIPLIER);

	public static final Codec<AttunedConfig> CODEC = RecordCodecBuilder.create(in -> in.group(
		Codec.intRange(MIN_STARTING_CAPACITY, MAX_STARTING_CAPACITY)
			.optionalFieldOf("starting_capacity", DEFAULT.startingCapacity())
			.forGetter(AttunedConfig::startingCapacity),
		Codec.intRange(MIN_CAPACITY_CAP, MAX_CAPACITY_CAP)
			.optionalFieldOf("capacity_cap", DEFAULT.capacityCap())
			.forGetter(AttunedConfig::capacityCap),
		Codec.intRange(MIN_CAPACITY_PER_SHARD, MAX_CAPACITY_PER_SHARD)
			.optionalFieldOf("capacity_per_shard", DEFAULT.capacityPerShard())
			.forGetter(AttunedConfig::capacityPerShard),
		Codec.floatRange(MIN_FOCUS_LOOT_CHANCE, MAX_FOCUS_LOOT_CHANCE)
			.optionalFieldOf("focus_loot_chance", DEFAULT.focusLootChance())
			.forGetter(AttunedConfig::focusLootChance),
		LOOT_MULTIPLIER.optionalFieldOf("low_loot_multiplier", DEFAULT.lowLootMultiplier())
			.forGetter(AttunedConfig::lowLootMultiplier),
		LOOT_MULTIPLIER.optionalFieldOf("common_loot_multiplier", DEFAULT.commonLootMultiplier())
			.forGetter(AttunedConfig::commonLootMultiplier),
		LOOT_MULTIPLIER.optionalFieldOf("rich_loot_multiplier", DEFAULT.richLootMultiplier())
			.forGetter(AttunedConfig::richLootMultiplier),
		LOOT_MULTIPLIER.optionalFieldOf("treasure_loot_multiplier", DEFAULT.treasureLootMultiplier())
			.forGetter(AttunedConfig::treasureLootMultiplier),
		LOOT_MULTIPLIER.optionalFieldOf("shard_fragment_loot_multiplier", DEFAULT.shardFragmentLootMultiplier())
			.forGetter(AttunedConfig::shardFragmentLootMultiplier),
		Codec.intRange(MIN_COOLDOWN_TICKS, MAX_COOLDOWN_TICKS)
			.optionalFieldOf("voidstep_cooldown_ticks", DEFAULT.voidstepCooldownTicks())
			.forGetter(AttunedConfig::voidstepCooldownTicks),
		Codec.intRange(MIN_COOLDOWN_TICKS, MAX_COOLDOWN_TICKS)
			.optionalFieldOf("gravebind_cooldown_ticks", DEFAULT.gravebindCooldownTicks())
			.forGetter(AttunedConfig::gravebindCooldownTicks),
		Codec.BOOL.optionalFieldOf("broadcast_pact_deaths", DEFAULT.broadcastPactDeaths())
			.forGetter(AttunedConfig::broadcastPactDeaths),
		Codec.intRange(MIN_SURGE_INTERVAL_TICKS, MAX_SURGE_INTERVAL_TICKS)
			.optionalFieldOf("surge_interval_ticks", DEFAULT.surgeIntervalTicks())
			.forGetter(AttunedConfig::surgeIntervalTicks),
		Codec.intRange(MIN_SURGE_DURATION_TICKS, MAX_SURGE_DURATION_TICKS)
			.optionalFieldOf("surge_duration_ticks", DEFAULT.surgeDurationTicks())
			.forGetter(AttunedConfig::surgeDurationTicks),
		Codec.intRange(MIN_SURGE_RADIUS, MAX_SURGE_RADIUS)
			.optionalFieldOf("surge_radius", DEFAULT.surgeRadius())
			.forGetter(AttunedConfig::surgeRadius)
	).apply(in, AttunedConfig::new));

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
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(json));
		} catch (Exception e) {
			Attuned.LOGGER.error("Could not write {}", path, e);
		}
	}
}
