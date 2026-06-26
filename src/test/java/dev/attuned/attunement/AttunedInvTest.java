package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AttunedInvTest {
	private static final Path SOURCE = Path.of("src/main/java/dev/attuned/attunement/AttunedInv.java");

	@Test
	void inventoryNormalizesToSixImmutableSlots() throws IOException {
		String source = readSource();

		assertTrue(source.contains("public static final int SIZE = 6;"),
			"AttunedInv should keep the six equipped Focus slots contract.");
		assertTrue(source.contains("for (int i = 0; i < SIZE; i++)"),
			"Construction should pad or truncate to exactly SIZE slots.");
		assertTrue(source.contains("ItemStack stack = i < sourceSize ? source.get(i) : ItemStack.EMPTY;"),
			"Missing slots should normalize to ItemStack.EMPTY.");
		assertTrue(source.contains("return List.copyOf(list);"),
			"The normalized inventory snapshot should be immutable.");
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
	void getCopiesMutableItemStackAndValidatesSlots() throws IOException {
		String source = readSource();

		assertTrue(source.contains("return copyStack(items.get(requireSlot(slot)));"),
			"get should not expose the stored mutable ItemStack from the immutable snapshot.");
		assertTrue(source.contains("private static int requireSlot(int slot)"),
			"Slot validation should be centralized.");
		assertTrue(source.contains("throw new IllegalArgumentException(\"slot must be between 0 and \" + (SIZE - 1));"),
			"Invalid slots should fail with the stable bounds message.");
	}

	private static String readSource() throws IOException {
		assertTrue(Files.isRegularFile(SOURCE), "Expected file to exist: " + SOURCE);
		return Files.readString(SOURCE, StandardCharsets.UTF_8);
	}
}
