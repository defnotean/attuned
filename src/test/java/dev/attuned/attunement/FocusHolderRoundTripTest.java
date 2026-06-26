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
	void holderDecodePathPreservesConfiguredSize() throws IOException {
		String holder = read(HOLDER);
		assertTrue(holder.contains("items -> new FocusHolder(size, maxPerSlot, items)"),
			"Codec and stream codec decode should rebuild holders with the captured size/cap.");
		assertTrue(holder.contains("int sourceSize = source == null ? 0 : source.size();"),
			"Decode normalization should tolerate absent persisted lists.");
		assertTrue(holder.contains("for (int i = 0; i < size; i++)"),
			"Decode normalization should clamp overlong lists and pad short lists to the configured size.");
	}

	@Test
	void satchelComponentSurvivesBothSaveAndDropPaths() throws IOException {
		String components = read(COMPONENTS);
		assertTrue(components.contains(".persistent(FocusHolder.codec(SATCHEL_SIZE, 1))"),
			"Contents must persist so a kept-on-death / chunk-saved satchel keeps its foci.");
		assertTrue(components.contains(".networkSynchronized(FocusHolder.streamCodec(SATCHEL_SIZE, 1))"),
			"Contents must network-sync so a dropped ItemEntity carries its foci to clients.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
