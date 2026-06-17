package dev.attuned.mixin;

import dev.attuned.attunement.AttunedAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerAttunedStateMixin {
	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void attuned$saveState(CompoundTag tag, CallbackInfo ci) {
		AttunedAttachments.save((Player) (Object) this, tag);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void attuned$loadState(CompoundTag tag, CallbackInfo ci) {
		AttunedAttachments.load((Player) (Object) this, tag);
	}
}
