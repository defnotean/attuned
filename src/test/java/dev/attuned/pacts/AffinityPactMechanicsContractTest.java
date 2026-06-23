package dev.attuned.pacts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source guardrails for the four expansion Affinity pacts. Each ships a distinct,
 * on-theme mechanic wired into the same hooks the original pacts use; this pins the
 * wiring and the player-facing description so the pacts cannot regress to the inert
 * "you are sworn to the Tide" placeholders they started as.
 *
 * <ul>
 *   <li><b>Tidesworn</b> — melee hits briefly Slow the target (AFTER_DAMAGE proc).</li>
 *   <li><b>Forgebound</b> — melee hits sear the target with a short ignite (AFTER_DAMAGE proc).</li>
 *   <li><b>Wildroot</b> — a slow passive Regeneration while the pact holds (per-tick).</li>
 *   <li><b>Nightsworn</b> — incoming damage is dulled while standing in the dark (incoming-damage hook).</li>
 * </ul>
 */
class AffinityPactMechanicsContractTest {
	private static final Path PACTS =
		Path.of("src/main/java/dev/attuned/pacts/Pacts.java");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void tideswornSlowsOnDirectMeleeHits() throws IOException {
		String pacts = read(PACTS);
		String afterDamage = methodBody(pacts,
			"private static void afterDamage(LivingEntity defender, DamageSource source,");
		String tideswornSlow = methodBody(pacts,
			"private static void tideswornSlow(Player attacker, LivingEntity defender, DamageSource source)");

		assertTrue(afterDamage.contains("attackerPact == Pact.TIDESWORN"),
			"Tidesworn should dispatch from the shared AFTER_DAMAGE hook, like Pyresworn.");
		assertTrue(afterDamage.contains("tideswornSlow(attacker, defender, source)"),
			"Tidesworn dispatch should call its slow helper.");
		assertTrue(tideswornSlow.contains("MobEffects.SLOWNESS") || tideswornSlow.contains("MobEffects.MOVEMENT_SLOWDOWN"),
			"Tidesworn's control effect is a Slowness, dragging the foe in the current.");
		assertTrue(tideswornSlow.contains("isDirectMelee(attacker, source)"),
			"Tidesworn should only Slow on a direct melee hit.");
		assertTrue(tideswornSlow.contains("canStrikePactTarget(attacker, defender)"),
			"Tidesworn should share the friendly-fire/PvP guard with the other strike pacts.");
	}

	@Test
	void forgeboundSearsOnDirectMeleeHits() throws IOException {
		String pacts = read(PACTS);
		String afterDamage = methodBody(pacts,
			"private static void afterDamage(LivingEntity defender, DamageSource source,");
		String forgeboundSear = methodBody(pacts,
			"private static void forgeboundSear(Player attacker, LivingEntity defender, DamageSource source)");

		assertTrue(afterDamage.contains("attackerPact == Pact.FORGEBOUND"),
			"Forgebound should dispatch from the shared AFTER_DAMAGE hook, like Pyresworn.");
		assertTrue(afterDamage.contains("forgeboundSear(attacker, defender, source)"),
			"Forgebound dispatch should call its sear helper.");
		assertTrue(forgeboundSear.contains("PactTier4.forgeboundIgniteSeconds(attacker)"),
			"Forgebound's ember is a short ignite, tier-aware via PactTier4.");
		assertTrue(forgeboundSear.contains("isDirectMelee(attacker, source)"),
			"Forgebound should only sear on a direct melee hit.");
		assertTrue(forgeboundSear.contains("canStrikePactTarget(attacker, defender)"),
			"Forgebound should share the friendly-fire/PvP guard with the other strike pacts.");
		assertTrue(pacts.contains("FORGEBOUND_IGNITE_SECONDS = 2"),
			"Forgebound's ignite should be shorter than Pyresworn's, marking it as the milder burn.");
	}

	@Test
	void wildrootKnitsPassiveRegenerationWhileHeld() throws IOException {
		String pacts = read(PACTS);
		String tick = methodBody(pacts, "private static void tick(MinecraftServer server)");

		assertTrue(tick.contains("now == Pact.WILDROOT"),
			"Wildroot's sustain should refresh from the per-tick handler, like Windrunner's Speed.");
		assertTrue(tick.contains("MobEffects.REGENERATION"),
			"Wildroot's sustain effect is a slow Regeneration.");
		assertTrue(pacts.contains("WILDROOT_REGEN_TICK"),
			"Wildroot should refresh its regeneration on a fixed tick cadence.");
	}

	@Test
	void nightswornDullsIncomingDamageOnlyInTheDark() throws IOException {
		String pacts = read(PACTS);
		String adjustDamage = methodBody(pacts,
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount,\n"
				+ "\t\t\tCombatContext context)");
		String isInDark = methodBody(pacts, "private static boolean isInDark(Player player)");

		assertTrue(adjustDamage.contains("defenderPact == Pact.NIGHTSWORN"),
			"Nightsworn should be a defender-side dampen, layered beside Stoneheart's.");
		assertTrue(adjustDamage.contains("isInDark(")
				&& adjustDamage.contains("PactTier4.nightswornDarkDampen"),
			"Nightsworn's dampen should apply only while the wearer stands in the dark, tier-aware via PactTier4.");
		assertTrue(isInDark.contains("getMaxLocalRawBrightness")
				&& isInDark.contains("NIGHTSWORN_MAX_LIGHT"),
			"Nightsworn's darkness check should read the raw light level, matching the Umbral Foci.");
		assertTrue(pacts.contains("NIGHTSWORN_MAX_LIGHT = 7"),
			"Nightsworn's darkness threshold should match the Umbral Foci's light level of 7.");
	}

