package dev.attuned.content.behavior;

import dev.attuned.Attuned;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.network.TremorOreHintPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
 * Tremor Focus: mining stone reveals the nearest ore pulse through a brief outline.
 */
public final class TremorBehavior implements dev.attuned.api.focus.FocusBehavior {
	private static final ResourceLocation FOCUS_ID =
		new ResourceLocation(Attuned.MOD_ID, "tremor_focus");
	private static final TagKey<net.minecraft.world.level.block.Block> ORES =
		TagKey.create(Registries.BLOCK, new ResourceLocation("c", "ores"));
	private static final int RADIUS = 5;
	/** Cap on outlined blocks per reveal, so a giant deposit cannot flood the packet. */
	private static final int MAX_VEIN_BLOCKS = 32;
	/** Veins may extend a little past the scan radius; bound the flood fill anyway. */
	private static final double MAX_VEIN_REACH_SQ = 10.0 * 10.0;
	private static final long COOLDOWN_TICKS = 80L;
	private static final Map<UUID, Long> LAST_SCAN = new HashMap<>();
	private static boolean initialized;

	public TremorBehavior() {
		initLifecycle();
	}

	private static void initLifecycle() {
		if (initialized) {
			return;
		}
		initialized = true;
		PlayerBlockBreakEvents.AFTER.register(TremorBehavior::afterBlockBreak);
		AttunedPlayerCleanup.onForget(LAST_SCAN::remove);
		AttunedServerCleanup.onStop(LAST_SCAN::clear);
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
		Optional<BlockPos> ore = nearestOre(server, pos);
		if (ore.isPresent()) {
			BlockPos orePos = ore.get();
			java.util.List<BlockPos> vein = collectVein(server, orePos);
			ServerPlayNetworking.send(serverPlayer, new TremorOreHintPayload(vein));
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
			|| state.is(Blocks.ANDESITE)
			// Nether depths and geode shells: ancient-debris tunnels run through
			// blackstone and basalt layers, and amethyst pockets sit in calcite.
			|| state.is(Blocks.BLACKSTONE)
			|| state.is(Blocks.BASALT)
			|| state.is(Blocks.SMOOTH_BASALT)
			|| state.is(Blocks.CALCITE);
	}

	/**
	 * Expands the nearest ore into its whole connected vein: a bounded flood fill
	 * over the 26-neighborhood collecting blocks of the same ore family, so the
	 * outline traces the deposit instead of a single block. Stone and deepslate
	 * variants of the same ore count as one family across the transition border.
	 */
	private static java.util.List<BlockPos> collectVein(ServerLevel level, BlockPos seed) {
		String family = oreFamily(level.getBlockState(seed));
		java.util.List<BlockPos> vein = new java.util.ArrayList<>();
		java.util.Set<BlockPos> seen = new java.util.HashSet<>();
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
		queue.add(seed.immutable());
		seen.add(seed.immutable());
		while (!queue.isEmpty() && vein.size() < MAX_VEIN_BLOCKS) {
			BlockPos current = queue.removeFirst();
			vein.add(current);
			for (BlockPos neighbor : BlockPos.betweenClosed(current.offset(-1, -1, -1), current.offset(1, 1, 1))) {
				BlockPos candidate = neighbor.immutable();
				if (seen.contains(candidate) || candidate.distSqr(seed) > MAX_VEIN_REACH_SQ) {
					continue;
				}
				BlockState state = level.getBlockState(candidate);
				if (state.is(ORES) && oreFamily(state).equals(family)) {
					seen.add(candidate);
					queue.addLast(candidate);
				}
			}
		}
		return vein;
	}

	/** Same-ore grouping key: deepslate variants collapse onto their stone ore. */
	private static String oreFamily(BlockState state) {
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return path.startsWith("deepslate_") ? path.substring("deepslate_".length()) : path;
	}

	private static Optional<BlockPos> nearestOre(ServerLevel level, BlockPos center) {
		BlockPos nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (BlockPos scan : BlockPos.betweenClosed(center.offset(-RADIUS, -RADIUS, -RADIUS),
				center.offset(RADIUS, RADIUS, RADIUS))) {
			if (level.getBlockState(scan).is(ORES)) {
				double distance = scan.distSqr(center);
				if (distance < nearestDistance) {
					nearest = scan.immutable();
					nearestDistance = distance;
				}
			}
		}
		return Optional.ofNullable(nearest);
	}

	private static boolean hasActiveTremor(ServerPlayer player) {
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
