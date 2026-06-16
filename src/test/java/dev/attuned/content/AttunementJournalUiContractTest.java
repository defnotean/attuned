package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
		assertTrue(screenSource.contains("GuiGraphicsExtractor") || screenSource.contains("GuiGraphics"),
			"Journal UI should use the current client render API");
		assertTrue(screenSource.contains("extractBackground(GuiGraphicsExtractor")
				|| screenSource.contains("public void render(GuiGraphics")
				|| screenSource.contains("public void render(PoseStack"),
			"Journal UI should draw a custom background/content layer");
		assertTrue(screenSource.contains("textures/gui/attunement_journal.png"),
			"Journal UI should use its generated custom book texture");
		assertTrue(screenSource.contains("CHAPTERS = List.of"),
			"Journal UI should keep chapter navigation separate from vanilla book pages");
		assertTrue(screenSource.contains("private static final class JournalButton extends Button"),
			"Journal UI should render chapter/page controls with codex-style custom buttons");
		assertTrue(screenSource.contains("extractContents(GuiGraphicsExtractor")
				|| screenSource.contains("renderContents(GuiGraphics")
				|| screenSource.contains("renderWidget(GuiGraphics")
				|| screenSource.contains("renderWidget(PoseStack"),
			"Journal buttons should draw their own face through the active render API");
		assertTrue(screenSource.contains("private static final int NAV_X_OFFSET = 34"),
			"Chapter buttons should align to the painted journal navigation rail");
		assertTrue(screenSource.contains("private static final int CHAPTER_BUTTON_Y = 34"),
			"Chapter buttons should start on the first painted journal tab");
		assertTrue(screenSource.contains("private static final int CHAPTER_BUTTON_WIDTH = 62"),
			"Chapter buttons should fit inside the painted journal tabs");
		assertTrue(screenSource.contains("private static final int CHAPTER_BUTTON_HEIGHT = 12"),
			"Chapter buttons should be compact enough to stay within the journal panel");
		assertTrue(screenSource.contains("private static final int CHAPTER_BUTTON_GAP = 1"),
			"Chapter buttons should leave visible separation between the painted journal tabs");
		assertTrue(screenSource.contains("private static final int PAGE_CONTENT_WIDTH = 216"),
			"Page content width should match the generated journal texture and preview fixture");
		assertTrue(screenSource.contains("int navX = left + NAV_X_OFFSET"),
			"Chapter button X should come from the painted rail offset, not the outer panel padding");
		assertTrue(screenSource.contains("int y = top + CHAPTER_BUTTON_Y"),
			"Chapter button Y should come from the painted tab offset");
		assertTrue(screenSource.contains("CHAPTER_BUTTON_WIDTH, CHAPTER_BUTTON_HEIGHT"),
			"Chapter buttons should use the painted tab width and height");
		assertTrue(screenSource.contains("return PAGE_CONTENT_WIDTH;"),
			"Page navigation and text should use the painted 216px page region, not derived padding math");
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
		assertTrue(screenSource.contains("chapter(\"Offshore\""),
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
		assertTrue(lang.contains("\"journal.attuned.page34\""),
			"Journal UI should include an Affinity overview page.");
		assertTrue(lang.contains("\"journal.attuned.page35\""),
			"Journal UI should include the first Affinity matchup page.");
		assertTrue(lang.contains("\"journal.attuned.page36\""),
			"Journal UI should include the second Affinity matchup page.");
		assertTrue(screenSource.indexOf("journal.attuned.page4")
				< screenSource.indexOf("journal.attuned.page34")
				&& screenSource.indexOf("journal.attuned.page34")
				< screenSource.indexOf("journal.attuned.page35")
				&& screenSource.indexOf("journal.attuned.page35")
				< screenSource.indexOf("journal.attuned.page36")
				&& screenSource.indexOf("journal.attuned.page36")
				< screenSource.indexOf("journal.attuned.page5"),
			"Affinity reference pages should live in Core after the affinity cycle and before Discord.");
		assertTrue(itemSource.indexOf("\"journal.attuned.page4\"")
				< itemSource.indexOf("\"journal.attuned.page34\"")
				&& itemSource.indexOf("\"journal.attuned.page34\"")
				< itemSource.indexOf("\"journal.attuned.page35\"")
				&& itemSource.indexOf("\"journal.attuned.page35\"")
				< itemSource.indexOf("\"journal.attuned.page36\"")
				&& itemSource.indexOf("\"journal.attuned.page36\"")
				< itemSource.indexOf("\"journal.attuned.page5\""),
			"Written-book fallback should keep Affinity reference pages beside the core affinity cycle.");
		String affinityOverview = translationValue(lang, "journal.attuned.page34").replace("\\n", "\n");
		assertTrue(affinityOverview.contains("Focus tooltips name only the affinity"),
			"Affinity overview should explain that individual Focus tooltips do not list matchups.");
		assertTrue(affinityOverview.contains("journal"),
			"Affinity overview should direct players to the journal for matchup details.");
		String affinityPageOne = translationValue(lang, "journal.attuned.page35").replace("\\n", "\n");
		assertTrue(affinityPageOne.contains("Fury: beats Bastion, Verdant; weak to Holy, Tide."),
			"Journal should list Fury's full affinity matchups.");
		assertTrue(affinityPageOne.contains("Bastion: beats Zephyr, Umbral; weak to Fury, Forge."),
			"Journal should list Bastion's full affinity matchups.");
		assertTrue(affinityPageOne.contains("Zephyr: beats Holy, Tide; weak to Bastion, Umbral."),
			"Journal should list Zephyr's full affinity matchups.");
		assertTrue(affinityPageOne.contains("Holy: beats Fury, Umbral; weak to Zephyr, Verdant."),
			"Journal should list Holy's full affinity matchups.");
		String affinityPageTwo = translationValue(lang, "journal.attuned.page36").replace("\\n", "\n");
		assertTrue(affinityPageTwo.contains("Tide: beats Fury, Forge; weak to Zephyr, Verdant."),
			"Journal should list Tide's full affinity matchups.");
		assertTrue(affinityPageTwo.contains("Forge: beats Bastion, Verdant; weak to Tide, Umbral."),
			"Journal should list Forge's full affinity matchups.");
		assertTrue(affinityPageTwo.contains("Verdant: beats Tide, Holy; weak to Fury, Forge."),
			"Journal should list Verdant's full affinity matchups.");
		assertTrue(affinityPageTwo.contains("Umbral: beats Zephyr, Forge; weak to Bastion, Holy."),
			"Journal should list Umbral's full affinity matchups.");

		assertEightfoldPactPages(screenSource, itemSource, lang);
		assertEightfoldCapstonePages(screenSource, itemSource, lang);
		assertPhase3SurfacingPages(screenSource, itemSource, lang);
	}

	/**
	 * Pins that the Pacts chapter documents all eight single-affinity pacts — the
	 * original four (Pyresworn, Stoneheart, Windrunner, Radiant Covenant) plus the
	 * four promoted ones (Tidesworn, Forgebound, Wildroot, Nightsworn) — in both the
	 * custom screen and the written-book fallback, with matching journal copy.
	 */
	private static void assertEightfoldPactPages(String screenSource, String itemSource, String lang) {
		for (String pactPage : new String[] {
				"journal.attuned.page37", "journal.attuned.page38",
				"journal.attuned.page39", "journal.attuned.page40" }) {
			assertTrue(lang.contains("\"" + pactPage + "\""),
				"Journal copy should include the new pact page " + pactPage);
			assertTrue(screenSource.contains(pactPage),
				"Journal UI should route to the new pact page " + pactPage);
			assertTrue(itemSource.contains("\"" + pactPage + "\""),
				"Written-book fallback should include the new pact page " + pactPage);
		}
		assertTrue(translationValue(lang, "journal.attuned.page37").contains("Tidesworn"),
			"The Tide pact page should name Tidesworn.");
		assertTrue(translationValue(lang, "journal.attuned.page38").contains("Forgebound"),
			"The Forge pact page should name Forgebound.");
		assertTrue(translationValue(lang, "journal.attuned.page39").contains("Wildroot"),
			"The Verdant pact page should name Wildroot.");
		assertTrue(translationValue(lang, "journal.attuned.page40").contains("Nightsworn"),
			"The Umbral pact page should name Nightsworn.");
		assertTrue(screenSource.contains("gemPage(\"journal.attuned.page37\", 0xFF2F7FD0, Affinity.TIDE)"),
			"Tidesworn page should render as a Tide gem page.");
		assertTrue(screenSource.contains("gemPage(\"journal.attuned.page40\", 0xFF7A4FB5, Affinity.UMBRAL)"),
			"Nightsworn page should render as an Umbral gem page.");
	}

	/**
	 * Pins that the Apex chapter documents the four promoted-affinity capstones
	 * (Riptide, Crucible, Bloomward, Gloaming) alongside the originals, in both the
	 * custom screen and the written-book fallback, kept beside the base Apex page.
	 */
	private static void assertEightfoldCapstonePages(String screenSource, String itemSource, String lang) {
		assertTrue(lang.contains("\"journal.attuned.page41\""),
			"Journal copy should include the promoted-affinity capstone page.");
		assertTrue(screenSource.contains("journal.attuned.page41"),
			"Journal UI should route to the promoted-affinity capstone page.");
		assertTrue(itemSource.contains("\"journal.attuned.page41\""),
			"Written-book fallback should include the promoted-affinity capstone page.");
		String capstonePage = translationValue(lang, "journal.attuned.page41").replace("\\n", "\n");
		assertTrue(capstonePage.contains("Riptide"),
			"Capstone page should name the Tide capstone Riptide.");
		assertTrue(capstonePage.contains("Crucible"),
			"Capstone page should name the Forge capstone Crucible.");
		assertTrue(capstonePage.contains("Bloomward"),
			"Capstone page should name the Verdant capstone Bloomward.");
		assertTrue(capstonePage.contains("Gloaming"),
			"Capstone page should name the Umbral capstone Gloaming.");
		assertTrue(screenSource.indexOf("journal.attuned.page7")
				< screenSource.indexOf("journal.attuned.page41")
				&& screenSource.indexOf("journal.attuned.page41")
				< screenSource.indexOf("journal.attuned.page30"),
			"The capstone page should sit between the base Apex page and Maelstrom.");
		assertTrue(itemSource.indexOf("\"journal.attuned.page7\"")
				< itemSource.indexOf("\"journal.attuned.page41\"")
				&& itemSource.indexOf("\"journal.attuned.page41\"")
				< itemSource.indexOf("\"journal.attuned.page30\""),
			"Written-book fallback should keep the capstone page beside the base Apex page.");
	}

	private static void assertPhase3SurfacingPages(String screenSource, String itemSource, String lang) {
		assertTrue(lang.contains("\"journal.attuned.page_tempering\""),
			"Journal copy should include the Tempering page.");
		assertTrue(lang.contains("\"journal.attuned.pact_trials.title\""),
			"Journal copy should include the Pact Trials page title.");
		assertTrue(lang.contains("\"journal.attuned.trial.progress\""),
			"Journal copy should include pact trial progress formatting.");
		assertTrue(lang.contains("\"journal.attuned.trial.complete\""),
			"Journal copy should include pact trial completion copy.");
		assertTrue(screenSource.contains("journal.attuned.page_tempering"),
			"Journal UI should route to the Tempering page.");
		assertTrue(screenSource.contains("PACT_TRIALS_PAGE_KEY"),
			"Journal UI should include the dynamic Pact Trials page.");
		assertTrue(screenSource.contains("pactTrialPageText()"),
			"Journal UI should build pact trial progress from the synced attachment.");
		assertTrue(screenSource.contains("AttunedAttachments.getPactTrialProgress(player)"),
			"Journal UI should read pact trial progress through AttunedAttachments.");
		assertTrue(itemSource.contains("\"journal.attuned.page_tempering\""),
			"Written-book fallback should include the Tempering page.");
		assertTrue(screenSource.indexOf("journal.attuned.page13")
				< screenSource.indexOf("journal.attuned.page_tempering"),
			"Tempering page should follow the Builds adjusting page.");
		assertTrue(screenSource.indexOf("page(\"journal.attuned.page18\"")
				< screenSource.indexOf("dynamicPage(PACT_TRIALS_PAGE_KEY")
				&& screenSource.indexOf("dynamicPage(PACT_TRIALS_PAGE_KEY")
				< screenSource.indexOf("page(\"journal.attuned.page19\""),
			"Pact Trials page should sit between Untethered and Discord in the Pacts chapter.");
		String temperingPage = translationValue(lang, "journal.attuned.page_tempering").replace("\\n", "\n");
		assertTrue(temperingPage.contains("Tempered"),
			"Tempering journal copy should name the Tempered result.");
		assertTrue(temperingPage.contains("+25%"),
			"Tempering journal copy should mention the stat bonus.");
		assertTrue(temperingPage.contains("+1"),
			"Tempering journal copy should mention the extra attunement cost.");
	}

	@Test
	void journalScreenAndWrittenBookFallbackKeepTheSameStaticPageOrder() throws IOException {
		String screenSource = read(JOURNAL_SCREEN_SOURCE);
		String itemSource = read(JOURNAL_ITEM_SOURCE);

		List<String> screenPages = Pattern.compile("(?:page|gemPage)\\(\\\"(journal\\.attuned\\.page[0-9]+)\\\"")
			.matcher(screenSource)
			.results()
			.map(result -> result.group(1))
			.toList();
		List<String> itemPages = Pattern.compile("\\\"(journal\\.attuned\\.page[0-9]+)\\\"")
			.matcher(itemSource)
			.results()
			.map(result -> result.group(1))
			.toList();

		assertEquals(screenPages, itemPages,
			"The written-book fallback page order should mirror the custom journal screen chapter order.");
	}

	@Test
	void journalReadingPaneScrollsInsteadOfTruncating() throws IOException {
		String screenSource = read(JOURNAL_SCREEN_SOURCE);
		assertTrue(screenSource.contains("public boolean mouseScrolled("),
			"The journal should handle the mouse wheel to scroll long pages");
		assertTrue(screenSource.contains("this.scrollOffset"),
			"The journal should track a scroll offset for the reading pane");
		assertTrue(screenSource.contains("this.maxScroll"),
			"The journal should clamp scrolling to a computed maximum");
		assertFalse(screenSource.contains("Component.literal(\"...\")"),
			"The reading pane should scroll, not truncate content with an ellipsis");
		assertTrue(screenSource.contains("private void drawChapter("),
			"Each chapter should render as one continuous, scrollable document");
		assertTrue(screenSource.contains("CHAPTERS.get(this.chapterIndex)"),
			"Navigation should be per-chapter (the rail and Previous/Next move between chapters)");
		assertTrue(screenSource.contains("public boolean mouseClicked("),
			"The journal scrollbar should respond to direct mouse clicks, not only wheel input");
		assertTrue(screenSource.contains("public boolean mouseDragged("),
			"The journal scrollbar thumb should be draggable");
		assertTrue(screenSource.contains("public boolean mouseReleased("),
			"The journal should stop dragging the scrollbar when the mouse button is released");
		assertTrue(screenSource.contains("this.scrollbarDragging"),
			"The journal should track active scrollbar dragging state");
		assertTrue(screenSource.contains("updateScrollFromMouse(mouseY)"),
			"Scrollbar click and drag should map mouse Y to the scroll offset");
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
