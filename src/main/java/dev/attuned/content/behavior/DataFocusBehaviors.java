package dev.attuned.content.behavior;

import dev.attuned.compat.AttributeModifierIds;

import dev.attuned.Attuned;
import dev.attuned.AttunedConfig;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import dev.attuned.api.focus.FocusBehaviorDef;
import dev.attuned.api.focus.FocusCondition;
import dev.attuned.api.focus.ModifierEntry;
import dev.attuned.combat.CombatTargets;
import dev.attuned.compat.CompassTags;
import dev.attuned.compat.CompassTags.LodestoneTags;
import dev.attuned.effect.AttunedLuck;
import dev.attuned.network.ActionBarMessages;
import dev.attuned.party.CircleContributions;
import dev.attuned.party.CircleEffectCooldowns;
import dev.attuned.party.CircleNavigationTargets;
import dev.attuned.party.CirclePolicy;
import dev.attuned.party.CircleRuntime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Builds runtime {@link FocusBehavior} instances from datapack-defined
 * {@link FocusBehaviorDef} palette entries.
 *
 * <p>This is the data half of the code-first-then-data resolution funnel: when no code
 * behavior is registered for a Focus's {@code behavior} id, {@code AttunedRegistries}
 * looks the id up in the {@code focus_behavior} registry and hands the definition here.
 *
 * <p>The shipped palette covers {@code attuned:conditional_mob_effect},
 * {@code attuned:on_hit_effect}, {@code attuned:periodic_effect},
 * {@code attuned:attribute_while}, {@code attuned:block_context_effect},
 * {@code attuned:use_item_window}, {@code attuned:party_assist},
 * {@code attuned:marked_target}, and {@code attuned:navigation_hint}. All palette
 * behaviors are passive (no Focus Ability).
 */
public final class DataFocusBehaviors {
	private DataFocusBehaviors() {}

	/** Stable id prefix for transient {@code attribute_while} modifiers. Distinct from
	 * {@code AttunedEffects}' {@code slot_N_mod_N} scheme so the two never collide. Qualified
	 * per behaviour id by {@link #attributeWhileModifierId} so two different {@code attribute_while}
	 * Foci on the same attribute install independent modifiers instead of clobbering one another. */
	private static final String ATTRIBUTE_WHILE_MODIFIER_PREFIX = "palette_attr_while";
	static final int BLOCK_CONTEXT_RADIUS_Y = 1;
	static final int PARTY_ASSIST_SCAN_CADENCE_TICKS = 20;
	private static final String USE_ITEM_WINDOW_MODIFIER_PREFIX = "palette_use_item_window";
	private static final Map<UseItemWindowKey, Long> cooldownEndsAt = new HashMap<>();
	private static final Map<UseItemWindowKey, ActiveModifier> activeModifiers = new HashMap<>();
	private static boolean useItemWindowLifecycleInitialized;
	private static final float MARKED_TARGET_CHARGE_THRESHOLD = 0.9F;
	private static final Map<MarkedTargetKey, MarkedTargetMark> marks = new HashMap<>();
	private static final Map<MarkedTargetCooldownKey, Long> markedTargetCooldownEndsAt = new HashMap<>();
	private static boolean markedTargetLifecycleInitialized;
	private static final Map<NavigationHintCacheKey, NavigationHintTarget> navigationHintTargets = new HashMap<>();
	private static boolean navigationHintLifecycleInitialized;
	private static final CircleEffectCooldowns partyAssistCooldowns = new CircleEffectCooldowns();
	private static boolean partyAssistLifecycleInitialized;

	/** The transient-modifier id a given {@code attribute_while} behaviour owns: the shared prefix
	 * qualified by the behaviour's registry id, so distinct behaviours never share a modifier id. */
	static ResourceLocation attributeWhileModifierId(ResourceLocation behaviorId) {
		return new ResourceLocation(Attuned.MOD_ID,
			ATTRIBUTE_WHILE_MODIFIER_PREFIX + "/" + behaviorId.getNamespace() + "/" + behaviorId.getPath());
	}

