package dev.attuned.attunement;

import com.mojang.serialization.Codec;
import dev.attuned.Attuned;
import dev.attuned.AttunedConfig;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Per-player attunement state: the attunement capacity and the six Focus slots.
 * Both are persistent across restarts and synced to the owning client.
 */
public final class AttunedAttachments {
	private AttunedAttachments() {}

	public static final int MAX_PRESETS = 9;

	public static final AttachmentType<Integer> CAPACITY = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "capacity"),
		builder -> builder
			.initializer(() -> AttunedConfig.get().startingCapacity())
			.persistent(Codec.INT)
			.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
			.copyOnDeath()
	);

	public static final AttachmentType<AttunedInv> INVENTORY = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "inventory"),
		builder -> builder
			.initializer(AttunedInv::empty)
			.persistent(AttunedInv.CODEC)
			.syncWith(AttunedInv.STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
			.copyOnDeath()
	);

	/** The first synced list attachment: named Focus loadouts for the owning client. */
	public static final AttachmentType<List<FocusPreset>> PRESETS = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "presets"),
		builder -> builder
			.initializer(() -> List.of())
			.persistent(FocusPreset.CODEC.listOf())
			.syncWith(FocusPreset.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly())
			.copyOnDeath()
	);

	/** Ids of the progression milestones a player has already claimed. */
	public static final AttachmentType<List<String>> MILESTONES = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "milestones"),
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
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "resonance"),
		builder -> builder
			.initializer(() -> 0.0F)
			.persistent(Codec.FLOAT)
			.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly())
			.copyOnDeath()
	);

	/** Ids of one-time onboarding toasts a player has already seen. */
	public static final AttachmentType<List<String>> ONBOARDING = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "onboarding"),
		builder -> builder
			.initializer(() -> List.of())
			.persistent(Codec.STRING.listOf())
			.copyOnDeath()
	);

	/** Forces this class to load so the attachment types register during mod init. */
	public static void init() {}

	public static int getCapacity(Player player) {
		return clampCapacity(player.getAttachedOrElse(CAPACITY, 0));
	}

	public static void setCapacity(Player player, int value) {
		player.setAttached(CAPACITY, clampCapacity(value));
	}

	private static int clampCapacity(int value) {
		return Math.min(AttunedConfig.get().capacityCap(), Math.max(0, value));
	}

	public static AttunedInv getInventory(Player player) {
		return player.getAttachedOrElse(INVENTORY, AttunedInv.empty());
	}

	public static void setSlot(Player player, int slot, ItemStack stack) {
		if (slot < 0 || slot >= AttunedInv.SIZE) {
			return;
		}
		if (stack == null || stack.isEmpty()) {
			player.setAttached(INVENTORY, getInventory(player).with(slot, ItemStack.EMPTY));
			return;
		}
		if (Attunement.definitionFor(player, stack).isEmpty()) {
			return;
		}
		// Read through getInventory (never null — falls back to an empty inventory),
		// then replace the whole value. Do not use modifyAttached here: it passes
		// null to its operator when the attachment has never been set.
		player.setAttached(INVENTORY, getInventory(player).with(slot, cappedSlotStack(stack)));
	}

	private static ItemStack cappedSlotStack(ItemStack stack) {
		ItemStack copy = stack.copy();
		copy.setCount(Math.min(copy.getCount(), 1));
		return copy;
	}

	public static List<FocusPreset> getPresets(Player player) {
		return normalizePresets(player.getAttachedOrElse(PRESETS, List.of()));
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
				player.setAttached(PRESETS, List.copyOf(updated));
				return;
			}
		}
		if (updated.size() >= MAX_PRESETS) {
			return;
		}
		updated.add(preset);
		player.setAttached(PRESETS, List.copyOf(updated));
	}

	public static void deletePreset(Player player, int index) {
		List<FocusPreset> current = getPresets(player);
		if (index < 0 || index >= current.size()) {
			return;
		}
		List<FocusPreset> updated = new ArrayList<>(current);
		updated.remove(index);
		player.setAttached(PRESETS, List.copyOf(updated));
	}

	/** Whether the player has already claimed the milestone with the given id. */
	public static boolean hasMilestone(Player player, String id) {
		return normalizedAttachmentId(id)
			.map(milestoneId -> player.getAttachedOrElse(MILESTONES, List.of()).contains(milestoneId))
			.orElse(false);
	}

	/** Records a milestone id as claimed. A no-op if it was already claimed. */
	public static void addMilestone(Player player, String id) {
		Optional<String> normalized = normalizedAttachmentId(id);
		if (normalized.isEmpty()) {
			return;
		}
		String milestoneId = normalized.get();
		List<String> claimed = player.getAttachedOrElse(MILESTONES, List.of());
		if (claimed.contains(milestoneId)) {
			return;
		}
		List<String> updated = new ArrayList<>(claimed);
		updated.add(milestoneId);
		player.setAttached(MILESTONES, List.copyOf(updated));
	}

	public static float getResonance(Player player) {
		return clampResonance(player.getAttachedOrElse(RESONANCE, 0.0F));
	}

	public static void setResonance(Player player, float value) {
		player.setAttached(RESONANCE, clampResonance(value));
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
			.map(onboardingId -> player.getAttachedOrElse(ONBOARDING, List.of()).contains(onboardingId))
			.orElse(false);
	}

	/** Records an onboarding toast id as seen. A no-op if it was already seen. */
	public static void markOnboarding(Player player, String id) {
		Optional<String> normalized = normalizedAttachmentId(id);
		if (normalized.isEmpty()) {
			return;
		}
		String onboardingId = normalized.get();
		List<String> seen = player.getAttachedOrElse(ONBOARDING, List.of());
		if (seen.contains(onboardingId)) {
			return;
		}
		List<String> updated = new ArrayList<>(seen);
		updated.add(onboardingId);
		player.setAttached(ONBOARDING, List.copyOf(updated));
	}

	private static Optional<String> normalizedAttachmentId(String id) {
		if (id == null) {
			return Optional.empty();
		}
		String normalized = id.trim();
		return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);
	}
}
