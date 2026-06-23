package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for Resonance event handlers on combat hot paths. */
class ResonanceSourceContractTest {
	private static final Path RESONANCE_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/Resonance.java");

	@Test
	void playerCombatStateResolvesAttunementOnceForCapstoneAndMatchup() throws IOException {
		String source = read();
		String playerState = methodBody(source, "private static PlayerCombatState playerState(Player player)");

		assertTrue(source.contains("private record PlayerCombatState("),
			"Resonance should carry per-event player combat state explicitly.");
		assertEquals(1, countOccurrences(playerState, "Attunement.resolution(player)"),
			"Player combat state should resolve current attunement once.");
		assertTrue(playerState.contains("List<Integer> activeSlots = resolution.activeSlots()"),
			"Player combat state should reuse active slots from the shared resolution.");
		assertEquals(0, countOccurrences(playerState, "Attunement.committedAffinity(player)"),
			"Player combat state should derive committed affinity from cached active definitions.");
		assertEquals(0, countOccurrences(playerState, "Attunement.isDiscord(player)"),
			"Player combat state should derive Discord from cached active definitions.");
		assertEquals(0, countOccurrences(playerState, "Apex.capstoneOf(player)"),
			"Player combat state should derive Apex from cached active definitions.");
		assertTrue(playerState.contains("Apex.resolveCapstone(orderedActiveAffinities, used, Attunement.capacity(player))"),
			"Player combat state should resolve Apex from ordered active affinities and cached used budget.");
		assertTrue(playerState.contains("used += Attunement.effectiveCost(definition, inv.get(slot))"),
			"Player combat state should sum Tempered-aware effective cost so the Apex/Resonance gate "
				+ "matches BudgetResolver and Attunement.used().");
	}

	@Test
	void damageAndDeathHandlersReusePlayerCombatStateForMatchupsAndApex() throws IOException {
		String source = read();
		String afterDamage = methodBody(source,
			"private static void afterDamage(LivingEntity defender, DamageSource source,");
		String afterDeath = methodBody(source,
			"private static void afterDeath(LivingEntity entity, DamageSource source)");

		assertEquals(0, countOccurrences(afterDamage, "Apex.capstoneOf("),
			"Damage resonance should not resolve Apex separately from player combat state.");
		assertEquals(0, countOccurrences(afterDamage, "matchup(attackerPlayer,"),
			"Damage resonance should not re-resolve attacker affinity through Player matchup.");
		assertEquals(0, countOccurrences(afterDamage, "matchup(defenderPlayer,"),
			"Damage resonance should not re-resolve defender affinity through Player matchup.");
		assertEquals(1, countOccurrences(afterDamage, "playerState(attackerPlayer)"),
			"Damage resonance should cache attacker combat state once.");
		assertEquals(1, countOccurrences(afterDamage, "playerState(defenderPlayer)"),
			"Damage resonance should cache defender combat state once.");
		assertTrue(afterDamage.contains("matchup(attackerState, defender, defenderState)"),
			"Attacker resonance gain should use cached player state for both sides when available.");
		assertTrue(afterDamage.contains("matchup(defenderState, attacker, attackerState)"),
			"Defender resonance drain should use cached player state for both sides when available.");
		assertTrue(afterDamage.contains("defenderState.isAt(Apex.Capstone.STILLPOINT)"),
			"Stillpoint resonance should branch on cached defender capstone.");
		assertTrue(afterDamage.contains("hasAffinityPressure(defender, defenderState)"),
			"Maelstrom resonance should reuse defender state when the defender is a player.");

		assertEquals(0, countOccurrences(afterDeath, "Apex.capstoneOf("),
			"Death resonance should not resolve Apex separately from player combat state.");
		assertEquals(0, countOccurrences(afterDeath, "matchup(player,"),
			"Death resonance should not re-resolve attacker affinity through Player matchup.");
		assertTrue(afterDeath.contains("PlayerCombatState attackerState = playerState(player)"),
			"Death resonance should cache attacker combat state once.");
		assertTrue(afterDeath.contains("matchup(attackerState, entity, targetState)"),
			"Death resonance should use cached target player state when available.");
		assertTrue(afterDeath.contains("attackerState.isAt(Apex.Capstone.MAELSTROM)"),
			"Maelstrom death gain should branch on cached attacker capstone.");
	}

	@Test
	void resonanceRewardsRequireHostileOrValidPvpTargets() throws IOException {
		String source = read();
		String afterDamage = methodBody(source,
			"private static void afterDamage(LivingEntity defender, DamageSource source,");
		String afterDeath = methodBody(source,
			"private static void afterDeath(LivingEntity entity, DamageSource source)");

		assertTrue(afterDamage.contains("CombatTargets.isHostileOrPvpOpponent(defender, attackerPlayer)"),
			"Hit resonance should only build from hostile mobs or valid PvP opponents, not passive targets.");
		assertBefore(afterDamage,
			"CombatTargets.isHostileOrPvpOpponent(defender, attackerPlayer)",
			"add(attackerPlayer, dealtDamage * hitEmpoweredGainPerDamage)");
		assertTrue(afterDeath.contains("if (!CombatTargets.isHostileOrPvpOpponent(entity, player))"),
			"Kill resonance, streaks, advancements, and surge rewards should reject passive or friendly targets.");
		assertBefore(afterDeath,
			"if (!CombatTargets.isHostileOrPvpOpponent(entity, player))",
			"PlayerCombatState attackerState = playerState(player)");
		assertBefore(afterDeath,
			"if (!CombatTargets.isHostileOrPvpOpponent(entity, player))",
			"ResonantSurges.tryKillReward(serverPlayer)");
	}

	@Test
	void environmentalAndSelfDamageExitBeforeCombatStateResolution() throws IOException {
		String source = read();
		String afterDamage = methodBody(source,
			"private static void afterDamage(LivingEntity defender, DamageSource source,");

		assertTrue(afterDamage.contains("if (attacker == null || attacker == defender)"),
			"Environmental damage and self-damage should leave before resonance combat state is resolved.");
		assertBefore(afterDamage,
			"if (attacker == null || attacker == defender)",
			"PlayerCombatState defenderState = null");
		assertBefore(afterDamage,
			"if (attacker == null || attacker == defender)",
			"add(attackerPlayer, dealtDamage * hitEmpoweredGainPerDamage)");
		assertBefore(afterDamage,
			"if (attacker == null || attacker == defender)",
			"markCombat(attackerPlayer)");
	}

	private static String read() throws IOException {
		return Files.readString(RESONANCE_SOURCE, StandardCharsets.UTF_8);
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

	private static int countOccurrences(String value, String needle) {
		int count = 0;
		int index = 0;
		while ((index = value.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
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
