package dev.attuned.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-grep contract for {@code /attuned validate}'s author-pack validation.
 *
 * <p>The command cannot be exercised directly here: it needs a live
 * {@code CommandSourceStack} and a bootstrapped registry, which the test
 * classpath cannot provide. Instead this pins the load-bearing literals of the
 * {@code validateContent} method body so the per-file reporting, the item /
 * behavior / attribute resolution checks, and the missing-lang-key warnings
 * cannot silently regress.
 */
class ValidateCommandContractTest {
	private static final Path COMMANDS =
		Path.of("src/main/java/dev/attuned/command/AttunedCommands.java");

	@Test
	void validateReportsEachFocusProblemAgainstItsItemKey() throws IOException {
		String body = validateBody();

		assertTrue(body.contains("registry.listElements().forEach("),
			"validate must walk every FocusDefinition so author packs are checked file by file.");
		assertTrue(body.contains("BuiltInRegistries.ITEM.getKey(def.item().value())"),
			"validate must report each Focus problem against its item key so authors can locate the failing file.");
	}

	@Test
	void validateResolvesBehaviorThroughTheSingleLookupPoint() throws IOException {
		String body = validateBody();

		assertTrue(body.contains("AttunedRegistries.getBehavior(behaviorId, registries) == null"),
			"validate must resolve every FocusDefinition behavior id through AttunedRegistries.getBehavior.");
	}

	@Test
	void validateFlagsAnUnresolvedFocusItem() throws IOException {
		String body = validateBody();

		assertTrue(body.contains("!def.item().isBound()"),
			"validate must flag a Focus whose item failed to resolve to a real item.");
	}

	@Test
	void validateValidatesAttributeIdsOnFocusModifiers() throws IOException {
		String body = validateBody();

		assertTrue(body.contains("def.modifiers()"),
			"validate must inspect a Focus's attribute modifiers so a bad attribute id is caught.");
		assertTrue(body.contains(".attribute().isBound()") || body.contains("attribute().isBound()"),
			"validate must flag a modifier whose attribute id failed to resolve.");
	}

	@Test
	void validateValidatesThePaletteBehaviorRegistryFileByFile() throws IOException {
		String body = validateBody();

		assertTrue(body.contains("FOCUS_BEHAVIORS"),
			"validate must walk the focus_behavior palette registry so author behavior files are checked too.");
	}

	@Test
	void missingLangKeysAreWarningsNotFailures() throws IOException {
		String body = validateBody();

		assertTrue(body.contains("List<String> warnings"),
			"validate must collect missing lang keys as warnings, separate from hard failures.");
		assertTrue(body.contains("\"item.\" +"),
			"validate must probe the item.<namespace>.<path> display-name lang key for every Focus.");
		assertTrue(body.contains("Language.getInstance().has("),
			"validate must use the server-side Language probe to detect a missing lang key.");
	}

	@Test
	void aPassWithOnlyWarningsStillSurfacesTheWarningCount() throws IOException {
		String body = validateBody();

		assertTrue(body.contains("\"Attuned validation passed: \""),
			"validate must keep its success message so passing packs read clearly.");
		assertTrue(body.contains("warnings.size()"),
			"A pack that passes with only lang warnings must still surface the warning count.");
		assertTrue(body.contains("warnings.isEmpty()"),
			"validate must branch on whether any warnings were collected.");
	}

	@Test
	void validateKeepsCappingThePrintedProblemList() throws IOException {
		String body = validateBody();

		assertTrue(body.contains("Math.min(8, problems.size())"),
			"validate must keep capping the printed problem list.");
	}

	private static String validateBody() throws IOException {
		String source = read(COMMANDS);
		return methodBody(source, "private static int validateContent(CommandSourceStack source)");
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
