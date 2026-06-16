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
	void padsFewerThanSixSlotsToInventorySize() {
		String json = "{\"name\":\"Sparse\",\"slots\":[\"attuned:edge_focus\"]}";
		String encoded = BuildShareCodec.PREFIX
			+ Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
		FocusPreset decoded = BuildShareCodec.decode(encoded).orElseThrow();
		assertEquals(AttunedInv.SIZE, decoded.slots().size());
		assertEquals("attuned:edge_focus", decoded.slots().get(0));
		assertEquals("", decoded.slots().get(5));
	}
}
