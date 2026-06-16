package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-grep contract pins for the second-tier Grand Focus Reliquary: a 54-slot
 * (9x6) reliquary that shares the {@link dev.attuned.menu.SatchelMenu} /
 * {@link dev.attuned.client.screen.SatchelScreen} stack with the small satchel,
 * deriving its row count from the actual container size rather than the small
 * {@code SATCHEL_SIZE} constant.
 */
class GrandSatchelContractTest {
	private static final Path COMPONENTS = Path.of("src/main/java/dev/attuned/content/AttunedComponents.java");
	private static final Path ITEM = Path.of("src/main/java/dev/attuned/content/SatchelItem.java");
	private static final Path CONTENT = Path.of("src/main/java/dev/attuned/content/AttunedContent.java");
	private static final Path TABS = Path.of("src/main/java/dev/attuned/content/AttunedCreativeTabs.java");
	private static final Path MENU = Path.of("src/main/java/dev/attuned/menu/SatchelMenu.java");
	private static final Path MENU_TYPE = Path.of("src/main/java/dev/attuned/menu/SatchelMenuType.java");
	private static final Path CONTAINER = Path.of("src/main/java/dev/attuned/menu/SatchelContainer.java");
	private static final Path NET = Path.of("src/main/java/dev/attuned/menu/PresetNetworking.java");
	private static final Path SCREENS = Path.of("src/client/java/dev/attuned/client/screen/AltarScreens.java");
	private static final Path LANG = Path.of("src/main/resources/assets/attuned/lang/en_us.json");
	private static final Path RECIPE = Path.of("src/main/resources/data/attuned/recipe/grand_satchel_of_foci.json");
	private static final Path ITEM_DEF = Path.of("src/main/resources/assets/attuned/items/grand_satchel_of_foci.json");
	private static final Path ITEM_MODEL = Path.of("src/main/resources/assets/attuned/models/item/grand_satchel_of_foci.json");

	@Test
	void grandContentsComponentRegistersInsideTheSameGuardWithA54SlotSize() throws IOException {
		String components = read(COMPONENTS);
		assertTrue(components.contains("public static final int GRAND_SATCHEL_SIZE = 54;"),
			"Grand reliquary capacity must be a stable 54 (9x6) constant beside SATCHEL_SIZE.");
		assertTrue(components.contains("public static DataComponentType<FocusHolder> GRAND_SATCHEL_CONTENTS;"),
			"GRAND_SATCHEL_CONTENTS should be an assignable field populated in the same init().");
		assertTrue(components.contains("\"grand_satchel_contents\""),
			"The grand component id path should be grand_satchel_contents.");
		assertTrue(components.contains(".persistent(FocusHolder.codec(GRAND_SATCHEL_SIZE, 1))"),
			"The grand component should persist via the holder codec at the grand size.");
		assertTrue(components.contains(".networkSynchronized(FocusHolder.streamCodec(GRAND_SATCHEL_SIZE, 1))"),
			"The grand component should sync via the holder stream codec at the grand size.");
		assertTrue(components.contains("public static FocusHolder emptyGrandContents()"),
			"AttunedComponents should expose an emptyGrandContents() default for the grand item.");
		// Both registrations must live after the single idempotent guard flip.
		assertBefore(components, "initialized = true;",
			"new ResourceLocation(Attuned.MOD_ID, \"grand_satchel_contents\")");
	}

	@Test
	void grandSatchelItemIsRegisteredAndAcceptedInTheUtilityTabBesideTheSmallSatchel() throws IOException {
		String content = read(CONTENT);
		assertTrue(content.contains("GRAND_SATCHEL_OF_FOCI = register(\"grand_satchel_of_foci\""),
			"The grand reliquary should be a public field using the plain register helper.");
		assertTrue(content.contains("grand_satchel_of_foci"),
			"The grand reliquary registry path should be grand_satchel_of_foci.");
		String tabs = read(TABS);
		int satchel = tabs.indexOf("output.accept(AttunedContent.SATCHEL_OF_FOCI)");
		int grand = tabs.indexOf("output.accept(AttunedContent.GRAND_SATCHEL_OF_FOCI)");
		assertTrue(satchel >= 0 && grand >= 0 && Math.abs(satchel - grand) < 400,
			"The grand reliquary should be accepted in the utility tab next to the small satchel.");
	}

