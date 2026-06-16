package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-grep contract pins for Affinity Loom — the Reweaving II altar mode that
 * rerolls one Focus into another with the same affinity at escalating shard cost.
 */
class ReweavingAffinityLoomContractTest {
	private static final Path REWEAVING_MENU =
		Path.of("src/main/java/dev/attuned/menu/ReweavingMenu.java");
	private static final Path REWEAVING_NETWORKING =
		Path.of("src/main/java/dev/attuned/menu/ReweavingNetworking.java");
	private static final Path REWEAVING_SCREEN =
		Path.of("src/client/java/dev/attuned/client/screen/ReweavingScreen.java");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void reweavingMenuExposesAffinityLoomGateAndEscalatingCost() throws IOException {
		String menu = read(REWEAVING_MENU);
		assertTrue(menu.contains("int affinityLoomRerolls = 0"),
			"ReweavingMenu should track loom rerolls per menu session.");
		assertTrue(menu.contains("canAffinityLoom()"),
			"ReweavingMenu should expose canAffinityLoom().");
		assertTrue(menu.contains("affinityLoomShardCost()"),
			"ReweavingMenu should expose escalating shard cost.");
		assertTrue(menu.contains("AttunedConfig.get()"),
			"Shard cost should read Affinity Loom settings from config.");
		assertTrue(menu.contains("affinityLoomMaxShardCost()")
				&& menu.contains("affinityLoomBaseShardCost()"),
			"Shard cost should use base + rerolls capped by max.");
		assertTrue(menu.contains("incrementAffinityLoomRerolls()"),
			"ReweavingMenu should increment rerolls after a successful loom roll.");
		assertTrue(menu.contains("ATTUNEMENT_SHARD"),
			"Affinity Loom catalyst should accept Attunement Shards.");
	}

	@Test
	void reweavingNetworkingRollsSameAffinityAfterTemperGate() throws IOException {
		String networking = read(REWEAVING_NETWORKING);
		assertTrue(networking.contains("menu.canAffinityLoom()"),
			"Server-side reweaving should branch to Affinity Loom when canAffinityLoom().");
		assertTrue(networking.contains("ReweavingResultPicker.pickSameAffinity"),
			"Affinity Loom should roll via pickSameAffinity.");
		assertBefore(networking, "menu.canTemper()", "menu.canAffinityLoom()",
			"Affinity Loom should be checked after temper and before classic reweave.");
		assertBefore(networking, "menu.canAffinityLoom()", "hasThreeFociAndFragment",
			"Affinity Loom should be checked before the three-Focus reweave gate.");
		assertTrue(networking.contains("menu.incrementAffinityLoomRerolls()"),
			"Successful loom rolls should increment the session reroll counter.");
	}

	@Test
	void reweavingScreenCommitsAndHintsAffinityLoom() throws IOException {
		String screen = read(REWEAVING_SCREEN);
		assertTrue(screen.contains("canAffinityLoom()"),
			"Reweaving screen should enable commit when Affinity Loom is ready.");
		assertTrue(screen.contains("screen.attuned.reweaving_altar.hint.affinity_loom"),
			"Reweaving screen should show the Affinity Loom hint.");
		assertTrue(screen.contains("affinityLoomShardCost()"),
			"Affinity Loom hint should pass the escalating shard cost.");
	}

	@Test
	void affinityLoomLangKeyExists() throws IOException {
		String lang = read(LANG);
		assertTrue(lang.contains("\"screen.attuned.reweaving_altar.hint.affinity_loom\""),
			"en_us.json should define the Affinity Loom hint.");
	}

	private static void assertBefore(String source, String earlier, String later, String message) {
		int e = source.indexOf(earlier);
		int l = source.indexOf(later);
		assertTrue(e >= 0 && l >= 0 && e < l, message);
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
