package net.fabricmc.fabric.api.itemgroup.v1;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

public final class FabricItemGroup {
	private FabricItemGroup() {}

	public static FabricCreativeModeTab.Builder builder() {
		return FabricCreativeModeTab.builder();
	}
}
