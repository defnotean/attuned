package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Source-level guardrails for the custom Attunement Journal screen. These keep
 * the guide item on Attuned's own UI path without booting a client.
 */
class AttunementJournalUiContractTest {
	private static final Path JOURNAL_ITEM_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunementJournalItem.java");
	private static final Path JOURNAL_SCREEN_SOURCE =
		Path.of("src/client/java/dev/attuned/client/screen/AttunementJournalScreen.java");
	private static final Path CLIENT_INIT_SOURCE =
		Path.of("src/client/java/dev/attuned/client/AttunedClient.java");
	private static final Path COMMON_INIT_SOURCE =
		Path.of("src/main/java/dev/attuned/Attuned.java");
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void journalItemOpensCustomScreenPayloadInsteadOfVanillaBookScreen() throws IOException {
		String itemSource = read(JOURNAL_ITEM_SOURCE);

		assertTrue(itemSource.contains("ServerPlayNetworking.send(serverPlayer, new OpenJournalPayload())"),
			"Journal right-click should ask the client to open Attuned's custom screen");
		assertFalse(itemSource.contains("super.use("),
			"Journal right-click should not fall through to the vanilla written-book screen");
		assertTrue(read(COMMON_INIT_SOURCE).contains("JournalNetworking.init()"),
			"Common init should register the journal open-screen payload");
		assertTrue(read(CLIENT_INIT_SOURCE).contains("AttunementJournalScreen.initNetworking()"),
			"Client init should register the journal payload receiver");
	}

