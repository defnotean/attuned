package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source guardrails for three combat hot-path perf fixes:
 * a Lodestone tick throttle, batched Resonance decay, and a Thornward/Leech
 * clamp against the victim's health pool so the Apex Execute sentinel cannot
 * reflect or leech tens of thousands of damage in PvP.
 */
class CombatPerfGatesContractTest {
	private static final Path LODESTONE_SOURCE =
		Path.of("src/main/java/dev/attuned/content/behavior/LodestoneBehavior.java");
	private static final Path RESONANCE_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/Resonance.java");
	private static final Path ATTUNED_COMBAT_SOURCE =
		Path.of("src/main/java/dev/attuned/combat/AttunedCombat.java");

	@Test
	void lodestonePullSkipsEveryOtherTick() throws IOException {
		String onTick = methodBody(read(LODESTONE_SOURCE),
			"public void onTick(ServerPlayer player, ItemStack focus)");

		assertTrue(onTick.contains("player.tickCount % 2 != 0"),
			"Lodestone should gate its entity query on a % 2 tick throttle like Epitaph.");
		int gate = onTick.indexOf("player.tickCount % 2 != 0");
		int query = onTick.indexOf("getEntitiesOfClass");
		assertTrue(query < 0 || gate < query,
			"The % 2 throttle should guard the getEntitiesOfClass query, not run after it.");
	}

	@Test
	void resonanceDecayBatchesEveryTwentyTicks() throws IOException {
		String tick = methodBody(read(RESONANCE_SOURCE),
			"private static void tick(MinecraftServer server)");

		assertTrue(tick.contains("% 20"),
			"Resonance decay should apply on a 20-tick cadence instead of writing the attachment every tick.");
		assertTrue(tick.contains("resonanceDecayPerTick() * 20"),
			"Batched decay should drain 20x the per-tick rate to preserve the rest-decay curve.");
	}

	@Test
	void thornwardAndLeechClampAgainstVictimHealthPool() throws IOException {
		String afterDamage = methodBody(read(ATTUNED_COMBAT_SOURCE),
			"private static void afterDamage(LivingEntity defender, DamageSource source,");

		assertTrue(afterDamage.contains("Math.min(dealtDamage, defender.getMaxHealth())"),
			"Thornward reflect and Leech heal should clamp dealt damage to the victim's pool "
				+ "so Apex Execute's sentinel cannot reflect or leech a huge amount in PvP.");
		assertTrue(!afterDamage.contains("dealtDamage * THORNWARD_REFLECT"),
			"Thornward reflect should scale off the clamped figure, not raw dealt damage.");
		assertTrue(!afterDamage.contains("dealtDamage * LEECH_LIFESTEAL"),
			"Leech heal should scale off the clamped figure, not raw dealt damage.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected source file to exist: " + path);
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
}
