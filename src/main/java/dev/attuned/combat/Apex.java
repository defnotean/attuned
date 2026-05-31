package dev.attuned.combat;

import dev.attuned.AttunedAdvancements;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.AffinityColors;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * Apex capstones: late-build passives gated by Focus layout, nearly full
 * attunement capacity, and combat Resonance.
 */
public final class Apex {
	private Apex() {}

	public enum Capstone {
		EXECUTE("Execute", "Your strikes finish off low-health foes.", Affinity.FURY,
			ChatFormatting.RED, Affinity.FURY.argb()),
		UNYIELDING("Unyielding", "No single blow can land hard, and knockback is ignored.", Affinity.BASTION,
			ChatFormatting.GOLD, Affinity.BASTION.argb()),
		UNTOUCHABLE("Untouchable", "A chance to dodge attacks outright while sprinting.", Affinity.ZEPHYR,
			ChatFormatting.AQUA, Affinity.ZEPHYR.argb()),
		JUDGMENT("Judgment", "Judgment marks wounded Fury-aligned foes for a decisive strike.", Affinity.HOLY,
			ChatFormatting.YELLOW, Affinity.HOLY.argb()),
		MAELSTROM("Maelstrom", "Discord Apex adds force to direct hits and scrambles struck foes.", null,
			ChatFormatting.LIGHT_PURPLE, AffinityColors.DISCORD_ARGB),
		STILLPOINT("Stillpoint", "Neutral Apex grants Absorption pulses and denies affinity pressure.", null,
			ChatFormatting.GRAY, AffinityColors.NEUTRAL_ARGB);

		private final String displayName;
		private final String description;
		private final Affinity affinity;
		private final ChatFormatting chatColor;
		private final int argb;

		Capstone(String displayName, String description, Affinity affinity, ChatFormatting chatColor, int argb) {
			this.displayName = displayName;
			this.description = description;
			this.affinity = affinity;
			this.chatColor = chatColor;
			this.argb = argb;
		}

		public String displayName() {
			return displayName;
		}

		public String description() {
			return description;
		}

		public Optional<Affinity> affinity() {
			return Optional.ofNullable(affinity);
		}

		public ChatFormatting chatColor() {
			return chatColor;
		}

		public int argb() {
			return argb;
		}

		public static Capstone ofAffinity(Affinity affinity) {
			return switch (affinity) {
				case FURY -> EXECUTE;
				case BASTION -> UNYIELDING;
				case ZEPHYR -> UNTOUCHABLE;
				case HOLY -> JUDGMENT;
			};
		}
	}

	private static final int MIN_FOCI = 4;
	private static final int BUDGET_SLACK = 1;

	private static final float EXECUTE_DAMAGE = 100000.0F;
	private static final float EXECUTE_NORMAL = 0.20F;
	private static final float EXECUTE_EMPOWERED = 0.35F;
	private static final float CAP_NORMAL = 0.15F;
	private static final float CAP_EMPOWERED = 0.10F;
	private static final float DODGE_NORMAL = 0.40F;
	private static final float DODGE_EMPOWERED = 0.65F;
	private static final float JUDGMENT_THRESHOLD = 0.30F;
	private static final float JUDGMENT_DAMAGE_BONUS = 0.40F;
	private static final float MAELSTROM_DAMAGE_BONUS = 0.10F;
	private static final int MAELSTROM_SCRAMBLE_TICKS = 60;
	private static final int STILLPOINT_ABSORPTION_TICKS = 60;
	private static final int STILLPOINT_ABSORPTION_COOLDOWN_TICKS = 160;

	private static final Map<UUID, Capstone> apexState = new HashMap<>();
	private static final Map<UUID, Boolean> armedState = new HashMap<>();
	private static final Map<ScrambleKey, Long> maelstromScrambles = new HashMap<>();
	private static final Map<UUID, Long> stillpointPulses = new HashMap<>();

