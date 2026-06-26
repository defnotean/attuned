package dev.attuned.attunement;

import com.mojang.serialization.Codec;
import dev.attuned.Attuned;
import dev.attuned.AttunedConfig;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.network.AttunementStatePayload;
import dev.attuned.pacts.Pact;
import dev.attuned.pacts.PactTrialProgress;
import dev.attuned.pacts.PactTrials;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-player attunement state: the attunement capacity and the six Focus slots.
 * Both are persistent across restarts and synced to the owning client.
 */
public final class AttunedAttachments {
	private AttunedAttachments() {}

	public static final int MAX_PRESETS = 9;
	private static final Map<UUID, Map<AttachmentType<?>, Object>> PLAYER_ATTACHMENTS = new HashMap<>();
	private static boolean initialized;

	public static final AttachmentType<Integer> CAPACITY = AttachmentRegistry.create(
		new ResourceLocation(Attuned.MOD_ID, "capacity"),
		builder -> builder
			.initializer(() -> AttunedConfig.get().startingCapacity())
			.persistent(Codec.INT)
			.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
			.copyOnDeath()
	);

	public static final AttachmentType<AttunedInv> INVENTORY = AttachmentRegistry.create(
		new ResourceLocation(Attuned.MOD_ID, "inventory"),
		builder -> builder
			.initializer(AttunedInv::empty)
			.persistent(AttunedInv.CODEC)
			.syncWith(AttunedInv.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.copyOnDeath()
	);

	/** The first synced list attachment: named Focus loadouts for the owning client. */
	public static final AttachmentType<List<FocusPreset>> PRESETS = AttachmentRegistry.create(
		new ResourceLocation(Attuned.MOD_ID, "presets"),
		builder -> builder
			.initializer(() -> List.of())
			.persistent(FocusPreset.CODEC.listOf())
			.syncWith(FocusPreset.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly())
			.copyOnDeath()
	);

	/** Ids of the progression milestones a player has already claimed. */
	public static final AttachmentType<List<String>> MILESTONES = AttachmentRegistry.create(
		new ResourceLocation(Attuned.MOD_ID, "milestones"),
		builder -> builder
			.initializer(() -> List.of())
			.persistent(Codec.STRING.listOf())
			.copyOnDeath()
	);

	/**
	 * Combat resonance, the {@code [0, 1]} gauge that gates Apex. Persists
	 * across death so a player who has earned their way up to the Apex
	 * threshold keeps it after respawning; otherwise dying inside an empowered
	 * fight would silently strip the capstone the player just earned.
	 */
	public static final AttachmentType<Float> RESONANCE = AttachmentRegistry.create(
		new ResourceLocation(Attuned.MOD_ID, "resonance"),
		builder -> builder
			.initializer(() -> 0.0F)
			.persistent(Codec.FLOAT)
			.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly())
			.copyOnDeath()
	);

	/** Ids of one-time onboarding toasts a player has already seen. */
	public static final AttachmentType<List<String>> ONBOARDING = AttachmentRegistry.create(
		new ResourceLocation(Attuned.MOD_ID, "onboarding"),
		builder -> builder
			.initializer(() -> List.of())
			.persistent(Codec.STRING.listOf())
			.copyOnDeath()
	);

	/** Per-pact trial counters and permanent Tier 4 completions. Synced for the journal. */
	public static final AttachmentType<PactTrialProgress> PACT_TRIAL_PROGRESS = AttachmentRegistry.create(
		new ResourceLocation(Attuned.MOD_ID, "pact_trial_progress"),
		builder -> builder
			.initializer(() -> PactTrialProgress.EMPTY)
			.persistent(PactTrialProgress.CODEC)
			.syncWith(PactTrialProgress.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.copyOnDeath()
	);

	/** Confluence ids this player has discovered (each first activation). Synced for the journal. */
	public static final AttachmentType<List<String>> DISCOVERED_CONFLUENCES = AttachmentRegistry.create(
		new ResourceLocation(Attuned.MOD_ID, "discovered_confluences"),
		builder -> builder
			.initializer(() -> List.of())
			.persistent(Codec.STRING.listOf())
			.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly())
			.copyOnDeath()
	);

	/** Per-pact trial progress surfaced in the journal; trials runtime fills this in. */
	public record PactTrialState(int progress, int goal, boolean tier4Complete) {}

