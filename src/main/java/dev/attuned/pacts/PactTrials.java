package dev.attuned.pacts;

import dev.attuned.compat.ParticleCompat;

import dev.attuned.compat.PlayerMessages;
import dev.attuned.Milestones;
import dev.attuned.AttunedAdvancements;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.combat.CombatTargets;
import dev.attuned.combat.Resonance;
import dev.attuned.onboarding.Onboarding;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Long-term Pact Trial progression: counters accrue only while the matching pact
 * is awake; completion is permanent and unlocks a small Tier 4 bonus while that
 * pact is active again.
 */
public final class PactTrials {
	private PactTrials() {}

	private static final Map<Pact, Integer> GOALS = new EnumMap<>(Pact.class);
	static {
		GOALS.put(Pact.PYRESWORN, 40);
		GOALS.put(Pact.STONEHEART, 400);
		GOALS.put(Pact.WINDRUNNER, 6_400);
		GOALS.put(Pact.RADIANT_COVENANT, 25);
		GOALS.put(Pact.TIDESWORN, 40);
		GOALS.put(Pact.FORGEBOUND, 25);
		GOALS.put(Pact.WILDROOT, 36_000);
		GOALS.put(Pact.NIGHTSWORN, 150);
		GOALS.put(Pact.UNTETHERED, 20);
	}

	private static final int WILDROOT_ACCRUE_PER_TICK = 20;
	private static final double WINDRUNNER_MAX_DELTA_PER_TICK = 1.25;
	private static final double TRIAL_COMBAT_RADIUS = 16.0D;
	private static final int[] TRIAL_PROGRESS_MILESTONES = {25, 50, 75};

