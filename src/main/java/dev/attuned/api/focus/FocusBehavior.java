package dev.attuned.api.focus;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Code-side behaviour for a Focus, layered on top of its declarative modifiers.
 * Implementations are registered by id and referenced from a {@link FocusDefinition}.
 *
 * <p>"Active" means the Focus is equipped <em>and</em> within the player's attunement
 * budget; a Focus pushed over budget is dormant and counts as deactivated.
 */
public interface FocusBehavior {

	/** Called when the Focus becomes active (equipped and within the attunement budget). */
	default void onActivate(ServerPlayer player, ItemStack focus) {}

	/** Called when the Focus stops being active (unequipped, or pushed dormant by the budget). */
	default void onDeactivate(ServerPlayer player, ItemStack focus) {}

	/** Called every server tick while the Focus is active. */
	default void onTick(ServerPlayer player, ItemStack focus) {}
}
