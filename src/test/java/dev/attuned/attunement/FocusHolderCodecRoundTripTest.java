package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-level coverage for the holder codec shape. NeoForge 21.11 plain unit
 * tests cannot initialize vanilla ItemStack codecs without a full game loader.
 */
class FocusHolderCodecRoundTripTest {
	private static final Path HOLDER = Path.of("src/main/java/dev/attuned/attunement/FocusHolder.java");

	@Test
	void persistenceCodecRoundTripUsesCapturedSizeAndCap() throws IOException {
		String holder = read(HOLDER);
		assertTrue(holder.contains("ItemStack.OPTIONAL_CODEC.listOf().xmap("),
			"FocusHolder persistence should be list-backed through ItemStack.OPTIONAL_CODEC.");
		assertTrue(holder.contains("items -> new FocusHolder(size, maxPerSlot, items)"),
			"Decoding should rebuild with the codec-captured size and cap.");
		assertTrue(holder.contains("FocusHolder::items"),
			"Encoding should expose the normalized immutable holder items.");
	}

	@Test
	void streamCodecRoundTripUsesCapturedSizeAndCap() throws IOException {
		String holder = read(HOLDER);
		assertTrue(holder.contains("ItemStack.OPTIONAL_LIST_STREAM_CODEC.map("),
			"FocusHolder network sync should be list-backed through ItemStack.OPTIONAL_LIST_STREAM_CODEC.");
		assertTrue(holder.contains("items -> new FocusHolder(size, maxPerSlot, items)"),
			"Network decoding should rebuild with the stream-captured size and cap.");
		assertTrue(holder.contains("FocusHolder::items"),
			"Network encoding should expose the normalized immutable holder items.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
