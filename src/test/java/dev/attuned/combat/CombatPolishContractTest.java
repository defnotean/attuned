package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guards for the 1.5.x combat feel / QOL polish batch. */
class CombatPolishContractTest {
	private static final Path READOUT =
		Path.of("src/client/java/dev/attuned/client/AttunementReadout.java");
	private static final Path FOCI_HUD =
		Path.of("src/client/java/dev/attuned/client/hud/FociHud.java");
	private static final Path COMBAT_HUD =
		Path.of("src/client/java/dev/attuned/client/hud/CombatHud.java");
	private static final Path FEEDBACK =
		Path.of("src/main/java/dev/attuned/combat/CombatFeedback.java");
	private static final Path APEX =
		Path.of("src/main/java/dev/attuned/combat/Apex.java");
	private static final Path RESONANCE =
		Path.of("src/main/java/dev/attuned/combat/Resonance.java");
	private static final Path SURGES =
		Path.of("src/main/java/dev/attuned/combat/ResonantSurges.java");
	private static final Path MILESTONES =
		Path.of("src/main/java/dev/attuned/Milestones.java");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void hudUsesSmoothedResonanceDisplay() throws IOException {
		String readout = read(READOUT);
		assertTrue(readout.contains("displayResonance(Player player)"),
			"AttunementReadout should expose smoothed resonance for HUD bars.");
		assertTrue(read(FOCI_HUD).contains("AttunementReadout.displayResonance(player)"),
			"FociHud should paint the smoothed resonance fill.");
		assertTrue(read(COMBAT_HUD).contains("AttunementReadout.displayResonance(player)"),
			"CombatHud should paint the smoothed resonance fill.");
	}

	@Test
	void combatFeedbackCoversAffinityCapstoneProcs() throws IOException {
		String feedback = read(FEEDBACK);
		assertTrue(feedback.contains("riptideDrag("));
		assertTrue(feedback.contains("crucibleIgnite("));
		assertTrue(feedback.contains("bloomwardHeal("));
		assertTrue(feedback.contains("gloamingWeakness("));
		assertTrue(feedback.contains("judgmentStrike("));
		assertTrue(feedback.contains("maelstromHit("));
		assertTrue(feedback.contains("ParticleTypes.SPLASH")
			&& feedback.contains("ParticleTypes.FLAME")
			&& feedback.contains("ParticleTypes.HAPPY_VILLAGER")
			&& feedback.contains("ParticleTypes.SQUID_INK"),
			"Each promoted capstone should use distinct particle types.");

		String apex = read(APEX);
		assertTrue(apex.contains("CombatFeedback.riptideDrag("));
		assertTrue(apex.contains("CombatFeedback.crucibleIgnite("));
		assertTrue(apex.contains("CombatFeedback.bloomwardHeal("));
		assertTrue(apex.contains("CombatFeedback.gloamingWeakness("));
		assertTrue(apex.contains("CombatFeedback.judgmentStrike("));
		assertTrue(apex.contains("CombatFeedback.maelstromHit("));
	}

	@Test
	void combatFeedbackCoversFinisherAndDefensiveCapstones() throws IOException {
		String feedback = read(FEEDBACK);
		assertTrue(feedback.contains("executeFinisher("));
		assertTrue(feedback.contains("unyieldingCap("));
		assertTrue(feedback.contains("stillpointPulse("));
		assertTrue(feedback.contains("resonance.attuned.kill_streak"));

		String apex = read(APEX);
		assertTrue(apex.contains("CombatFeedback.executeFinisher("));
		assertTrue(apex.contains("CombatFeedback.unyieldingCap("));
		assertTrue(apex.contains("CombatFeedback.stillpointPulse("));
		assertTrue(apex.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.ABILITY,\n"
				+ "\t\t\tComponent.translatable(\"apex.attuned.rearmed\""));
		assertTrue(apex.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.WARNING,\n"
				+ "\t\t\tComponent.translatable(\"apex.attuned.dormant\"))"));
	}

	@Test
	void resonancePausesDecayDuringCombatGrace() throws IOException {
		String source = read(RESONANCE);
		assertTrue(source.contains("LAST_COMBAT_TICK"));
		assertTrue(source.contains("COMBAT_GRACE_TICKS"));
		assertTrue(source.contains("markCombat("));
		assertTrue(source.contains("Milestones.onApexArmed("));
	}

	@Test
	void surgeKillRewardAndBeaconAreWired() throws IOException {
		String surges = read(SURGES);
		assertTrue(surges.contains("tryKillReward("));
		assertTrue(surges.contains("ATTUNEMENT_SHARD_FRAGMENT"));
		assertTrue(surges.contains("y + 6.0"));

		String resonance = read(RESONANCE);
		assertTrue(resonance.contains("ResonantSurges.tryKillReward("));
	}

	@Test
	void attunedNativeMilestonesExist() throws IOException {
		String milestones = read(MILESTONES);
		assertTrue(milestones.contains("APEX_ARMED"));
		assertTrue(milestones.contains("FIRST_CONFLUENCE"));
		assertTrue(milestones.contains("PACT_TRIAL"));
		assertTrue(milestones.contains("onApexArmed("));
		assertTrue(milestones.contains("onFirstConfluence("));
		assertTrue(milestones.contains("onPactTrialComplete("));

		String lang = read(LANG);
		assertTrue(lang.contains("apex.attuned.rearmed"));
		assertTrue(lang.contains("apex.attuned.dormant"));
		assertTrue(lang.contains("resonance.attuned.kill_streak"));
		assertTrue(lang.contains("surge.attuned.kill_reward"));
	}

	@Test
	void combatMomentumAndPactPreviewAreWired() throws IOException {
		String momentum = read(Path.of("src/main/java/dev/attuned/combat/CombatMomentum.java"));
		assertTrue(momentum.contains("effectiveCooldown("));
		assertTrue(momentum.contains("killShaveTicks("));

		String resonance = read(RESONANCE);
		assertTrue(resonance.contains("CombatMomentum.killShaveTicks("));
		assertTrue(resonance.contains("PactTacticals.shaveCooldown("));
		assertTrue(resonance.contains("Apex.shaveIdentityCooldown("));
		assertTrue(resonance.contains("killStreak(Player player)"));

		String apex = read(APEX);
		assertTrue(apex.contains("CombatMomentum.effectiveCooldown("));
		assertTrue(apex.contains("shaveIdentityCooldown("));

		String pacts = read(Path.of("src/main/java/dev/attuned/pacts/Pacts.java"));
		assertTrue(pacts.contains("oneAwayAffinity("));

		String synergies = read(Path.of("src/main/java/dev/attuned/synergy/Synergies.java"));
		assertTrue(synergies.contains("oneAwayConfluence("));
		assertTrue(synergies.contains("SynergyResolver.previewOf("));

		String onboarding = read(Path.of("src/main/java/dev/attuned/onboarding/Onboarding.java"));
		assertTrue(onboarding.contains("tryPactPreviewHint("));
		assertTrue(onboarding.contains("Pacts.oneAwayAffinity("));
		assertTrue(onboarding.contains("tryConfluencePreviewHint("));
		assertTrue(onboarding.contains("Synergies.oneAwayConfluence("));
		assertTrue(onboarding.contains("\"confluence_one_away_\""));
		assertTrue(onboarding.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.AMBIENT, preview)"));
	}

	private static String read(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
