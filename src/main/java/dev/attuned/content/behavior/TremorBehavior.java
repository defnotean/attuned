package dev.attuned.content.behavior;

import dev.attuned.Attuned;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tremor Focus: mining stone may hint that ore is somewhere nearby.
 */
public final class TremorBehavior implements dev.attuned.api.focus.FocusBehavior {
	private static final Identifier FOCUS_ID =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "tremor_focus");
	private static final TagKey<net.minecraft.world.level.block.Block> ORES =
		TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));
	private static final int RADIUS = 5;
	private static final long COOLDOWN_TICKS = 80L;
	private static final Map<UUID, Long> LAST_SCAN = new HashMap<>();

	public TremorBehavior() {
		PlayerBlockBreakEvents.AFTER.register(TremorBehavior::afterBlockBreak);
		AttunedPlayerCleanup.onForget(LAST_SCAN::remove);
	}

	private static void afterBlockBreak(Level level, Player player, BlockPos pos,
			BlockState state, net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)
				|| !hasActiveTremor(serverPlayer) || !isStoneLike(state)) {
			return;
		}
		long now = server.getGameTime();
		UUID playerId = serverPlayer.getUUID();
		Long last = LAST_SCAN.get(playerId);
		if (last != null && now - last < COOLDOWN_TICKS) {
			return;
		}
		LAST_SCAN.put(playerId, now);
		if (nearOre(server, pos)) {
			server.sendParticles(ParticleTypes.NOTE,
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
				2, 0.25, 0.25, 0.25, 0.0);
			server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.25F, 0.7F);
		}
	}

	private static boolean isStoneLike(BlockState state) {
		return state.is(Blocks.STONE)
			|| state.is(Blocks.DEEPSLATE)
			|| state.is(Blocks.NETHERRACK)
			|| state.is(Blocks.END_STONE)
			|| state.is(Blocks.TUFF)
			|| state.is(Blocks.GRANITE)
			|| state.is(Blocks.DIORITE)
			|| state.is(Blocks.ANDESITE);
	}

	private static boolean nearOre(ServerLevel level, BlockPos center) {
		for (BlockPos scan : BlockPos.betweenClosed(center.offset(-RADIUS, -RADIUS, -RADIUS),
				center.offset(RADIUS, RADIUS, RADIUS))) {
			if (level.getBlockState(scan).is(ORES)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasActiveTremor(ServerPlayer player) {
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
