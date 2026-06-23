package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for keeping Thornward reflection out of player-attack procs. */
class ThornwardReflectionContractTest {
	private static final Path ATTUNED_COMBAT =
		Path.of("src/main/java/dev/attuned/combat/AttunedCombat.java");
	private static final Path APEX = Path.of("src/main/java/dev/attuned/combat/Apex.java");
	private static final Path PACTS = Path.of("src/main/java/dev/attuned/pacts/Pacts.java");
	private static final Path RESONANCE = Path.of("src/main/java/dev/attuned/combat/Resonance.java");
	private static final Path UNSEEN = Path.of("src/main/java/dev/attuned/combat/UnseenCombat.java");
	private static final Path REVENANT = Path.of("src/main/java/dev/attuned/combat/RevenantCombat.java");
	private static final Path MOSSHEART =
		Path.of("src/main/java/dev/attuned/content/behavior/MossheartBehavior.java");
	private static final Path KILNWARD =
		Path.of("src/main/java/dev/attuned/content/behavior/KilnwardBehavior.java");
	private static final Path RADIANT =
		Path.of("src/main/java/dev/attuned/content/behavior/RadiantFocusBehaviors.java");

	@Test
	void reflectedThornwardDamageCannotReenterPlayerAttackProcPipelines() throws IOException {
		String combat = read(ATTUNED_COMBAT);
		String apex = read(APEX);
		String pacts = read(PACTS);
		String resonance = read(RESONANCE);
		String unseen = read(UNSEEN);

		assertTrue(combat.contains("public static boolean isReflecting()"),
			"The reflection guard should be visible to all combat pipeline stages.");
		assertTrue(methodBody(combat,
			"public static float applyAffinity(ServerLevel level, LivingEntity defender,\n"
				+ "\t\t\tDamageSource source, float amount, CombatContext context)")
				.contains("AttunedCombat.isReflecting()"),
			"Reflected damage should not receive affinity, Sunlance, or Temper scaling.");
		assertTrue(methodBody(apex,
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount,\n"
				+ "\t\t\tCombatContext context)")
				.contains("AttunedCombat.isReflecting()"),
			"Reflected damage should not trigger Apex execute or other attacker capstones.");
		assertTrue(methodBody(apex,
			"private static void afterDamage(LivingEntity defender, DamageSource source,")
				.contains("AttunedCombat.isReflecting()"),
			"Reflected damage should not trigger Apex after-damage capstones or Stillpoint pulses.");
		assertTrue(methodBody(pacts,
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount,\n"
				+ "\t\t\tCombatContext context)")
				.contains("AttunedCombat.isReflecting()"),
			"Reflected damage should not trigger Pact damage bonuses.");
		assertTrue(methodBody(pacts,
			"private static void afterDamage(LivingEntity defender, DamageSource source,")
				.contains("AttunedCombat.isReflecting()"),
			"Reflected damage should not trigger Pyresworn or Radiant after-damage effects.");
		assertTrue(methodBody(resonance,
			"private static void afterDamage(LivingEntity defender, DamageSource source,")
				.contains("AttunedCombat.isReflecting()"),
			"Reflected damage should not award or drain resonance credit.");
		assertTrue(methodBody(unseen,
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount)")
				.contains("AttunedCombat.isReflecting()"),
			"Reflected damage should not spend Needle opener resources.");
		assertTrue(methodBody(unseen,
			"private static void afterDamage(LivingEntity defender, DamageSource source,")
				.contains("AttunedCombat.isReflecting()"),
			"Reflected damage should not confirm deferred Needle/Veil opener state.");

		String revenant = read(REVENANT);
		assertTrue(methodBody(revenant,
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount)")
				.contains("AttunedCombat.isReflecting()"),
			"Reflected damage should not spend or multiply Ashen Debt.");
		assertTrue(methodBody(revenant,
			"private static void afterDamage(LivingEntity defender, DamageSource source,")
				.contains("AttunedCombat.isReflecting()"),
			"Reflected damage should not register Ashen Debts or proc Bonechill.");
	}

	@Test
	void reflectedThornwardDamageCannotSpendReactiveDefenderFocusCooldowns() throws IOException {
		String mossheart = read(MOSSHEART);
		String kilnward = read(KILNWARD);
		String radiant = read(RADIANT);

		assertTrue(methodBody(mossheart,
			"private static void afterDamage(LivingEntity defender, DamageSource source,")
				.contains("AttunedCombat.isReflecting()"),
			"Reflected damage should not spend Mossheart's hostile-hit Resistance cooldown.");
		assertTrue(methodBody(kilnward,
			"private static void afterDamage(LivingEntity defender, DamageSource source,")
				.contains("AttunedCombat.isReflecting()"),
			"Reflected damage should not spend Kilnward's hostile-hit Resistance cooldown.");
		assertTrue(methodBody(radiant,
			"private static void afterDamage(LivingEntity defender, DamageSource source,")
				.contains("AttunedCombat.isReflecting()"),
			"Reflected damage should not spend Votive's hostile-hit shield cooldown.");
		assertTrue(methodBody(radiant,
			"private void afterDamage(LivingEntity defender, DamageSource source,")
				.contains("AttunedCombat.isReflecting()"),
			"Reflected damage should not spend Bellwether's hostile-hit shield cooldown.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected source file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static String methodBody(String source, String signaturePrefix) {
		source = source.replace("\r\n", "\n").replace('\r', '\n');
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
