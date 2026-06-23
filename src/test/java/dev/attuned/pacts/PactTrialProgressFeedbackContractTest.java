package dev.attuned.pacts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for pact-trial mid-progress overlay feedback. */
class PactTrialProgressFeedbackContractTest {
	private static final Path PACT_TRIALS =
		Path.of("src/main/java/dev/attuned/pacts/PactTrials.java");
	private static final Path LANG =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void accrueFiresOneShotMilestoneOverlayToasts() throws IOException {
		String trials = read(PACT_TRIALS);
		String lang = read(LANG);

		assertTrue(trials.contains("TRIAL_PROGRESS_MILESTONES = {25, 50, 75}"),
			"Pact trials should track 25%, 50%, and 75% progress milestones.");
		assertTrue(trials.contains("checkTrialMilestones(player, pact, current, next, goal)"),
			"Accrual should evaluate milestone crossings before updating progress.");
		assertTrue(trials.contains("AttunedAttachments.sawOnboarding(player, onboardId)"),
			"Milestone feedback should gate on the shared onboarding attachment.");
		assertTrue(trials.contains("AttunedAttachments.markOnboarding(player, onboardId)"),
			"Milestone feedback should mark onboarding before firing.");
		assertTrue(trials.contains("\"pact_trial_\" + id + \"_\" + percent"),
			"Milestone onboarding ids should be pact-scoped, e.g. pact_trial_pyresworn_25.");
		assertTrue(trials.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS,"),
			"Milestone feedback should use progress-priority action-bar overlay toasts.");
		assertTrue(trials.contains("pact.attuned.trial.progress"),
			"Milestone overlay should use the shared trial progress lang key.");
		assertTrue(trials.contains("SoundEvents.EXPERIENCE_ORB_PICKUP"),
			"Milestone feedback should play a soft pickup chime.");
		assertTrue(lang.contains("\"pact.attuned.trial.progress\""),
			"Lang should include the trial progress milestone suffix.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
