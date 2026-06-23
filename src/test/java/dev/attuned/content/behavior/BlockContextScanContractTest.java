package dev.attuned.content.behavior;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source-level performance guardrails for block-context Focus scans. */
class BlockContextScanContractTest {
	private static final Path HARVEST =
		Path.of("src/main/java/dev/attuned/content/behavior/HarvestBehavior.java");

	@Test
	void harvestCropScanIsTimedCappedAndVerticallyShallow() throws IOException {
		String source = read(HARVEST);
		String growNearbyCrops = methodBody(source, "private void growNearbyCrops(ServerPlayer player)");

		assertTrue(source.contains("GROW_INTERVAL = 20"),
			"Harvest should still scan on a one-second cadence, not every tick.");
		assertTrue(source.contains("MAX_PER_PASS = 3"),
			"Harvest should keep a small per-pass crop growth cap.");
		assertTrue(source.contains("RADIUS_XZ = 5"),
			"Harvest should keep its intended 5-block horizontal crop range.");
		assertTrue(source.contains("RADIUS_Y = 1"),
			"Harvest should scan a shallow crop-height band instead of a full cube.");
		assertTrue(growNearbyCrops.contains("center.offset(-RADIUS_XZ, -RADIUS_Y, -RADIUS_XZ)")
				&& growNearbyCrops.contains("center.offset(RADIUS_XZ, RADIUS_Y, RADIUS_XZ)"),
			"Harvest crop scanning should use separate horizontal and vertical radii.");
		assertFalse(growNearbyCrops.contains("center.offset(-RADIUS, -RADIUS, -RADIUS)"),
			"Harvest crop scanning should not use one cubic radius for all axes.");
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
