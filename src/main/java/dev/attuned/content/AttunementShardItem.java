package dev.attuned.content;

import dev.attuned.attunement.AttunedAttachments;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A consumable item that permanently raises the holder's attunement capacity.
 * Each shard used grants +2 capacity, up to the cap of {@value #CAPACITY_CAP}.
 */
public class AttunementShardItem extends Item {
	/** Hard ceiling on attunement capacity reachable via shards. */
	public static final int CAPACITY_CAP = 20;
	/** Capacity granted per shard consumed. */
	public static final int CAPACITY_PER_SHARD = 2;

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

		int capacity = AttunedAttachments.getCapacity(player);
		if (capacity >= CAPACITY_CAP) {
			// Already at the cap: do nothing and don't consume the shard.
			return InteractionResult.FAIL;
		}

		int raised = Math.min(CAPACITY_CAP, capacity + CAPACITY_PER_SHARD);
		AttunedAttachments.setCapacity(player, raised);
		stack.shrink(1);
		return InteractionResult.SUCCESS_SERVER;
	}
}
