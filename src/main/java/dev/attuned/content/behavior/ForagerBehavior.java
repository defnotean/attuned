package dev.attuned.content.behavior;

import dev.attuned.Attuned;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;

/**
 * Forager Focus: small bonus food/seed finds from vanilla gathering blocks.
 */
public final class ForagerBehavior implements dev.attuned.api.focus.FocusBehavior {
	private static final Identifier FOCUS_ID =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "forager_focus");

	public ForagerBehavior() {
		PlayerBlockBreakEvents.AFTER.register(ForagerBehavior::afterBlockBreak);
	}

	private static void afterBlockBreak(Level level, Player player, BlockPos pos,
			BlockState state, net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)
				|| !hasActiveForager(serverPlayer)) {
			return;
		}
		ItemStack reward = rewardFor(state, server.getRandom());
		if (!reward.isEmpty()) {
			serverPlayer.getInventory().placeItemBackInInventory(reward);
		}
	}

	private static ItemStack rewardFor(BlockState state, RandomSource random) {
		if (state.is(BlockTags.LEAVES) && random.nextInt(18) == 0) {
			return new ItemStack(Items.APPLE);
		}
		if (isGrassLike(state) && random.nextInt(6) == 0) {
			return new ItemStack(Items.WHEAT_SEEDS);
		}
		if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)
				&& random.nextInt(10) == 0) {
			return cropReward(state);
		}
		return ItemStack.EMPTY;
	}

	private static boolean isGrassLike(BlockState state) {
		return state.is(Blocks.SHORT_GRASS)
			|| state.is(Blocks.TALL_GRASS)
			|| state.is(Blocks.FERN)
			|| state.is(Blocks.LARGE_FERN);
	}

	private static ItemStack cropReward(BlockState state) {
		if (state.is(Blocks.WHEAT)) {
			return new ItemStack(Items.WHEAT);
		}
		if (state.is(Blocks.CARROTS)) {
			return new ItemStack(Items.CARROT);
		}
		if (state.is(Blocks.POTATOES)) {
			return new ItemStack(Items.POTATO);
		}
		if (state.is(Blocks.BEETROOTS)) {
			return new ItemStack(Items.BEETROOT);
		}
		return new ItemStack(Items.WHEAT_SEEDS);
	}

	private static boolean hasActiveForager(ServerPlayer player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			Identifier id = BuiltInRegistries.ITEM.getKey(inv.get(slot).getItem());
			if (FOCUS_ID.equals(id)) {
				return true;
			}
		}
		return false;
	}
}
