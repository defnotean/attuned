package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.network.ActionBarMessages;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.CustomData;

/** Offshore Harpoon Focus: summons one temporary custom trident on the ability key. */
public final class HarpoonBehavior implements FocusBehavior {
	static final int COOLDOWN_TICKS = 1200;
	// A summoned harpoon — held or thrown and stuck in the world — lives for exactly one
	// cooldown, then despawns, so it disappears precisely when the ability becomes ready again.
	static final int LIFETIME_TICKS = COOLDOWN_TICKS;
	private static final int ENTITY_CLEANUP_INTERVAL_TICKS = 20;
	private static final String MARKER_ID = "attuned:offshore_harpoon";
	private static final String MARKER_KEY = "marker";
	private static final String OWNER_KEY = "owner";
	private static final String EXPIRES_AT_KEY = "expires_at";
	private static final int CUSTOM_MODEL_DATA = 7123001;
	private static final Component HARPOON_NAME =
		Component.translatable("item.attuned.ocean_relic_trident");
	private static final Map<UUID, Long> ACTIVE_HARPOONS = new HashMap<>();
	private static boolean initialized;

	public HarpoonBehavior() {
		initLifecycle();
	}

	@Override
	public boolean hasActiveAbility() {
		return true;
	}

	@Override
	public int abilityCooldownTicks() {
		return COOLDOWN_TICKS;
	}

	@Override
	public boolean onAbility(ServerPlayer player, ItemStack focus) {
		long now = player.level().getGameTime();
		removeInvalidInventoryHarpoons(player, now);
		if (ACTIVE_HARPOONS.getOrDefault(player.getUUID(), -1L) > now) {
			ActionBarMessages.send(player, ActionBarMessages.Priority.WARNING,
				Component.translatable("item.attuned.harpoon_focus.active"));
			return false;
		}

		ItemStack harpoon = createHarpoon(player, now);
		if (!placeHarpoon(player, harpoon)) {
			ActionBarMessages.send(player, ActionBarMessages.Priority.WARNING,
				Component.translatable("item.attuned.harpoon_focus.no_space"));
			return false;
		}

		ACTIVE_HARPOONS.put(player.getUUID(), now + LIFETIME_TICKS);
		player.level().playSound(null, player.blockPosition(),
			SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.75F, 0.85F);
		return true;
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		removeInvalidInventoryHarpoons(player, player.level().getGameTime());
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		removeForPlayer(player);
	}

	public static boolean isTemporaryHarpoon(ItemStack stack) {
		return temporaryHarpoonTag(stack) != null;
	}

	public static boolean shouldDiscardProjectile(ItemStack stack, long now) {
		CompoundTag tag = temporaryHarpoonTag(stack);
		return tag != null && expiresAt(tag) <= now;
	}