	private enum Matchup { EMPOWERED, NORMAL, NEUTRALIZED }

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Apex::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Apex::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) ->
			maelstromScrambles.keySet().removeIf(key -> key.targetId().equals(entity.getUUID())));
		ServerTickEvents.END_SERVER_TICK.register(Apex::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			apexState.clear();
			armedState.clear();
			maelstromScrambles.clear();
			stillpointPulses.clear();
		});
		AttunedPlayerCleanup.onForget(uuid -> {
			apexState.remove(uuid);
			armedState.remove(uuid);
			stillpointPulses.remove(uuid);
			maelstromScrambles.keySet().removeIf(key -> key.playerId().equals(uuid));
		});
	}

	public static Optional<Capstone> resolveCapstone(List<Optional<Affinity>> activeAffinities,
			int used, int capacity) {
		if (activeAffinities.size() < MIN_FOCI) {
			return Optional.empty();
		}
		if (used > capacity || capacity - used > BUDGET_SLACK) {
			return Optional.empty();
		}

		EnumMap<Affinity, Integer> counts = new EnumMap<>(Affinity.class);
		int neutral = 0;
		for (Optional<Affinity> affinity : activeAffinities) {
			if (affinity.isPresent()) {
				counts.merge(affinity.get(), 1, Integer::sum);
			} else {
				neutral++;
			}
		}
		if (counts.isEmpty()) {
			return neutral == activeAffinities.size()
				? Optional.of(Capstone.STILLPOINT)
				: Optional.empty();
		}
		if (counts.size() == 1 && neutral == 0) {
			return Optional.of(Capstone.ofAffinity(counts.keySet().iterator().next()));
		}
		if (counts.size() == Affinity.values().length) {
			return Optional.of(Capstone.MAELSTROM);
		}
		return Optional.empty();
	}

	public static Optional<Capstone> capstoneOf(Player player) {
		List<Integer> active = Attunement.activeSlots(player);
		List<Optional<Affinity>> activeAffinities = new ArrayList<>(active.size());
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : active) {
			activeAffinities.add(Attunement.definitionFor(player, inv.get(slot))
				.flatMap(FocusDefinition::affinity));
		}
		return resolveCapstone(activeAffinities, Attunement.used(player), Attunement.capacity(player));
	}

	public static Optional<Affinity> affinityOf(Player player) {
		return capstoneOf(player).flatMap(Capstone::affinity);
	}

	public static boolean isAt(Player player, Affinity affinity) {
		return affinityOf(player).filter(a -> a == affinity).isPresent();
	}

	public static boolean isAt(Player player, Capstone capstone) {
		return capstoneOf(player).filter(c -> c == capstone).isPresent();
	}

	public static String capstoneName(Affinity affinity) {
		return Capstone.ofAffinity(affinity).displayName();
	}

	public static String capstoneDescription(Affinity affinity) {
		return Capstone.ofAffinity(affinity).description();
	}

	public static float adjustDamage(LivingEntity defender, DamageSource source, float amount) {
		if (amount <= 0.0F) {
			return amount;
		}
		LivingEntity attacker = AttunedCombat.attackerOf(source);

		if (defender instanceof Player defenderPlayer
				&& isAt(defenderPlayer, Capstone.UNYIELDING)
				&& Resonance.atApex(defenderPlayer)) {
			Matchup matchup = matchupAgainst(Affinity.BASTION, attacker);
			if (matchup != Matchup.NEUTRALIZED) {
				float fraction = matchup == Matchup.EMPOWERED ? CAP_EMPOWERED : CAP_NORMAL;
				float cap = defenderPlayer.getMaxHealth() * fraction;
				if (amount > cap) {
					amount = cap;
				}
			}
		}

		if (!(defender instanceof Player) && defender.getMaxHealth() > 0.0F
				&& attacker instanceof Player attackerPlayer
				&& isAt(attackerPlayer, Capstone.EXECUTE)
				&& Resonance.atApex(attackerPlayer)
				&& isApexMeleeTarget(defender, attackerPlayer, source)) {
			Matchup matchup = matchupAgainst(Affinity.FURY, defender);
			if (matchup != Matchup.NEUTRALIZED) {
				float threshold = matchup == Matchup.EMPOWERED ? EXECUTE_EMPOWERED : EXECUTE_NORMAL;
				if (defender.getHealth() / defender.getMaxHealth() <= threshold) {
					amount = Math.max(amount, EXECUTE_DAMAGE);
				}
			}
		}

		if (!(defender instanceof Player) && defender.getMaxHealth() > 0.0F
				&& attacker instanceof Player attackerPlayer
				&& isAt(attackerPlayer, Capstone.JUDGMENT)
				&& Resonance.atApex(attackerPlayer)
				&& isApexMeleeTarget(defender, attackerPlayer, source)) {
			Matchup matchup = matchupAgainst(Affinity.HOLY, defender);
			if (matchup == Matchup.EMPOWERED
					&& defender.getHealth() / defender.getMaxHealth() <= JUDGMENT_THRESHOLD) {
				amount *= (1.0F + JUDGMENT_DAMAGE_BONUS);
			}
		}

		if (!(defender instanceof Player) && defender.getMaxHealth() > 0.0F
				&& attacker instanceof Player attackerPlayer
				&& isAt(attackerPlayer, Capstone.MAELSTROM)
				&& Resonance.atApex(attackerPlayer)
				&& isApexMeleeTarget(defender, attackerPlayer, source)
				&& MobAffinities.of(defender).isPresent()) {
			amount *= (1.0F + MAELSTROM_DAMAGE_BONUS);
			markScrambled(attackerPlayer, defender);
		}
		return amount;
	}

	private static void afterDamage(LivingEntity defender, DamageSource source,
			float originalDamage, float dealtDamage, boolean blocked) {
		if (dealtDamage <= 0.0F || !(defender instanceof Player defenderPlayer)) {
			return;
		}
		LivingEntity attacker = AttunedCombat.attackerOf(source);
		if (attacker != null
				&& isAt(defenderPlayer, Capstone.STILLPOINT)
				&& Resonance.atApex(defenderPlayer)
				&& hasAffinityPressure(attacker)) {
			pulseStillpoint(defenderPlayer);
		}
	}

	public static boolean suppressesIncomingAdvantage(Player defender, LivingEntity attacker) {
		if (attacker == null) {
			return false;
		}
		if (isAt(defender, Capstone.STILLPOINT)
				&& Resonance.atApex(defender)
				&& hasAffinityPressure(attacker)) {
			return true;
		}
		return isAt(defender, Capstone.MAELSTROM)
			&& Resonance.atApex(defender)
			&& isScrambledBy(attacker, defender);
	}

	static boolean hasAffinityPressure(LivingEntity entity) {
		if (entity instanceof Player player) {
			return Attunement.isDiscord(player) || Attunement.committedAffinity(player).isPresent();
		}
		return AttunedCombat.affinityOf(entity).isPresent();
	}

	private static boolean isApexMeleeTarget(LivingEntity defender, Player attacker, DamageSource source) {
		return isDirectMelee(attacker, source)
			&& !isOwnPet(defender, attacker)
			&& !(defender instanceof AbstractVillager);
	}

	private static boolean isDirectMelee(Player player, DamageSource source) {
		if (source.getDirectEntity() != player) {
			return false;
		}
		return !source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
			&& !source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
	}

	private static boolean isOwnPet(LivingEntity defender, Player attacker) {
		if (!(defender instanceof TamableAnimal pet)) {
			return false;
		}
		var ownerRef = pet.getOwnerReference();
		return ownerRef != null && attacker.getUUID().equals(ownerRef.getUUID());
	}

	private static void markScrambled(Player player, LivingEntity target) {
		long expiresAt = target.level().getGameTime() + MAELSTROM_SCRAMBLE_TICKS;
		maelstromScrambles.put(new ScrambleKey(target.getUUID(), player.getUUID()), expiresAt);
	}

	private static boolean isScrambledBy(LivingEntity target, Player player) {
		ScrambleKey key = new ScrambleKey(target.getUUID(), player.getUUID());
		Long expiresAt = maelstromScrambles.get(key);
		if (expiresAt == null) {
			return false;
		}
		if (target.level().getGameTime() >= expiresAt) {
			maelstromScrambles.remove(key);
			return false;
		}
		return true;
	}

	private static void pulseStillpoint(Player player) {
		long now = player.level().getGameTime();
		Long last = stillpointPulses.get(player.getUUID());
		if (last != null && now - last < STILLPOINT_ABSORPTION_COOLDOWN_TICKS) {
			return;
		}
		stillpointPulses.put(player.getUUID(), now);
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,
			STILLPOINT_ABSORPTION_TICKS, 0, true, true, true));
	}

	public static boolean ignoresKnockback(LivingEntity entity) {
		return entity instanceof Player player
			&& isAt(player, Capstone.UNYIELDING)
			&& Resonance.atApex(player);
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || !player.isSprinting()) {
			return true;
		}
		if (!isAt(player, Capstone.UNTOUCHABLE) || !Resonance.atApex(player)) {
			return true;
		}
		LivingEntity attacker = AttunedCombat.attackerOf(source);
		if (attacker == null) {
			return true;
		}
		Matchup matchup = matchupAgainst(Affinity.ZEPHYR, attacker);
		if (matchup == Matchup.NEUTRALIZED) {
			return true;
		}
		float chance = matchup == Matchup.EMPOWERED ? DODGE_EMPOWERED : DODGE_NORMAL;
		if (player.getRandom().nextFloat() >= chance) {
			return true;
		}
		onDodge(player);
		return false;
	}

	private static Matchup matchupAgainst(Affinity capstone, LivingEntity other) {
		Optional<Affinity> otherAffinity =
			other == null ? Optional.empty() : AttunedCombat.affinityOf(other);
		if (otherAffinity.isEmpty()) {
			return Matchup.NORMAL;
		}
		Affinity o = otherAffinity.get();
		if (o.beats(capstone)) {
			return Matchup.NEUTRALIZED;
		}
		if (capstone.beats(o)) {
			return Matchup.EMPOWERED;
		}
		return Matchup.NORMAL;
	}

	private static void tick(MinecraftServer server) {
		long nowTime = server.overworld().getGameTime();
		maelstromScrambles.entrySet().removeIf(entry -> nowTime >= entry.getValue());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			Capstone now = capstoneOf(player).orElse(null);
			Capstone was = apexState.get(id);
			boolean armedNow = now != null && Resonance.atApex(player);
			boolean armedWas = Boolean.TRUE.equals(armedState.get(id));

			if (now == null && was == null) {
				continue;
			}
			if (now == null) {
				apexState.remove(id);
				armedState.remove(id);
				announceLost(player);
				continue;
			}
			if (was == null) {
				apexState.put(id, now);
				armedState.put(id, armedNow);
				if (armedNow) {
					announceGained(player, now);
				} else {
					announceGainedDormant(player, now);
				}
				continue;
			}
			if (was != now) {
				apexState.put(id, now);
				armedState.put(id, armedNow);
				announceLost(player);
				if (armedNow) {
					announceGained(player, now);
				} else {
					announceGainedDormant(player, now);
				}
				continue;
			}
			if (armedNow != armedWas) {
				armedState.put(id, armedNow);
				if (armedNow) {
					announceRearmed(player, now);
				} else {
					announceDormant(player);
				}
			}
		}
	}

	private static void announceGained(ServerPlayer player, Capstone capstone) {
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.0F);
		player.sendSystemMessage(Component.literal("Apex active: ")
			.withStyle(ChatFormatting.GRAY)
			.append(Component.literal(capstone.displayName())
				.withStyle(capstone.chatColor(), ChatFormatting.BOLD))
			.append(Component.literal(". " + capstone.description())
				.withStyle(ChatFormatting.GRAY)));
		AttunedAdvancements.award(player, "attunement/apex");
	}

	private static void announceGainedDormant(ServerPlayer player, Capstone capstone) {
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 0.8F);
		player.sendSystemMessage(Component.translatable(
				"apex.attuned.unlocked_dormant",
				Component.literal(capstone.displayName())
					.withStyle(capstone.chatColor(), ChatFormatting.BOLD))
			.withStyle(ChatFormatting.GRAY));
	}

	private static void announceRearmed(ServerPlayer player, Capstone capstone) {
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.3F);
		player.sendSystemMessage(Component.literal("Apex active again: ")
			.withStyle(ChatFormatting.GRAY)
			.append(Component.literal(capstone.displayName())
				.withStyle(capstone.chatColor(), ChatFormatting.BOLD))
			.append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
		AttunedAdvancements.award(player, "attunement/apex");
	}

	private static void announceDormant(ServerPlayer player) {
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6F, 0.6F);
		player.sendSystemMessage(Component.literal("Your Apex sleeps. Resonance is too low.")
			.withStyle(ChatFormatting.GRAY));
	}

	private static void announceLost(ServerPlayer player) {
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.0F);
		player.sendSystemMessage(Component.literal("Your Apex has faded.")
			.withStyle(ChatFormatting.GRAY));
	}

	private static void onDodge(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 1, true, false, true));
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.CLOUD,
			player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, player.blockPosition(),
			SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.7F);
	}

	private record ScrambleKey(UUID targetId, UUID playerId) {
	}
}
