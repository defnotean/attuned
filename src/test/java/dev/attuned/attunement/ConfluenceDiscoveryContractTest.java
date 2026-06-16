package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-grep contract coverage for the Confluence discovery attachment and the
 * Journal "Confluences" chapter that renders it. Minecraft-free: it reads source
 * files as Strings and pins the structural decisions a unit test cannot reach
 * without Bootstrapping Minecraft (attachment sync/persistence flags, the journal
 * page/chapter wiring + drift guard, and the synced-attachment render read).
 */
class ConfluenceDiscoveryContractTest {
	private static final Path ATTACHMENTS =
		Path.of("src/main/java/dev/attuned/attunement/AttunedAttachments.java");
	private static final Path JOURNAL =
		Path.of("src/client/java/dev/attuned/client/screen/AttunementJournalScreen.java");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void discoveryAttachmentIsPersistentSyncedCopyOnDeath() throws IOException {
		String attachments = read(ATTACHMENTS);
		assertTrue(attachments.contains(
				"public static final AttachmentType<List<String>> DISCOVERED_CONFLUENCES = AttachmentRegistry.create(")
				|| attachments.contains("public static final AttachmentType<List<String>> DISCOVERED_CONFLUENCES")
				&& attachments.contains("AttachmentRegistry.<List<String>>builder()")
				|| attachments.contains("private static final String DISCOVERED_CONFLUENCES_KEY"),
			"Discoveries must be stored in the active per-player state backend.");
		assertTrue(attachments.contains(
				"new ResourceLocation(Attuned.MOD_ID, \"discovered_confluences\")")
				|| attachments.contains("DiscoveredConfluences"),
			"Discovery state must use a stable discovered_confluences/DiscoveredConfluences id.");
		assertTrue(attachments.contains(".persistent(Codec.STRING.listOf())")
				|| attachments.contains("tag.put(DISCOVERED_CONFLUENCES_KEY, stringList(discoveredConfluences));"),
			"Discovered confluences must persist across restart.");
		assertTrue(attachments.contains(
				".syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly())")
				|| attachments.contains(".buildAndRegister(new ResourceLocation(Attuned.MOD_ID, \"discovered_confluences\"))")
				|| attachments.contains("AttunedStatePayload"),
			"Discovered confluences must sync to the owning client through the active branch sync path.");
		assertTrue(attachments.contains(".copyOnDeath()")
				|| attachments.contains("STATES.put(to.getUUID(), state(from).copy());"),
			"Discoveries must survive death.");

		assertTrue(attachments.contains("public static List<String> getDiscoveredConfluences(Player player)"),
			"A discovery getter must exist for the journal render.");
		assertTrue(attachments.contains("public static void markConfluenceDiscovered(Player player, String id)"),
			"A discovery setter must exist for the runtime to record first activation.");

		String body = methodBody(attachments,
			"public static void markConfluenceDiscovered(Player player, String id)");
		assertTrue(body.contains("normalizedAttachmentId(id)"),
			"markConfluenceDiscovered must normalize the id like markOnboarding.");
		assertTrue(body.contains("List.copyOf(updated)"),
			"markConfluenceDiscovered must store an immutable snapshot.");
		assertTrue(body.contains("setAttached(DISCOVERED_CONFLUENCES,")
				|| body.contains("state(player).discoveredConfluences = List.copyOf(updated);"),
			"markConfluenceDiscovered must write the discovery state.");
		assertTrue(body.contains("sync(player)"),
			"markConfluenceDiscovered must sync the discovery state to the owning client.");
	}

	@Test
	void journalExposesConfluencesChapterWithDriftGuardAndLang() throws IOException {
		String screen = read(JOURNAL);
		assertTrue(screen.contains("chapter(\"Confluences\""),
			"Journal should expose Confluences as its own chapter.");
		assertTrue(screen.contains("dynamicPage(CONFLUENCE_PAGE_KEY"),
			"Confluences should be a dynamic page so its rows render from live discovery state.");
		assertTrue(screen.indexOf("chapter(\"HUD\"") < screen.indexOf("chapter(\"Confluences\""),
			"The Confluences chapter must come after the existing chapters in the rail.");
		assertTrue(screen.contains("List<Chapter> CHAPTERS"),
			"Chapters own their pages directly (no hand-maintained page-index lists).");
		assertTrue(screen.contains("AttunedAttachments.getDiscoveredConfluences("),
			"Confluences page must read the synced discovery attachment to choose discovered vs redacted rows.");
		assertTrue(screen.contains("journal.attuned.confluence.redacted"),
			"Undiscovered Confluences must render the redacted row.");

		String lang = read(LANG);
		assertTrue(lang.contains("\"journal.attuned.confluence.intro\""), "Missing journal intro key.");
		assertTrue(lang.contains("\"journal.attuned.confluence.title\""), "Missing journal title key.");
		assertTrue(lang.contains("\"journal.attuned.confluence.redacted\""), "Missing journal redacted key.");
		assertTrue(lang.contains("\"journal.attuned.confluence.members\""), "Missing journal member-count key.");
	}

	// --- helpers ---

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

	private static String methodRegion(String source, String start, String end) {
		int s = source.indexOf(start);
		assertTrue(s >= 0, "Missing region start: " + start);
		int e = source.indexOf(end, s);
		assertTrue(e >= 0, "Missing region end: " + end);
		return source.substring(s, e);
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
