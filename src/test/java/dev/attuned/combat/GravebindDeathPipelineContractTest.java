package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for Gravebind's refused-death pipeline. */
class GravebindDeathPipelineContractTest {
	private static final Path GRAVEBIND =
		Path.of("src/main/java/dev/attuned/combat/GravebindSave.java");
	private static final Path RESONANCE =
		Path.of("src/main/java/dev/attuned/combat/Resonance.java");
	private static final Path UNSEEN_COMBAT =
		Path.of("src/main/java/dev/attuned/combat/UnseenCombat.java");

	@Test
	void gravebindRescueResetsKillStreakWithoutClearingVeilState() throws IOException {
		String gravebind = read(GRAVEBIND);
		String rescue = methodBody(gravebind, "private static void rescue(ServerPlayer player)");

		assertTrue(gravebind.contains("import dev.attuned.combat.Resonance;"),
			"Gravebind should reset combat streak state through Resonance.");
		assertTrue(rescue.contains("Resonance.resetKillStreak(player)"),
			"A refused death should end the rolling kill streak like a real death.");
		assertBefore(rescue, "Resonance.resetKillStreak(player)", "player.setHealth(");
		assertFalse(rescue.contains("removeAllEffects()"),
			"Gravebind rescue should not strip mob effects while the player remains alive.");
		assertFalse(rescue.contains("VeilBehavior.forgetDeath"),
			"Gravebind rescue should not clear Veil state — the player never died.");
	}

	@Test
	void resonanceExposesKillStreakResetForNearDeathHooks() throws IOException {
		String resonance = read(RESONANCE);

		assertTrue(resonance.contains("public static void resetKillStreak(Player player)"),
			"Near-death hooks such as Gravebind need a public kill-streak reset entry point.");
		assertTrue(resonance.contains("resetKillStreak(player.getUUID())"),
			"The public reset should delegate to the server-owned streak maps.");
	}

	@Test
	void veilStateStillClearsOnlyOnActualDeath() throws IOException {
		String unseen = read(UNSEEN_COMBAT);

		assertTrue(unseen.contains("VeilBehavior.forgetDeath(entity.getUUID())"),
			"Veil should still drop only on real AFTER_DEATH, not on Gravebind refusal.");
		assertFalse(read(GRAVEBIND).contains("VeilBehavior"),
			"Gravebind should not touch Veil lifecycle directly.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
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

	private static void assertBefore(String source, String earlier, String later) {
		int earlierIndex = source.indexOf(earlier);
		int laterIndex = source.indexOf(later);
		assertTrue(earlierIndex >= 0, "Missing source fragment: " + earlier);
		assertTrue(laterIndex >= 0, "Missing source fragment: " + later);
		assertTrue(earlierIndex < laterIndex, "Expected `" + earlier + "` before `" + later + "`.");
	}
}
