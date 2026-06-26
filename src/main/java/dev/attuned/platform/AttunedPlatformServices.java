package dev.attuned.platform;

import java.nio.file.Path;

public record AttunedPlatformServices(Path configDirectory) {
	public Path serverConfigPath() {
		return configDirectory.resolve("attuned.json");
	}

	public Path clientConfigPath() {
		return configDirectory.resolve("attuned-client.json");
	}
}
