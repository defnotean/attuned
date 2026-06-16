package dev.attuned.pacts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.attuned.combat.Resonance;
import dev.attuned.network.FocusAbilityStatusPayload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract coverage for pact tactical micro-actives. */
class PactTacticalContractTest {
	private static final Path PACT_TACTICALS =
		Path.of("src/main/java/dev/attuned/pacts/PactTacticals.java");
	private static final Path ABILITY_STATE =
		Path.of("src/main/java/dev/attuned/network/FocusAbilityState.java");
	private static final Path PACTS =
		Path.of("src/main/java/dev/attuned/pacts/Pacts.java");

	@Test
	void pactTacticalUsesThirtySecondCooldown() throws IOException {
		String source = read(PACT_TACTICALS);

		assertTrue(source.contains("COOLDOWN_TICKS = 600"),
			"Pact tacticals should use a 30-second cooldown.");
		assertTrue(source.contains("FocusAbilityStatusPayload.PACT_TACTICAL_SLOT"),
			"Pact tactical cooldown should sync through the pact tactical slot sentinel.");
	}

	@Test
	void pactTacticalRequiresResonanceAtApex() throws IOException {
		String source = read(PACT_TACTICALS);

		assertTrue(source.contains("Resonance.atApex(player)"),
			"Pact tacticals should require resonance at or above the Apex threshold.");
		assertEquals(0.50F, Resonance.APEX_THRESHOLD,
			"Contract assumes the Apex threshold remains at 0.50.");
	}

	@Test
	void focusAbilityStatePrioritizesFocusThenPactThenApex() throws IOException {
		String state = read(ABILITY_STATE);

		int focusIndex = state.indexOf("firstActiveAbility(player)");
		int pactIndex = state.indexOf("PactTacticals.tryTrigger(player)");
		int apexIndex = state.indexOf("Apex.tryIdentityAbility(player)");

		assertTrue(focusIndex >= 0 && pactIndex >= 0 && apexIndex >= 0,
			"Focus ability state should wire focus, pact tactical, and Apex paths.");
		assertTrue(focusIndex < pactIndex && pactIndex < apexIndex,
			"Ability key resolution should be focus ability, pact tactical, then Apex.");
	}

	@Test
	void pactTacticalInitIsWiredFromPactsInit() throws IOException {
		String pacts = read(PACTS);
		assertTrue(pacts.contains("PactTacticals.init()"),
			"Pacts.init should initialize pact tactical state.");
	}

	@Test
	void pactTacticalSlotPreservesCooldownPayload() {
		FocusAbilityStatusPayload payload =
			new FocusAbilityStatusPayload(FocusAbilityStatusPayload.PACT_TACTICAL_SLOT, 120, 600);

		assertEquals(FocusAbilityStatusPayload.PACT_TACTICAL_SLOT, payload.slot());
		assertEquals(120, payload.remainingTicks());
		assertEquals(600, payload.totalTicks());
	}

	@Test
	void focusAbilityStateExposesSyncForPactTacticals() throws IOException {
		String state = read(ABILITY_STATE);
		assertTrue(state.contains("public static void syncStatus(ServerPlayer player, int slot, int remainingTicks, int totalTicks)"),
			"Pact tacticals should reuse the shared ability status sync path.");
	}

	private static String read(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
