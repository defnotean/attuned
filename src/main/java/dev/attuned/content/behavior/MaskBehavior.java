package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.combat.CombatFeedback;
import dev.attuned.api.focus.FocusBehavior;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Mask Focus: low-light crouching briefly resists reveal and Glowing effects. */
public final class MaskBehavior implements FocusBehavior {
	private static final int CHARGE_TICKS = 40;
	private static final int RESIST_TICKS = 100;
	private static final int ABILITY_COOLDOWN_TICKS = 400;
	private static final int MAX_LIGHT = 7;

	private static final Map<UUID, State> STATES = new HashMap<>();

	public MaskBehavior() {
		AttunedPlayerCleanup.onForget(STATES::remove);
		AttunedServerCleanup.onStop(STATES::clear);
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		State state = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
		if (state.resistTicks > 0) {
			player.removeEffect(MobEffects.GLOWING);
			state.resistTicks--;
			return;
		}
		if (!canCharge(player)) {
			state.chargeTicks = 0;
			return;
		}
		state.chargeTicks++;
		if (state.chargeTicks >= CHARGE_TICKS) {
			state.chargeTicks = 0;
			state.resistTicks = RESIST_TICKS;
			player.removeEffect(MobEffects.GLOWING);
		}
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		STATES.remove(player.getUUID());
	}

	@Override
	public boolean hasActiveAbility() {
		return true;
	}

	@Override
	public int abilityCooldownTicks() {
		return ABILITY_COOLDOWN_TICKS;
	}

	@Override
	public boolean onAbility(ServerPlayer player, ItemStack focus) {
		State state = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
		state.chargeTicks = 0;
		state.resistTicks = RESIST_TICKS;
		player.removeEffect(MobEffects.GLOWING);
		CombatFeedback.abilityCast(player, CombatFeedback.AbilityFlavor.STEALTH);
		return true;
	}

	public static boolean resistsReveal(LivingEntity target) {
		if (!(target instanceof ServerPlayer player)) {
			return false;
		}
		if (VeilBehavior.isVeiled(player)) {
			return false;
		}
		State state = STATES.get(player.getUUID());
		return state != null && state.resistTicks > 0;
	}

	private static boolean canCharge(ServerPlayer player) {
		return player.isCrouching()
			&& player.level().getMaxLocalRawBrightness(player.blockPosition()) <= MAX_LIGHT;
	}

	private static final class State {
		private int chargeTicks;
		private int resistTicks;
	}
}
