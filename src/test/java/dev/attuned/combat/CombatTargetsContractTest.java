package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CombatTargetsContractTest {
	private static final Path SOURCE = Path.of("src/main/java/dev/attuned/combat/CombatTargets.java");

	@Test
	void ownedOrAlliedHostileMobsDoNotCountAsRewardTargets() throws IOException {
		String source = read();

		assertTrue(source.contains("import net.minecraft.world.entity.OwnableEntity;"),
			"CombatTargets should use Minecraft's owner API when it can identify summoned/tamed entities.");
		assertTrue(source.contains("private static boolean isOwnedOrAllied"),
			"The summon/pet reward suppression rule should be centralized in CombatTargets.");
		assertTrue(source.contains("target instanceof OwnableEntity ownable")
				&& source.contains("ownable.getOwnerUUID()"),
			"Owned hostile mobs should be detectable through OwnableEntity#getOwnerUUID on 1.21.1.");
		assertTrue(source.contains("target.isAlliedTo(player)"),
			"Team-allied mobs should not qualify for hostile-only rewards.");

		String gate = methodBody(source,
			"public static boolean isHostileOrPvpOpponent(LivingEntity target, Player player)");
		assertTrue(gate.contains("!isOwnedOrAllied(target, player)"),
			"Hostile-only Attuned rewards should suppress owner-linked or allied summons before the monster gate.");
		assertTrue(gate.contains("isHostile(target)"),
			"Natural monster targets should still be allowed after the summon/alliance guard.");
	}

	private static String read() throws IOException {
		return Files.readString(SOURCE, StandardCharsets.UTF_8);
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
