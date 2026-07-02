package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract coverage for server-authoritative Focus Ability cooldown state. */
class AbilityCooldownStateContractTest {
	private static final Path FOCUS_BEHAVIOR =
		Path.of("src/main/java/dev/attuned/api/focus/FocusBehavior.java");
	private static final Path ABILITY_STATE =
		Path.of("src/main/java/dev/attuned/network/FocusAbilityState.java");
	private static final Path ABILITY_PAYLOAD =
		Path.of("src/main/java/dev/attuned/network/FocusAbilityStatusPayload.java");
	private static final Path ATTUNED_NETWORKING =
		Path.of("src/main/java/dev/attuned/network/AttunedNetworking.java");
	private static final Path JOURNAL_NETWORKING =
		Path.of("src/main/java/dev/attuned/network/JournalNetworking.java");
	private static final Path SMOKE =
		Path.of("src/main/java/dev/attuned/content/behavior/SmokeBehavior.java");
	private static final Path VOIDSTEP =
		Path.of("src/main/java/dev/attuned/content/behavior/VoidstepBehavior.java");

	@Test
	void focusBehaviorExposesAbilityCooldownAndSuccess() throws IOException {
		String behavior = read(FOCUS_BEHAVIOR);
		String smoke = read(SMOKE);
		String voidstep = read(VOIDSTEP);

		assertTrue(behavior.contains("default int abilityCooldownTicks()"),
			"Ability behaviors should expose their cooldown duration.");
		assertTrue(behavior.contains("default boolean onAbility"),
			"Ability handlers should report whether the ability actually fired.");
		assertTrue(smoke.contains("public int abilityCooldownTicks()"),
			"Smoke should declare its ability cooldown for the HUD.");
		assertTrue(voidstep.contains("public int abilityCooldownTicks()"),
			"Voidstep should declare its ability cooldown for the HUD.");
		assertTrue(voidstep.contains("return false"),
			"Voidstep should be able to avoid cooldown when no valid blink destination exists.");
	}

	@Test
	void abilityStateChoosesOneActiveAbilityAndSyncsCooldown() throws IOException {
		String state = read(ABILITY_STATE);
		String payload = read(ABILITY_PAYLOAD);
		String networking = read(ATTUNED_NETWORKING);
		String journal = read(JOURNAL_NETWORKING);

		assertTrue(state.contains("class FocusAbilityState"),
			"Shared ability state should live in one server-side helper.");
		assertTrue(state.contains("firstActiveAbility"),
			"The helper should select the first active ability-capable Focus.");
		assertTrue(state.contains("cooldownRemaining"),
			"The helper should expose cooldown remaining for HUD sync.");
		assertTrue(state.contains("ServerPlayNetworking.send(player, new FocusAbilityStatusPayload")
				|| state.contains("NetworkPackets.send(player, new FocusAbilityStatusPayload"),
			"The server should sync ability status to the owning client.");
		assertTrue(state.contains("item.attuned.focus_ability.none"),
			"The ability key should explain when no active ability Focus can fire.");
		assertTrue(state.contains("item.attuned.focus_ability.cooldown"),
			"The ability key should explain when the selected ability is still cooling down.");
		assertTrue(state.contains("ABILITY_TRIGGER_RATE_LIMIT_TICKS"),
			"Ability triggers should be rate-limited to reject forged packet spam.");
		assertTrue(state.contains("FAILED_ABILITY_THROTTLE_TICKS"),
			"Failed ability attempts should enforce a retry throttle before the next trigger.");
		assertTrue(state.contains("FAILED_RETRY_UNTIL"),
			"Failed ability throttles should be tracked per player.");
		assertTrue(payload.contains("record FocusAbilityStatusPayload(int slot, int remainingTicks, int totalTicks)"),
			"The payload should carry selected ability slot and cooldown progress.");
		assertTrue(networking.contains("FocusAbilityState.trigger(player)"),
			"The ability packet receiver should delegate to the shared state helper.");
		assertTrue(journal.contains("PayloadTypeRegistry.clientboundPlay().register(FocusAbilityStatusPayload.TYPE")
				|| journal.contains("PayloadTypeRegistry.playS2C().register(FocusAbilityStatusPayload.TYPE")
				|| payload.contains("PacketType.create(new ResourceLocation(Attuned.MOD_ID, \"focus_ability_status\")"),
			"The cooldown status payload should be registered clientbound.");
	}

	@Test
	void abilityBehaviorCallbacksAreIsolatedAndLogged() throws IOException {
		String state = read(ABILITY_STATE);

		assertTrue(state.contains("runAbility(selection.behavior(), player, selection.stack())"),
			"Ability execution should go through an isolated helper.");
		assertTrue(state.contains("hasActiveAbility(behavior, player, stack)"),
			"Ability availability checks should go through an isolated helper.");
		assertTrue(state.contains("abilityCooldownTicks(selection.behavior(), player, selection.stack())"),
			"Ability cooldown lookups should go through an isolated helper.");
		assertTrue(state.contains("catch (RuntimeException e)"),
			"Ability behavior failures should not escape the packet handler or server tick.");
		assertTrue(state.contains("Attuned.LOGGER.warn(\"Attuned Focus ability"),
			"Ability behavior failures should be logged.");
		assertTrue(state.contains("return Math.max(0, behavior.abilityCooldownTicks())"),
			"Cooldown values should be clamped before HUD sync and storage.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
