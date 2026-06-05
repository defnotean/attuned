package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract coverage for transient runtime cleanup coordinators. */
class RuntimeCleanupContractTest {
	private static final Path PLAYER_CLEANUP =
		Path.of("src/main/java/dev/attuned/AttunedPlayerCleanup.java");
	private static final Path SERVER_CLEANUP =
		Path.of("src/main/java/dev/attuned/AttunedServerCleanup.java");
	private static final Path ATTUNED =
		Path.of("src/main/java/dev/attuned/Attuned.java");
	private static final Path ATTUNED_EFFECTS =
		Path.of("src/main/java/dev/attuned/effect/AttunedEffects.java");
	private static final Path ATTUNED_COMBAT =
		Path.of("src/main/java/dev/attuned/combat/AttunedCombat.java");
	private static final Path UNSEEN_COMBAT =
		Path.of("src/main/java/dev/attuned/combat/UnseenCombat.java");
	private static final Path REVENANT_COMBAT =
		Path.of("src/main/java/dev/attuned/combat/RevenantCombat.java");
	private static final Path GRAVEBIND_SAVE =
		Path.of("src/main/java/dev/attuned/combat/GravebindSave.java");
	private static final Path ALTAR_NETWORKING =
		Path.of("src/main/java/dev/attuned/menu/AltarNetworking.java");

	@Test
	void playerCleanupRegistersDisconnectHookOnceAndRejectsNullCallbacks() throws IOException {
		String source = read(PLAYER_CLEANUP);

		assertTrue(source.contains("private static boolean initialized;"),
			"Player cleanup should guard against duplicate event registration");
		assertTrue(source.contains("if (initialized)"),
			"Player cleanup should skip repeated init calls");
		assertTrue(source.contains("initialized = true;"),
			"Player cleanup should mark the disconnect hook as registered");
		assertTrue(source.contains("ServerPlayConnectionEvents.DISCONNECT.register"),
			"Player cleanup should own the disconnect event hook");
		assertTrue(source.contains("Objects.requireNonNull(cleanup, \"cleanup\")"),
			"Player cleanup callback registration should reject null callbacks immediately");
	}

	@Test
	void serverCleanupRegistersStopHookOnceAndRejectsNullCallbacks() throws IOException {
		String source = read(SERVER_CLEANUP);

		assertTrue(source.contains("private static boolean initialized;"),
			"Server cleanup should guard against duplicate event registration");
		assertTrue(source.contains("if (initialized)"),
			"Server cleanup should skip repeated init calls");
		assertTrue(source.contains("initialized = true;"),
			"Server cleanup should mark the server-stop hook as registered");
		assertTrue(source.contains("ServerLifecycleEvents.SERVER_STOPPED.register"),
			"Server cleanup should own the server-stop event hook");
		assertTrue(source.contains("Objects.requireNonNull(cleanup, \"cleanup\")"),
			"Server cleanup callback registration should reject null callbacks immediately");
		assertTrue(read(ATTUNED).contains("AttunedServerCleanup.init()"),
			"The mod initializer should install the central server cleanup hook");
	}

	@Test
	void runtimeCachesRegisterServerStopCleanup() throws IOException {
		assertContains(read(ATTUNED_EFFECTS), "AttunedServerCleanup.onStop(() -> {");
		assertContains(read(ATTUNED_EFFECTS), "ACTIVE.clear();");
		assertContains(read(ATTUNED_EFFECTS), "DORMANT.clear();");
		assertContains(read(ATTUNED_EFFECTS), "auraTick = 0;");
		assertContains(read(ATTUNED_COMBAT), "AttunedServerCleanup.onStop(() -> {");
		assertContains(read(ATTUNED_COMBAT), "LAST_AFFINITY_SPARK.clear();");
		assertContains(read(UNSEEN_COMBAT), "AttunedServerCleanup.onStop(LAST_NEEDLE::clear)");
		assertContains(read(REVENANT_COMBAT), "DEBTS.clear();");
		assertContains(read(REVENANT_COMBAT), "LAST_RITES.clear();");
		assertContains(read(GRAVEBIND_SAVE), "AttunedServerCleanup.onStop(lastSave::clear)");
		assertContains(read(ALTAR_NETWORKING), "AttunedServerCleanup.onStop(LAST_BIND_TICK::clear)");
	}

	@Test
	void mobAffinitySparkCacheIsBoundedByTime() throws IOException {
		String source = read(ATTUNED_COMBAT);

		assertTrue(source.contains("AFFINITY_SPARK_CACHE_TTL_TICKS"),
			"Mob affinity spark throttles should have an explicit TTL");
		assertTrue(source.contains("pruneAffinitySparkCache(now)"),
			"Mob affinity spark throttles should prune while combat feedback is active");
		assertTrue(source.contains("LAST_AFFINITY_SPARK.entrySet().removeIf"),
			"Mob affinity spark throttle pruning should remove stale mob UUID entries");
	}

	private static void assertContains(String source, String needle) {
		assertTrue(source.contains(needle), "Expected source to contain: " + needle);
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
