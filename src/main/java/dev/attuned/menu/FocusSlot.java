package dev.attuned.menu;

import dev.attuned.Attuned;
import dev.attuned.attunement.Attunement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A single Focus accessory slot in the player inventory menu. Behaves like a
 * normal slot except it is capped at one item, matching the {@link FocusContainer}
 * it is backed by.
 */
public class FocusSlot extends Slot {

	/**
	 * When true, every Focus slot is suppressed: the survival inventory's recipe
	 * book is open across the side panel, so the slots must neither render nor
	 * accept clicks. Only client screen code writes this while a screen is shown;
	 * a dedicated server never touches it, and {@code AbstractContainerMenu} never
	 * reads {@link #isActive()}, so menu logic is unaffected on either side.
	 */
	private static boolean suppressed = false;

	/** Hides ({@code true}) or restores ({@code false}) every Focus slot. */
	public static void setSuppressed(boolean value) {
		suppressed = value;
	}

	/** The container backing this slot, typed for the {@link #mayPlace} check. */
	private final FocusContainer focusContainer;

	public FocusSlot(FocusContainer container, int index, int x, int y) {
		super(container, index, x, y);
		this.focusContainer = container;
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		// Only a registered Focus may occupy a Focus slot.
		return Attunement.definitionFor(focusContainer.player(), stack).isPresent()
			|| isFocusItem(stack);
	}

	private static boolean isFocusItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id != null
			&& id.getNamespace().equals(Attuned.MOD_ID)
			&& id.getPath().endsWith("_focus");
	}

	@Override
	public boolean isActive() {
		// An inactive slot is neither drawn nor hovered — exactly how the panel
		// steps aside while the recipe book is open over it.
		return !suppressed;
	}
}
