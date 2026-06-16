package dev.attuned.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CombatMomentumTest {
	@Test
	void effectiveCooldownIsUnchangedBelowStreakGate() {
		assertEquals(600, CombatMomentum.effectiveCooldown(600, 2, true));
		assertEquals(600, CombatMomentum.effectiveCooldown(600, 5, false));
	}

	@Test
	void effectiveCooldownScalesWithStreakAtApex() {
		assertEquals(580, CombatMomentum.effectiveCooldown(600, 3, true));
		assertEquals(540, CombatMomentum.effectiveCooldown(600, 5, true));
		assertEquals(440, CombatMomentum.effectiveCooldown(600, 10, true));
		assertEquals(400, CombatMomentum.effectiveCooldown(600, 20, true));
	}

	@Test
	void killShaveTicksScaleWithStreakAtApex() {
		assertEquals(0, CombatMomentum.killShaveTicks(2, true));
		assertEquals(50, CombatMomentum.killShaveTicks(3, true));
		assertEquals(70, CombatMomentum.killShaveTicks(5, true));
		assertEquals(150, CombatMomentum.killShaveTicks(20, true));
	}
}
