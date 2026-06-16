package dev.attuned.attunement;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
		ItemStack.CODEC.listOf().xmap(AttunedInv::sized, AttunedInv::items);

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
		ItemStack copy = stack.copy();
		copy.setCount(Math.min(copy.getCount(), 1));
		return copy;
	}

	public ItemStack get(int slot) {
		return copyStack(items.get(requireSlot(slot)));
	}

	public AttunedInv with(int slot, ItemStack stack) {
		List<ItemStack> copy = new ArrayList<>(items);
		copy.set(requireSlot(slot), copyStack(stack));
		return new AttunedInv(copy);
	}

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (int i = 0; i < SIZE; i++) {
			ItemStack stack = items.get(i);
			if (stack.isEmpty()) {
				continue;
			}
			CompoundTag entry = stack.save(new CompoundTag());
			entry.putInt("Slot", i);
			list.add(entry);
		}
		tag.put("Items", list);
		return tag;
	}

	public static AttunedInv fromTag(CompoundTag tag) {
		List<ItemStack> decoded = new ArrayList<>(SIZE);
		for (int i = 0; i < SIZE; i++) {
			decoded.add(ItemStack.EMPTY);
		}
		if (tag != null) {
			ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag entry = list.getCompound(i);
				int slot = entry.getInt("Slot");
				if (slot >= 0 && slot < SIZE) {
					decoded.set(slot, ItemStack.of(entry));
				}
			}
		}
		return new AttunedInv(decoded);
	}

	private static int requireSlot(int slot) {
		if (slot < 0 || slot >= SIZE) {
			throw new IllegalArgumentException("slot must be between 0 and " + (SIZE - 1));
		}
		return slot;
	}
}