	/** Forces this class to load so the attachment types register during mod init. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncToClient(handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> syncToClient(newPlayer));
		AttunedPlayerCleanup.onForget(PLAYER_ATTACHMENTS::remove);
		AttunedServerCleanup.onStop(PLAYER_ATTACHMENTS::clear);
	}

	public static <T> T get(Player player, AttachmentType<T> type, T fallback) {
		if (player == null) {
			return fallback;
		}
		Map<AttachmentType<?>, Object> values = PLAYER_ATTACHMENTS.get(player.getUUID());
		Object value = values == null ? null : values.get(type);
		if (value == null) {
			if (player instanceof ServerPlayer serverPlayer) {
				Optional<T> persisted = loadPersistent(serverPlayer, type);
				if (persisted.isPresent()) {
					T persistedValue = persisted.get();
					PLAYER_ATTACHMENTS
						.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
						.put(type, persistedValue);
					return persistedValue;
				}
			}
			T initial = type.initialValue();
			return initial == null ? fallback : initial;
		}
		return type.cast(value);
	}

	public static <T> void set(Player player, AttachmentType<T> type, T value) {
		if (player == null) {
			return;
		}
		PLAYER_ATTACHMENTS
			.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
			.put(type, value);
		if (player instanceof ServerPlayer serverPlayer) {
			persist(serverPlayer, type, value);
			syncToClient(serverPlayer);
		}
	}

	private static <T> Optional<T> loadPersistent(ServerPlayer player, AttachmentType<T> type) {
		Codec<T> codec = type.persistentCodec();
		if (codec == null) {
			return Optional.empty();
		}
		Tag tag = player.getPersistentData().get(type.id().toString());
		if (tag == null) {
			return Optional.empty();
		}
		return codec.parse(NbtOps.INSTANCE, tag)
			.resultOrPartial(message -> Attuned.LOGGER.warn(
				"Unable to decode persisted attachment {} for {}: {}",
				type.id(), player.getScoreboardName(), message));
	}

	private static <T> void persist(ServerPlayer player, AttachmentType<T> type, T value) {
		Codec<T> codec = type.persistentCodec();
		if (codec == null) {
			return;
		}
		codec.encodeStart(NbtOps.INSTANCE, value)
			.resultOrPartial(message -> Attuned.LOGGER.warn(
				"Unable to encode persisted attachment {} for {}: {}",
				type.id(), player.getScoreboardName(), message))
			.ifPresent(tag -> player.getPersistentData().put(type.id().toString(), tag));
	}

	/**
	 * Synced trial progress per {@link Pact}. Returns an empty map when {@code player}
	 * is null; otherwise reads the attachment with {@link PactTrialProgress#EMPTY} as
	 * the defensive fallback.
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

	public static int getCapacity(Player player) {
		return clampCapacity(get(player, CAPACITY, 0));
	}

	public static void setCapacity(Player player, int value) {
		set(player, CAPACITY, clampCapacity(value));
	}

	private static int clampCapacity(int value) {
		return Math.min(AttunedConfig.get().capacityCap(), Math.max(0, value));
	}

	public static AttunedInv getInventory(Player player) {
		return get(player, INVENTORY, AttunedInv.empty());
	}

	public static void setSlot(Player player, int slot, ItemStack stack) {
		if (slot < 0 || slot >= AttunedInv.SIZE) {
			return;
		}
		if (stack == null || stack.isEmpty()) {
			set(player, INVENTORY, getInventory(player).with(slot, ItemStack.EMPTY));
			return;
		}
		if (Attunement.definitionFor(player, stack).isEmpty()) {
			return;
		}
		// Read through getInventory (never null â€” falls back to an empty inventory),
		// then replace the whole value. Do not use modifyAttached here: it passes
		// null to its operator when the attachment has never been set.
		set(player, INVENTORY, getInventory(player).with(slot, cappedSlotStack(stack)));
	}

	private static ItemStack cappedSlotStack(ItemStack stack) {
		ItemStack copy = stack.copy();
		copy.setCount(Math.min(copy.getCount(), 1));
		return copy;
	}

	public static List<FocusPreset> getPresets(Player player) {
		return normalizePresets(get(player, PRESETS, List.of()));
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
				set(player, PRESETS, List.copyOf(updated));
				return;
			}
		}
		if (updated.size() >= MAX_PRESETS) {
			return;
		}
		updated.add(preset);
		set(player, PRESETS, List.copyOf(updated));
	}

	public static void deletePreset(Player player, int index) {
		List<FocusPreset> current = getPresets(player);
		if (index < 0 || index >= current.size()) {
			return;
		}
		List<FocusPreset> updated = new ArrayList<>(current);
		updated.remove(index);
		set(player, PRESETS, List.copyOf(updated));
	}

	/** Whether the player has already claimed the milestone with the given id. */
	public static boolean hasMilestone(Player player, String id) {
		return normalizedAttachmentId(id)
			.map(milestoneId -> get(player, MILESTONES, List.of()).contains(milestoneId))
			.orElse(false);
	}

