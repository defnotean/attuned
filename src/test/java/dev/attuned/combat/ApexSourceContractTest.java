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
		assertTrue(capstoneOf.contains("used += Attunement.effectiveCost(definition, inv.get(slot))"),
			"Capstone lookup should accumulate Tempered-aware effective cost so the near-full-budget "
				+ "gate agrees with BudgetResolver, Attunement.used(), and the readout.");
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

	@Test
	void promotedAffinityCapstonesProcTheirEffectsOnLandedApexHits() throws IOException {
		String apex = read();

		// The four promoted-affinity capstones exist and are bound to their affinity.
		assertTrue(apex.contains("RIPTIDE(\"Riptide\"") && apex.contains("Affinity.TIDE,"),
			"Riptide should exist and be bound to Tide.");
		assertTrue(apex.contains("CRUCIBLE(\"Crucible\"") && apex.contains("Affinity.FORGE,"),
			"Crucible should exist and be bound to Forge.");
		assertTrue(apex.contains("BLOOMWARD(\"Bloomward\"") && apex.contains("Affinity.VERDANT,"),
			"Bloomward should exist and be bound to Verdant.");
		assertTrue(apex.contains("GLOAMING(\"Gloaming\"") && apex.contains("Affinity.UMBRAL,"),
			"Gloaming should exist and be bound to Umbral.");

		// afterDamage must restructure to drive the attacker-side proc for any
		// defender while still pulsing Stillpoint for player defenders.
		String afterDamage = methodBody(apex,
			"private static void afterDamage(LivingEntity defender, DamageSource source,");
		assertTrue(afterDamage.contains("applyAffinityCapstoneProc(defender, source, attacker)"),
			"afterDamage should drive the attacker-side affinity-capstone proc.");
		assertTrue(afterDamage.contains("isAt(defenderPlayer, Capstone.STILLPOINT)"),
			"afterDamage must preserve the defender-side Stillpoint pulse.");

		// The proc dispatch must reference each new capstone so the wiring cannot
		// silently vanish, and each proc must apply its documented effect.
		String proc = methodBody(apex,
			"private static void applyAffinityCapstoneProc(LivingEntity defender, DamageSource source,");
		assertTrue(proc.contains("case RIPTIDE") && proc.contains("case CRUCIBLE")
				&& proc.contains("case BLOOMWARD") && proc.contains("case GLOAMING"),
			"The affinity-capstone proc dispatch must branch on all four new capstones.");

		assertTrue(methodBody(apex, "private static void procRiptide(").contains("MobEffects.MOVEMENT_SLOWDOWN"),
			"Riptide should apply Slowness to the target.");
		assertTrue(methodBody(apex, "private static void procCrucible(").contains("igniteForSeconds")
				|| methodBody(apex, "private static void procCrucible(").contains("setSecondsOnFire"),
			"Crucible should set the target on fire.");
		assertTrue(methodBody(apex, "private static void procBloomward(").contains("attacker.heal("),
			"Bloomward should heal the attacker.");
		assertTrue(methodBody(apex, "private static void procGloaming(").contains("MobEffects.WEAKNESS"),
			"Gloaming should apply Weakness to the target.");
	}

	private static String read() throws IOException {
		return Files.readString(APEX_SOURCE, StandardCharsets.UTF_8);
	}

	private static String methodBody(String source, String signature) {
		source = source.replace("\r\n", "\n").replace('\r', '\n');
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
