package dev.attuned.attunement;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Immutable, size-bound storage for Focus stacks.
 *
 * <p>Each codec instance captures the size and per-slot cap to use at decode
 * time. Persisted lists are padded or truncated to that captured size, and
 * stacks are clamped to the captured cap on every load.
 */
public record FocusHolder(int size, int maxPerSlot, List<ItemStack> items) {
	public FocusHolder {
		size = Math.max(0, size);
		maxPerSlot = Math.max(1, maxPerSlot);
		items = sizedItems(size, maxPerSlot, items);
	}

	public static FocusHolder empty(int size, int maxPerSlot) {
		return new FocusHolder(size, maxPerSlot, List.of());
	}

	public static Codec<FocusHolder> codec(int size, int maxPerSlot) {
		return ItemStack.OPTIONAL_CODEC.listOf().xmap(
			items -> new FocusHolder(size, maxPerSlot, items),
			FocusHolder::items);
	}

	public static StreamCodec<RegistryFriendlyByteBuf, FocusHolder> streamCodec(int size, int maxPerSlot) {
		return ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(
			items -> new FocusHolder(size, maxPerSlot, items),
			FocusHolder::items);
	}

	public List<ItemStack> items() {
		return copyItems(items, maxPerSlot);
	}

	public ItemStack get(int slot) {
		return copyStack(items.get(requireSlot(slot, size)), maxPerSlot);
	}

	public FocusHolder with(int slot, ItemStack stack) {
		List<ItemStack> copy = new ArrayList<>(items);
		copy.set(requireSlot(slot, size), copyStack(stack, maxPerSlot));
		return new FocusHolder(size, maxPerSlot, copy);
	}

	private static List<ItemStack> sizedItems(int size, int maxPerSlot, List<ItemStack> source) {
		List<ItemStack> list = new ArrayList<>(size);
		int sourceSize = source == null ? 0 : source.size();
		for (int i = 0; i < size; i++) {
			ItemStack stack = i < sourceSize ? source.get(i) : ItemStack.EMPTY;
			list.add(copyStack(stack, maxPerSlot));
		}
		return List.copyOf(list);
	}

	private static List<ItemStack> copyItems(List<ItemStack> source, int maxPerSlot) {
		List<ItemStack> copy = new ArrayList<>(source.size());
		for (ItemStack stack : source) {
			copy.add(copyStack(stack, maxPerSlot));
		}
		return List.copyOf(copy);
	}

	private static ItemStack copyStack(ItemStack stack, int maxPerSlot) {
		if (stack == null || stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack copy = stack.copy();
		copy.setCount(Math.min(copy.getCount(), Math.max(1, maxPerSlot)));
		return copy;
	}

	private static int requireSlot(int slot, int size) {
		if (slot < 0 || slot >= size) {
			throw new IllegalArgumentException("slot must be between 0 and " + (size - 1));
		}
		return slot;
	}
}
