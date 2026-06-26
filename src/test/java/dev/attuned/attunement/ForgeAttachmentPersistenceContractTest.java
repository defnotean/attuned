package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source contracts for the Forge attachment persistence shim. */
class ForgeAttachmentPersistenceContractTest {
	private static final Path ATTACHMENTS =
		Path.of("src/main/java/dev/attuned/attunement/AttunedAttachments.java");
	private static final Path ATTACHMENT_TYPE =
		Path.of("src/main/java/net/fabricmc/fabric/api/attachment/v1/AttachmentType.java");
	private static final Path ATTACHMENT_REGISTRY =
		Path.of("src/main/java/net/fabricmc/fabric/api/attachment/v1/AttachmentRegistry.java");

	@Test
	void attachmentRegistryRetainsPersistentCodec() throws IOException {
		String type = read(ATTACHMENT_TYPE);
		String registry = read(ATTACHMENT_REGISTRY);

		assertTrue(type.contains("private final Codec<T> persistentCodec;"),
			"Forge's Fabric attachment shim must retain each persistent codec.");
		assertTrue(type.contains("public Codec<T> persistentCodec()"),
			"The stored codec must be visible to the Forge persistence adapter.");
		assertTrue(registry.contains("private Codec<T> persistentCodec;"),
			"The builder must carry the codec configured by .persistent(...).");
		assertTrue(registry.contains("this.persistentCodec = Objects.requireNonNull(codec, \"codec\");"),
			".persistent(...) must not be a no-op on Forge.");
		assertTrue(registry.contains("builder.copyOnDeath, builder.persistentCodec"),
			"AttachmentType creation must receive the retained codec.");
		assertTrue(registry.contains("initializer, copyOnDeath, persistentCodec"),
			"The older builder().buildAndRegister(...) path must retain the persistent codec too.");
	}

	@Test
	void serverReadsAndWritesUsePersistentEntityData() throws IOException {
		String attachments = read(ATTACHMENTS);
		String getBody = methodBody(attachments, "public static <T> T get(Player player, AttachmentType<T> type, T fallback)");
		String setBody = methodBody(attachments, "public static <T> void set(Player player, AttachmentType<T> type, T value)");
		String loadBody = methodBody(attachments, "private static <T> Optional<T> loadPersistent(ServerPlayer player, AttachmentType<T> type)");
		String persistBody = methodBody(attachments, "private static <T> void persist(ServerPlayer player, AttachmentType<T> type, T value)");

		assertTrue(getBody.contains("player instanceof ServerPlayer serverPlayer"),
			"Only the logical server should read world-persistent attachment data.");
		assertTrue(getBody.contains("loadPersistent(serverPlayer, type)"),
			"Missing in-memory values must be hydrated from persistent data before defaults are used.");
		assertTrue(getBody.contains(".put(type, persistedValue);"),
			"Hydrated values must be cached so later gameplay reads see the same state.");
		assertTrue(setBody.contains("persist(serverPlayer, type, value);"),
			"Server writes must encode the attachment value into the player's persistent data.");
		assertTrue(loadBody.contains("player.getPersistentData().get(type.id().toString())"),
			"Forge persistence should key values by the attachment id.");
		assertTrue(loadBody.contains("codec.parse(NbtOps.INSTANCE, tag)"),
			"Persistent reads must use the attachment's real codec.");
		assertTrue(persistBody.contains("codec.encodeStart(NbtOps.INSTANCE, value)"),
			"Persistent writes must use the attachment's real codec.");
		assertTrue(persistBody.contains("player.getPersistentData().put(type.id().toString(), tag)"),
			"Encoded values must be written back to persistent entity data.");
	}

	@Test
	void respawnCopiesArePersistedForReconnects() throws IOException {
		String attachments = read(ATTACHMENTS);
		String respawnBody = methodBody(attachments,
			"private static void copyForRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive)");

		assertTrue(respawnBody.contains("syncToClient(newPlayer);"),
			"Even empty respawn copies should refresh the owner HUD mirror.");
		assertTrue(respawnBody.contains("type.copyOnDeath()"),
			"Respawn copy rules should still honor the attachment's copyOnDeath flag.");
		assertTrue(respawnBody.contains("persistCopied(newPlayer, type, value)"),
			"Copied values must be persisted for the replacement player, not only cached in memory.");
		assertTrue(attachments.contains("private static <T> void persistCopied(ServerPlayer player, AttachmentType<T> type, Object value)"),
			"Wildcard respawn copies should be funneled through a typed persistence helper.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static String methodBody(String source, String signaturePrefix) {
		int signatureStart = source.indexOf(signaturePrefix);
		assertTrue(signatureStart >= 0, "Missing method signature: " + signaturePrefix);
		int bodyStart = source.indexOf('{', signatureStart);
		assertTrue(bodyStart >= 0, "Missing method body: " + signaturePrefix);
		int depth = 0;
		for (int index = bodyStart; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '{') {
				depth++;
			} else if (current == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(bodyStart, index + 1);
				}
			}
		}
		throw new AssertionError("Unterminated method body: " + signaturePrefix);
	}
}
