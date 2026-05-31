package dev.attuned.mixin;

import dev.attuned.content.behavior.SeafarersFishing;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Adds active Seafarers Foci to the Luck of the Sea value used by new bobbers. */
@Mixin(FishingRodItem.class)
public abstract class FishingRodItemMixin {

	@ModifyArgs(
		method = "use",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/FishingHook;<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V"
		)
	)
	private void attuned$boostFishingLuck(Args args) {
		Player player = args.get(0);
		int luck = args.get(2);
		args.set(2, SeafarersFishing.boostedFishingLuck(player, luck));
	}
}
