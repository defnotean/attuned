package dev.attuned.pacts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for pact-trial HUD polish and completion fanfare. */
class PactTrialHudContractTest {
	private static final Path PACT_TRIALS =
		Path.of("src/main/java/dev/attuned/pacts/PactTrials.java");
	private static final Path FOCI_HUD =
		Path.of("src/client/java/dev/attuned/client/hud/FociHud.java");
	private static final Path ONBOARDING =
		Path.of("src/main/java/dev/attuned/onboarding/Onboarding.java");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void pactTrialCompletionPlaysFanfareAndOnboardingHint() throws IOException {
		String trials = read(PACT_TRIALS);
		String onboarding = read(ONBOARDING);
		String lang = read(LANG);

		assertTrue(trials.contains("SoundEvents.UI_TOAST_CHALLENGE_COMPLETE"),
			"Trial completion should play the challenge-complete toast sound.");
		assertTrue(trials.contains("SoundEvents.AMETHYST_BLOCK_CHIME"),
			"Trial completion should layer the onboarding amethyst chime.");
		assertTrue(trials.contains("ParticleTypes.END_ROD"),
			"Trial completion should burst end-rod particles at the player's chest.");
		assertTrue(trials.contains("new DustParticleOptions(pact.argb()"),
			"Trial completion should tint dust particles with the pact colour.");
		assertTrue(trials.contains("Onboarding.tryPactTrialCompleteHint(player)"),
			"Trial completion should fire the one-time onboarding hint.");
		assertTrue(onboarding.contains("tryPactTrialCompleteHint"),
			"Onboarding should expose the pact-trial-complete hint entry point.");
		assertTrue(onboarding.contains("HINT_PACT_TRIAL_COMPLETE"),
			"The pact-trial-complete hint should be keyed for one-shot delivery.");
		assertTrue(lang.contains("\"onboarding.attuned.pact_trial_complete\""),
			"Lang should include the pact-trial-complete onboarding title.");
		assertTrue(lang.contains("\"onboarding.attuned.pact_trial_complete.detail\""),
			"Lang should include the pact-trial-complete onboarding detail.");
	}

	@Test
	void fociHudDrawsPactTrialProgressPip() throws IOException {
		String hud = read(FOCI_HUD);

		assertTrue(hud.contains("drawTrialPip"),
			"The Foci HUD should paint a pact-trial progress pip.");
		assertTrue(hud.contains("readout.pact()"),
			"The trial pip should require an active pact from the readout.");
		assertTrue(hud.contains("AttunedAttachments.getPactTrialProgress(player)"),
			"The trial pip should read synced trial progress from the attachment.");
		assertTrue(hud.contains("tier4Complete()"),
			"The trial pip should hide once Tier 4 is complete.");
		assertTrue(hud.contains("TRIAL_PIP_W = 20"),
			"The trial pip should use a compact ~20px width.");
		assertTrue(hud.contains("TRIAL_PIP_H = 3"),
			"The trial pip should use a thin 3px height.");
		assertTrue(hud.contains("pact.get().argb()"),
			"The trial pip fill should use the active pact colour.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}