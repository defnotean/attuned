package dev.attuned.content.behavior;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Kilnward Focus: hostile hits near heat grant brief Resistance, not fire immunity. */
public final class KilnwardBehavior implements FocusBehavior {
	private static final ResourceLocation FOCUS_ID =
		ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, "kilnward_focus");
	private static final int RESISTANCE_TICKS = 60;
	private static final int COOLDOWN_TICKS = 240;
	private static final int HEAT_RADIUS_XZ = 4;
	private static final int HEAT_RADIUS_Y = 2;
	private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();
	private static boolean initialized;

	public KilnwardBehavior() {
		initLifecycle();
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		COOLDOWNS.computeIfPresent(player.getUUID(),
			(id, remaining) -> remaining > 1 ? remaining - 1 : null);
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {}

	private static void initLifecycle() {
		if (initialized) {
			return;
		}
		initialized = true;
		AttunedPlayerCleanup.onForget(COOLDOWNS::remove);
		AttunedServerCleanup.onStop(COOLDOWNS::clear);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(KilnwardBehavior::afterDamage);
	}

	private static void afterDamage(LivingEntity defender, DamageSource source,
			float originalDamage, float dealtDamage, boolean blocked) {
		if (dealtDamage <= 0.0F || AttunedCombat.isReflecting() || !(defender instanceof ServerPlayer player)
				|| COOLDOWNS.getOrDefault(player.getUUID(), 0) > 0
				|| !hasActiveKilnward(player)
				|| !nearHeat(player)) {
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

	private static boolean nearHeat(ServerPlayer player) {
		BlockPos origin = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(
				origin.offset(-HEAT_RADIUS_XZ, -HEAT_RADIUS_Y, -HEAT_RADIUS_XZ),
				origin.offset(HEAT_RADIUS_XZ, HEAT_RADIUS_Y, HEAT_RADIUS_XZ))) {
			if (isHeat(player.level().getBlockState(pos))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isHeat(BlockState state) {
		return isLitFurnace(state)
			|| state.is(Blocks.MAGMA_BLOCK)
			|| state.is(Blocks.LAVA)
			|| state.getFluidState().is(FluidTags.LAVA);
	}

	private static boolean isLitFurnace(BlockState state) {
		return (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER))
			&& state.getValue(AbstractFurnaceBlock.LIT);
	}

	private static boolean hasActiveKilnward(ServerPlayer player) {
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