	static ResourceLocation useItemWindowModifierId(ResourceLocation behaviorId) {
		return new ResourceLocation(Attuned.MOD_ID,
			USE_ITEM_WINDOW_MODIFIER_PREFIX + "/" + behaviorId.getNamespace() + "/" + behaviorId.getPath());
	}

	/** Builds a passive {@link FocusBehavior} for the given palette definition. The behaviour's
	 * registry {@code behaviorId} qualifies any per-instance state (e.g. the {@code attribute_while}
	 * modifier id) so two distinct palette behaviours never collide on the same attribute. */
	public static FocusBehavior build(ResourceLocation behaviorId, FocusBehaviorDef definition) {
		if (definition instanceof FocusBehaviorDef.ConditionalMobEffect effect) {
			return new ConditionalMobEffectBehavior(effect);
		}
		if (definition instanceof FocusBehaviorDef.OnHitEffect onHit) {
			return new OnHitEffectBehavior(onHit);
		}
		if (definition instanceof FocusBehaviorDef.PeriodicEffect periodic) {
			return new PeriodicEffectBehavior(periodic);
		}
		if (definition instanceof FocusBehaviorDef.AttributeWhile attributeWhile) {
			return new AttributeWhileBehavior(behaviorId, attributeWhile);
		}
		if (definition instanceof FocusBehaviorDef.BlockContextEffect blockContext) {
			return new BlockContextEffectBehavior(blockContext);
		}
		if (definition instanceof FocusBehaviorDef.UseItemWindow useItem) {
			return new UseItemWindowBehavior(behaviorId, useItem);
		}
		if (definition instanceof FocusBehaviorDef.PartyAssist partyAssist) {
			return new PartyAssistBehavior(behaviorId, partyAssist);
		}
		if (definition instanceof FocusBehaviorDef.MarkedTarget markedTarget) {
			return new MarkedTargetBehavior(behaviorId, markedTarget);
		}
		if (definition instanceof FocusBehaviorDef.NavigationHint navigation) {
			return new NavigationHintBehavior(behaviorId, navigation);
		}
		throw new IllegalArgumentException("Unknown focus behavior definition: " + definition);
	}

	/**
	 * Refreshes a mob effect on the wearer for as long as the configured condition holds.
	 * When the condition stops holding the effect is left to lapse on its own short duration,
	 * which keeps the data behavior side-effect-symmetric with the shipped code behaviors
	 * (e.g. {@code TideBehavior}) without needing per-effect teardown bookkeeping.
	 */
	static final class ConditionalMobEffectBehavior implements FocusBehavior {
		private final FocusBehaviorDef.ConditionalMobEffect def;

		ConditionalMobEffectBehavior(FocusBehaviorDef.ConditionalMobEffect def) {
			this.def = def;
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			if (!def.condition().test(player)) {
				return;
			}
			PassiveEffectRefresher.refresh(player, def.effect(), def.durationTicks(), def.amplifier(), def.refreshTicks());
		}

		FocusCondition condition() {
			return def.condition();
		}
	}

	/**
	 * Keeps a passive effect refreshed while the wearer is near blocks matching a configured tag.
	 * The scan is intentionally shallow and cadence-gated so datapack-authored context buffs stay
	 * closer to a small local check than a broad world query.
	 */
	static final class BlockContextEffectBehavior implements FocusBehavior {
		private final FocusBehaviorDef.BlockContextEffect def;
		private final Map<UUID, BlockContextScan> scanCache = new HashMap<>();

		BlockContextEffectBehavior(FocusBehaviorDef.BlockContextEffect def) {
			this.def = def;
		}

		@Override
		public void onActivate(ServerPlayer player, ItemStack focus) {
			refreshIfInContext(player);
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			if (player.tickCount % def.refreshTicks() != 0) {
				return;
			}
			refreshIfInContext(player);
		}

		@Override
		public void onDeactivate(ServerPlayer player, ItemStack focus) {
			scanCache.remove(player.getUUID());
		}

