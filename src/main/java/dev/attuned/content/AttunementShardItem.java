package dev.attuned.content;

import dev.attuned.AttunedConfig;
import dev.attuned.attunement.AttunedAttachments;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A consumable item that permanently raises the holder's attunement capacity.
 * Each shard raises capacity by {@code capacity_per_shard} up to
 * {@code capacity_cap}, both set in {@code config/attuned.json}.
 */
public class AttunementShardItem extends Item {

	public AttunementShardItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		// Capacity is server-authoritative state; only mutate it on the server.
		// The client still needs a consuming result so the swing/animation plays.
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		int cap = AttunedConfig.get().capacityCap();
		int capacity = AttunedAttachments.getCapacity(player);
		if (capacity >= cap) {
			// Already at the cap: do nothing and don't consume the shard.
			return InteractionResult.FAIL;
		}

		int raised = Math.min(cap, capacity + AttunedConfig.get().capacityPerShard());
		AttunedAttachments.setCapacity(player, raised);
		stack.shrink(1);
		return InteractionResult.SUCCESS_SERVER;
	}
}
