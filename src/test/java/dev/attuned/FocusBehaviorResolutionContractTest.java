package dev.attuned;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Pins the single code-first-then-data Focus behaviour resolution funnel and its registry
 * wiring. The runtime resolution touches {@code RegistryAccess} and the built-in mob-effect
 * registry, neither available without a bootstrapped Minecraft, so the ordering and the
 * fall-back path are asserted by literal source rather than executed here.
 */
class FocusBehaviorResolutionContractTest {
	private static final Path REGISTRIES =
		Path.of("src/main/java/dev/attuned/AttunedRegistries.java");
	private static final Path INIT =
		Path.of("src/main/java/dev/attuned/Attuned.java");
	private static final Path BUILDER =
		Path.of("src/main/java/dev/attuned/content/behavior/DataFocusBehaviors.java");

	@Test
	void registryDeclaresTheSyncedBehaviourPalette() throws IOException {
		String source = read(REGISTRIES);

		assertTrue(source.contains("ResourceKey<Registry<FocusBehaviorDef>> FOCUS_BEHAVIORS"),
			"AttunedRegistries should declare the focus_behavior palette registry key.");
		assertTrue(source.contains("\"focus_behavior\""),
			"The palette registry should live at data/<ns>/attuned/focus_behavior.");

		String init = read(INIT);
		assertTrue(init.contains("DynamicRegistries.registerSynced(AttunedRegistries.FOCUS_BEHAVIORS, FocusBehaviorDef.CODEC);"),
			"Attuned#onInitialize should register the palette as a synced dynamic registry.");
	}

	@Test
	void resolutionFunnelIsCodeFirstThenData() throws IOException {
		String source = read(REGISTRIES);

		// The data overload exists and is the funnel.
		assertTrue(source.contains("public static FocusBehavior getBehavior(ResourceLocation id, RegistryAccess registries)"),
			"There should be a single registry-aware resolution funnel.");
		// Code is consulted first.
		assertTrue(source.contains("FocusBehavior code = BEHAVIORS.get(id);")
				&& source.contains("if (code != null) {")
				&& source.contains("return code;"),
			"The funnel must return a registered code behaviour before consulting data.");
		// Then data is built from the palette registry.
		assertTrue(source.contains("return dataBehavior(id, registries);"),
			"The funnel must fall back to a data behaviour when no code behaviour exists.");
		assertTrue(source.contains("registries.lookupOrThrow(FOCUS_BEHAVIORS).getValue(id)")
				|| (source.contains("ResourceKey.create(FOCUS_BEHAVIORS, id)")
					&& source.contains("registries.lookupOrThrow(FOCUS_BEHAVIORS)")
					&& source.contains(".get(key)")),
			"The data path must read the definition from the focus_behavior registry by id.");
		assertTrue(source.contains("DataFocusBehaviors.build(id, def)"),
			"The data path must build a runtime FocusBehavior from the palette definition, passing "
				+ "the behaviour id so per-instance state (e.g. attribute_while modifier ids) stays distinct.");
	}

	@Test
	void builderRejectsUnknownPaletteTypesAtCompileTime() throws IOException {
		String source = read(BUILDER);

		// An exhaustive switch over the sealed palette is the unknown-type rejection: any new
		// FocusBehaviorDef variant fails to compile until it is handled here.
		assertTrue(source.contains("return switch (definition) {"),
			"DataFocusBehaviors should exhaustively switch over the sealed palette.");
		assertTrue(source.contains("case FocusBehaviorDef.ConditionalMobEffect effect ->"),
			"DataFocusBehaviors should build the conditional_mob_effect palette type.");
		assertTrue(source.contains("def.condition().test(player)"),
			"The conditional behaviour should only refresh while its condition holds.");
		assertTrue(source.contains("PassiveEffectRefresher.shouldRefresh(current, def.refreshTicks())"),
			"The conditional behaviour should reuse the shared near-expiry refresh gate.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
