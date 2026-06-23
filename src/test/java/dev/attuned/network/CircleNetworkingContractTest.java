package dev.attuned.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source contracts for the server-authoritative Circle lifecycle network path. */
class CircleNetworkingContractTest {
	private static final Path ATTUNED_NETWORKING =
		Path.of("src/main/java/dev/attuned/network/AttunedNetworking.java");
	private static final Path CIRCLE_NETWORKING =
		Path.of("src/main/java/dev/attuned/network/CircleNetworking.java");
	private static final Path JOURNAL_NETWORKING =
		Path.of("src/main/java/dev/attuned/network/JournalNetworking.java");
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void circleLifecyclePayloadsAreRegisteredServerbound() throws IOException {
		String networking = read(CIRCLE_NETWORKING);
		String attunedNetworking = read(ATTUNED_NETWORKING);
		String journalNetworking = read(JOURNAL_NETWORKING);

		assertTrue(attunedNetworking.contains("CircleNetworking.init();"),
			"Common networking init should install the Circle lifecycle receivers.");
		assertTrue(journalNetworking.contains("CircleSnapshotSync.init()"),
			"Common networking init should install Circle snapshot sync on this legacy packet branch.");
		for (String payload : circlePayloads()) {
			Path payloadFile = Path.of("src/main/java/dev/attuned/network/" + payload + ".java");
			assertTrue(Files.isRegularFile(payloadFile),
				payload + " should exist as a dedicated serverbound request payload.");
			String source = read(payloadFile);
			assertTrue(source.contains("PacketType.create(new ResourceLocation(Attuned.MOD_ID,"),
				payload + " should expose a Fabric PacketType for the 1.19.2 networking API.");
			assertTrue(source.contains("FriendlyByteBuf"),
				payload + " should serialize through FriendlyByteBuf on the 1.19.2 networking API.");
			assertTrue(networking.contains("ServerPlayNetworking.registerGlobalReceiver("
					+ payload + ".TYPE"),
				payload + " should have a server-side receiver.");
		}
	}

	@Test
	void circleHandlersWrapOnlyTheServerOwnedManager() throws IOException {
		String networking = read(CIRCLE_NETWORKING);

		assertTrue(networking.contains("import dev.attuned.party.CircleContributions;"),
			"Circle lifecycle network handlers should clear stale contribution windows after membership changes.");
		assertTrue(networking.contains("CircleRuntime.manager().create(player.getUUID(), payload.name(), nowTick(player))"),
			"Create requests should derive membership from the sender and use server time for create cooldowns.");
		assertTrue(networking.contains("CircleRuntime.manager().invite(player.getUUID(), payload.target(), nowTick(player))"),
			"Invite requests should use the sender as actor and the server tick for expiry.");
		assertTrue(networking.contains("CircleRuntime.manager().accept(player.getUUID(), payload.inviter(), nowTick(player))"),
			"Accept requests should use the sender as the accepting target.");
		assertTrue(networking.contains("CircleRuntime.manager().leave(player.getUUID(), nowTick(player))"),
			"Leave requests should remove only the sending player and use server time for disband cooldowns.");
		assertTrue(networking.contains("CircleRuntime.manager().kick(player.getUUID(), payload.target())"),
			"Kick requests should use the sender as actor and let the manager enforce leadership.");
		assertTrue(networking.contains("CircleRuntime.manager().disband(player.getUUID(), nowTick(player))"),
			"Disband requests should use the sender as actor and server time for create cooldowns.");
		assertTrue(networking.contains("handlePing(player, payload)"),
			"Ping requests should be handled by one server-side validation helper.");
		assertTrue(!networking.contains("payload.members"),
			"Circle packets must not accept a client-submitted member list.");
		assertTrue(!networking.contains("payload.circleId"),
			"Circle packets must not let clients choose or spoof a Circle id.");
	}

