package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-grep contract pins for Focus tempering — the runtime-flavored wiring
 * that the Minecraft-free test classpath cannot exercise directly: the data
 * component, the effect multiplier, the cost surcharge, the reweaving gate, and
 * the tooltip lang key.
 */
class TemperingContractTest {
	private static final Path COMPONENTS =
		Path.of("src/main/java/dev/attuned/content/AttunedComponents.java");
	private static final Path EFFECTS =
		Path.of("src/main/java/dev/attuned/effect/AttunedEffects.java");
	private static final Path ATTUNEMENT =
		Path.of("src/main/java/dev/attuned/attunement/Attunement.java");
	private static final Path RESOLVER =
		Path.of("src/main/java/dev/attuned/content/TemperingResolver.java");
	private static final Path REWEAVING_MENU =
		Path.of("src/main/java/dev/attuned/menu/ReweavingMenu.java");
	private static final Path REWEAVING_NETWORKING =
		Path.of("src/main/java/dev/attuned/menu/ReweavingNetworking.java");
	private static final Path TOOLTIPS =
		Path.of("src/client/java/dev/attuned/client/AttunedTooltips.java");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void temperedComponentRegistersInsideTheIdempotentGuard() throws IOException {
		String components = read(COMPONENTS);
		assertTrue(components.contains("public static DataComponentType<Unit> TEMPERED;"),
			"TEMPERED should be an assignable component field populated in init().");
		assertTrue(components.contains("\"tempered\""),
			"The Tempered component id path should be tempered.");
		assertBefore(components, "initialized = true;",
			"ForgeRegistration.dataComponent(\"tempered\"");
	}

	@Test
	void effectsAmplifyTemperedModifiers() throws IOException {
		String effects = read(EFFECTS);
		assertTrue(effects.contains("* 1.25") || effects.contains("TEMPERED_MODIFIER_MULTIPLIER"),
			"AttunedEffects should boost a Tempered Focus's modifiers by 25%.");
		assertTrue(effects.contains("AttunedComponents.TEMPERED"),
			"AttunedEffects should key the boost off the Tempered component.");
	}

	@Test
	void budgetCostSourceAppliesTheTemperedSurcharge() throws IOException {
		String resolver = read(RESOLVER);
		assertTrue(resolver.contains("baseCost + TEMPER_COST_SURCHARGE")
				|| resolver.contains("baseCost + 1"),
			"temperedCost should add one attunement point.");
		assertTrue(resolver.contains("public static int temperedCost("),
			"TemperingResolver should expose temperedCost.");

		String attunement = read(ATTUNEMENT);
		assertTrue(attunement.contains("TemperingResolver.temperedCost("),
			"The budget cost source should add the Tempered surcharge via temperedCost.");
		assertTrue(attunement.contains("effectiveCost(def, stack)"),
			"Budget candidates should use the Tempered-aware effective cost.");
	}

	@Test
	void reweavingGatesTheTemperPathThroughTheResolver() throws IOException {
		String menu = read(REWEAVING_MENU);
		assertTrue(menu.contains("TemperingResolver.canTemper"),
			"ReweavingMenu should gate the same-id temper path through TemperingResolver.canTemper.");

		String networking = read(REWEAVING_NETWORKING);
		assertTrue(networking.contains("menu.canTemper()"),
			"Server-side reweaving should branch to the temper path when canTemper().");
		assertTrue(networking.contains("AttunedComponents.TEMPERED"),
			"The temper result should carry the Tempered component.");
	}

	@Test
	void temperedTooltipLineAndLangKeyExist() throws IOException {
		String tooltips = read(TOOLTIPS);
		assertTrue(tooltips.contains("tooltip.attuned.tempered"),
			"AttunedTooltips should render the Tempered line.");
		assertTrue(tooltips.contains("ChatFormatting.GOLD"),
			"The Tempered Focus should use gold name styling.");

		String lang = read(LANG);
		assertTrue(lang.contains("\"tooltip.attuned.tempered\""),
			"en_us.json should define the Tempered tooltip line.");
	}

	private static void assertBefore(String source, String earlier, String later) {
		int e = source.indexOf(earlier);
		int l = source.indexOf(later);
		assertTrue(e >= 0 && l >= 0 && e < l, "Expected " + earlier + " before " + later);
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
