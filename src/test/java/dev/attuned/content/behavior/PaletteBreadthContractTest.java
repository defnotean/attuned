package dev.attuned.content.behavior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source-grep contract for the runtime half of the broadened behavior palette: the three new
 * passive factories built by {@link DataFocusBehaviors} and the no-mixin on-hit dispatch wired
 * into the existing combat {@code AFTER_DAMAGE} handler. The factories touch registry-bound APIs
 * (effect/attribute holders), so their wiring is pinned by literal source assertions rather than a
 * bootstrapped round-trip.
 */
class PaletteBreadthContractTest {
	private static final Path DATA_BEHAVIORS =
		Path.of("src/main/java/dev/attuned/content/behavior/DataFocusBehaviors.java");
	private static final Path PASSIVE_EFFECT_REFRESHER =
		Path.of("src/main/java/dev/attuned/content/behavior/PassiveEffectRefresher.java");
	private static final Path PALETTE_COMBAT =
		Path.of("src/main/java/dev/attuned/combat/PaletteCombat.java");
	private static final Path PALETTE_ITEM_USE =
		Path.of("src/main/java/dev/attuned/content/behavior/PaletteItemUse.java");
	private static final Path ATTUNED_COMBAT =
		Path.of("src/main/java/dev/attuned/combat/AttunedCombat.java");
	private static final Path ATTUNED =
		Path.of("src/main/java/dev/attuned/Attuned.java");
	private static final Path CIRCLE_NETWORKING =
		Path.of("src/main/java/dev/attuned/network/CircleNetworking.java");
	private static final Path CIRCLE_RUNTIME =
		Path.of("src/main/java/dev/attuned/party/CircleRuntime.java");
	private static final Path CIRCLE_NAVIGATION_TARGETS =
		Path.of("src/main/java/dev/attuned/party/CircleNavigationTargets.java");
	private static final Path CIRCLE_CONTRIBUTIONS =
		Path.of("src/main/java/dev/attuned/party/CircleContributions.java");

	@Test
	void buildDispatchesEveryPaletteTypeExhaustively() throws IOException {
		String source = read(DATA_BEHAVIORS);

		assertTrue(source.contains("case FocusBehaviorDef.ConditionalMobEffect effect -> new ConditionalMobEffectBehavior(effect)"),
			"build should still construct ConditionalMobEffectBehavior.");
		assertTrue(source.contains("case FocusBehaviorDef.OnHitEffect onHit -> new OnHitEffectBehavior(onHit)"),
			"build should construct OnHitEffectBehavior for the on_hit_effect type.");
		assertTrue(source.contains("case FocusBehaviorDef.PeriodicEffect periodic -> new PeriodicEffectBehavior(periodic)"),
			"build should construct PeriodicEffectBehavior for the periodic_effect type.");
		assertTrue(source.contains("case FocusBehaviorDef.AttributeWhile attributeWhile -> new AttributeWhileBehavior(behaviorId, attributeWhile)"),
			"build should construct AttributeWhileBehavior for the attribute_while type, passing the behaviour id.");
		assertTrue(source.contains("case FocusBehaviorDef.BlockContextEffect blockContext -> new BlockContextEffectBehavior(blockContext)"),
			"build should construct BlockContextEffectBehavior for the block_context_effect type.");
		assertTrue(source.contains("case FocusBehaviorDef.UseItemWindow useItem -> new UseItemWindowBehavior(behaviorId, useItem)"),
			"build should construct UseItemWindowBehavior for the use_item_window type, passing the behaviour id.");
		assertTrue(source.contains("case FocusBehaviorDef.PartyAssist partyAssist -> new PartyAssistBehavior(behaviorId, partyAssist)"),
			"build should construct PartyAssistBehavior for the party_assist type, passing the behaviour id.");
		assertTrue(source.contains("case FocusBehaviorDef.MarkedTarget markedTarget -> new MarkedTargetBehavior(behaviorId, markedTarget)"),
			"build should construct MarkedTargetBehavior for the marked_target type, passing the behaviour id.");
		assertTrue(source.contains("case FocusBehaviorDef.NavigationHint navigation -> new NavigationHintBehavior(behaviorId, navigation)"),
			"build should construct NavigationHintBehavior for the navigation_hint type, passing the behaviour id.");
	}

