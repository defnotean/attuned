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
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

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

	@Test
	void netmenderRepairsFishingRodsWithCooldownInsteadOfOnlyPreventingCatchDamage() throws IOException {
		String source = Files.readString(SEAFARERS_FISHING, StandardCharsets.UTF_8);

		assertTrue(source.contains("NETMENDER_REPAIR_COOLDOWN_TICKS"),
			"Netmender should have a short explicit mend cooldown");
		assertTrue(source.contains("netmenderCooldowns"),
			"Netmender should track per-player cooldown state");
		assertTrue(source.contains("player.getLevel().getGameTime()")
				|| source.contains("player.getLevel().getGameTime()"),
			"Netmender should compare cooldowns against server game time");
		assertTrue(source.contains("rod.isDamageableItem()"),
			"Netmender should only try to mend damageable fishing rod stacks");
		assertTrue(source.contains("rod.getDamageValue() > 0"),
			"Netmender should only spend a mend proc when the rod actually has damage to repair");
		assertTrue(source.contains("rod.setDamageValue(rod.getDamageValue() - 1)"),
			"Netmender should restore one existing durability point on the rod stack");
		assertTrue(source.contains("AttunedPlayerCleanup.onForget(netmenderCooldowns::remove)"),
			"Netmender cooldown state should be forgotten on disconnect");
		assertTrue(source.contains("AttunedServerCleanup.onStop(netmenderCooldowns::clear)"),
			"Netmender cooldown state should be cleared between server lifetimes");
	}

	@Test
	void netmenderTooltipDescribesRepairInsteadOfDamagePrevention() throws IOException {
		String lang = Files.readString(LANG, StandardCharsets.UTF_8);

		assertTrue(lang.contains("\"item.attuned.netmender_focus.effect\": \"Grants +1 Luck and a Luck of the Sea boost; fish catches may restore 1 rod durability.\""),
			"Netmender tooltip should describe the active repair behavior");
		assertTrue(!lang.contains("prevent rod damage"),
			"Netmender tooltip should not describe the old damage-prevention behavior");
	}
}
