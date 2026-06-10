package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for preserving satchel stacks whose datapack definition failed to load. */
class SatchelDefinitionlessStackContractTest {
	private static final Path CONTAINER = Path.of("src/main/java/dev/attuned/menu/SatchelContainer.java");

	@Test
	void satchelSlotsRenderStoredStacksEvenWhenDefinitionsAreMissing() throws IOException {
		String source = read(CONTAINER);
		String focusStackAt = methodBody(source, "private ItemStack focusStackAt(int slot)");

		assertTrue(focusStackAt.contains("return holder().get(slot);"),
			"The satchel should expose stored stacks directly so definitionless Foci are visible and removable.");
		assertFalse(focusStackAt.contains("Attunement.definitionFor(player, stack).isEmpty()"),
			"Missing focus definitions must not make a stored satchel item look like an empty slot.");
		assertTrue(source.contains("if (Attunement.definitionFor(player, stack).isEmpty())"),
			"Insertion should still reject new non-Focus stacks at the server boundary.");
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
