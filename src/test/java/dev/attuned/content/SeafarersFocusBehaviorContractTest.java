package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source-level coverage for peaceful Seafarers utility behaviors. */
class SeafarersFocusBehaviorContractTest {
	private static final Path HARBORLIGHT =
		Path.of("src/main/java/dev/attuned/content/behavior/HarborlightBehavior.java");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void harborlightWorksWithHeldOrNearbyLanternsWithoutRevealingPlayers() throws IOException {
		String source = Files.readString(HARBORLIGHT, StandardCharsets.UTF_8);

		assertTrue(source.contains("holdsLantern(player) || nearLantern(player)"),
			"Harborlight should work when the player holds a lantern or stands near one");
		assertTrue(source.contains("private static boolean nearLantern(ServerPlayer player)"),
			"Harborlight should expose a nearby-lantern predicate");
		assertTrue(source.contains("BlockPos.betweenClosed"),
			"Harborlight should scan a small nearby area for placed lanterns");
		assertTrue(source.contains("state.is(Blocks.LANTERN)")
				&& source.contains("state.is(Blocks.SOUL_LANTERN)"),
			"Harborlight should recognize both vanilla placed lantern blocks");
		assertTrue(source.contains("MobEffects.NIGHT_VISION"),
			"Harborlight should use navigation-friendly night vision");
		assertTrue(!source.contains("MobEffects.GLOWING"),
			"Harborlight must not reveal players or mobs with glowing");
	}

	@Test
	void harborlightTooltipMentionsHeldOrNearbyLanterns() throws IOException {
		String lang = Files.readString(LANG, StandardCharsets.UTF_8);

		assertTrue(lang.contains("\"item.attuned.harborlight_focus.effect\": \"Grants +1 Luck and a Luck of the Sea boost; near water in low light, holding or standing near a lantern grants night vision.\""),
			"Harborlight tooltip should explain both held and nearby placed lantern activation");
	}
}
