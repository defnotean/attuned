package dev.attuned.combat;

import dev.attuned.compat.ParticleCompat;

import dev.attuned.compat.PlayerMessages;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.AffinityColors;
import dev.attuned.pacts.Pact;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Short server-side combat feedback — particles and sounds that make resonance,
 * pact tacticals, surges, and capstone procs readable in the moment.
 */
public final class CombatFeedback {
	private CombatFeedback() {}
	/** Minimum resonance delta before a gain cue fires. */
	private static final float GAIN_THRESHOLD = 0.025F;
	/** Minimum resonance delta before a drain cue fires. */
	private static final float DRAIN_THRESHOLD = 0.04F;
	private static final int COOLDOWN_TICKS = 6;

	private static final Map<UUID, Long> LAST_RESONANCE_FEEDBACK = new HashMap<>();

	static {
		dev.attuned.AttunedServerCleanup.onStop(LAST_RESONANCE_FEEDBACK::clear);
		dev.attuned.AttunedPlayerCleanup.onForget(LAST_RESONANCE_FEEDBACK::remove);
	}

	public static void resonanceGain(ServerPlayer player, float delta, float after) {
		if (delta < GAIN_THRESHOLD || !cooldownReady(player)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = player.getX();
		double y = player.getY() + player.getBbHeight() * 0.55;
		double z = player.getZ();
		int count = after >= Resonance.APEX_THRESHOLD ? 6 : 3;
		level.sendParticles(ParticleTypes.END_ROD, x, y, z, count, 0.25, 0.35, 0.25, 0.02);
		float pitch = 0.85F + Math.min(0.35F, delta * 2.0F);
		level.playSound(null, player.blockPosition(),
			SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.35F, pitch);
		markFeedback(player);
	}

	public static void resonanceDrain(ServerPlayer player) {
		if (!cooldownReady(player)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = player.getX();
		double y = player.getY() + player.getBbHeight() * 0.55;
		double z = player.getZ();
		level.sendParticles(ParticleTypes.SMOKE, x, y, z, 4, 0.2, 0.2, 0.2, 0.01);
		level.playSound(null, player.blockPosition(),
			SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 0.45F, 0.65F);
		markFeedback(player);
	}

	public static void resonanceArmed(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = player.getX();
		double y = player.getY() + player.getBbHeight() * 0.65;
		double z = player.getZ();
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 8, 0.35, 0.45, 0.35, 0.08);
		level.playSound(null, player.blockPosition(),
			SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.55F, 1.35F);
	}

	public static void resonanceKillStreak(ServerPlayer player, int streak) {
		if (streak < 3) {
			return;
		}
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = player.getX();
		double y = player.getY() + player.getBbHeight() * 0.7;
		double z = player.getZ();
		int burst = Math.min(12, 4 + streak);
		level.sendParticles(ParticleTypes.CRIT, x, y, z, burst, 0.4, 0.35, 0.4, 0.12);
		float pitch = 1.0F + Math.min(0.4F, (streak - 2) * 0.08F);
		level.playSound(null, player.blockPosition(),
			SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.25F, pitch);
		PlayerMessages.overlay(player, net.minecraft.network.chat.Component.translatable(
			"resonance.attuned.kill_streak", streak));
	}

	public static void surgeCharge(ServerPlayer player) {
		if (!cooldownReady(player)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = player.getX();
		double y = player.getY() + player.getBbHeight() * 0.5;
		double z = player.getZ();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 3, 0.2, 0.35, 0.2, 0.04);
		if (Resonance.atApex(player)) {
			level.playSound(null, player.blockPosition(),
				SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.2F, 1.6F);
		}
		markFeedback(player);
	}

	public static void pactTactical(ServerPlayer player, Pact pact, boolean overcharge) {
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = player.getX();
		double y = player.getY() + player.getBbHeight() * 0.55;
		double z = player.getZ();
		int color = pact.argb() & 0x00FFFFFF;
		int particleScale = overcharge ? 2 : 1;
		switch (pact) {
			case PYRESWORN -> {
				level.sendParticles(ParticleTypes.FLAME, x, y, z, 16 * particleScale, 0.45, 0.35, 0.45, 0.04);
				level.playSound(null, player.blockPosition(),
					SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, overcharge ? 0.9F : 0.7F, overcharge ? 0.95F : 1.1F);
			}
			case STONEHEART -> {
				level.sendParticles(ParticleTypes.CRIT, x, y, z, 10, 0.35, 0.25, 0.35, 0.05);
				level.playSound(null, player.blockPosition(),
					SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.4F);
			}
			case WINDRUNNER -> {
				level.sendParticles(ParticleTypes.CLOUD, x, y, z, 8, 0.35, 0.15, 0.35, 0.02);
				level.playSound(null, player.blockPosition(),
					SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.45F, 1.5F);
			}
			case RADIANT_COVENANT -> {
				level.sendParticles(ParticleTypes.GLOW, x, y + 0.2, z, 12, 0.5, 0.4, 0.5, 0.02);
				level.playSound(null, player.blockPosition(),
					SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.55F, 1.45F);
			}
			case TIDESWORN -> {
				level.sendParticles(ParticleTypes.SPLASH, x, y, z, 14, 0.45, 0.25, 0.45, 0.08);
				level.playSound(null, player.blockPosition(),
					SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.PLAYERS, 0.5F, 1.2F);
			}
			case FORGEBOUND -> {
				level.sendParticles(ParticleTypes.LAVA, x, y, z, 6, 0.35, 0.25, 0.35, 0.01);
				level.sendParticles(ParticleTypes.SMOKE, x, y + 0.3, z, 8, 0.3, 0.2, 0.3, 0.02);
				level.playSound(null, player.blockPosition(),
					SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 0.85F);
			}
			case WILDROOT -> {
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 10, 0.4, 0.35, 0.4, 0.02);
				level.playSound(null, player.blockPosition(),
					SoundEvents.GRASS_STEP, SoundSource.PLAYERS, 0.55F, 1.25F);
			}
			case NIGHTSWORN -> {
				level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 10, 0.35, 0.3, 0.35, 0.02);
				level.playSound(null, player.blockPosition(),
					SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.35F, 1.6F);
			}
			case UNTETHERED -> {
				level.sendParticles(ParticleTypes.CLOUD, x, y, z, 8, 0.45, 0.25, 0.45, 0.05);
				level.playSound(null, player.blockPosition(),
					SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.55F, 1.15F);
			}
		}
		level.sendParticles(ParticleCompat.dust(color, overcharge ? 1.1F : 0.85F),
			x, y, z, 4 * particleScale, 0.25, 0.25, 0.25, 0.0);
	}

	public static void pactTactical(ServerPlayer player, Pact pact) {
		pactTactical(player, pact, false);
	}

	public static void apexProc(ServerPlayer player, Apex.Capstone capstone) {
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = player.getX();
		double y = player.getY() + player.getBbHeight() * 0.6;
		double z = player.getZ();
		level.sendParticles(ParticleCompat.dust(capstoneColor(capstone), 1.0F),
			x, y, z, 8, 0.35, 0.35, 0.35, 0.0);
		level.playSound(null, player.blockPosition(),
			SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.55F, procPitch(capstone));
	}

	/** Riptide slowness drag landed on a foe. */
	public static void riptideDrag(ServerPlayer player, LivingEntity victim) {
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = victim.getX();
		double y = victim.getY() + victim.getBbHeight() * 0.4;
		double z = victim.getZ();
		int color = Affinity.TIDE.argb() & 0x00FFFFFF;
		level.sendParticles(ParticleTypes.SPLASH, x, y, z, 14, 0.4, 0.25, 0.4, 0.06);
		level.sendParticles(ParticleTypes.BUBBLE, x, y + 0.2, z, 8, 0.3, 0.2, 0.3, 0.03);
		level.sendParticles(ParticleCompat.dust(color, 0.9F), x, y, z, 4, 0.2, 0.2, 0.2, 0.0);
		level.playSound(null, victim.blockPosition(),
			SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.PLAYERS, 0.45F, 1.35F);
	}

	/** Crucible ignite landed on a foe. */
	public static void crucibleIgnite(ServerPlayer player, LivingEntity victim) {
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = victim.getX();
		double y = victim.getY() + victim.getBbHeight() * 0.5;
		double z = victim.getZ();
		int color = Affinity.FORGE.argb() & 0x00FFFFFF;
		level.sendParticles(ParticleTypes.FLAME, x, y, z, 12, 0.35, 0.3, 0.35, 0.03);
		level.sendParticles(ParticleTypes.LAVA, x, y, z, 4, 0.25, 0.2, 0.25, 0.01);
		level.sendParticles(ParticleCompat.dust(color, 1.0F), x, y, z, 6, 0.25, 0.25, 0.25, 0.0);
		level.playSound(null, victim.blockPosition(),
			SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.55F, 0.9F);
	}

	/** Bloomward lifesteal heal returned to the attacker. */
	public static void bloomwardHeal(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = player.getX();
		double y = player.getY() + player.getBbHeight() * 0.55;
		double z = player.getZ();
		int color = Affinity.VERDANT.argb() & 0x00FFFFFF;
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 8, 0.35, 0.35, 0.35, 0.02);
		level.sendParticles(ParticleCompat.dust(color, 0.85F), x, y, z, 5, 0.25, 0.25, 0.25, 0.0);
		level.playSound(null, player.blockPosition(),
			SoundEvents.GRASS_STEP, SoundSource.PLAYERS, 0.5F, 1.3F);
	}

	/** Gloaming weakness landed on a foe. */
	public static void gloamingWeakness(ServerPlayer player, LivingEntity victim) {
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = victim.getX();
		double y = victim.getY() + victim.getBbHeight() * 0.5;
		double z = victim.getZ();
		int color = Affinity.UMBRAL.argb() & 0x00FFFFFF;
		level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 10, 0.35, 0.3, 0.35, 0.02);
		level.sendParticles(ParticleCompat.dust(color, 0.95F), x, y, z, 6, 0.25, 0.25, 0.25, 0.0);
		level.playSound(null, victim.blockPosition(),
			SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.35F, 1.2F);
	}

	/** Judgment bonus strike on a wounded Fury-aligned foe. */
	public static void judgmentStrike(ServerPlayer player, LivingEntity victim) {
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = victim.getX();
		double y = victim.getY() + victim.getBbHeight() * 0.55;
		double z = victim.getZ();
		int color = Affinity.HOLY.argb() & 0x00FFFFFF;
		level.sendParticles(ParticleTypes.GLOW, x, y, z, 10, 0.35, 0.35, 0.35, 0.02);
		level.sendParticles(ParticleTypes.CRIT, x, y, z, 8, 0.3, 0.3, 0.3, 0.1);
		level.sendParticles(ParticleCompat.dust(color, 1.1F), x, y, z, 8, 0.3, 0.3, 0.3, 0.0);
		level.playSound(null, victim.blockPosition(),
			SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.5F);
	}

	/** Maelstrom damage bonus hit — light discord spark. */
	public static void maelstromHit(ServerPlayer player, LivingEntity victim) {
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = victim.getX();
		double y = victim.getY() + victim.getBbHeight() * 0.5;
		double z = victim.getZ();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 4, 0.25, 0.25, 0.25, 0.06);
		level.sendParticles(ParticleCompat.dust(AffinityColors.DISCORD_RGB, 0.75F),
			x, y, z, 3, 0.2, 0.2, 0.2, 0.0);
		level.playSound(null, victim.blockPosition(),
			SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.25F, 1.6F);
	}

	/** A guaranteed Execute finisher — heavier than a generic capstone proc. */
	public static void executeFinisher(ServerPlayer player, LivingEntity victim) {
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = victim.getX();
		double y = victim.getY() + victim.getBbHeight() * 0.55;
		double z = victim.getZ();
		int color = Affinity.FURY.argb() & 0x00FFFFFF;
		level.sendParticles(ParticleCompat.dust(color, 1.4F), x, y, z, 24, 0.55, 0.45, 0.55, 0.0);
		level.sendParticles(ParticleTypes.CRIT, x, y, z, 20, 0.5, 0.4, 0.5, 0.18);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 10, 0.35, 0.35, 0.35, 0.04);
		level.playSound(null, victim.blockPosition(),
			SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.85F, 0.75F);
		level.playSound(null, victim.blockPosition(),
			SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.6F);
	}

	/** Unyielding capped a single heavy blow. */
	public static void unyieldingCap(ServerPlayer player) {
		if (!cooldownReady(player)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = player.getX();
		double y = player.getY() + player.getBbHeight() * 0.55;
		double z = player.getZ();
		int color = Affinity.BASTION.argb() & 0x00FFFFFF;
		level.sendParticles(ParticleCompat.dust(color, 1.0F), x, y, z, 10, 0.35, 0.35, 0.35, 0.0);
		level.sendParticles(ParticleTypes.CRIT, x, y, z, 6, 0.25, 0.25, 0.25, 0.08);
		level.playSound(null, player.blockPosition(),
			SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7F, 0.95F);
		markFeedback(player);
	}

	/** Stillpoint absorption pulse landed. */
	public static void stillpointPulse(ServerPlayer player) {
		if (!cooldownReady(player)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = player.getX();
		double y = player.getY() + player.getBbHeight() * 0.6;
		double z = player.getZ();
		level.sendParticles(ParticleTypes.END_ROD, x, y, z, 6, 0.3, 0.35, 0.3, 0.02);
		level.playSound(null, player.blockPosition(),
			SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.45F, 1.1F);
		markFeedback(player);
	}

	public static void abilityCast(ServerPlayer player, AbilityFlavor flavor) {
		ServerLevel level = (ServerLevel) player.getLevel();
		double x = player.getX();
		double y = player.getY() + player.getBbHeight() * 0.55;
		double z = player.getZ();
		switch (flavor) {
			case FORGE -> {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 10, 0.3, 0.35, 0.3, 0.06);
				level.playSound(null, player.blockPosition(),
					SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.45F, 1.25F);
			}
			case TIDE -> {
				level.sendParticles(ParticleTypes.BUBBLE, x, y, z, 10, 0.35, 0.3, 0.35, 0.04);
				level.playSound(null, player.blockPosition(),
					SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS, 0.5F, 0.9F);
			}
			case STEALTH -> {
				level.sendParticles(ParticleTypes.SMOKE, x, y, z, 8, 0.25, 0.4, 0.25, 0.02);
				level.playSound(null, player.blockPosition(),
					SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.45F, 0.75F);
			}
			case HOLY -> {
				level.sendParticles(ParticleTypes.GLOW, x, y + 0.15, z, 10, 0.35, 0.35, 0.35, 0.02);
				level.playSound(null, player.blockPosition(),
					SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.35F);
			}
		}
	}

	public enum AbilityFlavor { FORGE, TIDE, STEALTH, HOLY }

	private static int capstoneColor(Apex.Capstone capstone) {
		return switch (capstone) {
			case RIPTIDE -> Affinity.TIDE.argb() & 0x00FFFFFF;
			case CRUCIBLE -> Affinity.FORGE.argb() & 0x00FFFFFF;
			case BLOOMWARD -> Affinity.VERDANT.argb() & 0x00FFFFFF;
			case GLOAMING -> Affinity.UMBRAL.argb() & 0x00FFFFFF;
			case MAELSTROM -> AffinityColors.DISCORD_RGB;
			case EXECUTE -> Affinity.FURY.argb() & 0x00FFFFFF;
			case JUDGMENT -> Affinity.HOLY.argb() & 0x00FFFFFF;
			case UNYIELDING, UNTOUCHABLE, STILLPOINT -> 0xFFFFFF;
		};
	}

	private static float procPitch(Apex.Capstone capstone) {
		return switch (capstone) {
			case RIPTIDE -> 0.85F;
			case CRUCIBLE -> 1.15F;
			case BLOOMWARD -> 1.05F;
			case GLOAMING -> 0.75F;
			case MAELSTROM -> 1.25F;
			case EXECUTE, UNYIELDING, UNTOUCHABLE, JUDGMENT, STILLPOINT -> 1.0F;
		};
	}

	private static boolean cooldownReady(ServerPlayer player) {
		long now = player.getLevel().getGameTime();
		Long last = LAST_RESONANCE_FEEDBACK.get(player.getUUID());
		return last == null || now - last >= COOLDOWN_TICKS;
	}

	private static void markFeedback(ServerPlayer player) {
		LAST_RESONANCE_FEEDBACK.put(player.getUUID(), player.getLevel().getGameTime());
	}

	static float gainThreshold() {
		return GAIN_THRESHOLD;
	}

	static float drainThreshold() {
		return DRAIN_THRESHOLD;
	}
}
