package dev.attuned.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the protected pickup stack for temporary harpoon cleanup on 1.19.x. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
	@Invoker("getPickupItem")
	ItemStack attuned$pickupItem();
}
