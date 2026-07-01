package dev.attuned.network;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source contracts for public-only Circle snapshot sync. */
class CircleSnapshotSyncContractTest {
	private static final Path SNAPSHOT_SYNC =
		Path.of("src/main/java/dev/attuned/network/CircleSnapshotSync.java");
	private static final Path SNAPSHOT_PAYLOAD =
		Path.of("src/main/java/dev/attuned/network/CircleSnapshotPayload.java");
	private static final Path JOURNAL_NETWORKING =
		Path.of("src/main/java/dev/attuned/network/JournalNetworking.java");
	private static final Path CIRCLE_NETWORKING =
		Path.of("src/main/java/dev/attuned/network/CircleNetworking.java");
	private static final Path COMMANDS =
		Path.of("src/main/java/dev/attuned/command/AttunedCommands.java");

	@Test
	void clientboundCircleSnapshotPayloadIsRegistered() throws IOException {
		String payload = read(SNAPSHOT_PAYLOAD);
		String journal = read(JOURNAL_NETWORKING);

		assertTrue(payload.contains("record CircleSnapshotPayload(String name, List<CircleSnapshotPayload.Member> members)"),
			"Circle snapshots should expose only the Circle name and public member rows.");
		assertTrue(payload.contains("record Member(UUID id, String name, boolean leader, String stance, String role, int activeCount, int dormantCount)"),
			"Member rows should contain stable player id, public name, leader marker, and public stance/role/count summary.");
		assertTrue(payload.contains("PacketType.create(new ResourceLocation(Attuned.MOD_ID, \"circle_snapshot\")")
				&& payload.contains("buf.readList(Member::read)")
				&& payload.contains("buf.writeCollection(members, (buffer, member) -> member.write(buffer))"),
			"Snapshot payloads should serialize bounded member rows through the 1.18.2 FriendlyByteBuf packet API.");
		assertTrue(journal.contains("CircleSnapshotSync.init()"),
			"The common networking init should install Circle snapshot sync.");
	}

	@Test
	void snapshotSyncSendsOnlyPublicAttunementSummary() throws IOException {
		String sync = read(SNAPSHOT_SYNC);

		assertTrue(sync.contains("CircleRuntime.manager().circleOf(player.getUUID())"),
			"Snapshot sync should derive membership from the server-owned Circle manager.");
		assertTrue(sync.contains("sendIfChanged(player, snapshotFor(server, circle))"),
			"Snapshot sync should send the server-built public snapshot through dedupe.");
		assertTrue(sync.contains("sendIfChanged(player, CircleSnapshotPayload.EMPTY)"),
			"Players outside a Circle should receive an explicit empty snapshot.");
		assertTrue(sync.contains("Attunement.resolution(member)"),
			"Public Circle snapshots should derive active/dormant counts from the resolved budget state.");
		assertTrue(sync.contains("Attunement.isDiscord(member)"),
			"Public Circle snapshots should expose a coarse Discord stance.");
		assertTrue(sync.contains("Attunement.committedAffinity(member)"),
			"Public Circle snapshots should expose only the committed affinity name, not exact Foci.");
		assertTrue(sync.contains("Attunement.publicRole(member)"),
			"Public Circle snapshots should expose a coarse inferred party role without serializing exact Foci.");
		assertTrue(sync.contains("new CircleSnapshotPayload.Member(player.getUUID(),"),
			"Snapshot sync should construct bounded public member rows.");
		assertTrue(sync.contains("summary.role()"),
			"Snapshot member rows should include the public role label derived server-side.");
		for (String forbidden : new String[] {
				"AttunedAttachments", "definitionFor(", "FocusDefinition", "cooldown", "inventory", "getInventory("}) {
			assertTrue(!sync.contains(forbidden),
				"Circle snapshots must not include exact Focus/inventory/cooldown state: " + forbidden);
		}
	}

	@Test
	void snapshotSyncRespectsServerPartyHudToggle() throws IOException {
		String sync = read(SNAPSHOT_SYNC);

		assertTrue(sync.contains("import dev.attuned.AttunedConfig;"),
			"Snapshot sync should consult the server config, not only the client HUD preference.");
		assertTrue(sync.contains("!AttunedConfig.get().partyHudEnabled()"),
			"Disabling party_hud_enabled should make the server send an empty Circle snapshot.");
		assertTrue(sync.indexOf("!AttunedConfig.get().partyHudEnabled()")
				< sync.indexOf("CircleRuntime.manager().circleOf(player.getUUID())"),
			"The server HUD toggle should be checked before reading and sending Circle membership.");
		assertTrue(sync.contains("sendIfChanged(player, CircleSnapshotPayload.EMPTY)"),
			"Disabled server HUD snapshots should clear any previously visible party rows.");
	}

