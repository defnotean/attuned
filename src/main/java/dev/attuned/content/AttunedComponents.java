package dev.attuned.content;

import dev.attuned.attunement.FocusHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Branch-local item state helpers for Minecraft versions before data components. */
public final class AttunedComponents {
	private static final String ROOT_KEY = "Attuned";
	private static final String SATCHEL_KEY = "satchel_contents";
	private static final String GRAND_SATCHEL_KEY = "grand_satchel_contents";
	private static final String TEMPERED_KEY = "tempered";

	private AttunedComponents() {}

	public static final int SATCHEL_SIZE = 27;
	/** Grand Focus Reliquary capacity: a 9x6 Foci grid, twice the small satchel. */
	public static final int GRAND_SATCHEL_SIZE = 54;

	public static FocusHolder emptyContents() {
		return FocusHolder.empty(SATCHEL_SIZE, 1);
	}

	public static FocusHolder emptyGrandContents() {
		return FocusHolder.empty(GRAND_SATCHEL_SIZE, 1);
	}

	public static FocusHolder getContents(ItemStack stack, boolean grand) {
		int size = grand ? GRAND_SATCHEL_SIZE : SATCHEL_SIZE;
		if (stack == null || stack.isEmpty()) {
			return FocusHolder.empty(size, 1);
		}
		CompoundTag root = stack.getTagElement(ROOT_KEY);
		if (root == null) {
			return FocusHolder.empty(size, 1);
		}
		return FocusHolder.fromTag(root.getCompound(contentsKey(grand)), size, 1);
	}

	public static void setContents(ItemStack stack, boolean grand, FocusHolder contents) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		FocusHolder normalized = contents == null
			? FocusHolder.empty(grand ? GRAND_SATCHEL_SIZE : SATCHEL_SIZE, 1)
			: contents;
		stack.getOrCreateTagElement(ROOT_KEY).put(contentsKey(grand), normalized.toTag());
	}

	public static boolean isTempered(ItemStack stack) {
		CompoundTag root = stack == null || stack.isEmpty() ? null : stack.getTagElement(ROOT_KEY);
		return root != null && root.getBoolean(TEMPERED_KEY);
	}

	public static void setTempered(ItemStack stack) {
		if (stack != null && !stack.isEmpty()) {
			stack.getOrCreateTagElement(ROOT_KEY).putBoolean(TEMPERED_KEY, true);
		}
	}

	public static void init() {}

	private static String contentsKey(boolean grand) {
		return grand ? GRAND_SATCHEL_KEY : SATCHEL_KEY;
	}
}
