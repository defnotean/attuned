package dev.attuned.content.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Executed coverage for Mossheart's reactive-Resistance cooldown countdown — guards
 * the decrement-and-clear boundary that gates re-granting Resistance on hostile hits.
 */
class MossheartBehaviorCooldownTest {
	@Test
	void countsDownEachTickWhileOnCooldown() {
		assertEquals(239, MossheartBehavior.tickCooldown(240),
			"A fresh 240-tick cooldown decrements toward ready.");
		assertEquals(1, MossheartBehavior.tickCooldown(2),
			"The penultimate tick leaves one tick remaining.");
	}

	@Test
	void clearsTheCooldownOnceItElapses() {
		assertNull(MossheartBehavior.tickCooldown(1),
			"Reaching the final tick clears the cooldown so Resistance can be re-granted.");
		assertNull(MossheartBehavior.tickCooldown(0),
			"A non-positive remaining is treated as cleared.");
	}
}
