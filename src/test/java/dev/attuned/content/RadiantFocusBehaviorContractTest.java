package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source-level guardrails for Radiant/Reliquary behavior edge cases. */
class RadiantFocusBehaviorContractTest {
	private static final Path RADIANT_BEHAVIORS =
		Path.of("src/main/java/dev/attuned/content/behavior/RadiantFocusBehaviors.java");

	@Test
	void censerTrimPreservesExistingEffectDisplayFlags() throws IOException {
		String source = read(RADIANT_BEHAVIORS);
		String trim = source.contains("private static void trim(ServerPlayer player, Holder<MobEffect> effect)")
			? methodBody(source, "private static void trim(ServerPlayer player, Holder<MobEffect> effect)")
			: methodBody(source, "private static void trim(ServerPlayer player, MobEffect effect)");

		assertTrue(trim.contains("current.isAmbient(), current.isVisible(), current.showIcon()"),
			"Censer should shorten poison/wither without forcing hidden or no-icon effects to become visible.");
	}

	@Test
	void bellwetherBellScanIsHorizWideButVerticallyShallow() throws IOException {
		String source = read(RADIANT_BEHAVIORS);
		String nearBlock = methodBody(source,
			"private static boolean nearBlock(ServerPlayer player, net.minecraft.world.level.block.Block block,");

		assertTrue(source.contains("BELL_RADIUS_XZ = 8"),
			"Bellwether should keep its planned 8-block horizontal bell range.");
		assertTrue(source.contains("BELL_RADIUS_Y = 2"),
			"Bellwether should not scan an 8-block-tall cube just to find nearby bells.");
		assertTrue(source.contains("nearBlock(player, Blocks.BELL, BELL_RADIUS_XZ, BELL_RADIUS_Y)"),
			"Bellwether should pass separate horizontal and vertical radii to the block scan.");
		assertFalse(source.contains("nearBlock(player, Blocks.BELL, BELL_RADIUS)"),
			"Bellwether should not use one radius for all three axes.");
		assertTrue(source.contains("int radiusXz, int radiusY)"),
			"The shared block scan should accept separate horizontal and vertical radii.");
		assertTrue(nearBlock.contains("origin.offset(-radiusXz, -radiusY, -radiusXz)")
				&& nearBlock.contains("origin.offset(radiusXz, radiusY, radiusXz)"),
			"The shared block scan should be horizontally wide and vertically shallow.");
		assertFalse(nearBlock.contains("origin.offset(-radius, -radius, -radius)"),
			"The shared block scan should not use a cubic radius.");
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
