package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
	private static final Path HARPOON =
		Path.of("src/main/java/dev/attuned/content/behavior/HarpoonBehavior.java");
	private static final Path TREMOR =
		Path.of("src/main/java/dev/attuned/content/behavior/TremorBehavior.java");
	private static final Path VEIL =
		Path.of("src/main/java/dev/attuned/content/behavior/VeilBehavior.java");
	private static final Path SOFTSTEP =
		Path.of("src/main/java/dev/attuned/content/behavior/SoftstepBehavior.java");
	private static final Path AEGIS =
		Path.of("src/main/java/dev/attuned/content/behavior/AegisBehavior.java");
	private static final Path DELVER =
		Path.of("src/main/java/dev/attuned/content/behavior/DelverBehavior.java");
	private static final Path EMBERWARD =
		Path.of("src/main/java/dev/attuned/content/behavior/EmberwardBehavior.java");
	private static final Path HARBORLIGHT =
		Path.of("src/main/java/dev/attuned/content/behavior/HarborlightBehavior.java");
	private static final Path HARVEST =
		Path.of("src/main/java/dev/attuned/content/behavior/HarvestBehavior.java");
	private static final Path HEARTH =
		Path.of("src/main/java/dev/attuned/content/behavior/HearthBehavior.java");
	private static final Path LANTERN =
		Path.of("src/main/java/dev/attuned/content/behavior/LanternBehavior.java");
	private static final Path NIGHTGAZE =
		Path.of("src/main/java/dev/attuned/content/behavior/NightgazeBehavior.java");
	private static final Path STORMCALL =
		Path.of("src/main/java/dev/attuned/content/behavior/StormcallBehavior.java");
	private static final Path TIDE =
		Path.of("src/main/java/dev/attuned/content/behavior/TideBehavior.java");
	private static final Path RADIANT_BEHAVIORS =
		Path.of("src/main/java/dev/attuned/content/behavior/RadiantFocusBehaviors.java");
	private static final List<String> RUNTIME_EVENT_HOOKS = List.of(
		"PlayerBlockBreakEvents.AFTER.register",
		"ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register",
		"ServerLifecycleEvents.SERVER_STOPPING.register",
		"ServerLivingEntityEvents.ALLOW_DAMAGE.register",
		"ServerLivingEntityEvents.ALLOW_DEATH.register",
		"ServerLivingEntityEvents.AFTER_DAMAGE.register",
		"ServerLivingEntityEvents.AFTER_DEATH.register",
		"ServerPlayConnectionEvents.DISCONNECT.register",
		"ServerPlayConnectionEvents.JOIN.register",
		"ServerPlayerEvents.AFTER_RESPAWN.register",
		"ServerTickEvents.END_SERVER_TICK.register"
	);

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
		assertTrue(source.contains("runPlayerCleanup(cleanup, handler.player)"),
			"Player cleanup callbacks should be isolated so one failure does not block later callbacks");
		assertTrue(source.contains("runUuidCleanup(cleanup, id)"),
			"UUID cleanup callbacks should be isolated so one failure does not block later callbacks");
		assertTrue(source.contains("catch (RuntimeException e)"),
			"Player cleanup should catch runtime failures from registered callbacks");
		assertTrue(source.contains("Attuned.LOGGER.warn(\"Attuned player cleanup callback failed"),
			"Player cleanup failures should be logged");
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
		assertTrue(source.contains("ServerLifecycleEvents.SERVER_STOPPING.register"),
			"Server cleanup should run while live players and worlds are still available");
		assertTrue(source.contains("private static final List<Consumer<MinecraftServer>> SERVER_CALLBACKS"),
			"Server cleanup should support callbacks that need the stopping server");
		assertTrue(source.contains("public static void onStopServer(Consumer<MinecraftServer> cleanup)"),
			"Server cleanup should expose a server-aware callback registration path");
		assertTrue(source.contains("cleanup.accept(server)"),
			"Server-aware cleanup callbacks should receive the stopping server");
		assertTrue(source.contains("Objects.requireNonNull(cleanup, \"cleanup\")"),
			"Server cleanup callback registration should reject null callbacks immediately");
		assertTrue(source.contains("runServerCleanup(cleanup, server)"),
			"Server-aware cleanup callbacks should be isolated from each other");
		assertTrue(source.contains("runCleanup(cleanup)"),
			"Runnable cleanup callbacks should be isolated from each other");
		assertTrue(source.contains("catch (RuntimeException e)"),
			"Server cleanup should catch runtime failures from registered callbacks");
		assertTrue(source.contains("Attuned.LOGGER.warn(\"Attuned server cleanup callback failed"),
			"Server cleanup failures should be logged");
		assertTrue(read(ATTUNED).contains("AttunedServerCleanup.init()"),
			"The mod initializer should install the central server cleanup hook");
	}

	@Test
	void cleanupCoordinatorsIterateSnapshotsDuringTeardown() throws IOException {
		String playerCleanup = read(PLAYER_CLEANUP);
		String serverCleanup = read(SERVER_CLEANUP);

		assertTrue(playerCleanup.contains("for (Consumer<ServerPlayer> cleanup : List.copyOf(PLAYER_CALLBACKS))"),
			"Player cleanup should iterate a snapshot so callback registration during teardown cannot interrupt cleanup.");
		assertTrue(playerCleanup.contains("for (Consumer<UUID> cleanup : List.copyOf(CALLBACKS))"),
			"UUID cleanup should iterate a snapshot so callback registration during teardown cannot interrupt cleanup.");
		assertTrue(serverCleanup.contains("for (Consumer<MinecraftServer> cleanup : List.copyOf(SERVER_CALLBACKS))"),
			"Server-aware cleanup should iterate a snapshot so callback registration during teardown cannot interrupt cleanup.");
		assertTrue(serverCleanup.contains("for (Runnable cleanup : List.copyOf(CALLBACKS))"),
			"Runnable cleanup should iterate a snapshot so callback registration during teardown cannot interrupt cleanup.");
	}

	@Test
	void focusEffectRuntimeRegistersHooksOnce() throws IOException {
		String effects = read(ATTUNED_EFFECTS);

		assertTrue(effects.contains("private static boolean initialized;"),
			"Focus effects should guard against duplicate tick, respawn, and cleanup hook registration");
		assertTrue(effects.contains("if (initialized)"),
			"Focus effects should skip repeated init calls");
		assertTrue(effects.contains("initialized = true;"),
			"Focus effects should mark hooks as registered before installing callbacks");
		assertBefore(effects, "initialized = true;", "ServerTickEvents.END_SERVER_TICK.register");
		assertBefore(effects, "initialized = true;", "ServerPlayerEvents.AFTER_RESPAWN.register");
		assertBefore(effects, "initialized = true;", "AttunedPlayerCleanup.onForgetPlayer");
		assertBefore(effects, "initialized = true;", "AttunedServerCleanup.onStopServer");
	}

	@Test
	void directRuntimeEventHooksAreInitIdempotent() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path file : directRuntimeEventHookFiles()) {
			String source = read(file);
			if (!source.contains("private static boolean initialized;")) {
				violations.add(file + ": missing initialized field");
				continue;
			}
			if (!source.contains("if (initialized)")) {
				violations.add(file + ": repeated init calls are not skipped");
			}
			if (!source.contains("initialized = true;")) {
				violations.add(file + ": initialized is never set");
				continue;
			}
			for (String marker : RUNTIME_EVENT_HOOKS) {
				if (source.contains(marker)) {
					try {
						assertBefore(source, "initialized = true;", marker);
					} catch (AssertionError e) {
						violations.add(file + ": guard is set after " + marker);
					}
				}
			}
		}

		assertTrue(violations.isEmpty(),
			"Direct runtime event hooks should be registered idempotently: " + violations);
	}

	@Test
	void runtimeCachesRegisterServerStopCleanup() throws IOException {
		String effects = read(ATTUNED_EFFECTS);
		assertContains(effects, "deactivateTrackedFoci(oldPlayer)");
		assertContains(effects, "AttunedPlayerCleanup.onForgetPlayer(AttunedEffects::deactivateTrackedFoci)");
		assertContains(effects, "AttunedServerCleanup.onStopServer(AttunedEffects::deactivateAllTrackedFoci)");
		assertContains(effects, "private static void deactivateAllTrackedFoci(MinecraftServer server)");
		assertContains(effects, "private static void deactivateTrackedFoci(ServerPlayer player)");
		assertContains(effects, "removeFocus(player, entry.getKey(), entry.getValue())");
		assertContains(effects, "Attuned.LOGGER.warn(\"Attuned Focus deactivation failed");
		assertContains(effects, "ACTIVE.clear();");
		assertContains(effects, "DORMANT.clear();");
		assertContains(effects, "auraTick = 0;");
		assertTrue(!effects.contains("AttunedPlayerCleanup.onForget(ACTIVE::remove)"),
			"Player cleanup should deactivate tracked Foci before dropping active snapshots");
		assertTrue(!effects.contains("AttunedPlayerCleanup.onForget(DORMANT::remove)"),
			"Player cleanup should use the deactivation helper instead of raw dormant-state removal");
		assertContains(read(ATTUNED_COMBAT), "AttunedServerCleanup.onStop(() -> {");
		assertContains(read(ATTUNED_COMBAT), "LAST_AFFINITY_SPARK.clear();");
		assertContains(read(UNSEEN_COMBAT), "AttunedServerCleanup.onStop(LAST_NEEDLE::clear)");
		assertContains(read(REVENANT_COMBAT), "DEBTS.clear();");
		assertContains(read(REVENANT_COMBAT), "LAST_RITES.clear();");
		assertContains(read(GRAVEBIND_SAVE), "AttunedServerCleanup.onStop(lastSave::clear)");
		assertContains(read(ALTAR_NETWORKING), "AttunedServerCleanup.onStop(LAST_BIND_TICK::clear)");
		assertContains(read(APEX), "AttunedServerCleanup.onStop(() -> {");
		assertContains(read(APEX), "maelstromScrambles.clear();");
		assertContains(read(APEX), "key.playerId().equals(uuid) || key.targetId().equals(uuid)");
		String pacts = read(PACTS);
		assertContains(pacts, "AttunedServerCleanup.onStopServer(server -> {");
		assertContains(pacts, "for (ServerPlayer player : server.getPlayerList().getPlayers())");
		assertContains(pacts, "removeWindrunnerStepHeight(player);");
		assertContains(pacts, "AttunedPlayerCleanup.onForgetPlayer(player -> {");
		assertContains(pacts, "windrunnerRuns.clear();");
		assertContains(read(FOCUS_ABILITY_STATE), "AttunedServerCleanup.onStop(() -> {");
		assertContains(read(FOCUS_ABILITY_STATE), "COOLDOWNS.clear();");
		assertContains(read(ALTAR_ANIMATIONS), "AttunedServerCleanup.onStop(() -> {");
		assertContains(read(ALTAR_ANIMATIONS), "serverTick = 0;");
		assertContains(read(ONBOARDING), "AttunedServerCleanup.onStop(() -> tickCounter = 0)");
		assertContains(read(HARPOON), "AttunedServerCleanup.onStopServer(HarpoonBehavior::removeAllTemporaryHarpoons)");
		assertContains(read(TREMOR), "AttunedServerCleanup.onStop(LAST_SCAN::clear)");
		assertContains(read(VEIL), "AttunedServerCleanup.onStopServer(VeilBehavior::forgetAllPlayers)");
		assertContains(read(VEIL), "STATES.clear();");
		assertContains(read(VEIL), "BLOCKED_UNTIL.clear();");
		assertContains(read(SOFTSTEP), "AttunedServerCleanup.onStopServer(this::restoreAllPlayers)");
		assertContains(read(SOFTSTEP), "player.setSilent(wasSilent);");
		assertContains(read(SOFTSTEP), "originalSilent.clear();");
		assertContains(read(AEGIS), "AttunedServerCleanup.onStop(ticksSinceGrant::clear)");
		assertContains(read(HARBORLIGHT), "AttunedServerCleanup.onStop(ticks::clear)");
		assertContains(read(HARVEST), "AttunedServerCleanup.onStop(ticks::clear)");
		assertContains(read(HEARTH), "AttunedServerCleanup.onStop(ticks::clear)");
		assertContains(read(LANTERN), "AttunedServerCleanup.onStop(ticks::clear)");
		assertContains(read(STORMCALL), "AttunedServerCleanup.onStop(ticks::clear)");
		String radiantBehaviors = read(RADIANT_BEHAVIORS);
		assertOccurrences(radiantBehaviors, "AttunedServerCleanup.onStop(state::clear)", 2);
		assertContains(radiantBehaviors, "AttunedServerCleanup.onStop(cooldowns::clear)");
		assertContains(radiantBehaviors, "AttunedServerCleanup.onStop(ticks::clear)");
		assertContains(radiantBehaviors, "AttunedServerCleanup.onStop(states::clear)");
	}

	@Test
	void focusEffectCleanupRegistersBeforeBehaviorFallbackCleanup() throws IOException {
		String source = read(ATTUNED);

		assertBefore(source, "AttunedPlayerCleanup.init()", "AttunedEffects.init()");
		assertBefore(source, "AttunedServerCleanup.init()", "AttunedEffects.init()");
		assertBefore(source, "AttunedEffects.init()", "AttunedContent.init()");
	}

	@Test
	void rawServerStopHooksStayCentralized() throws IOException {
		List<String> hooks = directServerStopHooks();

		assertEquals(1, hooks.size(), "Only AttunedServerCleanup should own raw server-stop hooks: " + hooks);
		assertTrue(hooks.stream().anyMatch(hook ->
				hook.contains("AttunedServerCleanup.java")
					&& hook.contains("ServerLifecycleEvents.SERVER_STOPPING.register")),
			"The central server cleanup coordinator should own the ordinary server-stopping hook");
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

	@Test
	void passiveUtilityEffectsRefreshOnlyNearExpiry() throws IOException {
		for (Path behavior : List.of(DELVER, EMBERWARD, NIGHTGAZE, TIDE)) {
			String source = read(behavior);
			assertContains(source, "PassiveEffectRefresher.refresh(");
			assertTrue(!source.contains("player.addEffect(new MobEffectInstance"),
				behavior + " should not allocate and send a new passive effect instance every tick");
		}
	}

	private static void assertContains(String source, String needle) {
		assertTrue(source.contains(needle), "Expected source to contain: " + needle);
	}

	private static void assertOccurrences(String source, String needle, int expected) {
		int count = 0;
		int offset = 0;
		while ((offset = source.indexOf(needle, offset)) >= 0) {
			count++;
			offset += needle.length();
		}
		assertEquals(expected, count, "Expected " + expected + " occurrences of: " + needle);
	}

	private static void assertBefore(String source, String earlier, String later) {
		int earlierIndex = source.indexOf(earlier);
		int laterIndex = source.indexOf(later);
		assertTrue(earlierIndex >= 0, "Expected source to contain: " + earlier);
		assertTrue(laterIndex >= 0, "Expected source to contain: " + later);
		assertTrue(earlierIndex < laterIndex, "Expected " + earlier + " before " + later);
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
					if (lines[i].contains("ServerLifecycleEvents.SERVER_STOPPING.register")) {
						hooks.add(file.toString() + ":" + (i + 1) + ": " + lines[i].trim());
					}
				}
			}
		}
		return hooks;
	}

	private static List<Path> directRuntimeEventHookFiles() throws IOException {
		List<Path> files = new ArrayList<>();
		try (var paths = Files.walk(SOURCE_ROOT)) {
			for (Path file : paths
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java"))
					.toList()) {
				String source = read(file);
				if (RUNTIME_EVENT_HOOKS.stream().anyMatch(source::contains)) {
					files.add(file);
				}
			}
		}
		return files;
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