	@Test
	void periodicEffectReusesTheSharedRefresher() throws IOException {
		String source = read(DATA_BEHAVIORS);

		assertTrue(source.contains("package dev.attuned.content.behavior;"),
			"PeriodicEffectBehavior must live where the package-private PassiveEffectRefresher is reachable.");
		assertTrue(source.contains("PassiveEffectRefresher.refresh("),
			"periodic_effect should refresh through the shared PassiveEffectRefresher.");
		assertTrue(source.contains("player.tickCount % def.refreshTicks()"),
			"periodic_effect should refresh on a fixed tick cadence.");
	}

	@Test
	void periodicAndConditionalEffectsSharePassiveVisibilityFlags() throws IOException {
		String data = read(DATA_BEHAVIORS);
		String refresher = read(PASSIVE_EFFECT_REFRESHER);

		assertTrue(refresher.contains("private static final boolean PASSIVE_AMBIENT = true"),
			"passive palette effects should share one ambient flag.");
		assertTrue(refresher.contains("private static final boolean PASSIVE_VISIBLE = false"),
			"passive palette effects should share one particles-visible flag.");
		assertTrue(refresher.contains("private static final boolean PASSIVE_SHOW_ICON = false"),
			"passive palette effects should share one HUD-icon flag.");
		assertTrue(squash(refresher).contains(
				"static void refresh(ServerPlayer player, Holder<MobEffect> effect, int duration, int amplifier, int refreshThresholdTicks)"),
			"conditional_mob_effect should be able to reuse the same passive flags with its custom refresh threshold.");
		assertTrue(methodBody(data, "public void onTick(ServerPlayer player, ItemStack focus)")
				.contains("PassiveEffectRefresher.refresh(player, def.effect(), def.durationTicks(), def.amplifier(), def.refreshTicks())"),
			"conditional_mob_effect must reuse PassiveEffectRefresher so its apply flags cannot drift from periodic_effect.");
		assertTrue(methodBody(data, "private void refreshPeriodicEffect(ServerPlayer player)")
				.contains("PassiveEffectRefresher.refresh(player, def.effect(), def.durationTicks(), def.amplifier())"),
			"periodic_effect should use the default passive refresher overload with the shared apply flags.");
	}

	@Test
	void attributeWhileTogglesATransientModifierUnderADistinctStableId() throws IOException {
		String source = read(DATA_BEHAVIORS);

		assertTrue(source.contains("\"palette_attr_while\""),
			"attribute_while must use a stable, distinct modifier-id prefix.");
		assertFalse(source.contains("\"slot_\""),
			"attribute_while must use a distinct modifier-id prefix (palette_attr_while) so it never "
				+ "collides with AttunedEffects' slot_N_mod_N scheme.");
		assertTrue(source.contains("addTransientModifier(") && source.contains("removeModifier("),
			"attribute_while should add and remove a transient modifier as the condition flips.");
		assertTrue(source.contains("def.condition().test("),
			"attribute_while should gate the modifier on its FocusCondition.");
		assertTrue(methodBody(source, "public void onDeactivate(ServerPlayer player, ItemStack focus)", 2)
				.contains("removeModifier("),
			"attribute_while should strip its modifier on deactivation so unequipping never strands it.");
	}

	@Test
	void attributeWhileQualifiesItsModifierIdPerBehaviorSoTwoFociDoNotCollide() throws IOException {
		String source = read(DATA_BEHAVIORS);

		// The modifier id is derived from the behaviour's registry id, not one shared constant, so
		// two different attribute_while Foci on the same attribute install independent transient
		// modifiers and stack instead of one silently dropping the other.
		assertTrue(source.contains("Identifier attributeWhileModifierId(Identifier behaviorId)"),
			"attribute_while modifier ids must be derived from the behaviour id.");
		assertTrue(source.contains("behaviorId.getNamespace()") && source.contains("behaviorId.getPath()"),
			"the per-behaviour modifier id must incorporate the behaviour's registry id so two "
				+ "distinct attribute_while behaviours never share one id.");
		assertTrue(source.contains(
				"AttributeWhileBehavior(Identifier behaviorId, FocusBehaviorDef.AttributeWhile def)"),
			"AttributeWhileBehavior must receive the behaviour id to qualify its modifier id.");
		assertTrue(source.contains("this.modifierId = attributeWhileModifierId(behaviorId)"),
			"AttributeWhileBehavior must hold a per-instance modifier id.");
		assertFalse(source.contains("ATTRIBUTE_WHILE_MODIFIER_ID"),
			"the single shared modifier-id constant must be replaced by the per-behaviour derivation.");
	}

