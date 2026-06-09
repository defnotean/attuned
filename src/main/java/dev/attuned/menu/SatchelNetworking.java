package dev.attuned.menu;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.attunement.FocusHolder;
import dev.attuned.content.AttunedComponents;
import dev.attuned.content.AttunedContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative receiver for satchel focus moves. */
public final class SatchelNetworking {
	private static boolean initialized;
	private static final int MOVE_COOLDOWN_TICKS = 2;
	private static final Map<UUID, Long> LAST_MOVE_TICK = new HashMap<>();

	private SatchelNetworking() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		PayloadTypeRegistry.serverboundPlay().register(MoveFocusPayload.TYPE, MoveFocusPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MoveFocusPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			player.level().getServer().execute(() -> tryMove(player, payload));
		});
		AttunedPlayerCleanup.onForget(LAST_MOVE_TICK::remove);
		AttunedServerCleanup.onStop(LAST_MOVE_TICK::clear);
	}

	private static void tryMove(ServerPlayer player, MoveFocusPayload payload) {
		if (!(player.containerMenu instanceof SatchelMenu menu)) {
			return;
		}
		if (payload.direction() < 0 || payload.satchelSlot() < 0 || payload.equippedSlot() < 0
				|| payload.direction() > 1) {
			return;
		}
		ItemStack satchel = player.getItemInHand(menu.hand());
		if (satchel.getItem() != AttunedContent.SATCHEL_OF_FOCI) {
			return;
		}
		UUID id = player.getUUID();
		long now = player.level().getGameTime();
		Long last = LAST_MOVE_TICK.get(id);
		if (last != null && now - last < MOVE_COOLDOWN_TICKS) {
			return;
		}

		FocusHolder holder = satchel.get(AttunedComponents.SATCHEL_CONTENTS);
		if (holder == null) {
			holder = AttunedComponents.emptyContents();
		}
		List<Optional<String>> satchelIds = satchelIds(player, holder);
		List<Optional<String>> equippedIds = equippedIds(player);
		SatchelMoveResolver.Move move = payload.direction() == 0
			? SatchelMoveResolver.satchelToEquipped(satchelIds, equippedIds, payload.satchelSlot(), payload.equippedSlot())
			: SatchelMoveResolver.equippedToSatchel(satchelIds, equippedIds, payload.equippedSlot(), payload.satchelSlot());
		if (!move.applied()) {
			return;
		}

		if (payload.direction() == 0) {
			ItemStack source = holder.get(payload.satchelSlot()).copy();
			ItemStack displaced = AttunedAttachments.getInventory(player).get(payload.equippedSlot()).copy();
			AttunedAttachments.setSlot(player, payload.equippedSlot(), source);
			// Defense in depth: only mutate the satchel once the equip has actually landed.
			// The resolver and setSlot share the same definitionFor() gate, so a divergence is
			// not currently reachable, but guarding the write guarantees a focus can never end
			// up duplicated across both the satchel slot and the equipped slot.
			if (!ItemStack.isSameItemSameComponents(
					AttunedAttachments.getInventory(player).get(payload.equippedSlot()), source)) {
				return;
			}
			satchel.set(AttunedComponents.SATCHEL_CONTENTS,
				holder.with(move.satchelSlot(), move.satchelWrite().isPresent() ? displaced : ItemStack.EMPTY));
		} else {
			ItemStack source = AttunedAttachments.getInventory(player).get(payload.equippedSlot()).copy();
			AttunedAttachments.setSlot(player, payload.equippedSlot(), ItemStack.EMPTY);
			satchel.set(AttunedComponents.SATCHEL_CONTENTS,
				holder.with(move.satchelSlot(), source));
		}
		LAST_MOVE_TICK.put(id, now);
		menu.broadcastChanges();
	}

	private static List<Optional<String>> satchelIds(ServerPlayer player, FocusHolder holder) {
		List<Optional<String>> ids = new ArrayList<>(AttunedComponents.SATCHEL_SIZE);
		for (int i = 0; i < AttunedComponents.SATCHEL_SIZE; i++) {
			ids.add(focusId(player, holder.get(i)));
		}
		return List.copyOf(ids);
	}

	private static List<Optional<String>> equippedIds(ServerPlayer player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		List<Optional<String>> ids = new ArrayList<>(AttunedInv.SIZE);
		for (int i = 0; i < AttunedInv.SIZE; i++) {
			ids.add(focusId(player, inv.get(i)));
		}
		return List.copyOf(ids);
	}

	private static Optional<String> focusId(ServerPlayer player, ItemStack stack) {
		if (stack.isEmpty() || Attunement.definitionFor(player, stack).isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
	}
}
