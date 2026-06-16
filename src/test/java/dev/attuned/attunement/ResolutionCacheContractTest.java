package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ResolutionCacheContractTest {
	private static final Path SOURCE = Path.of("src/main/java/dev/attuned/attunement/Attunement.java");

	@Test
	void currentResolutionCachesByPlayerInventoryIdentityAndCapacity() throws IOException {
		String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

		assertTrue(source.contains("private static final Map<UUID, CachedResolution>"),
			"Attunement should keep a per-player resolution cache keyed by UUID.");
		assertTrue(source.contains(
				"private record CachedResolution(AttunedInv inv, int capacity, BudgetResolver.Resolution resolution)"),
			"The cache entry should remember the immutable inventory instance, capacity, and resolved result.");
		assertTrue(source.contains("cached.inv() == inv"),
			"Cache hits must use AttunedInv object identity so a replaced immutable inventory invalidates the entry.");
		assertTrue(source.contains("cached.capacity() == capacity"),
			"Cache hits must also be gated by the player's current attunement capacity.");
		assertTrue(source.contains("return cached.resolution();"),
			"The fast path should return the previously resolved immutable result.");
		assertTrue(source.contains("AttunedPlayerCleanup.onForget(RESOLUTION_CACHE::remove)"),
			"Per-player cache state should be dropped when the player disconnects.");
		assertTrue(source.contains("AttunedServerCleanup.onStop(RESOLUTION_CACHE::clear)"),
			"Resolution cache state should be cleared when the server stops.");
	}

	@Test
	void cacheIsThreadSafeAndServerOnlyInSingleplayer() throws IOException {
		String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

		assertTrue(source.contains("new ConcurrentHashMap<>()"),
			"In singleplayer the client render thread and server thread share this static map, "
				+ "so it must be a ConcurrentHashMap, never a plain HashMap.");
		assertTrue(source.contains("if (player.level().isClientSide()) {")
				|| source.contains("if (player.getLevel().isClientSide()) {"),
			"Client-side lookups must bypass the cache: the client sees different AttunedInv "
				+ "instances for the same player UUID, which would thrash the server's entries.");
	}
}
