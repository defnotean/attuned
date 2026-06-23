package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Pure and source-level guardrails for the Attuned damage formula. */
class DamageFormulaTest {
	private static final Path MIXIN_SOURCE =
		Path.of("src/main/java/dev/attuned/mixin/LivingEntityHurtMixin.java");
	private static final Path ATTUNED_COMBAT_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/AttunedCombat.java");
	private static final Path APEX_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/Apex.java");
	private static final Path PACTS_SOURCE =
		Path.of("src/main/java/dev/attuned/pacts/Pacts.java");
	private static final Path UNSEEN_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/UnseenCombat.java");
	private static final Path REVENANT_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/RevenantCombat.java");

	@Test
	void multipliersCompoundInsteadOfAddingTheirBonusPercentages() {
		float amount = 10.0F;

		amount = DamageFormula.multiply(amount, 1.25F); // affinity or Discord
		amount = DamageFormula.amplify(amount, 0.15F); // pact/confluence/party-style bonus
		amount = DamageFormula.amplify(amount, 0.10F); // Apex-style bonus
		amount = DamageFormula.multiply(amount, 1.35F); // opener multiplier

		assertEquals(10.0F * 1.25F * 1.15F * 1.10F * 1.35F, amount, 0.0001F,
			"Damage bonuses from separate systems should compound multiplicatively.");
	}

	@Test
	void dampensCapsAndExecuteFloorsAreExplicitFormulaSteps() {
		assertEquals(18.0F, DamageFormula.dampen(20.0F, 0.10F), 0.0001F,
			"Damage dampening is a multiplier below 1.0, not a flat subtraction.");
		assertEquals(12.0F, DamageFormula.cap(20.0F, 12.0F), 0.0001F,
			"Damage caps are explicit non-multiplicative stages.");
		assertEquals(100000.0F, DamageFormula.floor(7.0F, 100000.0F), 0.0001F,
			"Execute-style sentinels are explicit floors, not hidden multipliers.");
	}

	@Test
	void hurtMixinKeepsOneOrderedDamagePipelineForFutureRoles() throws IOException {
		String source = read(MIXIN_SOURCE);
		String adjustDamage = methodBody(source,
			source.contains("private float attuned$adjustDamage(float amount, ServerLevel level, DamageSource source)")
				? "private float attuned$adjustDamage(float amount, ServerLevel level, DamageSource source)"
				: "private float attuned$adjustDamage(float amount, DamageSource source)");

		assertOrdered(adjustDamage,
			"AttunedCombat.applyAffinity(level, self, source, amount, context)",
			"Apex.adjustDamage(self, source, scaled, context)",
			"Pacts.adjustDamage(self, source, capped, context)",
			"UnseenCombat.adjustDamage(self, source, adjusted)",
			"RevenantCombat.adjustDamage(self, source, unseen)");
		assertTrue(adjustDamage.contains("Confluence") && adjustDamage.contains("party role"),
			"The shared damage pipeline should document where future Confluence and party-role "
				+ "damage hooks are allowed to join.");
	}

	@Test
	void currentDamageHooksUseTheSharedFormulaHelpers() throws IOException {
		String attunedCombat = read(ATTUNED_COMBAT_SOURCE);
		String apex = read(APEX_SOURCE);
		String pacts = read(PACTS_SOURCE);
		String unseen = read(UNSEEN_SOURCE);
		String revenant = read(REVENANT_SOURCE);

		assertTrue(attunedCombat.contains("DamageFormula.multiply(amount, multiplier)"),
			"Affinity and Discord scaling should use the shared multiplier helper.");
		assertTrue(attunedCombat.contains("DamageFormula.amplify(adjusted, CINDER_BURNING_BONUS)")
				&& attunedCombat.contains("DamageFormula.amplify(adjusted, SUNLANCE_BONUS)")
				&& attunedCombat.contains("DamageFormula.amplify(adjusted, TEMPER_BONUS)"),
			"Focus damage bonuses should use the shared amplify helper.");
		assertTrue(apex.contains("DamageFormula.cap(amount, cap)")
				&& apex.contains("DamageFormula.floor(amount, EXECUTE_DAMAGE)")
				&& apex.contains("DamageFormula.amplify(amount, JUDGMENT_DAMAGE_BONUS)")
				&& apex.contains("DamageFormula.amplify(amount, MAELSTROM_DAMAGE_BONUS)"),
			"Apex should name its caps, execute floor, and multipliers through the shared helper.");
		assertTrue(pacts.contains("DamageFormula.dampen(amount, STONEHEART_DAMPEN)")
				&& pacts.contains("DamageFormula.dampen(amount, dampen)")
				&& pacts.contains("DamageFormula.amplify(amount, UNTETHERED_AMPLIFY)")
				&& pacts.contains("DamageFormula.amplify(amount, PactTier4.radiantUndeadBonus(attackerPlayer))"),
			"Pact dampens and amplifiers should use the shared helper.");
		assertTrue(unseen.contains("DamageFormula.multiply(amount, NEEDLE_MULTIPLIER)"),
			"Needle should shape its opener through the shared multiplier helper.");
		assertTrue(revenant.contains("DamageFormula.multiply(amount, DEBT_MULTIPLIER)"),
			"Ashen Debt should shape its opener through the shared multiplier helper.");
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

	private static void assertOrdered(String haystack, String... needles) {
		int previous = -1;
		for (String needle : needles) {
			int index = haystack.indexOf(needle);
			assertTrue(index > previous, "Expected to find in order: " + needle);
			previous = index;
		}
	}
}
