package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-level guard for player-bound helpers that are awkward to unit-test
 * without bootstrapping Minecraft's runtime.
 */
class AttunementSourceContractTest {
	private static final Path SOURCE = Path.of("src/main/java/dev/attuned/attunement/Attunement.java");
	private static final Path EFFECTS_SOURCE =
		Path.of("src/main/java/dev/attuned/effect/AttunedEffects.java");

	@Test
	void hypotheticalCapacityActiveSlotsUsesProvidedCapacityWithoutMutatingPlayer() throws IOException {
		String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

		assertTrue(source.contains(
				"public static List<Integer> activeSlots(Player player, int hypotheticalCapacity)"),
			"Attunement should expose a hypothetical-capacity active slot helper");
		assertTrue(source.contains(
				"return BudgetResolver.resolve(candidates(player), hypotheticalCapacity);"),
			"Hypothetical active slot resolution should use the provided capacity value");
	}

	@Test
	void currentResolutionIsSharedByActiveSlotsAndDormantReasons() throws IOException {
		String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

		assertTrue(source.contains("public static BudgetResolver.Resolution resolution(Player player)"),
			"Attunement should expose one detailed resolution for callers that need active and dormant data");
		assertTrue(source.contains("return BudgetResolver.resolveDetailed(candidates(player), capacity(player));"),
			"The shared resolution should gather candidates once and resolve active plus dormant slots");
		assertTrue(source.contains("return resolution(player).activeSlots();"),
			"activeSlots should delegate to the shared current resolution");
		assertTrue(source.contains("return resolution(player).dormantReasons();"),
			"dormantReasons should delegate to the shared current resolution");
	}

	@Test
	void effectsTickUsesOneResolutionForActiveAndDormantState() throws IOException {
		String source = Files.readString(EFFECTS_SOURCE, StandardCharsets.UTF_8);

		assertTrue(source.contains("BudgetResolver.Resolution resolution = Attunement.resolution(player);"),
			"The per-player tick should resolve the attunement budget once");
		assertTrue(source.contains("List<Integer> currentActive = resolution.activeSlots();"),
			"The active slot list should come from the shared resolution");
		assertTrue(source.contains("Map<Integer, BudgetResolver.DormantReason> dormantReasons = resolution.dormantReasons();"),
			"Dormant reasons should come from the same shared resolution");
		assertTrue(!source.contains("Attunement.dormantReasons(player)"),
			"The per-player tick should not rerun resolution just to read dormant reasons");
		assertTrue(!source.contains("Attunement.isDiscord(player)"),
			"The per-player tick should derive Discord from the already-known active affinities");
		assertTrue(source.contains("spawnAura(player, activeAffinities)"),
			"Aura rendering should reuse the active affinities derived from the shared active slots");
		assertTrue(source.contains("private static ParticleOptions auraParticle(Set<Affinity> activeAffinities)"),
			"Aura particle choice should not recompute the player's active slots");
	}

	@Test
	void focusBehaviorCallbacksAreIsolated() throws IOException {
		String source = Files.readString(EFFECTS_SOURCE, StandardCharsets.UTF_8);

		assertTrue(source.contains("runBehaviorActivate(behavior, player, focus.stack())"),
			"Focus activation callbacks should be isolated through a logged helper");
		assertTrue(source.contains("runBehaviorDeactivate(behavior, player, focus.stack())"),
			"Focus deactivation callbacks should be isolated through a logged helper");
		assertTrue(source.contains("runBehaviorTick(behavior, player, focus.stack())"),
			"Focus tick callbacks should be isolated through a logged helper");
		assertTrue(source.contains("catch (RuntimeException e)"),
			"Focus behavior callback failures should be caught so one Focus cannot stop the whole tick");
		assertTrue(source.contains("Attuned.LOGGER.warn(\"Attuned Focus behavior"),
			"Focus behavior callback failures should be logged");
	}

	@Test
	void appliedFocusSnapshotDrivesRemovalAndTicks() throws IOException {
		String source = Files.readString(EFFECTS_SOURCE, StandardCharsets.UTF_8);
		String removeFocus = methodRegion(source, "private static void removeFocus", "private static void deactivateAllTrackedFoci");
		String tickFocus = methodRegion(source, "private static void tickFocus", "private record AppliedFocus");

		assertTrue(source.contains("private static final Map<UUID, Map<Integer, AppliedFocus>> ACTIVE"),
			"Active Focus state should store the effect payload that was actually applied");
		assertTrue(source.contains("new AppliedFocus(stack.copy(), List.copyOf(def.modifiers()), def.behavior())"),
			"Applied Focus snapshots should defensively copy the stack and modifier payload");
		assertTrue(source.contains("private record AppliedFocus(ItemStack stack, List<ModifierEntry> modifiers, Optional<Identifier> behavior)"),
			"Applied Focus snapshots should remember stack, modifiers, and behavior id");
		assertTrue(source.contains("sameAppliedFocus(previous, now)"),
			"Effect diffing should notice definition payload changes, not only stack changes");
		assertTrue(source.contains("private static void removeFocus(ServerPlayer player, int slot, AppliedFocus focus)"),
			"Focus removal should consume the applied snapshot rather than a bare stack");
		assertTrue(!removeFocus.contains("Attunement.definitionFor"),
			"Focus removal should not re-resolve the live datapack definition during teardown");
		assertTrue(!tickFocus.contains("Attunement.definitionFor"),
			"Focus ticking should use the behavior id that belongs to the active applied snapshot");
	}

	private static String methodRegion(String source, String start, String end) {
		int startIndex = source.indexOf(start);
		int endIndex = source.indexOf(end, startIndex);
		assertTrue(startIndex >= 0, "Expected source to contain: " + start);
		assertTrue(endIndex > startIndex, "Expected source to contain after " + start + ": " + end);
		return source.substring(startIndex, endIndex);
	}
}
