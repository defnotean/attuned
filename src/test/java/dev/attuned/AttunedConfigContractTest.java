package dev.attuned;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class AttunedConfigContractTest {
	@Test
	void startingCapacityIsCappedAtCapacityCap() {
		AttunedConfig direct = config(30, 12);
		assertEquals(12, direct.startingCapacity(),
			"Direct config construction should preserve the capacity cap invariant.");

		JsonObject json = new JsonObject();
		json.addProperty("starting_capacity", 30);
		json.addProperty("capacity_cap", 12);
		AttunedConfig parsed = AttunedConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

		assertEquals(12, parsed.capacityCap());
		assertEquals(12, parsed.startingCapacity(),
			"Parsed config should not let players start above the configured cap.");
	}

	@Test
	void directConfigRejectsNonPositiveCapacityCap() {
		assertThrows(IllegalArgumentException.class, () -> config(0, 0),
			"Direct config construction should reject a capacity cap that would let capacity become negative.");
	}

	@Test
	void directConfigRejectsCodecOutOfRangeNumbers() {
		assertInvalid(() -> fullConfig(-1, 20, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(257, 20, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 257, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 20, 0, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 20, 65, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 20, 2, -0.01F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 20, 2, 1.01F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 20, 2, 0.25F, -0.01F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 20, 2, 0.25F, Float.NaN, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 20, 2, 0.25F, 129.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1200));
		assertInvalid(() -> fullConfig(4, 20, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, -1, 1200));
		assertInvalid(() -> fullConfig(4, 20, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1728001, 1200));
		assertInvalid(() -> fullConfig(4, 20, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, -1));
		assertInvalid(() -> fullConfig(4, 20, 2, 0.25F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 200, 1728001));
	}

	@Test
	void combatDefaultsMatchTheDocumentedSchema() {
		assertEquals(1.33F, AttunedConfig.DEFAULT.advantageMultiplier());
		assertEquals(0.75F, AttunedConfig.DEFAULT.disadvantageMultiplier());
		assertEquals(1.20F, AttunedConfig.DEFAULT.discordDamageMultiplier());
		assertEquals(0.012F, AttunedConfig.DEFAULT.resonanceHitEmpoweredGainPerDamage());
		assertEquals(0.10F, AttunedConfig.DEFAULT.resonanceHitNeutralizedLoss());
		assertEquals(0.30F, AttunedConfig.DEFAULT.resonanceKillEmpoweredGain());
		assertEquals(0.00025F, AttunedConfig.DEFAULT.resonanceDecayPerTick());
	}

	@Test
	void combatKeysRoundTripThroughTheCodec() {
		JsonObject json = new JsonObject();
		json.addProperty("advantage_multiplier", 1.5F);
		json.addProperty("disadvantage_multiplier", 0.6F);
		json.addProperty("discord_damage_multiplier", 1.25F);
		json.addProperty("resonance_hit_empowered_gain_per_damage", 0.02F);
		json.addProperty("resonance_hit_neutralized_loss", 0.15F);
		json.addProperty("resonance_kill_empowered_gain", 0.4F);
		json.addProperty("resonance_decay_per_tick", 0.0005F);
		AttunedConfig parsed = AttunedConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(1.5F, parsed.advantageMultiplier());
		assertEquals(0.6F, parsed.disadvantageMultiplier());
		assertEquals(1.25F, parsed.discordDamageMultiplier());
		assertEquals(0.02F, parsed.resonanceHitEmpoweredGainPerDamage());
		assertEquals(0.15F, parsed.resonanceHitNeutralizedLoss());
		assertEquals(0.4F, parsed.resonanceKillEmpoweredGain());
		assertEquals(0.0005F, parsed.resonanceDecayPerTick());
	}

	@Test
	void resonantSurgeDefaultsMatchTheDocumentedSchema() {
		assertEquals(12000, AttunedConfig.DEFAULT.surgeIntervalTicks(),
			"Surge interval defaults to 10 minutes (12000 ticks).");
		assertEquals(1200, AttunedConfig.DEFAULT.surgeDurationTicks(),
			"Surge duration defaults to 60 seconds (1200 ticks).");
		assertEquals(16, AttunedConfig.DEFAULT.surgeRadius(),
			"Surge radius defaults to 16 blocks.");
	}

	@Test
	void resonantSurgeKeysRoundTripThroughTheCodec() {
		JsonObject json = new JsonObject();
		json.addProperty("surge_interval_ticks", 4000);
		json.addProperty("surge_duration_ticks", 800);
		json.addProperty("surge_radius", 24);
		AttunedConfig parsed = AttunedConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(4000, parsed.surgeIntervalTicks());
		assertEquals(800, parsed.surgeDurationTicks());
		assertEquals(24, parsed.surgeRadius());
	}

	@Test
	void directConfigRejectsOutOfRangeSurgeNumbers() {
		assertInvalid(() -> surgeConfig(199, 1200, 16));
		assertInvalid(() -> surgeConfig(1728001, 1200, 16));
		assertInvalid(() -> surgeConfig(12000, 199, 16));
		assertInvalid(() -> surgeConfig(12000, 72001, 16));
		assertInvalid(() -> surgeConfig(12000, 1200, 3));
		assertInvalid(() -> surgeConfig(12000, 1200, 65));
	}

	private static AttunedConfig surgeConfig(int surgeIntervalTicks, int surgeDurationTicks, int surgeRadius) {
		return new AttunedConfig(
			AttunedConfig.DEFAULT.startingCapacity(),
			AttunedConfig.DEFAULT.capacityCap(),
			AttunedConfig.DEFAULT.capacityPerShard(),
			AttunedConfig.DEFAULT.focusLootChance(),
			AttunedConfig.DEFAULT.lowLootMultiplier(),
			AttunedConfig.DEFAULT.commonLootMultiplier(),
			AttunedConfig.DEFAULT.richLootMultiplier(),
			AttunedConfig.DEFAULT.treasureLootMultiplier(),
			AttunedConfig.DEFAULT.shardFragmentLootMultiplier(),
			AttunedConfig.DEFAULT.voidstepCooldownTicks(),
			AttunedConfig.DEFAULT.gravebindCooldownTicks(),
			AttunedConfig.DEFAULT.broadcastPactDeaths(),
			surgeIntervalTicks,
			surgeDurationTicks,
			surgeRadius,
			AttunedConfig.DEFAULT.advantageMultiplier(),
			AttunedConfig.DEFAULT.disadvantageMultiplier(),
			AttunedConfig.DEFAULT.discordDamageMultiplier(),
			AttunedConfig.DEFAULT.resonanceHitEmpoweredGainPerDamage(),
			AttunedConfig.DEFAULT.resonanceHitNeutralizedLoss(),
			AttunedConfig.DEFAULT.resonanceKillEmpoweredGain(),
			AttunedConfig.DEFAULT.resonanceDecayPerTick(),
			AttunedConfig.DEFAULT.affinityLoomBaseShardCost(),
			AttunedConfig.DEFAULT.affinityLoomMaxShardCost());
	}

	@Test
	void affinityLoomDefaultsMatchTheDocumentedSchema() {
		assertEquals(1, AttunedConfig.DEFAULT.affinityLoomBaseShardCost(),
			"First Affinity Loom reroll defaults to one Attunement Shard.");
		assertEquals(3, AttunedConfig.DEFAULT.affinityLoomMaxShardCost(),
			"Affinity Loom shard cost defaults to a cap of three.");
	}

	@Test
	void affinityLoomKeysRoundTripThroughTheCodec() {
		JsonObject json = new JsonObject();
		json.addProperty("affinity_loom_base_shard_cost", 2);
		json.addProperty("affinity_loom_max_shard_cost", 5);
		AttunedConfig parsed = AttunedConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(2, parsed.affinityLoomBaseShardCost());
		assertEquals(5, parsed.affinityLoomMaxShardCost());
	}

	@Test
	void directConfigRejectsMaxAffinityLoomCostBelowBase() {
		assertInvalid(() -> loomConfig(3, 2));
	}

	private static AttunedConfig loomConfig(int affinityLoomBaseShardCost, int affinityLoomMaxShardCost) {
		return new AttunedConfig(
			AttunedConfig.DEFAULT.startingCapacity(),
			AttunedConfig.DEFAULT.capacityCap(),
			AttunedConfig.DEFAULT.capacityPerShard(),
			AttunedConfig.DEFAULT.focusLootChance(),
			AttunedConfig.DEFAULT.lowLootMultiplier(),
			AttunedConfig.DEFAULT.commonLootMultiplier(),
			AttunedConfig.DEFAULT.richLootMultiplier(),
			AttunedConfig.DEFAULT.treasureLootMultiplier(),
			AttunedConfig.DEFAULT.shardFragmentLootMultiplier(),
			AttunedConfig.DEFAULT.voidstepCooldownTicks(),
			AttunedConfig.DEFAULT.gravebindCooldownTicks(),
			AttunedConfig.DEFAULT.broadcastPactDeaths(),
			AttunedConfig.DEFAULT.surgeIntervalTicks(),
			AttunedConfig.DEFAULT.surgeDurationTicks(),
			AttunedConfig.DEFAULT.surgeRadius(),
			AttunedConfig.DEFAULT.advantageMultiplier(),
			AttunedConfig.DEFAULT.disadvantageMultiplier(),
			AttunedConfig.DEFAULT.discordDamageMultiplier(),
			AttunedConfig.DEFAULT.resonanceHitEmpoweredGainPerDamage(),
			AttunedConfig.DEFAULT.resonanceHitNeutralizedLoss(),
			AttunedConfig.DEFAULT.resonanceKillEmpoweredGain(),
			AttunedConfig.DEFAULT.resonanceDecayPerTick(),
			affinityLoomBaseShardCost,
			affinityLoomMaxShardCost);
	}

	private static AttunedConfig config(int startingCapacity, int capacityCap) {
		return fullConfig(
			startingCapacity,
			capacityCap,
			AttunedConfig.DEFAULT.capacityPerShard(),
			AttunedConfig.DEFAULT.focusLootChance(),
			AttunedConfig.DEFAULT.lowLootMultiplier(),
			AttunedConfig.DEFAULT.commonLootMultiplier(),
			AttunedConfig.DEFAULT.richLootMultiplier(),
			AttunedConfig.DEFAULT.treasureLootMultiplier(),
			AttunedConfig.DEFAULT.shardFragmentLootMultiplier(),
			AttunedConfig.DEFAULT.voidstepCooldownTicks(),
			AttunedConfig.DEFAULT.gravebindCooldownTicks());
	}

	private static AttunedConfig fullConfig(
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
			int gravebindCooldownTicks) {
		return new AttunedConfig(
			startingCapacity,
			capacityCap,
			capacityPerShard,
			focusLootChance,
			lowLootMultiplier,
			commonLootMultiplier,
			richLootMultiplier,
			treasureLootMultiplier,
			shardFragmentLootMultiplier,
			voidstepCooldownTicks,
			gravebindCooldownTicks,
			AttunedConfig.DEFAULT.broadcastPactDeaths(),
			AttunedConfig.DEFAULT.surgeIntervalTicks(),
			AttunedConfig.DEFAULT.surgeDurationTicks(),
			AttunedConfig.DEFAULT.surgeRadius(),
			AttunedConfig.DEFAULT.advantageMultiplier(),
			AttunedConfig.DEFAULT.disadvantageMultiplier(),
			AttunedConfig.DEFAULT.discordDamageMultiplier(),
			AttunedConfig.DEFAULT.resonanceHitEmpoweredGainPerDamage(),
			AttunedConfig.DEFAULT.resonanceHitNeutralizedLoss(),
			AttunedConfig.DEFAULT.resonanceKillEmpoweredGain(),
			AttunedConfig.DEFAULT.resonanceDecayPerTick(),
			AttunedConfig.DEFAULT.affinityLoomBaseShardCost(),
			AttunedConfig.DEFAULT.affinityLoomMaxShardCost());
	}

	private static void assertInvalid(Executable constructor) {
		assertThrows(IllegalArgumentException.class, constructor,
			"Direct config construction should reject numbers outside the codec contract.");
	}
}
