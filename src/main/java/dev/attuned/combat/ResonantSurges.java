package dev.attuned.combat;

import dev.attuned.AttunedConfig;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.content.AttunedContent;
import dev.attuned.attunement.Attunement;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
 * emits the feedback, and lightly lures untargeted monsters toward the site.</p>
 */
public final class ResonantSurges {
	private ResonantSurges() {}

	/** Resonance granted per second to a player inside a surge, before the surge multiplier. */
	private static final float BASE_RESONANCE_PER_SECOND = 0.05F;
	/** Discord stance earns half the normal surge resonance rate. */
	private static final float DISCORD_SURGE_GAIN_FACTOR = 0.5F;
	/** A surge site sits this far horizontally from its anchor player, at minimum. */
	private static final int MIN_OFFSET = 24;
	/** A surge site sits at most this far horizontally from its anchor player. */
	private static final int MAX_OFFSET = 48;
	/** How often untargeted monsters are nudged toward the surge (4 s at 20 TPS). */
	private static final int MOB_LURE_INTERVAL_TICKS = 80;
	/** Horizontal search radius for the lightweight mob lure. */
	private static final double MOB_LURE_RADIUS = 32.0;
	/** Speed I duration applied while a monster is drawn toward the surge. */
	private static final int MOB_LURE_SPEED_TICKS = 40;
	/** Velocity nudge toward the surge when pathing is unavailable or slow. */
	private static final double MOB_LURE_NUDGE = 0.3;
	/** Within this horizontal distance, players see approximate surge coordinates. */
	private static final double SURGE_COORD_HINT_RADIUS = 128.0;
	/** Approximate coordinate grid for the surge start broadcast. */
	private static final int SURGE_COORD_GRID = 8;
	/** Chance a kill inside an active surge drops shard fragments. */
	private static final float SURGE_KILL_REWARD_CHANCE = 0.45F;

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
		broadcastSurgeStart(level, site);
	}

	/** Resonance gain for one surge tick; Discord stance earns half the normal rate. */
	static float surgeResonanceGain(float baseGain, boolean discord) {
		return discord ? baseGain * DISCORD_SURGE_GAIN_FACTOR : baseGain;
	}

	static float surgeResonanceGain(Player player, float baseGain) {
		return surgeResonanceGain(baseGain, Attunement.isDiscord(player));
	}

	/** Whether the player is standing inside the live surge radius. */
	static boolean containsPlayer(ServerPlayer player) {
		Surge surge = active;
		if (surge == null || !player.level().dimension().equals(surge.dimension())) {
			return false;
		}
		BlockPos pos = surge.pos();
		double dx = player.getX() - (pos.getX() + 0.5);
		double dz = player.getZ() - (pos.getZ() + 0.5);
		return ResonantSurgeResolver.isInside(dx, dz, AttunedConfig.get().surgeRadius());
	}

	/**
	 * Bonus loot for fighting at the surge site — a small shard-fragment drip that
	 * rewards players who answer the storm beacon.
	 */
	static void tryKillReward(ServerPlayer player) {
		if (!containsPlayer(player) || player.getRandom().nextFloat() > SURGE_KILL_REWARD_CHANCE) {
			return;
		}
		int count = 1 + player.getRandom().nextInt(2);
		ItemStack stack = new ItemStack(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT, count);
		if (player.getInventory().add(stack)) {
			player.sendOverlayMessage(Component.translatable("surge.attuned.kill_reward", count));
			return;
		}
		ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
		drop.setDefaultPickUpDelay();
		player.level().addFreshEntity(drop);
		player.sendOverlayMessage(Component.translatable("surge.attuned.kill_reward", count));
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
				Resonance.grantSurge(player, surgeResonanceGain(player, gain));
				if (player instanceof ServerPlayer serverPlayer) {
					CombatFeedback.surgeCharge(serverPlayer);
				}
			}
		}
		if (expired) {
			active = null;
			lastSurgeEnd = now;
			return;
		}
		if (tickCounter % MOB_LURE_INTERVAL_TICKS == 0) {
			lureMonsters(level, pos);
		}
		emitFeedback(level, pos);
	}

	/** Alerts every player in the dimension and marks the surge site audibly. */
	private static void broadcastSurgeStart(ServerLevel level, BlockPos site) {
		level.playSound(null, site, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.2F, 1.0F);
		for (ServerPlayer player : level.players()) {
			player.sendOverlayMessage(surgeStartedMessage(site, player));
		}
	}

	private static Component surgeStartedMessage(BlockPos site, ServerPlayer player) {
		double dx = player.getX() - (site.getX() + 0.5);
		double dz = player.getZ() - (site.getZ() + 0.5);
		if (dx * dx + dz * dz <= SURGE_COORD_HINT_RADIUS * SURGE_COORD_HINT_RADIUS) {
			int approxX = Math.floorDiv(site.getX(), SURGE_COORD_GRID) * SURGE_COORD_GRID;
			int approxZ = Math.floorDiv(site.getZ(), SURGE_COORD_GRID) * SURGE_COORD_GRID;
			return Component.translatable("surge.attuned.started.coords", approxX, site.getY(), approxZ);
		}
		return Component.translatable("surge.attuned.started.nearby");
	}

	/** Draws untargeted monsters toward the surge with Speed I and a path/velocity nudge. */
	private static void lureMonsters(ServerLevel level, BlockPos pos) {
		double centerX = pos.getX() + 0.5;
		double centerY = pos.getY();
		double centerZ = pos.getZ() + 0.5;
		AABB area = new AABB(
			centerX - MOB_LURE_RADIUS, centerY - MOB_LURE_RADIUS, centerZ - MOB_LURE_RADIUS,
			centerX + MOB_LURE_RADIUS, centerY + MOB_LURE_RADIUS, centerZ + MOB_LURE_RADIUS);
		List<Monster> monsters = level.getEntitiesOfClass(Monster.class, area,
			monster -> monster.isAlive() && monster.getTarget() == null);
		for (Monster monster : monsters) {
			monster.addEffect(new MobEffectInstance(
				MobEffects.SPEED, MOB_LURE_SPEED_TICKS, 0, true, false, false));
			monster.getNavigation().moveTo(centerX, centerY, centerZ, 1.0);
			Vec3 delta = new Vec3(centerX - monster.getX(), 0.0, centerZ - monster.getZ());
			if (delta.lengthSqr() > 1.0E-4) {
				monster.setDeltaMovement(monster.getDeltaMovement().add(delta.normalize().scale(MOB_LURE_NUDGE)));
			}
		}
	}

	/** A loud, visible pulse at the surge site: a spark column plus an ambient boom. */
	private static void emitFeedback(ServerLevel level, BlockPos pos) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.0;
		double z = pos.getZ() + 0.5;
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 40, 0.6, 3.0, 0.6, 0.5);
		level.sendParticles(ParticleTypes.END_ROD, x, y + 2.0, z, 18, 0.3, 3.0, 0.3, 0.02);
		level.sendParticles(ParticleTypes.END_ROD, x, y + 6.0, z, 12, 0.15, 4.0, 0.15, 0.01);
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
