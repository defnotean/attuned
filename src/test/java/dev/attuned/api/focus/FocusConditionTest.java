package dev.attuned.api.focus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Truth tables for the pure {@link FocusCondition} decision helpers and round-trip coverage
 * for the registry-free condition variants through the REAL dispatch codec. The tag variants
 * carry a built-in-registry-backed {@code is(TagKey)} evaluation and are exercised in-game;
 * their codec only touches {@link ResourceLocation} so they still decode here.
 */
class FocusConditionTest {

	// ---- Pure decision truth tables ---------------------------------------

	@Test
	void lowLightHoldsAtOrBelowThreshold() {
		assertTrue(FocusCondition.lowLightHolds(0, 7), "Pitch dark is low light.");
		assertTrue(FocusCondition.lowLightHolds(7, 7), "At the threshold counts as low light.");
		assertFalse(FocusCondition.lowLightHolds(8, 7), "One above the threshold is not low light.");
		assertFalse(FocusCondition.lowLightHolds(15, 7), "Full sun is not low light.");
	}

	@Test
	void blockAndBiomeTagDecisionsArePassThrough() {
		assertTrue(FocusCondition.blockTagHolds(true), "A matching foot block holds the block-tag condition.");
		assertFalse(FocusCondition.blockTagHolds(false), "A non-matching foot block fails the block-tag condition.");
		assertTrue(FocusCondition.biomeTagHolds(true), "A matching biome holds the biome-tag condition.");
		assertFalse(FocusCondition.biomeTagHolds(false), "A non-matching biome fails the biome-tag condition.");
	}

	// ---- Codec dispatch ----------------------------------------------------

	@Test
	void unitConditionsRoundTripThroughTheDispatchCodec() {
		assertEquals(FocusCondition.Type.IN_RAIN, roundTrip("{\"condition\":\"in_rain\"}").type());
		assertEquals(FocusCondition.Type.UNDERWATER, roundTrip("{\"condition\":\"underwater\"}").type());
		assertEquals(FocusCondition.Type.SNEAKING, roundTrip("{\"condition\":\"sneaking\"}").type());
	}

	@Test
	void lowLightConditionDecodesItsThreshold() {
		FocusCondition decoded = roundTrip("{\"condition\":\"low_light\",\"max_light\":3}");
		assertEquals(FocusCondition.Type.LOW_LIGHT, decoded.type());
		assertEquals(3, ((FocusCondition.LowLight) decoded).maxLight(),
			"low_light must decode its configured max_light.");
	}

	@Test
	void lowLightConditionDefaultsItsThreshold() {
		FocusCondition decoded = roundTrip("{\"condition\":\"low_light\"}");
		assertEquals(FocusCondition.DEFAULT_MAX_LIGHT, ((FocusCondition.LowLight) decoded).maxLight(),
			"low_light without max_light falls back to the default threshold.");
	}

	@Test
	void lowLightProgrammaticConstructorMatchesCodecBounds() {
		assertEquals(0, new FocusCondition.LowLight(0).maxLight());
		assertEquals(15, new FocusCondition.LowLight(15).maxLight());

		IllegalArgumentException low = assertThrows(IllegalArgumentException.class,
			() -> new FocusCondition.LowLight(-1));
		assertTrue(low.getMessage().contains("Low-light max_light must be between 0 and 15"));

		IllegalArgumentException high = assertThrows(IllegalArgumentException.class,
			() -> new FocusCondition.LowLight(16));
		assertTrue(high.getMessage().contains("Low-light max_light must be between 0 and 15"));
	}

	@Test
	void tagConditionsCarryTheirIdentifiers() {
		FocusCondition block = roundTrip("{\"condition\":\"on_block_tag\",\"tag\":\"minecraft:wool\"}");
		assertEquals(new ResourceLocation("minecraft", "wool"),
			((FocusCondition.OnBlockTag) block).tag(), "on_block_tag must decode its tag id.");

		FocusCondition biome = roundTrip("{\"condition\":\"in_biome_tag\",\"tag\":\"minecraft:is_ocean\"}");
		assertEquals(new ResourceLocation("minecraft", "is_ocean"),
			((FocusCondition.InBiomeTag) biome).tag(), "in_biome_tag must decode its tag id.");
	}

	@Test
	void unknownConditionTypeIsRejected() {
		var result = FocusCondition.CODEC.parse(JsonOps.INSTANCE,
			JsonParser.parseString("{\"condition\":\"teleporting\"}"));
		assertTrue(result.isError(), "An unknown condition type must fail to decode.");
		String message = result.error().orElseThrow().message();
		assertTrue(message.contains("Unknown Focus condition"),
			"The decode error should name the unknown condition.");
		assertTrue(message.contains("conditions: in_rain, underwater, low_light, sneaking, on_block_tag, in_biome_tag"),
			"The decode error should list known condition ids for datapack authors.");
	}

	private static FocusCondition roundTrip(String json) {
		FocusCondition decoded = FocusCondition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
			.result()
			.orElseThrow(() -> new AssertionError("FocusCondition failed to decode: " + json));
		// Re-encode then decode again to guard the dispatch wiring symmetrically.
		var encoded = FocusCondition.CODEC.encodeStart(JsonOps.INSTANCE, decoded)
			.result()
			.orElseThrow(() -> new AssertionError("FocusCondition failed to encode: " + decoded));
		return FocusCondition.CODEC.parse(JsonOps.INSTANCE, encoded)
			.result()
			.orElseThrow(() -> new AssertionError("FocusCondition failed to re-decode: " + encoded));
	}
}