	@Test
	void blockContextEffectScansAShallowCappedRadiusOnlyOnRefreshCadence() throws IOException {
		String source = read(DATA_BEHAVIORS);
		String onTick = methodBody(source, "public void onTick(ServerPlayer player, ItemStack focus)", 2);
		String scan = methodBody(source, "private boolean scanNearbyBlocks(ServerPlayer player)");

		assertTrue(source.contains("static final int BLOCK_CONTEXT_RADIUS_Y = 1"),
			"block_context_effect should scan a shallow vertical band, not a full cube.");
		assertTrue(onTick.contains("if (player.tickCount % def.refreshTicks() != 0)"),
			"block_context_effect should throttle block scans with refresh_ticks.");
		assertTrue(onTick.contains("refreshIfInContext(player)"),
			"block_context_effect should keep the tick path small and delegate the scan/refresh.");
		assertTrue(scan.contains("BlockPos.betweenClosed("),
			"block_context_effect should use one bounded block-position scan.");
		assertTrue(scan.contains("center.offset(-def.radius(), -BLOCK_CONTEXT_RADIUS_Y, -def.radius())")
				&& scan.contains("center.offset(def.radius(), BLOCK_CONTEXT_RADIUS_Y, def.radius())"),
			"block_context_effect should use the configured horizontal radius and shallow vertical cap.");
		assertFalse(scan.contains("center.offset(-def.radius(), -def.radius(), -def.radius())"),
			"block_context_effect must not scan a cubic radius.");
		assertTrue(scan.contains("TagKey.create(Registries.BLOCK, def.blockTag())"),
			"block_context_effect should build a block tag key from the data field.");
		assertTrue(scan.contains("state.is(blockTag)"),
			"block_context_effect should match blocks through the configured tag.");
		assertTrue(source.contains("BlockStateProperties.LIT") && source.contains("def.requiresLit()"),
			"block_context_effect should support a lit-state guard for forge/campfire contexts.");
		assertTrue(source.contains("PassiveEffectRefresher.refresh(player, def.effect(), def.durationTicks(), def.amplifier(), def.refreshTicks())"),
			"block_context_effect should refresh through the shared passive flags and its scan cadence.");
	}

	@Test
	void useItemWindowRunsFromServerItemUseEventWithPerBehaviorCooldowns() throws IOException {
		String data = read(DATA_BEHAVIORS);
		String router = read(PALETTE_ITEM_USE);
		String init = read(ATTUNED);

		assertTrue(init.contains("PaletteItemUse.init();"),
			"Attuned should register the use_item_window item-use event bridge during initialization.");
		assertTrue(router.contains("UseItemCallback.EVENT.register(PaletteItemUse::useItem)"),
			"use_item_window should run from Fabric's server-side item-use event.");
		assertTrue(router.contains("!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)"),
			"use_item_window should ignore client-side item-use callbacks.");
		assertTrue(router.contains("Attunement.activeSlots(serverPlayer)"),
			"use_item_window should inspect active Foci only when an item is used.");
		assertTrue(router.contains("AttunedRegistries.getBehavior(behaviorId, serverPlayer.registryAccess())"),
			"use_item_window should resolve data behaviors through the same code-first/data fallback path.");
		assertTrue(router.contains("behavior instanceof UseItemWindowBehavior useItem"),
			"use_item_window should dispatch only to the matching palette runtime.");
		assertFalse(router.contains("onTick("),
			"the item-use router must not create a per-tick inventory scan.");

		assertTrue(data.contains("static final class UseItemWindowBehavior implements FocusBehavior"),
			"use_item_window should build a passive FocusBehavior runtime.");
		assertTrue(data.contains("private final Identifier behaviorId"),
			"use_item_window cooldowns/modifier ids should be qualified by behavior id.");
		assertTrue(data.contains("useItemWindowModifierId(behaviorId)"),
			"use_item_window transient modifiers should be keyed by behavior id.");
		assertTrue(data.contains("Map<UseItemWindowKey, Long> cooldownEndsAt"),
			"use_item_window should store cooldowns by behavior id and player id.");
		assertTrue(data.contains("UseItemWindowKey key = new UseItemWindowKey(behaviorId, player.getUUID())"),
			"use_item_window should key cooldowns and modifier windows by behavior id plus player.");
		assertTrue(data.contains("if (now < cooldownEndsAt.getOrDefault(key, 0L))"),
			"use_item_window should fail closed while its per-player cooldown is active.");
		assertTrue(data.contains("cooldownEndsAt.put(key, now + def.cooldownTicks())"),
			"use_item_window should start cooldown after a valid use.");
		assertTrue(data.contains("def.condition().isPresent() && !def.condition().get().test(player)"),
			"use_item_window should honor its optional FocusCondition.");
		assertTrue(data.contains("stack.is(TagKey.create(Registries.ITEM, itemTag))"),
			"use_item_window should support item-tag matching without scanning inventory.");
		assertTrue(data.contains("PassiveEffectRefresher.apply(player, effect, def.durationTicks(), def.amplifier())"),
			"use_item_window should use shared passive effect flags.");
		assertTrue(data.contains("activeModifiers.put(key, new ActiveModifier"),
			"use_item_window should track transient modifier windows for cleanup.");
		assertTrue(data.contains("removeModifier(modifierId)"),
			"use_item_window should remove transient modifiers when the window ends or deactivates.");
	}

