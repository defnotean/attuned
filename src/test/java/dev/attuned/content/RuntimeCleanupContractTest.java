package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.io.IOException;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract coverage for transient runtime cleanup coordinators. */
class RuntimeCleanupContractTest {
	private static final Path SOURCE_ROOT =
		Path.of("src/main/java/dev/attuned");
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
	private static final Path APEX =
		Path.of("src/main/java/dev/attuned/combat/Apex.java");
	private static final Path PACTS =
		Path.of("src/main/java/dev/attuned/pacts/Pacts.java");
	private static final Path FOCUS_ABILITY_STATE =
		Path.of("src/main/java/dev/attuned/network/FocusAbilityState.java");
	private static final Path ALTAR_ANIMATIONS =
		Path.of("src/main/java/dev/attuned/content/AltarAnimations.java");
	private static final Path ONBOARDING =
		Path.of("src/main/java/dev/attuned/onboarding/Onboarding.java");

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
		assertContains(read(APEX), "AttunedServerCleanup.onStop(() -> {");
		assertContains(read(APEX), "maelstromScrambles.clear();");
		assertContains(read(PACTS), "AttunedServerCleanup.onStop(() -> {");
		assertContains(read(PACTS), "windrunnerRuns.clear();");
		assertContains(read(FOCUS_ABILITY_STATE), "AttunedServerCleanup.onStop(() -> {");
		assertContains(read(FOCUS_ABILITY_STATE), "COOLDOWNS.clear();");
		assertContains(read(ALTAR_ANIMATIONS), "AttunedServerCleanup.onStop(() -> {");
		assertContains(read(ALTAR_ANIMATIONS), "serverTick = 0;");
		assertContains(read(ONBOARDING), "AttunedServerCleanup.onStop(() -> tickCounter = 0)");
	}

	@Test
	void rawServerStopHooksStayCentralizedExceptHarpoonServerSweep() throws IOException {
		List<String> hooks = directServerStopHooks();

		assertEquals(2, hooks.size(),
			"Only AttunedServerCleanup and Harpoon's server-wide entity sweep should own raw server-stop hooks: "
				+ hooks);
		assertTrue(hooks.stream().anyMatch(hook ->
				hook.contains("AttunedServerCleanup.java")
					&& hook.contains("ServerLifecycleEvents.SERVER_STOPPED.register")),
			"The central server cleanup coordinator should own the ordinary server-stop hook");
		assertTrue(hooks.stream().anyMatch(hook ->
				hook.contains("HarpoonBehavior.java")
					&& hook.contains("HarpoonBehavior::removeAllTemporaryHarpoons")),
			"Harpoon keeps a raw hook because its cleanup needs the MinecraftServer to scan loaded levels");
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

	private static List<String> directServerStopHooks() throws IOException {
		List<String> hooks = new ArrayList<>();
		try (var paths = Files.walk(SOURCE_ROOT)) {
			for (Path file : paths
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java"))
					.toList()) {
				String[] lines = read(file).split("\\R");
				for (int i = 0; i < lines.length; i++) {
					if (lines[i].contains("ServerLifecycleEvents.SERVER_STOPPED.register")) {
						hooks.add(file.toString() + ":" + (i + 1) + ": " + lines[i].trim());
					}
				}
			}
		}
		return hooks;
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
