package net.fabricmc.fabric.api.itemgroup.v1;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;

public final class FabricItemGroup {
	private FabricItemGroup() {}

	public static CreativeModeTab.Builder builder() {
		return CreativeModeTab.builder();
	}

	public static CreativeModeTab.Builder builder(ResourceLocation id) {
		return CreativeModeTab.builder();
	}
}