	@Test
	void journalScreenKeepsCustomCodexLayoutContract() throws IOException {
		String screenSource = read(JOURNAL_SCREEN_SOURCE);
		String itemSource = read(JOURNAL_ITEM_SOURCE);
		String lang = read(LANG_FILE);

		assertTrue(screenSource.contains("extends Screen"),
			"Journal UI should be its own screen");
		assertTrue(screenSource.contains("GuiGraphicsExtractor"),
			"Journal UI should use the current client render extraction API");
		assertTrue(screenSource.contains("extractBackground(GuiGraphicsExtractor"),
			"Journal UI should draw a custom background/content layer");
		assertTrue(screenSource.contains("textures/gui/attunement_journal.png"),
			"Journal UI should use its generated custom book texture");
		assertTrue(screenSource.contains("CHAPTERS = List.of"),
			"Journal UI should keep chapter navigation separate from vanilla book pages");
		assertTrue(screenSource.contains("private static final class JournalButton extends Button"),
			"Journal UI should render chapter/page controls with codex-style custom buttons");
		assertTrue(screenSource.contains("extractContents(GuiGraphicsExtractor"),
			"Journal buttons should draw their own face through the render extraction API");
		assertTrue(screenSource.contains("Component.translatable(\"screen.attuned.journal.previous\")"),
			"Previous page button should use translatable screen copy");
		assertTrue(screenSource.contains("Component.translatable(\"screen.attuned.journal.next\")"),
			"Next page button should use translatable screen copy");
		assertFalse(screenSource.contains("BookViewScreen"),
			"Journal UI should not reuse the vanilla book screen");
		assertTrue(lang.contains("\"journal.attuned.screen.title\""),
			"Journal UI should have its own screen title copy");
		assertTrue(lang.contains("\"journal.attuned.screen.subtitle\""),
			"Journal UI should have its own screen subtitle copy");
		assertTrue(lang.contains("\"journal.attuned.page20\""),
			"Journal UI should include the reweaving lore pages");
		assertTrue(lang.contains("\"journal.attuned.page28\""),
			"Journal UI should include the HUD lore page");
		assertTrue(lang.contains("\"journal.attuned.page32\""),
			"Journal UI should include the Offshore Harpoon page");
		assertTrue(lang.contains("\"journal.attuned.page33\""),
			"Journal UI should include the Revenant faction page");
		String revenantPage = translationValue(lang, "journal.attuned.page33").replace("\\n", "\n");
		assertTrue(revenantPage.length() <= 190,
			"Revenant journal copy should fit the compact custom journal page");
		assertTrue(revenantPage.contains("Revenant"),
			"Revenant journal copy should name the faction");
		assertTrue(revenantPage.contains("debts"),
			"Revenant journal copy should carry the faction's debt theme");
		assertTrue(revenantPage.contains("death"),
			"Revenant journal copy should explain the death-facing identity");
		assertTrue(screenSource.contains("journal.attuned.page33"),
			"Journal UI should route to the Revenant faction page");
		assertTrue(itemSource.contains("\"journal.attuned.page33\""),
			"Written-book fallback should include the Revenant faction page");
		String offshorePage = translationValue(lang, "journal.attuned.page32").replace("\\n", "\n");
		assertTrue(offshorePage.length() <= 190,
			"Offshore Harpoon journal copy should fit the compact custom journal page");
		assertTrue(paragraphSplits(offshorePage) <= 1,
			"Offshore Harpoon journal copy should keep paragraph spacing compact");
		assertTrue(offshorePage.contains("Offshore"),
			"Offshore Harpoon journal copy should name the Offshore faction");
		assertTrue(offshorePage.contains("Harpoon Focus"),
			"Offshore Harpoon journal copy should name the Harpoon Focus");
		assertTrue(offshorePage.contains("temporary"),
			"Offshore Harpoon journal copy should explain the temporary summon");
		assertTrue(offshorePage.contains("crafted"),
			"Offshore Harpoon journal copy should say the trident cannot be crafted");
		assertTrue(screenSource.contains("journal.attuned.page32"),
			"Journal UI should route to the Offshore Harpoon page");
		assertTrue(screenSource.contains("new Chapter(\"Offshore\""),
			"Journal UI should expose Offshore as its own chapter");
		assertTrue(itemSource.contains("\"journal.attuned.page32\""),
			"Written-book fallback should include the Offshore Harpoon page");
		assertTrue(lang.contains("\"journal.attuned.page29\""),
			"Journal UI should include the Radiant Covenant page");
		assertTrue(lang.contains("\"journal.attuned.page30\""),
			"Journal UI should include the Maelstrom Apex page");
		assertTrue(lang.contains("\"journal.attuned.page31\""),
			"Journal UI should include the Stillpoint Apex page");
		assertTrue(screenSource.contains("journal.attuned.page30"),
			"Journal UI should route to the Maelstrom Apex page");
		assertTrue(screenSource.contains("journal.attuned.page31"),
			"Journal UI should route to the Stillpoint Apex page");
		assertTrue(screenSource.indexOf("journal.attuned.page7") < screenSource.indexOf("journal.attuned.page30")
				&& screenSource.indexOf("journal.attuned.page30") < screenSource.indexOf("journal.attuned.page31")
				&& screenSource.indexOf("journal.attuned.page31") < screenSource.indexOf("journal.attuned.page8"),
			"Apex chapter navigation should page through the base, Maelstrom, and Stillpoint pages together");
		assertTrue(itemSource.contains("\"journal.attuned.page29\""),
			"Written-book fallback should include the Radiant Covenant page");
		assertTrue(itemSource.indexOf("\"journal.attuned.page7\"")
				< itemSource.indexOf("\"journal.attuned.page30\"")
				&& itemSource.indexOf("\"journal.attuned.page30\"")
				< itemSource.indexOf("\"journal.attuned.page31\"")
				&& itemSource.indexOf("\"journal.attuned.page31\"")
				< itemSource.indexOf("\"journal.attuned.page8\""),
			"Written-book fallback should keep the Apex pages adjacent");
	}

	private static String read(Path file) throws IOException {
		return Files.readString(file, StandardCharsets.UTF_8);
	}

	private static String translationValue(String lang, String key) {
		Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
		Matcher matcher = pattern.matcher(lang);
		assertTrue(matcher.find(), "Expected translation key " + key);
		return matcher.group(1);
	}

	private static int paragraphSplits(String copy) {
		return copy.split("\n\n", -1).length - 1;
	}
}
