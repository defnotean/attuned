package dev.attuned.synergy;

import dev.attuned.compat.AttributeModifierIds;
import dev.attuned.compat.PlayerMessages;
import dev.attuned.Attuned;
import dev.attuned.AttunedAdvancements;
import dev.attuned.AttunedConfig;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedRegistries;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.api.focus.ModifierEntry;
import dev.attuned.api.synergy.SynergyDefinition;
import dev.attuned.Milestones;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.Attunement;
import dev.attuned.network.ActionBarMessages;
import dev.attuned.onboarding.Onboarding;
import dev.attuned.party.CircleContributions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Confluences runtime: each throttled server tick it resolves which Confluences
 * are active for every online player (via the pure {@link SynergyResolver}), diffs
 * against the previous tick, and turns gains/losses into applied attribute modifiers,
 * {@link FocusBehavior} activate/deactivate hooks, chat transitions, advancements, and
 * a one-time first-discovery fanfare.
 *
 * <p>Mirrors {@code Pacts}: a {@code % 20} throttle, a per-player last-state map with
 * put-after-detect diffing, an on-join strip-then-reapply reconcile so transient
 * modifiers never accumulate across sessions, and full per-player/server cleanup. A
 * Confluence costs no attunement budget — it is an emergent reward layered on top of
 * the existing Focus resolution.</p>
 */
public final class Synergies {
	private Synergies() {}

	private static boolean initialized;
	private static int ticks;
	private static final Map<UUID, Set<String>> synergyState = new HashMap<>();
	private static final Map<UUID, List<FocusBehavior>> activeBehaviors = new HashMap<>();

	/** Registers the tick handler, the on-join reconcile, and cleanup callbacks. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		ServerTickEvents.END_SERVER_TICK.register(Synergies::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> reconcileOnJoin(handler.player));
		AttunedPlayerCleanup.onForgetPlayer(player -> {
			synergyState.remove(player.getUUID());
			activeBehaviors.remove(player.getUUID());
		});
		AttunedServerCleanup.onStopServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				reconcileOnJoin(player);
			}
			ticks = 0;
			synergyState.clear();
			activeBehaviors.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		ticks++;
		if (ticks % 20 != 0) {
			return; // Confluences resolve, and tick their behaviors, once a second.
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player);
		}
	}

	private static void tickPlayer(ServerPlayer player) {
		Set<String> activeFocusIds = new HashSet<>();
		for (int slot : Attunement.activeSlots(player)) {
			ItemStack stack = AttunedAttachments.getInventory(player).get(slot);
			Attunement.definitionFor(player, stack).ifPresent(def ->
				activeFocusIds.add(BuiltInRegistries.ITEM.getKey(def.item().value()).toString()));
		}

		Registry<SynergyDefinition> registry =
			player.level().registryAccess().registryOrThrow(AttunedRegistries.SYNERGY_DEFINITIONS);
		List<SynergyResolver.SynergyDef> defs = new ArrayList<>();
		Map<String, SynergyDefinition> byId = new HashMap<>();
		registry.stream().forEach(def -> {
			String id = registry.getKey(def).toString();
			byId.put(id, def);
			List<String> members = def.members().stream().map(ResourceLocation::toString).toList();
			defs.add(new SynergyResolver.SynergyDef(id, members));
		});

		Set<String> now = SynergyResolver.activeConfluences(activeFocusIds, defs);
		Set<String> was = synergyState.getOrDefault(player.getUUID(), Set.of());
		for (String id : now) {
			if (!was.contains(id)) {
				onGain(player, id, byId.get(id));
			}
		}
		for (String id : was) {
			if (!now.contains(id)) {
				onLoss(player, id, byId.get(id));
			}
		}
		synergyState.put(player.getUUID(), Set.copyOf(now)); // put-after-detect
		tickActiveBehaviors(player, now, byId);
	}

	/**
	 * Resolves the behaviors of every currently-active Confluence and ticks each one with an
	 * empty backing stack — Confluence behaviors carry no item, so they must tolerate
	 * {@link ItemStack#EMPTY}. Rebuilt from the freshly-diffed active set each throttled tick so
	 * a behavior dropped this tick stops ticking immediately; the cached list is cleaned per
	 * player/server alongside {@code synergyState}.
	 */
	private static void tickActiveBehaviors(ServerPlayer player, Set<String> now,
			Map<String, SynergyDefinition> byId) {
		List<FocusBehavior> behaviors = new ArrayList<>();
		for (String id : now) {
			SynergyDefinition def = byId.get(id);
			if (def == null) {
				continue;
			}
			def.behavior().ifPresent(behaviorId -> {
				FocusBehavior behavior = AttunedRegistries.getBehavior(behaviorId);
				if (behavior != null) {
					behaviors.add(behavior);
				}
			});
		}
		if (behaviors.isEmpty()) {
			activeBehaviors.remove(player.getUUID());
			return;
		}
		activeBehaviors.put(player.getUUID(), behaviors);
		for (FocusBehavior behavior : behaviors) {
			behavior.onTick(player, ItemStack.EMPTY); // a Confluence has no backing stack
		}
	}