	@Test
	void absorbedDamageTrialCreditRequiresRealCombatPressure() throws IOException {
		String pacts = read(PACTS);
		String adjustDamage = methodBody(pacts,
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount,\n"
				+ "\t\t\tCombatContext context)");
		String helper = methodBody(pacts,
			"private static boolean isTrialCreditThreat(LivingEntity attacker, ServerPlayer player)");

		assertTrue(adjustDamage.contains("isTrialCreditThreat(context.attacker(), stoneheartPlayer)"),
			"Stoneheart trial progress should not accrue from environmental, passive, allied, or friendly-fire-blocked damage.");
		assertBefore(adjustDamage,
			"isTrialCreditThreat(context.attacker(), stoneheartPlayer)",
			"PactTrials.onStoneheartAbsorb");
		assertTrue(adjustDamage.contains("isTrialCreditThreat(context.attacker(), nightswornPlayer)"),
			"Nightsworn trial progress should not accrue from environmental, passive, allied, or friendly-fire-blocked damage.");
		assertBefore(adjustDamage,
			"isTrialCreditThreat(context.attacker(), nightswornPlayer)",
			"PactTrials.onNightswornAbsorb");

		assertTrue(helper.contains("attacker != null"),
			"Environmental damage should not qualify as trial combat pressure.");
		assertTrue(helper.contains("CombatTargets.isHostileOrPvpOpponent(attacker, player)"),
			"Absorbed-damage trial credit should use the same hostile/PvP predicate as other reward paths.");
	}

	@Test
	void stoneheartChallengeRequiresRealCombatPressure() throws IOException {
		String pacts = read(PACTS);
		String challenge = methodBody(pacts,
			"private static void maybeAwardStoneheartChallenge(LivingEntity defender,");

		assertTrue(challenge.contains("!isTrialCreditThreat(attacker, player)"),
			"Stoneheart's heavy-hit challenge should not award from environmental, passive, allied, or friendly-fire-blocked damage.");
		assertBefore(challenge,
			"!isTrialCreditThreat(attacker, player)",
			"AttunedAdvancements.award(player, STONEHEART_CHALLENGE)");
	}

	@Test
	void nonPlayerPactPayoffsUseTheSharedHostileAndSummonGate() throws IOException {
		String pacts = read(PACTS);
		String adjustDamage = methodBody(pacts,
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount,\n"
				+ "\t\t\tCombatContext context)");
		String untetheredChallenge = methodBody(pacts,
			"private static void maybeAwardUntetheredChallenge(LivingEntity entity, DamageSource source)");

		assertTrue(adjustDamage.contains("CombatTargets.isHostileOrPvpOpponent(defender, attackerPlayer)"),
			"Radiant Covenant's undead bonus should use the shared hostile/PvP gate so allied or owned undead summons do not qualify.");
		assertBefore(adjustDamage,
			"CombatTargets.isHostileOrPvpOpponent(defender, attackerPlayer)",
			"PactTier4.radiantUndeadBonus(attackerPlayer)");
		assertTrue(untetheredChallenge.contains("!CombatTargets.isHostileOrPvpOpponent(entity, player)"),
			"Untethered kill challenge credit should use the shared hostile/PvP gate so allied or owned affinity mobs do not qualify.");
		assertBefore(untetheredChallenge,
			"!CombatTargets.isHostileOrPvpOpponent(entity, player)",
			"PactTrials.onUntetheredKill(player)");
	}


	@Test
	void descriptionsAdvertiseTheRealMechanics() throws IOException {
		JsonObject lang = languageRoot();
		assertDescription(lang, "tidesworn", "slow");
		assertDescription(lang, "forgebound", "ember");
		assertDescription(lang, "wildroot", "regeneration");
		assertDescription(lang, "nightsworn", "dark");

		// Guard against a regression to the inert placeholder copy.
		for (String id : new String[] {"tidesworn", "forgebound", "wildroot", "nightsworn"}) {
			String description = lang.get("pact.attuned." + id + ".description").getAsString();
			assertTrue(!description.contains("mark you with"),
				"Pact " + id + " should describe a real mechanic, not the placeholder flavor text.");
		}
	}

	@Test
	void strikePactsShareOneDirectMeleeAndFriendlyFireGuard() throws IOException {
		String pacts = read(PACTS);

		assertTrue(pacts.contains("private static boolean isDirectMelee(Player attacker, DamageSource source)"),
			"The on-strike pacts should share one direct-melee helper.");
		assertTrue(pacts.contains("private static boolean canStrikePactTarget(Player attacker, LivingEntity defender)"),
			"The new on-strike pacts should share one friendly-fire/PvP guard.");
		// Tidesworn and Forgebound each route through the shared guard; Pyresworn keeps
		// its own inline guards (pinned by FocusPvpAvailabilityContractTest).
		assertEquals(2, countOccurrences(pacts, "canStrikePactTarget(attacker, defender)"),
			"Tidesworn and Forgebound should route through the shared friendly-fire/PvP guard.");
	}

	private static void assertDescription(JsonObject lang, String id, String needle) {
		String key = "pact.attuned." + id + ".description";
		assertTrue(lang.has(key), "Missing description key: " + key);
		String description = lang.get(key).getAsString().toLowerCase();
		assertTrue(description.contains(needle),
			"Pact " + id + " description should mention '" + needle + "': " + description);
	}

	private static JsonObject languageRoot() throws IOException {
		return JsonParser.parseString(read(LANG)).getAsJsonObject();
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
