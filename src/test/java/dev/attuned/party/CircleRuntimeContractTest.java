package dev.attuned.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CircleRuntimeContractTest {
	private static final Path ATTUNED = Path.of("src/main/java/dev/attuned/Attuned.java");
	private static final Path RUNTIME = Path.of("src/main/java/dev/attuned/party/CircleRuntime.java");
	private static final UUID LEADER = UUID.fromString("00000000-0000-0000-0000-000000000201");
	private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000202");
	private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000000203");

	@Test
	void circleRuntimeRegistersCleanupHooksForTransientPartyState() throws IOException {
		String source = read(RUNTIME);

		assertTrue(source.contains("import dev.attuned.AttunedConfig;"),
			"Circle runtime should read server-owned party settings from AttunedConfig.");
		assertTrue(source.contains("private static CircleManager manager"),
			"Circle runtime should own one server-runtime CircleManager.");
		assertTrue(source.contains("private static final int DEFAULT_MAX_MEMBERS = 4"),
			"Runtime should start with the dossier's default four-member Circle cap.");
		assertTrue(source.contains("private static final long DEFAULT_INVITE_TTL_TICKS = 20L * 60L"),
			"Runtime should use the dossier's default 60-second invite expiry.");
		assertTrue(source.contains("public static int maxMembers()"),
			"Runtime should expose the configured Circle cap for invite prompts.");
		assertTrue(source.contains("public static long inviteTtlSeconds()"),
			"Runtime should expose the configured invite expiry for invite prompts.");
		assertTrue(source.contains("return AttunedConfig.get().partyMaxMembers();"),
			"Circle cap prompts should use the loaded server config.");
		assertTrue(source.contains("return AttunedConfig.get().partyInviteTtlTicks() / 20L;"),
			"Invite expiry prompts should convert the loaded server config ticks to seconds.");
		assertTrue(source.contains("private static final long DEFAULT_INVITE_COOLDOWN_TICKS = 20L * 10L"),
			"Runtime should use the dossier's default ten-second invite-send cooldown.");
		assertTrue(source.contains("private static final long DEFAULT_CREATE_COOLDOWN_TICKS = 20L * 30L"),
			"Runtime should prevent rapid disband/recreate loops for thirty seconds.");
		assertTrue(source.contains("new CircleManager(AttunedConfig.get().partyMaxMembers(),")
				&& source.contains("AttunedConfig.get().partyInviteTtlTicks(),")
				&& source.contains("AttunedConfig.get().partyInviteCooldownTicks(),")
				&& source.contains("AttunedConfig.get().partyCreateCooldownTicks())"),
			"The runtime manager should use server-configured cap, invite expiry, and invite/create spam control.");
		assertTrue(source.contains("AttunedPlayerCleanup.onForgetPlayer(CircleRuntime::forgetPlayer)"),
			"Disconnect cleanup must see the ServerPlayer so remaining Circle members can be resynced.");
		assertTrue(source.contains("CircleSnapshotSync.sync(memberPlayer)"),
			"Disconnect cleanup must refresh remaining Circle HUD snapshots after removing a member.");
		assertTrue(source.contains("manager.circleOf(player.getUUID())"),
			"Disconnect cleanup should capture the old Circle before mutating membership.");
		assertBefore(source, "manager.circleOf(player.getUUID())", "manager.forgetPlayer(player.getUUID())");
		assertTrue(!source.contains("AttunedPlayerCleanup.onForget(MANAGER::forgetPlayer)"),
			"Circle cleanup should not also register a UUID-only hook that double-applies the disconnect mutation.");
		assertTrue(source.contains("AttunedServerCleanup.onStop(() -> manager.clear())"),
			"Server-stop cleanup must drop every transient Circle.");
	}

	@Test
	void modInitializerWiresCircleRuntimeAfterCleanupServices() throws IOException {
		String source = read(ATTUNED);

		assertTrue(source.contains("import dev.attuned.party.CircleRuntime;"),
			"Mod initializer should import the Circle runtime owner.");
		assertTrue(source.contains("CircleRuntime.init();"),
			"Mod initializer should register Circle runtime cleanup hooks.");
		assertBefore(source, "AttunedPlayerCleanup.init();", "CircleRuntime.init();");
		assertBefore(source, "AttunedServerCleanup.init();", "CircleRuntime.init();");
		assertBefore(source, "CircleRuntime.init();", "AttunedNetworking.init();");
	}

	@Test
	void managerSingletonCanBeClearedBetweenServerLifetimes() {
		CircleManager manager = CircleRuntime.manager();
		manager.clear();

		manager.create(LEADER, "Ember Watch");
		assertEquals(1, manager.circleCount());

		manager.clear();
		assertEquals(0, manager.circleCount());
		assertTrue(manager.circleOf(LEADER).isEmpty());
	}

	@Test
	void runtimeManagerUsesTenSecondInviteCooldown() {
		CircleManager manager = CircleRuntime.manager();
		manager.clear();
		try {
			manager.create(LEADER, "Ember Watch", 100L);

			CirclePolicy.Result first = manager.invite(LEADER, SECOND, 120L);
			CirclePolicy.Result afterSixSeconds = manager.invite(LEADER, THIRD, 240L);
			CirclePolicy.Result afterTenSeconds = manager.invite(LEADER, THIRD, 320L);

			assertEquals(CirclePolicy.Status.OK, first.status());
			assertEquals(CirclePolicy.Status.INVITE_COOLDOWN, afterSixSeconds.status(),
				"Runtime defaults should still suppress invite spam six seconds after a successful invite.");
			assertEquals(CirclePolicy.Status.OK, afterTenSeconds.status(),
				"Runtime defaults should allow the next invite once ten seconds have elapsed.");
		} finally {
			manager.clear();
		}
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}

	private static void assertBefore(String source, String earlier, String later) {
		int earlierIndex = source.indexOf(earlier);
		int laterIndex = source.indexOf(later);
		assertTrue(earlierIndex >= 0, "Expected source to contain: " + earlier);
		assertTrue(laterIndex >= 0, "Expected source to contain: " + later);
		assertTrue(earlierIndex < laterIndex, "Expected " + earlier + " before " + later);
	}
}
