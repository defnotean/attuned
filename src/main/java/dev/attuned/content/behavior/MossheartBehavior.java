package dev.attuned.content.behavior;

import dev.attuned.compat.AfterDamageCallback;

import dev.attuned.Attuned;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.combat.AttunedCombat;
import dev.attuned.combat.CombatTargets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Mossheart Focus: reactive Resistance while rooted on moss, grass, or leaves. */
public final class MossheartBehavior implements FocusBehavior {
	private static final ResourceLocation FOCUS_ID =
		new ResourceLocation(Attuned.MOD_ID, "mossheart_focus");
	private static final int RESISTANCE_TICKS = 60;
	private static final int COOLDOWN_TICKS = 240;
	private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();
	private static boolean initialized;

	public MossheartBehavior() {
		initLifecycle();
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		COOLDOWNS.computeIfPresent(player.getUUID(), (id, remaining) -> tickCooldown(remaining));
	}

	/** One cooldown tick: decrements, clearing the entry (null) once it elapses. */
	static Integer tickCooldown(Integer remaining) {
		return remaining != null && remaining > 1 ? remaining - 1 : null;
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		COOLDOWNS.remove(player.getUUID());
	}

	private static void initLifecycle() {
		if (initialized) {
			return;
		}
		initialized = true;
		AttunedPlayerCleanup.onForget(COOLDOWNS::remove);
		AttunedServerCleanup.onStop(COOLDOWNS::clear);
		AfterDamageCallback.EVENT.register(MossheartBehavior::afterDamage);
	}

	private static void afterDamage(LivingEntity defender, DamageSource source,
			float originalDamage, float dealtDamage, boolean blocked) {
		if (dealtDamage <= 0.0F || !(defender instanceof ServerPlayer player)
				|| COOLDOWNS.getOrDefault(player.getUUID(), 0) > 0
				|| !hasActiveMossheart(player)
				|| !onGreenFooting(player)) {
			return;
		}
		LivingEntity attacker = AttunedCombat.attackerOf(source);
		if (attacker == null || attacker == defender
				|| !CombatTargets.isHostileOrPvpOpponent(attacker, player)) {
			return;
		}
		player.addEffect(new MobEffectInstance(
			MobEffects.DAMAGE_RESISTANCE, RESISTANCE_TICKS, 0, true, false, true));
		COOLDOWNS.put(player.getUUID(), COOLDOWN_TICKS);
	}

	private static boolean onGreenFooting(ServerPlayer player) {
		BlockState feet = player.level().getBlockState(player.blockPosition());
		BlockState below = player.level().getBlockState(player.blockPosition().below());
		return isGreenFooting(feet) || isGreenFooting(below);
	}

	private static boolean isGreenFooting(BlockState state) {
		return state.is(BlockTags.LEAVES)
			|| state.is(Blocks.GRASS_BLOCK)
			|| state.is(Blocks.GRASS)
			|| state.is(Blocks.TALL_GRASS)
			|| state.is(Blocks.MOSS_BLOCK)
			|| state.is(Blocks.MOSS_CARPET);
	}

	private static boolean hasActiveMossheart(ServerPlayer player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(inv.get(slot).getItem());
			if (FOCUS_ID.equals(id)) {
				return true;
			}
		}
		return false;
	}
}
