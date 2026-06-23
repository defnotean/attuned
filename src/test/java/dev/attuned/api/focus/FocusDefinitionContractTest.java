package dev.attuned.api.focus;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract coverage for Focus API data-boundary records. */
class FocusDefinitionContractTest {
	private static final Path FOCUS_DEFINITION =
		Path.of("src/main/java/dev/attuned/api/focus/FocusDefinition.java");
	private static final Path MODIFIER_ENTRY =
		Path.of("src/main/java/dev/attuned/api/focus/ModifierEntry.java");
	private static final Path REFERENCE =
		Path.of("docs/reference.md");

	@Test
	void focusDefinitionDefensivelyCopiesAndRejectsNullFields() throws IOException {
		String source = read(FOCUS_DEFINITION);

		assertTrue(source.contains("import java.util.Objects;"),
			"FocusDefinition should use explicit null checks at the API boundary.");
		assertTrue(source.contains("public FocusDefinition {"),
			"FocusDefinition should normalize constructor inputs before storing them.");
		assertTrue(source.contains("item = Objects.requireNonNull(item, \"item\");"),
			"FocusDefinition should reject a null item holder.");
		assertTrue(source.contains("affinity = Objects.requireNonNull(affinity, \"affinity\");"),
			"FocusDefinition should reject a null affinity Optional.");
		assertTrue(source.contains("faction = Objects.requireNonNull(faction, \"faction\");"),
			"FocusDefinition should reject a null faction Optional.");
		assertTrue(source.contains("modifiers = List.copyOf(Objects.requireNonNull(modifiers, \"modifiers\"));"),
			"FocusDefinition should store an immutable defensive modifier snapshot.");
		assertTrue(source.contains("behavior = Objects.requireNonNull(behavior, \"behavior\");"),
			"FocusDefinition should reject a null behavior Optional.");
	}

	@Test
	void focusDefinitionProgrammaticCostMatchesCodecRange() throws IOException {
		String source = read(FOCUS_DEFINITION);

		assertTrue(source.contains("private static final int MIN_COST = 0;"),
			"FocusDefinition should name the minimum valid cost once.");
		assertTrue(source.contains("private static final int MAX_COST = 64;"),
			"FocusDefinition should name the maximum valid cost once.");
		assertTrue(source.contains("Codec.intRange(MIN_COST, MAX_COST)"),
			"FocusDefinition codec should use the same named range as the constructor.");
		assertTrue(source.contains("if (cost < MIN_COST || cost > MAX_COST)"),
			"FocusDefinition constructor should enforce the same named cost range for programmatic definitions.");
		assertTrue(source.contains("\"Focus cost must be between \" + MIN_COST + \" and \" + MAX_COST"),
			"Programmatic cost range failures should explain the valid FocusDefinition range.");
	}

	@Test
	void modifierEntryRejectsInvalidProgrammaticInputs() throws IOException {
		String source = read(MODIFIER_ENTRY);

		assertTrue(source.contains("import java.util.Objects;"),
			"ModifierEntry should use explicit null checks at the API boundary.");
		assertTrue(source.contains("public ModifierEntry {"),
			"ModifierEntry should validate constructor inputs before storing them.");
		assertTrue(source.contains("attribute = Objects.requireNonNull(attribute, \"attribute\");"),
			"ModifierEntry should reject a null attribute holder.");
		assertTrue(source.contains("operation = Objects.requireNonNull(operation, \"operation\");"),
			"ModifierEntry should reject a null attribute operation.");
		assertTrue(source.contains("private static final double MIN_AMOUNT = -1024.0D;"),
			"ModifierEntry should name the minimum valid modifier amount once.");
		assertTrue(source.contains("private static final double MAX_AMOUNT = 1024.0D;"),
			"ModifierEntry should name the maximum valid modifier amount once.");
		assertTrue(source.contains("if (!Double.isFinite(amount))"),
			"ModifierEntry should reject NaN and infinite modifier amounts.");
		assertTrue(source.contains("if (amount < MIN_AMOUNT || amount > MAX_AMOUNT)"),
			"ModifierEntry should reject runaway but finite modifier amounts.");
		assertTrue(source.contains("\"Modifier amount must be between \" + MIN_AMOUNT + \" and \" + MAX_AMOUNT"),
			"Programmatic amount range failures should explain the valid ModifierEntry range.");
	}

	@Test
	void modifierEntryCodecRejectsNonFiniteAndOutOfRangeAmounts() throws IOException {
		String source = read(MODIFIER_ENTRY);

		assertTrue(source.contains("import com.mojang.serialization.DataResult;"),
			"ModifierEntry should use DataResult so codec failures stay structured.");
		assertTrue(source.contains("private static final Codec<Double> BOUNDED_AMOUNT_CODEC"),
			"ModifierEntry should name the bounded amount codec.");
		assertTrue(source.contains("Codec.DOUBLE.validate(ModifierEntry::validateAmount)")
				|| source.contains("Codec.DOUBLE.flatXmap(ModifierEntry::validateAmount, ModifierEntry::validateAmount)"),
			"ModifierEntry should validate datapack amount values before construction.");
		assertTrue(source.contains("BOUNDED_AMOUNT_CODEC.fieldOf(\"amount\")"),
			"ModifierEntry should use the bounded amount codec for FocusDefinition JSON.");
		assertTrue(source.contains("private static DataResult<Double> validateAmount(double amount)"),
			"ModifierEntry should centralize codec amount validation.");
		assertTrue(source.contains("DataResult.error(() -> \"Modifier amount must be finite\")")
				|| source.contains("DataResult.error(\"Modifier amount must be finite\")"),
			"ModifierEntry codec failures should explain invalid amount values.");
		assertTrue(source.contains("DataResult.error(() -> \"Modifier amount must be between \" + MIN_AMOUNT + \" and \" + MAX_AMOUNT)")
				|| source.contains("DataResult.error(\"Modifier amount must be between \" + MIN_AMOUNT + \" and \" + MAX_AMOUNT)"),
			"ModifierEntry codec failures should explain out-of-range amount values.");
	}

	@Test
	void modifierEntryReferenceDocumentsAmountBounds() throws IOException {
		String reference = read(REFERENCE);

		assertTrue(reference.contains("`amount` (a finite number from -1024.0 to 1024.0)"),
			"Reference docs should tell datapack authors the safe modifier amount range.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
