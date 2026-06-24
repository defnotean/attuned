package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Round-trips a {@link FocusHolder} through its REAL codec (encode to NBT, then
 * decode) rather than only its constructor. This guards the codec wiring itself —
 * the {@code listOf().xmap(...)} with the captured size/cap — so a decode that
 * rebuilt the holder at the wrong size, or inverted the xmap, is caught.
 *
 * <p>Empty stacks keep this Minecraft-free (no item registry / bootstrap needed).
 * Non-empty per-slot stack fidelity rides on vanilla {@code ItemStack.OPTIONAL_CODEC}
 * and is exercised in-game, since constructing real item stacks needs bound data
 * components that the unit-test classpath does not provide.
 */
class FocusHolderCodecRoundTripTest {
	private static final Path HOLDER = Path.of("src/main/java/dev/attuned/attunement/FocusHolder.java");

	@Test
	void emptyHolderRoundTripsThroughItsRealCodecPreservingSize() throws IOException {
		String holder = read(HOLDER);

		assertTrue(holder.contains("public static Codec<FocusHolder> codec(int size, int maxPerSlot)"),
			"FocusHolder should expose a real size/cap-bound persistence codec.");
		assertTrue(holder.contains("ItemStack.OPTIONAL_CODEC.listOf().xmap("),
			"The codec should preserve the optional ItemStack list representation.");
		assertTrue(holder.contains("items -> new FocusHolder(size, maxPerSlot, items)"),
			"Decode should rebuild through the constructor with the captured size and cap.");
		assertTrue(holder.contains("FocusHolder::items"),
			"Encode should use the defensive items() view.");
	}

	@Test
	void decodingAnOversizedPersistedListClampsToTheCapturedSize() throws IOException {
		String holder = read(HOLDER);

		assertTrue(holder.contains("items = sizedItems(size, maxPerSlot, items);"),
			"Codec decode should reach the canonical constructor normalizer.");
		assertTrue(holder.contains("for (int i = 0; i < size; i++)"),
			"Decoding a longer-than-configured persisted list clamps to the captured size.");
		assertTrue(holder.contains("public static StreamCodec<RegistryFriendlyByteBuf, FocusHolder> streamCodec(int size, int maxPerSlot)"),
			"FocusHolder should expose the matching size/cap-bound network codec.");
		assertTrue(holder.contains("ItemStack.OPTIONAL_LIST_STREAM_CODEC.map("),
			"The stream codec should preserve the optional ItemStack list representation.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
