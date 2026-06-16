package dev.attuned.content.behavior;

import dev.attuned.Attuned;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;

/**
 * Waystone Focus: points held compasses back toward the wearer's last death.
 */
public final class WaystoneBehavior implements FocusBehavior {
	private static final ResourceLocation BEACON_FOCUS =
		new ResourceLocation(Attuned.MOD_ID, "beacon_focus");
	private static final Component WAYSTONE_COMPASS_NAME =
		Component.translatable("item.attuned.waystone_compass");

	private final Map<UUID, Map<ItemStack, TrackerSnapshot>> changedCompasses = new HashMap<>();

	public WaystoneBehavior() {
		AttunedPlayerCleanup.onForgetPlayer(this::restorePlayer);
		AttunedServerCleanup.onStop(this::restoreAllCompasses);
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		if (hasActiveBeaconTarget(player)) {
			restorePlayer(player);
			return;
		}
		Optional<GlobalPos> death = player.getLastDeathLocation();
		if (death.isEmpty()) {
			restorePlayer(player);
			return;
		}
		LodestoneTracker tracker = new LodestoneTracker(death, false);
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

	private static boolean hasActiveBeaconTarget(ServerPlayer player) {
		if (player.getRespawnPosition() == null) {
			return false;
		}
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			ResourceLocation id = BuiltInRegistries.ITEM.getKey(inv.get(slot).getItem());
			if (BEACON_FOCUS.equals(id)) {
				return true;
			}
		}
		return false;
	}

	private void applyTracker(UUID playerId, ItemStack compass, LodestoneTracker tracker) {
		LodestoneTracker current = compass.get(DataComponents.LODESTONE_TRACKER);
		TrackerSnapshot snapshot = snapshotFor(playerId, compass);
		if (snapshot != null && !Objects.equals(current, snapshot.focusTracker)) {
			restoreNameIfStillFocusName(compass, snapshot);
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
			compass.set(DataComponents.CUSTOM_NAME, WAYSTONE_COMPASS_NAME);
			return;
		}
		if (!Objects.equals(current, tracker)) {
			compass.set(DataComponents.LODESTONE_TRACKER, tracker);
		}
		if (!Objects.equals(compass.get(DataComponents.CUSTOM_NAME), WAYSTONE_COMPASS_NAME)) {
			compass.set(DataComponents.CUSTOM_NAME, WAYSTONE_COMPASS_NAME);
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
		if (!compass.is(Items.COMPASS)) {
			return;
		}
		if (!Objects.equals(compass.get(DataComponents.LODESTONE_TRACKER), snapshot.focusTracker)) {
			restoreNameIfStillFocusName(compass, snapshot);
			return;
		}
		if (snapshot.hadOriginal) {
			compass.set(DataComponents.LODESTONE_TRACKER, snapshot.originalTracker);
		} else {
			compass.remove(DataComponents.LODESTONE_TRACKER);
		}
		restoreName(compass, snapshot);
	}

	private static boolean canTrack(ItemStack stack) {
		return stack.is(Items.COMPASS);
	}

	private static void restoreNameIfStillFocusName(ItemStack compass, TrackerSnapshot snapshot) {
		if (Objects.equals(compass.get(DataComponents.CUSTOM_NAME), WAYSTONE_COMPASS_NAME)) {
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
		private LodestoneTracker focusTracker;

		private TrackerSnapshot(
				LodestoneTracker originalTracker,
				boolean hadOriginal,
				Component originalName,
				boolean hadOriginalName,
				LodestoneTracker focusTracker) {
			this.originalTracker = originalTracker;
			this.hadOriginal = hadOriginal;
			this.originalName = originalName;
			this.hadOriginalName = hadOriginalName;
			this.focusTracker = focusTracker;
		}
	}
}
