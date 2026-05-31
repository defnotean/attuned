package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-level coverage for Seafarers fishing hooks that are otherwise buried
 * in Minecraft's fishing rod and bobber internals.
 */
class SeafarersFishingContractTest {
	private static final Path MIXINS_JSON = Path.of("src/main/resources/attuned.mixins.json");
	private static final Path ROD_MIXIN =
		Path.of("src/main/java/dev/attuned/mixin/FishingRodItemMixin.java");
	private static final Path SEAFARERS_FISHING =
		Path.of("src/main/java/dev/attuned/content/behavior/SeafarersFishing.java");

	@Test
	void seafarersBoostLuckOfTheSeaWhenCastingRod() throws IOException {
		assertTrue(Files.isRegularFile(ROD_MIXIN),
			"Seafarers fishing should hook rod casting, not only catch retrieval");

		String mixins = Files.readString(MIXINS_JSON, StandardCharsets.UTF_8);
		assertTrue(mixins.contains("\"FishingRodItemMixin\""),
			"The rod-casting mixin must be registered");

		String source = Files.readString(SEAFARERS_FISHING, StandardCharsets.UTF_8);
		assertTrue(source.contains("fishingLuckBonus"),
			"SeafarersFishing should expose a focused Luck of the Sea bonus helper");
		assertTrue(source.contains("LINECAST_LUCK_OF_THE_SEA_BONUS"),
			"Linecast should carry the strongest focused fishing-luck boost");
		assertTrue(source.contains("NETMENDER_LUCK_OF_THE_SEA_BONUS"),
			"Netmender should still contribute fishing luck, not only durability");
		assertTrue(source.contains("HARBORLIGHT_LUCK_OF_THE_SEA_BONUS"),
			"Harborlight should contribute to the broader Seafarers fishing setup");
		assertTrue(source.contains("DRIFTGLASS_LUCK_OF_THE_SEA_BONUS"),
			"Driftglass should contribute to the broader Seafarers fishing setup");
	}
}
