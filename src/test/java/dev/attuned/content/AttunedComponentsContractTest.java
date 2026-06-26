package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AttunedComponentsContractTest {
	private static final Path COMPONENTS = Path.of("src/main/java/dev/attuned/content/AttunedComponents.java");
	private static final Path BOOTSTRAP = Path.of("src/main/java/dev/attuned/Attuned.java");

	@Test
	void satchelContentsComponentRegistersInsideTheIdempotentGuard() throws IOException {
		String components = read(COMPONENTS);
		assertTrue(components.contains("private static boolean initialized;"),
			"Component registration should be idempotent.");
		assertTrue(components.contains("if (initialized)"),
			"Component registration should skip repeated init calls.");
		assertTrue(components.contains("initialized = true;"),
			"Component registration should set its init guard.");
		assertTrue(components.contains("public static DataComponentType<FocusHolder> SATCHEL_CONTENTS;"),
			"SATCHEL_CONTENTS should be an assignable (non-final) field populated in init(), like AltarMenuType.TYPE.");
		assertTrue(components.contains("NeoForgeDeferredRegistries.dataComponent"),
			"The component should register into the DATA_COMPONENT_TYPE deferred registry.");
		assertBefore(components, "initialized = true;", "NeoForgeDeferredRegistries.dataComponent");
		assertTrue(components.contains("\"satchel_contents\""),
			"The component id path should be satchel_contents.");
		assertTrue(components.contains(".persistent(FocusHolder.codec(SATCHEL_SIZE, 1))"),
			"The component should persist via the holder codec.");
		assertTrue(components.contains(".networkSynchronized(FocusHolder.streamCodec(SATCHEL_SIZE, 1))"),
			"The component should sync via the holder stream codec.");
		assertTrue(components.contains("public static final int SATCHEL_SIZE = 27;"),
			"Satchel capacity must be a stable pinned constant (27 = a 9x3 foci grid) that never shrinks.");
		assertTrue(components.contains("public static FocusHolder emptyContents()"),
			"AttunedComponents should expose an emptyContents() default.");
	}

	@Test
	void componentsInitRunsBeforeContentInit() throws IOException {
		String bootstrap = read(BOOTSTRAP);
		int components = bootstrap.indexOf("AttunedComponents.init()");
		int content = bootstrap.indexOf("AttunedContent.init()");
		assertTrue(components >= 0, "Bootstrap should initialize AttunedComponents.");
		assertTrue(content >= 0, "Bootstrap should initialize AttunedContent.");
		assertTrue(components < content,
			"Components must register before items so the satchel can attach a default component.");
	}

	private static void assertBefore(String source, String earlier, String later) {
		int e = source.indexOf(earlier);
		int l = source.indexOf(later);
		assertTrue(e >= 0 && l >= 0 && e < l, "Expected " + earlier + " before " + later);
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
