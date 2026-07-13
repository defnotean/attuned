package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Source-level guardrails for Luck stacking and loot-pressure caps. */
class LuckStackingContractTest {
	private static final Path ATTUNED_LUCK =
		Path.of("src/main/java/dev/attuned/effect/AttunedLuck.java");
	private static final Path EFFECTS =
		Path.of("src/main/java/dev/attuned/effect/AttunedEffects.java");
	private static final Path RADIANT_BEHAVIORS =
		Path.of("src/main/java/dev/attuned/content/behavior/RadiantFocusBehaviors.java");
	private static final Path DATA_FOCUS_BEHAVIORS =
		Path.of("src/main/java/dev/attuned/content/behavior/DataFocusBehaviors.java");
	private static final Path SEAFARERS_FISHING =
		Path.of("src/main/java/dev/attuned/content/behavior/SeafarersFishing.java");

	@Test
	void attunedLuckModifiersShareOnePositiveAdditiveCap() throws IOException {
		String helper = read(ATTUNED_LUCK);
		String effects = read(EFFECTS);
		String radiant = read(RADIANT_BEHAVIORS);

		assertTrue(helper.contains("MAX_ATTUNED_LUCK_BONUS = 3.0D"),
			"Attuned-contributed Luck should be capped around vanilla-friendly Luck III.");
		assertTrue(helper.contains("luck.getModifiers()"),
			"The Luck cap should account for already-installed Attuned Luck modifiers.");
		assertTrue(helper.contains("modifier.id().getNamespace().equals(Attuned.MOD_ID)")
				|| helper.contains("modifier.name().startsWith(\"attuned:\")"),
			"The cap should count only Attuned-owned Luck modifiers, not vanilla effects or other mods.");
		assertTrue(helper.contains("modifier.operation() == AttributeModifier.Operation.ADD_VALUE")
				&& helper.contains("modifier.amount() > 0.0D"),
			"The cap should only constrain positive additive Luck sources.");
		assertTrue(helper.contains("Math.min(requested, Math.max(0.0D, MAX_ATTUNED_LUCK_BONUS - currentPositiveAttunedLuck(luck)))"),
			"New Attuned Luck modifiers should be trimmed to remaining cap headroom.");

		assertTrue(effects.contains("AttunedLuck.isPositiveAddLuck(entry)")
				&& effects.contains("AttunedLuck.cappedPositiveAddLuck(ai, amount)"),
			"Declarative Focus Luck modifiers should pass through the shared Attuned Luck cap.");
		String applyFocus = methodBody(effects, "private static void applyFocus(ServerPlayer player, int slot, AppliedFocus focus)");
		int cappedAmountGuard = applyFocus.indexOf("if (amount <= 0.0D)");
		assertTrue(cappedAmountGuard >= 0
				&& applyFocus.indexOf("continue;", cappedAmountGuard) > cappedAmountGuard,
			"Fully capped declarative Luck modifiers should not install zero-value leftovers.");

		assertTrue(radiant.contains("AttunedLuck.cappedPositiveAddLuck(luck, LUCK_BONUS)")
				&& radiant.contains("if (amount <= 0.0D)")
				&& radiant.contains("new AttributeModifier(")
				&& radiant.contains("AttributeModifier.Operation.ADD_VALUE"),
			"Namesake's conditional Luck should share the same cap as static Focus Luck.");

		String dataBehaviors = read(DATA_FOCUS_BEHAVIORS);
		int attributeWhileIndex = dataBehaviors.indexOf("static final class AttributeWhileBehavior");
		assertTrue(attributeWhileIndex >= 0, "Missing AttributeWhileBehavior implementation.");
		String attributeWhile = dataBehaviors.substring(attributeWhileIndex);
		assertTrue(attributeWhile.contains("AttunedLuck.isPositiveAddLuck(modifier)")
				&& attributeWhile.contains("AttunedLuck.cappedPositiveAddLuck(ai, amount)"),
			"Palette attribute_while Luck modifiers should pass through the shared Attuned Luck cap.");
		assertTrue(attributeWhile.contains("if (amount <= 0.0D)"),
			"Fully capped attribute_while Luck modifiers should not install zero-value leftovers.");
	}

	@Test
	void seafarersFishingLuckBoostCapsBelowTheFullFocusStack() throws IOException {
		String source = read(SEAFARERS_FISHING);
		int total = constant(source, "LINECAST_LUCK_OF_THE_SEA_BONUS")
			+ constant(source, "NETMENDER_LUCK_OF_THE_SEA_BONUS")
			+ constant(source, "HARBORLIGHT_LUCK_OF_THE_SEA_BONUS")
			+ constant(source, "DRIFTGLASS_LUCK_OF_THE_SEA_BONUS");
		int max = constant(source, "MAX_LUCK_OF_THE_SEA_BONUS");

		assertTrue(max < total,
			"Seafarers fishing luck should cap below the full four-Focus stack.");
		assertTrue(max <= 3,
			"Seafarers fishing luck should stay near vanilla Luck of the Sea III.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}

	private static int constant(String source, String name) {
		Pattern pattern = Pattern.compile("private static final int " + name + " = (\\d+);");
		Matcher matcher = pattern.matcher(source);
		assertTrue(matcher.find(), "Missing integer constant: " + name);
		return Integer.parseInt(matcher.group(1));
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
