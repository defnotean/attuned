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
