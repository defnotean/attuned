package dev.attuned.mixin;

import dev.attuned.AttunedThrownHarpoonEntity;
import dev.attuned.content.behavior.HarpoonBehavior;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Governs the lifecycle and client visibility of a thrown temporary Offshore Harpoon.
 *
 * <p>Unlike an ordinary trident it stays stuck wherever it lands (never discarded on impact) and is
 * never collectable; it only leaves the world once its cooldown-bound lifetime expires. The
 * temporary-harpoon marker lives in the server-only pickup {@code ItemStack}, so a dedicated flag is
 * synced to clients the same way vanilla syncs {@code ID_FOIL}/{@code ID_LOYALTY}, letting the
 * renderer swap in the custom mesh.</p>
 */
@Mixin(ThrownTrident.class)
public abstract class ThrownTridentMixin implements AttunedThrownHarpoonEntity {
	@Unique
	private static final EntityDataAccessor<Boolean> ATTUNED_TEMPORARY_HARPOON =
		SynchedEntityData.defineId(ThrownTrident.class, EntityDataSerializers.BOOLEAN);

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void attuned$defineHarpoonFlag(SynchedEntityData.Builder builder, CallbackInfo ci) {
		builder.define(ATTUNED_TEMPORARY_HARPOON, false);
	}

	@Inject(
		method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
		at = @At("TAIL"))
	private void attuned$markThrownHarpoon(Level level, LivingEntity owner, ItemStack tridentItem, CallbackInfo ci) {
		attuned$setHarpoonFlag(tridentItem);
	}

	@Inject(
		method = "<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V",
		at = @At("TAIL"))
	private void attuned$markSpawnedHarpoon(
			Level level, double x, double y, double z, ItemStack tridentItem, CallbackInfo ci) {
		attuned$setHarpoonFlag(tridentItem);
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void attuned$discardExpiredHarpoon(CallbackInfo ci) {
		ThrownTrident trident = (ThrownTrident) (Object) this;
		if (HarpoonBehavior.shouldDiscardProjectile(attuned$pickupStack(),
				trident.level().getGameTime())) {
			trident.discard();
			ci.cancel();
		}
	}

	@Inject(method = "tryPickup", at = @At("HEAD"), cancellable = true)
	private void attuned$blockTemporaryHarpoonPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
		ItemStack stack = attuned$pickupStack();
		if (!HarpoonBehavior.isTemporaryHarpoon(stack)) {
			return;
		}
		ThrownTrident trident = (ThrownTrident) (Object) this;
		// A temporary harpoon is never collectable; once its lifetime ends it simply despawns
		// instead of returning to anyone's inventory.
		if (HarpoonBehavior.shouldDiscardProjectile(stack, trident.level().getGameTime())) {
			trident.discard();
		}
		cir.setReturnValue(false);
	}

	@Override
	public boolean attuned$isTemporaryHarpoon() {
		return ((Entity) (Object) this).getEntityData().get(ATTUNED_TEMPORARY_HARPOON);
	}

	@Unique
	private void attuned$setHarpoonFlag(ItemStack tridentItem) {
		((Entity) (Object) this).getEntityData().set(
			ATTUNED_TEMPORARY_HARPOON, HarpoonBehavior.isTemporaryHarpoon(tridentItem));
	}

	@Unique
	private ItemStack attuned$pickupStack() {
		return ((AbstractArrow) (Object) this).getPickupItemStackOrigin();
	}
}
