package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract coverage for the Updraft Focus elytra lift behavior. */
class UpdraftFocusContractTest {
	private static final Path BEHAVIOR =
		Path.of("src/main/java/dev/attuned/content/behavior/UpdraftBehavior.java");
	private static final Path NETWORKING =
		Path.of("src/main/java/dev/attuned/network/AttunedNetworking.java");
	private static final Path CLIENT =
		Path.of("src/client/java/dev/attuned/client/UpdraftLiftClient.java");
	private static final Path HURT_MIXIN =
		Path.of("src/main/java/dev/attuned/mixin/LivingEntityHurtMixin.java");
	private static final Path FOCUS =
		Path.of("src/main/resources/data/attuned/attuned/focus/updraft_focus.json");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void updraftFocusIsWiredForElytraLift() throws IOException {
		String behavior = read(BEHAVIOR);
		assertTrue(behavior.contains("public void onTick(ServerPlayer player, ItemStack focus)"));
		assertTrue(behavior.contains("tickFlight(player);"));
		assertTrue(behavior.contains("public void onDeactivate(ServerPlayer player, ItemStack focus)"));
		assertTrue(behavior.contains("setControls(player.getUUID(), false, false);"));
		assertTrue(behavior.contains("tickFlight("));
		assertTrue(behavior.contains("applyFlightControls("));
		assertTrue(behavior.contains("mitigatesFallDamage("));
		assertTrue(behavior.contains("setControls("));
		assertTrue(behavior.contains("LAST_FLIGHT_TICK"));
		assertFalse(behavior.contains("startFallFlying()"),
			"Updraft boost must not force the player into elytra flight from a normal jump.");
		assertFalse(behavior.contains("canStartGlide("),
			"Updraft should only control an already active elytra flight state.");
		assertTrue(behavior.contains("instanceof ElytraItem"),
			"Functional-glider check should accept any ElytraItem (vanilla or modded subclass), "
				+ "not a hardcoded elytra item id; 1.20.6 has no glider data component.");
		assertFalse(behavior.contains("Items.ELYTRA"),
			"Updraft should not hardcode the vanilla elytra item.");
		assertTrue(behavior.contains("BOOST_THRUST"));
		assertTrue(behavior.contains("BRAKE_FACTOR"));
		assertTrue(behavior.contains("MAX_SPEED"));
		assertTrue(behavior.contains("spawnFlightEffects("));
		assertTrue(behavior.contains("spawnBoostEffects("));
		assertTrue(behavior.contains("spawnBrakeEffects("));
		assertTrue(behavior.contains("ParticleTypes.GUST"));
		assertTrue(behavior.contains("ParticleTypes.CLOUD"));
		assertTrue(behavior.contains("SoundEvents.WIND_CHARGE_THROW"));
		assertTrue(behavior.contains("SoundEvents.ELYTRA_FLYING"));
		assertTrue(behavior.contains("recordPvpDamage("));
		assertTrue(behavior.contains("PVP_EXHAUSTION_TICKS = 100"));
		assertTrue(behavior.contains("isPvpExhausted("));
		assertTrue(behavior.contains("applyPvpExhaustion("));
		assertTrue(behavior.contains("MobEffects.WEAKNESS"));
		assertTrue(behavior.contains("MobEffects.MOVEMENT_SLOWDOWN"));
		assertTrue(behavior.contains("item.attuned.updraft_focus.exhausted"));

		String networking = read(NETWORKING);
		assertTrue(networking.contains("UpdraftLiftPayload.TYPE"));
		assertTrue(networking.contains("UpdraftBehavior.setControls"));

		String hurtMixin = read(HURT_MIXIN);
		assertTrue(hurtMixin.contains("UpdraftBehavior.recordPvpDamage(self, source)"));

		String client = read(CLIENT);
		assertTrue(client.contains("UpdraftLiftPayload"));
		assertTrue(client.contains("keyJump.isDown()"));
		assertTrue(client.contains("keySprint.isDown()"));
		assertTrue(client.contains("lastSent"));
		assertTrue(client.contains("lastBrakeSent"));
		assertTrue(client.contains("heartbeat"));
		assertTrue(client.contains("wantsLift(Player player)"));
		assertTrue(client.contains("wantsBrake(Player player)"));
		assertFalse(client.contains("player.isFallFlying() || (!player.onGround()"),
			"Client boost packets must not be sent merely because the player is airborne.");
		assertTrue(client.contains("return player.isFallFlying();"));
		assertTrue(client.contains("heartbeat % 10"));
		assertTrue(client.contains("applyLocalPrediction("));
		assertTrue(client.contains("UpdraftBehavior.controlledMotion("));

		String mixins = read(Path.of("src/main/resources/attuned.mixins.json"));
		assertTrue(mixins.contains("PlayerUpdraftMixin"));
		assertFalse(mixins.contains("LivingEntityUpdraftFallMixin"),
			"Fall mitigation should live in the single ordered LivingEntityHurtMixin pipeline, "
				+ "not a separate mixin whose ordering is left to class-name sort.");
		assertTrue(read(HURT_MIXIN).contains("UpdraftBehavior.mitigatesFallDamage(player)"),
			"The ordered damage pipeline should apply Updraft fall-damage mitigation as an explicit stage.");

		String focus = read(FOCUS);
		assertTrue(focus.contains("\"behavior\": \"attuned:updraft\""));
		assertTrue(focus.contains("\"affinity\": \"zephyr\""));
		assertTrue(focus.contains("\"unique\": true"));

		String lang = read(LANG);
		assertTrue(lang.contains("item.attuned.updraft_focus.effect"));
		assertTrue(lang.contains("item.attuned.updraft_focus.exhausted"));
	}

	private static String read(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
