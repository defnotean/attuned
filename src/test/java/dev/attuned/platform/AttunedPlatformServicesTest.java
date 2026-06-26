package dev.attuned.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class AttunedPlatformServicesTest {
	@Test
	void defaultServiceUsesProvidedConfigDirectory() {
		AttunedPlatformServices services = new AttunedPlatformServices(Path.of("config"));

		assertEquals(Path.of("config", "attuned.json"), services.serverConfigPath());
		assertEquals(Path.of("config", "attuned-client.json"), services.clientConfigPath());
	}

	@Test
	void serviceHolderCanInstallLoaderSpecificPaths() {
		AttunedPlatformServices original = AttunedPlatform.services();
		try {
			AttunedPlatform.install(new AttunedPlatformServices(Path.of("loader-config")));

			assertEquals(Path.of("loader-config", "attuned.json"), AttunedPlatform.services().serverConfigPath());
			assertEquals(Path.of("loader-config", "attuned-client.json"), AttunedPlatform.services().clientConfigPath());
		} finally {
			AttunedPlatform.install(original);
		}
	}
}
