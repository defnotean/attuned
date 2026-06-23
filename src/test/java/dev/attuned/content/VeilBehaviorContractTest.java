package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for Veil's interaction with vanilla invisibility. */
class VeilBehaviorContractTest {
	private static final Path VEIL =
		Path.of("src/main/java/dev/attuned/content/behavior/VeilBehavior.java");
	private static final Path MASK =
		Path.of("src/main/java/dev/attuned/content/behavior/MaskBehavior.java");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void veilClearPreservesExternalInvisibilityEffects() throws IOException {
		String source = read(VEIL);
		String clear = methodBody(source,
			"private static void clear(ServerPlayer player, State state, boolean broken)");

		assertTrue(source.contains("import net.minecraft.world.effect.MobEffects;"),
			"Veil should know when vanilla invisibility is currently active.");
		assertEquals(1, countOccurrences(clear, "player.setInvisible(false)"),
			"Veil should have one path that removes only its own invisibility flag.");
		assertTrue(clear.contains("if (!player.hasEffect(MobEffects.INVISIBILITY))"),
			"Veil should not clear invisibility if another source is maintaining it.");
		assertBefore(clear, "if (!player.hasEffect(MobEffects.INVISIBILITY))", "player.setInvisible(false)");
		assertBefore(clear, "player.setInvisible(false)", "state.appliedInvisibility = false;");
	}

	@Test
	void revealCountersBreakVeilAndBlockImmediateRecharge() throws IOException {
		String source = read(VEIL);
		String onTick = methodBody(source, "public void onTick(ServerPlayer player, ItemStack focus)");
		String onAbility = methodBody(source, "public boolean onAbility(ServerPlayer player, ItemStack focus)");
		String isRevealed = methodBody(source, "private static boolean isRevealed(ServerPlayer player)");
		String lang = read(LANG);

		assertTrue(source.contains("REVEALED_BREAK_COOLDOWN_TICKS"),
			"Reveal counterplay should use a named cooldown distinct from normal movement breaks.");
		assertTrue(isRevealed.contains("player.hasEffect(MobEffects.GLOWING)"),
			"Vanilla Glowing is the shared reveal signal and should counter Veil.");
		assertTrue(onTick.contains("if (isRevealed(player))")
				&& onTick.contains("breakVeil(player, REVEALED_BREAK_COOLDOWN_TICKS)")
				&& onTick.contains("return;"),
			"Ticking a revealed Veil wearer should break Veil and stop charging for that tick.");
		assertBefore(onTick, "if (isRevealed(player))", "if (!canCharge(player, now))");
		assertTrue(onAbility.contains("if (isRevealed(player))")
				&& onAbility.contains("breakVeil(player, REVEALED_BREAK_COOLDOWN_TICKS)")
				&& onAbility.contains("return false;"),
			"The instant Veil ability should not bypass an active reveal counter.");
		assertTrue(lang.contains("item.attuned.veil_focus.effect")
				&& lang.contains("revealed"),
			"Veil tooltip should tell players that reveal effects are counterplay.");
	}

	@Test
	void maskDoesNotHideAnActiveVeilFromRevealCounters() throws IOException {
		String mask = read(MASK);
		String resistsReveal = methodBody(mask, "public static boolean resistsReveal(LivingEntity target)");
		String lang = read(LANG);

		assertTrue(resistsReveal.contains("VeilBehavior.isVeiled(player)"),
			"Mask should know when the target is already fully veiled.");
		assertBefore(resistsReveal, "VeilBehavior.isVeiled(player)", "State state = STATES.get");
		assertTrue(resistsReveal.contains("return false;"),
			"Mask should not prevent reveal effects from breaking an already-active Veil.");
		assertTrue(lang.contains("item.attuned.mask_focus.effect")
				&& lang.contains("except while fully veiled"),
			"Mask tooltip should explain the stacked-stealth counterplay limit.");
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

	private static int countOccurrences(String value, String needle) {
		int count = 0;
		int index = 0;
		while ((index = value.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
	}

	private static void assertBefore(String source, String earlier, String later) {
		int earlierIndex = source.indexOf(earlier);
		int laterIndex = source.indexOf(later);
		assertTrue(earlierIndex >= 0, "Expected source to contain: " + earlier);
		assertTrue(laterIndex >= 0, "Expected source to contain: " + later);
		assertTrue(earlierIndex < laterIndex, "Expected " + earlier + " before " + later);
	}
}
