package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract coverage for client-side global registration owners. */
class ClientRegistrationContractTest {
	private static final Path CLIENT_SOURCE_ROOT =
		Path.of("src/client/java/dev/attuned");
	private static final Path GRADLE_PROPERTIES =
		Path.of("gradle.properties");
	private static final Path MAIN_MIXIN_CONFIG =
		Path.of("src/main/resources/attuned.mixins.json");
	private static final Path CLIENT_MIXIN_CONFIG =
		Path.of("src/client/resources/attuned.client.mixins.json");
	private static final List<String> CLIENT_REGISTRATION_MARKERS = List.of(
		"ClientPlayNetworking.registerGlobalReceiver",
		"ClientTickEvents.END_CLIENT_TICK.register",
		"HudElementRegistry.attachElementAfter",
		"ItemTooltipCallback.EVENT.register",
		"KeyMappingHelper.registerKeyMapping",
		"LevelRenderEvents.END_MAIN.register",
		"MenuScreens.register"
	);

	@Test
	void directClientRegistrationsAreInitIdempotent() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path file : directClientRegistrationFiles()) {
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
			for (String marker : CLIENT_REGISTRATION_MARKERS) {
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
			"Direct client registrations should be idempotent: " + violations);
	}

	@Test
	void mixinCompatibilityLevelsMatchBranchJavaTarget() throws IOException {
		String gradle = read(GRADLE_PROPERTIES);
		String expected = "JAVA_" + gradleProperty(gradle, "java_version");

		assertTrue(read(MAIN_MIXIN_CONFIG).contains("\"compatibilityLevel\": \"" + expected + "\""),
			"Main mixin compatibility level should match gradle.properties java_version.");
		assertTrue(read(CLIENT_MIXIN_CONFIG).contains("\"compatibilityLevel\": \"" + expected + "\""),
			"Client mixin compatibility level should match gradle.properties java_version.");
	}

	private static List<Path> directClientRegistrationFiles() throws IOException {
		List<Path> files = new ArrayList<>();
		try (var paths = Files.walk(CLIENT_SOURCE_ROOT)) {
			for (Path file : paths
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java"))
					.toList()) {
				String source = read(file);
				if (CLIENT_REGISTRATION_MARKERS.stream().anyMatch(source::contains)) {
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

	private static String gradleProperty(String source, String key) {
		for (String line : source.split("\\R")) {
			if (line.startsWith(key + "=")) {
				return line.substring(key.length() + 1).trim();
			}
		}
		throw new AssertionError("Missing gradle property " + key);
	}
}
