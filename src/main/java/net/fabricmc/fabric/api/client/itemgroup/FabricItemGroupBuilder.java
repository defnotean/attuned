package net.fabricmc.fabric.api.client.itemgroup;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class FabricItemGroupBuilder {
	private final ResourceLocation id;
	private Supplier<ItemStack> icon = () -> ItemStack.EMPTY;
	private Consumer<List<ItemStack>> appendItems = stacks -> {};

	private FabricItemGroupBuilder(ResourceLocation id) {
		this.id = id;
	}

	public static FabricItemGroupBuilder create(ResourceLocation id) {
		return new FabricItemGroupBuilder(id);
	}

	public FabricItemGroupBuilder icon(Supplier<ItemStack> icon) {
		this.icon = Objects.requireNonNull(icon, "icon");
		return this;
	}

	public FabricItemGroupBuilder appendItems(Consumer<List<ItemStack>> appendItems) {
		this.appendItems = Objects.requireNonNull(appendItems, "appendItems");
		return this;
	}

	public CreativeModeTab build() {
		String label = id.getNamespace() + "." + id.getPath();
		return new CreativeModeTab(label) {
			@Override
			public ItemStack makeIcon() {
				return icon.get();
			}

			@Override
			public void fillItemList(NonNullList<ItemStack> items) {
				appendItems.accept(items);
			}
		};
	}
}
