package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for small named Focus-pair payoffs that make builds feel more playful. */
class ResonantComboContractTest {
	private static final Path UNSEEN =
		Path.of("src/main/java/dev/attuned/combat/UnseenCombat.java");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");
	private static final Path REFERENCE = Path.of("docs/reference.md");

	@Test
	void silentOpeningRequiresSoftstepAndConfirmedNeedleOpener() throws IOException {
		String unseen = read(UNSEEN);
		String adjustDamage = methodBody(unseen,
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount)");
		String afterDamage = methodBody(unseen,
			"private static void afterDamage(LivingEntity defender, DamageSource source,");

		assertTrue(unseen.contains("Resonant Combo: Softstep + Needle"),
			"The first Resonant Combo should be documented where the proc is implemented.");
		assertTrue(unseen.contains("private static final ResourceLocation SOFTSTEP_FOCUS"),
			"Silent Opening should require Softstep as its second active Focus.");
		assertTrue(unseen.contains("new ResourceLocation(\"attuned\", \"softstep_focus\")"),
			"The Softstep member id should be explicit and stable.");
		assertTrue(unseen.contains("private static final int NEEDLE_SOFTSTEP_WEAKNESS_TICKS = 80"),
			"The opener should create a noticeable but short 4-second Weakness window.");

		assertTrue(!adjustDamage.contains("applyNeedleSoftstepCombo"),
			"Silent Opening must not fire at HEAD before vanilla confirms damage landed.");
		assertTrue(!adjustDamage.contains("MobEffects.WEAKNESS"),
			"The HEAD damage-shaping path should not apply the combo effect.");
		assertTrue(afterDamage.contains("dealtDamage > 0.0F"),
			"The combo should inherit Needle's landed-hit guard.");
		assertTrue(afterDamage.contains("PENDING_NEEDLE.remove(attacker.getUUID())"),
			"The combo should use Needle's pending opener confirmation.");
		assertTrue(afterDamage.contains("pending.target().equals(defender.getUUID())"),
			"The combo should only fire for the target Needle actually shaped.");
		assertTrue(afterDamage.contains("pending.gameTime() == attacker.getLevel().getGameTime()")
				|| afterDamage.contains("pending.gameTime() == attacker.getLevel().getGameTime()"),
			"The combo should only fire on the same tick as the shaped opener.");

		int consumesIndex = afterDamage.indexOf("if (pending.consumes())");
		int comboIndex = afterDamage.indexOf("applyNeedleSoftstepCombo(attacker, defender)");
		assertTrue(consumesIndex >= 0, "Needle should still distinguish proc swings from ordinary Veil breaks.");
		assertTrue(comboIndex > consumesIndex,
			"Silent Opening should fire only inside the confirmed consuming Needle opener branch.");
	}

	@Test
	void silentOpeningAppliesWeaknessAndFeedback() throws IOException {
		String unseen = read(UNSEEN);
		String combo = methodBody(unseen,
			"private static void applyNeedleSoftstepCombo(ServerPlayer attacker, LivingEntity defender)");
		String feedback = methodBody(unseen,
			"private static void needleSoftstepComboFeedback(ServerPlayer attacker, LivingEntity defender)");

		assertTrue(combo.contains("hasActiveFocus(attacker, SOFTSTEP_FOCUS)"),
			"Needle alone should keep its normal behavior; the combo requires active Softstep.");
		assertTrue(combo.contains("defender.addEffect(new MobEffectInstance("),
			"The combo should apply a vanilla effect instead of adding new persistent state.");
		assertTrue(combo.contains("MobEffects.WEAKNESS"),
			"Silent Opening should weaken the target after the opener lands.");
		assertTrue(combo.contains("NEEDLE_SOFTSTEP_WEAKNESS_TICKS, 0, true, false, true"),
			"Weakness should be short, level I, ambient, hidden-particles, and visible in inventory.");
		assertTrue(combo.contains("needleSoftstepComboFeedback(attacker, defender)"),
			"The combo needs visible/audible feedback so it feels like a build moment.");

		assertTrue(feedback.contains("sendParticles("), "The combo should have particle feedback.");
		assertTrue(feedback.contains("playSound(null, defender.blockPosition()"),
			"The combo should have sound feedback on the target.");
		assertTrue(feedback.contains("ActionBarMessages.send(attacker, ActionBarMessages.Priority.ABILITY"),
			"The attacker should get a compact gated action-bar confirmation when the combo lands.");
		assertTrue(feedback.contains("Component.translatable(\"combo.attuned.softstep_needle\")"),
			"The combo confirmation should keep the player-facing translation key.");
	}

	@Test
	void silentOpeningHasPlayerFacingTextAndReferenceDocs() throws IOException {
		String lang = read(LANG);
		String reference = read(REFERENCE);

		assertTrue(lang.contains("\"combo.attuned.softstep_needle\": \"Resonant Combo: Silent Opening weakened the target.\""),
			"The action-bar combo message should be localizable.");
		assertTrue(reference.contains("## Resonant Combos"),
			"Reference docs should introduce the new pair-combo system.");
		assertTrue(reference.contains("Silent Opening"),
			"Reference docs should name the first shipped Resonant Combo.");
		assertTrue(reference.contains("Softstep + Needle"),
			"Reference docs should list the active Focus pair.");
		assertTrue(reference.contains("Weakness I for 80 ticks"),
			"Reference docs should state the exact current effect for tuning visibility.");
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