	@Test
	void markedTargetRunsThroughCombatHookWithPerBehaviorMarksAndCleanup() throws IOException {
		String data = read(DATA_BEHAVIORS);
		String palette = read(PALETTE_COMBAT);
		String combat = read(ATTUNED_COMBAT);

		assertTrue(data.contains("public static final class MarkedTargetBehavior implements FocusBehavior"),
			"marked_target should build a combat-facing runtime behavior.");
		assertTrue(data.contains("private final Identifier behaviorId"),
			"marked_target mark/cooldown keys should include the behavior id.");
		assertTrue(data.contains("Map<MarkedTargetKey, MarkedTargetMark> marks"),
			"marked_target should store short-lived marks by behavior/attacker/target.");
		assertTrue(data.contains("Map<MarkedTargetCooldownKey, Long> markedTargetCooldownEndsAt"),
			"marked_target should store cooldowns by behavior and attacker.");
		assertTrue(data.contains("MarkedTargetKey key = new MarkedTargetKey(behaviorId, attacker.getUUID(), victim.getUUID())"),
			"marked_target marks should be keyed by behavior id, attacker id, and target id.");
		assertTrue(data.contains("if (now < markedTargetCooldownEndsAt.getOrDefault(cooldownKey, 0L))"),
			"marked_target should fail closed while its per-behavior cooldown is active.");
		assertTrue(data.contains("marks.put(key, new MarkedTargetMark(now + def.markDurationTicks()))"),
			"marked_target should create a short-lived mark after the prime trigger.");
		assertTrue(data.contains("marks.remove(key)") && data.contains("markedTargetCooldownEndsAt.put(cooldownKey, now + def.cooldownTicks())"),
			"marked_target should consume marks and start cooldown after a valid consume trigger.");
		assertTrue(data.contains("AttunedPlayerCleanup.onForget(playerId ->"),
			"marked_target should clear per-player mark/cooldown state on disconnect.");
		assertTrue(data.contains("AttunedServerCleanup.onStop(() ->"),
			"marked_target should clear server-lifetime state on stop.");
		assertTrue(data.contains("effectOnConsume().targetSelf() ? attacker : victim"),
			"marked_target should support attacker- or victim-targeted consume effects.");

		assertTrue(palette.contains("behavior instanceof MarkedTargetBehavior markedTarget"),
			"PaletteCombat should dispatch to marked_target behaviors from the existing hit hook.");
		assertTrue(palette.contains("markedTarget.onChargedMeleeHit(player, defender, now)"),
			"marked_target should receive charged melee hits from PaletteCombat.");
		assertTrue(palette.contains("AttunedCombat.isChargedDirectMelee(player, defender, source, markedTarget.chargeThreshold())"),
			"marked_target should reuse the shared charged direct melee guard.");
		assertTrue(palette.contains("CombatTargets.isHostileOrPvpOpponent(defender, player)"),
			"marked_target should reuse the shared hostile/PvP target guard.");
		assertFalse(palette.contains("@At"), "marked_target must not introduce a mixin injection point.");
		assertFalse(palette.contains("Mixin"), "marked_target must run through the existing event, not a mixin.");

		assertTrue(methodBody(combat,
				"private static void afterDamage(LivingEntity defender, DamageSource source,")
				.contains("PaletteCombat.onMeleeHit("),
			"marked_target must share the existing AFTER_DAMAGE palette combat hook.");
	}

