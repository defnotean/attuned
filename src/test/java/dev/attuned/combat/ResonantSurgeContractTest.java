package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-grep contract coverage for the resonant-surge runtime: config keys and
 * their documented defaults, init wiring after {@link Resonance}, the throttled
 * tick, the active-surge cleanup, and the {@link Resonance#grantSurge} hook.
 */
class ResonantSurgeContractTest {
	private static final Path ATTUNED =
		Path.of("src/main/java/dev/attuned/Attuned.java");
	private static final Path CONFIG =
		Path.of("src/main/java/dev/attuned/AttunedConfig.java");
	private static final Path SURGES =
		Path.of("src/main/java/dev/attuned/combat/ResonantSurges.java");
	private static final Path RESONANCE =
		Path.of("src/main/java/dev/attuned/combat/Resonance.java");
	private static final Path REFERENCE =
		Path.of("docs/reference.md");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void configCarriesTheSurgeKeysWithDocumentedDefaults() throws IOException {
		String config = read(CONFIG);
		assertContains(config, "surge_interval_ticks");
		assertContains(config, "surge_duration_ticks");
		assertContains(config, "surge_radius");
		assertContains(config, "int surgeIntervalTicks");
		assertContains(config, "int surgeDurationTicks");
		assertContains(config, "int surgeRadius");
		assertContains(config, "12000, 1200, 16");

		String reference = read(REFERENCE);
		assertContains(reference, "`surge_interval_ticks` | 12000");
		assertContains(reference, "`surge_duration_ticks` | 1200");
		assertContains(reference, "`surge_radius` | 16");
	}

	@Test
	void initIsWiredDirectlyAfterResonance() throws IOException {
		String source = read(ATTUNED);
		assertContains(source, "ResonantSurges.init();");
		assertContains(source, "import dev.attuned.combat.ResonantSurges;");
		assertBefore(source, "Resonance.init();", "ResonantSurges.init();");
	}

	@Test
	void surgeModuleIsAThrottledServerTickHandler() throws IOException {
		String source = read(SURGES);
		assertContains(source, "ServerTickEvents.END_SERVER_TICK.register");
		assertContains(source, "% 20");
		assertContains(source, "ResonantSurgeResolver.shouldStart");
		assertContains(source, "Heightmap.Types.WORLD_SURFACE");
		assertContains(source, "getHeight(");
	}

	@Test
	void surgeModuleGrantsResonanceThroughTheNamedHook() throws IOException {
		String surges = read(SURGES);
		assertContains(surges, "Resonance.grantSurge");
		assertContains(surges, "ResonantSurgeResolver.resonanceGainMultiplier()");

		String resonance = read(RESONANCE);
		assertContains(resonance, "public static void grantSurge(Player player, float amount)");
	}

	@Test
	void surgeModuleIsInitIdempotentAndClearsTheActiveSurgeOnStop() throws IOException {
		String source = read(SURGES);
		assertContains(source, "private static boolean initialized;");
		assertContains(source, "if (initialized)");
		assertContains(source, "initialized = true;");
		assertBefore(source, "initialized = true;", "ServerTickEvents.END_SERVER_TICK.register");
		assertContains(source, "AttunedServerCleanup.onStop(");
	}

	@Test
	void surgeActionbarStringsAreLocalised() throws IOException {
		String lang = read(LANG);
		assertContains(lang, "\"surge.attuned.faded\"");
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
}
