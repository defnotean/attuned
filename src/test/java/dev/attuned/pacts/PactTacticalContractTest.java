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
		assertTrue(source.contains("CombatMomentum.effectiveCooldown("),
			"Pact tactical cooldown should scale with kill-streak momentum at Apex.");
		assertTrue(source.contains("shaveCooldown("),
			"Pact tactical cooldown should shave on streak kills.");
		assertTrue(source.contains("FocusAbilityStatusPayload.PACT_TACTICAL_SLOT"),
			"Pact tactical cooldown should sync through the pact tactical slot sentinel.");
	}

	@Test
	void pactTacticalCooldownSurvivesDisconnectAndPrunesAfterExpiry() throws IOException {
		String source = read(PACT_TACTICALS);

		assertTrue(!source.contains("AttunedPlayerCleanup.onForget(COOLDOWN_ENDS::remove)"),
			"Disconnect/relog should not reset an active pact tactical cooldown.");
		assertTrue(source.contains("ServerTickEvents.END_SERVER_TICK.register(PactTacticals::tick)"),
			"Pact tactical cooldown tombstones should be pruned by server time.");
		assertTrue(source.contains("private static void tick(MinecraftServer server)"),
			"Pact tactical pruning should have a named tick hook.");
		assertTrue(source.contains("pruneExpiredCooldowns(server.overworld().getGameTime())"),
			"Pact tactical pruning should use the same server clock as cooldown storage.");
		assertTrue(source.contains("COOLDOWN_ENDS.entrySet().removeIf(entry -> entry.getValue() <= now)"),
			"Expired pact tactical cooldown tombstones should be removed.");
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
	void radiantCovenantTacticalRevealRequiresLineOfSightAndMaskCounterplay() throws IOException {
		String source = read(PACT_TACTICALS);
		String glowHostiles = methodBody(source,
			"private static void glowHostiles(ServerPlayer player, ServerLevel level, double radius, int ticks)");
		String visibleTargets = methodBody(source,
			"private static List<LivingEntity> visibleRevealTargets(ServerPlayer player, ServerLevel level, double radius)");

		assertTrue(source.contains("import dev.attuned.content.behavior.MaskBehavior;"),
			"Radiant Covenant tactical reveal should respect the same Mask counterplay as other reveal effects.");
		assertTrue(glowHostiles.contains("visibleRevealTargets(player, level, radius)"),
			"Radiant Covenant tactical reveal must not use the generic nearby-hostile list.");
		assertTrue(!glowHostiles.contains("nearbyHostiles(player, level, radius)"),
			"Generic nearby target selection can reveal PvP targets through walls.");
		assertTrue(visibleTargets.contains("CombatTargets.isHostileOrPvpOpponent(target, player)"),
			"Reveal targets should still use the shared hostile/PvP target gate.");
		assertTrue(visibleTargets.contains("!MaskBehavior.resistsReveal(target)"),
			"Reveal targets should allow Mask to counter Glowing effects.");
		assertTrue(visibleTargets.contains("player.hasLineOfSight(target)"),
			"Reveal targets should require line of sight so pact tacticals cannot wallhack PvP opponents.");
	}

	@Test
	void targetedPactTacticalsMarkCircleContributionOnlyAfterFindingValidTargets() throws IOException {
		String source = read(PACT_TACTICALS);
		String helper = methodBody(source,
			"private static void recordTacticalContribution(ServerPlayer player, List<LivingEntity> targets)");

		assertTrue(source.contains("import dev.attuned.party.CircleContributions;"),
			"Pact tactical support should feed the server-owned Circle contribution runtime.");
		assertTrue(helper.contains("if (!targets.isEmpty())"),
			"Tactical contribution should require at least one valid hostile/PvP target.");
		assertTrue(helper.contains("CircleContributions.recordContribution(player)"),
			"Successful targeted tacticals should open the player's Circle contribution window.");

		for (String signature : new String[] {
				"private static void glowHostiles(ServerPlayer player, ServerLevel level, double radius, int ticks)",
				"private static void slowHostiles(ServerPlayer player, ServerLevel level, double radius, int ticks)",
				"private static void igniteHostiles(ServerPlayer player, ServerLevel level, double radius, int seconds)",
				"private static void blindHostilesInDark(ServerPlayer player, ServerLevel level, double radius, int ticks)",
				"private static void knockbackHostiles(ServerPlayer player, ServerLevel level, double radius, double strength)" }) {
			String body = methodBody(source, signature);
			assertTrue(body.contains("List<LivingEntity> targets"),
				"Targeted tactical should materialize the valid target list before contribution credit: " + signature);
			assertTrue(body.contains("recordTacticalContribution(player, targets)"),
				"Targeted tactical should mark contribution once per successful cast: " + signature);
			assertBefore(body, "List<LivingEntity> targets", "recordTacticalContribution(player, targets)");
		}

		String pyresworn = methodBody(source,
			"private static void firePyresworn(ServerPlayer player, ServerLevel level, boolean overcharge, double radiusScale)");
		assertTrue(pyresworn.contains("recordTacticalContribution(player, hostiles)"),
			"Pyresworn tactical should count as contribution only when it ignites real targets.");
		assertBefore(pyresworn, "if (!hostiles.isEmpty())", "recordTacticalContribution(player, hostiles)");
		assertBefore(pyresworn, "recordTacticalContribution(player, hostiles)",
			pyresworn.contains("target.igniteForSeconds(seconds)")
				? "target.igniteForSeconds(seconds)" : "target.setSecondsOnFire(seconds)");
		assertTrue(!pyresworn.substring(pyresworn.indexOf("player.addEffect")).contains("recordTacticalContribution"),
			"Pyresworn's no-target self-buff fallback must not open a contribution window.");
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

	private static String methodBody(String source, String signaturePrefix) {
		int signatureStart = source.indexOf(signaturePrefix);
		assertTrue(signatureStart >= 0, "Missing method signature: " + signaturePrefix);
		int bodyStart = source.indexOf('{', signatureStart);
		assertTrue(bodyStart >= 0, "Missing method body: " + signaturePrefix);
		int depth = 0;
		for (int index = bodyStart; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '{') {
				depth++;
			} else if (current == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(bodyStart, index + 1);
				}
			}
		}
		throw new AssertionError("Unterminated method body: " + signaturePrefix);
	}

	private static void assertBefore(String source, String earlier, String later) {
		int earlierIndex = source.indexOf(earlier);
		int laterIndex = source.indexOf(later);
		assertTrue(earlierIndex >= 0, "Missing expected source fragment: " + earlier);
		assertTrue(laterIndex >= 0, "Missing expected source fragment: " + later);
		assertTrue(earlierIndex < laterIndex,
			"Expected `" + earlier + "` to appear before `" + later + "`.");
	}
}
