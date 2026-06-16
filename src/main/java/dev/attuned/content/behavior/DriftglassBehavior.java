package dev.attuned.content.behavior;

import dev.attuned.Attuned;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.compat.CompassTags;
import dev.attuned.compat.CompassTags.LodestoneTags;
import dev.attuned.compat.LastDeathPositions;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Driftglass Focus: held compasses point back to the player's latest fishing or
 * boating return point, then restore cleanly when the Focus deactivates.
 */
public final class DriftglassBehavior implements FocusBehavior {
	private static final ResourceLocation BEACON_FOCUS =
		new ResourceLocation(Attuned.MOD_ID, "beacon_focus");
	private static final ResourceLocation WAYSTONE_FOCUS =
		new ResourceLocation(Attuned.MOD_ID, "waystone_focus");
	private static final Component DRIFTGLASS_COMPASS_NAME =
		new net.minecraft.network.chat.TranslatableComponent("item.attuned.driftglass_compass");

	private final Map<UUID, GlobalPos> points = new HashMap<>();
	private final Map<UUID, Map<ItemStack, TrackerSnapshot>> changedCompasses = new HashMap<>();

	public DriftglassBehavior() {
		AttunedPlayerCleanup.onForgetPlayer(player -> {
			points.remove(player.getUUID());
			restorePlayer(player);
		});
		AttunedServerCleanup.onStop(this::restoreAllCompasses);
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		if (hasHigherPriorityCompassFocus(player)) {
			restorePlayer(player);
			return;
		}
		if (player.getVehicle() instanceof Boat || player.fishing != null) {
			points.put(player.getUUID(), GlobalPos.of(player.getLevel().dimension(), player.blockPosition()));
		}
		GlobalPos point = points.get(player.getUUID());
		if (point == null) {
			restorePlayer(player);
			return;
		}
		LodestoneTags tracker = CompassTags.target(point);
		restoreCompassesNoLongerHeld(player);
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (canTrack(held)) {
				applyTracker(player.getUUID(), held, tracker);
			}
		}
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		restorePlayer(player);
	}

	private static boolean hasHigherPriorityCompassFocus(ServerPlayer player) {
		boolean beaconCanTrack = player.getRespawnPosition() != null;
		boolean waystoneCanTrack = LastDeathPositions.get(player).isPresent();
		if (!beaconCanTrack && !waystoneCanTrack) {
			return false;
		}
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(inv.get(slot).getItem());
			if (beaconCanTrack && BEACON_FOCUS.equals(id)) {
				return true;
			}
			if (waystoneCanTrack && WAYSTONE_FOCUS.equals(id)) {
				return true;
			}
		}
		return false;
	}

	private void applyTracker(UUID playerId, ItemStack compass, LodestoneTags tracker) {
		LodestoneTags current = CompassTags.lodestone(compass);
		TrackerSnapshot snapshot = snapshotFor(playerId, compass);
		if (snapshot != null && !Objects.equals(current, snapshot.focusTracker)) {
			restoreNameIfStillFocusName(compass, snapshot);
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
			CompassTags.setCustomName(compass, DRIFTGLASS_COMPASS_NAME);
			return;
		}
		if (!Objects.equals(current, tracker)) {
			CompassTags.setLodestone(compass, tracker);
		}
		if (!Objects.equals(CompassTags.customName(compass), DRIFTGLASS_COMPASS_NAME)) {
			CompassTags.setCustomName(compass, DRIFTGLASS_COMPASS_NAME);
		}
		snapshot.focusTracker = tracker;
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
			if (!canTrack(compass) || !isHeld(player, compass)) {
				restoreIfStillFocusTracker(compass, entry.getValue());
				it.remove();
			}
		}
		if (snapshots.isEmpty()) {
			changedCompasses.remove(playerId);
		}
	}

	private static boolean isHeld(ServerPlayer player, ItemStack stack) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (player.getItemInHand(hand) == stack) {
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
		snapshots.forEach(this::restoreIfStillFocusTracker);
	}

	private void restoreAllCompasses() {
		for (Map<ItemStack, TrackerSnapshot> snapshots : changedCompasses.values()) {
			snapshots.forEach(this::restoreIfStillFocusTracker);
		}
		changedCompasses.clear();
		points.clear();
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

	private void restoreIfStillFocusTracker(ItemStack compass, TrackerSnapshot snapshot) {
		if (!canTrack(compass)) {
			return;
		}
		if (!Objects.equals(CompassTags.lodestone(compass), snapshot.focusTracker)) {
			restoreNameIfStillFocusName(compass, snapshot);
			return;
		}
		if (snapshot.hadOriginal) {
			CompassTags.setLodestone(compass, snapshot.originalTracker);
		} else {
			CompassTags.setLodestone(compass, null);
		}
		restoreName(compass, snapshot);
	}

	private static boolean canTrack(ItemStack stack) {
		return stack.is(Items.COMPASS);
	}

	private static void restoreNameIfStillFocusName(ItemStack compass, TrackerSnapshot snapshot) {
		if (Objects.equals(CompassTags.customName(compass), DRIFTGLASS_COMPASS_NAME)) {
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
		private LodestoneTags focusTracker;

		private TrackerSnapshot(
				LodestoneTags originalTracker,
				boolean hadOriginal,
				Component originalName,
				boolean hadOriginalName,
				LodestoneTags focusTracker) {
			this.originalTracker = originalTracker;
			this.hadOriginal = hadOriginal;
			this.originalName = originalName;
			this.hadOriginalName = hadOriginalName;
			this.focusTracker = focusTracker;
		}
	}
}
