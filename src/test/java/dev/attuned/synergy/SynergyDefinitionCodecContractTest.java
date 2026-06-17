package dev.attuned.synergy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Contract coverage for the {@link dev.attuned.api.synergy.SynergyDefinition}
 * data-boundary record and its codec wiring.
 *
 * <p>This is a SOURCE-GREP contract test, not a codec round-trip. Instantiating
 * {@code SynergyDefinition.CODEC} eagerly initializes {@code ModifierEntry.CODEC},
 * which calls {@code BuiltInRegistries.ATTRIBUTE.holderByNameCodec()} — that needs a
 * Bootstrapped Minecraft, which the unit-test classpath cannot provide (the same
 * reason {@code FocusDefinitionContractTest} pins {@code FocusDefinition}'s codec by
 * source rather than round-tripping it). The codec's runtime behaviour is exercised
 * in-game via the datapack-load smoke check.
 */
class SynergyDefinitionCodecContractTest {
	private static final Path SYNERGY_DEFINITION =
		Path.of("src/main/java/dev/attuned/api/synergy/SynergyDefinition.java");

	@Test
	void synergyDefinitionDefensivelyCopiesAndRejectsNullFields() throws IOException {
		String source = read(SYNERGY_DEFINITION);

		assertTrue(source.contains("import java.util.Objects;"),
			"SynergyDefinition should use explicit null checks at the API boundary.");
		assertTrue(source.contains("public SynergyDefinition {"),
			"SynergyDefinition should normalize constructor inputs before storing them.");
		assertTrue(source.contains("members = List.copyOf(Objects.requireNonNull(members, \"members\"));"),
			"SynergyDefinition should store an immutable defensive member snapshot and reject null.");
		assertTrue(source.contains("modifiers = List.copyOf(Objects.requireNonNull(modifiers, \"modifiers\"));"),
			"SynergyDefinition should store an immutable defensive modifier snapshot and reject null.");
		assertTrue(source.contains("behavior = Objects.requireNonNull(behavior, \"behavior\");"),
			"SynergyDefinition should reject a null behavior Optional.");
	}

	@Test
	void synergyDefinitionCodecWiresMembersModifiersAndBehavior() throws IOException {
		String source = read(SYNERGY_DEFINITION);

		assertTrue(source.contains("import dev.attuned.api.focus.ModifierEntry;"),
			"SynergyDefinition should reuse the real ModifierEntry codec for its modifiers.");
		assertTrue(source.contains("RecordCodecBuilder.create"),
			"SynergyDefinition should build its codec with RecordCodecBuilder, like FocusDefinition.");
		assertTrue(source.contains("ResourceLocation.CODEC.listOf().fieldOf(\"members\")"),
			"members must be a required list of Identifiers (the Focus item ids that must all be active).");
		assertTrue(source.contains("ModifierEntry.CODEC.listOf().optionalFieldOf(\"modifiers\", List.of())"),
			"modifiers must default to the empty list when omitted, mirroring FocusDefinition.");
		assertTrue(source.contains("ResourceLocation.CODEC.optionalFieldOf(\"behavior\")"),
			"behavior must be an optional ResourceLocation into the FocusBehavior registry.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
