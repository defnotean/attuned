package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.attunement.BudgetResolver;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.combat.CombatTargets;
import dev.attuned.content.FactionSetBonusResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Faction set bonuses — three or more ACTIVE Foci sharing a faction grant a
 * small, free passive perk for that faction. Server-only, applied on a throttled
 * server tick: each perk is a modest vanilla {@link MobEffectInstance} kept
 * refreshed (ambient + icon-hidden, like {@link MossheartBehavior}) so it never
 * stacks or flashes the HUD. A couple of perks are reactive windows gated by a
 * per-player cooldown map.
 *
 * <p>The active faction ids are derived from {@link Attunement#resolution} and
 * {@link FocusDefinition#faction()} and reduced through the pure
 * {@link FactionSetBonusResolver}; the runtime here only decides how each woken
 * faction's perk applies.</p>
 */
public final class FactionSetBonuses {
	private static final String FACTION_UNSEEN = "attuned:unseen";
	private static final String FACTION_SEAFARERS = "attuned:seafarers";
	private static final String FACTION_OFFSHORE = "attuned:offshore";
	private static final String FACTION_RADIANT = "attuned:radiant";
	private static final String FACTION_RELIQUARY = "attuned:reliquary";
	private static final String FACTION_VERDANT_CHOIR = "attuned:verdant_choir";
	private static final String FACTION_ASHEN_FORGE = "attuned:ashen_forge";
	private static final String FACTION_REVENANT = "attuned:revenant";
	private static final String FACTION_TIDEBORN = "attuned:tideborn";
	private static final String FACTION_FORGEBOUND = "attuned:forgebound";
	private static final String FACTION_WILDROOT = "attuned:wildroot";
	private static final String FACTION_UMBRAL = "attuned:umbral";
	private static final String FACTION_DEEP_LANTERNS = "attuned:deep_lanterns";

	/** Refreshed passive perks aim well above one throttle window so they never lapse. */
	private static final int PASSIVE_DURATION = 100;
	/** Short reactive windows for the cooldown-gated perks. */
	private static final int RADIANT_REGEN_TICKS = 80;
	private static final int RADIANT_LIGHT_LEVEL = 12;
	private static final long RADIANT_COOLDOWN_TICKS = 1200L;
	private static final int VERDANT_SATURATION_TICKS = 20;
	private static final long VERDANT_COOLDOWN_TICKS = 1200L;
	private static final int REVENANT_SLOWNESS_TICKS = 60;
	private static final double REVENANT_RADIUS = 5.0D;
	/** Matches Nightsworn pact darkness threshold ({@link dev.attuned.pacts.Pacts}). */
	private static final int UMBRAL_MAX_LIGHT = 7;

	/** Per-player game-time of the last grant for each cooldown-gated faction. */
	private static final Map<UUID, Long> RADIANT_COOLDOWNS = new HashMap<>();
	private static final Map<UUID, Long> VERDANT_COOLDOWNS = new HashMap<>();

	private static int tickCounter;
	private static boolean initialized;

	private FactionSetBonuses() {}

	/** Registers the throttled set-bonus applier and its cleanup hooks once. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;
		AttunedPlayerCleanup.onForget(RADIANT_COOLDOWNS::remove);
		AttunedPlayerCleanup.onForget(VERDANT_COOLDOWNS::remove);
		AttunedServerCleanup.onStop(() -> {
			RADIANT_COOLDOWNS.clear();
			VERDANT_COOLDOWNS.clear();
			tickCounter = 0;
		});
		ServerTickEvents.END_SERVER_TICK.register(FactionSetBonuses::tick);
	}

	private static void tick(MinecraftServer server) {
		if (tickCounter++ % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			applyTo(player);
		}
	}

	private static void applyTo(ServerPlayer player) {
		Set<String> factions = FactionSetBonusResolver.activeSetFactions(
			activeFactionIds(player), FactionSetBonusResolver.DEFAULT_THRESHOLD);
		if (factions.isEmpty()) {
			return;
		}
		for (String faction : factions) {
			applyPerk(player, faction);
		}
	}

	/** The faction id (or {@code ""} for none) of each ACTIVE Focus, in slot order. */
	private static List<String> activeFactionIds(ServerPlayer player) {
		BudgetResolver.Resolution resolution = Attunement.resolution(player);
		AttunedInv inv = AttunedAttachments.getInventory(player);
		List<Integer> activeSlots = resolution.activeSlots();
		List<String> factionIds = new ArrayList<>(activeSlots.size());
		for (int slot : activeSlots) {
			Optional<FocusDefinition> definition = Attunement.definitionFor(player, inv.get(slot));
			factionIds.add(definition
				.flatMap(FocusDefinition::faction)
				.map(id -> id.toString())
				.orElse(""));
		}
		return factionIds;
	}

	private static void applyPerk(ServerPlayer player, String faction) {
		switch (faction) {
			case FACTION_UNSEEN -> {
				if (player.isCrouching()) {
					refresh(player, MobEffects.MOVEMENT_SPEED);
				}
			}
			case FACTION_SEAFARERS -> {
				if (nearWater(player)) {
					refresh(player, MobEffects.LUCK);
				}
			}
			case FACTION_OFFSHORE -> {
				if (player.isUnderWater()) {
					refresh(player, MobEffects.WATER_BREATHING);
				}
			}
			case FACTION_RADIANT -> tryRadiant(player);
			case FACTION_RELIQUARY -> refresh(player, MobEffects.LUCK);
			case FACTION_VERDANT_CHOIR -> tryVerdant(player);
			case FACTION_ASHEN_FORGE -> {
				if (nearHeat(player)) {
					refresh(player, MobEffects.FIRE_RESISTANCE);
				}
			}
			case FACTION_REVENANT -> chillNearbyUndead(player);
			case FACTION_TIDEBORN -> {
				if (nearWater(player)) {
					refresh(player, MobEffects.DOLPHINS_GRACE);
				}
			}
			case FACTION_FORGEBOUND -> {
				if (nearHeat(player)) {
					refresh(player, MobEffects.DIG_SPEED);
				}
			}
			case FACTION_WILDROOT -> {
				if (onGrass(player)) {
					refresh(player, MobEffects.SLOW_FALLING);
				}
			}
			case FACTION_UMBRAL -> {
				if (inDark(player)) {
					refresh(player, MobEffects.MOVEMENT_SPEED);
				}
			}
			case FACTION_DEEP_LANTERNS -> {
				if (nearWater(player) || nearLantern(player)) {
					refresh(player, MobEffects.NIGHT_VISION);
				}
			}
			default -> { /* Unknown faction: no perk. */ }
		}
	}

	/** Radiant: a brief Regeneration window when standing in strong light, on cooldown. */
	private static void tryRadiant(ServerPlayer player) {
		if (player.level().getMaxLocalRawBrightness(player.blockPosition()) < RADIANT_LIGHT_LEVEL) {
			return;
		}
		if (onCooldown(RADIANT_COOLDOWNS, player, RADIANT_COOLDOWN_TICKS)) {
			return;
		}
		player.addEffect(new MobEffectInstance(
			MobEffects.REGENERATION, RADIANT_REGEN_TICKS, 0, true, false, false));
		RADIANT_COOLDOWNS.put(player.getUUID(), player.level().getGameTime());
	}

	/** Verdant Choir: a saturation tick while standing on grass, on cooldown. */
	private static void tryVerdant(ServerPlayer player) {
		if (!onGrass(player)) {
			return;
		}
		if (onCooldown(VERDANT_COOLDOWNS, player, VERDANT_COOLDOWN_TICKS)) {
			return;
		}
		player.addEffect(new MobEffectInstance(
			MobEffects.SATURATION, VERDANT_SATURATION_TICKS, 0, true, false, false));
		VERDANT_COOLDOWNS.put(player.getUUID(), player.level().getGameTime());
	}

	/** Revenant: nearby undead are chilled with Slowness, reusing Bonechill's shape. */
	private static void chillNearbyUndead(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		AABB area = player.getBoundingBox().inflate(REVENANT_RADIUS);
		List<LivingEntity> undead = level.getEntitiesOfClass(LivingEntity.class, area, entity ->
			entity != player && entity.isAlive() && entity.typeHolder().is(EntityTypeTags.UNDEAD)
				&& CombatTargets.isHostileOrPvpOpponent(entity, player));
		for (LivingEntity entity : undead) {
			entity.addEffect(new MobEffectInstance(
				MobEffects.MOVEMENT_SLOWDOWN, REVENANT_SLOWNESS_TICKS, 0, true, false, true));
		}
	}

	private static boolean onCooldown(Map<UUID, Long> cooldowns, ServerPlayer player, long cooldownTicks) {
		Long last = cooldowns.get(player.getUUID());
		return last != null && player.level().getGameTime() - last < cooldownTicks;
	}

	private static void refresh(ServerPlayer player, Holder<MobEffect> effect) {
		PassiveEffectRefresher.refresh(player, effect, PASSIVE_DURATION, 0, true, false, false);
	}

	private static boolean inDark(ServerPlayer player) {
		return player.level().getMaxLocalRawBrightness(player.blockPosition()) <= UMBRAL_MAX_LIGHT;
	}

	private static boolean nearWater(ServerPlayer player) {
		BlockPos pos = player.blockPosition();
		return player.level().getFluidState(pos).is(FluidTags.WATER)
			|| player.level().getFluidState(pos.below()).is(FluidTags.WATER)
			|| player.level().getFluidState(pos.north()).is(FluidTags.WATER)
			|| player.level().getFluidState(pos.south()).is(FluidTags.WATER)
			|| player.level().getFluidState(pos.east()).is(FluidTags.WATER)
			|| player.level().getFluidState(pos.west()).is(FluidTags.WATER);
	}

	private static boolean onGrass(ServerPlayer player) {
		BlockState feet = player.level().getBlockState(player.blockPosition());
		BlockState below = player.level().getBlockState(player.blockPosition().below());
		return isGrass(feet) || isGrass(below);
	}

	private static boolean isGrass(BlockState state) {
		return state.is(Blocks.GRASS_BLOCK)
			|| state.is(Blocks.SHORT_GRASS)
			|| state.is(Blocks.TALL_GRASS);
	}

	private static boolean nearHeat(ServerPlayer player) {
		BlockPos origin = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(
				origin.offset(-4, -2, -4), origin.offset(4, 2, 4))) {
			if (isHeat(player.level().getBlockState(pos))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isHeat(BlockState state) {
		return isLitFurnace(state)
			|| state.is(Blocks.MAGMA_BLOCK)
			|| state.is(Blocks.LAVA)
			|| state.getFluidState().is(FluidTags.LAVA);
	}

	private static boolean isLitFurnace(BlockState state) {
		return (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER))
			&& state.getValue(AbstractFurnaceBlock.LIT);
	}

	private static boolean nearLantern(ServerPlayer player) {
		BlockPos origin = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(
				origin.offset(-4, -2, -4), origin.offset(4, 2, 4))) {
			BlockState state = player.level().getBlockState(pos);
			if (state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)) {
				return true;
			}
		}
		return false;
	}
}
