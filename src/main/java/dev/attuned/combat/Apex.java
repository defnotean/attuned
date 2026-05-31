package dev.attuned.combat;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedAdvancements;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
 * The Apex capstone: a per-affinity passive that switches on only when a player
 * is fully committed to one affinity — at least {@link #MIN_FOCI} active Foci,
 * all sharing one affinity (no neutral Foci), with the attunement budget so
 * nearly spent that no further Focus could fit.
 *
 * <p>Every capstone is wired into the Fury &gt; Bastion &gt; Zephyr &gt; Fury
 * counter-cycle: it is <b>empowered</b> against the affinity it beats, behaves
 * <b>normally</b> against the unaffiliated, and is <b>neutralized</b> by the
 * affinity that beats it — so even a maxed build still answers to
 * rock-paper-scissors.
 *
 * <ul>
 *   <li><b>Fury — Execute:</b> finishes a low-health mob. Empowered against
 *       Bastion mobs (higher health threshold); neutralized against Zephyr.</li>
 *   <li><b>Bastion — Unyielding:</b> caps how much one hit can remove. The cap
 *       tightens against Zephyr attackers and is pierced entirely by Fury.
 *       Knockback is always ignored ({@code LivingEntityKnockbackMixin}).</li>
 *   <li><b>Zephyr — Untouchable:</b> a chance to dodge attacks while sprinting.
 *       Higher against Fury attackers; nil against Bastion.</li>
 * </ul>
 *
 * <p>A per-tick check announces to the player — with a sound and a chat line —
 * when a capstone switches on or off, so the passive is never silent.
 */
public final class Apex {
	private Apex() {}

	/** Minimum active Foci for an Apex build. */
	private static final int MIN_FOCI = 4;
	/** Apex requires the budget within this many points of full. */
	private static final int BUDGET_SLACK = 1;

	/** Damage used to finish an executed mob — lethal through any armour. */
	private static final float EXECUTE_DAMAGE = 100000.0F;

	/** Fury execute health threshold against a neutral target. */
	private static final float EXECUTE_NORMAL = 0.20F;
	/** Fury execute threshold against the affinity Fury beats — devastating. */
	private static final float EXECUTE_EMPOWERED = 0.35F;

	/** Bastion's per-hit damage cap (fraction of max health) vs a neutral attacker. */
	private static final float CAP_NORMAL = 0.15F;
	/** Bastion's cap against the affinity Bastion beats — tighter. */
	private static final float CAP_EMPOWERED = 0.10F;

	/** Zephyr dodge chance against a neutral attacker. */
	private static final float DODGE_NORMAL = 0.40F;
	/** Zephyr dodge chance against the affinity Zephyr beats. */
	private static final float DODGE_EMPOWERED = 0.65F;

	/** Holy Judgment only judges wounded hostile mobs, avoiding full-health burst. */
	private static final float JUDGMENT_THRESHOLD = 0.30F;
	/** Holy Judgment adds damage instead of executing outright. */
	private static final float JUDGMENT_DAMAGE_BONUS = 0.40F;

	/** Per-player Apex affinity as of last tick, for spotting on/off changes. */
	private static final Map<UUID, Affinity> apexState = new HashMap<>();
	/**
	 * Per-player armed flag as of last tick. A capstone is <em>armed</em> when
	 * the player's Foci qualify for Apex <em>and</em> resonance is at or above
	 * the gating threshold. Tracking arming separately from affinity lets us
	 * announce the dormant / rearmed transitions that happen when resonance
	 * crosses the threshold without the Foci changing.
	 */
	private static final Map<UUID, Boolean> armedState = new HashMap<>();

	/** How a capstone fares against another combatant's affinity. */
	private enum Matchup { EMPOWERED, NORMAL, NEUTRALIZED }

	/** Registers the Zephyr dodge veto and the activation watcher. */
	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Apex::allowDamage);
		ServerTickEvents.END_SERVER_TICK.register(Apex::tick);
		AttunedPlayerCleanup.onForget(uuid -> {
			apexState.remove(uuid);
			armedState.remove(uuid);
		});
	}

	/**
	 * The affinity a player has reached Apex in, if any. Apex requires at least
	 * {@link #MIN_FOCI} active Foci, every one of them on the same affinity (no
	 * neutral Foci), and the budget all but spent.
	 */
	public static Optional<Affinity> affinityOf(Player player) {
		List<Integer> active = Attunement.activeSlots(player);
		if (active.size() < MIN_FOCI) {
			return Optional.empty();
		}
		AttunedInv inv = AttunedAttachments.getInventory(player);
		Affinity shared = null;
		for (int slot : active) {
			Optional<Affinity> affinity = Attunement.definitionFor(player, inv.get(slot))
				.flatMap(FocusDefinition::affinity);
			if (affinity.isEmpty()) {
				return Optional.empty();
			}
			if (shared == null) {
				shared = affinity.get();
			} else if (shared != affinity.get()) {
				return Optional.empty();
			}
		}
		if (shared == null) {
			return Optional.empty();
		}
		if (Attunement.capacity(player) - Attunement.used(player) > BUDGET_SLACK) {
			return Optional.empty();
		}
		return Optional.of(shared);
	}

	/** Whether a player is at Apex in a specific affinity. */
	public static boolean isAt(Player player, Affinity affinity) {
		return affinityOf(player).filter(a -> a == affinity).isPresent();
	}

	/** The capstone's display name for an affinity. */
	public static String capstoneName(Affinity affinity) {
		return switch (affinity) {
			case FURY -> "Execute";
			case BASTION -> "Unyielding";
			case ZEPHYR -> "Untouchable";
			case HOLY -> "Judgment";
		};
	}

	/** A one-line description of what an affinity's capstone does. */
	public static String capstoneDescription(Affinity affinity) {
		return switch (affinity) {
			case FURY -> "Your strikes finish off low-health foes.";
			case BASTION -> "No single blow can land hard, and knockback is ignored.";
			case ZEPHYR -> "A chance to dodge attacks outright while sprinting.";
			case HOLY -> "Judgment marks wounded Fury-aligned foes for a decisive strike.";
		};
	}

	/**
	 * Applies the damage-shaping capstones to one hit — the Bastion cap on a
	 * defending player and the Fury execute on a low-health mob — each scaled by
	 * the affinity matchup. The capstone only fires while the player's Resonance
	 * is at or above the Apex threshold. Called from {@code LivingEntityHurtMixin}.
	 */
	public static float adjustDamage(LivingEntity defender, DamageSource source, float amount) {
		if (amount <= 0.0F) {
			return amount;
		}
		LivingEntity attacker = AttunedCombat.attackerOf(source);

		// Bastion — Unyielding: cap one hit, unless a Fury attacker pierces it.
		if (defender instanceof Player defenderPlayer
				&& isAt(defenderPlayer, Affinity.BASTION)
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

		// Fury — Execute: finish a low-health mob, unless its affinity counters Fury.
		// Restricted to direct melee from the Apex player and never against their
		// own tamed pets or villagers. Fire ticks, drowning and friendly fire
		// must not one-shot something just because the player has Fury Apex.
		if (!(defender instanceof Player) && defender.getMaxHealth() > 0.0F
				&& attacker instanceof Player attackerPlayer
				&& isAt(attackerPlayer, Affinity.FURY)
				&& Resonance.atApex(attackerPlayer)
				&& source.getDirectEntity() == attackerPlayer
				&& !isOwnPet(defender, attackerPlayer)
				&& !(defender instanceof AbstractVillager)) {
			Matchup matchup = matchupAgainst(Affinity.FURY, defender);
			if (matchup != Matchup.NEUTRALIZED) {
				float threshold = matchup == Matchup.EMPOWERED ? EXECUTE_EMPOWERED : EXECUTE_NORMAL;
				if (defender.getHealth() / defender.getMaxHealth() <= threshold) {
					amount = Math.max(amount, EXECUTE_DAMAGE);
				}
			}
		}
		// Holy - Judgment: a decisive but non-executing strike against wounded
		// Fury-aligned mobs. Zephyr-affinity targets counter and neutralize it.
		if (!(defender instanceof Player) && defender.getMaxHealth() > 0.0F
				&& attacker instanceof Player attackerPlayer
				&& isAt(attackerPlayer, Affinity.HOLY)
				&& Resonance.atApex(attackerPlayer)
				&& source.getDirectEntity() == attackerPlayer
				&& !isOwnPet(defender, attackerPlayer)
				&& !(defender instanceof AbstractVillager)) {
			Matchup matchup = matchupAgainst(Affinity.HOLY, defender);
			if (matchup == Matchup.EMPOWERED
					&& defender.getHealth() / defender.getMaxHealth() <= JUDGMENT_THRESHOLD) {
				amount *= (1.0F + JUDGMENT_DAMAGE_BONUS);
			}
		}
		return amount;
	}

	/**
	 * Whether {@code defender} is a {@link TamableAnimal} owned by
	 * {@code attacker}. Used to make sure a player's own wolves, cats and
	 * parrots are never the target of an Apex finisher.
	 */
	private static boolean isOwnPet(LivingEntity defender, Player attacker) {
		if (!(defender instanceof TamableAnimal pet)) {
			return false;
		}
		var ownerRef = pet.getOwnerReference();
		return ownerRef != null && attacker.getUUID().equals(ownerRef.getUUID());
	}

	/** Whether knockback against this entity should be ignored (Bastion Apex at Resonance). */
	public static boolean ignoresKnockback(LivingEntity entity) {
		return entity instanceof Player player
			&& isAt(player, Affinity.BASTION)
			&& Resonance.atApex(player);
	}

	/** {@code ALLOW_DAMAGE} veto implementing the Zephyr dodge. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || !player.isSprinting()) {
			return true;
		}
		if (!isAt(player, Affinity.ZEPHYR) || !Resonance.atApex(player)) {
			return true;
		}
		// Only a blow from a combatant can be dodged — never fall, fire or the void.
		LivingEntity attacker = AttunedCombat.attackerOf(source);
		if (attacker == null) {
			return true;
		}
		Matchup matchup = matchupAgainst(Affinity.ZEPHYR, attacker);
		if (matchup == Matchup.NEUTRALIZED) {
			return true; // Bastion's blows always land.
		}
		float chance = matchup == Matchup.EMPOWERED ? DODGE_EMPOWERED : DODGE_NORMAL;
		if (player.getRandom().nextFloat() >= chance) {
			return true;
		}
		onDodge(player);
		return false;
	}

	/**
	 * How an Apex capstone of {@code capstone} affinity fares against another
	 * combatant: empowered when the capstone's affinity counters theirs,
	 * neutralized when theirs counters the capstone, normal otherwise (an
	 * unaffiliated combatant, or a mirror match).
	 */
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

	/**
	 * Each tick, watch every player for four distinct Apex transitions and
	 * narrate each one to the player so the capstone is never silent.
	 *
	 * <p>The capstone has two independent inputs, the player's Foci layout
	 * and their resonance, and either can flip the capstone on or off. The
	 * transitions are:</p>
	 *
	 * <ul>
	 *   <li><b>gained</b>: a player who had no Apex affinity now does and is at
	 *       or above the resonance threshold.</li>
	 *   <li><b>dormant</b>: a player who was armed still has the same Apex
	 *       affinity but resonance has fallen below the threshold.</li>
	 *   <li><b>rearmed</b>: a player who went dormant has crossed resonance
	 *       back over the threshold with the same Apex affinity.</li>
	 *   <li><b>lost</b>: a player no longer qualifies for any Apex (their Foci
	 *       layout has changed).</li>
	 * </ul>
	 */
	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			Affinity now = affinityOf(player).orElse(null);
			Affinity was = apexState.get(id);
			boolean armedNow = now != null && Resonance.atApex(player);
			boolean armedWas = Boolean.TRUE.equals(armedState.get(id));

			if (now == null && was == null) {
				continue;
			}
			if (now == null) {
				// Foci no longer qualify, so the capstone is lost outright.
				apexState.remove(id);
				armedState.remove(id);
				announceLost(player);
				continue;
			}
			if (was == null) {
				// Fresh Apex affinity. Announce that the capstone has unlocked
				// either way so the player learns about the qualification; if
				// resonance is too low we follow with a dormant flavor so they
				// know the effect is gated on Resonance, not on their Foci.
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
				// Affinity itself changed without dropping through null. Treat it
				// as a lost-and-gained pair so each capstone gets its own beat.
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
			// Same Apex affinity as last tick, so only the armed flag can flip.
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

	private static void announceGained(ServerPlayer player, Affinity affinity) {
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.0F);
		player.sendSystemMessage(Component.literal("Apex active: ")
			.withStyle(ChatFormatting.GRAY)
			.append(Component.literal(capstoneName(affinity))
				.withStyle(affinityColor(affinity), ChatFormatting.BOLD))
			.append(Component.literal(". " + capstoneDescription(affinity))
				.withStyle(ChatFormatting.GRAY)));
		AttunedAdvancements.award(player, "attunement/apex");
	}

	/**
	 * Announces that a capstone has just qualified but resonance is below the
	 * Apex threshold, so the effect itself is still asleep. The player has done
	 * the work of stacking the Foci — we should not leave them in silence
	 * wondering whether anything happened, but we also should not claim the
	 * capstone is "active".
	 */
	private static void announceGainedDormant(ServerPlayer player, Affinity affinity) {
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 0.8F);
		player.sendSystemMessage(Component.translatable(
				"apex.attuned.unlocked_dormant",
				Component.literal(capstoneName(affinity))
					.withStyle(affinityColor(affinity), ChatFormatting.BOLD))
			.withStyle(ChatFormatting.GRAY));
	}

	private static void announceRearmed(ServerPlayer player, Affinity affinity) {
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.3F);
		player.sendSystemMessage(Component.literal("Apex active again: ")
			.withStyle(ChatFormatting.GRAY)
			.append(Component.literal(capstoneName(affinity))
				.withStyle(affinityColor(affinity), ChatFormatting.BOLD))
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

	private static ChatFormatting affinityColor(Affinity affinity) {
		return switch (affinity) {
			case FURY -> ChatFormatting.RED;
			case BASTION -> ChatFormatting.GOLD;
			case ZEPHYR -> ChatFormatting.AQUA;
			case HOLY -> ChatFormatting.YELLOW;
		};
	}
}