		private void refreshIfInContext(ServerPlayer player) {
			if (nearbyBlockContextHolds(player)) {
				PassiveEffectRefresher.refresh(player, def.effect(), def.durationTicks(), def.amplifier(), def.refreshTicks());
			}
		}

		private boolean nearbyBlockContextHolds(ServerPlayer player) {
			UUID playerId = player.getUUID();
			BlockPos center = player.blockPosition();
			BlockContextScan cached = scanCache.get(playerId);
			if (cached != null
					&& cached.center().equals(center)
					&& player.tickCount - cached.tick() < def.refreshTicks()) {
				return cached.holds();
			}
			boolean holds = scanNearbyBlocks(player);
			scanCache.put(playerId, new BlockContextScan(center.immutable(), player.tickCount, holds));
			return holds;
		}

		private boolean scanNearbyBlocks(ServerPlayer player) {
			BlockPos center = player.blockPosition();
			TagKey<Block> blockTag = TagKey.create(Registries.BLOCK, def.blockTag());
			for (BlockPos pos : BlockPos.betweenClosed(
					center.offset(-def.radius(), -BLOCK_CONTEXT_RADIUS_Y, -def.radius()),
					center.offset(def.radius(), BLOCK_CONTEXT_RADIUS_Y, def.radius()))) {
				BlockState state = player.getLevel().getBlockState(pos);
				if (state.is(blockTag) && litRequirementHolds(state)) {
					return true;
				}
			}
			return false;
		}

		private boolean litRequirementHolds(BlockState state) {
			if (!def.requiresLit()) {
				return true;
			}
			return state.hasProperty(BlockStateProperties.LIT)
				&& state.getValue(BlockStateProperties.LIT);
		}

		private record BlockContextScan(BlockPos center, int tick, boolean holds) {}
	}

	static final class UseItemWindowBehavior implements FocusBehavior {
		private final ResourceLocation behaviorId;
		private final FocusBehaviorDef.UseItemWindow def;
		private final ResourceLocation modifierId;

		UseItemWindowBehavior(ResourceLocation behaviorId, FocusBehaviorDef.UseItemWindow def) {
			this.behaviorId = behaviorId;
			this.def = def;
			this.modifierId = useItemWindowModifierId(behaviorId);
			initUseItemWindowLifecycle();
		}

		boolean tryUse(ServerPlayer player, ItemStack stack) {
			if (!matchesUsedItem(stack)) {
				return false;
			}
			long now = player.getLevel().getGameTime();
			UseItemWindowKey key = new UseItemWindowKey(behaviorId, player.getUUID());
			if (now < cooldownEndsAt.getOrDefault(key, 0L)) {
				return false;
			}
			if (def.condition().isPresent() && !def.condition().get().test(player)) {
				return false;
			}
			boolean applied = false;
			if (def.effect().isPresent()) {
				def.effect().ifPresent(effect ->
					PassiveEffectRefresher.apply(player, effect, def.durationTicks(), def.amplifier()));
				applied = true;
			}
			if (def.modifier().isPresent()) {
				applied |= applyModifierWindow(player, key, now, def.modifier().get());
			}
			if (applied) {
				cooldownEndsAt.put(key, now + def.cooldownTicks());
			}
			return applied;
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			UseItemWindowKey key = new UseItemWindowKey(behaviorId, player.getUUID());
			ActiveModifier active = activeModifiers.get(key);
			if (active != null && player.getLevel().getGameTime() >= active.expiresAt()) {
				removeModifier(player, active);
				activeModifiers.remove(key);
			}
		}

		@Override
		public void onDeactivate(ServerPlayer player, ItemStack focus) {
			UseItemWindowKey key = new UseItemWindowKey(behaviorId, player.getUUID());
			ActiveModifier active = activeModifiers.remove(key);
			if (active != null) {
				removeModifier(player, active);
			}
		}

