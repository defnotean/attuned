package dev.attuned.combat;

import dev.attuned.AttunedConfig;
import dev.attuned.AttunedServerCleanup;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Resonant surges — periodic thunderstorm events. While a dimension is
 * thundering, a surge site may activate near a random online player there: for
 * the configured duration, players within the surge radius gain resonance at
 * {@link ResonantSurgeResolver#resonanceGainMultiplier()} the normal rate. The
 * surge is deliberately loud — particles and ambient sound every second — so the
 * reward of fast resonance comes with the risk of being a beacon in the dark.
 *
 * <p>Server-only and single-threaded: the one active surge and its arithmetic
 * live entirely on the server tick thread. Start eligibility is delegated to the
 * pure {@link ResonantSurgeResolver}; this module supplies the live world state,
 * picks the site, grants resonance through {@link Resonance#grantSurge}, and
 * emits the feedback. Per the 1.4 plan the mob-lure AI is intentionally skipped:
 * the risk-reward is carried by the noise, not pathfinding.</p>
 */
public final class ResonantSurges {
	private ResonantSurges() {}

	/** Resonance granted per second to a player inside a surge, before the surge multiplier. */
	private static final float BASE_RESONANCE_PER_SECOND = 0.05F;
	/** A surge site sits this far horizontally from its anchor player, at minimum. */
	private static final int MIN_OFFSET = 24;
	/** A surge site sits at most this far horizontally from its anchor player. */
	private static final int MAX_OFFSET = 48;

	/** The single active surge, or {@code null} when none is live. Server-thread only. */
	private static Surge active;
	/** Game-time the previous surge ended, gating the next start. */
	private static long lastSurgeEnd;
	private static int tickCounter;
	private static boolean initialized;

	/** One live surge: where it is, which dimension, and when it ends. */
	private record Surge(ResourceKey<Level> dimension, BlockPos pos, long endTick) {}

	/** Registers the throttled surge driver and its cleanup hook once. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		AttunedServerCleanup.onStop(() -> {
			active = null;
			lastSurgeEnd = 0L;
			tickCounter = 0;
		});
		ServerTickEvents.END_SERVER_TICK.register(ResonantSurges::tick);
	}

	private static void tick(MinecraftServer server) {
		if (tickCounter++ % 20 != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return;
		}
		long now = overworld.getGameTime();
		if (active != null) {
			sustain(server, now);
			return;
		}
		maybeStart(server, now);
	}

	/** Tries to spawn a fresh surge near a random online player in a thundering dimension. */
	private static void maybeStart(MinecraftServer server, long now) {
		AttunedConfig config = AttunedConfig.get();
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		List<ServerPlayer> candidates = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (player.level().isThundering()) {
				candidates.add(player);
			}
		}
		if (!ResonantSurgeResolver.shouldStart(
				now, lastSurgeEnd, config.surgeIntervalTicks(), !candidates.isEmpty(), players.size())) {
			return;
		}
		ServerPlayer anchor = candidates.get(anchor(candidates).nextInt(candidates.size()));
		ServerLevel level = anchor.level();
		RandomSource random = anchor.getRandom();
		int offsetX = signedOffset(random);
		int offsetZ = signedOffset(random);
		int x = (int) Math.floor(anchor.getX()) + offsetX;
		int z = (int) Math.floor(anchor.getZ()) + offsetZ;
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
		BlockPos site = new BlockPos(x, y, z);
		active = new Surge(level.dimension(), site, now + config.surgeDurationTicks());
	}

	/** Grants resonance and emits feedback while a surge is live; ends it on expiry. */
	private static void sustain(MinecraftServer server, long now) {
		Surge surge = active;
		ServerLevel level = server.getLevel(surge.dimension());
		if (level == null) {
			active = null;
			lastSurgeEnd = now;
			return;
		}
		int radius = AttunedConfig.get().surgeRadius();
		float gain = BASE_RESONANCE_PER_SECOND * ResonantSurgeResolver.resonanceGainMultiplier();
		boolean expired = now >= surge.endTick();
		BlockPos pos = surge.pos();
		for (ServerPlayer player : level.players()) {
			double dx = player.getX() - (pos.getX() + 0.5);
			double dz = player.getZ() - (pos.getZ() + 0.5);
			if (!ResonantSurgeResolver.isInside(dx, dz, radius)) {
				continue;
			}
			if (expired) {
				player.sendOverlayMessage(Component.translatable("surge.attuned.faded"));
			} else {
				Resonance.grantSurge(player, gain);
			}
		}
		if (expired) {
			active = null;
			lastSurgeEnd = now;
			return;
		}
		emitFeedback(level, pos);
	}

	/** A loud, visible pulse at the surge site: a spark column plus an ambient boom. */
	private static void emitFeedback(ServerLevel level, BlockPos pos) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.0;
		double z = pos.getZ() + 0.5;
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 40, 0.6, 3.0, 0.6, 0.5);
		level.sendParticles(ParticleTypes.END_ROD, x, y + 2.0, z, 18, 0.3, 3.0, 0.3, 0.02);
		level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 1.4F, 1.6F);
	}

	private static int signedOffset(RandomSource random) {
		int magnitude = MIN_OFFSET + random.nextInt(MAX_OFFSET - MIN_OFFSET + 1);
		return random.nextBoolean() ? magnitude : -magnitude;
	}

	private static RandomSource anchor(List<ServerPlayer> candidates) {
		return candidates.get(0).getRandom();
	}
}
