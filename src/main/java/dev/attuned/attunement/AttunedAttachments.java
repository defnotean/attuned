package dev.attuned.attunement;

import dev.attuned.AttunedConfig;
import dev.attuned.compat.NetworkPackets;
import dev.attuned.network.AttunedStatePayload;
import dev.attuned.pacts.Pact;
import dev.attuned.pacts.PactTrialProgress;
import dev.attuned.pacts.PactTrials;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Per-player attunement state for Minecraft versions before Fabric data attachments.
 * State is stored in a branch-local map and persisted by {@code PlayerAttunedStateMixin}.
 */
public final class AttunedAttachments {
	private static final String ROOT_KEY = "Attuned";
	private static final String CAPACITY_KEY = "Capacity";
	private static final String INVENTORY_KEY = "Inventory";
	private static final String PRESETS_KEY = "Presets";
	private static final String MILESTONES_KEY = "Milestones";
	private static final String RESONANCE_KEY = "Resonance";
	private static final String ONBOARDING_KEY = "Onboarding";
	private static final String PACT_TRIAL_PROGRESS_KEY = "PactTrialProgress";
	private static final String DISCOVERED_CONFLUENCES_KEY = "DiscoveredConfluences";

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static boolean initialized;

	private AttunedAttachments() {}

	public static final int MAX_PRESETS = 9;

	/** Per-pact trial progress surfaced in the journal; trials runtime fills this in. */
	public record PactTrialState(int progress, int goal, boolean tier4Complete) {}

	/** Installs the branch-local sync hook used before Fabric data attachments. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
	}

	public static void copy(Player from, Player to) {
		STATES.put(to.getUUID(), state(from).copy());
		sync(to);
	}

	public static void load(Player player, CompoundTag playerTag) {
		if (playerTag == null || !playerTag.contains(ROOT_KEY, Tag.TAG_COMPOUND)) {
			return;
		}
		STATES.put(player.getUUID(), State.fromTag(playerTag.getCompound(ROOT_KEY)));
	}

	public static void save(Player player, CompoundTag playerTag) {
		if (playerTag != null) {
			playerTag.put(ROOT_KEY, state(player).toTag());
		}
	}

	public static void applySync(Player player, CompoundTag tag) {
		if (player != null && tag != null) {
			STATES.put(player.getUUID(), State.fromTag(tag));
		}
	}

	public static void sync(Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			NetworkPackets.send(serverPlayer, new AttunedStatePayload(state(serverPlayer).toTag()));
		}
	}

	/**
	 * Synced trial progress per {@link Pact}. Returns an empty map when {@code player}
	 * is null; otherwise reads the branch-local pact progress state.
	 */
	public static Map<Pact, PactTrialState> getPactTrialProgress(Player player) {
		if (player == null) {
			return Map.of();
		}
		Map<Pact, PactTrialState> out = new EnumMap<>(Pact.class);
		for (Pact pact : Pact.values()) {
			out.put(pact, new PactTrialState(
				PactTrials.progress(player, pact),
				PactTrials.goalOf(pact),
				PactTrials.isTier4Complete(player, pact)));
		}
		return Map.copyOf(out);
	}

	public static PactTrialProgress pactTrialProgress(Player player) {
		return state(player).pactTrialProgress;
	}

	public static void setPactTrialProgress(Player player, PactTrialProgress progress) {
		state(player).pactTrialProgress = progress == null ? PactTrialProgress.EMPTY : progress;
		sync(player);
	}

	public static int getCapacity(Player player) {
		return clampCapacity(state(player).capacity);
	}

	public static void setCapacity(Player player, int value) {
		state(player).capacity = clampCapacity(value);
		sync(player);
	}

	private static int clampCapacity(int value) {
		return Math.min(AttunedConfig.get().capacityCap(), Math.max(0, value));
	}

	public static AttunedInv getInventory(Player player) {
		return state(player).inventory;
	}

	public static void setSlot(Player player, int slot, ItemStack stack) {
		if (slot < 0 || slot >= AttunedInv.SIZE) {
			return;
		}
		if (stack == null || stack.isEmpty()) {
			state(player).inventory = getInventory(player).with(slot, ItemStack.EMPTY);
			sync(player);
			return;
		}
		if (Attunement.definitionFor(player, stack).isEmpty()) {
			return;
		}
		state(player).inventory = getInventory(player).with(slot, cappedSlotStack(stack));
		sync(player);
	}

