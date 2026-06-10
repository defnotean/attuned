package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for charged melee checks that run after vanilla resets the attack ticker. */
class ChargedMeleeSnapshotContractTest {
	private static final Path MIXINS_JSON = Path.of("src/main/resources/attuned.mixins.json");
	private static final Path PLAYER_ATTACK_MIXIN =
		Path.of("src/main/java/dev/attuned/mixin/PlayerAttackMixin.java");
	private static final Path ATTUNED_COMBAT =
		Path.of("src/main/java/dev/attuned/combat/AttunedCombat.java");
	private static final Path PACTS = Path.of("src/main/java/dev/attuned/pacts/Pacts.java");

	@Test
	void playerAttackMixinSnapshotsChargeBeforeVanillaResetsTheTicker() throws IOException {
		String mixins = read(MIXINS_JSON);
		String mixin = read(PLAYER_ATTACK_MIXIN);

		assertTrue(mixins.contains("\"PlayerAttackMixin\""),
			"The player attack mixin must be registered.");
		assertTrue(mixin.contains("@Mixin(Player.class)"),
			"The snapshot must hook the vanilla player attack method.");
		assertTrue(mixin.contains("method = \"attack\""),
			"The snapshot should be captured at the real attack boundary.");
		assertTrue(mixin.contains("target = \"Lnet/minecraft/world/entity/player/Player;resetAttackStrengthTicker()V\""),
			"The injection point must be anchored to vanilla's reset call.");
		assertTrue(mixin.contains("shift = At.Shift.BEFORE"),
			"The charge must be read before vanilla resets the attack strength ticker.");
		assertTrue(mixin.contains("getAttackStrengthScale(0.5F)"),
			"The mixin should snapshot the same partial tick scale used by charged-hit thresholds.");
		assertTrue(mixin.contains("AttunedCombat.rememberMeleeCharge("),
			"The snapshot should be stored in the shared combat helper.");
	}

	@Test
	void chargedCombatFeaturesReadThePreResetSnapshotInsteadOfLiveVanillaCharge() throws IOException {
		String combat = read(ATTUNED_COMBAT);
		String pacts = read(PACTS);

		assertTrue(combat.contains("record MeleeChargeSnapshot("),
			"Stored charge should be tied to a specific attacker and target.");
		assertTrue(combat.contains("import dev.attuned.AttunedPlayerCleanup;")
				&& combat.contains("AttunedPlayerCleanup.onForget(MELEE_CHARGE_SNAPSHOTS::remove)"),
			"Per-player charge snapshots should be dropped when a player disconnects.");
		assertTrue(combat.contains("public static void rememberMeleeCharge(Player attacker, Entity target, float charge)"),
			"PlayerAttackMixin needs a narrow API for storing the pre-reset charge.");
		assertTrue(combat.contains("public static boolean isChargedDirectMelee(Player attacker, LivingEntity defender,"),
			"Charged melee detection should be shared by Focus and Pact systems.");

		String attunedHelper = methodBody(combat,
			"public static boolean isChargedDirectMelee(Player attacker, LivingEntity defender,\n"
				+ "\t\t\tDamageSource source, float threshold)");
		assertTrue(attunedHelper.contains("meleeCharge(attacker, defender) >= threshold"),
			"Charged checks must use the pre-reset snapshot, not the live ticker.");
		assertEquals(0, countOccurrences(attunedHelper, "getAttackStrengthScale("),
			"The shared charged-melee helper must not read vanilla's reset ticker.");

		assertEquals(0, countOccurrences(combat, "player.getAttackStrengthScale("),
			"Sunlance and Temper must not read the post-reset ticker.");
		assertEquals(0, countOccurrences(pacts, "attacker.getAttackStrengthScale("),
			"Pyresworn and Radiant Covenant must not read the post-reset ticker.");
		assertTrue(pacts.contains("AttunedCombat.isChargedDirectMelee(attackerPlayer, defender, source, RADIANT_COVENANT_SWING_THRESHOLD)")
				&& pacts.contains("AttunedCombat.isChargedDirectMelee(attacker, defender, source, PYRESWORN_CHARGED_SWING_THRESHOLD)"),
			"Pact damage and after-damage effects should share the same charged-melee helper.");
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
