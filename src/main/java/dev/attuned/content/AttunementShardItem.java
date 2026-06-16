package dev.attuned.content;

import dev.attuned.compat.PlayerMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Attunement Shard — the consumable that raises attunement capacity. A shard
 * is not spent from the hand; it is bound at an {@link AttunementAltarBlock},
 * which consumes it and raises the binder's capacity. Using one in the hand only
 * points the player toward an Altar.
 */
public class AttunementShardItem extends Item {

	public AttunementShardItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide()) {
			PlayerMessages.system(player, new net.minecraft.network.chat.TextComponent(
					"Bind this at an Attunement Altar to raise your capacity.")
				.withStyle(ChatFormatting.GRAY));
		}
		return InteractionResultHolder.pass(player.getItemInHand(hand));
	}
}
