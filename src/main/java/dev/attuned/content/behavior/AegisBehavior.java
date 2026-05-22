package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/**
 * Aegis Focus: periodically tops the wearer up with an absorption shield.
 *
 * <p>Roughly every {@link #RECHARGE_TICKS} ticks, if the wearer has no Absorption
 * effect, a fresh short one is granted — so a destroyed shield comes back after a
 * cooldown rather than being permanently refreshed. The per-player tick counter is
 * cleared on deactivation so a re-equipped Focus starts its cooldown afresh.
 */
public final class AegisBehavior implements FocusBehavior {

	/** Ticks between absorption top-ups (~4 seconds). */
	private static final int RECHARGE_TICKS = 80;

	/** Duration of each granted Absorption effect, in ticks. */
	private static final int ABSORPTION_DURATION = 200;

	/** Per-player count of active ticks since the last absorption top-up. */
	private final Map<UUID, Integer> ticksSinceGrant = new HashMap<>();

	public AegisBehavior() {
		AttunedPlayerCleanup.onForget(ticksSinceGrant::remove);
	}

	@Override
	public void onActivate(ServerPlayer player, ItemStack focus) {
		// Grant immediately on equip, then begin the recharge cycle.
		ticksSinceGrant.put(player.getUUID(), RECHARGE_TICKS);
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		UUID id = player.getUUID();
		int ticks = ticksSinceGrant.getOrDefault(id, 0) + 1;
		if (ticks >= RECHARGE_TICKS) {
			ticks = 0;
			if (!player.hasEffect(MobEffects.ABSORPTION)) {
				player.addEffect(new MobEffectInstance(
					MobEffects.ABSORPTION, ABSORPTION_DURATION, 0, true, true, true));
			}
		}
		ticksSinceGrant.put(id, ticks);
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		ticksSinceGrant.remove(player.getUUID());
	}
}
