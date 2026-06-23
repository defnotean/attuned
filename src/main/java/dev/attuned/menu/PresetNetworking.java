package dev.attuned.menu;

import dev.attuned.Attuned;
import dev.attuned.AttunedConfig;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedRegistries;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.attunement.BudgetResolver;
import dev.attuned.attunement.FocusHolder;
import dev.attuned.attunement.FocusPreset;
import dev.attuned.content.AttunedComponents;
import dev.attuned.content.AttunedContent;
import dev.attuned.menu.PartySetupSuggestions.MemberRole;
import dev.attuned.menu.PresetMetadataResolver.ResolvedFocus;
import dev.attuned.network.ActionBarMessages;
import dev.attuned.party.CircleRuntime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
		ServerPlayNetworking.registerGlobalReceiver(SavePresetPayload.TYPE, (payload, player, sender) -> {
			player.level().getServer().execute(() -> savePreset(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(ApplyPresetPayload.TYPE, (payload, player, sender) -> {
			player.level().getServer().execute(() -> applyPreset(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(DeletePresetPayload.TYPE, (payload, player, sender) -> {
			player.level().getServer().execute(() -> deletePreset(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(QuickApplyPresetPayload.TYPE, (payload, player, sender) -> {
			player.level().getServer().execute(() -> quickApplyPreset(player, payload));
		});
		ServerPlayNetworking.registerGlobalReceiver(ImportPresetPayload.TYPE, (payload, player, sender) -> {
			player.level().getServer().execute(() -> importPreset(player, payload));
		});
		AttunedPlayerCleanup.onForget(LAST_APPLY_TICK::remove);
		AttunedServerCleanup.onStop(LAST_APPLY_TICK::clear);
	}

	private static void importPreset(ServerPlayer player, ImportPresetPayload payload) {
		if (!hasOpenLiveSatchel(player)) {
			return;
		}
		Registry<FocusDefinition> registry =
			player.level().registryAccess().registryOrThrow(AttunedRegistries.FOCUS_DEFINITIONS);
		PresetImportValidator.Result validation =
			PresetImportValidator.validate(payload.preset(), registeredFocusIds(registry));
		if (!validation.missing().isEmpty()) {
			player.sendSystemMessage(Component.translatable(
				"screen.attuned.preset.import_missing", String.join(", ", validation.missing()))
				.withStyle(ChatFormatting.RED));
			return;
		}
		FocusPreset preset = PresetMetadataResolver.withWarningsEnabled(payload.preset(),
			AttunedConfig.get().partySetupSuggestionsEnabled());
		AttunedAttachments.savePreset(player, preset);
		ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS,
			Component.translatable("screen.attuned.preset.imported", preset.name()));
	}

	private static void savePreset(ServerPlayer player, SavePresetPayload payload) {
		if (!hasOpenLiveSatchel(player)) {
			return;
		}
		List<String> ids = new ArrayList<>(AttunedInv.SIZE);
		List<ResolvedFocus> facts = new ArrayList<>(AttunedInv.SIZE);
		AttunedInv inv = AttunedAttachments.getInventory(player);
		BudgetResolver.Resolution resolution = Attunement.resolution(player);
		for (int i = 0; i < AttunedInv.SIZE; i++) {
			ItemStack stack = inv.get(i);
			Optional<FocusDefinition> definition = Attunement.definitionFor(player, stack);
			if (stack.isEmpty() || definition.isEmpty()) {
				ids.add("");
			} else {
				ids.add(keyFor(stack));
				Optional<BudgetResolver.DormantReason> dormantReason =
					Optional.ofNullable(resolution.dormantReasons().get(i));
				boolean activeAbility = dormantReason.isEmpty()
					&& hasActiveAbility(player, definition.get(), stack);
				facts.add(new ResolvedFocus(
					definition.get().affinity(),
					definition.get().faction(),
					activeAbility,
					dormantReason));
			}
		}
		FocusPreset.SetupMetadata metadata = PresetMetadataResolver.infer(facts,
			AttunedConfig.get().partySetupSuggestionsEnabled());
		metadata = withPartyWarnings(metadata, partySetupWarnings(player, metadata.role()));
		AttunedAttachments.savePreset(player, new FocusPreset(payload.name(), ids, metadata));
	}

	private static FocusPreset.SetupMetadata withPartyWarnings(FocusPreset.SetupMetadata metadata,
			List<String> warnings) {
		if (warnings.isEmpty()) {
			return metadata;
		}
		List<String> merged = new ArrayList<>(metadata.warnings());
		merged.addAll(warnings);
		return new FocusPreset.SetupMetadata(
			metadata.role(),
			metadata.note(),
			metadata.preferredPartySize(),
			merged,
			metadata.requires(),
			metadata.minecraftVersion(),
			metadata.attunedVersion());
	}

	private static List<String> partySetupWarnings(ServerPlayer player, String candidateRole) {
		if (!AttunedConfig.get().partySetupSuggestionsEnabled()) {
			return List.of();
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return List.of();
		}
		return CircleRuntime.manager().circleOf(player.getUUID())
			.map(circle -> {
				List<MemberRole> roles = new ArrayList<>(circle.members().size());
				for (UUID member : circle.members()) {
					ServerPlayer memberPlayer = server.getPlayerList().getPlayer(member);
					if (memberPlayer != null) {
						roles.add(new MemberRole(member,
							member.equals(player.getUUID()) ? candidateRole : Attunement.publicRole(memberPlayer)));
					}
				}
				return PartySetupSuggestions.warnings(player.getUUID(), candidateRole, roles);
			})
			.orElse(List.of());
	}

	private static boolean hasActiveAbility(ServerPlayer player, FocusDefinition definition, ItemStack stack) {
		ResourceLocation behaviorId = definition.behavior().orElse(null);
		if (behaviorId == null) {
			return false;
		}
		FocusBehavior behavior = AttunedRegistries.getBehavior(behaviorId, player.level().registryAccess());
		try {
			return behavior != null && behavior.hasActiveAbility();
		} catch (RuntimeException e) {
			Attuned.LOGGER.warn("Attuned setup metadata ability check failed for {} using {} ({})",
				player.getUUID(), stack.getItem(), behavior.getClass().getName(), e);
			return false;
		}
	}

	private static void applyPreset(ServerPlayer player, ApplyPresetPayload payload) {
		if (!hasOpenLiveSatchel(player)) {
			return;
		}
		SatchelState satchel = satchelState(player);
		applyPresetCore(player, payload.index(), satchel);
	}

	/**
	 * Hotkey path: no open menu required. The reliquary is sourced from the
	 * player's inventory (first Focus Reliquary found); equipped and loose
	 * inventory Foci still participate exactly like the menu apply.
	 */
	private static void quickApplyPreset(ServerPlayer player, QuickApplyPresetPayload payload) {
		applyPresetCore(player, payload.index(), inventorySatchelState(player));
	}

	private static void applyPresetCore(ServerPlayer player, int index, SatchelState satchel) {
		List<FocusPreset> presets = AttunedAttachments.getPresets(player);
		if (index < 0 || index >= presets.size()) {
			return;
		}
		UUID id = player.getUUID();
		long now = player.level().getGameTime();
		Long last = LAST_APPLY_TICK.get(id);
		if (last != null && now - last < APPLY_COOLDOWN_TICKS) {
			ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS,
				Component.translatable("screen.attuned.preset.cooldown"));
			return;
		}

		Registry<FocusDefinition> registry =
			player.level().registryAccess().registryOrThrow(AttunedRegistries.FOCUS_DEFINITIONS);
		Set<String> registeredFocusIds = registeredFocusIds(registry);
		Map<String, Integer> inventoryCounts = inventoryFocusCounts(player, registeredFocusIds);
		PresetApplicationResolver.Result result = PresetApplicationResolver.applyAllOrNothing(
			presets.get(index).slots(),
			equippedIds(player),
			satchel.ids(),
			inventoryCounts,
			registeredFocusIds);
		if (!result.missing().isEmpty()) {
			player.sendSystemMessage(Component.translatable(
				"screen.attuned.preset.missing", String.join(", ", result.missing()))
				.withStyle(ChatFormatting.RED));
			return;
		}

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
		if (hasOverflow(satchelStacks) || hasOverflow(displacedEquippedStacks)) {
			player.sendSystemMessage(Component.translatable(
				"screen.attuned.preset.missing", String.join(", ", overflowIds(satchelStacks, displacedEquippedStacks)))
				.withStyle(ChatFormatting.RED));
			return;
		}

		for (int slot = 0; slot < AttunedInv.SIZE; slot++) {
			AttunedAttachments.setSlot(player, slot, equippedStacks.get(slot));
		}
		if (!satchel.stack().isEmpty()) {
			ItemStack satchelStack = satchel.stack();
			AttunedComponents.setContents(satchelStack, isGrandReliquary(satchelStack),
				new FocusHolder(sizeOf(satchelStack), 1, residualSatchel));
		}
		removeConsumedInventory(player, consumedInventory);
		LAST_APPLY_TICK.put(id, now);
		if (player.containerMenu instanceof SatchelMenu menu) {
			menu.broadcastChanges();
		}
		ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS,
			Component.translatable("screen.attuned.preset.applied", presets.get(index).name()));
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
			&& isReliquary(player.getItemInHand(menu.hand()));
	}

	/** True for either reliquary tier: the small satchel or the Grand Focus Reliquary. */
	private static boolean isReliquary(ItemStack stack) {
		return stack.getItem() == AttunedContent.SATCHEL_OF_FOCI
			|| stack.getItem() == AttunedContent.GRAND_SATCHEL_OF_FOCI;
	}

	/** Grid size for the reliquary tier of this stack. */
	private static int sizeOf(ItemStack stack) {
		return stack.getItem() == AttunedContent.GRAND_SATCHEL_OF_FOCI
			? AttunedComponents.GRAND_SATCHEL_SIZE
			: AttunedComponents.SATCHEL_SIZE;
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
			ids.add(keyFor(stack));
		}
		return ids;
	}

	private static SatchelState satchelState(ServerPlayer player) {
		if (!(player.containerMenu instanceof SatchelMenu menu)) {
			return new SatchelState(ItemStack.EMPTY, List.of(), List.of());
		}
		ItemStack satchel = player.getItemInHand(menu.hand());
		if (!isReliquary(satchel)) {
			return new SatchelState(ItemStack.EMPTY, List.of(), List.of());
		}
		return satchelStateOf(satchel);
	}

	/**
	 * Hotkey-path reliquary lookup: the first Focus Reliquary (either tier) anywhere
	 * in the player's inventory. Empty state when the player carries none — the apply
	 * then sources from equipped and loose inventory Foci only.
	 */
	private static SatchelState inventorySatchelState(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (isReliquary(stack)) {
				return satchelStateOf(stack);
			}
		}
		return new SatchelState(ItemStack.EMPTY, List.of(), List.of());
	}

	private static SatchelState satchelStateOf(ItemStack satchel) {
		int size = sizeOf(satchel);
		FocusHolder holder = AttunedComponents.getContents(satchel, isGrandReliquary(satchel));
		List<String> ids = new ArrayList<>(size);
		List<ItemStack> stacks = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			ItemStack stack = holder.get(i);
			String id = keyFor(stack);
			ids.add(id);
			stacks.add(stack);
		}
		return new SatchelState(satchel, ids, stacks);
	}

	private static boolean isGrandReliquary(ItemStack stack) {
		return stack.getItem() == AttunedContent.GRAND_SATCHEL_OF_FOCI;
	}

	private static Map<String, Integer> inventoryFocusCounts(ServerPlayer player, Set<String> registeredFocusIds) {
		Map<String, Integer> counts = new HashMap<>();
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			String id = keyFor(stack);
			if (!id.isEmpty() && registeredFocusIds.contains(FocusPreset.slotId(id))) {
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
			if (keyFor(stack).equals(targetId)) {
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
			String id = keyFor(stack);
			if (!registeredFocusIds.contains(FocusPreset.slotId(id))) {
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
		return id.equals(keyFor(stack)) ? stack.copy() : ItemStack.EMPTY;
	}

	private static void addStack(Map<String, Deque<ItemStack>> stacks, ItemStack stack) {
		String id = keyFor(stack);
		if (id.isEmpty()) {
			return;
		}
		stacks.computeIfAbsent(id, ignored -> new ArrayDeque<>()).addLast(stack.copy());
	}

	private static String keyFor(ItemStack stack) {
		return FocusPreset.slotKey(idFor(stack), AttunedComponents.isTempered(stack));
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

	private static boolean hasOverflow(Map<String, Deque<ItemStack>> stacks) {
		for (Deque<ItemStack> queue : stacks.values()) {
			if (!queue.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static List<String> overflowIds(Map<String, Deque<ItemStack>> satchelStacks,
			Map<String, Deque<ItemStack>> displacedEquippedStacks) {
		List<String> ids = new ArrayList<>();
		collectOverflowIds(ids, satchelStacks);
		collectOverflowIds(ids, displacedEquippedStacks);
		return ids;
	}

	private static void collectOverflowIds(List<String> ids, Map<String, Deque<ItemStack>> stacks) {
		for (Map.Entry<String, Deque<ItemStack>> entry : stacks.entrySet()) {
			for (int i = 0; i < entry.getValue().size(); i++) {
				ids.add(entry.getKey());
			}
		}
	}

	private record SatchelState(ItemStack stack, List<String> ids, List<ItemStack> stacks) {}
	private record InventoryFocus(int slot, ItemStack stack) {}
}
