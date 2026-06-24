package net.fabricmc.fabric.api.itemgroup.v1;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;

public final class FabricItemGroup {
	private FabricItemGroup() {}

	public static CreativeModeTab.Builder builder() {
		return CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0);
	}

	public static CreativeModeTab.Builder builder(ResourceLocation id) {
		return CreativeModeTab.builder(CreativeModeTab.Row.TOP, columnFor(id));
	}

	private static int columnFor(ResourceLocation id) {
		return switch (id.getPath()) {
			case "attuned" -> 0;
			case "attuned_zephyr_holy" -> 1;
			case "attuned_tide_forge" -> 2;
			case "attuned_verdant_umbral" -> 3;
			case "attuned_utility" -> 4;
			default -> 0;
		};
	}
}
