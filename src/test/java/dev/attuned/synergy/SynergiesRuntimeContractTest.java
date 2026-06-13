package dev.attuned.synergy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-grep contract coverage for the {@code Synergies} server-tick runtime: it
 * pins the wiring order, the throttle, the active-id-set bridge, the diff/last-state
 * map, the stable modifier-id discipline, behavior dispatch, the on-join reconcile,
 * the transition/advancement/fanfare hooks, and the cleanup registrations — the
 * runtime details a unit test cannot exercise without Bootstrapping Minecraft.
 */
class SynergiesRuntimeContractTest {
	private static final Path SYNERGIES =
		Path.of("src/main/java/dev/attuned/synergy/Synergies.java");
	private static final Path ATTUNED =
		Path.of("src/main/java/dev/attuned/Attuned.java");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void synergiesIsWiredAfterPactsAndBeforePactDeathMessages() throws IOException {
		String attuned = read(ATTUNED);
		assertBefore(attuned, "Pacts.init();", "Synergies.init();");
		assertBefore(attuned, "Synergies.init();", "PactDeathMessages.init();");
		assertTrue(attuned.contains("import dev.attuned.synergy.Synergies;"),
			"Attuned should import the Synergies runtime.");
	}

	@Test
	void synergiesInitIsIdempotentAndRegistersTickAndJoin() throws IOException {
		String synergies = read(SYNERGIES);
		assertTrue(synergies.contains("if (initialized) {"), "init() must be idempotent.");
		assertTrue(synergies.contains("initialized = true;"), "init() must set the guard.");
		assertTrue(synergies.contains("ServerTickEvents.END_SERVER_TICK.register(Synergies::tick)"),
			"Synergies must register a server-tick handler.");
		assertTrue(synergies.contains("ServerPlayConnectionEvents.JOIN.register"),
			"Synergies must register a JOIN handler for the on-join reconcile.");
		assertTrue(synergies.contains("(handler, sender, server) ->"),
			"JOIN callback should follow the 3-param arity pattern.");
		assertTrue(synergies.contains("handler.player"),
			"JOIN handler resolves the ServerPlayer via handler.player.");
	}

	@Test
	void tickThrottlesAndBuildsTheActiveIdSetThroughTheStringBridge() throws IOException {
		String synergies = read(SYNERGIES);
		assertTrue(synergies.contains("% 20"), "The tick must be throttled (no per-tick work).");
		String tickPlayer = methodBody(synergies, "private static void tickPlayer(");
		assertTrue(tickPlayer.contains("Attunement.activeSlots(player)"),
			"tickPlayer must iterate the active slots.");
		assertTrue(tickPlayer.contains("Attunement.definitionFor(player,"),
			"tickPlayer must resolve each slot's FocusDefinition.");
		assertTrue(tickPlayer.contains("BuiltInRegistries.ITEM.getKey("),
			"tickPlayer must key Foci by item id.");
		assertTrue(tickPlayer.contains(".value()).toString()"),
			"The Identifier->String bridge is load-bearing: members compare as Strings.");
		assertTrue(tickPlayer.contains("SynergyResolver.activeConfluences("),
			"tickPlayer must delegate activation to the pure resolver.");
	}

	@Test
	void lastStateMapAndDiffAreDeclared() throws IOException {
		String synergies = read(SYNERGIES);
		assertTrue(synergies.contains("Map<UUID, Set<String>> synergyState"),
			"A per-player last-state map drives gain/loss diffing.");
		assertTrue(synergies.contains("new HashMap<>()"), "The state map is a HashMap.");
	}

	@Test
	void modifierIdIsStablePerConfluence() throws IOException {
		String synergies = read(SYNERGIES);
		String modifierId = methodBody(synergies, "private static Identifier modifierId(");
		assertTrue(modifierId.contains("Identifier.fromNamespaceAndPath(Attuned.MOD_ID, \"confluence_\""),
			"Confluence modifier ids use a stable confluence_ prefixed namespace path.");
	}

	@Test
	void modifiersAreAppliedAndRemovedSymmetrically() throws IOException {
		String synergies = read(SYNERGIES);
		assertTrue(synergies.contains("addTransientModifier("),
			"Active Confluences apply their modifiers transiently.");
		assertTrue(synergies.contains("removeModifier(modifierId("),
			"Dropped Confluences remove their modifiers by the same stable id.");
	}

