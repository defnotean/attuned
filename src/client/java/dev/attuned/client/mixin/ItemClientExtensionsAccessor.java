package dev.attuned.client.mixin;

import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Installs Forge client render extensions on vanilla items used by Attuned. */
@Mixin(Item.class)
public interface ItemClientExtensionsAccessor {
	@Accessor("renderProperties")
	void attuned$setRenderProperties(Object renderProperties);
}
