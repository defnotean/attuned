package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for Focus procs that advertise direct melee combat. */
class DirectCombatFocusContractTest {
	private static final Path ATTUNED_COMBAT =
		Path.of("src/main/java/dev/attuned/combat/AttunedCombat.java");
	private static final Path UNSEEN_COMBAT =
		Path.of("src/main/java/dev/attuned/combat/UnseenCombat.java");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void thornwardAndLeechOnlyProcFromDirectMeleeSources() throws IOException {
		String combat = read(ATTUNED_COMBAT);
		String afterDamage = methodBody(combat, "private static void afterDamage(LivingEntity defender, DamageSource source,");
		String lang = read(LANG);

		assertTrue(lang.contains("\"item.attuned.thornward_focus.effect\": \"Reflects 25% of melee damage back at attackers.\""),
			"Thornward's player-facing text says melee damage.");
		assertTrue(lang.contains("\"item.attuned.leech_focus.effect\": \"Heals you for 20% of the melee damage you deal.\""),
			"Leech's player-facing text says melee damage.");
		assertTrue(combat.contains("private static boolean isDirectMelee(LivingEntity attacker, DamageSource source)"),
			"Shared direct-melee detection should live beside the other direct-combat helpers.");
		assertTrue(combat.contains("source.getDirectEntity() != attacker"),
			"Direct melee should require the direct damage entity to be the attacker.");
		assertTrue((combat.contains("DamageTypeTags.IS_PROJECTILE")
					&& combat.contains("DamageTypeTags.IS_EXPLOSION"))
				|| (combat.contains("source.isProjectile()")
					&& combat.contains("source.isExplosion()")),
			"Direct melee should reject projectile and explosion damage sources.");
		assertEquals(2, countOccurrences(afterDamage, "&& isDirectMelee(attacker, source)"),
			"Both Thornward reflection and Leech lifesteal should be gated to direct melee hits.");
	}

	@Test
	void needleOnlyAmplifiesDirectNonProjectileNonExplosionHits() throws IOException {
		String combat = read(UNSEEN_COMBAT);
		String adjustDamage = methodBody(combat,
			"public static float adjustDamage(LivingEntity defender, DamageSource source, float amount)");
		String lang = read(LANG);

		assertTrue(lang.contains("\"item.attuned.needle_focus.effect\": \"Your first hit from behind or from a veil deals 35% more damage, then cools down.\""),
			"Needle's player-facing text describes a hit opener, not projectile or explosion splash.");
		assertTrue(combat.contains("import net.minecraft.tags.DamageTypeTags;")
				|| (combat.contains("source.isProjectile()") && combat.contains("source.isExplosion()")),
			"Needle's direct-hit helper should be able to reject projectile and explosion damage tags.");
		assertTrue(combat.contains("private static boolean isDirectHit(ServerPlayer attacker, DamageSource source)"),
			"Needle's direct-hit detection should live beside the opener logic.");
		assertTrue(combat.contains("source.getDirectEntity() != attacker"),
			"Needle should require the player to be the direct damage entity.");
		assertTrue((combat.contains("DamageTypeTags.IS_PROJECTILE")
					&& combat.contains("DamageTypeTags.IS_EXPLOSION"))
				|| (combat.contains("source.isProjectile()")
					&& combat.contains("source.isExplosion()")),
			"Needle should reject projectile and explosion damage sources even if another mod marks the player as direct.");
		assertTrue(adjustDamage.contains("!isDirectHit(attacker, source)"),
			"Needle should return before breaking veil or applying its multiplier when the source is not a direct hit.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected source file to exist: " + path);
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

	private static int countOccurrences(String value, String needle) {
		int count = 0;
		int index = 0;
		while ((index = value.indexOf(needle, index)) >= 0) {
			count++;
			index += needle.length();
		}
		return count;
	}
}
