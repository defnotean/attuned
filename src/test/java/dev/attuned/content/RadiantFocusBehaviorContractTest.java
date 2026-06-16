package dev.attuned.content;

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