	private static final Map<UUID, WindrunnerTrialRun> windrunnerTrialRuns = new HashMap<>();
	private static boolean initialized;

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		ServerTickEvents.END_SERVER_TICK.register(PactTrials::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			PactTier4.removeStoneheartToughness(player);
			PactTier4.reconcileStoneheartToughness(player, Pacts.activeOf(player).orElse(null));
		});
		AttunedServerCleanup.onStopServer(server -> windrunnerTrialRuns.clear());
		AttunedPlayerCleanup.onForgetPlayer(player -> {
			windrunnerTrialRuns.remove(player.getUUID());
			PactTier4.removeStoneheartToughness(player);
		});
	}

	/** Whether trial counters should advance for this pact given the active pact. */
	public static boolean shouldAccrue(Optional<Pact> active, Pact target) {
		return active.filter(p -> p == target).isPresent();
	}

	public static void accrue(ServerPlayer player, Pact pact, int amount) {
		if (amount <= 0 || !shouldAccrue(Pacts.activeOf(player), pact) || isTier4Complete(player, pact)) {
			return;
		}
		String id = pactId(pact);
		PactTrialProgress progress = get(player);
		int current = progress.counters().getOrDefault(id, 0);
		int next = current + amount;
		int goal = goalOf(pact);
		checkTrialMilestones(player, pact, current, next, goal);
		if (next >= goal) {
			player.setAttached(AttunedAttachments.PACT_TRIAL_PROGRESS, progress.withCounter(id, goal));
			onComplete(player, pact);
			return;
		}
		player.setAttached(AttunedAttachments.PACT_TRIAL_PROGRESS, progress.withCounter(id, next));
	}

	public static int progress(Player player, Pact pact) {
		return get(player).counters().getOrDefault(pactId(pact), 0);
	}

	public static boolean isTier4Complete(Player player, Pact pact) {
		return get(player).tier4Completed().contains(pactId(pact));
	}

	public static int goalOf(Pact pact) {
		return GOALS.getOrDefault(pact, 0);
	}

	public static void onComplete(ServerPlayer player, Pact pact) {
		if (isTier4Complete(player, pact)) {
			return;
		}
		String id = pactId(pact);
		PactTrialProgress progress = get(player).withTier4Completed(id);
		player.setAttached(AttunedAttachments.PACT_TRIAL_PROGRESS, progress);
		AttunedAdvancements.award(player, "attunement/pact_" + id + "_trial");
		PlayerMessages.system(player, Component.translatable("pact.attuned." + id + "_trial.title")
			.withStyle(pact.chatColor(), ChatFormatting.BOLD)
			.append(Component.translatable("pact.attuned.trial.complete").withStyle(ChatFormatting.GRAY)));
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.blockPosition(),
			SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.2F);
		level.playSound(null, player.blockPosition(),
			SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.4F);
		double px = player.getX();
		double py = player.getY() + player.getBbHeight() * 0.65;
		double pz = player.getZ();
		level.sendParticles(ParticleTypes.END_ROD, px, py, pz, 14, 0.5, 0.4, 0.5, 0.02);
		level.sendParticles(ParticleCompat.dust(pact.argb() & 0x00FFFFFF, 0.9F),
			px, py, pz, 6, 0.4, 0.3, 0.4, 0.0);
		Onboarding.tryPactTrialCompleteHint(player);
		Milestones.onPactTrialComplete(player);
		PactTier4.reconcileStoneheartToughness(player, Pacts.activeOf(player).orElse(null));
	}

	public static void onPyreswornIgnite(ServerPlayer player) {
		accrue(player, Pact.PYRESWORN, 1);
	}

	public static void onStoneheartAbsorb(ServerPlayer player, float absorbed) {
		if (absorbed > 0.0F && player.isBlocking()) {
			accrue(player, Pact.STONEHEART, Math.max(1, Math.round(absorbed)));
		}
	}

	public static void onRadiantReveal(ServerPlayer player) {
		accrue(player, Pact.RADIANT_COVENANT, 1);
	}

	public static void onTideswornSlow(ServerPlayer player) {
		accrue(player, Pact.TIDESWORN, 1);
	}

	public static void onForgeboundIgnite(ServerPlayer player) {
		if (nearTrialPressure(player)) {
			accrue(player, Pact.FORGEBOUND, 1);
		}
	}

	public static void onNightswornAbsorb(ServerPlayer player, float absorbed) {
		if (absorbed > 0.0F) {
			accrue(player, Pact.NIGHTSWORN, Math.max(1, Math.round(absorbed)));
		}
	}

	public static void onUntetheredKill(ServerPlayer player) {
		if (Resonance.atApex(player) && nearTrialPressure(player)) {
			accrue(player, Pact.UNTETHERED, 1);
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Pact active = Pacts.activeOf(player).orElse(null);
			PactTier4.reconcileStoneheartToughness(player, active);
			if (active == Pact.WILDROOT) {
				int rate = wildrootAccrueRate(player);
				if (rate > 0) {
					accrue(player, Pact.WILDROOT, rate);
				}
			}
			trackWindrunnerTrial(player, active);
		}
	}

	private static void trackWindrunnerTrial(ServerPlayer player, Pact active) {
		UUID id = player.getUUID();
		if (active != Pact.WINDRUNNER || !player.isSprinting()
				|| player.isPassenger() || player.isFallFlying()
				|| (!Resonance.atApex(player) && !nearTrialPressure(player))) {
			windrunnerTrialRuns.remove(id);
			return;
		}
		ResourceKey<Level> dimension = player.level().dimension();
		double x = player.getX();
		double z = player.getZ();
		WindrunnerTrialRun previous = windrunnerTrialRuns.get(id);
		if (previous == null || !previous.dimension().equals(dimension)) {
			windrunnerTrialRuns.put(id, new WindrunnerTrialRun(dimension, x, z, 0.0));
			return;
		}
		double dx = x - previous.x();
		double dz = z - previous.z();
		double step = Math.sqrt(dx * dx + dz * dz);
		if (step > WINDRUNNER_MAX_DELTA_PER_TICK) {
			windrunnerTrialRuns.put(id, new WindrunnerTrialRun(dimension, x, z, 0.0));
			return;
		}
		double distance = previous.distance() + step;
		int wholeBlocks = (int) distance;
		int accrued = wholeBlocks - previous.accruedBlocks();
		if (accrued > 0) {
			accrue(player, Pact.WINDRUNNER, accrued);
		}
		windrunnerTrialRuns.put(id, new WindrunnerTrialRun(dimension, x, z, distance, wholeBlocks));
	}

	private static void checkTrialMilestones(ServerPlayer player, Pact pact, int current, int next, int goal) {
		if (goal <= 0) {
			return;
		}
		String id = pactId(pact);
		for (int percent : TRIAL_PROGRESS_MILESTONES) {
			int threshold = (goal * percent) / 100;
			if (threshold <= 0 || current >= threshold || next < threshold) {
				continue;
			}
			String onboardId = "pact_trial_" + id + "_" + percent;
			if (AttunedAttachments.sawOnboarding(player, onboardId)) {
				continue;
			}
			AttunedAttachments.markOnboarding(player, onboardId);
			((ServerLevel) player.level()).playSound(null, player.blockPosition(),
				SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.35F, 1.25F);
			PlayerMessages.overlay(player,
				Component.translatable("pact.attuned." + id + "_trial.title")
					.withStyle(pact.chatColor())
					.append(Component.translatable("pact.attuned.trial.progress", percent)
						.withStyle(ChatFormatting.GRAY)));
		}
	}

	private static PactTrialProgress get(Player player) {
		return player.getAttachedOrElse(AttunedAttachments.PACT_TRIAL_PROGRESS, PactTrialProgress.EMPTY);
	}

	static String pactId(Pact pact) {
		return pact.name().toLowerCase(Locale.ROOT);
	}

	private record WindrunnerTrialRun(ResourceKey<Level> dimension, double x, double z, double distance,
			int accruedBlocks) {
		WindrunnerTrialRun(ResourceKey<Level> dimension, double x, double z, double distance) {
			this(dimension, x, z, distance, 0);
		}
	}

	private static int wildrootAccrueRate(ServerPlayer player) {
		if (player.hasEffect(MobEffects.REGENERATION)) {
			return WILDROOT_ACCRUE_PER_TICK;
		}
		return nearTrialPressure(player) ? WILDROOT_ACCRUE_PER_TICK / 2 : 0;
	}

	private static boolean nearTrialPressure(ServerPlayer player) {
		AABB area = player.getBoundingBox().inflate(TRIAL_COMBAT_RADIUS);
		for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, area,
				target -> target != player
					&& target.isAlive()
					&& CombatTargets.isHostileOrPvpOpponent(target, player))) {
			return true;
		}
		return false;
	}
}
