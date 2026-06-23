package dev.attuned.combat;

/**
 * Pure, Minecraft-free decision logic for {@link ResonantSurges}.
 *
 * <p>A resonance surge is a periodic thunderstorm event: once enough game-time
 * has elapsed since the last one ended, and the world is both thundering and
 * populated, a surge site activates near a random online player. Players within
 * its radius gain resonance at {@link #resonanceGainMultiplier()} the normal
 * rate. This class owns the gating arithmetic so it can be tested without the
 * server; the runtime module supplies the live world state.</p>
 */
public final class ResonantSurgeResolver {
	private ResonantSurgeResolver() {}

	/** Players inside a surge gain resonance at four times the base rate. */
	private static final float RESONANCE_GAIN_MULTIPLIER = 4.0F;

	/**
	 * Whether a new surge may start this tick.
	 *
	 * @param now            the current game-time, in ticks
	 * @param lastSurgeEnd   the game-time the previous surge ended (0 if none yet)
	 * @param intervalTicks  the minimum gap between surges, in ticks
	 * @param isThundering    whether any dimension is currently thundering
	 * @param onlinePlayers  the number of players available to anchor the surge
	 * @return {@code true} when every condition for a fresh surge is met
	 */
	public static boolean shouldStart(long now, long lastSurgeEnd, long intervalTicks,
			boolean isThundering, int onlinePlayers) {
		if (!isThundering || onlinePlayers <= 0) {
			return false;
		}
		return now - lastSurgeEnd >= intervalTicks;
	}

	/**
	 * Whether a flat horizontal offset from the surge centre falls within its
	 * field. The radius edge counts as inside.
	 *
	 * @param dx     the x distance from the surge centre
	 * @param dz     the z distance from the surge centre
	 * @param radius the surge radius, in blocks
	 */
	public static boolean isInside(double dx, double dz, int radius) {
		return dx * dx + dz * dz <= (double) radius * radius;
	}

	/**
	 * Whether a player inside a live surge has done enough to receive surge credit.
	 * The activity flags are intentionally split so runtime systems can add mining
	 * or contextual hooks without weakening the no-idle default.
	 */
	public static boolean shouldGrantCredit(boolean insideSurge, boolean moved, boolean recentCombat,
			boolean minedRelevantBlock, boolean contextualAction) {
		return insideSurge && (moved || recentCombat || minedRelevantBlock || contextualAction);
	}

	/** The resonance-gain multiplier applied to players inside a live surge. */
	public static float resonanceGainMultiplier() {
		return RESONANCE_GAIN_MULTIPLIER;
	}
}