	@Test
	void navigationHintUsesStoredTargetsAndRestrainedActionBarFeedback() throws IOException {
		String data = read(DATA_BEHAVIORS);
		String networking = read(CIRCLE_NETWORKING);
		String runtime = read(CIRCLE_RUNTIME);
		String targets = read(CIRCLE_NAVIGATION_TARGETS);

		assertTrue(data.contains("static final class NavigationHintBehavior implements FocusBehavior"),
			"navigation_hint should build a passive FocusBehavior runtime.");
		assertTrue(data.contains("private final Identifier behaviorId"),
			"navigation_hint cached targets should be qualified by behavior id.");
		assertTrue(data.contains("Map<NavigationHintCacheKey, NavigationHintTarget> navigationHintTargets"),
			"navigation_hint should cache explicit stored targets by behavior/player.");
		assertTrue(data.contains("player.tickCount % def.feedbackCadenceTicks()"),
			"navigation_hint should throttle feedback with feedback_cadence_ticks.");
		assertTrue(data.contains("def.sameDimensionOnly()")
				&& data.contains("target.dimension().equals(player.level().dimension())"),
			"navigation_hint should enforce same_dimension_only before showing directional feedback.");
		assertTrue(data.contains("now - target.observedAtTick() > def.maxAgeTicks()"),
			"navigation_hint should expire stale stored targets with max_age_ticks.");
		assertTrue(data.contains("CircleNavigationTargets.latestPingFor(player, now)"),
			"navigation_hint should read accepted Circle pings from server-owned target state.");
		assertTrue(data.contains("held.get(DataComponents.LODESTONE_TRACKER)")
				&& data.contains("tracker.target()"),
			"navigation_hint should read explicit vanilla lodestone compass targets.");
		assertTrue(data.contains("ActionBarMessages.send(player, ActionBarMessages.Priority.AMBIENT"),
			"navigation_hint should use restrained ambient action-bar feedback.");
		assertFalse(data.contains("findNearestMapStructure"),
			"navigation_hint must never locate structures live.");
		assertFalse(data.contains("locateStructure"),
			"navigation_hint must never locate structures live.");
		assertFalse(data.contains("getChunk("),
			"navigation_hint must not scan chunks for navigation targets.");

		assertTrue(runtime.contains("CircleNavigationTargets.init();"),
			"Circle runtime should initialize the accepted-ping target cache.");
		assertTrue(networking.contains("CircleNavigationTargets.rememberPing(circle, player.level().dimension(), pos, now, now + PING_VISIBLE_TICKS)"),
			"Circle networking should store only validated accepted pings as navigation targets.");
		assertTrue(targets.contains("AttunedServerCleanup.onStop(LATEST_PINGS::clear)"),
			"Circle navigation targets should clear server-lifetime ping state on stop.");
		assertTrue(targets.contains("Optional<CirclePolicy.Circle> maybeCircle = CircleRuntime.manager().circleOf(player.getUUID())"),
			"Circle navigation targets should only expose pings to current Circle members.");
	}

