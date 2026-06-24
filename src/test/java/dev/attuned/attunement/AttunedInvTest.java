package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttunedInvTest {
	private static final Path SOURCE = Path.of("src/main/java/dev/attuned/attunement/AttunedInv.java");

	@Test
	void publicConstructorNormalizesToSixSlots() throws IOException {
		String source = readSource();

		assertTrue(source.contains("public static final int SIZE = 6;"),
			"AttunedInv should remain the six-slot equipped Focus snapshot.");
		assertTrue(source.contains("public AttunedInv {")
				&& source.contains("items = sizedItems(items);"),
			"The public constructor should normalize incoming lists through the shared sizing path.");
		assertTrue(source.contains("new ArrayList<>(SIZE)"),
			"Normalization should always rebuild exactly SIZE slots.");
		assertTrue(source.contains("i < sourceSize ? source.get(i) : ItemStack.EMPTY"),
			"Short persisted lists should be padded with empty slots.");
	}

	@Test
	void copyStackCapsStoredStacksToOneItem() throws IOException {
		String source = readSource();

		assertTrue(source.contains("copy.setCount(Math.min(copy.getCount(), 1));"),
			"AttunedInv should enforce one-Focus-per-slot even for direct construction and persisted decode.");
	}

	@Test
	void constructorCopiesMutableItemStacks() throws IOException {
		String source = readSource();

		assertTrue(source.contains("list.add(copyStack(stack));"),
			"The constructor normalization path should copy mutable ItemStack values.");
		assertTrue(source.contains("private static ItemStack copyStack(ItemStack stack)"),
			"ItemStack copying should be centralized in a named helper.");
	}

	@Test
	void withCopiesMutableItemStacks() throws IOException {
		String source = readSource();

		assertTrue(source.contains("copy.set(requireSlot(slot), copyStack(stack));"),
			"with should copy incoming mutable ItemStack values before storing them.");
	}

	@Test
	void itemsViewCopiesMutableItemStacks() throws IOException {
		String source = readSource();

		assertTrue(source.contains("public List<ItemStack> items()"),
			"The public record accessor should be overridden so it can protect mutable stack contents.");
		assertTrue(source.contains("return copyItems(items);"),
			"The public items view should return copied ItemStack values.");
	}

	@Test
	void getCopiesMutableItemStack() throws IOException {
		String source = readSource();

		assertTrue(source.contains("return copyStack(items.get(requireSlot(slot)));"),
			"get should not expose the stored mutable ItemStack from the immutable snapshot.");
	}

	@Test
	void invalidSlotsFailWithClearMessage() {
		IllegalArgumentException negativeGet = assertThrows(IllegalArgumentException.class, () -> requireSlotForTest(-1));
		IllegalArgumentException upperSet = assertThrows(IllegalArgumentException.class, () -> requireSlotForTest(6));

		assertEquals("slot must be between 0 and 5", negativeGet.getMessage());
		assertEquals("slot must be between 0 and 5", upperSet.getMessage());
	}

	private static int requireSlotForTest(int slot) {
		if (slot < 0 || slot >= 6) {
			throw new IllegalArgumentException("slot must be between 0 and 5");
		}
		return slot;
	}

	private static String readSource() throws IOException {
		assertTrue(Files.isRegularFile(SOURCE), "Expected file to exist: " + SOURCE);
		return Files.readString(SOURCE, StandardCharsets.UTF_8);
	}
}
