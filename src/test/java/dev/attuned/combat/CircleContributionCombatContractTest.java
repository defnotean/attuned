package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for Circle shared-credit contribution hooks in combat. */
class CircleContributionCombatContractTest {
	private static final Path ATTUNED_COMBAT =
		Path.of("src/main/java/dev/attuned/combat/AttunedCombat.java");

	@Test
	void combatRecordsOnlyHostileOrValidPvpContributions() throws IOException {
		String source = read();
		String afterDamage = methodBody(source,
			"private static void afterDamage(LivingEntity defender, DamageSource source,");
		String contribution = methodBody(source,
			"private static void recordCircleCombatContribution(LivingEntity defender, LivingEntity attacker)");

		assertTrue(source.contains("import dev.attuned.party.CircleContributions;"),
			"Combat contribution recording should use the server-owned Circle contribution runtime.");
		assertTrue(afterDamage.contains("recordCircleCombatContribution(defender, attacker)"),
			"Combat should mark Circle contribution before shared-credit consumers run.");
		assertBefore(afterDamage,
			"if (REFLECTING.get())",
			"recordCircleCombatContribution(defender, attacker)");
		assertBefore(afterDamage,
			"if (attacker == null || attacker == defender)",
			"recordCircleCombatContribution(defender, attacker)");
		assertBefore(afterDamage,
			"recordCircleCombatContribution(defender, attacker)",
			"if (dealtDamage <= 0.0F)");

		assertTrue(contribution.contains("attacker instanceof ServerPlayer attackerPlayer"),
			"Only real server players should record attacker-side Circle contribution.");
		assertTrue(contribution.contains("CombatTargets.isHostileOrPvpOpponent(defender, attackerPlayer)"),
			"Attacker contribution should require a hostile mob or valid PvP target.");
		assertBefore(contribution,
			"CombatTargets.isHostileOrPvpOpponent(defender, attackerPlayer)",
			"CircleContributions.recordContribution(attackerPlayer)");

		assertTrue(contribution.contains("defender instanceof ServerPlayer defenderPlayer"),
			"Only real server players should record defender-side Circle contribution.");
		assertTrue(contribution.contains("CombatTargets.isHostileOrPvpOpponent(attacker, defenderPlayer)"),
			"Defender contribution should require damage from a hostile mob or valid PvP opponent.");
		assertBefore(contribution,
			"CombatTargets.isHostileOrPvpOpponent(attacker, defenderPlayer)",
			"CircleContributions.recordContribution(defenderPlayer)");
		assertEquals(2, countOccurrences(contribution, "CircleContributions.recordContribution("),
			"One landed combat event should be able to credit the player attacker and the player defender only.");
	}

	@Test
	void blockedOrAbsorbedHitsCanContributeWithoutRunningDamageProcs() throws IOException {
		String afterDamage = methodBody(read(),
			"private static void afterDamage(LivingEntity defender, DamageSource source,");

		assertBefore(afterDamage,
			"recordCircleCombatContribution(defender, attacker)",
			"if (dealtDamage <= 0.0F)");
		assertBefore(afterDamage,
			"if (dealtDamage <= 0.0F)",
			"float pooledDamage = Math.min(dealtDamage, defender.getMaxHealth())");
		assertBefore(afterDamage,
			"if (dealtDamage <= 0.0F)",
			"hasActiveFocus(defenderPlayer, THORNWARD_FOCUS)");
		assertBefore(afterDamage,
			"if (dealtDamage <= 0.0F)",
			"hasActiveFocus(attackerPlayer, LEECH_FOCUS)");
		assertBefore(afterDamage,
			"if (dealtDamage <= 0.0F)",
			"hasActiveFocus(attackerPlayer, DREADFANG_FOCUS)");
		assertBefore(afterDamage,
			"if (dealtDamage <= 0.0F)",
			"PaletteCombat.onMeleeHit(attacker, defender, source, dealtDamage)");
	}

	private static String read() throws IOException {
		assertTrue(Files.isRegularFile(ATTUNED_COMBAT), "Expected source file to exist: " + ATTUNED_COMBAT);
		return Files.readString(ATTUNED_COMBAT, StandardCharsets.UTF_8);
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
		assertTrue(earlierIndex >= 0, "Missing source fragment: " + earlier);
		assertTrue(laterIndex >= 0, "Missing source fragment: " + later);
		assertTrue(earlierIndex < laterIndex, "Expected `" + earlier + "` before `" + later + "`.");
	}
}
