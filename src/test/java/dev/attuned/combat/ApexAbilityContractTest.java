package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Contract coverage for Apex identity abilities: when the Focus Ability key
 * fires with no active ability Focus, an armed Maelstrom or Stillpoint capstone
 * may unleash its identity ability instead.
 */
class ApexAbilityContractTest {
	private static final Path ABILITY_STATE =
		Path.of("src/main/java/dev/attuned/network/FocusAbilityState.java");
	private static final Path APEX =
		Path.of("src/main/java/dev/attuned/combat/Apex.java");
	private static final Path KNOCKBACK_MIXIN =
		Path.of("src/main/java/dev/attuned/mixin/LivingEntityKnockbackMixin.java");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");
	private static final Path REFERENCE =
		Path.of("docs/reference.md");

	@Test
	void abilityStateFallsThroughToTheApexIdentityAbility() throws IOException {
		String state = read(ABILITY_STATE);

		assertTrue(state.contains("Apex.tryIdentityAbility(player)"),
			"With no active ability Focus, the ability key should offer the Apex identity ability.");
		assertTrue(state.contains("item.attuned.focus_ability.none"),
			"Falling through with no Apex ability should still explain that no ability Focus is equipped.");
	}

	@Test
	void apexExposesAGatedIdentityAbilityWithCooldowns() throws IOException {
		String apex = read(APEX);

		assertTrue(apex.contains("public static boolean tryIdentityAbility(ServerPlayer player)"),
			"Apex should expose the identity ability entry point used by the ability key.");
		assertTrue(apex.contains("Resonance.atApex(player)"),
			"Identity abilities should only fire while the capstone is armed at Apex resonance.");
		assertTrue(apex.contains("Capstone.MAELSTROM") && apex.contains("Capstone.STILLPOINT"),
			"Only the Maelstrom and Stillpoint capstones own an identity ability.");
		assertTrue(apex.contains("static final int MAELSTROM_NOVA_COOLDOWN_TICKS = 600"),
			"Maelstrom's nova should be on a 600-tick cooldown.");
		assertTrue(apex.contains("static final int STILLPOINT_FIELD_COOLDOWN_TICKS = 600"),
			"Stillpoint's field should be on a 600-tick cooldown.");
	}

	@Test
	void identityAbilityCooldownMapIsCleanupRegistered() throws IOException {
		String apex = read(APEX);

		assertTrue(apex.contains("Map<UUID, Long> identityCooldowns")
				|| apex.contains("identityCooldowns = new HashMap<>()"),
			"Identity ability cooldowns should be tracked per player.");
		assertTrue(apex.contains("identityCooldowns.clear()"),
			"Identity cooldowns must be cleared on server stop.");
		assertTrue(!apex.contains("identityCooldowns.remove(uuid)"),
			"Identity cooldowns must survive relog so disconnecting cannot reset the ability cooldown.");
	}

	@Test
	void maelstromKnocksBackAndWeakensWhileStillpointPacifiesMonsters() throws IOException {
		String apex = read(APEX);

		assertTrue(apex.contains("getEntitiesOfClass(LivingEntity.class"),
			"Maelstrom's nova should sweep nearby living entities.");
		assertTrue(apex.contains("MobEffects.WEAKNESS"),
			"Maelstrom's nova should apply Weakness to caught foes.");
		assertTrue(apex.contains(".knockback("),
			"Maelstrom's nova should knock caught foes away from the player.");
		assertTrue(apex.contains("getEntitiesOfClass(Monster.class"),
			"Stillpoint's field should gather nearby hostile monsters.");
		assertTrue(apex.contains("monster.setTarget(null)") || apex.contains("setTarget(null)"),
			"Stillpoint's field should pacify nearby monsters by dropping their target.");
		assertTrue(apex.contains("MobEffects.SLOWNESS"),
			"Stillpoint's field should apply brief Slowness to nearby monsters.");
	}

	@Test
	void apexKnockbackMixinTracksTheMinecraft26Descriptor() throws IOException {
		String mixin = read(KNOCKBACK_MIXIN);

		assertTrue(mixin.contains("DamageSource source"),
			"The knockback injector must include the 26.2 DamageSource parameter.");
		assertTrue(mixin.contains("float knockbackResistance"),
			"The knockback injector must include the 26.2 resistance parameter.");
		assertTrue(mixin.contains("boolean force"),
			"The knockback injector must include the 26.2 force parameter.");
		assertTrue(mixin.contains("CallbackInfo ci"),
			"The knockback injector should still be cancellable.");
	}

	@Test
	void identityAbilitiesHaveActionbarStringsAndDocs() throws IOException {
		String lang = read(LANG);
		String reference = read(REFERENCE);

		assertTrue(lang.contains("apex.attuned.maelstrom_nova"),
			"Maelstrom's nova needs an action bar string.");
		assertTrue(lang.contains("apex.attuned.stillpoint_field"),
			"Stillpoint's field needs an action bar string.");
		assertTrue(lang.contains("apex.attuned.identity_cooldown"),
			"The identity ability cooldown needs an action bar string.");
		assertTrue(reference.contains("Maelstrom") && reference.contains("chaos nova"),
			"The reference should describe Maelstrom's identity ability.");
		assertTrue(reference.contains("Stillpoint") && reference.contains("tranquility field"),
			"The reference should describe Stillpoint's identity ability.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
