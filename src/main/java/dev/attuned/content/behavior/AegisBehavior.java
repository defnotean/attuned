package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
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
		AttunedServerCleanup.onStop(ticksSinceGrant::clear);
	}

	@Override
	public void onActivate(ServerPlayer player, ItemStack focus) {
		grantAbsorption(player);
		ticksSinceGrant.put(player.getUUID(), 0);
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		UUID id = player.getUUID();
		int prior = ticksSinceGrant.getOrDefault(id, 0);
		if (rechargesAfter(prior)) {
			grantAbsorption(player);
		}
		ticksSinceGrant.put(id, advance(prior));
	}

	/** Whether the tick following {@code priorTicks} active ticks reaches the recharge threshold. */
	static boolean rechargesAfter(int priorTicks) {
		return priorTicks + 1 >= RECHARGE_TICKS;
	}

	/** The stored counter after one active tick: resets to 0 on a recharge, otherwise increments. */
	static int advance(int priorTicks) {
		return rechargesAfter(priorTicks) ? 0 : priorTicks + 1;
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		ticksSinceGrant.remove(player.getUUID());
	}

	private static void grantAbsorption(ServerPlayer player) {
		if (!player.hasEffect(MobEffects.ABSORPTION)) {
			player.addEffect(new MobEffectInstance(
				MobEffects.ABSORPTION, ABSORPTION_DURATION, 0, true, true, true));
		}
	}
}
