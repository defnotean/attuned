package dev.attuned.pacts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Minecraft-free contract coverage for Pact Trial progress persistence and accrual rules. */
class PactTrialContractTest {
	@Test
	void codecRoundTripPreservesCountersAndCompletions() {
		PactTrialProgress original = new PactTrialProgress(
			Map.of("pyresworn", 12, "windrunner", 4096),
			List.of("stoneheart", "wildroot"));

		var encoded = PactTrialProgress.CODEC.encodeStart(JsonOps.INSTANCE, original)
			.getOrThrow(msg -> new AssertionError("encode failed: " + msg));
		PactTrialProgress decoded = PactTrialProgress.CODEC.parse(JsonOps.INSTANCE, encoded)
			.getOrThrow(msg -> new AssertionError("decode failed: " + msg));

		assertEquals(original, decoded);
	}

	@Test
	void accrueOnlyWhileMatchingPactAwake() {
		assertTrue(PactTrials.shouldAccrue(Optional.of(Pact.PYRESWORN), Pact.PYRESWORN));
		assertFalse(PactTrials.shouldAccrue(Optional.of(Pact.STONEHEART), Pact.PYRESWORN));
		assertFalse(PactTrials.shouldAccrue(Optional.empty(), Pact.WINDRUNNER));
	}

	@Test
	void tier4CompletedListTracksPermanentCompletions() {
		PactTrialProgress progress = PactTrialProgress.EMPTY.withTier4Completed("pyresworn")
			.withTier4Completed("untethered");

		assertTrue(progress.tier4Completed().contains("pyresworn"));
		assertTrue(progress.tier4Completed().contains("untethered"));
		assertEquals(2, progress.tier4Completed().size());
		assertEquals(progress, progress.withTier4Completed("pyresworn"));
	}

	@Test
	void goalsCoverAllNinePacts() {
		for (Pact pact : Pact.values()) {
			assertTrue(PactTrials.goalOf(pact) > 0, "Missing trial goal for " + pact);
		}
	}
}
