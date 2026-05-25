package dev.attuned.combat;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.content.behavior.VeilBehavior;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
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

	private static final Identifier NEEDLE_FOCUS =
		Identifier.fromNamespaceAndPath("attuned", "needle_focus");
	private static final float NEEDLE_MULTIPLIER = 1.35F;
	private static final int NEEDLE_COOLDOWN_TICKS = 120;
	private static final double BEHIND_DOT_THRESHOLD = -0.35D;

	private static final Map<UUID, Long> LAST_NEEDLE = new HashMap<>();

	public static void init() {
		AttunedPlayerCleanup.onForget(LAST_NEEDLE::remove);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(UnseenCombat::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) ->
			LAST_NEEDLE.remove(entity.getUUID()));
	}

	public static float adjustDamage(LivingEntity defender, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer attacker)
				|| source.getDirectEntity() != attacker
				|| defender == attacker) {
			return amount;
		}
		boolean wasVeiled = VeilBehavior.isVeiled(attacker);
		VeilBehavior.breakVeil(attacker);
		if (!hasActiveFocus(attacker, NEEDLE_FOCUS) || !canNeedle(attacker, defender, wasVeiled)) {
			return amount;
		}
		LAST_NEEDLE.put(attacker.getUUID(), attacker.level().getGameTime());
		needleFeedback(attacker, defender);
		return amount * NEEDLE_MULTIPLIER;
	}

	private static boolean canNeedle(ServerPlayer attacker, LivingEntity defender, boolean wasVeiled) {
		long now = attacker.level().getGameTime();
		Long last = LAST_NEEDLE.get(attacker.getUUID());
		if (last != null && now - last < NEEDLE_COOLDOWN_TICKS) {
			return false;
		}
		return wasVeiled || isBehind(attacker, defender);
	}

	private static boolean isBehind(ServerPlayer attacker, LivingEntity defender) {
		Vec3 defenderLook = defender.getLookAngle().normalize();
		Vec3 toAttacker = attacker.position().subtract(defender.position()).normalize();
		return defenderLook.dot(toAttacker) < BEHIND_DOT_THRESHOLD;
	}

	private static void needleFeedback(ServerPlayer attacker, LivingEntity defender) {
		ServerLevel level = (ServerLevel) attacker.level();
		level.sendParticles(ParticleTypes.CRIT,
			defender.getX(), defender.getY() + defender.getBbHeight() * 0.65, defender.getZ(),
			8, 0.2, 0.25, 0.2, 0.0);
		level.playSound(null, defender.blockPosition(),
			SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.6F, 1.55F);
	}

	private static void afterDamage(LivingEntity defender, DamageSource source,
			float originalDamage, float dealtDamage, boolean blocked) {
		Entity entity = source.getEntity();
		if (entity instanceof ServerPlayer attacker) {
			VeilBehavior.breakVeil(attacker);
		}
		if (defender instanceof ServerPlayer player && dealtDamage > 0.0F) {
			VeilBehavior.breakVeil(player);
		}
	}

	private static boolean hasActiveFocus(Player player, Identifier targetId) {
		AttunedInv inventory = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			ItemStack stack = inventory.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			Item item = stack.getItem();
			Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
			if (targetId.equals(itemId)) {
				return true;
			}
		}
		return false;
	}
}
