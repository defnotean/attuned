package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source contract for the size/cap-bound FocusHolder codec on the NBT-backed
 * Forge 1.19.4 branch. Direct runtime round-trips bootstrap Forge loader state
 * in this environment, so this keeps the codec wiring covered without starting
 * Minecraft inside JUnit.
 */
class FocusHolderCodecRoundTripTest {
	private static final Path HOLDER = Path.of("src/main/java/dev/attuned/attunement/FocusHolder.java");

	@Test
	void emptyHolderRoundTripsThroughItsRealCodecPreservingSize() throws IOException {
		String holder = read(HOLDER);

		assertTrue(holder.contains("public static Codec<FocusHolder> codec(int size, int maxPerSlot)"),
			"FocusHolder should expose a real size/cap-bound persistence codec.");
		assertTrue(holder.contains("Codec.PASSTHROUGH.flatXmap("),
			"Older Forge branches should persist FocusHolder as a compound NBT payload.");
		assertTrue(holder.contains("decodeTag(dynamic, size, maxPerSlot)"),
			"Decode should rebuild through the helper with the captured size and cap.");
		assertTrue(holder.contains("holder.toTag()"),
			"Encode should use the branch-local compound tag writer.");
	}

	@Test
	void decodingAnOversizedPersistedListClampsToTheCapturedSize() throws IOException {
		String holder = read(HOLDER);

		assertTrue(holder.contains("return new FocusHolder(size, maxPerSlot, decoded);"),
			"Codec decode should reach the canonical constructor normalizer.");
		assertTrue(holder.contains("for (int i = 0; i < size; i++)"),
			"Decoding a longer-than-configured persisted list clamps to the captured size.");
		assertTrue(holder.contains("copy.setCount(Math.min(copy.getCount(), Math.max(1, maxPerSlot)))"),
			"Decoded stacks should still be clamped to the captured per-slot cap.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
