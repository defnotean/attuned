package dev.attuned.content;

import dev.attuned.AttunedServerCleanup;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side, single-threaded animator for the Attunement Altar's bind effect.
 * When a shard is bound the capacity raise applies instantly, but the
 * <em>feedback</em> is drawn out into a short streamed-particle convergence
 * from the Altar to the binder and a finale dust burst with a chime.
 *
 * <p>A pending animation is a small record (altar position, player UUID,
 * affinity colour, start tick) tracked in {@link #ACTIVE}. The end-of-tick
 * server event walks the list, emits one short particle pulse per tick along
 * the line from the Altar's top to the player's chest, and on expiry plays
 * the finale. Animations are dropped silently if the player logs out; if the
 * player merely crossed dimensions mid-flight, a short rescue chime is played
 * at their new position so the bind still gets audible closure.
 *
 * <p>The list is only ever touched from the server tick thread, so no
 * synchronisation is needed.
 */
public final class AltarAnimations {
	private AltarAnimations() {}

	/** Total length of a bind animation, in server ticks. */
	private static final int DURATION_TICKS = 40;
	/** Particles emitted along the convergence trail each tick. */
	private static final int PARTICLES_PER_TICK = 2;

	/** Cached, bind-time-only ritual decor context. */
	public record RitualFlair(int litCandles, int amethyst) {
		public static final RitualFlair NONE = new RitualFlair(0, 0);

		public RitualFlair {
			litCandles = Math.max(0, Math.min(8, litCandles));
			amethyst = Math.max(0, Math.min(8, amethyst));
		}

		private boolean hasLitCandles() {
			return litCandles > 0;
		}

		private boolean hasFullCandleRing() {
			return litCandles >= 4;
		}

		private boolean hasAmethyst() {
			return amethyst > 0;
		}
	}

	/** A pending bind animation. */
	private record Pending(ServerLevel level, BlockPos altarPos, UUID playerUuid, int rgb,
			RitualFlair flair, int startTick) {}

	private static final List<Pending> ACTIVE = new ArrayList<>();
	private static int serverTick;

	/** Registers the per-tick driver. Call once from {@code onInitialize}. */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(AltarAnimations::tick);
		AttunedServerCleanup.onStop(() -> {
			ACTIVE.clear();
			serverTick = 0;
		});
	}

	/**
	 * Begins a bind animation from {@code altarPos} to {@code player}, drawn in
	 * the player's stance colour. The capacity raise itself is independent and
	 * should already have been applied by the caller.
	 */
	public static void begin(ServerLevel level, BlockPos altarPos, ServerPlayer player, int rgb) {
		begin(level, altarPos, player, rgb, RitualFlair.NONE);
	}

	/**
	 * Begins a bind animation with cached bind-time decor context. The caller is
	 * responsible for scanning the world once; the tick driver only reuses this
	 * small immutable summary.
	 */
	public static void begin(ServerLevel level, BlockPos altarPos, ServerPlayer player, int rgb, RitualFlair flair) {
		if (flair == null) {
			flair = RitualFlair.NONE;
		}
		ACTIVE.add(new Pending(level, altarPos, player.getUUID(), rgb, flair, serverTick));
		emitOpeningFlourish(level, altarPos, flair);
	}

	private static void tick(MinecraftServer server) {
		serverTick++;
		Iterator<Pending> it = ACTIVE.iterator();
		while (it.hasNext()) {
			Pending p = it.next();
			int age = serverTick - p.startTick();
			if (age >= DURATION_TICKS) {
				it.remove();
				emitFinale(p);
				continue;
			}
			ServerPlayer player = (ServerPlayer) p.level().getPlayerByUUID(p.playerUuid());
			if (player == null) {
				it.remove();
				continue;
			}
			if (!player.level().dimension().equals(p.level().dimension())) {
				// Player crossed dimensions; play a short chime at their new location so they get closure.
				player.level().playSound(null, player.blockPosition(),
					SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.4F, 1.6F);
				it.remove();
				continue;
			}
			emitStream(p, player, age);
			emitStreamFlourish(p, age);
		}
	}

	private static void emitStream(Pending p, ServerPlayer player, int age) {
		Vec3 altarTop = new Vec3(p.altarPos().getX() + 0.5, p.altarPos().getY() + 1.1, p.altarPos().getZ() + 0.5);
		Vec3 chest = player.position().add(0, player.getBbHeight() * 0.6, 0);
		double progress = (age + 0.5) / DURATION_TICKS;
		Vec3 emitAt = altarTop.lerp(chest, Math.min(1.0, progress));
		DustParticleOptions dust = new DustParticleOptions(p.rgb(), 0.9F);
		p.level().sendParticles(dust, emitAt.x, emitAt.y, emitAt.z, PARTICLES_PER_TICK, 0.1, 0.1, 0.1, 0.0);
	}

	private static void emitOpeningFlourish(ServerLevel level, BlockPos altarPos, RitualFlair flair) {
		if (flair.hasLitCandles()) {
			int points = flair.hasFullCandleRing() ? 12 : Math.min(8, 4 + flair.litCandles());
			emitCandleRing(level, altarPos, points, 0.72, 1.03);
			level.playSound(null, altarPos, SoundEvents.CANDLE_AMBIENT, SoundSource.BLOCKS, 0.15F, 1.2F);
		}
		if (flair.hasAmethyst()) {
			level.sendParticles(ParticleTypes.END_ROD,
				altarPos.getX() + 0.5, altarPos.getY() + 1.15, altarPos.getZ() + 0.5,
				Math.min(6, 2 + flair.amethyst()), 0.25, 0.2, 0.25, 0.015);
			level.playSound(null, altarPos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.25F, 1.35F);
		}
	}

	private static void emitStreamFlourish(Pending p, int age) {
		if (p.flair().hasFullCandleRing() && age > 0 && age % 10 == 0) {
			emitCandleRing(p.level(), p.altarPos(), 8, 0.65, 1.04);
		}
	}

	private static void emitCandleRing(ServerLevel level, BlockPos altarPos, int points, double radius, double yOffset) {
		double centerX = altarPos.getX() + 0.5;
		double y = altarPos.getY() + yOffset;
		double centerZ = altarPos.getZ() + 0.5;
		for (int i = 0; i < points; i++) {
			double angle = Math.PI * 2.0 * i / points;
			double x = centerX + Math.cos(angle) * radius;
			double z = centerZ + Math.sin(angle) * radius;
			level.sendParticles(ParticleTypes.SMALL_FLAME, x, y, z, 1, 0.01, 0.02, 0.01, 0.0);
		}
	}

	private static void emitFinale(Pending p) {
		ServerPlayer player = (ServerPlayer) p.level().getPlayerByUUID(p.playerUuid());
		if (player == null) {
			return;
		}
		DustParticleOptions dust = new DustParticleOptions(p.rgb(), 1.2F);
		p.level().sendParticles(dust,
			player.getX(), player.getY() + player.getBbHeight() / 2, player.getZ(),
			24, 0.4, 0.6, 0.4, 0.02);
		p.level().playSound(null, player.blockPosition(),
			SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.4F);
	}
}
