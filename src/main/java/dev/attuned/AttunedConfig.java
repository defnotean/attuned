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
		boolean broadcastPactDeaths) {

	public AttunedConfig {
		if (capacityCap < 1) {
			throw new IllegalArgumentException("capacityCap must be at least 1");
		}
		startingCapacity = Math.min(startingCapacity, capacityCap);
	}

	/** The built-in defaults — also the fallback for any missing key. */
	public static final AttunedConfig DEFAULT =
		new AttunedConfig(4, 20, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200, true);

	private static final Codec<Float> LOOT_MULTIPLIER = Codec.floatRange(0.0F, 128.0F);

	public static final Codec<AttunedConfig> CODEC = RecordCodecBuilder.create(in -> in.group(
		Codec.intRange(0, 256).optionalFieldOf("starting_capacity", DEFAULT.startingCapacity())
			.forGetter(AttunedConfig::startingCapacity),
		Codec.intRange(1, 256).optionalFieldOf("capacity_cap", DEFAULT.capacityCap())
			.forGetter(AttunedConfig::capacityCap),
		Codec.intRange(1, 64).optionalFieldOf("capacity_per_shard", DEFAULT.capacityPerShard())
			.forGetter(AttunedConfig::capacityPerShard),
		Codec.floatRange(0.0F, 1.0F).optionalFieldOf("focus_loot_chance", DEFAULT.focusLootChance())
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
		Codec.intRange(0, 1728000).optionalFieldOf("voidstep_cooldown_ticks", DEFAULT.voidstepCooldownTicks())
			.forGetter(AttunedConfig::voidstepCooldownTicks),
		Codec.intRange(0, 1728000).optionalFieldOf("gravebind_cooldown_ticks", DEFAULT.gravebindCooldownTicks())
			.forGetter(AttunedConfig::gravebindCooldownTicks),
		Codec.BOOL.optionalFieldOf("broadcast_pact_deaths", DEFAULT.broadcastPactDeaths())
			.forGetter(AttunedConfig::broadcastPactDeaths)
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
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(json));
		} catch (Exception e) {
			Attuned.LOGGER.error("Could not write {}", path, e);
		}
	}
}
