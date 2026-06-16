package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.attuned.test.MinecraftTestBootstrap;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
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
	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureBootstrapped();
	}

	@Test
	void emptyHolderRoundTripsThroughItsRealCodecPreservingSize() {
		Codec<FocusHolder> codec = FocusHolder.codec(27, 1);
		FocusHolder original = FocusHolder.empty(27, 1);

		Tag encoded = codec.encodeStart(NbtOps.INSTANCE, original)
			.result()
			.orElseThrow(() -> new AssertionError("FocusHolder failed to encode through its codec"));
		FocusHolder decoded = codec.parse(NbtOps.INSTANCE, encoded)
			.result()
			.orElseThrow(() -> new AssertionError("FocusHolder failed to decode through its codec"));

		assertEquals(27, decoded.items().size(),
			"A satchel-sized holder must decode back to its configured slot count.");
		for (int i = 0; i < 27; i++) {
			assertTrue(decoded.get(i).isEmpty(), "Every empty slot survives the encode/decode round-trip.");
		}
	}

	@Test
	void decodingAnOversizedPersistedListClampsToTheCapturedSize() {
		// Simulate a persisted satchel whose stored list is longer than the codec's
		// configured size (e.g. SATCHEL_SIZE shrank between versions): decode must clamp.
		Codec<FocusHolder> wide = FocusHolder.codec(40, 1);
		Codec<FocusHolder> narrow = FocusHolder.codec(27, 1);

		Tag encodedWide = wide.encodeStart(NbtOps.INSTANCE, FocusHolder.empty(40, 1))
			.result()
			.orElseThrow(() -> new AssertionError("wide holder failed to encode"));
		FocusHolder decodedNarrow = narrow.parse(NbtOps.INSTANCE, encodedWide)
			.result()
			.orElseThrow(() -> new AssertionError("narrow holder failed to decode an oversized list"));

		assertEquals(27, decodedNarrow.items().size(),
			"Decoding a longer-than-configured persisted list clamps to the captured size.");
	}
}
