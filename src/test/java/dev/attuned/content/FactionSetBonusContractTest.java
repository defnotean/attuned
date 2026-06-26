package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Source-grep contract coverage for the faction set-bonus runtime: init wiring,
 * the throttle, the pure-resolver delegation, cleanup registration, and that
 * every faction the perk table touches is documented.
 */
class FactionSetBonusContractTest {
	private static final Path ATTUNED =
		Path.of("src/main/java/dev/attuned/Attuned.java");
	private static final Path BONUSES =
		Path.of("src/main/java/dev/attuned/content/behavior/FactionSetBonuses.java");
	private static final Path REFERENCE =
		Path.of("docs/reference.md");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	/** Every faction the applier grants a perk for. */
	private static final List<String> FACTION_IDS = List.of(
		"attuned:unseen",
		"attuned:seafarers",
		"attuned:offshore",
		"attuned:tideborn",
		"attuned:forgebound",
		"attuned:wildroot",
		"attuned:radiant",
		"attuned:reliquary",
		"attuned:verdant_choir",
		"attuned:ashen_forge",
		"attuned:revenant",
		"attuned:umbral",
		"attuned:deep_lanterns");

	@Test
	void initIsWiredDirectlyAfterAttunedEffects() throws IOException {
		String source = read(ATTUNED);
		assertContains(source, "FactionSetBonuses.init();");
		assertContains(source, "import dev.attuned.content.behavior.FactionSetBonuses;");
		assertBefore(source, "AttunedEffects.init();", "FactionSetBonuses.init();");
		assertBefore(source, "FactionSetBonuses.init();", "AttunedComponents.init();");
	}

	@Test
	void applierIsAThrottledServerTickHandler() throws IOException {
		String source = read(BONUSES);
		assertContains(source, "ServerTickEvents.END_SERVER_TICK.register");
		assertContains(source, "% 20");
	}

	@Test
	void applierDelegatesToThePureResolver() throws IOException {
		String source = read(BONUSES);
		assertContains(source, "FactionSetBonusResolver.activeSetFactions");
		assertContains(source, "FocusDefinition::faction");
		assertContains(source, "Attunement.resolution(player)");
	}

	@Test
	void factionSourceWalksOnlyBudgetResolvedActiveSlots() throws IOException {
		String source = read(BONUSES);
		String activeFactionIds = methodBody(source, "private static List<String> activeFactionIds(ServerPlayer player)");

		assertContains(activeFactionIds, "BudgetResolver.Resolution resolution = Attunement.resolution(player)");
		assertContains(activeFactionIds, "List<Integer> activeSlots = resolution.activeSlots()");
		assertContains(activeFactionIds, "new ArrayList<>(activeSlots.size())");
		assertContains(activeFactionIds, "for (int slot : activeSlots)");
		assertTrue(!activeFactionIds.contains("for (int slot = 0"),
			"Faction set bonuses must not scan every equipped slot because over-budget Foci are dormant.");
		assertTrue(!activeFactionIds.contains("AttunedInv.SIZE"),
			"Faction set bonuses should count the resolved active-awake slice, not raw inventory size.");
	}

	@Test
	void applierIsInitIdempotentAndRegistersCleanup() throws IOException {
		String source = read(BONUSES);
		assertContains(source, "private static boolean initialized;");
		assertContains(source, "if (initialized)");
		assertContains(source, "initialized = true;");
		assertBefore(source, "initialized = true;", "ServerTickEvents.END_SERVER_TICK.register");
		assertContains(source, "AttunedPlayerCleanup.onForget(RADIANT_COOLDOWNS::remove)");
		assertContains(source, "AttunedPlayerCleanup.onForget(VERDANT_COOLDOWNS::remove)");
		assertContains(source, "AttunedServerCleanup.onStop(");
		assertContains(source, "RADIANT_COOLDOWNS.clear();");
		assertContains(source, "VERDANT_COOLDOWNS.clear();");
	}

	@Test
	void everyFactionPerkIsDocumentedAndLocalised() throws IOException {
		String reference = read(REFERENCE);
		String lang = read(LANG);
		assertContains(reference, "Set bonus (3 active Foci)");
		for (String factionId : FACTION_IDS) {
			assertTrue(reference.contains("`" + factionId + "`"),
				"reference.md Factions table should document " + factionId);
			String key = factionId.replace("attuned:", "faction.attuned.set_bonus.");
			assertTrue(lang.contains("\"" + key + "\""),
				"en_us.json should carry a set-bonus line for " + factionId + " (" + key + ")");
		}
	}

	@Test
	void perkTableCoversEveryDocumentedFaction() throws IOException {
		String source = read(BONUSES);
		for (String factionId : FACTION_IDS) {
			assertTrue(source.contains("\"" + factionId + "\""),
				"FactionSetBonuses should handle the faction " + factionId);
		}
	}

	@Test
	void revenantUndeadChillUsesSharedHostileTargetGate() throws IOException {
		String source = read(BONUSES);
		String chill = methodBody(source, "private static void chillNearbyUndead(ServerPlayer player)");

		assertContains(source, "import dev.attuned.combat.CombatTargets;");
		assertContains(chill, "CombatTargets.isHostileOrPvpOpponent(entity, player)");
		assertBefore(chill, "CombatTargets.isHostileOrPvpOpponent(entity, player)",
			"entity.addEffect(new MobEffectInstance(");
	}

	@Test
	void deepLanternSetBonusIsContextualNightVision() throws IOException {
		String source = read(BONUSES);
		String perkTable = methodBody(source, "private static void applyPerk(ServerPlayer player, String faction)");

		assertContains(source, "FACTION_DEEP_LANTERNS = \"attuned:deep_lanterns\"");
		assertContains(perkTable, "case FACTION_DEEP_LANTERNS");
		assertContains(perkTable, "nearLantern(player)");
		assertContains(perkTable, "MobEffects.NIGHT_VISION");
	}

	private static void assertContains(String source, String needle) {
		assertTrue(source.contains(needle), "Expected source to contain: " + needle);
	}

	private static void assertBefore(String source, String earlier, String later) {
		int earlierIndex = source.indexOf(earlier);
		int laterIndex = source.indexOf(later);
		assertTrue(earlierIndex >= 0, "Expected source to contain: " + earlier);
		assertTrue(laterIndex >= 0, "Expected source to contain: " + later);
		assertTrue(earlierIndex < laterIndex, "Expected " + earlier + " before " + later);
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
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
}
