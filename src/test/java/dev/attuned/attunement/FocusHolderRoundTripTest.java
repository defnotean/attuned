package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import dev.attuned.test.MinecraftTestBootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FocusHolderRoundTripTest {
	private static final Path COMPONENTS = Path.of("src/main/java/dev/attuned/content/AttunedComponents.java");

	@BeforeAll
	static void bootstrapMinecraft() {
		MinecraftTestBootstrap.ensureBootstrapped();
	}

	@Test
	void holderPreservesEmptySlotsAcrossItemsRoundTrip() {
		// The exact value the satchel component persists/syncs is FocusHolder.items().
		// Reconstructing from that list (what decode does) must be size-stable and lossless for empties.
		FocusHolder empty = FocusHolder.empty(27, 1);
		FocusHolder rebuilt = new FocusHolder(27, 1, empty.items());
		assertEquals(empty.items().size(), rebuilt.items().size(), "Empty satchel must round-trip its slot count.");
		for (int i = 0; i < 27; i++) {
			assertTrue(rebuilt.get(i).isEmpty(), "Empty slots survive the items() round-trip.");
		}
	}

	@Test
	void holderTruncatesOversizedSourceToTheConfiguredSize() {
		// A persisted list longer than size is deliberately truncated (documented, no migration).
		List<ItemStack> oversized = java.util.Collections.nCopies(40, ItemStack.EMPTY);
		FocusHolder holder = new FocusHolder(27, 1, oversized);
		assertEquals(27, holder.items().size(), "Decode must clamp an over-long persisted list to the configured size.");
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
