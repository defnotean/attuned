package dev.attuned.content.behavior;

import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.content.AttunedContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Catch-time hooks for the peaceful Seafarers fishing Foci. */
public final class SeafarersFishing {
	private static final float LINECAST_EXTRA_FISH_CHANCE = 0.20F;
	private static final float NETMENDER_PREVENT_DAMAGE_CHANCE = 0.35F;

	private SeafarersFishing() {}

	public static int afterRetrieve(ServerPlayer player, ItemStack rod, int damage) {
		if (damage != 1) {
			return damage;
		}
		if (hasActive(player, AttunedContent.LINECAST_FOCUS)
				&& player.getRandom().nextFloat() < LINECAST_EXTRA_FISH_CHANCE) {
			ItemEntity extra = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(),
				new ItemStack(Items.COD));
			player.level().addFreshEntity(extra);
		}
		if (hasActive(player, AttunedContent.NETMENDER_FOCUS)
				&& player.getRandom().nextFloat() < NETMENDER_PREVENT_DAMAGE_CHANCE) {
			return Math.max(0, damage - 1);
		}
		return damage;
	}

	private static boolean hasActive(ServerPlayer player, Item focus) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			if (inv.get(slot).is(focus)) {
				return true;
			}
		}
		return false;
	}
}