	private static void onGain(ServerPlayer player, String confluenceId, SynergyDefinition def) {
		if (def == null) {
			return;
		}
		applyModifiers(player, confluenceId, def);
		def.behavior().ifPresent(behaviorId -> {
			FocusBehavior behavior = AttunedRegistries.getBehavior(behaviorId);
			if (behavior != null) {
				behavior.onActivate(player, ItemStack.EMPTY); // a Confluence has no backing stack
			}
		});
		PlayerMessages.system(player, Component.translatable("confluence.attuned.gained", nameOf(confluenceId)));
		AttunedAdvancements.award(player, "attunement/confluence_" + pathOf(confluenceId));
		maybeFanfare(player, confluenceId);
	}

	private static void onLoss(ServerPlayer player, String confluenceId, SynergyDefinition def) {
		if (def == null) {
			return; // dropped from the datapack mid-session; transient modifiers clear on relog
		}
		removeModifiers(player, confluenceId, def);
		def.behavior().ifPresent(behaviorId -> {
			FocusBehavior behavior = AttunedRegistries.getBehavior(behaviorId);
			if (behavior != null) {
				behavior.onDeactivate(player, ItemStack.EMPTY);
			}
		});
		PlayerMessages.system(player, Component.translatable("confluence.attuned.faded", nameOf(confluenceId)));
	}

	private static void applyModifiers(ServerPlayer player, String confluenceId, SynergyDefinition def) {
		for (int i = 0; i < def.modifiers().size(); i++) {
			ModifierEntry entry = def.modifiers().get(i);
			AttributeInstance ai = player.getAttribute(entry.attribute().value());
			if (ai == null) {
				continue;
			}
			ResourceLocation id = modifierId(confluenceId, i);
			if (ai.getModifier(AttributeModifierIds.uuid(id)) == null) {
				ai.addTransientModifier(new AttributeModifier(AttributeModifierIds.uuid(id), AttributeModifierIds.name(id), entry.amount(), entry.operation()));
			}
		}
	}

	private static void removeModifiers(ServerPlayer player, String confluenceId, SynergyDefinition def) {
		for (int i = 0; i < def.modifiers().size(); i++) {
			ModifierEntry entry = def.modifiers().get(i);
			AttributeInstance ai = player.getAttribute(entry.attribute().value());
			if (ai == null) {
				continue;
			}
			ai.removeModifier(AttributeModifierIds.uuid(modifierId(confluenceId, i)));
		}
	}

	/**
	 * Strip-then-reapply: removes every confluence modifier this player might carry
	 * (defensive against stale persisted NBT), then clears the cached state so the
	 * very next {@link #tickPlayer} treats each currently-active Confluence as a fresh
	 * gain and re-applies it deterministically.
	 */
	private static void reconcileOnJoin(ServerPlayer player) {
		Registry<SynergyDefinition> registry =
			player.level().registryAccess().registryOrThrow(AttunedRegistries.SYNERGY_DEFINITIONS);
		registry.stream().forEach(def -> {
			removeModifiers(player, registry.getKey(def).toString(), def);
		});
		synergyState.remove(player.getUUID());
		activeBehaviors.remove(player.getUUID());
	}

	private static void maybeFanfare(ServerPlayer player, String confluenceId) {
		Onboarding.tryConfluenceHint(player);
		String onboardId = "confluence_first_" + pathOf(confluenceId);
		if (AttunedAttachments.sawOnboarding(player, onboardId)) {
			return;
		}
		AttunedAttachments.markOnboarding(player, onboardId);
		fanfare(player, confluenceId);
	}