	private static void initLifecycle() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(HarpoonBehavior::tickServer);
		AttunedServerCleanup.onStopServer(HarpoonBehavior::removeAllTemporaryHarpoons);
		AttunedPlayerCleanup.onForgetPlayer(HarpoonBehavior::removeForPlayer);
	}

	private static ItemStack createHarpoon(ServerPlayer player, long now) {
		ItemStack harpoon = new ItemStack(Items.TRIDENT);
		CompoundTag tag = new CompoundTag();
		tag.putString(MARKER_KEY, MARKER_ID);
		tag.putString(OWNER_KEY, player.getUUID().toString());
		tag.putLong(EXPIRES_AT_KEY, now + LIFETIME_TICKS);
		harpoon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		harpoon.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(CUSTOM_MODEL_DATA));
		harpoon.set(DataComponents.CUSTOM_NAME, HARPOON_NAME);
		harpoon.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
		return harpoon;
	}

	private static boolean placeHarpoon(ServerPlayer player, ItemStack harpoon) {
		if (player.getMainHandItem().isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, harpoon);
			return true;
		}

		Inventory inventory = player.getInventory();
		int freeSlot = inventory.getFreeSlot();
		if (freeSlot < 0) {
			return false;
		}
		inventory.setItem(freeSlot, player.getMainHandItem().copy());
		player.setItemInHand(InteractionHand.MAIN_HAND, harpoon);
		return true;
	}

	private static void tickServer(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (ACTIVE_HARPOONS.isEmpty()) {
			return;
		}
		cleanupActiveInventories(server, now);
		if (shouldSweepEntities(now)) {
			cleanupTransferredInventories(server, now);
			cleanupEntities(server, now);
		}
		pruneActiveHarpoons(now);
	}

	private static void cleanupActiveInventories(MinecraftServer server, long now) {
		for (UUID owner : List.copyOf(ACTIVE_HARPOONS.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player != null) {
				removeInvalidInventoryHarpoons(player, now);
			}
		}
	}

	private static boolean shouldSweepEntities(long now) {
		return now % ENTITY_CLEANUP_INTERVAL_TICKS == 0L;
	}

	private static void cleanupTransferredInventories(MinecraftServer server, long now) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			removeInventoryHarpoons(player, player.getUUID(), now, false);
		}
	}

	private static void pruneActiveHarpoons(long now) {
		Iterator<Map.Entry<UUID, Long>> entries = ACTIVE_HARPOONS.entrySet().iterator();
		while (entries.hasNext()) {
			if (entries.next().getValue() + ENTITY_CLEANUP_INTERVAL_TICKS <= now) {
				entries.remove();
			}
		}
	}

	private static void removeAllTemporaryHarpoons(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			removeInventoryHarpoons(player, player.getUUID(), Long.MAX_VALUE, true);
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity item && isTemporaryHarpoon(item.getItem())) {
					item.discard();
				} else if (entity instanceof ThrownTrident trident
						&& isTemporaryHarpoon(trident.getPickupItemStackOrigin())) {
					trident.discard();
				}
			}
		}
		ACTIVE_HARPOONS.clear();
	}

	private static void removeForPlayer(ServerPlayer player) {
		UUID owner = player.getUUID();
		removeInventoryHarpoons(player, owner, Long.MAX_VALUE, true);
		if (player.level() instanceof ServerLevel level) {
			removePlayerInventoriesForOwner(level.getServer(), owner);
			removeEntitiesForOwner(level.getServer(), owner);
		}
		ACTIVE_HARPOONS.remove(owner);
	}

	private static void removeInvalidInventoryHarpoons(ServerPlayer player, long now) {
		UUID owner = player.getUUID();
		removeInventoryHarpoons(player, owner, now, false);
	}

	private static void removeInventoryHarpoons(ServerPlayer player, UUID owner, long now, boolean force) {
		removeMarkedStack(player.getMainHandItem(), owner, now, force, true);
		removeMarkedStack(player.getOffhandItem(), owner, now, force, true);
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			removeMarkedStack(inventory.getItem(slot), owner, now, force, true);
		}
	}

	private static void removePlayerInventoriesForOwner(MinecraftServer server, UUID owner) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			removeInventoryHarpoonsForOwner(player, owner, Long.MAX_VALUE, true);
		}
	}

	private static void removeInventoryHarpoonsForOwner(ServerPlayer player, UUID owner, long now, boolean force) {
		removeMarkedStack(player.getMainHandItem(), owner, now, force, false);
		removeMarkedStack(player.getOffhandItem(), owner, now, force, false);
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			removeMarkedStack(inventory.getItem(slot), owner, now, force, false);
		}
	}

	private static void removeMarkedStack(ItemStack stack, UUID owner, long now, boolean force, boolean removeForeignOwner) {
		CompoundTag tag = temporaryHarpoonTag(stack);
		if (tag == null) {
			return;
		}
		UUID stackOwner = ownerOf(tag);
		if ((owner.equals(stackOwner) && (force || expiresAt(tag) <= now))
				|| (removeForeignOwner && !owner.equals(stackOwner))) {
			stack.shrink(stack.getCount());
		}
	}

	private static void cleanupEntities(MinecraftServer server, long now) {
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity item && shouldDiscardProjectile(item.getItem(), now)) {
					item.discard();
				} else if (entity instanceof ThrownTrident trident
						&& shouldDiscardProjectile(trident.getPickupItemStackOrigin(), now)) {
					trident.discard();
				}
			}
		}
	}

	private static void removeEntitiesForOwner(MinecraftServer server, UUID owner) {
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity item && isOwnedTemporaryHarpoon(item.getItem(), owner)) {
					item.discard();
				} else if (entity instanceof ThrownTrident trident
						&& isOwnedTemporaryHarpoon(trident.getPickupItemStackOrigin(), owner)) {
					trident.discard();
				}
			}
		}
	}

	private static boolean isOwnedTemporaryHarpoon(ItemStack stack, UUID owner) {
		CompoundTag tag = temporaryHarpoonTag(stack);
		return tag != null && owner.equals(ownerOf(tag));
	}

	private static CompoundTag temporaryHarpoonTag(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.is(Items.TRIDENT)) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag tag = data.copyTag();
		return tag.contains(MARKER_KEY) && MARKER_ID.equals(tag.getString(MARKER_KEY)) ? tag : null;
	}

	private static UUID ownerOf(CompoundTag tag) {
		String value = tag.contains(OWNER_KEY) ? tag.getString(OWNER_KEY) : "";
		try {
			return value.isBlank() ? null : UUID.fromString(value);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static long expiresAt(CompoundTag tag) {
		return tag.contains(EXPIRES_AT_KEY) ? tag.getLong(EXPIRES_AT_KEY) : -1L;
	}
}
