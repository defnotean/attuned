package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.content.AttunedContent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Catch-time hooks for the peaceful Seafarers fishing Foci. */
public final class SeafarersFishing {
	private static final float LINECAST_EXTRA_FISH_CHANCE = 0.20F;
	private static final float NETMENDER_REPAIR_CHANCE = 0.35F;
	private static final int NETMENDER_REPAIR_COOLDOWN_TICKS = 100;
	private static final int LINECAST_LUCK_OF_THE_SEA_BONUS = 2;
	private static final int NETMENDER_LUCK_OF_THE_SEA_BONUS = 1;
	private static final int HARBORLIGHT_LUCK_OF_THE_SEA_BONUS = 1;
	private static final int DRIFTGLASS_LUCK_OF_THE_SEA_BONUS = 1;
	private static final int MAX_LUCK_OF_THE_SEA_BONUS = 3;

	private static final Map<UUID, Long> netmenderCooldowns = new HashMap<>();

	static {
		AttunedPlayerCleanup.onForget(netmenderCooldowns::remove);
		AttunedServerCleanup.onStop(netmenderCooldowns::clear);
	}

	private SeafarersFishing() {}

	public static int boostedFishingLuck(Player player, int baseLuck) {
		return baseLuck + fishingLuckBonus(player);
	}

	public static int fishingLuckBonus(Player player) {
		int bonus = 0;
		if (hasActive(player, AttunedContent.LINECAST_FOCUS)) {
			bonus += LINECAST_LUCK_OF_THE_SEA_BONUS;
		}
		if (hasActive(player, AttunedContent.NETMENDER_FOCUS)) {
			bonus += NETMENDER_LUCK_OF_THE_SEA_BONUS;
		}
		if (hasActive(player, AttunedContent.HARBORLIGHT_FOCUS)) {
			bonus += HARBORLIGHT_LUCK_OF_THE_SEA_BONUS;
		}
		if (hasActive(player, AttunedContent.DRIFTGLASS_FOCUS)) {
			bonus += DRIFTGLASS_LUCK_OF_THE_SEA_BONUS;
		}
		return Math.min(MAX_LUCK_OF_THE_SEA_BONUS, bonus);
	}

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
				&& canRepairWithNetmender(player, rod)
				&& player.getRandom().nextFloat() < NETMENDER_REPAIR_CHANCE) {
			rod.setDamageValue(rod.getDamageValue() - 1);
			netmenderCooldowns.put(player.getUUID(), player.level().getGameTime() + NETMENDER_REPAIR_COOLDOWN_TICKS);
			return 0;
		}
		return damage;
	}

	private static boolean canRepairWithNetmender(ServerPlayer player, ItemStack rod) {
		Long readyAt = netmenderCooldowns.get(player.getUUID());
		return rod.isDamageableItem()
			&& rod.getDamageValue() > 0
			&& (readyAt == null || player.level().getGameTime() >= readyAt);
	}

	private static boolean hasActive(Player player, Item focus) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			if (inv.get(slot).is(focus)) {
				return true;
			}
		}
		return false;
	}
}
