package dev.attuned.mixin;

import dev.attuned.combat.AttunedCombat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures attack charge before vanilla resets it during Player.attack. */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {
	// HEAD, not an INVOKE on resetAttackStrengthTicker: in 26.1.2 Player.attack never
	// calls the reset itself (it happens outside the method), so an INVOKE anchor finds
	// zero targets and hard-fails injection. At method entry the ticker is still
	// un-reset, so the captured charge equals what vanilla reads during the attack.
	@Inject(method = "attack", at = @At("HEAD"))
	private void attuned$captureMeleeCharge(Entity target, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		// Player.attack also runs client-side for swing prediction; the snapshot map is
		// server state, so only the server thread may write it.
		if (self.getLevel().isClientSide()) {
			return;
		}
		AttunedCombat.rememberMeleeCharge(self, target, self.getAttackStrengthScale(0.5F));
	}
}
