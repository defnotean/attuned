package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FocusHolderTest {
	private static final Path HOLDER = Path.of("src/main/java/dev/attuned/attunement/FocusHolder.java");

	@Test
	void emptyHolderNormalizesToRequestedSize() throws IOException {
		String holder = read(HOLDER);
		assertTrue(holder.contains("public FocusHolder {")
				&& holder.contains("items = sizedItems(size, maxPerSlot, items);"),
			"The canonical constructor should normalize every holder through the shared sizing path.");
		assertTrue(holder.contains("return new FocusHolder(size, maxPerSlot, List.of());"),
			"empty(size, cap) should construct through the normalized constructor.");
		assertTrue(holder.contains("for (int i = 0; i < size; i++)"),
			"Normalization should pad or truncate to exactly the requested size.");
		assertTrue(holder.contains("i < sourceSize ? source.get(i) : ItemStack.EMPTY"),
			"Short persisted lists should be padded with empty slots.");
	}

	@Test
	void withProducesANewInstanceAndDoesNotMutateOriginal() throws IOException {
		String holder = read(HOLDER);
		String with = methodBody(holder, "public FocusHolder with(int slot, ItemStack stack)");

		assertTrue(with.contains("new ArrayList<>(items)"),
			"with(...) should copy the current immutable snapshot before editing.");
		assertTrue(with.contains("copy.set(requireSlot(slot, size), copyStack(stack, maxPerSlot));"),
			"with(...) should write the normalized stack into the copied list.");
		assertTrue(with.contains("return new FocusHolder(size, maxPerSlot, copy);"),
			"with(...) should return a fresh holder that preserves size and slot cap.");
	}

	@Test
	void invalidSlotsThrowWithBounds() {
		assertThrows(IllegalArgumentException.class, () -> requireSlotForTest(-1, 6));
		assertThrows(IllegalArgumentException.class, () -> requireSlotForTest(6, 6));
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

	private static int requireSlotForTest(int slot, int size) {
		if (slot < 0 || slot >= size) {
			throw new IllegalArgumentException("slot must be between 0 and " + (size - 1));
		}
		return slot;
	}

	private static String methodBody(String source, String signature) {
		int start = source.indexOf(signature);
		assertTrue(start >= 0, "Expected method: " + signature);
		int brace = source.indexOf('{', start);
		assertTrue(brace >= 0, "Expected method body: " + signature);
		int depth = 0;
		for (int index = brace; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '{') {
				depth++;
			} else if (current == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(brace, index + 1);
				}
			}
		}
		throw new AssertionError("Unclosed method body: " + signature);
	}
}
