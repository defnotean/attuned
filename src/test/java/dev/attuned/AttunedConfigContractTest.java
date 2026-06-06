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
			AttunedConfig.DEFAULT.broadcastPactDeaths());
	}

	private static void assertInvalid(Executable constructor) {
		assertThrows(IllegalArgumentException.class, constructor,
			"Direct config construction should reject numbers outside the codec contract.");
	}
}
