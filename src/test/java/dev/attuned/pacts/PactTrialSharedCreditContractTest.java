package dev.attuned.pacts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for Circle shared credit feeding Pact Trial progress. */
class PactTrialSharedCreditContractTest {
	private static final Path PACT_TRIALS =
		Path.of("src/main/java/dev/attuned/pacts/PactTrials.java");

	@Test
	void pactTrialCreditFansOutOnlyThroughEligibleCircleContributors() throws IOException {
		String source = read();
		String direct = methodBody(source,
			"private static boolean accrueDirect(ServerPlayer player, Pact pact, int amount)");
		String shared = methodBody(source,
			"private static void accrueWithCircleCredit(ServerPlayer player, Pact pact, int amount)");

		assertTrue(source.contains("import dev.attuned.party.CircleContributions;"),
			"Pact Trial sharing should use the server-owned Circle contribution runtime.");
		assertTrue(shared.contains("if (!accrueDirect(player, pact, amount))"),
			"Circle fan-out should only happen after the direct actor actually earned trial credit.");
		assertTrue(shared.contains("CircleContributions.eligibleContributors(player)"),
			"Shared trial credit should come from recent, online, nearby Circle contributors.");
		assertTrue(shared.contains("contributor.getUUID().equals(player.getUUID())"),
			"The direct actor should not receive the same Pact Trial credit twice.");
		assertTrue(shared.contains("sharedTrialTargetFor(contributor, pact)"),
			"Shared credit should first resolve whether the contributor gets same-Pact or support-trial credit.");
		assertTrue(shared.contains("sharedTrialAmount(sharedPact, pact, amount)"),
			"Support assists should be able to use a smaller amount than same-Pact shared credit.");
		assertFalse(shared.contains("accrue(contributor"),
			"Shared Pact Trial credit must not recursively fan out from recipients.");
		assertTrue(direct.contains("shouldAccrue(Pacts.activeOf(player), pact)")
				&& direct.contains("isTier4Complete(player, pact)"),
			"Direct and shared recipients should both use the existing Pact-awake and Tier 4 completion gates.");

		assertEquals(8, countOccurrences(source, "accrueWithCircleCredit("),
			"The helper plus seven discrete Pact Trial event hooks should grant eligible shared Circle credit.");
		assertTrue(source.contains("public static void accrue(ServerPlayer player, Pact pact, int amount)"),
			"The public primitive should remain available for direct, non-party trial progress.");
		assertTrue(methodBody(source, "public static void accrue(ServerPlayer player, Pact pact, int amount)")
				.contains("accrueDirect(player, pact, amount)"),
			"The public primitive should use the same direct accrual implementation as shared recipients.");
	}

	@Test
	void stoneheartSharedTrialSupportsWitnessRadiantAssistWithoutFullCredit() throws IOException {
		String source = read();
		String sharedTarget = methodBody(source,
			"private static Optional<Pact> sharedTrialTargetFor(ServerPlayer contributor, Pact pact)");
		String supportAssist = methodBody(source,
			"private static Optional<Pact> supportTrialAssistPact(ServerPlayer contributor, Pact sourcePact)");
		String sharedAmount = methodBody(source,
			"private static int sharedTrialAmount(Pact sharedPact, Pact sourcePact, int amount)");

		assertTrue(source.contains("import dev.attuned.attunement.Attunement;"),
			"Support-role assists should use the same public role labels exposed to Circle members.");
		assertTrue(sharedTarget.contains("Pacts.isAt(contributor, pact)"),
			"Contributors with the same Pact awake should keep normal same-trial shared credit.");
		assertTrue(sharedTarget.contains("supportTrialAssistPact(contributor, pact)"),
			"Contributors without the source Pact should be checked for explicit support-trial assist mappings.");
		assertTrue(supportAssist.contains("sourcePact == Pact.STONEHEART"),
			"The first support-trial tracer bullet should match the Stoneheart block/absorb example from the dossier.");
		assertTrue(supportAssist.contains("Pact.RADIANT_COVENANT"),
			"Witness support should earn Radiant Covenant trial credit, not full Stoneheart absorbed-damage credit.");
		assertTrue(supportAssist.contains("Pacts.isAt(contributor, Pact.RADIANT_COVENANT)"),
			"Radiant assist credit should still require the support player's Radiant Covenant Pact to be awake.");
		assertTrue(supportAssist.contains("Attunement.publicRole(contributor).equalsIgnoreCase(\"Witness\")"),
			"Radiant assist credit should require a compatible Witness support role.");
		assertTrue(sharedAmount.contains("sharedPact == sourcePact ? amount : 1"),
			"Support-trial assists should grant a small tick instead of mirroring full Stoneheart damage amounts.");
	}

	@Test
	void partyTrialAssistToggleDisablesOnlyCircleFanOut() throws IOException {
		String source = read();
		String directPublic = methodBody(source,
			"public static void accrue(ServerPlayer player, Pact pact, int amount)");
		String shared = methodBody(source,
			"private static void accrueWithCircleCredit(ServerPlayer player, Pact pact, int amount)");

		assertTrue(source.contains("import dev.attuned.AttunedConfig;"),
			"Shared Pact Trial assists should use the server config toggle.");
		assertTrue(shared.contains("if (!AttunedConfig.get().partyTrialAssistsEnabled())"),
			"Shared Pact Trial assists should be independently disableable.");
		assertBefore(shared, "if (!accrueDirect(player, pact, amount))",
			"if (!AttunedConfig.get().partyTrialAssistsEnabled())");
		assertBefore(shared, "partyTrialAssistsEnabled()",
			"CircleContributions.eligibleContributors(player)");
		assertFalse(directPublic.contains("partyTrialAssistsEnabled"),
			"The public direct accrual primitive should not be disabled by the party assist toggle.");
	}

	private static void assertBefore(String source, String earlier, String later) {
		int earlierIndex = source.indexOf(earlier);
		int laterIndex = source.indexOf(later);
		assertTrue(earlierIndex >= 0, "Expected source to contain: " + earlier);
		assertTrue(laterIndex >= 0, "Expected source to contain: " + later);
		assertTrue(earlierIndex < laterIndex, "Expected " + earlier + " before " + later);
	}

	private static String read() throws IOException {
		assertTrue(Files.isRegularFile(PACT_TRIALS), "Expected source file to exist: " + PACT_TRIALS);
		return Files.readString(PACT_TRIALS, StandardCharsets.UTF_8);
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
