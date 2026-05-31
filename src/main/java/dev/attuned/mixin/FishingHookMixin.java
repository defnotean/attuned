package dev.attuned.mixin;

import dev.attuned.content.behavior.SeafarersFishing;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
	@Inject(method = "retrieve", at = @At("RETURN"), cancellable = true)
	private void attuned$seafarersRetrieve(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
		FishingHook hook = (FishingHook) (Object) this;
		Player owner = hook.getPlayerOwner();
		if (owner instanceof ServerPlayer serverPlayer) {
			cir.setReturnValue(SeafarersFishing.afterRetrieve(serverPlayer, rod, cir.getReturnValue()));
		}
	}
}
