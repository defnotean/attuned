package dev.attuned.menu;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedRegistries;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.attunement.FocusHolder;
import dev.attuned.attunement.FocusPreset;
import dev.attuned.content.AttunedComponents;
import dev.attuned.content.AttunedContent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative save, apply, and delete handlers for Focus presets. */
public final class PresetNetworking {
	private static boolean initialized;
	private static final int APPLY_COOLDOWN_TICKS = 5;
	private static final Map<UUID, Long> LAST_APPLY_TICK = new HashMap<>();

	private PresetNetworking() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		PayloadTypeRegistry.serverboundPlay().register(SavePresetPayload.TYPE, SavePresetPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ApplyPresetPayload.TYPE, ApplyPresetPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DeletePresetPayload.TYPE, DeletePresetPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SavePresetPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			player.level().getServer().execute(() -> savePreset(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(ApplyPresetPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			player.level().getServer().execute(() -> applyPreset(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(DeletePresetPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			player.level().getServer().execute(() -> deletePreset(player, payload));
		});
		AttunedPlayerCleanup.onForget(LAST_APPLY_TICK::remove);
		AttunedServerCleanup.onStop(LAST_APPLY_TICK::clear);
	}

	private static void savePreset(ServerPlayer player, SavePresetPayload payload) {
		if (!hasOpenLiveSatchel(player)) {
			return;
		}
		List<String> ids = new ArrayList<>(AttunedInv.SIZE);
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int i = 0; i < AttunedInv.SIZE; i++) {
			ItemStack stack = inv.get(i);
			if (stack.isEmpty() || Attunement.definitionFor(player, stack).isEmpty()) {
				ids.add("");
			} else {
				ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
			}
		}
		AttunedAttachments.savePreset(player, new FocusPreset(payload.name(), ids));
	}

	private static void applyPreset(ServerPlayer player, ApplyPresetPayload payload) {
		if (!hasOpenLiveSatchel(player)) {
			return;
		}
		List<FocusPreset> presets = AttunedAttachments.getPresets(player);
		if (payload.index() < 0 || payload.index() >= presets.size()) {
			return;
		}
		UUID id = player.getUUID();
		long now = player.level().getGameTime();
		Long last = LAST_APPLY_TICK.get(id);
		if (last != null && now - last < APPLY_COOLDOWN_TICKS) {
			return;
		}

		Registry<FocusDefinition> registry =
			player.level().registryAccess().lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS);
		Set<String> registeredFocusIds = registeredFocusIds(registry);
		SatchelState satchel = satchelState(player);
		Map<String, Integer> inventoryCounts = inventoryFocusCounts(player, registeredFocusIds);
		PresetApplicationResolver.Result result = PresetApplicationResolver.apply(
			presets.get(payload.index()).slots(),
			equippedIds(player),
			satchel.ids(),
			inventoryCounts,
			registeredFocusIds);

		List<ItemStack> currentEquippedStacks = equippedStacks(player);
		Map<String, Deque<ItemStack>> satchelStacks =
			availableSatchelStacks(satchel, registeredFocusIds);
		Map<String, Deque<ItemStack>> displacedEquippedStacks =
			availableDisplacedEquippedStacks(currentEquippedStacks, result.equips(), registeredFocusIds);
		Map<String, Deque<InventoryFocus>> inventoryStacks =
			availableInventoryStacks(player, registeredFocusIds);
		List<InventoryFocus> consumedInventory = new ArrayList<>();
		List<ItemStack> equippedStacks = materializeEquippedStacks(result.equips(),
			currentEquippedStacks, satchelStacks, displacedEquippedStacks, inventoryStacks, consumedInventory);
		List<ItemStack> residualSatchel = materializeResidualSatchel(result.satchel(),
			satchelStacks, displacedEquippedStacks);

		for (int slot = 0; slot < AttunedInv.SIZE; slot++) {
			AttunedAttachments.setSlot(player, slot, equippedStacks.get(slot));
		}
		if (!satchel.stack().isEmpty()) {
			satchel.stack().set(AttunedComponents.SATCHEL_CONTENTS,
				new FocusHolder(AttunedComponents.SATCHEL_SIZE, 1, residualSatchel));
		}
		removeConsumedInventory(player, consumedInventory);
		returnOverflowToInventory(player, satchelStacks);
		returnOverflowToInventory(player, displacedEquippedStacks);
		LAST_APPLY_TICK.put(id, now);
		if (player.containerMenu instanceof SatchelMenu menu) {
			menu.broadcastChanges();
		}
		if (!result.missing().isEmpty()) {
			player.sendSystemMessage(Component.translatable(
				"screen.attuned.preset.missing", String.join(", ", result.missing()))
				.withStyle(ChatFormatting.RED));
		}
	}

	private static void deletePreset(ServerPlayer player, DeletePresetPayload payload) {
		if (!hasOpenLiveSatchel(player)) {
			return;
		}
		List<FocusPreset> presets = AttunedAttachments.getPresets(player);
		if (payload.index() < 0 || payload.index() >= presets.size()) {
			return;
		}
		if (!presets.get(payload.index()).name().equals(payload.name())) {
			return;
		}
		AttunedAttachments.deletePreset(player, payload.index());
	}

	private static boolean hasOpenLiveSatchel(ServerPlayer player) {
		return player.containerMenu instanceof SatchelMenu menu
			&& player.getItemInHand(menu.hand()).getItem() == AttunedContent.SATCHEL_OF_FOCI;
	}

	private static Set<String> registeredFocusIds(Registry<FocusDefinition> registry) {
		Set<String> ids = new HashSet<>();
		registry.stream()
			.map(def -> BuiltInRegistries.ITEM.getKey(def.item().value()).toString())
			.forEach(ids::add);
		return ids;
	}

	private static List<String> equippedIds(ServerPlayer player) {
		List<String> ids = new ArrayList<>(AttunedInv.SIZE);
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int i = 0; i < AttunedInv.SIZE; i++) {
			ItemStack stack = inv.get(i);
			ids.add(stack.isEmpty() || Attunement.definitionFor(player, stack).isEmpty()
				? ""
				: BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		}
		return ids;
	}

	private static SatchelState satchelState(ServerPlayer player) {
		if (!(player.containerMenu instanceof SatchelMenu menu)) {
			return new SatchelState(ItemStack.EMPTY, List.of(), List.of());
		}
		ItemStack satchel = player.getItemInHand(menu.hand());
		if (satchel.getItem() != AttunedContent.SATCHEL_OF_FOCI) {
			return new SatchelState(ItemStack.EMPTY, List.of(), List.of());
		}
		FocusHolder holder = satchel.get(AttunedComponents.SATCHEL_CONTENTS);
		if (holder == null) {
			holder = AttunedComponents.emptyContents();
		}
		List<String> ids = new ArrayList<>(AttunedComponents.SATCHEL_SIZE);
		List<ItemStack> stacks = new ArrayList<>(AttunedComponents.SATCHEL_SIZE);
		for (int i = 0; i < AttunedComponents.SATCHEL_SIZE; i++) {
			ItemStack stack = holder.get(i);
			String id = idFor(stack);
			ids.add(id);
			stacks.add(stack);
		}
		return new SatchelState(satchel, ids, stacks);
	}

	private static Map<String, Integer> inventoryFocusCounts(ServerPlayer player, Set<String> registeredFocusIds) {
		Map<String, Integer> counts = new HashMap<>();
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			String id = idFor(stack);
			if (!id.isEmpty() && registeredFocusIds.contains(id)) {
				counts.merge(id, stack.getCount(), Integer::sum);
			}
		}
		return counts;
	}

	private static String idFor(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static List<ItemStack> equippedStacks(ServerPlayer player) {
		List<ItemStack> stacks = new ArrayList<>(AttunedInv.SIZE);
		AttunedInv inv = AttunedAttachments.getInventory(player);
		for (int slot = 0; slot < AttunedInv.SIZE; slot++) {
			ItemStack stack = inv.get(slot);
			stacks.add(stack.copy());
		}
		return stacks;
	}

	private static Map<String, Deque<ItemStack>> availableSatchelStacks(
			SatchelState satchel, Set<String> registeredFocusIds) {
		Map<String, Deque<ItemStack>> stacks = new HashMap<>();
		for (ItemStack stack : satchel.stacks()) {
			addStack(stacks, stack);
		}
		return stacks;
	}

	private static Map<String, Deque<ItemStack>> availableDisplacedEquippedStacks(
			List<ItemStack> currentEquippedStacks, List<String> targetIds, Set<String> registeredFocusIds) {
		Map<String, Deque<ItemStack>> stacks = new HashMap<>();
		for (int slot = 0; slot < currentEquippedStacks.size(); slot++) {
			ItemStack stack = currentEquippedStacks.get(slot);
			String targetId = slot < targetIds.size() ? targetIds.get(slot) : "";
			if (idFor(stack).equals(targetId)) {
				continue;
			}
			addStack(stacks, stack);
		}
		return stacks;
	}

	private static Map<String, Deque<InventoryFocus>> availableInventoryStacks(
			ServerPlayer player, Set<String> registeredFocusIds) {
		Map<String, Deque<InventoryFocus>> stacks = new HashMap<>();
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			String id = idFor(stack);
			if (!registeredFocusIds.contains(id)) {
				continue;
			}
			for (int count = 0; count < stack.getCount(); count++) {
				ItemStack copy = stack.copy();
				copy.setCount(1);
				stacks.computeIfAbsent(id, ignored -> new ArrayDeque<>())
					.addLast(new InventoryFocus(slot, copy));
			}
		}
		return stacks;
	}

	private static List<ItemStack> materializeEquippedStacks(List<String> ids,
			List<ItemStack> currentEquippedStacks,
			Map<String, Deque<ItemStack>> satchelStacks,
			Map<String, Deque<ItemStack>> displacedEquippedStacks,
			Map<String, Deque<InventoryFocus>> inventoryStacks,
			List<InventoryFocus> consumedInventory) {
		List<ItemStack> stacks = new ArrayList<>(ids.size());
		for (int slot = 0; slot < ids.size(); slot++) {
			String id = ids.get(slot);
			ItemStack stack = matchingEquippedStack(currentEquippedStacks, slot, id);
			if (stack.isEmpty()) {
				stack = takeStack(satchelStacks, id);
			}
			if (stack.isEmpty()) {
				stack = takeStack(displacedEquippedStacks, id);
			}
			if (stack.isEmpty()) {
				InventoryFocus focus = takeInventoryStack(inventoryStacks, id);
				if (focus != null) {
					consumedInventory.add(focus);
					stack = focus.stack();
				}
			}
			stacks.add(stack);
		}
		return stacks;
	}

	private static List<ItemStack> materializeResidualSatchel(List<String> ids,
			Map<String, Deque<ItemStack>> satchelStacks,
			Map<String, Deque<ItemStack>> displacedEquippedStacks) {
		List<ItemStack> stacks = new ArrayList<>(ids.size());
		for (String id : ids) {
			ItemStack stack = takeStack(satchelStacks, id);
			if (stack.isEmpty()) {
				stack = takeStack(displacedEquippedStacks, id);
			}
			stacks.add(stack);
		}
		return stacks;
	}

	private static ItemStack matchingEquippedStack(List<ItemStack> currentEquippedStacks, int slot, String id) {
		if (id == null || id.isBlank() || slot < 0 || slot >= currentEquippedStacks.size()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = currentEquippedStacks.get(slot);
		return id.equals(idFor(stack)) ? stack.copy() : ItemStack.EMPTY;
	}

	private static void addStack(Map<String, Deque<ItemStack>> stacks, ItemStack stack) {
		String id = idFor(stack);
		if (id.isEmpty()) {
			return;
		}
		stacks.computeIfAbsent(id, ignored -> new ArrayDeque<>()).addLast(stack.copy());
	}

	private static ItemStack takeStack(Map<String, Deque<ItemStack>> stacks, String id) {
		if (id == null || id.isBlank()) {
			return ItemStack.EMPTY;
		}
		Deque<ItemStack> matches = stacks.get(id);
		if (matches == null || matches.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return matches.removeFirst();
	}

	private static InventoryFocus takeInventoryStack(Map<String, Deque<InventoryFocus>> stacks, String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		Deque<InventoryFocus> matches = stacks.get(id);
		return matches == null || matches.isEmpty() ? null : matches.removeFirst();
	}

	private static void removeConsumedInventory(ServerPlayer player, List<InventoryFocus> consumedInventory) {
		if (consumedInventory.isEmpty()) {
			return;
		}
		Map<Integer, Integer> consumedBySlot = new HashMap<>();
		for (InventoryFocus focus : consumedInventory) {
			consumedBySlot.merge(focus.slot(), 1, Integer::sum);
		}
		Inventory inventory = player.getInventory();
		for (Map.Entry<Integer, Integer> entry : consumedBySlot.entrySet()) {
			ItemStack stack = inventory.getItem(entry.getKey());
			stack.shrink(Math.min(entry.getValue(), stack.getCount()));
		}
	}

	private static void returnOverflowToInventory(ServerPlayer player, Map<String, Deque<ItemStack>> satchelStacks) {
		for (Deque<ItemStack> stacks : satchelStacks.values()) {
			while (!stacks.isEmpty()) {
				player.getInventory().placeItemBackInInventory(stacks.removeFirst());
			}
		}
	}

	private record SatchelState(ItemStack stack, List<String> ids, List<ItemStack> stacks) {}
	private record InventoryFocus(int slot, ItemStack stack) {}
}
