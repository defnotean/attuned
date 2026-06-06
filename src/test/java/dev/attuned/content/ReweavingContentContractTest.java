package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReweavingContentContractTest {
	private static final Path CONTENT_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedContent.java");
	private static final Path ATTUNED_INIT_SOURCE =
		Path.of("src/main/java/dev/attuned/Attuned.java");
	private static final Path MENU_TYPE_SOURCE =
		Path.of("src/main/java/dev/attuned/menu/ReweavingMenuType.java");
	private static final Path ALTAR_MENU_SOURCE =
		Path.of("src/main/java/dev/attuned/menu/AltarMenu.java");
	private static final Path REWEAVING_MENU_SOURCE =
		Path.of("src/main/java/dev/attuned/menu/ReweavingMenu.java");
	private static final Path ALTAR_NETWORKING_SOURCE =
		Path.of("src/main/java/dev/attuned/menu/AltarNetworking.java");
	private static final Path NETWORKING_SOURCE =
		Path.of("src/main/java/dev/attuned/menu/ReweavingNetworking.java");
	private static final Path SCREEN_REGISTRATION_SOURCE =
		Path.of("src/client/java/dev/attuned/client/screen/AltarScreens.java");
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void altarOfReweavingIsRegisteredAsSeparateBlockMenuAndNetworkPath() throws IOException {
		assertTrue(Files.readString(CONTENT_SOURCE, StandardCharsets.UTF_8).contains("ALTAR_OF_REWEAVING"));
		assertTrue(Files.readString(CONTENT_SOURCE, StandardCharsets.UTF_8).contains("\"altar_of_reweaving\""));
		assertTrue(Files.readString(ATTUNED_INIT_SOURCE, StandardCharsets.UTF_8).contains("ReweavingMenuType.init()"));
		assertTrue(Files.readString(ATTUNED_INIT_SOURCE, StandardCharsets.UTF_8).contains("ReweavingNetworking.init()"));
		assertTrue(Files.readString(MENU_TYPE_SOURCE, StandardCharsets.UTF_8)
			.contains("new MenuType<>(ReweavingMenu::new"));
		assertTrue(Files.readString(NETWORKING_SOURCE, StandardCharsets.UTF_8).contains("ReweavePayload.TYPE"));
		assertTrue(Files.readString(SCREEN_REGISTRATION_SOURCE, StandardCharsets.UTF_8)
			.contains("ReweavingMenuType.TYPE"));
	}

	@Test
	void altarOfReweavingHasDataAssetsAndLanguage() throws IOException {
		assertTrue(Files.isRegularFile(Path.of("src/main/resources/data/attuned/recipe/altar_of_reweaving.json")));
		assertTrue(Files.isRegularFile(
			Path.of("src/main/resources/data/attuned/loot_table/blocks/altar_of_reweaving.json")));
		assertTrue(Files.isRegularFile(
			Path.of("src/main/resources/assets/attuned/blockstates/altar_of_reweaving.json")));
		assertTrue(Files.isRegularFile(
			Path.of("src/main/resources/assets/attuned/models/block/altar_of_reweaving.json")));
		assertTrue(Files.isRegularFile(
			Path.of("src/main/resources/assets/attuned/models/item/altar_of_reweaving.json")));
		assertTrue(Files.isRegularFile(
			Path.of("src/main/resources/assets/attuned/textures/gui/altar_of_reweaving.png")));
		String lang = Files.readString(LANG_FILE, StandardCharsets.UTF_8);
		assertTrue(lang.contains("\"item.attuned.altar_of_reweaving\""));
		assertTrue(lang.contains("\"container.attuned.reweaving_altar\""));
		assertTrue(lang.contains("\"screen.attuned.reweaving_altar.reweave\""));
	}

	@Test
	void customMenusIgnoreInvalidQuickMoveSlots() throws IOException {
		assertQuickMoveSlotGuard(Files.readString(ALTAR_MENU_SOURCE, StandardCharsets.UTF_8), "AltarMenu");
		assertQuickMoveSlotGuard(Files.readString(REWEAVING_MENU_SOURCE, StandardCharsets.UTF_8), "ReweavingMenu");
	}

	@Test
	void customMenuPayloadsRejectStaleDimensionAccess() throws IOException {
		assertStaleDimensionGuard(
			Files.readString(ALTAR_NETWORKING_SOURCE, StandardCharsets.UTF_8), "AltarNetworking");
		assertStaleDimensionGuard(
			Files.readString(NETWORKING_SOURCE, StandardCharsets.UTF_8), "ReweavingNetworking");
	}

	@Test
	void reweavingFocusChecksUseSyncedDefinitionsInsteadOfStaticContentList() throws IOException {
		String menu = Files.readString(REWEAVING_MENU_SOURCE, StandardCharsets.UTF_8);
		String networking = Files.readString(NETWORKING_SOURCE, StandardCharsets.UTF_8);

		assertTrue(menu.contains("Attunement.definitionFor("),
			"Reweaving menu slots should accept any item backed by a synced FocusDefinition.");
		assertTrue(networking.contains("FocusLookup.forItem("),
			"Server-side reweaving validation should use the FocusDefinition registry.");
		assertTrue(!menu.contains("AttunedContent.isFocus("),
			"Reweaving menu slot checks should not be capped to the static shipped-Foci list.");
		assertTrue(!networking.contains("AttunedContent.isFocus("),
			"Reweaving sacrifices should not reject datapack-defined Focus items.");
	}

	private static void assertQuickMoveSlotGuard(String source, String menuName) {
		assertTrue(source.contains("if (slotIndex < 0 || slotIndex >= this.slots.size())"),
			menuName + " should reject invalid quick-move slot indexes before reading the slot list.");
		assertTrue(source.contains("return ItemStack.EMPTY;"),
			menuName + " should ignore invalid quick-move requests without moving items.");
	}

	private static void assertStaleDimensionGuard(String source, String handlerName) {
		assertTrue(source.contains("if (player.level() != serverLevel)"),
			handlerName + " should reject stale menu access from a different server level.");
	}
}
