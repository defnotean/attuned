package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CombatHudSettingsContractTest {
	private static final Path COMBAT_HUD_SOURCE =
		Path.of("src/client/java/dev/attuned/client/hud/CombatHud.java");

	@Test
	void combatHudSeparatelyHonorsOwnAndEnemyHudSettings() throws IOException {
		String source = Files.readString(COMBAT_HUD_SOURCE, StandardCharsets.UTF_8);

		assertTrue(source.contains("AttunedClientConfig.get().showOwnAffinityHud()"),
			"Combat HUD should read the own HUD setting.");
		assertTrue(source.contains("AttunedClientConfig.get().showEnemyAffinityHud()"),
			"Combat HUD should read the enemy HUD setting.");
		assertTrue(source.contains("if (!showOwn && !showEnemy)"),
			"Combat HUD should return early when both HUD sections are hidden.");
		assertTrue(source.contains("TargetStance targetStance = showEnemy ? targetedStance(target) : null"),
			"Combat HUD should avoid target stance resolution when enemy HUD is hidden.");
		assertTrue(source.contains("showOwn"), "Combat HUD should branch on showOwn.");
		assertTrue(source.contains("showEnemy"), "Combat HUD should branch on showEnemy.");
	}

	@Test
	void combatHudUsesAttunedPlayerStanceForPvpTargets() throws IOException {
		String source = Files.readString(COMBAT_HUD_SOURCE, StandardCharsets.UTF_8);

		assertTrue(source.contains("targetedStance("),
			"Combat HUD target resolution should go through one stance helper.");
		assertTrue(source.contains("target instanceof Player targetPlayer"),
			"Targeted players should use their equipped active Foci for the PvP HUD marker.");
		assertTrue(source.contains("Attunement.activeSlots(targetPlayer)"),
			"PvP targets with active neutral Foci should still show a neutral target marker.");
		assertTrue(source.contains("drawTargetGem("),
			"Target rendering should support player Discord/Apex/neutral stance sprites, not only mob affinity sprites.");
	}
}
