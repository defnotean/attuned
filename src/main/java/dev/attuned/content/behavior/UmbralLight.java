package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusCondition;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared darkness predicates for the Umbral Eclipse Foci. Reuses the same raw light read
 * and threshold logic the data {@link FocusCondition.LowLight} variant uses, so the code
 * behaviors and the datapack palette agree on what "in the dark" means.
 */
final class UmbralLight {
	private UmbralLight() {}

	/** The light level the wearer currently stands in, by the same measure the data conditions use. */
	static int lightAt(ServerPlayer player) {
		return player.level().getMaxLocalRawBrightness(player.blockPosition());
	}

	/** Whether the wearer stands at or below {@code maxLight}. */
	static boolean inLowLight(ServerPlayer player, int maxLight) {
		return FocusCondition.lowLightHolds(lightAt(player), maxLight);
	}
}
