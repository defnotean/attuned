package dev.attuned.mixin;

import dev.attuned.attunement.AttunedAttachments;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerAttunedStateMixin {
	@Inject(method = "restoreFrom", at = @At("TAIL"))
	private void attuned$copyState(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
		AttunedAttachments.copy(oldPlayer, (ServerPlayer) (Object) this);
	}
}
