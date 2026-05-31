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
}