	@Test
	void behaviorHooksAreDispatchedThroughTheBehaviorRegistry() throws IOException {
		String synergies = read(SYNERGIES);
		assertTrue(synergies.contains("AttunedRegistries.getBehavior("),
			"Behavior-bearing Confluences resolve through the single behavior registry funnel.");
		assertTrue(synergies.contains("behavior.onActivate("),
			"A gained Confluence fires its behavior's onActivate.");
		assertTrue(synergies.contains("behavior.onDeactivate("),
			"A lost Confluence fires its behavior's onDeactivate.");
	}

	@Test
	void activeBehaviorsAreTickedEveryThrottledTickWithAnEmptyStack() throws IOException {
		String synergies = read(SYNERGIES);
		assertTrue(synergies.contains("Map<UUID, List<FocusBehavior>> activeBehaviors"),
			"A per-player active-behaviors map drives the per-tick Confluence onTick dispatch.");
		String tickPlayer = methodBody(synergies, "private static void tickPlayer(");
		assertTrue(tickPlayer.contains("tickActiveBehaviors("),
			"tickPlayer must tick every active Confluence behavior after diffing.");
		String tickActive = methodBody(synergies, "private static void tickActiveBehaviors(");
		assertTrue(tickActive.contains("AttunedRegistries.getBehavior("),
			"Active behaviors resolve through the single behavior registry funnel.");
		assertTrue(tickActive.contains("behavior.onTick(player, ItemStack.EMPTY)"),
			"An active Confluence ticks its behavior with an empty backing stack each throttled tick.");
	}

	@Test
	void onJoinReconcileStripsStaleModifiers() throws IOException {
		String synergies = read(SYNERGIES);
		String reconcile = methodBody(synergies, "private static void reconcileOnJoin(");
		assertTrue(reconcile.contains("removeModifiers("),
			"reconcileOnJoin strips every confluence modifier so stale NBT cannot accumulate.");
		assertTrue(reconcile.contains("synergyState.remove("),
			"reconcileOnJoin clears the cached state so the next tick reapplies cleanly.");
	}

	@Test
	void transitionsAnnounceAndAwardAndFanfare() throws IOException {
		String synergies = read(SYNERGIES);
		assertTrue(synergies.contains("confluence.attuned.gained"),
			"A gained Confluence announces via the gained lang key.");
		assertTrue(synergies.contains("confluence.attuned.faded"),
			"A lost Confluence announces via the faded lang key.");
		assertTrue(synergies.contains("AttunedAdvancements.award(player, \"attunement/confluence_\""),
			"A gained Confluence awards its code-granted advancement.");
		assertTrue(synergies.contains("\"confluence_first_\""),
			"First-discovery fanfare keys off a confluence_first_ onboarding marker.");
		assertTrue(synergies.contains("AttunedAttachments.sawOnboarding(player,"),
			"Fanfare checks the onboarding marker before firing.");
		assertBefore(synergies, "sawOnboarding(player", "markOnboarding(player");
		assertTrue(synergies.contains("AttunedAttachments.markConfluenceDiscovered("),
			"First-discovery fanfare must record the discovery for the journal.");
	}

	@Test
	void perPlayerAndServerCleanupAreRegistered() throws IOException {
		String synergies = read(SYNERGIES);
		assertTrue(synergies.contains("AttunedPlayerCleanup.onForgetPlayer("),
			"Per-player state must be cleaned on forget.");
		assertTrue(synergies.contains("AttunedServerCleanup.onStopServer("),
			"Server-wide state must be cleaned on stop.");
		assertTrue(synergies.contains("synergyState.clear();"), "Stop clears the state map.");
		assertTrue(synergies.contains("activeBehaviors.clear();"),
			"Stop clears the active-behaviors map.");
		assertTrue(synergies.contains("ticks = 0;"), "Stop resets the tick counter.");
	}

	@Test
	void runtimeLangKeysExist() throws IOException {
		String lang = read(LANG);
		assertTrue(lang.contains("\"confluence.attuned.gained\""), "Missing gained lang key.");
		assertTrue(lang.contains("\"confluence.attuned.faded\""), "Missing faded lang key.");
		assertTrue(lang.contains("\"confluence.attuned.first_discovery\""),
			"Missing first-discovery lang key.");
	}

	// --- helpers (copied from the existing contract-test idiom) ---

	private static void assertBefore(String source, String earlier, String later) {
		int earlierIndex = source.indexOf(earlier);
		int laterIndex = source.indexOf(later);
		assertTrue(earlierIndex >= 0, "Expected source to contain: " + earlier);
		assertTrue(laterIndex >= 0, "Expected source to contain: " + later);
		assertTrue(earlierIndex < laterIndex, "Expected " + earlier + " before " + later);
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

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
