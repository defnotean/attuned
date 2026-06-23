package dev.attuned.party;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source contracts for the server-owned Circle shared-credit contribution runtime. */
class CircleContributionsContractTest {
	private static final Path RUNTIME = Path.of("src/main/java/dev/attuned/party/CircleRuntime.java");
	private static final Path CONTRIBUTIONS = Path.of("src/main/java/dev/attuned/party/CircleContributions.java");

	@Test
	void circleRuntimeInitializesSharedCreditContributionTracker() throws IOException {
		String runtime = read(RUNTIME);

		assertTrue(runtime.contains("CircleContributions.init();"),
			"Circle runtime should initialize the server-owned contribution tracker with other party state.");
		assertBefore(runtime, "manager = newManager();", "CircleContributions.init();");
	}

	@Test
	void contributionRuntimeRecordsOnlyEnabledCircleMembersAndCleansUp() throws IOException {
		String source = read(CONTRIBUTIONS);

		assertTrue(source.contains("private static final CircleContributionTracker TRACKER"),
			"Contribution runtime should own a single server-lifetime tracker.");
		assertTrue(source.contains("AttunedPlayerCleanup.onForget(TRACKER::forget)"),
			"Contribution state should drop when a player disconnects.");
		assertTrue(source.contains("AttunedServerCleanup.onStop(TRACKER::clear)"),
			"Contribution state should clear on server stop.");
		assertTrue(source.contains("if (!AttunedConfig.get().partySharedCreditEnabled())"),
			"The shared-credit toggle should fail closed before recording or granting credit.");
		assertTrue(source.contains("CircleRuntime.manager().circleOf(player.getUUID()).isEmpty()"),
			"Contribution recording should ignore players who are not in a server-owned Circle.");
		assertTrue(source.contains("TRACKER.record(player.getUUID(), player.getLevel().getGameTime())"),
			"Contribution recording should use server game time, not client-provided timestamps.");
	}

	@Test
	void contributionRuntimeClearsWindowsAfterMembershipChanges() throws IOException {
		String source = read(CONTRIBUTIONS);
		String helper = methodBody(source,
			"public static void forgetIfMembershipChanged(CirclePolicy.Result result, ServerPlayer... players)");

		assertTrue(helper.contains("result.status() != CirclePolicy.Status.OK\n"
				+ "\t\t\t&& result.status() != CirclePolicy.Status.DISBANDED"),
			"Contribution windows should only clear after lifecycle transitions that actually changed membership.");
		assertTrue(helper.contains("if (players == null)"),
			"Contribution cleanup should tolerate defensive null player arrays.");
		assertTrue(helper.contains("for (ServerPlayer player : players)"),
			"Contribution cleanup should handle multi-member disband snapshots.");
		assertTrue(helper.contains("if (player != null)"),
			"Contribution cleanup should skip offline affected members.");
		assertTrue(helper.contains("TRACKER.forget(player.getUUID())"),
			"Contribution cleanup should drop stale windows for affected online members.");
	}

	@Test
	void contributionRuntimeBuildsPolicyContextsFromOnlineCircleMembers() throws IOException {
		String source = read(CONTRIBUTIONS);

		assertTrue(source.contains("public static java.util.List<ServerPlayer> eligibleContributors(ServerPlayer player)"),
			"Runtime should expose eligible online Circle contributors for future Trial/surge hooks.");
		assertTrue(source.contains("AttunedConfig.get().partySharedCreditRadius()"),
			"Runtime should use the configured shared-credit radius.");
		assertTrue(source.contains("AttunedConfig.get().partySharedCreditWindowTicks()"),
			"Runtime should use the configured contribution window.");
		assertTrue(source.contains("AttunedConfig.get().partyCrossDimensionCreditEnabled()"),
			"Runtime should honor the explicit cross-dimension shared-credit opt-in.");
		assertTrue(source.contains("server.getPlayerList().getPlayer(memberId)"),
			"Runtime should resolve only currently online Circle members.");
		assertTrue(source.contains("member.getLevel() == player.getLevel()"),
			"Runtime should compute same-dimension membership from live server players.");
		assertTrue(source.contains("member.isAlive()"),
			"Runtime should reject dead members through the shared policy context.");
		assertTrue(source.contains("member.isSpectator()"),
			"Runtime should reject spectators through the shared policy context.");
		assertTrue(source.contains("TRACKER.lastContributionTick(memberId)"),
			"Runtime should feed the server-owned last contribution tick into the policy.");
		assertTrue(source.contains("CircleContributionPolicy.eligibleMembers("),
			"Runtime should delegate final eligibility to the shared pure policy.");
	}

