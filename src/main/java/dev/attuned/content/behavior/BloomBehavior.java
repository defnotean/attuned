package dev.attuned.content.behavior;

import dev.attuned.Attuned;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import java.util.Random;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Bloom Focus: rare seeds, flowers, and bee-adjacent finds from plant gathering. */
public final class BloomBehavior implements FocusBehavior {
	private static final ResourceLocation FOCUS_ID =
		new ResourceLocation(Attuned.MOD_ID, "bloom_focus");
	private static boolean initialized;

	public BloomBehavior() {
		initLifecycle();
	}

	private static void initLifecycle() {
		if (initialized) {
			return;
		}
		initialized = true;
		PlayerBlockBreakEvents.AFTER.register(BloomBehavior::afterBlockBreak);
	}

	private static void afterBlockBreak(Level level, Player player, BlockPos pos,
			BlockState state, net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)
				|| !hasActiveBloom(serverPlayer)) {
			return;
		}
		ItemStack reward = rewardFor(state, server.getRandom());
		if (!reward.isEmpty()) {
			serverPlayer.getInventory().placeItemBackInInventory(reward);
		}
	}

	private static ItemStack rewardFor(BlockState state, Random random) {
		if (state.is(BlockTags.FLOWERS) && random.nextInt(18) == 0) {
			return flowerReward(random);
		}
		if (state.is(BlockTags.LEAVES) && random.nextInt(36) == 0) {
			return new ItemStack(Items.HONEYCOMB);
		}
		if (isGrassLike(state) && random.nextInt(12) == 0) {
			return new ItemStack(Items.WHEAT_SEEDS);
		}
		return ItemStack.EMPTY;
	}

	private static ItemStack flowerReward(Random random) {
		return switch (random.nextInt(4)) {
			case 0 -> new ItemStack(Items.DANDELION);
			case 1 -> new ItemStack(Items.POPPY);
			case 2 -> new ItemStack(Items.BLUE_ORCHID);
			default -> new ItemStack(Items.OXEYE_DAISY);
		};
	}

	private static boolean isGrassLike(BlockState state) {
		return state.is(Blocks.GRASS)
			|| state.is(Blocks.TALL_GRASS)
			|| state.is(Blocks.FERN)
			|| state.is(Blocks.LARGE_FERN);
	}

	private static boolean hasActiveBloom(ServerPlayer player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(inv.get(slot).getItem());
			if (FOCUS_ID.equals(id)) {
				return true;
			}
		}
		return false;
	}
}
