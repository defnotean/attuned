package dev.attuned.combat;

import dev.attuned.AttunedConfig;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.content.AttunedContent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Gravebind Focus: a fatal blow is refused once a minute.
 *
 * <p>When a player with an active Gravebind Focus would die and the save is off
 * cooldown, the death is cancelled, the player is restored to half health with a
 * brief regeneration and resistance, and a totem-like flourish plays. The
 * per-player cooldown lives only in memory — it need not survive a restart.
 */
public final class GravebindSave {
	private GravebindSave() {}

	/** Per-player game-time of the last save. */
	private static final Map<UUID, Long> lastSave = new HashMap<>();
	private static boolean initialized;

	/** Registers the death hook that drives the Gravebind save. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !hasGravebindActive(player)) {
				return true;
			}
			// Mirror vanilla totems: never refuse /kill or other
			// invulnerability-bypassing damage, so admin tooling still works.
			if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}
			long now = player.level().getGameTime();
			Long last = lastSave.get(player.getUUID());
			if (last != null && now - last < AttunedConfig.get().gravebindCooldownTicks()) {
				return true;
			}
			lastSave.put(player.getUUID(), now);
			rescue(player);
			return false;
		});
		// Deliberately NOT cleared on disconnect: relogging must not grant a
		// fresh save while the cooldown is still running. Entries are bounded
		// by the online-player count and cleared on server stop.
		AttunedServerCleanup.onStop(lastSave::clear);
	}

	private static boolean hasGravebindActive(ServerPlayer player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			if (inv.get(slot).is(AttunedContent.GRAVEBIND_FOCUS)) {
				return true;
			}
		}
		return false;
	}

	private static void rescue(ServerPlayer player) {
		player.setHealth(player.getMaxHealth() * 0.5F);
		player.removeAllEffects();
		player.clearFire();
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1));

		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
			player.getX(), player.getY() + 1.0, player.getZ(),
			40, 0.5, 0.6, 0.5, 0.25);
		level.playSound(null, player.blockPosition(),
			SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
	}
}
