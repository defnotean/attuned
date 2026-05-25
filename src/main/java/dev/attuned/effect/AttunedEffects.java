package dev.attuned.effect;

import dev.attuned.Attuned;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedRegistries;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.AffinityColors;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.api.focus.ModifierEntry;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.Attunement;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Applies and removes Focus effects as a player's active attunement set changes,
 * and shows a subtle particle aura while a player has any active Focus.
 *
 * <p>Each server tick the active slots are diffed against the previous tick's
 * tracked state: newly-active (or changed) slots have their declarative attribute
 * modifiers applied and their {@link FocusBehavior} activated; slots that went
 * dormant (or changed) have theirs removed; slots that are unchanged get a
 * {@link FocusBehavior#onTick} call. Attribute modifiers are transient — they are
 * never persisted, so a full reapply on respawn is both correct and required.
 */
public final class AttunedEffects {
	private AttunedEffects() {}

	/** Ticks between aura particle bursts. */
	private static final int AURA_INTERVAL = 16;

	/**
	 * Per-player snapshot of which slots were active last tick and the exact stack
	 * that was active in each. Stack values are defensive copies so later in-world
	 * mutations cannot alias the tracked state.
	 */
	private static final Map<UUID, Map<Integer, ItemStack>> ACTIVE = new HashMap<>();
	/** Per-player dormant slot set from last tick, for one-shot dormancy hints. */
	private static final Map<UUID, Set<Integer>> DORMANT = new HashMap<>();

	/** Server-wide tick counter used to throttle the aura. */
	private static int auraTick;

	/** Registers the tick and respawn hooks that drive the effect system. */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			auraTick++;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tickPlayer(player);
			}
		});

		// A respawned player is a fresh entity with no transient modifiers; drop the
		// tracked state so the next tick reapplies every active Focus from scratch.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			ACTIVE.remove(newPlayer.getUUID());
			DORMANT.remove(newPlayer.getUUID());
		});

		// Drop a disconnecting player's snapshot too — like respawn, a reconnecting
		// player is a fresh entity whose active Foci must be reapplied from scratch.
		AttunedPlayerCleanup.onForget(ACTIVE::remove);
		AttunedPlayerCleanup.onForget(DORMANT::remove);
	}

	private static void tickPlayer(ServerPlayer player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		List<Integer> currentActive = Attunement.activeSlots(player);
		boolean wasTracked = ACTIVE.containsKey(player.getUUID());
		Map<Integer, ItemStack> tracked =
			ACTIVE.computeIfAbsent(player.getUUID(), id -> new HashMap<>());

		// A subtle ambient aura while the player has anything active.
		if (!currentActive.isEmpty() && auraTick % AURA_INTERVAL == 0) {
			spawnAura(player);
		}

		// Build this tick's active snapshot: slot -> current stack in that slot.
		Map<Integer, ItemStack> nextState = new HashMap<>();
		for (int slot : currentActive) {
			nextState.put(slot, inv.get(slot));
		}
		Set<Integer> dormantSlots = dormantSlots(player, inv, currentActive);
		announceNewDormantSlots(player, dormantSlots, wasTracked);

		boolean anyActivated = false;
		boolean anyDeactivated = false;

		// Removals: slots that were active last tick but are no longer active, or
		// whose stack changed (the old stack must be torn down with its own data).
		for (Map.Entry<Integer, ItemStack> entry : tracked.entrySet()) {
			int slot = entry.getKey();
			ItemStack previous = entry.getValue();
			ItemStack now = nextState.get(slot);
			if (now == null || !ItemStack.matches(previous, now)) {
				removeFocus(player, slot, previous);
				anyDeactivated = true;
			}
		}

		// Applications and ticks: walk the currently-active slots.
		for (int slot : currentActive) {
			ItemStack now = nextState.get(slot);
			ItemStack previous = tracked.get(slot);
			if (previous != null && ItemStack.matches(previous, now)) {
				// Unchanged: still active with the same stack — just tick its behaviour.
				tickFocus(player, now);
			} else {
				// Newly active, or the stack in this slot changed.
				applyFocus(player, slot, now);
				anyActivated = true;
			}
		}

		// Commit this tick's snapshot as defensive copies for next-tick diffing.
		tracked.clear();
		for (Map.Entry<Integer, ItemStack> entry : nextState.entrySet()) {
			tracked.put(entry.getKey(), entry.getValue().copy());
		}

		// Chime for real activation changes — but stay silent on the first-tick
		// reapply after a login or respawn, when every active Focus reads as new.
		if (wasTracked) {
			if (anyActivated) {
				playAttuneSound(player, 1.2F);
			}
			if (anyDeactivated) {
				playAttuneSound(player, 0.7F);
			}
		}
	}

	private static Set<Integer> dormantSlots(ServerPlayer player, AttunedInv inv, List<Integer> activeSlots) {
		Set<Integer> active = new HashSet<>(activeSlots);
		Set<Integer> dormant = new HashSet<>();
		for (int slot = 0; slot < AttunedInv.SIZE; slot++) {
			if (!active.contains(slot) && Attunement.definitionFor(player, inv.get(slot)).isPresent()) {
				dormant.add(slot);
			}
		}
		return dormant;
	}

	private static void announceNewDormantSlots(ServerPlayer player, Set<Integer> dormantSlots, boolean wasTracked) {
		UUID id = player.getUUID();
		Set<Integer> previous = DORMANT.getOrDefault(id, Set.of());
		if (wasTracked) {
			for (int slot : dormantSlots) {
				if (!previous.contains(slot)) {
					player.sendSystemMessage(Component.literal("A Focus falls dormant: ")
						.withStyle(ChatFormatting.GRAY)
						.append(AttunedAttachments.getInventory(player).get(slot).getHoverName())
						.append(Component.literal(". Move it higher or raise capacity.")
							.withStyle(ChatFormatting.DARK_GRAY)));
					break;
				}
			}
		}
		if (dormantSlots.isEmpty()) {
			DORMANT.remove(id);
		} else {
			DORMANT.put(id, Set.copyOf(dormantSlots));
		}
	}

	/** A soft chime when a Focus changes activation — pitched up to attune, down to lapse. */
	private static void playAttuneSound(ServerPlayer player, float pitch) {
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, pitch);
	}

	/** Stable, per-slot, per-modifier-index id so modifiers can be removed precisely. */
	private static Identifier modifierId(int slot, int index) {
		return Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "slot_" + slot + "_mod_" + index);
	}

	private static void applyFocus(ServerPlayer player, int slot, ItemStack stack) {
		Optional<FocusDefinition> definition = Attunement.definitionFor(player, stack);
		if (definition.isEmpty()) {
			return;
		}
		FocusDefinition def = definition.get();

		List<ModifierEntry> modifiers = def.modifiers();
		for (int i = 0; i < modifiers.size(); i++) {
			ModifierEntry entry = modifiers.get(i);
			AttributeInstance ai = player.getAttribute(entry.attribute());
			if (ai == null) {
				continue;
			}
			Identifier id = modifierId(slot, i);
			if (ai.getModifier(id) == null) {
				ai.addTransientModifier(new AttributeModifier(id, entry.amount(), entry.operation()));
			}
		}

		def.behavior().ifPresent(behaviorId -> {
			FocusBehavior behavior = AttunedRegistries.getBehavior(behaviorId);
			if (behavior != null) {
				behavior.onActivate(player, stack);
			}
		});
	}

	private static void removeFocus(ServerPlayer player, int slot, ItemStack stack) {
		Optional<FocusDefinition> definition = Attunement.definitionFor(player, stack);
		if (definition.isEmpty()) {
			return;
		}
		FocusDefinition def = definition.get();

		List<ModifierEntry> modifiers = def.modifiers();
		for (int i = 0; i < modifiers.size(); i++) {
			ModifierEntry entry = modifiers.get(i);
			AttributeInstance ai = player.getAttribute(entry.attribute());
			if (ai == null) {
				continue;
			}
			ai.removeModifier(modifierId(slot, i));
		}

		def.behavior().ifPresent(behaviorId -> {
			FocusBehavior behavior = AttunedRegistries.getBehavior(behaviorId);
			if (behavior != null) {
				behavior.onDeactivate(player, stack);
			}
		});
	}

	private static void tickFocus(ServerPlayer player, ItemStack stack) {
		Attunement.definitionFor(player, stack)
			.flatMap(FocusDefinition::behavior)
			.map(AttunedRegistries::getBehavior)
			.ifPresent(behavior -> behavior.onTick(player, stack));
	}

	/** A subtle ambient particle aura shown while the player has any active Focus. */
	private static void spawnAura(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(auraParticle(player),
			player.getX(), player.getY() + 1.0, player.getZ(),
			6, 0.4, 0.9, 0.4, 0.05);
	}

	/** The aura particle for a player's stance: affinity-coloured, Discord magenta, or neutral. */
	private static ParticleOptions auraParticle(ServerPlayer player) {
		if (Attunement.isDiscord(player)) {
			return new DustParticleOptions(AffinityColors.DISCORD_RGB, 1.0F);
		}
		Optional<Affinity> affinity = Attunement.committedAffinity(player);
		if (affinity.isPresent()) {
			return new DustParticleOptions(affinity.get().argb() & 0x00FFFFFF, 1.0F);
		}
		return ParticleTypes.WITCH;
	}
}