	@Test
	void membershipChangingNetworkHandlersClearContributionWindows() throws IOException {
		String networking = read(CIRCLE_NETWORKING);

		assertTrue(countOccurrences(networking, "CircleContributions.forgetIfMembershipChanged(result, player);") >= 3,
			"Create, accept, and leave packet paths should clear the actor's stale contribution window after success.");
		assertTrue(networking.contains("CircleContributions.forgetIfMembershipChanged(result, affected);"),
			"Disband packet path should clear stale contribution windows for every former online member.");
		assertTrue(networking.contains("CircleContributions.forgetIfMembershipChanged(result, target);"),
			"Kick packet path should clear stale contribution windows for the kicked target after success.");
		assertBefore(networking,
			"CircleRuntime.manager().create(player.getUUID(), payload.name(), nowTick(player))",
			"CircleContributions.forgetIfMembershipChanged(result, player);");
		assertBefore(networking,
			"CircleRuntime.manager().disband(player.getUUID(), nowTick(player))",
			"CircleContributions.forgetIfMembershipChanged(result, affected);");
		assertBefore(networking,
			"CircleRuntime.manager().kick(player.getUUID(), payload.target())",
			"CircleContributions.forgetIfMembershipChanged(result, target);");
	}

	@Test
	void pingHandlerValidatesRangeLoadedChunkMembershipAndRateLimitBeforeBroadcast() throws IOException {
		String networking = read(CIRCLE_NETWORKING);

		assertTrue(networking.contains("private static final Map<UUID, Long> LAST_PING"),
			"Circle ping cooldown state should be keyed by sender UUID.");
		assertTrue(networking.contains("AttunedServerCleanup.onStop(LAST_PING::clear)"),
			"Circle ping cooldown state should clear on server stop.");
		assertTrue(networking.contains("AttunedPlayerCleanup.onForget(LAST_PING::remove)"),
			"Circle ping cooldown state should clear for forgotten players.");
		assertTrue(networking.contains("CircleRuntime.manager().circleOf(player.getUUID())"),
			"Ping validation should require current server-owned Circle membership.");
		assertTrue(networking.contains("player.getLevel().hasChunkAt(pos)"),
			"Ping validation should reject unloaded target chunks.");
		assertTrue(networking.contains("CirclePingPolicy.allowed("),
			"Ping validation should route through the shared pure policy.");
		assertBefore(networking, "CirclePingPolicy.allowed(", "LAST_PING.put(player.getUUID(), now)");
		assertBefore(networking, "CirclePingPolicy.allowed(", "NetworkPackets.send(member, new CirclePingClientPayload(");
		assertTrue(networking.contains("for (UUID memberId : circle.members())"),
			"Accepted pings should broadcast only to current Circle members.");
		assertTrue(networking.contains("server.getPlayerList().getPlayer(memberId)"),
			"Accepted pings should skip offline Circle members.");
		assertTrue(!networking.contains("targetEntityId"),
			"Initial Circle pings should be location-only and avoid target-entity tracking.");
	}

	@Test
	void disbandHandlerSnapshotsAllFormerOnlineMembers() throws IOException {
		String networking = read(CIRCLE_NETWORKING);

		assertBefore(networking,
			"CircleRuntime.manager().circleOf(player.getUUID())",
			"CircleRuntime.manager().disband(player.getUUID(), nowTick(player))");
		assertTrue(networking.contains("onlineMembers(player, previousCircle)"),
			"Disband should capture the old Circle before mutation and clear snapshots for every former online member.");
		assertTrue(networking.contains("CircleSnapshotSync.syncAfter(player.getLevel().getServer(), result, affected)"),
			"Disband should sync the captured affected members because the DISBANDED result has no Circle members.");
	}

	@Test
	void inviteHandlerRejectsOfflineTargetsBeforeMutatingCircleState() throws IOException {
		String networking = read(CIRCLE_NETWORKING);

		assertBefore(networking,
			"if (target == null)",
			"CircleRuntime.manager().invite(player.getUUID(), payload.target(), nowTick(player))");
		assertTrue(networking.contains("circle.attuned.status.target_offline"),
			"Invite requests for offline/unknown UUIDs should fail with an explicit server-side message.");
	}

