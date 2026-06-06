package dev.attuned;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

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

	private static AttunedConfig config(int startingCapacity, int capacityCap) {
		return new AttunedConfig(
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
			AttunedConfig.DEFAULT.gravebindCooldownTicks(),
			AttunedConfig.DEFAULT.broadcastPactDeaths());
	}
}
