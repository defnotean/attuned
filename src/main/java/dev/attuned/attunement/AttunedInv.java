package dev.attuned.attunement;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of a player's six Focus slots. Stored as a data attachment,
 * which treats values as immutable — every edit produces a new instance via
 * {@link #with}, which is what triggers persistence and client sync.
 */
public record AttunedInv(List<ItemStack> items) {
	public static final int SIZE = 6;

	public AttunedInv {
		items = sizedItems(items);
	}

	public static final Codec<AttunedInv> CODEC =
		ItemStack.OPTIONAL_CODEC.listOf().xmap(AttunedInv::sized, AttunedInv::items);

	public static final StreamCodec<RegistryFriendlyByteBuf, AttunedInv> STREAM_CODEC =
		ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(AttunedInv::sized, AttunedInv::items);

	public static AttunedInv empty() {
		return sized(List.of());
	}

	public List<ItemStack> items() {
		return copyItems(items);
	}

	private static AttunedInv sized(List<ItemStack> source) {
		return new AttunedInv(source);
	}

	private static List<ItemStack> sizedItems(List<ItemStack> source) {
		List<ItemStack> list = new ArrayList<>(SIZE);
		int sourceSize = source == null ? 0 : source.size();
		for (int i = 0; i < SIZE; i++) {
			ItemStack stack = i < sourceSize ? source.get(i) : ItemStack.EMPTY;
			list.add(copyStack(stack));
		}
		return List.copyOf(list);
	}

	private static List<ItemStack> copyItems(List<ItemStack> source) {
		List<ItemStack> copy = new ArrayList<>(source.size());
		for (ItemStack stack : source) {
			copy.add(copyStack(stack));
		}
		return List.copyOf(copy);
	}

	private static ItemStack copyStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return stack.copy();
	}

	public ItemStack get(int slot) {
		return items.get(slot);
	}

	public AttunedInv with(int slot, ItemStack stack) {
		List<ItemStack> copy = new ArrayList<>(items);
		copy.set(slot, copyStack(stack));
		return new AttunedInv(copy);
	}
}