	@Test
	void circlePayloadCodecsKeepNamesAndUuidsBounded() throws IOException {
		String create = read(Path.of("src/main/java/dev/attuned/network/CircleCreatePayload.java"));
		assertTrue(create.contains("readUtf(CirclePolicy.MAX_NAME_LENGTH)")
				&& create.contains("buf.writeUtf(name)"),
			"Create payload should serialize the requested Circle name as UTF-8.");
		assertTrue(create.contains("CirclePolicy.sanitizeName(name)"),
			"Circle names should be sanitized before they leave payload construction.");

		for (String payload : new String[] {"CircleInvitePayload", "CircleAcceptPayload", "CircleKickPayload"}) {
			String source = read(Path.of("src/main/java/dev/attuned/network/" + payload + ".java"));
			assertTrue(source.contains("buf.readUUID()") && source.contains("buf.writeUUID(target")
					|| source.contains("buf.readUUID()") && source.contains("buf.writeUUID(inviter"),
				payload + " should serialize player references through FriendlyByteBuf UUID helpers.");
			assertTrue(source.contains("target = target == null ? new UUID(0L, 0L) : target")
					|| source.contains("inviter = inviter == null ? new UUID(0L, 0L) : inviter"),
				payload + " should normalize null UUIDs before storage.");
		}
	}

	@Test
	void circleStatusTranslationsCoverCooldownFailures() throws IOException {
		String lang = read(LANG_FILE);

		assertTrue(lang.contains("\"circle.attuned.status.invite_cooldown\""),
			"Invite cooldown failures should be player-readable.");
		assertTrue(lang.contains("\"circle.attuned.status.create_cooldown\""),
			"Create cooldown failures should be player-readable.");
	}

	@Test
	void inviteNotificationsShowSenderCircleSizeAndExpiry() throws IOException {
		String networking = read(CIRCLE_NETWORKING);
		String lang = read(LANG_FILE);

		assertTrue(networking.contains("sendInviteNotice(target, player, result.circle().orElseThrow())"),
			"Network invite success should use one rich invite-notification helper.");
		assertTrue(networking.contains("NetworkPackets.send(target, new CircleInvitePromptPayload("),
			"Invite success should send a bounded client prompt, not only transient chat text.");
		assertTrue(networking.contains("circle.invites().get(target.getUUID()).expiresAtTick()"),
			"Invite prompts should carry the server-owned invite expiration tick.");
		assertTrue(networking.contains("circle.name()"),
			"Invite notifications should include the Circle name.");
		assertTrue(networking.contains("circle.members().size()"),
			"Invite notifications should include current party size.");
		assertTrue(networking.contains("CircleRuntime.maxMembers()"),
			"Invite notifications should include the Circle cap.");
		assertTrue(networking.contains("CircleRuntime.inviteTtlSeconds()"),
			"Invite notifications should include the expiration window.");
		assertTrue(lang.contains("\"circle.attuned.invited\": \"%1$s invited you to %2$s (%3$s/%4$s). Accept within %5$ss.\""),
			"Invite copy should name sender, Circle, party size, capacity, and expiry seconds.");
	}

	private static String[] circlePayloads() {
		return new String[] {
			"CircleCreatePayload",
			"CircleInvitePayload",
			"CircleAcceptPayload",
			"CircleLeavePayload",
			"CircleDisbandPayload",
			"CircleKickPayload",
			"CirclePingPayload"
		};
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static void assertBefore(String source, String earlier, String later) {
		int earlierIndex = source.indexOf(earlier);
		int laterIndex = source.indexOf(later);
		assertTrue(earlierIndex >= 0, "Missing expected source fragment: " + earlier);
		assertTrue(laterIndex >= 0, "Missing expected source fragment: " + later);
		assertTrue(earlierIndex < laterIndex,
			"Expected `" + earlier + "` before `" + later + "`.");
	}

	private static int countOccurrences(String source, String needle) {
		int count = 0;
		int index = 0;
		while ((index = source.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
	}
}
