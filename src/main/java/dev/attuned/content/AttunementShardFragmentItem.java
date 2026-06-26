package dev.attuned.content;

import dev.attuned.compat.PlayerMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A smaller progression reward that teaches itself: right-clicking a fragment
 * tells the player how close they are to crafting a full Attunement Shard.
 */
public class AttunementShardFragmentItem extends Item {
	public static final int FRAGMENTS_PER_SHARD = 4;

	public AttunementShardFragmentItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide()) {
			sendProgressHint(player);
		}
		return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
	}

	public static void sendProgressHint(Player player) {
		int count = countFragments(player);
		Component message = count >= FRAGMENTS_PER_SHARD
			? Component.translatable("item.attuned.attunement_shard_fragment.use.ready", count)
			: Component.translatable("item.attuned.attunement_shard_fragment.use.progress",
				count, FRAGMENTS_PER_SHARD);
		PlayerMessages.system(player, message.copy().withStyle(ChatFormatting.GRAY));
	}

	private static int countFragments(Player player) {
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT.get())) {
				count += stack.getCount();
			}
		}
		return count;
	}
}
