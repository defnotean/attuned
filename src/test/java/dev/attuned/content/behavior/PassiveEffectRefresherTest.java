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

	@Test
	void weakerPassiveRefreshDoesNotOverwriteStrongerExternalEffect() throws Exception {
		Class<?> refresher = assertDoesNotThrow(() ->
			Class.forName("dev.attuned.content.behavior.PassiveEffectRefresher"));
		Method shouldRefresh = assertDoesNotThrow(() -> refresher.getDeclaredMethod(
			"shouldRefreshState", int.class, int.class, int.class, int.class));
		shouldRefresh.setAccessible(true);

		assertFalse((Boolean) shouldRefresh.invoke(null, 1, 5, 20, 0),
			"A passive Speed I refresh should not downgrade an existing stronger Speed II effect.");
		assertTrue((Boolean) shouldRefresh.invoke(null, 0, 5, 20, 0),
			"A matching passive effect should still refresh near expiry.");
		assertTrue((Boolean) shouldRefresh.invoke(null, 0, 50, 20, 1),
			"A stronger passive should be allowed to replace a weaker current effect.");
	}
}