	private static ItemStack cappedSlotStack(ItemStack stack) {
		ItemStack copy = stack.copy();
		copy.setCount(Math.min(copy.getCount(), 1));
		return copy;
	}

	public static List<FocusPreset> getPresets(Player player) {
		return normalizePresets(state(player).presets);
	}

	private static List<FocusPreset> normalizePresets(List<FocusPreset> presets) {
		if (presets == null || presets.isEmpty()) {
			return List.of();
		}
		List<FocusPreset> normalized = new ArrayList<>(Math.min(MAX_PRESETS, presets.size()));
		for (int i = 0; i < Math.min(MAX_PRESETS, presets.size()); i++) {
			FocusPreset preset = presets.get(i);
			if (preset != null) {
				normalized.add(new FocusPreset(preset.name(), preset.slots()));
			}
		}
		return List.copyOf(normalized);
	}

	public static void savePreset(Player player, FocusPreset preset) {
		if (preset == null) {
			return;
		}
		List<FocusPreset> current = getPresets(player);
		List<FocusPreset> updated = new ArrayList<>(current);
		for (int i = 0; i < updated.size(); i++) {
			if (updated.get(i).name().equals(preset.name())) {
				updated.set(i, preset);
				state(player).presets = List.copyOf(updated);
				sync(player);
				return;
			}
		}
		if (updated.size() >= MAX_PRESETS) {
			return;
		}
		updated.add(preset);
		state(player).presets = List.copyOf(updated);
		sync(player);
	}

	public static void deletePreset(Player player, int index) {
		List<FocusPreset> current = getPresets(player);
		if (index < 0 || index >= current.size()) {
			return;
		}
		List<FocusPreset> updated = new ArrayList<>(current);
		updated.remove(index);
		state(player).presets = List.copyOf(updated);
		sync(player);
	}

	/** Whether the player has already claimed the milestone with the given id. */
	public static boolean hasMilestone(Player player, String id) {
		return normalizedAttachmentId(id)
			.map(milestoneId -> state(player).milestones.contains(milestoneId))
			.orElse(false);
	}

	/** Records a milestone id as claimed. A no-op if it was already claimed. */
	public static void addMilestone(Player player, String id) {
		Optional<String> normalized = normalizedAttachmentId(id);
		if (normalized.isEmpty()) {
			return;
		}
		String milestoneId = normalized.get();
		if (state(player).milestones.contains(milestoneId)) {
			return;
		}
		List<String> updated = new ArrayList<>(state(player).milestones);
		updated.add(milestoneId);
		state(player).milestones = List.copyOf(updated);
		sync(player);
	}

	public static float getResonance(Player player) {
		return clampResonance(state(player).resonance);
	}

	public static void setResonance(Player player, float value) {
		state(player).resonance = clampResonance(value);
		sync(player);
	}

	private static float clampResonance(float value) {
		if (!Float.isFinite(value)) {
			return 0.0F;
		}
		return Math.min(1.0F, Math.max(0.0F, value));
	}

	/** Whether this player has already seen the onboarding toast with the given id. */
	public static boolean sawOnboarding(Player player, String id) {
		return normalizedAttachmentId(id)
			.map(onboardingId -> state(player).onboarding.contains(onboardingId))
			.orElse(false);
	}

	/** Records an onboarding toast id as seen. A no-op if it was already seen. */
	public static void markOnboarding(Player player, String id) {
		Optional<String> normalized = normalizedAttachmentId(id);
		if (normalized.isEmpty()) {
			return;
		}
		String onboardingId = normalized.get();
		if (state(player).onboarding.contains(onboardingId)) {
			return;
		}
		List<String> updated = new ArrayList<>(state(player).onboarding);
		updated.add(onboardingId);
		state(player).onboarding = List.copyOf(updated);
		sync(player);
	}

	/** Confluence ids this player has discovered, in discovery order. */
	public static List<String> getDiscoveredConfluences(Player player) {
		return state(player).discoveredConfluences;
	}

