package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FocusPresetTest {
	private static final Path ATTACHMENTS = Path.of("src/main/java/dev/attuned/attunement/AttunedAttachments.java");

	@Test
	void presetNormalizesToSixSlotsAndTrimsName() {
		FocusPreset preset = new FocusPreset("  Brawler  ", List.of("attuned:edge_focus"));
		assertEquals("Brawler", preset.name(), "Preset name should be trimmed.");
		assertEquals(AttunedInv.SIZE, preset.slots().size(), "Preset should always carry exactly SIZE slots.");
		assertEquals("attuned:edge_focus", preset.slots().get(0));
		assertEquals("", preset.slots().get(5), "Missing slots should pad to empty ids.");
	}

	@Test
	void presetTruncatesOverlongSlotLists() {
		FocusPreset preset = new FocusPreset("x", List.of("a", "b", "c", "d", "e", "f", "g", "h"));
		assertEquals(AttunedInv.SIZE, preset.slots().size(), "Extra slots beyond SIZE are truncated.");
	}

	@Test
	void presetRejectsBlankNames() {
		FocusPreset preset = new FocusPreset("   ", List.of());
		assertTrue(!preset.name().isEmpty(), "Blank preset names should fall back to a placeholder, not persist empty.");
	}

	@Test
	void presetsAttachmentMatchesInventoryPersistenceContract() throws IOException {
		String attachments = read(ATTACHMENTS);
		assertTrue(attachments.contains("AttachmentType<List<FocusPreset>> PRESETS")
				|| attachments.contains("private static final String PRESETS_KEY"),
			"Presets should be stored per-player.");
		assertTrue(attachments.contains("FocusPreset.CODEC.listOf()")
				|| attachments.contains("preset.toTag()"),
			"Presets should persist via a normalized preset representation.");
		assertTrue(attachments.contains("FocusPreset.STREAM_CODEC.apply(ByteBufCodecs.list())")
				|| attachments.contains(".buildAndRegister(new ResourceLocation(Attuned.MOD_ID, \"presets\"))")
				|| attachments.contains("AttunedStatePayload"),
			"Presets should sync to the owning client through the active branch sync path.");
		assertTrue(attachments.contains("AttachmentSyncPredicate.targetOnly()")
				|| attachments.contains("AttachmentRegistry.<List<FocusPreset>>builder()")
				|| attachments.contains("ServerPlayNetworking.send(serverPlayer")
				|| attachments.contains("NetworkPackets.send(serverPlayer"),
			"Presets should sync only to the owning client when sync is available.");
		assertTrue(attachments.contains(".copyOnDeath()")
				|| attachments.contains("STATES.put(to.getUUID(), state(from).copy());"),
			"Presets should survive death.");
		assertTrue(attachments.contains("public static List<FocusPreset> getPresets(Player player)"),
			"There should be a read helper.");
		assertTrue(attachments.contains("public static void savePreset(Player player, FocusPreset preset)"),
			"There should be a save helper.");
		assertTrue(attachments.contains("public static void deletePreset(Player player, int index)"),
			"There should be a delete helper.");
		assertTrue(attachments.contains("List.copyOf("),
			"Preset writes should persist an immutable defensive snapshot, like milestones/onboarding.");
	}

	@Test
	void presetReadsClampOldPersistedListsToTheMaxPresetCap() throws IOException {
		String attachments = read(ATTACHMENTS);
		assertTrue(attachments.contains("return normalizePresets(player.getAttachedOrElse(PRESETS, List.of()));")
				|| attachments.contains("return normalizePresets(state(player).presets);"),
			"Preset reads should clamp old persisted/synced lists instead of exposing more than MAX_PRESETS.");
		assertTrue(attachments.contains("private static List<FocusPreset> normalizePresets(List<FocusPreset> presets)"),
			"Preset list normalization should be centralized.");
		assertTrue(attachments.contains("Math.min(MAX_PRESETS, presets.size())"),
			"Preset list normalization should cap reads to MAX_PRESETS.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
