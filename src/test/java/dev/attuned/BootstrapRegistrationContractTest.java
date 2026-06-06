package dev.attuned;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract coverage for common mod bootstrap registration owners. */
class BootstrapRegistrationContractTest {
	private static final Path SOURCE_ROOT =
		Path.of("src/main/java/dev/attuned");
	private static final List<String> BOOTSTRAP_REGISTRATION_MARKERS = List.of(
		"CommandRegistrationCallback.EVENT.register",
		"LootTableEvents.MODIFY.register",
		"Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB",
		"Registry.register(BuiltInRegistries.MENU"
	);

	@Test
	void directBootstrapRegistrationsAreInitIdempotent() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path file : directBootstrapRegistrationFiles()) {
			String source = read(file);
			if (!source.contains("private static boolean initialized;")) {
				violations.add(file + ": missing initialized field");
				continue;
			}
			if (!source.contains("if (initialized)")) {
				violations.add(file + ": repeated init calls are not skipped");
			}
			if (!source.contains("initialized = true;")) {
				violations.add(file + ": initialized is never set");
				continue;
			}
			for (String marker : BOOTSTRAP_REGISTRATION_MARKERS) {
				if (source.contains(marker)) {
					try {
						assertBefore(source, "initialized = true;", marker);
					} catch (AssertionError e) {
						violations.add(file + ": guard is set after " + marker);
					}
				}
			}
		}

		assertTrue(violations.isEmpty(),
			"Direct bootstrap registrations should be idempotent: " + violations);
	}

	private static List<Path> directBootstrapRegistrationFiles() throws IOException {
		List<Path> files = new ArrayList<>();
		try (var paths = Files.walk(SOURCE_ROOT)) {
			for (Path file : paths
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java"))
					.toList()) {
				String source = read(file);
				if (BOOTSTRAP_REGISTRATION_MARKERS.stream().anyMatch(source::contains)) {
					files.add(file);
				}
			}
		}
		return files;
	}

	private static void assertBefore(String source, String earlier, String later) {
		int earlierIndex = source.indexOf(earlier);
		int laterIndex = source.indexOf(later);
		assertTrue(earlierIndex >= 0, "Expected source to contain: " + earlier);
		assertTrue(laterIndex >= 0, "Expected source to contain: " + later);
		assertTrue(earlierIndex < laterIndex, "Expected " + earlier + " before " + later);
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
