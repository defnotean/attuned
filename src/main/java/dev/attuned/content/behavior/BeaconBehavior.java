package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.compat.CompassTags;
import dev.attuned.compat.CompassTags.LodestoneTags;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
		new net.minecraft.network.chat.TranslatableComponent("item.attuned.beacon_compass");

	private final Map<UUID, Map<ItemStack, TrackerSnapshot>> changedCompasses = new HashMap<>();

	public BeaconBehavior() {
		AttunedPlayerCleanup.onForgetPlayer(this::restorePlayer);
		AttunedServerCleanup.onStop(this::restoreAllCompasses);
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		if (player.getRespawnPosition() == null) {
			restorePlayer(player);
			return;
		}
		GlobalPos home = GlobalPos.of(player.getRespawnDimension(), player.getRespawnPosition());
		LodestoneTags tracker = CompassTags.target(home);
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

	private void applyBeaconTracker(UUID playerId, ItemStack compass, LodestoneTags tracker) {
		LodestoneTags current = CompassTags.lodestone(compass);
		TrackerSnapshot snapshot = snapshotFor(playerId, compass);
		if (snapshot != null && !Objects.equals(current, snapshot.beaconTracker)) {
			restoreNameIfStillBeaconName(compass, snapshot);
			forget(playerId, compass);
			snapshot = null;
		}
		if (snapshot == null) {
			Component originalName = CompassTags.customName(compass);
			changedCompasses
				.computeIfAbsent(playerId, id -> new IdentityHashMap<>())
				.put(compass, new TrackerSnapshot(
					current, !current.isEmpty(), originalName, originalName != null, tracker));
			CompassTags.setLodestone(compass, tracker);
			CompassTags.setCustomName(compass, BEACON_COMPASS_NAME);
			return;
		}
		if (!Objects.equals(current, tracker)) {
			CompassTags.setLodestone(compass, tracker);
		}
		if (!Objects.equals(CompassTags.customName(compass), BEACON_COMPASS_NAME)) {
			CompassTags.setCustomName(compass, BEACON_COMPASS_NAME);
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

	private void restoreAllCompasses() {
		for (Map<ItemStack, TrackerSnapshot> snapshots : changedCompasses.values()) {
			snapshots.forEach(this::restoreIfStillBeaconTracker);
		}
		changedCompasses.clear();
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
		if (!Objects.equals(CompassTags.lodestone(compass), snapshot.beaconTracker)) {
			restoreNameIfStillBeaconName(compass, snapshot);
			return;
		}
		if (snapshot.hadOriginal) {
			CompassTags.setLodestone(compass, snapshot.originalTracker);
		} else {
			CompassTags.setLodestone(compass, null);
		}
		restoreName(compass, snapshot);
	}

	private static void restoreNameIfStillBeaconName(ItemStack compass, TrackerSnapshot snapshot) {
		if (Objects.equals(CompassTags.customName(compass), BEACON_COMPASS_NAME)) {
			restoreName(compass, snapshot);
		}
	}

	private static void restoreName(ItemStack compass, TrackerSnapshot snapshot) {
		if (snapshot.hadOriginalName) {
			CompassTags.setCustomName(compass, snapshot.originalName);
		} else {
			CompassTags.setCustomName(compass, null);
		}
	}

	private static final class TrackerSnapshot {
		private final LodestoneTags originalTracker;
		private final boolean hadOriginal;
		private final Component originalName;
		private final boolean hadOriginalName;
		private LodestoneTags beaconTracker;

		private TrackerSnapshot(
				LodestoneTags originalTracker,
				boolean hadOriginal,
				Component originalName,
				boolean hadOriginalName,
				LodestoneTags beaconTracker) {
			this.originalTracker = originalTracker;
			this.hadOriginal = hadOriginal;
			this.originalName = originalName;
			this.hadOriginalName = hadOriginalName;
			this.beaconTracker = beaconTracker;
		}
	}
}
