package dev.attuned.mixin;

import dev.attuned.content.behavior.SeafarersFishing;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Adds active Seafarers Foci to the Luck of the Sea value used by new bobbers. */
@Mixin(FishingRodItem.class)
public abstract class FishingRodItemMixin {

	@Redirect(
		method = "use",
		at = @At(
			value = "NEW",
			target = "net/minecraft/world/entity/projectile/FishingHook"
		)
	)
	private FishingHook attuned$boostFishingLuck(Player player, Level level, int luck, int lure) {
		return new FishingHook(player, level, SeafarersFishing.boostedFishingLuck(player, luck), lure);
	}
}