	@Test
	void partyAssistUsesCircleContributionWindowsCooldownsAndBeneficialTargetFilters() throws IOException {
		String data = read(DATA_BEHAVIORS);
		String contributions = read(CIRCLE_CONTRIBUTIONS);

		assertTrue(data.contains("import dev.attuned.combat.CombatTargets;"),
			"party_assist should reuse the central PvP safety gate.");
		assertTrue(data.contains("AttunedConfig.get().partyEffectsEnabled()"),
			"party_assist should honor the explicit party-effects server toggle.");
		assertTrue(data.contains("import dev.attuned.party.CircleContributions;"),
			"party_assist should reuse the server-owned Circle contribution ledger.");
		assertTrue(data.contains("import dev.attuned.party.CircleEffectCooldowns;"),
			"party_assist should reuse the party-effect cooldown helper.");
		assertTrue(data.contains("static final int PARTY_ASSIST_SCAN_CADENCE_TICKS = 20"),
			"party_assist should scan on a restrained cadence instead of every tick.");
		assertTrue(data.contains("private static final CircleEffectCooldowns partyAssistCooldowns = new CircleEffectCooldowns()"),
			"party_assist cooldowns should be Circle-scoped and tombstone-backed.");
		assertTrue(data.contains("static final class PartyAssistBehavior implements FocusBehavior"),
			"party_assist should build a passive FocusBehavior runtime.");
		assertTrue(data.contains("player.tickCount % PARTY_ASSIST_SCAN_CADENCE_TICKS"),
			"party_assist should throttle target scans.");
		assertTrue(data.contains("CircleRuntime.manager().circleOf(player.getUUID())"),
			"party_assist should require server-owned Circle membership.");
		assertTrue(data.contains("partyAssistCooldowns.ready(circle, behaviorId.toString(), now)"),
			"party_assist should fail closed while the Circle behavior cooldown is active.");
		assertTrue(data.contains("CircleContributions.eligibleAssistTargets(player, def.radius())"),
			"party_assist should use the data-defined radius for nearby Circle member targeting.");
		assertTrue(data.contains(".anyMatch(member -> member.getUUID().equals(player.getUUID()))"),
			"party_assist should require the wearer to be a nearby Circle member.");
		assertTrue(data.contains("target.getUUID().equals(player.getUUID())"),
			"party_assist should assist nearby Circle members, not self-buff the wearer.");
		assertTrue(data.contains("CombatTargets.canAffectPlayer(player, target) || CombatTargets.canAffectPlayer(target, player)"),
			"party_assist should dampen PvP pressure by refusing targets that are valid PvP opponents.");
		assertTrue(data.contains("target.getHealth() <= target.getMaxHealth() * 0.5F"),
			"party_assist wounded_members should target low-health allies.");
		assertTrue(data.contains("target.isUnderWater() && target.getAirSupply() <= target.getMaxAirSupply() / 2"),
			"party_assist drowning_members should target underwater allies with low air.");
		assertTrue(data.contains("PassiveEffectRefresher.apply(target, effect.effect(), effect.durationTicks(), effect.amplifier())"),
			"party_assist should apply its beneficial effect through the shared passive flags.");
		assertTrue(data.contains("recordSupportContribution(player)"),
			"party_assist should refresh the helper's contribution window after a need-based support assist lands.");
		assertTrue(data.contains("def.targetFilter() != FocusBehaviorDef.PartyAssistTargetFilter.NEARBY_MEMBERS"),
			"Generic nearby-member assists should not refresh contribution credit by themselves.");
		assertTrue(data.contains("CircleContributions.recordContribution(player)"),
			"Need-based party assists should feed the server-owned contribution ledger for support play.");
		assertTrue(data.contains("ActionBarMessages.send(target, ActionBarMessages.Priority.AMBIENT"),
			"party_assist should send restrained ambient feedback to the assisted target.");
		assertTrue(data.contains("Component.translatable(def.messageKey(), player.getDisplayName())"),
			"party_assist feedback should be pack-localizable and identify the assisting wearer.");
		assertTrue(data.contains("partyAssistCooldowns.start(circle, behaviorId.toString(), now, def.cooldownTicks())"),
			"party_assist should start its Circle cooldown only after an effect lands.");
		assertTrue(data.contains("AttunedServerCleanup.onStop(partyAssistCooldowns::clear)"),
			"party_assist server-lifetime cooldown state should clear on stop.");

		assertTrue(contributions.contains("public static java.util.List<ServerPlayer> eligibleAssistTargets(ServerPlayer player, double radius)"),
			"Circle contributions should expose a party-assist helper with a data-defined radius.");
		assertTrue(contributions.contains("public static java.util.List<ServerPlayer> nearbyCircleMembersForAssist(ServerPlayer player, double radius)"),
			"Circle contributions should expose a proximity-only assist helper without contribution windows.");
		assertTrue(contributions.contains("AttunedConfig.get().partyEffectsEnabled()"),
			"The party-assist helper should honor the party-effects toggle instead of shared-credit.");
	}

