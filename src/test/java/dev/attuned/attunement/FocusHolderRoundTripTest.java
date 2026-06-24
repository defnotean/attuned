package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FocusHolderRoundTripTest {
	private static final Path HOLDER = Path.of("src/main/java/dev/attuned/attunement/FocusHolder.java");
	private static final Path COMPONENTS = Path.of("src/main/java/dev/attuned/content/AttunedComponents.java");

	@Test
	void holderPreservesEmptySlotsAcrossItemsRoundTrip() throws IOException {
		String holder = read(HOLDER);

		assertTrue(holder.contains("public List<ItemStack> items()")
				&& holder.contains("return copyItems(items, maxPerSlot);"),
			"The persisted/synced items view should be a defensive copy of the normalized slot list.");
		assertTrue(holder.contains("private static List<ItemStack> sizedItems(int size, int maxPerSlot, List<ItemStack> source)"),
			"Decode should rebuild holder contents through the shared size/cap normalizer.");
		assertTrue(holder.contains("i < sourceSize ? source.get(i) : ItemStack.EMPTY"),
			"Empty padded slots must survive an items() -> constructor round trip.");
	}

	@Test
	void holderTruncatesOversizedSourceToTheConfiguredSize() throws IOException {
		String holder = read(HOLDER);

		assertTrue(holder.contains("for (int i = 0; i < size; i++)"),
			"Decode must clamp an over-long persisted list to the configured size.");
		assertTrue(holder.contains("int sourceSize = source == null ? 0 : source.size();"),
			"Oversized and null persisted lists should be normalized through one path.");
	}

	@Test
	void satchelComponentSurvivesBothSaveAndDropPaths() throws IOException {
		String components = read(COMPONENTS);
		assertTrue(components.contains(".persistent(FocusHolder.codec(SATCHEL_SIZE, 1))")
				|| components.contains("stack.getOrCreateTagElement(ROOT_KEY).put(contentsKey(grand), normalized.toTag())"),
			"Contents must persist so a kept-on-death / chunk-saved satchel keeps its foci.");
		assertTrue(components.contains(".networkSynchronized(FocusHolder.streamCodec(SATCHEL_SIZE, 1))")
				|| components.contains("FocusHolder.fromTag(root.getCompound(contentsKey(grand)), size, 1)"),
			"Contents must network-sync so a dropped ItemEntity carries its foci to clients.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
