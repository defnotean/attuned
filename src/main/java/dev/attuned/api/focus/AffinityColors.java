package dev.attuned.api.focus;

import java.util.Optional;

/**
 * The single source of truth for the affinity colour palette and the
 * Discord-or-affinity selection logic. Every readout that paints affinity
 * colour (the Focus panel gem and budget bar, the combat HUD gem, the
 * Pacts aura, the combat feedback particles) routes through here so a
 * palette retune lands in one place and every surface follows.
 *
 * <p>{@link Affinity#argb()} returns the per-affinity ARGB; this class
 * adds the constants that live outside the enum (the Discord stance
 * magenta and the neutral grey used when no affinity is committed) and a
 * combined {@link #argbOf(Optional, boolean)} helper for callers that need
 * stance colour without re-implementing the if-Discord-else-affinity
 * branch every time.</p>
 */
public final class AffinityColors {
	private AffinityColors() {}

	/** The Discord stance colour, ARGB. A clashing magenta. */
	public static final int DISCORD_ARGB = 0xFFCC44FF;
	/** The same Discord magenta as 24-bit RGB, for {@code DustParticleOptions}. */
	public static final int DISCORD_RGB = 0x00CC44FF;
	/** The unlit grey used when no affinity is committed and the player is not in Discord. */
	public static final int NEUTRAL_ARGB = 0xFF8B8B8B;

	/**
	 * The stance colour for a player: the Discord magenta if {@code discordStance}
	 * is true, otherwise the committed affinity's colour, or the neutral grey
	 * when no affinity is committed.
	 */
	public static int argbOf(Optional<Affinity> affinity, boolean discordStance) {
		if (discordStance) {
			return DISCORD_ARGB;
		}
		return affinity.map(Affinity::argb).orElse(NEUTRAL_ARGB);
	}
}