	@Test
	void contributionRuntimeExposesNearbyCircleMembersForDiscoveryWitnesses() throws IOException {
		String source = read(CONTRIBUTIONS);
		String helper = methodBody(source,
			"public static java.util.List<ServerPlayer> nearbyCircleMembers(ServerPlayer player)");

		assertFalse(helper.contains("partySharedCreditEnabled"),
			"Discovery witness proximity should not be coupled to the combat shared-credit toggle.");
		assertTrue(helper.contains("CircleRuntime.manager().circleOf(player.getUUID())"),
			"Discovery witnesses should come from server-owned Circle membership.");
		assertTrue(helper.contains("server.getPlayerList().getPlayer(memberId)"),
			"Discovery witnesses should be online players only.");
		assertTrue(helper.contains("member.getLevel() == player.getLevel()"),
			"Discovery witness range should be computed from live dimensions.");
		assertTrue(helper.contains("AttunedConfig.get().partyCrossDimensionCreditEnabled()"),
			"Cross-dimension discovery sharing should require the explicit config opt-in.");
		assertTrue(helper.contains("AttunedConfig.get().partySharedCreditRadius()"),
			"Discovery witnesses should use the shared-credit radius.");
		assertTrue(helper.contains("member.isAlive()") && helper.contains("member.isSpectator()"),
			"Dead players and spectators should not receive shared Confluence discovery credit.");
		assertFalse(helper.contains("TRACKER.lastContributionTick"),
			"Confluence discovery witnessing is proximity-based and should not require recent combat contribution.");
		assertFalse(helper.contains("CircleContributionPolicy.eligibleMembers("),
			"The discovery witness helper should not reuse the contribution-window policy.");
	}

	@Test
	void contributionRuntimeExposesSameDimensionContributionWindowTargetsForPartyAssist() throws IOException {
		String source = read(CONTRIBUTIONS);
		String helper = methodBody(source,
			"public static java.util.List<ServerPlayer> eligibleAssistTargets(ServerPlayer player, double radius)");

		assertTrue(helper.contains("if (!AttunedConfig.get().partySharedCreditEnabled())"),
			"Party assists should honor the shared-credit server toggle.");
		assertTrue(helper.contains("CircleRuntime.manager().circleOf(player.getUUID())"),
			"Party assists should require current server-owned Circle membership.");
		assertTrue(helper.contains("server.getPlayerList().getPlayer(memberId)"),
			"Party assists should only consider online Circle members.");
		assertTrue(helper.contains("member.getLevel() == player.getLevel()"),
			"Party assists should require same-dimension members.");
		assertTrue(helper.contains("member.isAlive()") && helper.contains("member.isSpectator()"),
			"Party assists should reject dead members and spectators through the shared context.");
		assertTrue(helper.contains("TRACKER.lastContributionTick(memberId)"),
			"Party assists should require recent member contribution.");
		assertTrue(helper.contains("CircleContributionPolicy.eligibleMembers("),
			"Party assists should delegate eligibility to the pure shared-credit policy.");
		assertTrue(helper.contains("AttunedConfig.get().partySharedCreditWindowTicks()"),
			"Party assists should reuse the configured contribution window.");
		assertTrue(helper.contains("Math.max(0.0D, radius)"),
			"Party assists should use the data-defined radius defensively.");
		assertTrue(helper.contains("false)"),
			"Party assists should force same-dimension checks instead of using cross-dimension shared-credit config.");
		assertFalse(helper.contains("AttunedConfig.get().partyCrossDimensionCreditEnabled()"),
			"Party assists should not be widened by the cross-dimension shared-credit opt-in.");
		assertFalse(helper.contains("AttunedConfig.get().partySharedCreditRadius()"),
			"Party assists should use their behavior data radius, not the global shared-credit radius.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
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

	private static void assertBefore(String source, String earlier, String later) {
		int earlierIndex = source.indexOf(earlier);
		int laterIndex = source.indexOf(later);
		assertTrue(earlierIndex >= 0, "Missing source fragment: " + earlier);
		assertTrue(laterIndex >= 0, "Missing source fragment: " + later);
		assertTrue(earlierIndex < laterIndex, "Expected `" + earlier + "` before `" + later + "`.");
	}
}
