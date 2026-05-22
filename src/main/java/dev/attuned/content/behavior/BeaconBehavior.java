package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusBehavior;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;

/**
 * Beacon Focus: a held compass is bound to the wearer's last bed, pointing the
 * way home instead of toward world spawn.
 *
 * <p>While active, any compass in hand has its lodestone tracker pointed at the
 * respawn position (untracked, so it never clears itself); the binding is dropped
 * when the Focus deactivates so the compass returns to ordinary behaviour.
 */
public final class BeaconBehavior implements FocusBehavior {

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		if (respawn == null) {
			return;
		}
		GlobalPos home = respawn.respawnData().globalPos();
		LodestoneTracker tracker = new LodestoneTracker(Optional.of(home), false);
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.is(Items.COMPASS)
					&& !tracker.equals(held.get(DataComponents.LODESTONE_TRACKER))) {
				held.set(DataComponents.LODESTONE_TRACKER, tracker);
			}
		}
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.is(Items.COMPASS)) {
				held.remove(DataComponents.LODESTONE_TRACKER);
			}
		}
	}
}
