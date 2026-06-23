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
	void presetSlotKeysCanRequireATemperedCopy() {
		String tempered = FocusPreset.slotKey("attuned:edge_focus", true);
		FocusPreset preset = new FocusPreset("Tempered", List.of(tempered));

		assertEquals("attuned:edge_focus#tempered", preset.slots().get(0),
			"Tempered Focus presets should preserve the component-sensitive slot key.");
		assertEquals("attuned:edge_focus", FocusPreset.slotId(preset.slots().get(0)));
		assertTrue(FocusPreset.requiresTempered(preset.slots().get(0)));
	}

	@Test
	void plainPresetSlotKeysRemainUntemperedAndBackwardCompatible() {
		FocusPreset preset = new FocusPreset("Old Build", List.of("attuned:edge_focus"));

		assertEquals("attuned:edge_focus", preset.slots().get(0));
		assertEquals("attuned:edge_focus", FocusPreset.slotId(preset.slots().get(0)));
		assertTrue(!FocusPreset.requiresTempered(preset.slots().get(0)));
		assertEquals("", preset.role(), "Old saved presets should decode with no setup role metadata.");
		assertEquals("", preset.note(), "Old saved presets should decode with no setup note metadata.");
		assertEquals(0, preset.preferredPartySize(), "Old saved presets should have no party-size preference.");
		assertEquals(List.of(), preset.warnings(), "Old saved presets should have no setup warnings.");
		assertEquals(List.of(), preset.requires(), "Old saved presets should have no build-code requires metadata.");
	}

	@Test
	void presetRejectsBlankNames() {
		FocusPreset preset = new FocusPreset("   ", List.of());
		assertTrue(!preset.name().isEmpty(), "Blank preset names should fall back to a placeholder, not persist empty.");
	}

	@Test
	void presetNamesStripControlCharactersAndMinecraftFormattingCodes() {
		FocusPreset preset = new FocusPreset(" \n§cRaid\r\nWatch\t§l! ", List.of());

		assertEquals("RaidWatch!", preset.name(),
			"Preset names should be single-line plain text before they reach chat or UI.");
	}

	@Test
	void setupMetadataIsSanitizedCappedAndBackwardCompatible() {
		FocusPreset preset = new FocusPreset("Rescue", List.of(),
			new FocusPreset.SetupMetadata(
				" \n\u00A7aWitness\tSupport ",
				" Bring breath\n\u00A7cNo click events ",
				9,
				List.of(" over\ncap ", "\u00A7cduplicate unique "),
				List.of(" attuned:tide_focus#tempered ", "", "attuned:tide_focus", "attuned:rescue_focus"),
				" 1.21.11\n ",
				" 1.5.4\u00A7k "));

		assertEquals("WitnessSupport", preset.role());
		assertEquals("Bring breathNo click events", preset.note());
		assertEquals(8, preset.preferredPartySize(), "Party size metadata should clamp to the supported party max.");
		assertEquals(List.of("overcap", "duplicate unique"), preset.warnings());
		assertEquals(List.of("attuned:tide_focus", "attuned:rescue_focus"), preset.requires(),
			"Requires metadata should be normalized to distinct base Focus ids, not trusted as item grants.");
		assertEquals("1.21.11", preset.minecraftVersion());
		assertEquals("1.5.4", preset.attunedVersion());
	}

	@Test
	void presetCodecPinsOptionalSetupMetadataFields() throws IOException {
		String source = read(Path.of("src/main/java/dev/attuned/attunement/FocusPreset.java"));

		assertTrue(source.contains("record SetupMetadata("),
			"FocusPreset should carry optional setup metadata without changing the old name+slots constructor.");
		assertTrue(source.contains("Codec.STRING.optionalFieldOf(\"role\", \"\")"),
			"Persistent preset data should allow an optional role label.");
		assertTrue(source.contains("Codec.STRING.optionalFieldOf(\"note\", \"\")"),
			"Persistent preset data should allow an optional short note.");
		assertTrue(source.contains("Codec.intRange(0, MAX_PARTY_SIZE).optionalFieldOf(\"party_size\", 0)"),
			"Persistent preset data should allow an optional party-size/solo preference.");
		assertTrue(source.contains("Codec.STRING.listOf().optionalFieldOf(\"warnings\", List.of())"),
			"Persistent preset data should allow captured setup warnings.");
		assertTrue(source.contains("Codec.STRING.listOf().optionalFieldOf(\"requires\", List.of())"),
			"Persistent preset data should allow build-code requires metadata.");
		assertTrue(source.contains("Codec.STRING.optionalFieldOf(\"mc\", \"\")"),
			"Build-code metadata should record the source Minecraft version when present.");
		assertTrue(source.contains("Codec.STRING.optionalFieldOf(\"attuned\", \"\")"),
			"Build-code metadata should record the source Attuned version when present.");
		assertTrue(source.contains("metadata.write(buf)")
				&& source.contains("SetupMetadata.read(buf)"),
			"Synced preset data should carry setup metadata to the owning client through this branch's FriendlyByteBuf path.");
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
				|| attachments.contains("new AttunedStatePayload(state(serverPlayer).toTag())"),
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

	@Test
	void presetReadsPreserveOptionalSetupMetadata() throws IOException {
		String attachments = read(ATTACHMENTS);

		assertTrue(attachments.contains("new FocusPreset(preset.name(), preset.slots(), preset.metadata())"),
			"Preset list normalization should keep saved setup metadata instead of downgrading imported builds to name+slots.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
