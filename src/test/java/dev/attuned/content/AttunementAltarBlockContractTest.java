package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Source-level guardrails for altar ambience that run without Minecraft
 * bootstrap.
 */
class AttunementAltarBlockContractTest {
	private static final Path ALTAR_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunementAltarBlock.java");
	private static final Pattern RADIUS = Pattern.compile(
		"PROXIMITY_PULSE_RADIUS\\s*=\\s*([0-9.]+)\\s*;");
	private static final Pattern COOLDOWN = Pattern.compile(
		"PROXIMITY_PULSE_COOLDOWN_TICKS\\s*=\\s*([0-9]+)L?\\s*;");

	@Test
	void proximityPulseStaysCosmeticGatedAndLowFrequency() throws IOException {
		String source = Files.readString(ALTAR_SOURCE, StandardCharsets.UTF_8);
		String proximitySource = between(source,
			"private static void tryProximityPulse",
			"private static AltarAffinity altarAffinityOf");

		assertEquals(5.0, doubleConstant(source, RADIUS), 0.25,
			"Altar proximity pulse radius should stay around five blocks");
		assertTrue(longConstant(source, COOLDOWN) >= 80,
			"Altar proximity pulse cooldown should stay at least 80 ticks");
		assertTrue(source.contains("!Attunement.activeSlots(player).isEmpty()"),
			"Altar proximity pulse should require at least one active Focus");
		assertTrue(source.contains("player.isLocalPlayer()"),
			"Altar proximity pulse should only react to the local client player");
		assertTrue(source.contains("record ProximityPulseKey"),
			"Altar proximity pulse cooldown should include dimension and position");
		assertTrue(source.contains("pruneProximityPulseCooldowns"),
			"Altar proximity pulse cooldown entries should be pruned");
		assertTrue(source.contains("java.util.concurrent.ConcurrentHashMap"),
			"Altar proximity pulse cooldowns should tolerate client ambience and server-stop cleanup touching the cache");
		assertTrue(source.contains("new ConcurrentHashMap<>()"),
			"Altar proximity pulse cooldowns should use a concurrent map implementation");
		assertTrue(source.contains("AttunedServerCleanup.onStop(PROXIMITY_PULSE_TICKS::clear)"),
			"Altar proximity pulse cooldowns should clear when the server stops");
		assertTrue(proximitySource.contains("level.addParticle("),
			"Altar proximity pulse should remain client-side particle ambience");
		assertTrue(!proximitySource.contains("playSound("),
			"Altar proximity pulse should not add sound spam");
		assertTrue(!proximitySource.contains("sendParticles("),
			"Altar proximity pulse should not become a server-side broadcast");
	}

	private static String between(String source, String start, String end) {
		int startIndex = source.indexOf(start);
		int endIndex = source.indexOf(end);
		assertTrue(startIndex >= 0, "Missing source section start: " + start);
		assertTrue(endIndex > startIndex, "Missing source section end: " + end);
		return source.substring(startIndex, endIndex);
	}

	private static double doubleConstant(String source, Pattern pattern) {
		Matcher matcher = pattern.matcher(source);
		assertTrue(matcher.find(), "Missing double constant matching " + pattern);
		return Double.parseDouble(matcher.group(1));
	}

	private static long longConstant(String source, Pattern pattern) {
		Matcher matcher = pattern.matcher(source);
		assertTrue(matcher.find(), "Missing long constant matching " + pattern);
		return Long.parseLong(matcher.group(1));
	}
}
