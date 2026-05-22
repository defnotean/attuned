package dev.attuned.network;

import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.content.AttunedContent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Attuned networking: registers the Voidstep teleport payload and resolves the
 * teleport server-side.
 *
 * <p>The teleport is always validated on the server — the Focus must be active
 * and off cooldown — so a forged payload achieves nothing.
 */
public final class AttunedNetworking {
	private AttunedNetworking() {}

	/** Voidstep cooldown, in ticks (10 seconds). */
	private static final int COOLDOWN_TICKS = 200;
	/** Maximum blink distance, in blocks. */
	private static final int MAX_DISTANCE = 8;

	/** Per-player game-time of the last Voidstep. */
	private static final Map<UUID, Long> lastStep = new HashMap<>();

	/** Registers the Voidstep payload type and its server-side receiver. */
	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(VoidstepPayload.TYPE, VoidstepPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(VoidstepPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			((ServerLevel) player.level()).getServer().execute(() -> voidstep(player));
		});
	}

	private static void voidstep(ServerPlayer player) {
		if (!hasVoidstepActive(player)) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = lastStep.get(player.getUUID());
		if (last != null && now - last < COOLDOWN_TICKS) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		Vec3 origin = player.position();
		Vec3 look = player.getLookAngle();
		Vec3 destination = null;
		// Walk inward from the full distance to the first spot the player fits.
		for (int dist = MAX_DISTANCE; dist >= 1; dist--) {
			Vec3 candidate = origin.add(look.scale(dist));
			AABB box = player.getBoundingBox().move(candidate.subtract(origin));
			if (level.noCollision(player, box)) {
				destination = candidate;
				break;
			}
		}
		if (destination == null) {
			return;
		}

		lastStep.put(player.getUUID(), now);
		spawnTrail(level, origin);
		player.connection.teleport(destination.x, destination.y, destination.z,
			player.getYRot(), player.getXRot());
		player.resetFallDistance();
		spawnTrail(level, destination);
		level.playSound(null, player.blockPosition(),
			SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	private static boolean hasVoidstepActive(ServerPlayer player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			if (inv.get(slot).is(AttunedContent.VOIDSTEP_FOCUS)) {
				return true;
			}
		}
		return false;
	}

	private static void spawnTrail(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 30, 0.3, 0.6, 0.3, 0.4);
	}
}
