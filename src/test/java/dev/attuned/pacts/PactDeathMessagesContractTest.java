package dev.attuned.pacts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PactDeathMessagesContractTest {
	private static final Path DEATH_MESSAGES =
		Path.of("src/main/java/dev/attuned/pacts/PactDeathMessages.java");
	private static final Path COMBAT_TARGETS =
		Path.of("src/main/java/dev/attuned/combat/CombatTargets.java");

	@Test
	void pactDeathBroadcastsRequireValidPvpCredit() throws IOException {
		String deathMessages = read(DEATH_MESSAGES);
		String afterDeath = methodBody(deathMessages,
			"private static void afterDeath(LivingEntity entity, DamageSource source)");

		assertTrue(deathMessages.contains("import dev.attuned.combat.CombatTargets;"),
			"Pact death broadcasts should share the central PvP/friendly-fire/Circle target policy.");
		assertTrue(afterDeath.contains("!CombatTargets.canCreditPvpKill(attackerPlayer, victim)"),
			"Same-Circle, friendly-fire-blocked, or PvP-disabled deaths should not broadcast pact kill lines.");
		assertBefore(afterDeath,
			"!CombatTargets.canCreditPvpKill(attackerPlayer, victim)",
			"Pacts.activeOf(attackerPlayer)");
		assertBefore(afterDeath,
			"!CombatTargets.canCreditPvpKill(attackerPlayer, victim)",
			"broadcast(victim, attackerPlayer, pact.get())");
	}

	@Test
	void pvpKillCreditDoesNotRequireTheDeadVictimToStillBeAlive() throws IOException {
		String combatTargets = read(COMBAT_TARGETS);
		String helper = methodBody(combatTargets,
			"public static boolean canCreditPvpKill(Player attacker, Player target)");

		assertTrue(helper.contains("pvpAllowed(attacker)"),
			"Death-message PvP credit should still respect the shared server PvP setting helper.");
		assertTrue(helper.contains("!sameCircle(attacker, target)"),
			"Circle members should not create hostile pact death broadcasts.");
		assertTrue(helper.contains("attacker.canHarmPlayer(target)"),
			"Server/team friendly-fire rules should still be honored.");
		assertTrue(!helper.contains("target.isAlive()"),
			"AFTER_DEATH runs after the victim dies, so this helper must not require a living target.");
	}

	private static String read(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static void assertBefore(String source, String earlier, String later) {
		int earlierIndex = source.indexOf(earlier);
		int laterIndex = source.indexOf(later);
		assertTrue(earlierIndex >= 0, "Missing expected text: " + earlier);
		assertTrue(laterIndex >= 0, "Missing expected text: " + later);
		assertTrue(earlierIndex < laterIndex,
			"Expected `" + earlier + "` before `" + later + "`.");
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
}
