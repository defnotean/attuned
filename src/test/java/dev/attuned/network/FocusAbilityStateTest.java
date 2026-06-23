package dev.attuned.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FocusAbilityStateTest {
	private static final Path STATE = Path.of("src/main/java/dev/attuned/network/FocusAbilityState.java");

	@Test
	void cooldownMessageSecondsUsesCeilingWithoutOverflow() {
		assertEquals(1, FocusAbilityState.cooldownSecondsForMessage(1));
		assertEquals(1, FocusAbilityState.cooldownSecondsForMessage(20));
		assertEquals(2, FocusAbilityState.cooldownSecondsForMessage(21));
		assertEquals(107374183, FocusAbilityState.cooldownSecondsForMessage(Integer.MAX_VALUE));
	}

	@Test
	void cooldownsAreScopedToTheCurrentAbilityId() throws IOException {
		String state = Files.readString(STATE, StandardCharsets.UTF_8);

		assertTrue(state.contains("Map<AbilityCooldownKey, Cooldown> COOLDOWNS"),
			"Cooldowns should not be keyed only by player UUID; loadout swaps can select a different ability.");
		assertTrue(state.contains("record AbilityCooldownKey(UUID playerId, String abilityId)"),
			"Cooldown identity should follow the behavior/ability id, not the physical Focus item.");
		assertTrue(!state.contains("record AbilityCooldownKey(UUID playerId, String itemId)"),
			"Item-scoped cooldowns let datapacks or future Foci bypass a shared ability cooldown.");
		assertTrue(!state.contains("record AbilityCooldownKey(UUID playerId, int slot, String abilityId)"),
			"Slot-scoped cooldowns let players bypass cooldowns by moving the same ability Focus to another slot.");
		assertTrue(state.contains("AbilityCooldownKey cooldownKey(UUID playerId)"),
			"The selected ability should be able to derive its cooldown identity.");
		assertTrue(state.contains("new AbilitySelection(slot, stack, behavior, behaviorId.toString())"),
			"Ability selection should retain the resolved behavior id that drove runtime behavior lookup.");
		assertTrue(!state.contains("BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()"),
			"The cooldown key should not be derived from the item stack identity.");
		assertTrue(state.contains("cooldownRemaining(player, selection)"),
			"Trigger and sync paths should check the cooldown for the current selected ability only.");
		assertTrue(state.contains("COOLDOWNS.put(selection.cooldownKey(player.getUUID())"),
			"Starting a cooldown should store it under the current selected ability key.");
		assertTrue(state.contains("COOLDOWNS.remove(selection.cooldownKey(player.getUUID()))"),
			"Zero-cooldown successful abilities should clear only their own selected-ability key.");
	}

	@Test
	void cooldownsSurviveDisconnectAndPruneAfterExpiry() throws IOException {
		String state = Files.readString(STATE, StandardCharsets.UTF_8);

		assertTrue(!state.contains("COOLDOWNS.keySet().removeIf(key -> key.playerId().equals(uuid))"),
			"Disconnect/relog should not reset active ability cooldowns.");
		assertTrue(state.contains("LAST_SENT.remove(uuid)"),
			"Disconnect should still drop stale client sync mirrors.");
		assertTrue(state.contains("pruneExpiredCooldowns(now)"),
			"Server ticks should prune expired offline cooldown tombstones.");
		assertTrue(state.contains("private static void pruneExpiredCooldowns(long now)"),
			"Expired cooldown pruning should be centralized.");
		assertTrue(state.contains("COOLDOWNS.entrySet().removeIf(entry -> entry.getValue().endsAt() <= now)"),
			"Cooldown tombstones should disappear once their server-time end has passed.");
	}

	@Test
	void cooldownsClearAfterSuccessfulDatapackReload() throws IOException {
		String state = Files.readString(STATE, StandardCharsets.UTF_8);

		assertTrue(state.contains("ServerLifecycleEvents.END_DATA_PACK_RELOAD.register"),
			"Datapack reload can replace ability behavior ids; stale cooldown keys should be dropped.");
		assertTrue(state.contains("if (success)"),
			"Failed reloads should keep the previous behavior/cooldown state intact.");
		assertTrue(state.contains("clearCooldownState();"),
			"Reload and server-stop cleanup should share one cooldown-state reset helper.");
		assertTrue(state.contains("COOLDOWNS.clear();"),
			"Reload cleanup should clear ability cooldown tombstones.");
		assertTrue(state.contains("LAST_SENT.clear();"),
			"Reload cleanup should force a fresh client cooldown sync after definitions rebuild.");
	}
}
