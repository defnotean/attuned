package dev.attuned.content.behavior;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class PassiveEffectRefresherTest {
	@Test
	void refreshDurationDecisionOnlyTriggersNearExpiry() throws Exception {
		Class<?> refresher = assertDoesNotThrow(() ->
			Class.forName("dev.attuned.content.behavior.PassiveEffectRefresher"));
		Method shouldRefresh = refresher.getDeclaredMethod(
			"shouldRefreshDuration", int.class, int.class);
		shouldRefresh.setAccessible(true);

		assertTrue((Boolean) shouldRefresh.invoke(null, 20, 20),
			"Passive effect should refresh at the near-expiry threshold");
		assertFalse((Boolean) shouldRefresh.invoke(null, 21, 20),
			"Passive effect should not be reallocated while plenty of duration remains");
	}
}
