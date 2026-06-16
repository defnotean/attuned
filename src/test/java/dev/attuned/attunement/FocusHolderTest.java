package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import dev.attuned.test.MinecraftTestBootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FocusHolderTest {
	private static final Path HOLDER = Path.of("src/main/java/dev/attuned/attunement/FocusHolder.java");

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureBootstrapped();
	}

	@Test
	void emptyHolderNormalizesToRequestedSize() {
		FocusHolder holder = FocusHolder.empty(27, 1);
		assertEquals(27, holder.items().size(), "empty(size, cap) should pad to exactly size slots.");
		for (int i = 0; i < 27; i++) {
			assertEquals(ItemStack.EMPTY, holder.get(i), "Every empty slot should read back EMPTY.");
		}
	}

	@Test
	void withProducesANewInstanceAndDoesNotMutateOriginal() {
		FocusHolder original = FocusHolder.empty(6, 1);
		FocusHolder updated = original.with(0, ItemStack.EMPTY);
		assertNotSame(original, updated, "with(...) must be copy-on-write, returning a fresh instance.");
		assertEquals(6, updated.items().size(), "with(...) preserves the configured size.");
	}

	@Test
	void invalidSlotsThrowWithBounds() {
		FocusHolder holder = FocusHolder.empty(6, 1);
		assertThrows(IllegalArgumentException.class, () -> holder.get(-1));
		assertThrows(IllegalArgumentException.class, () -> holder.get(6));
		assertThrows(IllegalArgumentException.class, () -> holder.with(6, ItemStack.EMPTY));
	}

	@Test
	void holderIsAParameterizedImmutableRecordWithCappingAndCodecs() throws IOException {
		String holder = read(HOLDER);
		assertTrue(holder.contains("public record FocusHolder(int size, int maxPerSlot, List<ItemStack> items)"),
			"FocusHolder should be a record parameterizing size and per-slot cap.");
		assertTrue(holder.contains("public static FocusHolder empty(int size, int maxPerSlot)"),
			"FocusHolder should expose an empty(size, maxPerSlot) factory.");
		assertTrue(holder.contains("public FocusHolder with(int slot, ItemStack stack)"),
			"FocusHolder should mutate copy-on-write via with(slot, stack).");
		assertTrue(holder.contains("public ItemStack get(int slot)"),
			"FocusHolder should expose a defensive get(slot).");
		assertTrue(holder.contains("copy.setCount(Math.min(copy.getCount(), Math.max(1, maxPerSlot)))"),
			"FocusHolder should cap each stored stack to maxPerSlot (its OWN parameterized cap).");
		assertTrue(holder.contains("public static Codec<FocusHolder> codec(int size, int maxPerSlot)"),
			"FocusHolder should build a size/cap-bound persistence Codec.");
		assertTrue(holder.contains("ItemStack.OPTIONAL_CODEC.listOf()"),
			"FocusHolder persistence should reuse the OPTIONAL_CODEC list pattern.");
		assertTrue(holder.contains(
			"public static StreamCodec<RegistryFriendlyByteBuf, FocusHolder> streamCodec(int size, int maxPerSlot)"),
			"FocusHolder should build a size/cap-bound network StreamCodec.");
		assertTrue(holder.contains("ItemStack.OPTIONAL_LIST_STREAM_CODEC"),
			"FocusHolder sync should reuse the OPTIONAL_LIST_STREAM_CODEC pattern.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
