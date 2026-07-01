package dev.attuned.combat;

import dev.attuned.compat.AfterDamageCallback;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.content.behavior.VeilBehavior;
import dev.attuned.network.ActionBarMessages;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Combat hooks for The Unseen Foci. Kept separate from the affinity cycle so
 * stealth tools do not become a fourth matchup lane.
 */
public final class UnseenCombat {
	private UnseenCombat() {}

	private static final ResourceLocation NEEDLE_FOCUS =
		new ResourceLocation("attuned", "needle_focus");
	private static final ResourceLocation SOFTSTEP_FOCUS =
		new ResourceLocation("attuned", "softstep_focus");
	private static final float NEEDLE_MULTIPLIER = 1.35F;
	private static final int NEEDLE_COOLDOWN_TICKS = 120;
	private static final int NEEDLE_SOFTSTEP_WEAKNESS_TICKS = 80;
	private static final double BEHIND_DOT_THRESHOLD = -0.35D;

	private static final Map<UUID, Long> LAST_NEEDLE = new HashMap<>();
	/**
	 * Pending Needle consumptions recorded at the HEAD damage stage, keyed by
	 * attacker and then by victim so a sword sweep (several HEAD-stage calls in
	 * one tick, before any after-damage runs) cannot overwrite earlier victims'
	 * records. The cooldown is only burned (and the Veil only broken) once the
	 * hit actually lands in {@link #afterDamage}, so a dodged or
	 * invulnerability-framed hit wastes the multiplier but keeps the opener.
	 */
	private static final Map<UUID, Map<UUID, PendingProc>> PENDING_NEEDLE = new HashMap<>();
	private static boolean initialized;

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		AttunedPlayerCleanup.onForget(LAST_NEEDLE::remove);
		AttunedPlayerCleanup.onForget(PENDING_NEEDLE::remove);
		AttunedServerCleanup.onStop(LAST_NEEDLE::clear);
		AttunedServerCleanup.onStop(PENDING_NEEDLE::clear);
		AfterDamageCallback.EVENT.register(UnseenCombat::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			LAST_NEEDLE.remove(entity.getUUID());
			PENDING_NEEDLE.remove(entity.getUUID());
			PENDING_NEEDLE.values().forEach(pending -> pending.remove(entity.getUUID()));
			if (entity instanceof ServerPlayer) {
				VeilBehavior.forgetDeath(entity.getUUID());
			}
		});
	}

	public static float adjustDamage(LivingEntity defender, DamageSource source, float amount) {
		if (AttunedCombat.isReflecting()
				|| !(source.getEntity() instanceof ServerPlayer attacker)
				|| defender == attacker
				|| !isDirectHit(attacker, source)) {
			return amount;
		}
		boolean wasVeiled = VeilBehavior.isVeiled(attacker);
		boolean validCombatTarget = CombatTargets.isHostileOrPvpOpponent(defender, attacker);
		Map<UUID, PendingProc> pendingForAttacker =
			PENDING_NEEDLE.computeIfAbsent(attacker.getUUID(), id -> new HashMap<>());
		if (!hasActiveFocus(attacker, NEEDLE_FOCUS) || !validCombatTarget
				|| !canNeedle(attacker, defender, wasVeiled, pendingForAttacker)) {
			// No proc this swing: still break the Veil, but only once the hit lands
			// (recorded so a dodged hit cannot strip stealth for free).
			pendingForAttacker.put(defender.getUUID(),
				new PendingProc(defender.getUUID(), attacker.getLevel().getGameTime(), false));
			return amount;
		}
		// Shape the damage now (it cannot be retro-multiplied) but defer burning the
		// cooldown to the after-damage stage so a dodged hit keeps the opener.
		pendingForAttacker.put(defender.getUUID(),
			new PendingProc(defender.getUUID(), attacker.getLevel().getGameTime(), true));
		needleFeedback(attacker, defender);
		return DamageFormula.multiply(amount, NEEDLE_MULTIPLIER);
	}

	private static boolean isDirectHit(ServerPlayer attacker, DamageSource source) {
		if (source.getDirectEntity() != attacker) {
			return false;
		}
		return !source.is(DamageTypeTags.IS_PROJECTILE)
			&& !source.is(DamageTypeTags.IS_EXPLOSION);
	}

	private static boolean canNeedle(ServerPlayer attacker, LivingEntity defender,
			boolean wasVeiled, Map<UUID, PendingProc> pendingForAttacker) {
		long now = attacker.getLevel().getGameTime();
		Long last = LAST_NEEDLE.get(attacker.getUUID());
		if (last != null && now - last < NEEDLE_COOLDOWN_TICKS) {
			return false;
		}
		// A sword sweep hits several victims in the same tick before any
		// after-damage stage burns the cooldown: only the first candidate of the
		// tick may proc, all later ones are ordinary swings.
		for (PendingProc pending : pendingForAttacker.values()) {
			if (pending.consumes() && pending.gameTime() == now) {
				return false;
			}
		}
		return wasVeiled || isBehind(attacker, defender);
	}

	private static boolean isBehind(ServerPlayer attacker, LivingEntity defender) {
		Vec3 defenderLook = defender.getLookAngle().normalize();
		Vec3 toAttacker = attacker.position().subtract(defender.position()).normalize();
		return defenderLook.dot(toAttacker) < BEHIND_DOT_THRESHOLD;
	}

	private static void needleFeedback(ServerPlayer attacker, LivingEntity defender) {
		ServerLevel level = (ServerLevel) attacker.getLevel();
		level.sendParticles(ParticleTypes.CRIT,
			defender.getX(), defender.getY() + defender.getBbHeight() * 0.65, defender.getZ(),
			8, 0.2, 0.25, 0.2, 0.0);
		level.playSound(null, defender.blockPosition(),
			SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.6F, 1.55F);
	}

	private static void afterDamage(LivingEntity defender, DamageSource source,
			float originalDamage, float dealtDamage, boolean blocked) {
		if (dealtDamage <= 0.0F || AttunedCombat.isReflecting()) {
			return;
		}
		Entity entity = source.getEntity();
		if (entity instanceof ServerPlayer attacker && dealtDamage > 0.0F) {
			// The hit landed: spend the deferred opener resources now. A dodged or
			// invulnerability-framed hit (dealtDamage <= 0) leaves these intact.
			Map<UUID, PendingProc> pendingForAttacker = PENDING_NEEDLE.get(attacker.getUUID());
			if (pendingForAttacker != null) {
				PendingProc pending = pendingForAttacker.remove(defender.getUUID());
				if (pendingForAttacker.isEmpty()) {
					PENDING_NEEDLE.remove(attacker.getUUID());
				}
				if (pending != null && pending.gameTime() == attacker.getLevel().getGameTime()) {
					VeilBehavior.breakVeil(attacker);
					if (pending.consumes()) {
						LAST_NEEDLE.put(attacker.getUUID(), attacker.getLevel().getGameTime());
						applyNeedleSoftstepCombo(attacker, defender);
					}
				}
			}
		}
		if (defender instanceof ServerPlayer player && dealtDamage > 0.0F) {
			VeilBehavior.breakVeil(player);
		}
	}

	/** Resonant Combo: Softstep + Needle rewards a quiet crouch into a confirmed opener. */
	private static void applyNeedleSoftstepCombo(ServerPlayer attacker, LivingEntity defender) {
		if (!hasActiveFocus(attacker, SOFTSTEP_FOCUS)) {
			return;
		}
		defender.addEffect(new MobEffectInstance(
			MobEffects.WEAKNESS,
			NEEDLE_SOFTSTEP_WEAKNESS_TICKS, 0, true, false, true));
		needleSoftstepComboFeedback(attacker, defender);
	}

	private static void needleSoftstepComboFeedback(ServerPlayer attacker, LivingEntity defender) {
		ServerLevel level = (ServerLevel) attacker.getLevel();
		level.sendParticles(ParticleTypes.SCULK_SOUL,
			defender.getX(), defender.getY() + defender.getBbHeight() * 0.65, defender.getZ(),
			10, 0.2, 0.25, 0.2, 0.01);
		level.playSound(null, defender.blockPosition(),
			SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.35F, 1.8F);
		ActionBarMessages.send(attacker, ActionBarMessages.Priority.ABILITY,
			Component.translatable("combo.attuned.softstep_needle"));
	}

	private static boolean hasActiveFocus(Player player, ResourceLocation targetId) {
		AttunedInv inventory = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			ItemStack stack = inventory.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			Item item = stack.getItem();
			ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
			if (targetId.equals(itemId)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A Needle opener shaped at the HEAD damage stage and awaiting confirmation in
	 * the after-damage stage. {@code consumes} distinguishes a proc'd swing (which
	 * burns the cooldown on a landed hit) from a non-proc swing (which only breaks
	 * the Veil).
	 */
	private record PendingProc(UUID target, long gameTime, boolean consumes) {
	}
}
