package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.FocusPreset;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class BuildShareCodecTest {
	@Test
	void roundTripPreservesPreset() {
		FocusPreset preset = new FocusPreset("Brawler",
			List.of("attuned:edge_focus", "", "attuned:flame_focus", "", "", "attuned:wind_focus"));
		String encoded = BuildShareCodec.encode(preset);
		FocusPreset decoded = BuildShareCodec.decode(encoded).orElseThrow();
		assertEquals(preset.name(), decoded.name());
		assertEquals(preset.slots(), decoded.slots());
	}

	@Test
	void roundTripPreservesOptionalSetupMetadata() {
		FocusPreset preset = new FocusPreset("Rescue",
			List.of("attuned:tide_focus", "", "", "", "", ""),
			new FocusPreset.SetupMetadata(
				"Support",
				"Bring breath",
				3,
				List.of("No reveal carrier"),
				List.of("attuned:tide_focus", "attuned:rescue_focus"),
				"1.21.11",
				"1.5.4"));

		FocusPreset decoded = BuildShareCodec.decode(BuildShareCodec.encode(preset)).orElseThrow();

		assertEquals("Support", decoded.role());
		assertEquals("Bring breath", decoded.note());
		assertEquals(3, decoded.preferredPartySize());
		assertEquals(List.of("No reveal carrier"), decoded.warnings());
		assertEquals(List.of("attuned:tide_focus", "attuned:rescue_focus"), decoded.requires());
		assertEquals("1.21.11", decoded.minecraftVersion());
		assertEquals("1.5.4", decoded.attunedVersion());
	}

	@Test
	void rejectsBadPrefix() {
		FocusPreset preset = new FocusPreset("Test", List.of());
		String encoded = BuildShareCodec.encode(preset);
		assertTrue(BuildShareCodec.decode(encoded.replace(BuildShareCodec.PREFIX, "wrong:")).isEmpty());
		assertTrue(BuildShareCodec.decode(encoded.substring(BuildShareCodec.PREFIX.length())).isEmpty());
	}

	@Test
	void rejectsBadBase64() {
		assertTrue(BuildShareCodec.decode(BuildShareCodec.PREFIX + "!!!").isEmpty());
	}

	@Test
	void rejectsMoreThanSixSlotsInJson() {
		String json = "{\"name\":\"Test\",\"slots\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\"]}";
		String encoded = BuildShareCodec.PREFIX
			+ Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
		assertTrue(BuildShareCodec.decode(encoded).isEmpty());
	}

	@Test
	void rejectsOverlongNameInJson() {
		String json = "{\"name\":\"" + "a".repeat(33) + "\",\"slots\":[]}";
		String encoded = BuildShareCodec.PREFIX
			+ Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
		assertTrue(BuildShareCodec.decode(encoded).isEmpty());
	}

	@Test
	void rejectsNonStringNameMetadataInJson() {
		String json = "{\"name\":{\"text\":\"Raid\",\"clickEvent\":{\"action\":\"run_command\"}},\"slots\":[]}";
		String encoded = BuildShareCodec.PREFIX
			+ Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));

		assertTrue(BuildShareCodec.decode(encoded).isEmpty(),
			"Build-share names must be plain strings, not chat component-like metadata objects.");
	}

	@Test
	void rejectsNonStringSlotMetadataInJson() {
		String json = "{\"name\":\"Raid\",\"slots\":[42]}";
		String encoded = BuildShareCodec.PREFIX
			+ Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));

		assertTrue(BuildShareCodec.decode(encoded).isEmpty(),
			"Build-share slot ids must be plain strings, not JSON numbers or other metadata.");
	}

	@Test
	void decodesKnownSetupMetadataAndIgnoresUnknownOptionalFields() {
		String json = """
			{"name":"Road","slots":["attuned:edge_focus"],"role":"Witness","note":"Carry reveal",
			"party_size":4,"requires":["attuned:edge_focus","attuned:missing_focus"],"mc":"1.21.11",
			"attuned":"1.5.4","unknown":{"clickEvent":{"action":"run_command"}}}
			""";
		String encoded = BuildShareCodec.PREFIX
			+ Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));

		FocusPreset decoded = BuildShareCodec.decode(encoded).orElseThrow();

		assertEquals("Witness", decoded.role());
		assertEquals("Carry reveal", decoded.note());
		assertEquals(4, decoded.preferredPartySize());
		assertEquals(List.of("attuned:edge_focus", "attuned:missing_focus"), decoded.requires());
		assertEquals(AttunedInv.SIZE, decoded.slots().size(),
			"Requires metadata should not be trusted as slot grants or extra equipped Foci.");
	}

	@Test
	void rejectsNonStringSetupMetadataInJson() {
		String json = "{\"name\":\"Raid\",\"slots\":[],\"role\":{\"text\":\"Witness\"}}";
		String encoded = BuildShareCodec.PREFIX
			+ Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));

		assertTrue(BuildShareCodec.decode(encoded).isEmpty(),
			"Build-share metadata must be plain strings/lists, not chat component-like objects.");
	}

	@Test
	void rejectsNonStringRequiresMetadataInJson() {
		String json = "{\"name\":\"Raid\",\"slots\":[],\"requires\":[42]}";
		String encoded = BuildShareCodec.PREFIX
			+ Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));

		assertTrue(BuildShareCodec.decode(encoded).isEmpty(),
			"Build-share requires metadata must contain only plain Focus id strings.");
	}

	@Test
	void padsFewerThanSixSlotsToInventorySize() {
		String json = "{\"name\":\"Sparse\",\"slots\":[\"attuned:edge_focus\"]}";
		String encoded = BuildShareCodec.PREFIX
			+ Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
		FocusPreset decoded = BuildShareCodec.decode(encoded).orElseThrow();
		assertEquals(AttunedInv.SIZE, decoded.slots().size());
		assertEquals("attuned:edge_focus", decoded.slots().get(0));
		assertEquals("", decoded.slots().get(5));
	}

	@Test
	void decodedBuildNamesAreSanitizedBeforeImport() {
		String json = "{\"name\":\"\\n§cRaid\\r\\nWatch\\t§l!\",\"slots\":[]}";
		String encoded = BuildShareCodec.PREFIX
			+ Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));

		FocusPreset decoded = BuildShareCodec.decode(encoded).orElseThrow();

		assertEquals("RaidWatch!", decoded.name(),
			"Imported build-share names should not preserve newlines or Minecraft formatting codes.");
	}
}
