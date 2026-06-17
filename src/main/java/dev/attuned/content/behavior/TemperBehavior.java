package dev.attuned.content.behavior;

import dev.attuned.Attuned;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Temper Focus: forge work briefly improves fully charged direct melee hits. */
public final class TemperBehavior implements FocusBehavior {
	private static final ResourceLocation FOCUS_ID =
		ResourceLocation.fromNamespaceAndPath(Attuned.MOD_ID, "temper_focus");
	private static final int WINDOW_TICKS = 200;
	private static final Map<UUID, Integer> TEMPERED = new HashMap<>();
	private static boolean initialized;

	public TemperBehavior() {
		initLifecycle();
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		TEMPERED.computeIfPresent(player.getUUID(),
			(id, remaining) -> remaining > 1 ? remaining - 1 : null);
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		TEMPERED.remove(player.getUUID());
	}

	public static boolean applies(Player player) {
		return isTempered(player) && hasActiveTemper(player);
	}

	private static void initLifecycle() {
		if (initialized) {
			return;
		}
		initialized = true;
		AttunedPlayerCleanup.onForget(TEMPERED::remove);
		AttunedServerCleanup.onStop(TEMPERED::clear);
		UseBlockCallback.EVENT.register(TemperBehavior::useBlock);
	}

	private static InteractionResult useBlock(Player player, Level level,
			InteractionHand hand, BlockHitResult hitResult) {
		if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)
				|| !hasActiveTemper(player)) {
			return InteractionResult.PASS;
		}
		BlockState state = level.getBlockState(hitResult.getBlockPos());
		if (isForgeBlock(state)) {
			TEMPERED.put(serverPlayer.getUUID(), WINDOW_TICKS);
		}
		return InteractionResult.PASS;
	}

	private static boolean isForgeBlock(BlockState state) {
		return state.is(Blocks.FURNACE)
			|| state.is(Blocks.BLAST_FURNACE)
			|| state.is(Blocks.SMITHING_TABLE)
			|| state.is(Blocks.ANVIL)
			|| state.is(Blocks.CHIPPED_ANVIL)
			|| state.is(Blocks.DAMAGED_ANVIL);
	}

	private static boolean isTempered(Player player) {
		return TEMPERED.getOrDefault(player.getUUID(), 0) > 0;
	}

	private static boolean hasActiveTemper(Player player) {
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