	/** Records a Confluence id as discovered. A no-op if already discovered. Server-side writes only. */
	public static void markConfluenceDiscovered(Player player, String id) {
		Optional<String> normalized = normalizedAttachmentId(id);
		if (normalized.isEmpty()) {
			return;
		}
		String confluenceId = normalized.get();
		if (state(player).discoveredConfluences.contains(confluenceId)) {
			return;
		}
		List<String> updated = new ArrayList<>(state(player).discoveredConfluences);
		updated.add(confluenceId);
		state(player).discoveredConfluences = List.copyOf(updated);
		sync(player);
	}

	private static Optional<String> normalizedAttachmentId(String id) {
		if (id == null) {
			return Optional.empty();
		}
		String normalized = id.trim();
		return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);
	}

	private static State state(Player player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> State.empty());
	}

	private static ListTag stringList(List<String> values) {
		ListTag list = new ListTag();
		for (String value : values) {
			list.add(StringTag.valueOf(value));
		}
		return list;
	}

	private static List<String> readStringList(CompoundTag tag, String key) {
		List<String> values = new ArrayList<>();
		ListTag list = tag.getList(key, Tag.TAG_STRING);
		for (int i = 0; i < list.size(); i++) {
			values.add(list.getString(i));
		}
		return List.copyOf(values);
	}

	private static final class State {
		private int capacity;
		private AttunedInv inventory;
		private List<FocusPreset> presets;
		private List<String> milestones;
		private float resonance;
		private List<String> onboarding;
		private PactTrialProgress pactTrialProgress;
		private List<String> discoveredConfluences;

		private State(
				int capacity,
				AttunedInv inventory,
				List<FocusPreset> presets,
				List<String> milestones,
				float resonance,
				List<String> onboarding,
				PactTrialProgress pactTrialProgress,
				List<String> discoveredConfluences) {
			this.capacity = clampCapacity(capacity);
			this.inventory = inventory == null ? AttunedInv.empty() : inventory;
			this.presets = normalizePresets(presets);
			this.milestones = milestones == null ? List.of() : List.copyOf(milestones);
			this.resonance = clampResonance(resonance);
			this.onboarding = onboarding == null ? List.of() : List.copyOf(onboarding);
			this.pactTrialProgress = pactTrialProgress == null ? PactTrialProgress.EMPTY : pactTrialProgress;
			this.discoveredConfluences = discoveredConfluences == null ? List.of() : List.copyOf(discoveredConfluences);
		}

		private static State empty() {
			return new State(AttunedConfig.get().startingCapacity(), AttunedInv.empty(), List.of(), List.of(),
				0.0F, List.of(), PactTrialProgress.EMPTY, List.of());
		}

		private State copy() {
			return new State(capacity, inventory, presets, milestones, resonance, onboarding,
				pactTrialProgress, discoveredConfluences);
		}

		private CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			tag.putInt(CAPACITY_KEY, capacity);
			tag.put(INVENTORY_KEY, inventory.toTag());
			ListTag presetTags = new ListTag();
			for (FocusPreset preset : presets) {
				presetTags.add(preset.toTag());
			}
			tag.put(PRESETS_KEY, presetTags);
			tag.put(MILESTONES_KEY, stringList(milestones));
			tag.putFloat(RESONANCE_KEY, resonance);
			tag.put(ONBOARDING_KEY, stringList(onboarding));
			tag.put(PACT_TRIAL_PROGRESS_KEY, pactTrialProgress.toTag());
			tag.put(DISCOVERED_CONFLUENCES_KEY, stringList(discoveredConfluences));
			return tag;
		}

		private static State fromTag(CompoundTag tag) {
			List<FocusPreset> presets = new ArrayList<>();
			ListTag presetTags = tag.getList(PRESETS_KEY, Tag.TAG_COMPOUND);
			for (int i = 0; i < presetTags.size(); i++) {
				presets.add(FocusPreset.fromTag(presetTags.getCompound(i)));
			}
			return new State(
				tag.contains(CAPACITY_KEY) ? tag.getInt(CAPACITY_KEY) : AttunedConfig.get().startingCapacity(),
				AttunedInv.fromTag(tag.getCompound(INVENTORY_KEY)),
				presets,
				readStringList(tag, MILESTONES_KEY),
				tag.getFloat(RESONANCE_KEY),
				readStringList(tag, ONBOARDING_KEY),
				PactTrialProgress.fromTag(tag.getCompound(PACT_TRIAL_PROGRESS_KEY)),
				readStringList(tag, DISCOVERED_CONFLUENCES_KEY));
		}
	}
}
