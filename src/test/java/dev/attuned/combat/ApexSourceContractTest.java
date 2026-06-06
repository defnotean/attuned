package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for Apex read paths that sit on combat hot paths. */
class ApexSourceContractTest {
	private static final Path APEX_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/Apex.java");

	@Test
	void capstoneOfDerivesBudgetFromOneCurrentResolution() throws IOException {
		String capstoneOf = methodBody(read(), "public static Optional<Capstone> capstoneOf(Player player)");

		assertEquals(1, countOccurrences(capstoneOf, "Attunement.resolution(player)"),
			"Capstone lookup should resolve the player's budget state once.");
		assertTrue(capstoneOf.contains("List<Integer> active = resolution.activeSlots()"),
			"Capstone lookup should reuse active slots from the detailed resolution.");
		assertEquals(0, countOccurrences(capstoneOf, "Attunement.activeSlots(player)"),
			"Capstone lookup should not recompute active slots through the convenience helper.");
		assertEquals(0, countOccurrences(capstoneOf, "Attunement.used(player)"),
			"Capstone lookup should derive used budget while walking the resolved active slots.");
		assertTrue(capstoneOf.contains("used += definition.cost()"),
			"Capstone lookup should accumulate used budget from the definitions it already reads.");
		assertTrue(capstoneOf.contains("resolveCapstone(activeAffinities, used, Attunement.capacity(player))"),
			"Capstone lookup should pass cached used budget into the pure resolver.");
	}

	@Test
	void damageAdjustmentCachesPlayerCapstonesForOneHit() throws IOException {
		String adjustDamage = methodBody(read(),
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount,\n"
				+ "\t\t\tCombatContext context)");

		assertEquals(0, countOccurrences(adjustDamage, "isAt("),
			"Damage adjustment should not repeatedly resolve capstones through isAt.");
		assertEquals(0, countOccurrences(adjustDamage, "Apex.capstoneOf("),
			"Context-aware damage adjustment should not directly resolve live capstones.");
		assertEquals(1, countOccurrences(adjustDamage, "context.capstoneOf(defenderPlayer)"),
			"One hit should read the defender capstone from context at most once.");
		assertEquals(1, countOccurrences(adjustDamage, "context.capstoneOf(attackerPlayer)"),
			"One hit should read the attacker capstone from context at most once.");
		assertTrue(adjustDamage.contains("defenderCapstone == Capstone.UNYIELDING"),
			"Unyielding should branch on the cached defender capstone.");
		assertTrue(adjustDamage.contains("attackerCapstone == Capstone.EXECUTE"),
			"Execute should branch on the cached attacker capstone.");
		assertTrue(adjustDamage.contains("attackerCapstone == Capstone.JUDGMENT"),
			"Judgment should branch on the cached attacker capstone.");
		assertTrue(adjustDamage.contains("attackerCapstone == Capstone.MAELSTROM"),
			"Maelstrom should branch on the cached attacker capstone.");
	}

	private static String read() throws IOException {
		return Files.readString(APEX_SOURCE, StandardCharsets.UTF_8);
	}

	private static String methodBody(String source, String signature) {
		int signatureStart = source.indexOf(signature);
		assertTrue(signatureStart >= 0, "Missing method signature: " + signature);
		int bodyStart = source.indexOf('{', signatureStart);
		assertTrue(bodyStart >= 0, "Missing method body: " + signature);
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
		throw new AssertionError("Unterminated method body: " + signature);
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
}
