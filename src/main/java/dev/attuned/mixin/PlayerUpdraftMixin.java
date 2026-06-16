package dev.attuned.mixin;

import dev.attuned.content.behavior.UpdraftBehavior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies Updraft Focus thrust after the player movement step resolves.
 */
@Mixin(Player.class)
public abstract class PlayerUpdraftMixin {

	@Inject(method = "aiStep", at = @At("TAIL"))
	private void attuned$updraftAfterAiStep(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (self.getLevel().isClientSide() || !(self instanceof ServerPlayer player)) {
			return;
		}
		UpdraftBehavior.tickFlight(player);
	}
}
