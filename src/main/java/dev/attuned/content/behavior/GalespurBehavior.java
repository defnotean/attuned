package dev.attuned.content.behavior;

import dev.attuned.compat.AttributeModifierIds;

import dev.attuned.Attuned;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Galespur Focus: doubles the movement attributes of the living mount the wearer
 * is currently riding, then restores the mount when the ride or Focus ends.
 */
public final class GalespurBehavior implements FocusBehavior {
	private static final ResourceLocation MOVEMENT_SPEED_ID =
		new ResourceLocation(Attuned.MOD_ID, "galespur_mount_speed");
	private static final ResourceLocation FLYING_SPEED_ID =
		new ResourceLocation(Attuned.MOD_ID, "galespur_mount_flying_speed");
	private static final double MOVEMENT_SPEED_BONUS = 1.0;
	private static final double FLYING_SPEED_BONUS = Math.sqrt(2.0) - 1.0;

	private static final Map<UUID, LivingEntity> MOUNT_BY_PLAYER = new HashMap<>();
	private static final Map<UUID, Integer> BOOSTERS_BY_MOUNT = new HashMap<>();

	public GalespurBehavior() {
		AttunedPlayerCleanup.onForgetPlayer(GalespurBehavior::forgetPlayer);
		AttunedServerCleanup.onStop(GalespurBehavior::removeAllBoosts);
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		UUID playerId = player.getUUID();
		LivingEntity currentMount = currentMount(player);
		LivingEntity previousMount = MOUNT_BY_PLAYER.get(playerId);
		if (previousMount != currentMount) {
			releaseMount(playerId, previousMount);
			if (currentMount != null) {
				MOUNT_BY_PLAYER.put(playerId, currentMount);
				retainMount(currentMount);
			}
		}
		if (currentMount != null) {
			applyBoost(currentMount);
		}
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		forgetPlayer(player);
	}

	private static LivingEntity currentMount(ServerPlayer player) {
		Entity vehicle = player.getVehicle();
		if (!(vehicle instanceof LivingEntity living) || !vehicle.hasPassenger(player)) {
			return null;
		}
		return living;
	}

	private static void retainMount(LivingEntity mount) {
		BOOSTERS_BY_MOUNT.merge(mount.getUUID(), 1, Integer::sum);
		applyBoost(mount);
	}

	private static void releaseMount(UUID playerId, LivingEntity mount) {
		MOUNT_BY_PLAYER.remove(playerId);
		if (mount == null) {
			return;
		}
		UUID mountId = mount.getUUID();
		int remaining = BOOSTERS_BY_MOUNT.getOrDefault(mountId, 0) - 1;
		if (remaining > 0) {
			BOOSTERS_BY_MOUNT.put(mountId, remaining);
			return;
		}
		BOOSTERS_BY_MOUNT.remove(mountId);
		removeBoost(mount);
	}

	private static void forgetPlayer(ServerPlayer player) {
		UUID playerId = player.getUUID();
		LivingEntity previousMount = MOUNT_BY_PLAYER.get(playerId);
		releaseMount(playerId, previousMount);
	}

	private static void removeAllBoosts() {
		for (LivingEntity mount : MOUNT_BY_PLAYER.values()) {
			removeBoost(mount);
		}
		MOUNT_BY_PLAYER.clear();
		BOOSTERS_BY_MOUNT.clear();
	}

	private static void applyBoost(LivingEntity mount) {
		applyModifier(mount.getAttribute(Attributes.MOVEMENT_SPEED), MOVEMENT_SPEED_ID, MOVEMENT_SPEED_BONUS);
		applyModifier(mount.getAttribute(Attributes.FLYING_SPEED), FLYING_SPEED_ID, FLYING_SPEED_BONUS);
	}

	private static void applyModifier(AttributeInstance attribute, ResourceLocation id, double amount) {
		if (attribute == null || attribute.getModifier(AttributeModifierIds.uuid(id)) != null) {
			return;
		}
		attribute.addTransientModifier(new AttributeModifier(AttributeModifierIds.uuid(id), AttributeModifierIds.name(id), amount, AttributeModifier.Operation.MULTIPLY_BASE));
	}

	private static void removeBoost(LivingEntity mount) {
		removeModifier(mount.getAttribute(Attributes.MOVEMENT_SPEED), MOVEMENT_SPEED_ID);
		removeModifier(mount.getAttribute(Attributes.FLYING_SPEED), FLYING_SPEED_ID);
	}

	private static void removeModifier(AttributeInstance attribute, ResourceLocation id) {
		if (attribute != null) {
			attribute.removeModifier(AttributeModifierIds.uuid(id));
		}
	}
}
