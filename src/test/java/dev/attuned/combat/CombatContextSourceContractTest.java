package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for sharing one player-state snapshot through the hurt pipeline. */
class CombatContextSourceContractTest {
	private static final Path MIXIN_SOURCE =
		Path.of("src/main/java/dev/attuned/mixin/LivingEntityHurtMixin.java");
	private static final Path CONTEXT_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/CombatContext.java");
	private static final Path ATTUNED_COMBAT_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/AttunedCombat.java");
	private static final Path APEX_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/Apex.java");
	private static final Path PACTS_SOURCE =
		Path.of("src/main/java/dev/attuned/pacts/Pacts.java");

	@Test
	void hurtMixinBuildsOneContextAndPassesItThroughDamageStages() throws IOException {
		String source = read(MIXIN_SOURCE);
		String adjustDamage = methodBodyAny(source,
			"private float attuned$adjustDamage(float amount, ServerLevel level, DamageSource source)",
			"private float attuned$adjustDamage(float amount, DamageSource source)");

		assertEquals(1, countOccurrences(adjustDamage, "CombatContext.of(self, source)"),
			"One hurt event should build one shared combat context.");
		assertTrue(adjustDamage.contains("AttunedCombat.applyAffinity(level, self, source, amount, context)"),
			"Affinity scaling should consume the shared context.");
		assertTrue(adjustDamage.contains("Apex.adjustDamage(self, source, scaled, context)"),
			"Apex damage shaping should consume the shared context.");
		assertTrue(adjustDamage.contains("Pacts.adjustDamage(self, source, capped, context)"),
			"Pact damage shaping should consume the shared context.");
	}

	@Test
	void combatContextResolvesEachPlayerOnceForAffinityApexPactAndActiveFocusState() throws IOException {
		String source = read(CONTEXT_SOURCE);
		String playerState = methodBody(source, "private static PlayerState playerState(Player player)");

		assertTrue(source.contains("public final class CombatContext"),
			"The shared per-hit combat context should be a named class.");
		assertTrue(source.contains("public record PlayerState("),
			"Player state should be explicit and reusable by combat systems.");
		assertEquals(1, countOccurrences(playerState, "Attunement.resolution(player)"),
			"Player state should resolve current attunement once.");
		assertTrue(playerState.contains("List<Integer> activeSlots = resolution.activeSlots()"),
			"Player state should reuse active slots from the shared resolution.");
		assertTrue(playerState.contains("EnumMap<Affinity, Integer> activeAffinityCounts"),
			"Player state should expose active affinity counts for Pact resolution.");
		assertTrue(playerState.contains("Set<ResourceLocation> activeFocusIds"),
			"Player state should expose active Focus item ids for active-Focus checks.");
		assertEquals(0, countOccurrences(playerState, "Attunement.activeSlots(player)"),
			"Player state should not use active-slot convenience helpers.");
		assertEquals(0, countOccurrences(playerState, "Attunement.committedAffinity(player)"),
			"Player state should derive committed affinity from cached active definitions.");
		assertEquals(0, countOccurrences(playerState, "Attunement.isDiscord(player)"),
			"Player state should derive Discord from cached active definitions.");
		assertEquals(0, countOccurrences(playerState, "Apex.capstoneOf(player)"),
			"Player state should derive Apex from cached active definitions.");
		assertTrue(playerState.contains("Apex.resolveCapstone(orderedActiveAffinities, used, Attunement.capacity(player))"),
			"Player state should resolve Apex from ordered active affinities and cached used budget.");
		assertTrue(playerState.contains("used += Attunement.effectiveCost(definition, stack)"),
			"Player state should sum Tempered-aware effective cost so the Apex capstone gate "
				+ "matches BudgetResolver and Attunement.used().");
	}

	@Test
	void contextAwareDamageStagesAvoidReResolvingPlayerState() throws IOException {
		String attunedCombat = read(ATTUNED_COMBAT_SOURCE);
		String apex = read(APEX_SOURCE);
		String pacts = read(PACTS_SOURCE);

		String applyAffinity = methodBody(attunedCombat,
			"public static float applyAffinity(ServerLevel level, LivingEntity defender,\n"
				+ "\t\t\tDamageSource source, float amount, CombatContext context)");
		String apexAdjust = methodBody(apex,
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount,\n"
				+ "\t\t\tCombatContext context)");
		String pactsAdjust = methodBody(pacts,
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount,\n"
				+ "\t\t\tCombatContext context)");

		assertEquals(0, countOccurrences(applyAffinity, "Attunement.isDiscord("),
			"Affinity scaling should read Discord from the context.");
		assertEquals(0, countOccurrences(applyAffinity, "Attunement.committedAffinity("),
			"Affinity scaling should read committed affinity from the context.");
		assertEquals(0, countOccurrences(applyAffinity, "Attunement.activeSlots("),
			"Affinity scaling should read active Focus ids from the context.");
		assertTrue(attunedCombat.contains("context.hasActiveFocus(player, CINDER_FOCUS)")
				&& attunedCombat.contains("context.hasActiveFocus(player, SUNLANCE_FOCUS)"),
			"Active-Focus checks should use the shared context.");

		assertEquals(0, countOccurrences(apexAdjust, "Apex.capstoneOf("),
			"Apex damage shaping should read capstones from the context.");
		assertEquals(0, countOccurrences(apexAdjust, "Resonance.atApex("),
			"Apex damage shaping should read Apex threshold state from the context.");
		assertTrue(apexAdjust.contains("context.hasAffinityPressure(defender)"),
			"Maelstrom target pressure should use the shared context.");

		assertEquals(0, countOccurrences(pactsAdjust, "activeOf(attackerPlayer)"),
			"Pact damage shaping should read attacker Pact from cached affinity counts.");
		assertEquals(0, countOccurrences(pactsAdjust, "activeOf(defenderPlayer)"),
			"Pact damage shaping should read defender Pact from cached affinity counts.");
		assertEquals(0, countOccurrences(pactsAdjust, "Apex.isAt(attackerPlayer"),
			"Pact damage shaping should read Maelstrom state from the context.");
		assertTrue(pactsAdjust.contains("activeOf(context.activeAffinityCounts(attackerPlayer))")
				&& pactsAdjust.contains("activeOf(context.activeAffinityCounts(defenderPlayer))"),
			"Pact damage shaping should resolve Pacts from cached affinity counts.");
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

	private static String methodBodyAny(String source, String... signaturePrefixes) {
		for (String signaturePrefix : signaturePrefixes) {
			if (source.contains(signaturePrefix)) {
				return methodBody(source, signaturePrefix);
			}
		}
		throw new AssertionError("Missing method signature: " + String.join(" or ", signaturePrefixes));
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