		private boolean matchesUsedItem(ItemStack stack) {
			if (def.item().filter(item -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)).isPresent()) {
				return true;
			}
			return def.itemTag()
				.filter(itemTag -> stack.is(TagKey.create(Registries.ITEM, itemTag)))
				.isPresent();
		}

		private boolean applyModifierWindow(ServerPlayer player, UseItemWindowKey key, long now,
				ModifierEntry modifier) {
			AttributeInstance ai = player.getAttribute(modifier.attribute().value());
			if (ai == null) {
				return false;
			}
			ActiveModifier active = activeModifiers.remove(key);
			if (active != null) {
				removeModifier(player, active);
			}
			double amount = modifier.amount();
			if (AttunedLuck.isPositiveAddLuck(modifier)) {
				amount = AttunedLuck.cappedPositiveAddLuck(ai, amount);
				if (amount <= 0.0D) {
					return false;
				}
			}
			if (ai.getModifier(AttributeModifierIds.uuid(modifierId)) != null) {
				ai.removeModifier(AttributeModifierIds.uuid(modifierId));
			}
			ai.addTransientModifier(new AttributeModifier(
				AttributeModifierIds.uuid(modifierId), AttributeModifierIds.name(modifierId),
				amount, modifier.operation()));
			activeModifiers.put(key, new ActiveModifier(modifier, now + def.durationTicks()));
			return true;
		}

