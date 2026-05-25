package dev.attuned.content.behavior;

import dev.attuned.api.focus.FocusBehavior;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;

/**
 * Beacon Focus: a held compass is bound to the wearer's last bed, pointing the
 * way home instead of toward world spawn.
 *
 * <p>While active, any compass in hand that needs changing has its current
 * lodestone tracker snapshotted before being pointed at the respawn position.
 * Only compasses still carrying the tracker written by Beacon are restored, so a
 * real lodestone compass keeps its own binding when the Focus deactivates.
 */
public final class BeaconBehavior implements FocusBehavior {
	private static final Component BEACON_COMPASS_NAME =
		Component.translatable("item.attuned.beacon_compass");

	private final Map<UUID, Map<ItemStack, TrackerSnapshot>> changedCompasses = new HashMap<>();

	public BeaconBehavior() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
			restorePlayer(handler.player));
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		if (respawn == null) {
			restorePlayer(player);
			return;
		}
		GlobalPos home = respawn.respawnData().globalPos();
		LodestoneTracker tracker = new LodestoneTracker(Optional.of(home), false);
		restoreCompassesNoLongerHeld(player);
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.is(Items.COMPASS)) {
				applyBeaconTracker(player.getUUID(), held, tracker);
			}
		}
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		restorePlayer(player);
	}

	private void applyBeaconTracker(UUID playerId, ItemStack compass, LodestoneTracker tracker) {
		LodestoneTracker current = compass.get(DataComponents.LODESTONE_TRACKER);
		TrackerSnapshot snapshot = snapshotFor(playerId, compass);
		if (snapshot != null && !Objects.equals(current, snapshot.beaconTracker)) {
			restoreNameIfStillBeaconName(compass, snapshot);
			forget(playerId, compass);
			snapshot = null;
		}
		if (snapshot == null) {
			Component originalName = compass.get(DataComponents.CUSTOM_NAME);
			changedCompasses
				.computeIfAbsent(playerId, id -> new IdentityHashMap<>())
				.put(compass, new TrackerSnapshot(
					current, current != null, originalName, originalName != null, tracker));
			compass.set(DataComponents.LODESTONE_TRACKER, tracker);
			compass.set(DataComponents.CUSTOM_NAME, BEACON_COMPASS_NAME);
			return;
		}
		if (!Objects.equals(current, tracker)) {
			compass.set(DataComponents.LODESTONE_TRACKER, tracker);
		}
		if (!Objects.equals(compass.get(DataComponents.CUSTOM_NAME), BEACON_COMPASS_NAME)) {
			compass.set(DataComponents.CUSTOM_NAME, BEACON_COMPASS_NAME);
		}
		snapshot.beaconTracker = tracker;
	}

	private TrackerSnapshot snapshotFor(UUID playerId, ItemStack compass) {
		Map<ItemStack, TrackerSnapshot> snapshots = changedCompasses.get(playerId);
		return snapshots == null ? null : snapshots.get(compass);
	}

	private void restoreCompassesNoLongerHeld(ServerPlayer player) {
		UUID playerId = player.getUUID();
		Map<ItemStack, TrackerSnapshot> snapshots = changedCompasses.get(playerId);
		if (snapshots == null) {
			return;
		}
		Iterator<Map.Entry<ItemStack, TrackerSnapshot>> it = snapshots.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<ItemStack, TrackerSnapshot> entry = it.next();
			ItemStack compass = entry.getKey();
			if (!compass.is(Items.COMPASS) || !isHeld(player, compass)) {
				restoreIfStillBeaconTracker(compass, entry.getValue());
				it.remove();
			}
		}
		if (snapshots.isEmpty()) {
			changedCompasses.remove(playerId);
		}
	}

	private static boolean isHeld(ServerPlayer player, ItemStack stack) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held == stack) {
				return true;
			}
		}
		return false;
	}

	private void restorePlayer(ServerPlayer player) {
		Map<ItemStack, TrackerSnapshot> snapshots = changedCompasses.remove(player.getUUID());
		if (snapshots == null) {
			return;
		}
		snapshots.forEach(this::restoreIfStillBeaconTracker);
	}

	private void forget(UUID playerId, ItemStack compass) {
		Map<ItemStack, TrackerSnapshot> snapshots = changedCompasses.get(playerId);
		if (snapshots == null) {
			return;
		}
		snapshots.remove(compass);
		if (snapshots.isEmpty()) {
			changedCompasses.remove(playerId);
		}
	}

	private void restoreIfStillBeaconTracker(ItemStack compass, TrackerSnapshot snapshot) {
		if (!compass.is(Items.COMPASS)) {
			return;
		}
		if (!Objects.equals(compass.get(DataComponents.LODESTONE_TRACKER), snapshot.beaconTracker)) {
			restoreNameIfStillBeaconName(compass, snapshot);
			return;
		}
		if (snapshot.hadOriginal) {
			compass.set(DataComponents.LODESTONE_TRACKER, snapshot.originalTracker);
		} else {
			compass.remove(DataComponents.LODESTONE_TRACKER);
		}
		restoreName(compass, snapshot);
	}

	private static void restoreNameIfStillBeaconName(ItemStack compass, TrackerSnapshot snapshot) {
		if (Objects.equals(compass.get(DataComponents.CUSTOM_NAME), BEACON_COMPASS_NAME)) {
			restoreName(compass, snapshot);
		}
	}

	private static void restoreName(ItemStack compass, TrackerSnapshot snapshot) {
		if (snapshot.hadOriginalName) {
			compass.set(DataComponents.CUSTOM_NAME, snapshot.originalName);
		} else {
			compass.remove(DataComponents.CUSTOM_NAME);
		}
	}

	private static final class TrackerSnapshot {
		private final LodestoneTracker originalTracker;
		private final boolean hadOriginal;
		private final Component originalName;
		private final boolean hadOriginalName;
		private LodestoneTracker beaconTracker;

		private TrackerSnapshot(
				LodestoneTracker originalTracker,
				boolean hadOriginal,
				Component originalName,
				boolean hadOriginalName,
				LodestoneTracker beaconTracker) {
			this.originalTracker = originalTracker;
			this.hadOriginal = hadOriginal;
			this.originalName = originalName;
			this.hadOriginalName = hadOriginalName;
			this.beaconTracker = beaconTracker;
		}
	}
}
