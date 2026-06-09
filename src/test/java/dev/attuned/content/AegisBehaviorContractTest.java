package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract coverage for Aegis' shield recharge semantics. */
class AegisBehaviorContractTest {
	private static final Path AEGIS =
		Path.of("src/main/java/dev/attuned/content/behavior/AegisBehavior.java");

	@Test
	void aegisGrantsItsFirstShieldOnActivationNotOneTickLater() throws IOException {
		String source = read(AEGIS);
		String activate = methodBody(source, "public void onActivate(ServerPlayer player, ItemStack focus)");

		assertTrue(activate.contains("grantAbsorption(player);"),
			"Aegis activation should grant the first shield immediately instead of only seeding the tick counter.");
		assertTrue(source.contains("private static void grantAbsorption(ServerPlayer player)"),
			"Aegis should centralize absorption application so activation and recharge share the same guard.");
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
