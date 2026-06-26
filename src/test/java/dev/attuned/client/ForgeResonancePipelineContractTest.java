package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source-level contracts for the Forge 1.20.1 resonance HUD data path. */
class ForgeResonancePipelineContractTest {
	private static final Path ATTACHMENTS =
		Path.of("src/main/java/dev/attuned/attunement/AttunedAttachments.java");
	private static final Path CLIENT_SYNC =
		Path.of("src/client/java/dev/attuned/client/AttunedStateClientSync.java");
	private static final Path READOUT =
		Path.of("src/client/java/dev/attuned/client/AttunementReadout.java");
	private static final Path RESONANCE =
		Path.of("src/main/java/dev/attuned/combat/Resonance.java");
	private static final Path DAMAGE_MIXIN =
		Path.of("src/main/java/dev/attuned/mixin/LivingEntityHurtMixin.java");
	private static final Path MAIN_MIXINS =
		Path.of("src/main/resources/attuned.mixins.json");
	private static final Path FORGE_MODS =
		Path.of("src/main/resources/META-INF/mods.toml");

	@Test
	void resonanceWritesSyncFullAttunedStateToForgeClients() throws IOException {
		String resonance = read(RESONANCE);
		String attachments = read(ATTACHMENTS);
		String clientSync = read(CLIENT_SYNC);
		String readout = read(READOUT);

		assertTrue(resonance.contains("AttunedAttachments.setResonance(player, clamped);"),
			"Resonance mutations should write through the AttunedAttachments boundary.");
		assertTrue(attachments.contains("state(player).resonance = clampResonance(value);"),
			"Forge branch-local resonance storage should update before syncing.");
		assertTrue(attachments.contains("sync(player);"),
			"Resonance writes should sync the branch-local player state.");
		assertTrue(attachments.contains("new AttunedStatePayload(state(serverPlayer).toTag())"),
			"Forge branch should send the full Attuned state payload after resonance changes.");
		assertTrue(clientSync.contains("AttunedAttachments.applySync(local, payload.tag());"),
			"Forge client should apply incoming Attuned state payloads to the local attachment mirror.");
		assertTrue(clientSync.contains("AttunementReadout.invalidate(local);"),
			"Applying synced state should invalidate the same-tick HUD readout cache.");
		assertTrue(readout.contains("public static void invalidate(Player player)"),
			"The client readout cache should expose a focused invalidation hook for sync receivers.");
	}

	@Test
	void resonanceProducerDamageBridgeIsPackagedForForgeRuntime() throws IOException {
		String resonance = read(RESONANCE);
		String damageMixin = read(DAMAGE_MIXIN);
		String mixins = read(MAIN_MIXINS);
		String mods = read(FORGE_MODS);

		assertTrue(resonance.contains("AfterDamageCallback.EVENT.register(Resonance::afterDamage)"),
			"Resonance should subscribe to the damage callback that awards combat gauge progress.");
		assertTrue(damageMixin.contains("AfterDamageCallback.EVENT.invoker().afterDamage"),
			"Forge 1.20.1 should fire the damage callback from the LivingEntity damage mixin.");
		assertTrue(mixins.contains("\"LivingEntityHurtMixin\""),
			"The damage callback producer mixin should be listed in the main mixin config.");
		assertTrue(mods.contains("config=\"attuned.mixins.json\""),
			"Forge metadata should ask Forge to load the main Attuned mixin config.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