	@Test
	void navigationHintMessagesHaveTranslations() throws IOException {
		String data = read(DATA_BEHAVIORS);
		String lang = read(Path.of("src/main/resources/assets/attuned/lang/en_us.json"));

		assertTrue(data.contains("Component.translatable(\"message.attuned.navigation_hint\""),
			"navigation_hint should use a translatable action-bar message.");
		assertTrue(lang.contains("\"message.attuned.navigation_hint\""),
			"navigation_hint action-bar copy should have a lang entry.");
	}

	@Test
	void paletteNumericBoundsAreEnforcedByCodecAndRecordConstructors() throws IOException {
		String source = read(Path.of("src/main/java/dev/attuned/api/focus/FocusBehaviorDef.java"));

		assertTrue(source.contains("Codec.intRange(MIN_AMPLIFIER, MAX_AMPLIFIER)"),
			"Effect amplifier fields should be codec-bounded.");
		assertTrue(source.contains("Codec.intRange(MIN_DURATION, MAX_DURATION)"),
			"Effect duration fields should be codec-bounded.");
		assertTrue(source.contains("Codec.floatRange(MIN_CHARGE, MAX_CHARGE)"),
			"On-hit charge threshold should be codec-bounded.");
		assertTrue(source.contains("if (refreshTicks < MIN_REFRESH || refreshTicks > MAX_DURATION)"),
			"Refresh ticks should be upper-bounded in record constructors as well as codecs.");
	}

	@Test
	void onHitEffectRunsThroughTheLiveCombatGuardsWithNoMixin() throws IOException {
		String palette = read(PALETTE_COMBAT);
		String combat = read(ATTUNED_COMBAT);

		assertTrue(palette.contains("AttunedCombat.isChargedDirectMelee("),
			"on_hit dispatch should reuse the shared charged-melee guard.");
		assertTrue(palette.contains("CombatTargets.isHostileOrPvpOpponent("),
			"on_hit dispatch should reuse the shared hostile/PvP target guard.");
		assertFalse(palette.contains("@At"), "on_hit must not introduce a mixin injection point.");
		assertFalse(palette.contains("Mixin"), "on_hit must run through the existing event, not a mixin.");

		assertTrue(methodBody(combat,
				"private static void afterDamage(LivingEntity defender, DamageSource source,")
				.contains("PaletteCombat.onMeleeHit("),
			"on_hit palette behaviors must proc from the existing AFTER_DAMAGE handler.");
	}

	@Test
	void paletteFactoriesStayPassiveOnly() throws IOException {
		String source = read(DATA_BEHAVIORS);

		assertEquals(0, count(source, "hasActiveAbility"),
			"Palette behaviors are passive-only (v2 defers active-ability authoring).");
		assertEquals(0, count(source, "onAbility"),
			"Palette behaviors are passive-only (v2 defers active-ability authoring).");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected source file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static int count(String value, String needle) {
		int total = 0;
		int index = 0;
		while ((index = value.indexOf(needle, index)) >= 0) {
			total++;
			index += needle.length();
		}
		return total;
	}

	private static String squash(String value) {
		return value.replaceAll("\\s+", " ");
	}

	private static String methodBody(String source, String signaturePrefix) {
		return methodBody(source, signaturePrefix, 1);
	}

	private static String methodBody(String source, String signaturePrefix, int occurrence) {
		int signatureStart = source.indexOf(signaturePrefix);
		for (int found = 1; found < occurrence && signatureStart >= 0; found++) {
			signatureStart = source.indexOf(signaturePrefix, signatureStart + signaturePrefix.length());
		}
		assertTrue(signatureStart >= 0, "Missing method signature: " + signaturePrefix);
		int bodyStart = source.indexOf('{', signatureStart);
		assertTrue(bodyStart >= 0, "Missing method body: " + signaturePrefix);
		int depth = 0;
		for (int index = bodyStart; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '{') {
				depth++;
			} else if (current == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(bodyStart, index + 1);
				}
			}
		}
		throw new AssertionError("Unterminated method body: " + signaturePrefix);
	}
}
