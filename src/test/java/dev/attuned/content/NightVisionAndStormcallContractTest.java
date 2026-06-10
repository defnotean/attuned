package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NightVisionAndStormcallContractTest {
	private static final Path NIGHTGAZE =
		Path.of("src/main/java/dev/attuned/content/behavior/NightgazeBehavior.java");
	private static final Path HARBORLIGHT =
		Path.of("src/main/java/dev/attuned/content/behavior/HarborlightBehavior.java");
	private static final Path STORMCALL =
		Path.of("src/main/java/dev/attuned/content/behavior/StormcallBehavior.java");

	@Test
	void nightVisionFociStayAboveTheClientFlickerWindow() throws IOException {
		String nightgaze = read(NIGHTGAZE);
		String harborlight = read(HARBORLIGHT);

		assertTrue(nightgaze.contains("private static final int DURATION = 600"),
			"Nightgaze should grant a long enough Night Vision duration to avoid constant client flicker.");
		assertTrue(harborlight.contains("private static final int DURATION = 600"),
			"Harborlight should keep conditional Night Vision well above the client flicker window.");
		assertTrue(harborlight.contains(
				"PassiveEffectRefresher.refresh(player, MobEffects.NIGHT_VISION, DURATION, 0, true, false, false)"),
			"Harborlight should refresh Night Vision near expiry instead of resending it every check.");
		assertTrue(!harborlight.contains("player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION"),
			"Harborlight should share the passive refresher instead of allocating the effect directly.");
	}

	@Test
	void stormcallChecksRainAtThePlayersBodyInsteadOfTheirFeet() throws IOException {
		String stormcall = read(STORMCALL);

		assertTrue(stormcall.contains("private static boolean isOpenRainAt(ServerPlayer player, ServerLevel level)"),
			"Stormcall should isolate its rain-position check.");
		assertTrue(stormcall.contains("level.isRainingAt(player.blockPosition().above())"),
			"Stormcall should test rain at the player's body/head air block, not the foot block.");
		assertTrue(!stormcall.contains("level.isRainingAt(player.blockPosition())"),
			"Stormcall's strike timer should not be gated by the foot-level rain check.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
