package dev.attuned.platform;

import java.nio.file.Path;
import java.util.Objects;

public final class AttunedPlatform {
	private static AttunedPlatformServices services = new AttunedPlatformServices(Path.of("config"));

	private AttunedPlatform() {}

	public static AttunedPlatformServices services() {
		return services;
	}

	public static void install(AttunedPlatformServices replacement) {
		services = Objects.requireNonNull(replacement, "replacement");
	}
}
