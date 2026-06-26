package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FocusHolderTest {
	private static final Path HOLDER = Path.of("src/main/java/dev/attuned/attunement/FocusHolder.java");

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

	@Test
	void holderNormalizesSizeAndProtectsMutableStacks() throws IOException {
		String holder = read(HOLDER);
		assertTrue(holder.contains("size = Math.max(0, size);"),
			"FocusHolder should clamp negative sizes to zero.");
		assertTrue(holder.contains("maxPerSlot = Math.max(1, maxPerSlot);"),
			"FocusHolder should clamp per-slot caps to at least one.");
		assertTrue(holder.contains("for (int i = 0; i < size; i++)"),
			"FocusHolder should pad or truncate to the configured size.");
		assertTrue(holder.contains("ItemStack stack = i < sourceSize ? source.get(i) : ItemStack.EMPTY;"),
			"Missing slots should normalize to ItemStack.EMPTY.");
		assertTrue(holder.contains("return List.copyOf(list);"),
			"FocusHolder should store immutable slot snapshots.");
		assertTrue(holder.contains("return copyItems(items, maxPerSlot);"),
			"The public items accessor should return defensive stack copies.");
	}

	@Test
	void holderValidatesSlotsWithStableBounds() throws IOException {
		String holder = read(HOLDER);
		assertTrue(holder.contains("private static int requireSlot(int slot, int size)"),
			"FocusHolder slot validation should be centralized.");
		assertTrue(holder.contains("throw new IllegalArgumentException(\"slot must be between 0 and \" + (size - 1));"),
			"Invalid holder slots should fail with the stable bounds message.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