		private void removeModifier(ServerPlayer player, ActiveModifier active) {
			AttributeInstance ai = player.getAttribute(active.modifier().attribute().value());
			if (ai != null) {
				ai.removeModifier(AttributeModifierIds.uuid(modifierId));
			}
		}
	}

	private static void initUseItemWindowLifecycle() {
		if (useItemWindowLifecycleInitialized) {
			return;
		}
		useItemWindowLifecycleInitialized = true;
		AttunedPlayerCleanup.onForget(playerId -> {
			cooldownEndsAt.keySet().removeIf(key -> key.playerId().equals(playerId));
			activeModifiers.keySet().removeIf(key -> key.playerId().equals(playerId));
		});
		AttunedServerCleanup.onStop(() -> {
			cooldownEndsAt.clear();
			activeModifiers.clear();
		});
	}

	private record UseItemWindowKey(ResourceLocation behaviorId, UUID playerId) {}

	private record ActiveModifier(ModifierEntry modifier, long expiresAt) {}

	private static void initMarkedTargetLifecycle() {
		if (markedTargetLifecycleInitialized) {
			return;
		}
		markedTargetLifecycleInitialized = true;
		AttunedPlayerCleanup.onForget(playerId -> {
			marks.keySet().removeIf(key ->
				key.attackerId().equals(playerId) || key.targetId().equals(playerId));
			markedTargetCooldownEndsAt.keySet().removeIf(key -> key.attackerId().equals(playerId));
		});
		AttunedServerCleanup.onStop(() -> {
			marks.clear();
			markedTargetCooldownEndsAt.clear();
		});
	}

	private record MarkedTargetKey(ResourceLocation behaviorId, UUID attackerId, UUID targetId) {}

	private record MarkedTargetCooldownKey(ResourceLocation behaviorId, UUID attackerId) {}

	private record MarkedTargetMark(long expiresAt) {}

	private static void initNavigationHintLifecycle() {
		if (navigationHintLifecycleInitialized) {
			return;
		}
		navigationHintLifecycleInitialized = true;
		AttunedPlayerCleanup.onForget(playerId ->
			navigationHintTargets.keySet().removeIf(key -> key.playerId().equals(playerId)));
		AttunedServerCleanup.onStop(navigationHintTargets::clear);
	}

	private record NavigationHintCacheKey(ResourceLocation behaviorId, UUID playerId) {}

	private record NavigationHintTarget(ResourceKey<Level> dimension, BlockPos pos, long observedAtTick) {
		private NavigationHintTarget {
			pos = pos.immutable();
		}
	}

	private static void initPartyAssistLifecycle() {
		if (partyAssistLifecycleInitialized) {
			return;
		}
		partyAssistLifecycleInitialized = true;
		AttunedServerCleanup.onStop(partyAssistCooldowns::clear);
	}

	/**
	 * Applies a mob effect to the victim (or the attacker, when {@code target_self}) on a
	 * charged, hostile-only direct-melee hit. It does no per-tick work: the proc is driven by
	 * the existing combat {@code AFTER_DAMAGE} handler, which finds active Foci carrying this
	 * behavior and calls {@link #applyTo}. The charge and target gates live in the caller so the
	 * single set of {@code AttunedCombat}/{@code CombatTargets} predicates stays authoritative.
	 */
	public static final class OnHitEffectBehavior implements FocusBehavior {
		private final FocusBehaviorDef.OnHitEffect def;

		OnHitEffectBehavior(FocusBehaviorDef.OnHitEffect def) {
			this.def = def;
		}

		/** The charge fraction (0–1) a swing must reach before the effect procs. */
		public float chargeThreshold() {
			return def.chargeThreshold();
		}

		/** Whether the proc may only land on a hostile mob or valid PvP opponent. */
		public boolean hostileOnly() {
			return def.hostileOnly();
		}

		/** Whether the effect lands on the attacker instead of the victim. */
		public boolean targetSelf() {
			return def.targetSelf();
		}

		/** Picks the recipient and applies the configured effect to it. */
		public void applyTo(ServerPlayer attacker, LivingEntity victim) {
			LivingEntity recipient = def.targetSelf() ? attacker : victim;
			recipient.addEffect(new MobEffectInstance(def.effect().value(), def.durationTicks(), def.amplifier()));
		}
	}

	/**
	 * Keeps a flat mob effect refreshed on the wearer on a fixed cadence — an unconditional
	 * buff. Reuses the shared {@link PassiveEffectRefresher} so the apply flags (ambient,
	 * hidden, icon-less) match the rest of the passive behaviors.
	 */
	public static final class MarkedTargetBehavior implements FocusBehavior {
		private final ResourceLocation behaviorId;
		private final FocusBehaviorDef.MarkedTarget def;

		MarkedTargetBehavior(ResourceLocation behaviorId, FocusBehaviorDef.MarkedTarget def) {
			this.behaviorId = behaviorId;
			this.def = def;
			initMarkedTargetLifecycle();
		}

		public float chargeThreshold() {
			return MARKED_TARGET_CHARGE_THRESHOLD;
		}

		public void onChargedMeleeHit(ServerPlayer attacker, LivingEntity victim, long now) {
			MarkedTargetCooldownKey cooldownKey = new MarkedTargetCooldownKey(behaviorId, attacker.getUUID());
			if (now < markedTargetCooldownEndsAt.getOrDefault(cooldownKey, 0L)) {
				return;
			}
			MarkedTargetKey key = new MarkedTargetKey(behaviorId, attacker.getUUID(), victim.getUUID());
			MarkedTargetMark mark = marks.get(key);
			if (def.consumeTrigger() == FocusBehaviorDef.MarkedTargetTrigger.CHARGED_MELEE_HIT
					&& mark != null && now <= mark.expiresAt()) {
				marks.remove(key);
				applyConsumeEffect(attacker, victim);
				markedTargetCooldownEndsAt.put(cooldownKey, now + def.cooldownTicks());
				return;
			}
			if (mark != null) {
				marks.remove(key);
			}
			if (def.primeTrigger() == FocusBehaviorDef.MarkedTargetTrigger.CHARGED_MELEE_HIT) {
				marks.put(key, new MarkedTargetMark(now + def.markDurationTicks()));
			}
		}

		private void applyConsumeEffect(ServerPlayer attacker, LivingEntity victim) {
			LivingEntity recipient = def.effectOnConsume().targetSelf() ? attacker : victim;
			FocusBehaviorDef.EffectOnConsume effect = def.effectOnConsume();
			recipient.addEffect(new MobEffectInstance(effect.effect().value(), effect.durationTicks(), effect.amplifier()));
		}
	}

	static final class PartyAssistBehavior implements FocusBehavior {
		private final ResourceLocation behaviorId;
		private final FocusBehaviorDef.PartyAssist def;

		PartyAssistBehavior(ResourceLocation behaviorId, FocusBehaviorDef.PartyAssist def) {
			this.behaviorId = behaviorId;
			this.def = def;
			initPartyAssistLifecycle();
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			if (!AttunedConfig.get().partyEffectsEnabled()) {
				return;
			}
			if (player.tickCount % PARTY_ASSIST_SCAN_CADENCE_TICKS != 0) {
				return;
			}
			if (def.trigger() != FocusBehaviorDef.PartyAssistTrigger.PERIODIC_SCAN) {
				return;
			}
			tryAssist(player);
		}

		private void tryAssist(ServerPlayer player) {
			Optional<CirclePolicy.Circle> maybeCircle = CircleRuntime.manager().circleOf(player.getUUID());
			if (maybeCircle.isEmpty()) {
				return;
			}
			CirclePolicy.Circle circle = maybeCircle.orElseThrow();
			long now = player.getLevel().getGameTime();
			if (!partyAssistCooldowns.ready(circle, behaviorId.toString(), now)) {
				return;
			}
			List<ServerPlayer> members = CircleContributions.eligibleAssistTargets(player, def.radius());
			boolean wearerEligible = members.stream()
				.anyMatch(member -> member.getUUID().equals(player.getUUID()));
			if (!wearerEligible) {
				return;
			}
			for (ServerPlayer target : members) {
				if (!matchesTarget(player, target)) {
					continue;
				}
				applyAssist(player, target);
				partyAssistCooldowns.start(circle, behaviorId.toString(), now, def.cooldownTicks());
				return;
			}
		}

		private boolean matchesTarget(ServerPlayer player, ServerPlayer target) {
			if (target.getUUID().equals(player.getUUID())) {
				return false;
			}
			if (CombatTargets.canAffectPlayer(player, target) || CombatTargets.canAffectPlayer(target, player)) {
				return false;
			}
			if (UpdraftBehavior.isPvpAssistDampened(player) || UpdraftBehavior.isPvpAssistDampened(target)) {
				return false;
			}
			return switch (def.targetFilter()) {
				case NEARBY_MEMBERS -> true;
				case WOUNDED_MEMBERS -> target.getHealth() <= target.getMaxHealth() * 0.5F;
				case DROWNING_MEMBERS -> target.isUnderWater() && target.getAirSupply() <= target.getMaxAirSupply() / 2;
			};
		}

		private void applyAssist(ServerPlayer player, ServerPlayer target) {
			FocusBehaviorDef.PartyAssistEffect effect = def.effect();
			PassiveEffectRefresher.apply(target, effect.effect(), effect.durationTicks(), effect.amplifier());
			recordSupportContribution(player);
			ActionBarMessages.send(target, ActionBarMessages.Priority.AMBIENT,
				Component.translatable(def.messageKey(), player.getDisplayName()));
		}

		private void recordSupportContribution(ServerPlayer player) {
			if (def.targetFilter() != FocusBehaviorDef.PartyAssistTargetFilter.NEARBY_MEMBERS) {
				CircleContributions.recordContribution(player);
			}
		}
	}

	static final class NavigationHintBehavior implements FocusBehavior {
		private final ResourceLocation behaviorId;
		private final FocusBehaviorDef.NavigationHint def;

		NavigationHintBehavior(ResourceLocation behaviorId, FocusBehaviorDef.NavigationHint def) {
			this.behaviorId = behaviorId;
			this.def = def;
			initNavigationHintLifecycle();
		}

		@Override
		public void onActivate(ServerPlayer player, ItemStack focus) {
			showHint(player);
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			if (player.tickCount % def.feedbackCadenceTicks() != 0) {
				return;
			}
			showHint(player);
		}

		@Override
		public void onDeactivate(ServerPlayer player, ItemStack focus) {
			navigationHintTargets.remove(cacheKey(player));
		}

		private void showHint(ServerPlayer player) {
			long now = player.getLevel().getGameTime();
			Optional<NavigationHintTarget> maybeTarget = target(player, now);
			if (maybeTarget.isEmpty()) {
				return;
			}
			NavigationHintTarget target = maybeTarget.orElseThrow();
			if (now - target.observedAtTick() > def.maxAgeTicks()) {
				navigationHintTargets.remove(cacheKey(player));
				return;
			}
			boolean sameDimension = target.dimension().equals(player.getLevel().dimension());
			if (def.sameDimensionOnly() && !sameDimension) {
				return;
			}
			if (!sameDimension) {
				ActionBarMessages.send(player, ActionBarMessages.Priority.AMBIENT,
					Component.translatable("message.attuned.navigation_hint.cross_dimension"));
				return;
			}
			ActionBarMessages.send(player, ActionBarMessages.Priority.AMBIENT,
				Component.translatable("message.attuned.navigation_hint",
					Component.literal(directionLabel(player, target.pos())),
					distanceBlocks(player, target.pos())));
		}

		private Optional<NavigationHintTarget> target(ServerPlayer player, long now) {
			return switch (def.targetKind()) {
				case CIRCLE_PING -> circlePingTarget(player, now);
				case CIRCLE_LEADER -> circleLeaderTarget(player, now);
				case HELD_LODESTONE_COMPASS -> lodestoneCompassTarget(player, now);
			};
		}

		private Optional<NavigationHintTarget> circlePingTarget(ServerPlayer player, long now) {
			return CircleNavigationTargets.latestPingFor(player, now)
				.map(target -> new NavigationHintTarget(target.dimension(), target.pos(), target.createdAtTick()));
		}

		private Optional<NavigationHintTarget> circleLeaderTarget(ServerPlayer player, long now) {
			Optional<CirclePolicy.Circle> maybeCircle = CircleRuntime.manager().circleOf(player.getUUID());
			if (maybeCircle.isEmpty()) {
				return Optional.empty();
			}
			UUID leaderId = maybeCircle.orElseThrow().leader();
			if (leaderId.equals(player.getUUID())) {
				return Optional.empty();
			}
			MinecraftServer server = player.getLevel().getServer();
			if (server == null) {
				return Optional.empty();
			}
			ServerPlayer leader = server.getPlayerList().getPlayer(leaderId);
			if (leader == null) {
				return Optional.empty();
			}
			return Optional.of(new NavigationHintTarget(
				leader.getLevel().dimension(), leader.blockPosition(), now));
		}

		private Optional<NavigationHintTarget> lodestoneCompassTarget(ServerPlayer player, long now) {
			Optional<NavigationHintTarget> held = lodestoneTarget(player.getMainHandItem(), now)
				.or(() -> lodestoneTarget(player.getOffhandItem(), now));
			if (held.isPresent()) {
				navigationHintTargets.put(cacheKey(player), held.orElseThrow());
				return held;
			}
			return Optional.ofNullable(navigationHintTargets.get(cacheKey(player)));
		}

		private Optional<NavigationHintTarget> lodestoneTarget(ItemStack held, long now) {
			if (!held.is(Items.COMPASS)) {
				return Optional.empty();
			}
			LodestoneTags tracker = CompassTags.lodestone(held);
			if (!(tracker.pos() instanceof CompoundTag posTag) || tracker.dimension() == null) {
				return Optional.empty();
			}
			Optional<ResourceKey<Level>> dimension = Level.RESOURCE_KEY_CODEC
				.parse(NbtOps.INSTANCE, tracker.dimension())
				.result();
			if (dimension.isEmpty()) {
				return Optional.empty();
			}
			GlobalPos target = GlobalPos.of(dimension.orElseThrow(), NbtUtils.readBlockPos(posTag));
			return Optional.of(new NavigationHintTarget(target.dimension(), target.pos(), now));
		}

		private NavigationHintCacheKey cacheKey(ServerPlayer player) {
			return new NavigationHintCacheKey(behaviorId, player.getUUID());
		}

		private static long distanceBlocks(ServerPlayer player, BlockPos pos) {
			double dx = pos.getX() + 0.5D - player.getX();
			double dy = pos.getY() + 0.5D - player.getY();
			double dz = pos.getZ() + 0.5D - player.getZ();
			return Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
		}

		private static String directionLabel(ServerPlayer player, BlockPos pos) {
			double dx = pos.getX() + 0.5D - player.getX();
			double dz = pos.getZ() + 0.5D - player.getZ();
			double absX = Math.abs(dx);
			double absZ = Math.abs(dz);
			if (absX < 2.0D && absZ < 2.0D) {
				return "here";
			}
			if (absX < absZ * 0.45D) {
				return dz < 0.0D ? "north" : "south";
			}
			if (absZ < absX * 0.45D) {
				return dx < 0.0D ? "west" : "east";
			}
			String northSouth = dz < 0.0D ? "north" : "south";
			String eastWest = dx < 0.0D ? "west" : "east";
			return northSouth + "-" + eastWest;
		}
	}

	static final class PeriodicEffectBehavior implements FocusBehavior {
		private final FocusBehaviorDef.PeriodicEffect def;

		PeriodicEffectBehavior(FocusBehaviorDef.PeriodicEffect def) {
			this.def = def;
		}

		@Override
		public void onActivate(ServerPlayer player, ItemStack focus) {
			refreshPeriodicEffect(player);
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			if (player.tickCount % def.refreshTicks() != 0) {
				return;
			}
			refreshPeriodicEffect(player);
		}

		private void refreshPeriodicEffect(ServerPlayer player) {
			PassiveEffectRefresher.refresh(player, def.effect(), def.durationTicks(), def.amplifier());
		}
	}

	/**
	 * Adds a transient attribute modifier while the condition holds and removes it when it stops,
	 * flipping it under a per-behaviour id so toggling is idempotent. The id is qualified by the
	 * behaviour's registry id ({@link #attributeWhileModifierId}), so two different
	 * {@code attribute_while} Foci targeting the same attribute install independent modifiers and
	 * stack instead of one silently dropping the other. {@code onDeactivate} removes the modifier
	 * unconditionally, so unequipping the Focus while the condition still holds never strands it.
	 */
	static final class AttributeWhileBehavior implements FocusBehavior {
		private final FocusBehaviorDef.AttributeWhile def;
		private final ResourceLocation modifierId;

		AttributeWhileBehavior(ResourceLocation behaviorId, FocusBehaviorDef.AttributeWhile def) {
			this.def = def;
			this.modifierId = attributeWhileModifierId(behaviorId);
		}

		@Override
		public void onTick(ServerPlayer player, ItemStack focus) {
			ModifierEntry modifier = def.modifier();
			AttributeInstance ai = player.getAttribute(modifier.attribute().value());
			if (ai == null) {
				return;
			}
			boolean present = ai.getModifier(AttributeModifierIds.uuid(modifierId)) != null;
			if (def.condition().test(player)) {
				if (!present) {
					double amount = modifier.amount();
					if (AttunedLuck.isPositiveAddLuck(modifier)) {
						amount = AttunedLuck.cappedPositiveAddLuck(ai, amount);
						if (amount <= 0.0D) {
							return;
						}
					}
					ai.addTransientModifier(new AttributeModifier(
						AttributeModifierIds.uuid(modifierId), AttributeModifierIds.name(modifierId),
						amount, modifier.operation()));
				}
			} else if (present) {
				ai.removeModifier(AttributeModifierIds.uuid(modifierId));
			}
		}

		@Override
		public void onDeactivate(ServerPlayer player, ItemStack focus) {
			AttributeInstance ai = player.getAttribute(def.modifier().attribute().value());
			if (ai != null) {
				ai.removeModifier(AttributeModifierIds.uuid(modifierId));
			}
		}

		FocusCondition condition() {
			return def.condition();
		}
	}
}
