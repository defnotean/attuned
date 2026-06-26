package net.fabricmc.fabric.api.creativetab.v1;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class FabricCreativeModeTab {
	private FabricCreativeModeTab() {}

	public static Builder builder() {
		return new Builder(CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0));
	}

	public static final class Builder {
		private final CreativeModeTab.Builder delegate;

		private Builder(CreativeModeTab.Builder delegate) {
			this.delegate = delegate;
		}

		public Builder title(Component title) {
			delegate.title(title);
			return this;
		}

		public Builder icon(java.util.function.Supplier<ItemStack> icon) {
			delegate.icon(icon);
			return this;
		}

		public Builder displayItems(CreativeModeTab.DisplayItemsGenerator generator) {
			delegate.displayItems(generator);
			return this;
		}

		public CreativeModeTab build() {
			return delegate.build();
		}
	}
}
