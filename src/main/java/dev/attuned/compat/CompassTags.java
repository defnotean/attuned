package dev.attuned.compat;

import java.util.Objects;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** NBT-backed compass state for Minecraft versions before item data components. */
public final class CompassTags {
	private CompassTags() {}

	public static LodestoneTags target(GlobalPos target) {
		Tag dimension = Level.RESOURCE_KEY_CODEC
			.encodeStart(NbtOps.INSTANCE, target.dimension())
			.result()
			.orElse(null);
		if (dimension == null) {
			return LodestoneTags.EMPTY;
		}
		return new LodestoneTags(NbtUtils.writeBlockPos(target.pos()), dimension, Boolean.FALSE);
	}

	public static LodestoneTags lodestone(ItemStack stack) {
		CompoundTag root = stack == null ? null : stack.getTag();
		if (root == null) {
			return LodestoneTags.EMPTY;
		}
		return new LodestoneTags(
			copy(root.get(CompassItem.TAG_LODESTONE_POS)),
			copy(root.get(CompassItem.TAG_LODESTONE_DIMENSION)),
			root.contains(CompassItem.TAG_LODESTONE_TRACKED)
				? Boolean.valueOf(root.getBoolean(CompassItem.TAG_LODESTONE_TRACKED))
				: null);
	}

	public static void setLodestone(ItemStack stack, LodestoneTags lodestone) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		CompoundTag root = stack.getOrCreateTag();
		root.remove(CompassItem.TAG_LODESTONE_POS);
		root.remove(CompassItem.TAG_LODESTONE_DIMENSION);
		root.remove(CompassItem.TAG_LODESTONE_TRACKED);
		if (lodestone == null || lodestone.isEmpty()) {
			return;
		}
		if (lodestone.pos() != null) {
			root.put(CompassItem.TAG_LODESTONE_POS, lodestone.pos().copy());
		}
		if (lodestone.dimension() != null) {
			root.put(CompassItem.TAG_LODESTONE_DIMENSION, lodestone.dimension().copy());
		}
		if (lodestone.tracked() != null) {
			root.putBoolean(CompassItem.TAG_LODESTONE_TRACKED, lodestone.tracked().booleanValue());
		}
	}

	public static Component customName(ItemStack stack) {
		return stack != null && !stack.isEmpty() && stack.hasCustomHoverName()
			? stack.getHoverName()
			: null;
	}

	public static void setCustomName(ItemStack stack, Component name) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		if (name == null) {
			stack.resetHoverName();
		} else {
			stack.setHoverName(name);
		}
	}

	private static Tag copy(Tag tag) {
		return tag == null ? null : tag.copy();
	}

	public record LodestoneTags(Tag pos, Tag dimension, Boolean tracked) {
		public static final LodestoneTags EMPTY = new LodestoneTags(null, null, null);

		public boolean isEmpty() {
			return pos == null && dimension == null && tracked == null;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof LodestoneTags tags)) {
				return false;
			}
			return Objects.equals(pos, tags.pos)
				&& Objects.equals(dimension, tags.dimension)
				&& Objects.equals(tracked, tags.tracked);
		}

		@Override
		public int hashCode() {
			return Objects.hash(pos, dimension, tracked);
		}
	}
}