	@Test
	void satchelItemIsParameterizedByComponentAndSize() throws IOException {
		String item = read(ITEM);
		assertTrue(item.contains("DataComponentType<FocusHolder>"),
			"SatchelItem should take the contents component type so both tiers share the class.");
		assertTrue(item.contains("GRAND_SATCHEL") || item.contains("grand"),
			"SatchelItem (or its registration) must wire a grand variant.");
	}

	@Test
	void menuDerivesRowsFromTheActualContainerSizeNotTheSmallConstant() throws IOException {
		String menu = read(MENU);
		assertTrue(menu.contains("size / 9") || menu.contains("/ 9"),
			"The reliquary menu rows must derive from the container size (size / 9), not a fixed 3.");
		assertTrue(menu.contains("getContainerSize()"),
			"The menu should read the actual container size to size its grid and slot boundaries.");
		assertTrue(menu.contains("public int equippedStart()") || menu.contains("int equippedStart()"),
			"EQUIPPED_START must become a per-instance value derived from the actual container size.");
		assertTrue(menu.contains("public int equippedEnd()") || menu.contains("int equippedEnd()"),
			"EQUIPPED_END must become a per-instance value derived from the actual container size.");
	}

	@Test
	void containerIsParameterizedByComponentAndSize() throws IOException {
		String container = read(CONTAINER);
		assertTrue(container.contains("DataComponentType<FocusHolder>"),
			"SatchelContainer should be parameterized by the contents component type for both tiers.");
		assertTrue(container.contains("GRAND_SATCHEL_OF_FOCI") || container.contains("contentsType"),
			"SatchelContainer must support the grand reliquary item/component.");
	}

	@Test
	void bothReliquariesNestingIsRejectedInBothMenusMayPlace() throws IOException {
		String menu = read(MENU);
		assertTrue(menu.contains("!= AttunedContent.SATCHEL_OF_FOCI"),
			"Reliquary slots must still reject the small satchel item (no nested-bag duplication).");
		assertTrue(menu.contains("!= AttunedContent.GRAND_SATCHEL_OF_FOCI"),
			"Reliquary slots must also reject the grand reliquary item (no nested-bag duplication).");
	}

	@Test
	void quickApplyInventoryLookupAcceptsEitherReliquary() throws IOException {
		String net = read(NET);
		assertTrue(net.contains("GRAND_SATCHEL_OF_FOCI"),
			"The inventory reliquary lookup (quick apply) must accept the grand reliquary too.");
	}

	@Test
	void bothMenuTypesMapToTheSameScreenClass() throws IOException {
		String menuType = read(MENU_TYPE);
		assertTrue(menuType.contains("GRAND_TYPE"),
			"A second menu type should back the grand reliquary so the client builds a 54-slot grid.");
		String screens = read(SCREENS);
		assertTrue(screens.contains("MenuScreens.register(SatchelMenuType.TYPE, SatchelScreen::new)"),
			"The small reliquary must still map to SatchelScreen.");
		assertTrue(screens.contains("MenuScreens.register(SatchelMenuType.GRAND_TYPE, SatchelScreen::new)"),
			"The grand reliquary must open the same SatchelScreen class.");
	}

	@Test
	void grandReliquaryHasModelItemDefinitionRecipeAndLang() throws IOException {
		assertTrue(Files.isRegularFile(ITEM_DEF), "Grand reliquary needs an item definition JSON.");
		assertTrue(Files.isRegularFile(ITEM_MODEL), "Grand reliquary needs an item model JSON.");

		JsonObject recipe = JsonParser.parseString(read(RECIPE)).getAsJsonObject();
		assertTrue(recipe.getAsJsonObject("result").get("id").getAsString().equals("attuned:grand_satchel_of_foci"),
			"Recipe should produce the grand reliquary item.");
		assertTrue(read(RECIPE).contains("attuned:satchel_of_foci"),
			"The grand reliquary recipe should upgrade an existing Focus Reliquary.");

		JsonObject lang = JsonParser.parseString(read(LANG)).getAsJsonObject();
		assertTrue(lang.has("item.attuned.grand_satchel_of_foci"), "Grand reliquary needs a display name.");
		assertTrue(lang.has("item.attuned.grand_satchel_of_foci.lore"), "Grand reliquary needs lore.");
		assertTrue(lang.has("item.attuned.grand_satchel_of_foci.lore2"), "Grand reliquary needs lore2.");
		assertTrue(lang.has("item.attuned.grand_satchel_of_foci.effect"), "Grand reliquary needs an effect line.");
		assertTrue(lang.has("container.attuned.grand_satchel"), "Grand reliquary menu needs a window title key.");
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
