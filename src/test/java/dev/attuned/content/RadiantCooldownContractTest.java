package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guardrails for reactive defensive cooldowns that should survive Focus swaps/death. */
class RadiantCooldownContractTest {
	private static final Path RADIANT_BEHAVIORS =
		Path.of("src/main/java/dev/attuned/content/behavior/RadiantFocusBehaviors.java");
	private static final Path MOSSHEART_BEHAVIOR =
		Path.of("src/main/java/dev/attuned/content/behavior/MossheartBehavior.java");
	private static final Path KILNWARD_BEHAVIOR =
		Path.of("src/main/java/dev/attuned/content/behavior/KilnwardBehavior.java");

	@Test
	void reactiveRadiantCooldownsAreSeparateFromActiveFocusTracking() throws IOException {
		String source = Files.readString(RADIANT_BEHAVIORS, StandardCharsets.UTF_8);
		String votive = classBody(source, "public static final class Votive implements FocusBehavior");
		String bellwether = classBody(source, "public static final class Bellwether implements FocusBehavior");
		String oathguard = classBody(source, "public static final class Oathguard implements FocusBehavior");

		assertTrue(votive.contains("Set<UUID> ACTIVE"),
			"Votive needs separate active tracking so its cooldown state can survive unequip.");
		assertFalse(methodBody(votive, "public void onDeactivate(ServerPlayer player, ItemStack focus)")
				.contains("STATE.remove(player.getUUID())"),
			"Votive deactivation must not clear the hostile-hit shield cooldown.");
		assertTrue(methodBody(votive,
			"private static void afterDamage(LivingEntity defender, DamageSource source,")
				.contains("ACTIVE.contains(player.getUUID())"),
			"Votive's global damage hook must still require the Focus to be active.");

		assertTrue(bellwether.contains("Set<UUID> active"),
			"Bellwether needs separate active tracking so its reveal cooldown can survive unequip.");
		assertFalse(methodBody(bellwether, "public void onDeactivate(ServerPlayer player, ItemStack focus)")
				.contains("state.remove(player.getUUID())"),
			"Bellwether deactivation must not clear the bell reveal cooldown.");
		assertTrue(methodBody(bellwether, "private InteractionResult useBlock(")
				.contains("!active.contains(serverPlayer.getUUID())"),
			"Bellwether's global bell callback must still require the Focus to be active.");

		assertFalse(methodBody(oathguard, "public void onDeactivate(ServerPlayer player, ItemStack focus)")
				.contains("cooldowns.remove(player.getUUID())"),
			"Oathguard deactivation must not clear the defensive shield cooldown.");
		assertTrue(methodBody(oathguard,
			"private void afterDamage(LivingEntity defender, DamageSource source,")
				.contains("!active.contains(player.getUUID())"),
			"Oathguard's global damage hook must still require the Focus to be active.");
	}

	@Test
	void reactiveMossheartAndKilnwardCooldownsSurviveFocusDeactivation() throws IOException {
		String mossheart = Files.readString(MOSSHEART_BEHAVIOR, StandardCharsets.UTF_8);
		String kilnward = Files.readString(KILNWARD_BEHAVIOR, StandardCharsets.UTF_8);

		assertFalse(methodBody(mossheart, "public void onDeactivate(ServerPlayer player, ItemStack focus)")
				.contains("COOLDOWNS.remove(player.getUUID())"),
			"Mossheart deactivation/death must not clear its hostile-hit Resistance cooldown.");
		assertFalse(methodBody(kilnward, "public void onDeactivate(ServerPlayer player, ItemStack focus)")
				.contains("COOLDOWNS.remove(player.getUUID())"),
			"Kilnward deactivation/death must not clear its hostile-hit Resistance cooldown.");
		assertTrue(methodBody(mossheart,
			"private static void afterDamage(LivingEntity defender, DamageSource source,")
				.contains("!hasActiveMossheart(player)"),
			"Mossheart's global damage hook must still require the Focus to be active.");
		assertTrue(methodBody(kilnward,
			"private static void afterDamage(LivingEntity defender, DamageSource source,")
				.contains("!hasActiveKilnward(player)"),
			"Kilnward's global damage hook must still require the Focus to be active.");
		assertTrue(mossheart.contains("AttunedPlayerCleanup.onForget(COOLDOWNS::remove)")
				&& kilnward.contains("AttunedPlayerCleanup.onForget(COOLDOWNS::remove)"),
			"Disconnect cleanup should still forget stale per-player defensive cooldown state.");
		assertTrue(mossheart.contains("AttunedServerCleanup.onStop(COOLDOWNS::clear)")
				&& kilnward.contains("AttunedServerCleanup.onStop(COOLDOWNS::clear)"),
			"Server stop cleanup should still clear defensive cooldown maps.");
	}

	private static String classBody(String source, String signaturePrefix) {
		int signatureStart = source.indexOf(signaturePrefix);
		assertTrue(signatureStart >= 0, "Missing class signature: " + signaturePrefix);
		int bodyStart = source.indexOf('{', signatureStart);
		assertTrue(bodyStart >= 0, "Missing class body: " + signaturePrefix);
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
		throw new AssertionError("Unterminated class body: " + signaturePrefix);
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