	private static void fanfare(ServerPlayer player, String confluenceId) {
		recordConfluenceDiscovery(player, confluenceId);
		shareConfluenceDiscovery(player, confluenceId);
		Component discovery = Component.translatable("confluence.attuned.first_discovery", nameOf(confluenceId))
			.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.blockPosition(),
				SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.0F);
			level.sendParticles(ParticleTypes.ENCHANT,
				player.getX(),
				player.getY() + player.getBbHeight() * 0.5,
				player.getZ(),
				16, 0.5, 0.7, 0.5, 0.4);
		}
		ActionBarMessages.send(player, ActionBarMessages.Priority.PROGRESS, discovery);
		PlayerMessages.system(player, discovery);
	}

	private static void recordConfluenceDiscovery(ServerPlayer player, String confluenceId) {
		AttunedAttachments.markConfluenceDiscovered(player, confluenceId); // flips the journal row to discovered
		if (AttunedAttachments.getDiscoveredConfluences(player).size() == 1) {
			Milestones.onFirstConfluence(player);
		}
	}

	private static void shareConfluenceDiscovery(ServerPlayer player, String confluenceId) {
		if (!AttunedConfig.get().partyConfluenceHintsEnabled()) {
			return;
		}
		for (ServerPlayer member : CircleContributions.nearbyCircleMembers(player)) {
			if (member.getUUID().equals(player.getUUID())) {
				continue;
			}
			recordConfluenceDiscovery(member, confluenceId);
		}
	}

	/**
	 * The discovered Confluence that is exactly one active member short, when unambiguous.
	 * Empty when nothing qualifies or when budget/policy suppresses the hint.
	 */
	public static Optional<String> oneAwayConfluence(Player player) {
		return SynergyResolver.previewOf(
			activeFocusIds(player),
			new HashSet<>(AttunedAttachments.getDiscoveredConfluences(player)),
			definitions(player));
	}

	/**
	 * Short action-bar hint for a build that is exactly one active Focus away from a
	 * single discovered Confluence.
	 */
	public static Optional<Component> previewOf(Player player) {
		Optional<String> previewId = oneAwayConfluence(player);
		if (previewId.isEmpty()) {
			return Optional.empty();
		}
		String id = previewId.get();
		Set<String> active = activeFocusIds(player);
		String missingMember = null;
		for (SynergyResolver.SynergyDef def : definitions(player)) {
			if (!def.id().equals(id)) {
				continue;
			}
			for (String member : def.members()) {
				if (!active.contains(member)) {
					missingMember = member;
					break;
				}
			}
			break;
		}
		Component confluenceName = nameOf(id);
		Component memberName = missingMember == null
			? Component.empty()
			: Component.translatable(itemKey(missingMember));
		return Optional.of(Component.translatable("confluence.attuned.preview", confluenceName, memberName)
			.withStyle(ChatFormatting.GRAY));
	}

	/**
	 * The Confluence table as resolver-ready {@link SynergyResolver.SynergyDef}s, read from the
	 * (client- or server-side) {@code SYNERGY_DEFINITIONS} registry. Used by the client readout to
	 * compute active Confluences and the "one away" preview without touching the budget core.
	 */
	public static List<SynergyResolver.SynergyDef> definitions(Player player) {
		Registry<SynergyDefinition> registry =
			player.level().registryAccess().registryOrThrow(AttunedRegistries.SYNERGY_DEFINITIONS);
		List<SynergyResolver.SynergyDef> defs = new ArrayList<>();
		registry.stream().forEach(def -> {
			defs.add(new SynergyResolver.SynergyDef(
				registry.getKey(def).toString(),
				def.members().stream().map(ResourceLocation::toString).toList()));
		});
		return defs;
	}

	/** Stable per-Confluence, per-modifier-index id, distinct from the slot-based Focus scheme. */
	private static ResourceLocation modifierId(String confluenceId, int index) {
		return new ResourceLocation(Attuned.MOD_ID, "confluence_"
			+ confluenceId.replace(':', '_') + "_mod_" + index);
	}

	private static Set<String> activeFocusIds(Player player) {
		Set<String> activeFocusIds = new HashSet<>();
		for (int slot : Attunement.activeSlots(player)) {
			ItemStack stack = AttunedAttachments.getInventory(player).get(slot);
			Attunement.definitionFor(player, stack).ifPresent(def ->
				activeFocusIds.add(BuiltInRegistries.ITEM.getKey(def.item().value()).toString()));
		}
		return activeFocusIds;
	}

	private static Component nameOf(String confluenceId) {
		return Component.translatable("confluence.attuned." + pathOf(confluenceId) + ".name");
	}

	private static String itemKey(String itemId) {
		int colon = itemId.indexOf(':');
		String namespace = colon >= 0 ? itemId.substring(0, colon) : "minecraft";
		String path = colon >= 0 ? itemId.substring(colon + 1) : itemId;
		return "item." + namespace + "." + path;
	}

	/** The path portion of a {@code namespace:path} id, computed without touching a registry. */
	private static String pathOf(String confluenceId) {
		int colon = confluenceId.indexOf(':');
		return colon >= 0 ? confluenceId.substring(colon + 1) : confluenceId;
	}
}
