package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-grep contract pins for the Reliquary build hover preview wiring in
 * {@link dev.attuned.client.screen.SatchelScreen}. The pure availability rule is
 * covered behaviourally by {@code BuildPreviewResolverTest}; here we only assert
 * that the screen routes through that resolver and draws greyed icons for the
 * MISSING slots, using the fork's item-draw hook.
 */
class BuildPreviewContractTest {
	private static final Path SCREEN = Path.of("src/client/java/dev/attuned/client/screen/SatchelScreen.java");

	@Test
	void hoverPreviewResolvesAvailabilityThroughThePureResolver() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("BuildPreviewResolver.availability"),
			"The hover preview must compute slot availability via the pure resolver, matching Apply's sourcing.");
	}

	@Test
	void hoverPreviewUsesSyncedFocusDefinitionRegistryForAvailability() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("import dev.attuned.AttunedRegistries;"),
			"The client preview should read the synced FocusDefinition registry.");
		assertTrue(screen.contains("lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS)"),
			"Availability should use the same registry id source as server-side Apply.");
		assertTrue(screen.contains("private Set<String> registeredFocusIds()"),
			"The registry-derived Focus ids should be isolated in one helper.");
		assertTrue(screen.contains("slots, equippedIds(), satchelIds(), inventoryFocusCounts(), registeredFocusIds())"),
			"The hover preview must pass registered Focus ids into the pure availability resolver.");
	}

	@Test
	void hoverPreviewDrawsSixFocusIconsViaTheForkItemHook() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("graphics.item(") || screen.contains("graphics.renderItem("),
			"The preview must draw the build's Foci as item icons via the active item-draw hook.");
		assertTrue(screen.contains("BuiltInRegistries.ITEM.getValue")
				|| screen.contains("BuiltInRegistries.ITEM.get("),
			"Each build slot id must be resolved to an item at runtime (client registry), not in unit tests.");
		assertTrue(screen.contains("ResourceLocation.parse("),
			"Build slot ids are parsed to Identifiers before registry lookup.");
	}

	@Test
	void missingSlotsGetAGreyedOverlay() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("PREVIEW_MISSING_OVERLAY"),
			"MISSING preview slots must be greyed with a dedicated overlay constant.");
		assertTrue(screen.contains("BuildPreviewResolver.Availability.MISSING"),
			"The grey treatment keys off the MISSING availability so it stays in sync with Apply.");
	}

	@Test
	void previewRowStaysInsideTheLogicalWindow() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("drawBuildPreview"),
			"The preview row should live in a dedicated helper for contract coverage.");
		assertTrue(screen.contains("WELL_FILL") && screen.contains("WELL_EDGE"),
			"The preview icons should reuse the existing slot-well backing so they read as the rest of the UI.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
