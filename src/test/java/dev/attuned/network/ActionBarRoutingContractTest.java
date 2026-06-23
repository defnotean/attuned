package dev.attuned.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source guardrails for routing noisy overlay feedback through the priority gate. */
class ActionBarRoutingContractTest {
	@Test
	void noisyFeedbackCallSitesUseThePriorityGate() throws IOException {
		assertRoutes("src/main/java/dev/attuned/network/FocusAbilityState.java", "WARNING");
		assertRoutes("src/main/java/dev/attuned/pacts/PactTacticals.java", "WARNING", "ABILITY");
		assertRoutes("src/main/java/dev/attuned/combat/ResonantSurges.java", "PROGRESS", "AMBIENT");
		assertRoutes("src/main/java/dev/attuned/combat/CombatFeedback.java", "PROGRESS");
		assertRoutes("src/main/java/dev/attuned/combat/Apex.java", "WARNING", "ABILITY");
		assertRoutes("src/main/java/dev/attuned/content/behavior/UpdraftBehavior.java", "WARNING");
		assertRoutes("src/main/java/dev/attuned/content/behavior/HarpoonBehavior.java", "WARNING");
		assertRoutes("src/main/java/dev/attuned/combat/UnseenCombat.java", "ABILITY");
		assertRoutes("src/main/java/dev/attuned/synergy/Synergies.java", "PROGRESS");
		assertRoutes("src/main/java/dev/attuned/pacts/PactTrials.java", "PROGRESS");
		assertRoutes("src/main/java/dev/attuned/menu/PresetNetworking.java", "PROGRESS");
		assertRoutes("src/main/java/dev/attuned/onboarding/Onboarding.java", "AMBIENT");
		assertRoutes("src/main/java/dev/attuned/network/AttunedNetworking.java", "PROGRESS");
	}

	private static void assertRoutes(String path, String... priorities) throws IOException {
		String source = java.nio.file.Files.readString(Path.of(path), StandardCharsets.UTF_8);
		assertFalse(source.contains("PlayerMessages.overlay("),
			path + " should not bypass ActionBarMessages through PlayerMessages.overlay.");
		assertFalse(source.contains(".sendOverlayMessage("),
			path + " should not bypass ActionBarMessages through direct overlay sends.");
		for (String priority : priorities) {
			assertTrue(source.contains("ActionBarMessages.Priority." + priority),
				path + " should route at least one feedback path as " + priority + ".");
		}
	}
}
