package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Softstep Focus: while the wearer is actually sneaking, they become silent.
 *
 * <p>The effect deliberately does not apply while sprinting or standing upright,
 * so it supports stealthy approach play without making every movement free.
 */
public final class SoftstepBehavior implements FocusBehavior {
	private final Map<UUID, Boolean> originalSilent = new HashMap<>();

	public SoftstepBehavior() {
		AttunedPlayerCleanup.onForgetPlayer(this::restore);
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		UUID id = player.getUUID();
		if (player.isCrouching() && !player.isSprinting()) {
			originalSilent.computeIfAbsent(id, ignored -> player.isSilent());
			player.setSilent(true);
		} else {
			restore(player);
		}
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		restore(player);
	}

	private void restore(ServerPlayer player) {
		Boolean wasSilent = originalSilent.remove(player.getUUID());
		if (wasSilent != null) {
			player.setSilent(wasSilent);
		}
	}
}