	/** Records a milestone id as claimed. A no-op if it was already claimed. */
	public static void addMilestone(Player player, String id) {
		Optional<String> normalized = normalizedAttachmentId(id);
		if (normalized.isEmpty()) {
			return;
		}
		String milestoneId = normalized.get();
		List<String> claimed = get(player, MILESTONES, List.of());
		if (claimed.contains(milestoneId)) {
			return;
		}
		List<String> updated = new ArrayList<>(claimed);
		updated.add(milestoneId);
		set(player, MILESTONES, List.copyOf(updated));
	}

	public static float getResonance(Player player) {
		return clampResonance(get(player, RESONANCE, 0.0F));
	}

	public static void setResonance(Player player, float value) {
		set(player, RESONANCE, clampResonance(value));
	}

	public static PactTrialProgress getPactTrialProgressValue(Player player) {
		return get(player, PACT_TRIAL_PROGRESS, PactTrialProgress.EMPTY);
	}

	public static void setPactTrialProgress(Player player, PactTrialProgress progress) {
		set(player, PACT_TRIAL_PROGRESS, progress == null ? PactTrialProgress.EMPTY : progress);
	}

	public static void applySyncedState(Player player, AttunementStatePayload state) {
		if (player == null || state == null) {
			return;
		}
		set(player, CAPACITY, clampCapacity(state.capacity()));
		set(player, INVENTORY, state.inventory());
		set(player, PRESETS, normalizePresets(state.presets()));
		set(player, RESONANCE, clampResonance(state.resonance()));
		set(player, PACT_TRIAL_PROGRESS, state.pactTrialProgress());
		set(player, DISCOVERED_CONFLUENCES, List.copyOf(state.discoveredConfluences()));
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
			.map(onboardingId -> get(player, ONBOARDING, List.of()).contains(onboardingId))
			.orElse(false);
	}

	/** Records an onboarding toast id as seen. A no-op if it was already seen. */
	public static void markOnboarding(Player player, String id) {
		Optional<String> normalized = normalizedAttachmentId(id);
		if (normalized.isEmpty()) {
			return;
		}
		String onboardingId = normalized.get();
		List<String> seen = get(player, ONBOARDING, List.of());
		if (seen.contains(onboardingId)) {
			return;
		}
		List<String> updated = new ArrayList<>(seen);
		updated.add(onboardingId);
		set(player, ONBOARDING, List.copyOf(updated));
	}

	/** Confluence ids this player has discovered, in discovery order. */
	public static List<String> getDiscoveredConfluences(Player player) {
		return get(player, DISCOVERED_CONFLUENCES, List.of());
	}

	/** Records a Confluence id as discovered. A no-op if already discovered. Server-side writes only. */
	public static void markConfluenceDiscovered(Player player, String id) {
		Optional<String> normalized = normalizedAttachmentId(id);
		if (normalized.isEmpty()) {
			return;
		}
		String confluenceId = normalized.get();
		List<String> discovered = get(player, DISCOVERED_CONFLUENCES, List.of());
		if (discovered.contains(confluenceId)) {
			return;
		}
		List<String> updated = new ArrayList<>(discovered);
		updated.add(confluenceId);
		set(player, DISCOVERED_CONFLUENCES, List.copyOf(updated));
	}

	public static void syncToClient(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		ServerPlayNetworking.send(serverPlayer, new AttunementStatePayload(
			getCapacity(serverPlayer),
			getInventory(serverPlayer),
			getPresets(serverPlayer),
			getResonance(serverPlayer),
			getPactTrialProgressValue(serverPlayer),
			getDiscoveredConfluences(serverPlayer)));
	}

	private static Optional<String> normalizedAttachmentId(String id) {
		if (id == null) {
			return Optional.empty();
		}
		String normalized = id.trim();
		return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);
	}
}
