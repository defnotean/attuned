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
		assertTrue(body.contains("focusDefinitionPath(focusId)"),
			"validate must report each Focus problem against its datapack file path so authors can locate the failing file.");
		assertTrue(body.contains("registryId(def.item(), BuiltInRegistries.ITEM::getKey)"),
			"validate must resolve the Focus item id without calling value() before checking whether the holder is bound.");
	}

	@Test
	void validateDiagnosticsIncludeFilePathFieldPathAndExactBadId() throws IOException {
		String source = read(COMMANDS);
		String body = validateBody();

		assertTrue(source.contains("record ValidationIssue("),
			"validate diagnostics should use a structured issue record instead of bare strings.");
		assertTrue(source.contains("String filePath, String fieldPath, String badId, String message"),
			"each diagnostic must carry the datapack file path, JSON field path, exact bad id, and explanation.");
		assertTrue(source.contains("filePath + \": \" + fieldPath + \" [\" + badId + \"] \" + message"),
			"formatted diagnostics must print file path, field path, and exact bad id in a stable order.");
		assertTrue(body.contains("List<ValidationIssue> problems"),
			"hard validation failures must keep structured issue details until they are printed.");
		assertTrue(body.contains("ValidationIssue.error(focusPath, \"item\", itemId.toString()"),
			"a bad Focus item must identify the source file, the item field, and the exact item id.");
		assertTrue(body.contains("ValidationIssue.error(focusPath, \"behavior\", behaviorId.toString()"),
			"a bad behavior id must identify the source file, the behavior field, and the exact behavior id.");
		assertTrue(body.contains("\"modifiers[\" + index + \"].attribute\""),
			"a bad modifier attribute must identify the exact modifiers[n].attribute field path.");
		assertTrue(body.contains("ValidationIssue.error(focusPath, fieldPath, attributeId.toString()"),
			"a bad modifier attribute must print the exact unresolved attribute id.");
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
	void validateChecksPaletteBehaviorNestedRegistryIdsFileByFile() throws IOException {
		String source = read(COMMANDS);
		String body = validateBody();

		assertTrue(source.contains("import dev.attuned.api.focus.FocusBehaviorDef;"),
			"validate must inspect the loaded FocusBehaviorDef variants, not just count palette entries.");
		assertTrue(source.contains("private static String focusBehaviorDefinitionPath(Identifier behaviorId)"),
			"validate diagnostics should format data/<ns>/attuned/focus_behavior/<id>.json paths.");
		assertTrue(body.contains("focusBehaviorDefinitionPath(behaviorId)"),
			"each palette behavior problem should be reported against its focus_behavior datapack file.");
		assertTrue(source.contains("private static void validateBehaviorDefinition("),
			"validate should share one helper for palette behavior nested-id checks.");
		assertTrue(source.contains("case FocusBehaviorDef.ConditionalMobEffect effect ->"),
			"conditional_mob_effect entries should have their nested mob-effect id checked.");
		assertTrue(source.contains("case FocusBehaviorDef.OnHitEffect effect ->"),
			"on_hit_effect entries should have their nested mob-effect id checked.");
		assertTrue(source.contains("case FocusBehaviorDef.PeriodicEffect effect ->"),
			"periodic_effect entries should have their nested mob-effect id checked.");
		assertTrue(source.contains("case FocusBehaviorDef.BlockContextEffect effect ->"),
			"block_context_effect entries should have their nested mob-effect id checked.");
		assertTrue(source.contains("case FocusBehaviorDef.UseItemWindow window ->"),
			"use_item_window entries should have their optional nested effect and modifier ids checked.");
		assertTrue(source.contains("case FocusBehaviorDef.PartyAssist assist ->"),
			"party_assist entries should have their nested assist effect id checked.");
		assertTrue(source.contains("case FocusBehaviorDef.MarkedTarget marked ->"),
			"marked_target entries should have their nested effect_on_consume id checked.");
		assertTrue(source.contains("case FocusBehaviorDef.NavigationHint navigation ->"),
			"navigation_hint entries should be recognized by the validate switch even though they carry no nested registry ids.");
		assertTrue(source.contains("ValidationIssue.error(behaviorPath, \"effect\", effectId.toString()"),
			"bad palette mob effects should identify the exact effect field and id.");
		assertTrue(source.contains("case FocusBehaviorDef.AttributeWhile attributeWhile ->"),
			"attribute_while entries should have their nested modifier attribute id checked.");
		assertTrue(source.contains("window.effect().ifPresent(effect -> validatePaletteMobEffect(problems, behaviorPath, effect))"),
			"use_item_window should validate an optional effect field when present.");
		assertTrue(source.contains("window.modifier().ifPresent(modifier -> validatePaletteModifier(problems, behaviorPath, modifier))"),
			"use_item_window should validate an optional modifier field when present.");
		assertTrue(source.contains("validatePaletteMobEffect(problems, behaviorPath, assist.effect().effect())"),
			"party_assist should validate its nested effect object.");
		assertTrue(source.contains("validatePaletteMobEffect(problems, behaviorPath, marked.effectOnConsume().effect())"),
			"marked_target should validate its effect_on_consume effect field.");
		assertTrue(source.contains("ValidationIssue.error(behaviorPath, \"modifier.attribute\", attributeId.toString()"),
			"bad palette modifier attributes should identify the exact modifier.attribute field and id.");
	}

	@Test
	void validateWalksConfluenceRegistryFileByFile() throws IOException {
		String source = read(COMMANDS);
		String body = validateBody();

		assertTrue(body.contains("AttunedRegistries.SYNERGY_DEFINITIONS"),
			"validate must walk the synergy registry so Confluence files are checked too.");
		assertTrue(source.contains("private static String synergyDefinitionPath(Identifier synergyId)"),
			"validate diagnostics should format synergy datapack paths beside focus paths.");
		assertTrue(body.contains("synergyDefinitionPath(synergyId)"),
			"each Confluence problem should be reported against data/<ns>/attuned/synergy/<id>.json.");
		assertTrue(body.contains("focusDefinitionIds.contains(memberId)"),
			"Confluence members should be validated against loaded FocusDefinition ids.");
		assertTrue(body.contains("\"members[\" + index + \"]\""),
			"bad Confluence members should identify the exact members[n] field.");
		assertTrue(body.contains("ValidationIssue.error(synergyPath, \"behavior\", behaviorId.toString()"),
			"bad Confluence behaviors should identify the synergy file, behavior field, and behavior id.");
		assertTrue(body.contains("ValidationIssue.error(synergyPath, fieldPath, attributeId.toString()"),
			"bad Confluence modifier attributes should identify the exact modifiers[n].attribute field.");
	}

	@Test
	void missingLangKeysAreWarningsNotFailures() throws IOException {
		String body = validateBody();

		assertTrue(body.contains("List<ValidationIssue> warnings"),
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
