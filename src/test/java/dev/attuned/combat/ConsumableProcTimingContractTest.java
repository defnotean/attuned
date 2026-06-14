package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source guardrails for the proc-timing fix: consumable openers (Needle cooldown,
 * Veil break, Ashen Debt) must SHAPE damage at the HEAD stage but only be SPENT in
 * the after-damage stage, so a dodged or invulnerability-framed hit wastes the
 * multiplier yet keeps the cooldown/debt intact.
 */
class ConsumableProcTimingContractTest {
	private static final Path UNSEEN =
		Path.of("src/main/java/dev/attuned/combat/UnseenCombat.java");
	private static final Path REVENANT =
		Path.of("src/main/java/dev/attuned/combat/RevenantCombat.java");

	@Test
	void needleConsumptionMovesFromHeadShapingToAfterDamage() throws IOException {
		String unseen = read(UNSEEN);
		String adjustDamage = methodBody(unseen,
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount)");
		String afterDamage = methodBody(unseen,
			"private static void afterDamage(LivingEntity defender, DamageSource source,");

		assertTrue(unseen.contains("PENDING_NEEDLE"),
			"Unseen should track a pending-consumption map so HEAD shaping does not burn the cooldown.");
		assertTrue(!adjustDamage.contains("LAST_NEEDLE.put"),
			"The HEAD stage must not set the Needle cooldown; a dodged hit should not burn it.");
		assertTrue(adjustDamage.contains("PENDING_NEEDLE.put"),
			"The HEAD stage should record a pending Needle consumption instead of spending it.");
		assertTrue(afterDamage.contains("LAST_NEEDLE.put"),
			"The after-damage stage should set the Needle cooldown only when damage actually lands.");
		assertTrue(afterDamage.contains("dealtDamage > 0.0F"),
			"Needle consumption should require that damage was actually dealt.");
		assertTrue(afterDamage.contains("PENDING_NEEDLE.remove"),
			"The after-damage stage should consume (and clear) the matching pending Needle entry.");
	}

	@Test
	void needlePendingMapRegistersCleanup() throws IOException {
		String unseen = read(UNSEEN);
		assertTrue(unseen.contains("AttunedPlayerCleanup.onForget(PENDING_NEEDLE::remove)"),
			"The pending Needle map must drop per-player state on forget.");
		assertTrue(unseen.contains("AttunedServerCleanup.onStop(PENDING_NEEDLE::clear)"),
			"The pending Needle map must clear on server stop.");
		assertTrue(unseen.contains("AttunedServerCleanup.onStop(LAST_NEEDLE::clear)"),
			"The Needle cooldown map cleanup must be preserved.");
	}

	@Test
	void ashenDebtConsumptionMovesFromHeadShapingToAfterDamage() throws IOException {
		String revenant = read(REVENANT);
		String adjustDamage = methodBody(revenant,
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount)");
		String afterDamage = methodBody(revenant,
			"private static void afterDamage(LivingEntity defender, DamageSource source,");

		assertTrue(revenant.contains("PENDING_DEBT"),
			"Revenant should track a pending-consumption map so HEAD shaping does not spend the debt.");
		assertTrue(!adjustDamage.contains("DEBTS.remove"),
			"The HEAD stage must not spend the Ashen Debt; a dodged hit should not consume it.");
		assertTrue(adjustDamage.contains("PENDING_DEBT.put"),
			"The HEAD stage should record a pending debt consumption instead of spending it.");
		assertTrue(afterDamage.contains("DEBTS.remove"),
			"The after-damage stage should spend the Ashen Debt only when damage actually lands.");
		assertTrue(afterDamage.contains("PENDING_DEBT.remove"),
			"The after-damage stage should consume (and clear) the matching pending debt entry.");
	}

	@Test
	void ashenDebtPendingMapRegistersCleanup() throws IOException {
		String revenant = read(REVENANT);
		assertTrue(revenant.contains("PENDING_DEBT.clear();"),
			"The pending debt map must clear on server stop.");
		assertTrue(revenant.contains("PENDING_DEBT.remove(uuid)"),
			"The pending debt map must drop per-player state on forget.");
		assertTrue(revenant.contains("DEBTS.clear();"),
			"The Ashen Debt map cleanup must be preserved.");
	}

	@Test
	void dodgedHitWastesMultiplierButKeepsResources() throws IOException {
		String unseen = read(UNSEEN);
		String revenant = read(REVENANT);
		String unseenAdjust = methodBody(unseen,
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount)");
		String revenantAdjust = methodBody(revenant,
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount)");

		assertTrue(unseenAdjust.contains("NEEDLE_MULTIPLIER"),
			"The Needle multiplier must still be applied at HEAD; it cannot be retro-multiplied.");
		assertTrue(revenantAdjust.contains("DEBT_MULTIPLIER"),
			"The Ashen Debt multiplier must still be applied at HEAD; it cannot be retro-multiplied.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected source file to exist: " + path);
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
}