	@Test
	void membershipMutationsRefreshAffectedPlayers() throws IOException {
		String networking = read(CIRCLE_NETWORKING);
		String commands = read(COMMANDS);

		assertTrue(networking.contains("CircleSnapshotSync.syncAfter(player.getLevel().getServer(), result, player"),
			"Packet handlers should sync the actor after each membership mutation.");
		assertTrue(commands.contains("CircleSnapshotSync.syncAfter(player.getLevel().getServer(), result, player"),
			"Circle commands should sync the actor after each membership mutation.");
		assertTrue(networking.contains("CircleSnapshotSync.syncAfter(player.getLevel().getServer(), result, player, target"),
			"Invite/accept/kick paths should include the other affected online player when present.");
	}

	@Test
	void snapshotSyncDeduplicatesIdenticalPayloadsAndCleansCache() throws IOException {
		String sync = read(SNAPSHOT_SYNC);
		String journal = read(JOURNAL_NETWORKING);

		assertTrue(journal.contains("CircleSnapshotSync.init()"),
			"Common networking init should register Circle snapshot dedupe cleanup hooks.");
		assertTrue(sync.contains("import dev.attuned.AttunedPlayerCleanup;"),
			"Snapshot dedupe cache should be cleaned up when a player disconnects.");
		assertTrue(sync.contains("import dev.attuned.AttunedServerCleanup;"),
			"Snapshot dedupe cache should be cleaned up when the server stops.");
		assertTrue(sync.contains("private static final Map<UUID, CircleSnapshotPayload> LAST_SENT"),
			"Snapshot sync should remember the last payload sent to each player.");
		assertTrue(sync.contains("AttunedPlayerCleanup.onForget(LAST_SENT::remove)"),
			"Disconnect cleanup should drop the stale dedupe entry.");
		assertTrue(sync.contains("AttunedServerCleanup.onStop(LAST_SENT::clear)"),
			"Server-stop cleanup should clear every snapshot dedupe entry.");
		assertTrue(sync.contains("private static void sendIfChanged(ServerPlayer player, CircleSnapshotPayload payload)"),
			"Snapshot sending should go through one dedupe helper.");
		assertTrue(sync.contains("payload.equals(LAST_SENT.get(player.getUUID()))"),
			"Identical snapshots should not be resent.");
		assertTrue(sync.contains("LAST_SENT.put(player.getUUID(), payload)"),
			"New snapshots should update the dedupe cache.");
		assertTrue(sync.contains("NetworkPackets.send(player, payload)"),
			"The dedupe helper should still send changed snapshots through the 1.18.2 networking bridge.");
	}

	@Test
	void snapshotSyncRefreshesPublicSummariesOnThrottledServerTick() throws IOException {
		String sync = read(SNAPSHOT_SYNC);

		assertTrue(sync.contains("import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;"),
			"Circle snapshot sync should use the server tick lifecycle to refresh public attunement summaries.");
		assertTrue(sync.contains("private static final long REFRESH_INTERVAL_TICKS"),
			"Periodic Circle snapshot refreshes should be explicitly throttled.");
		assertTrue(sync.contains("ServerTickEvents.END_SERVER_TICK.register(CircleSnapshotSync::tick)"),
			"Snapshot sync should register one periodic refresh hook during init.");
		assertTrue(sync.contains("private static void tick(MinecraftServer server)"),
			"The periodic refresh should live in one named server-tick helper.");
		assertTrue(sync.contains("now % REFRESH_INTERVAL_TICKS != 0L")
				&& sync.contains("long now = server.overworld().getGameTime();"),
			"Circle snapshot refresh should skip most ticks instead of scanning every server tick.");
		assertTrue(sync.contains("CircleRuntime.manager().pruneExpiredInvites(now)"),
			"The throttled tick should also prune invites whose TTL elapsed without an accept.");
		assertTrue(sync.contains("server.getPlayerList().getPlayers()"),
			"Periodic refresh should inspect online players only.");
		assertTrue(sync.contains("CircleRuntime.manager().circleOf(player.getUUID()).isPresent()"),
			"Periodic refresh should only rebuild snapshots for current Circle members.");
		assertTrue(sync.contains("sync(player)"),
			"Periodic refresh should reuse the deduped sync path so unchanged summaries are not resent.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
