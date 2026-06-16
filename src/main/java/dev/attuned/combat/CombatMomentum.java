package dev.attuned.combat;

/**
 * Pure combat-momentum policy: kill streaks at Apex shorten ability cooldowns so
 * aggressive play chains into faster tactical and identity ability access.
 */
public final class CombatMomentum {
	private CombatMomentum() {}

	/** Kill streak length before momentum shaves cooldowns. */
	public static final int STREAK_GATE = 3;

	/**
	 * Cooldown applied when an ability fires — high streaks at Apex trim up to one
	 * third off the base, but never below half the base duration.
	 */
	public static int effectiveCooldown(int baseTicks, int killStreak, boolean atApex) {
		if (!atApex || killStreak < STREAK_GATE || baseTicks <= 0) {
			return baseTicks;
		}
		int reduction = Math.min(baseTicks / 3, (killStreak - (STREAK_GATE - 1)) * 20);
		return Math.max(baseTicks / 2, baseTicks - reduction);
	}

	/** Ticks to shave from an in-flight cooldown when a streak kill lands at Apex. */
	public static int killShaveTicks(int killStreak, boolean atApex) {
		if (!atApex || killStreak < STREAK_GATE) {
			return 0;
		}
		return Math.min(150, 20 + killStreak * 10);
	}
}
