package dev.attuned.combat;

import dev.attuned.compat.ParticleCompat;

import dev.attuned.compat.AfterDamageCallback;

import dev.attuned.AttunedConfig;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.AffinityColors;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.content.behavior.DreadfangBehavior;
import dev.attuned.content.behavior.TemperBehavior;
import dev.attuned.party.CircleContributions;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The rock-paper-scissors counter-combat system.
 *
 * <p>Two effects layer onto every melee exchange between living entities:
 * <ol>
 *   <li><b>Affinity matchup</b> — damage is scaled when one combatant's
 *       {@link Affinity} counters the other's. Applied inside
 *       {@code LivingEntityHurtMixin}, which calls {@link #applyAffinity}.</li>
 *   <li><b>Focus procs</b> — the Thornward and Leech Foci have no attribute
 *       modifiers; their entire effect (reflect / lifesteal) fires from the
 *       {@code AFTER_DAMAGE} event handler registered in {@link #init()}.</li>
 * </ol>
 *
 * <p>The matchup scaling deliberately lives in a Mixin rather than an event:
 * the Fabric API in use exposes only {@code ALLOW_DAMAGE} (a veto) and
 * {@code AFTER_DAMAGE} (post-application) — neither can rescale the incoming
 * amount. The Mixin modifies the {@code float} amount argument of
 * {@code LivingEntity.hurtServer} before armour and absorption are applied.
 */
public final class AttunedCombat {
	private AttunedCombat() {}

	/** Damage multiplier when the attacker counters the defender. */
	private static final float THORNWARD_REFLECT = 0.25F;
	/** Fraction of damage dealt a Leech attacker recovers as health. */
	private static final float LEECH_LIFESTEAL = 0.20F;
	/** Extra direct-melee damage Cinder deals to burning enemies. */
	private static final float CINDER_BURNING_BONUS = 0.20F;
	/** Extra fully charged direct-melee damage Sunlance deals to its marked prey. */
	private static final float SUNLANCE_BONUS = 0.10F;
	/** Extra fully charged direct-melee damage Temper grants after recent forge work. */
	private static final float TEMPER_BONUS = 0.08F;
	/** Sunlance asks for a deliberate swing, not spam-click pressure. */
	private static final float SUNLANCE_CHARGED_SWING_THRESHOLD = 0.9F;
	/** Temper asks for the same deliberate, fully charged swing cadence. */
	private static final float TEMPER_CHARGED_SWING_THRESHOLD = 0.9F;
	/** Minimum delay between repeated affinity feedback bursts for one mob. */
	private static final long AFFINITY_SPARK_COOLDOWN_TICKS = 40L;
	/** Maximum age for cached mob feedback throttles. */
	private static final long AFFINITY_SPARK_CACHE_TTL_TICKS = 20L * 60L;

	private static final ResourceLocation THORNWARD_FOCUS =
		new ResourceLocation("attuned", "thornward_focus");
	private static final ResourceLocation LEECH_FOCUS =
		new ResourceLocation("attuned", "leech_focus");
	private static final ResourceLocation CINDER_FOCUS =
		new ResourceLocation("attuned", "cinder_focus");
	private static final ResourceLocation SUNLANCE_FOCUS =
		new ResourceLocation("attuned", "sunlance_focus");
	private static final ResourceLocation DREADFANG_FOCUS =
		new ResourceLocation("attuned", "dreadfang_focus");

	/** Re-entrancy guard so a reflected hit cannot trigger another reflection. */
	private static final ThreadLocal<Boolean> REFLECTING = ThreadLocal.withInitial(() -> false);
	/** Last game-time a mob affinity spark was shown for an entity. */
	private static final Map<UUID, Long> LAST_AFFINITY_SPARK = new HashMap<>();
	private static final Map<UUID, MeleeChargeSnapshot> MELEE_CHARGE_SNAPSHOTS = new HashMap<>();
	private static final long MELEE_CHARGE_SNAPSHOT_TTL_TICKS = 2L;
	private static long lastAffinitySparkPrune;
	private static boolean initialized;

	public record MeleeChargeSnapshot(UUID targetId, float charge, long gameTime) {}

	/** Registers the combat event handlers. Called from the mod initializer. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		AfterDamageCallback.EVENT.register(AttunedCombat::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) ->
			LAST_AFFINITY_SPARK.remove(entity.getUUID()));
		AttunedPlayerCleanup.onForget(MELEE_CHARGE_SNAPSHOTS::remove);
		AttunedServerCleanup.onStop(() -> {
			LAST_AFFINITY_SPARK.clear();
			MELEE_CHARGE_SNAPSHOTS.clear();
			lastAffinitySparkPrune = 0L;
		});
	}

	public static void rememberMeleeCharge(Player attacker, Entity target, float charge) {
		MELEE_CHARGE_SNAPSHOTS.put(attacker.getUUID(),
			new MeleeChargeSnapshot(target.getUUID(), charge, attacker.getLevel().getGameTime()));
	}

	public static boolean isReflecting() {
		return REFLECTING.get();
	}

	/**
	 * Scales one incoming hit by the affinity matchup and shows feedback for it.
	 * Invoked by {@code LivingEntityHurtMixin} before armour and absorption.
	 */
	public static float applyAffinity(ServerLevel level, LivingEntity defender,
			DamageSource source, float amount) {
		return applyAffinity(level, defender, source, amount, CombatContext.of(defender, source));
	}

	public static float applyAffinity(ServerLevel level, LivingEntity defender,
			DamageSource source, float amount, CombatContext context) {
		if (AttunedCombat.isReflecting()) {
			return amount;
		}
		float multiplier = affinityMultiplier(defender, source, context);
		if (multiplier != 1.0F) {
			matchupFeedback(level, defender, source, multiplier, context);
		}
		if (context.attacker() instanceof Player && !(defender instanceof Player)) {
			mobAffinitySpark(level, defender);
		}
		float adjusted = DamageFormula.multiply(amount, multiplier);
		if (cinderApplies(defender, source, context)) {
			adjusted = DamageFormula.amplify(adjusted, CINDER_BURNING_BONUS);
		}
		if (sunlanceApplies(defender, source, context)) {
			adjusted = DamageFormula.amplify(adjusted, SUNLANCE_BONUS);
		}
		if (temperApplies(defender, source, context)) {
			adjusted = DamageFormula.amplify(adjusted, TEMPER_BONUS);
		}
		return adjusted;
	}

	private static boolean cinderApplies(LivingEntity defender, DamageSource source,
			CombatContext context) {
		if (!(context.attacker() instanceof Player player) || !isDirectMelee(player, source)) {
			return false;
		}
		if (!CombatTargets.isHostileOrPvpOpponent(defender, player)) {
			return false;
		}
		return defender.isOnFire() && context.hasActiveFocus(player, CINDER_FOCUS);
	}

	private static boolean sunlanceApplies(LivingEntity defender, DamageSource source,
			CombatContext context) {
		if (!(context.attacker() instanceof Player player) || !isChargedDirectMelee(player, defender, source,
				SUNLANCE_CHARGED_SWING_THRESHOLD) || !context.hasActiveFocus(player, SUNLANCE_FOCUS)) {
			return false;
		}
		if (!CombatTargets.isHostileOrPvpOpponent(defender, player)) {
			return false;
		}
		return defender.getMobType() == MobType.UNDEAD
			|| context.affinityOf(defender).filter(affinity -> affinity == Affinity.FURY).isPresent();
	}

	private static boolean temperApplies(LivingEntity defender, DamageSource source,
			CombatContext context) {
		if (!(context.attacker() instanceof Player player)
				|| !isChargedDirectMelee(player, defender, source, TEMPER_CHARGED_SWING_THRESHOLD)) {
			return false;
		}
		if (!CombatTargets.isHostileOrPvpOpponent(defender, player)) {
			return false;
		}
		return TemperBehavior.applies(player);
	}

	public static boolean isChargedDirectMelee(Player attacker, LivingEntity defender,
			DamageSource source, float threshold) {
		return isDirectMelee(attacker, source)
			&& meleeCharge(attacker, defender) >= threshold;
	}

	private static float meleeCharge(Player attacker, LivingEntity defender) {
		MeleeChargeSnapshot snapshot = MELEE_CHARGE_SNAPSHOTS.get(attacker.getUUID());
		if (snapshot == null || !snapshot.targetId().equals(defender.getUUID())) {
			return 0.0F;
		}
		long now = attacker.getLevel().getGameTime();
		if (now - snapshot.gameTime() > MELEE_CHARGE_SNAPSHOT_TTL_TICKS) {
			MELEE_CHARGE_SNAPSHOTS.remove(attacker.getUUID());
			return 0.0F;
		}
		return snapshot.charge();
	}

	private static boolean isDirectMelee(LivingEntity attacker, DamageSource source) {
		if (source.getDirectEntity() != attacker) {
			return false;
		}
		return !source.is(DamageTypeTags.IS_PROJECTILE)
			&& !source.is(DamageTypeTags.IS_EXPLOSION);
	}

	private static void mobAffinitySpark(ServerLevel level, LivingEntity entity) {
		Optional<Affinity> affinity = MobAffinities.of(entity);
		if (affinity.isEmpty()) {
			return;
		}
		UUID id = entity.getUUID();
		long now = level.getGameTime();
		pruneAffinitySparkCache(now);
		Long last = LAST_AFFINITY_SPARK.get(id);
		if (last != null && now - last < AFFINITY_SPARK_COOLDOWN_TICKS) {
			return;
		}
		LAST_AFFINITY_SPARK.put(id, now);
		level.sendParticles(ParticleCompat.dust(affinity.get().argb() & 0x00FFFFFF, 0.8F),
			entity.getX(), entity.getY() + entity.getBbHeight() * 0.7, entity.getZ(),
			4, 0.2, 0.25, 0.2, 0.0);
	}

	private static void pruneAffinitySparkCache(long now) {
		if (LAST_AFFINITY_SPARK.isEmpty()
				|| now - lastAffinitySparkPrune < AFFINITY_SPARK_CACHE_TTL_TICKS) {
			return;
		}
		lastAffinitySparkPrune = now;
		LAST_AFFINITY_SPARK.entrySet().removeIf(entry ->
			now - entry.getValue() >= AFFINITY_SPARK_CACHE_TTL_TICKS);
	}

	/**
	 * The affinity-matchup damage multiplier for one attacker→defender exchange.
	 * A combatant in Discord both deals and takes the advantage multiplier
	 * against anyone; otherwise the rock-paper-scissors cycle applies, and a
	 * matchup with no counter (or an unattuned combatant) leaves damage at 1.0.
	 */
	public static float affinityMultiplier(LivingEntity defender, DamageSource source) {
		return affinityMultiplier(defender, source, CombatContext.of(defender, source));
	}

	public static float affinityMultiplier(LivingEntity defender, DamageSource source,
			CombatContext context) {
		LivingEntity attacker = context.attacker();
		if (attacker == null || attacker == defender) {
			return 1.0F;
		}
		if (defender instanceof Player defenderPlayer
				&& Apex.suppressesIncomingAdvantage(defenderPlayer, attacker, context)) {
			return 1.0F;
		}
		// Discord — clashing affinities — is a glass cannon on both ends.
		if (context.isDiscord(attacker) || context.isDiscord(defender)) {
			return AttunedConfig.get().discordDamageMultiplier();
		}
		Optional<Affinity> attackerAffinity = context.affinityOf(attacker);
		Optional<Affinity> defenderAffinity = context.affinityOf(defender);
		if (attackerAffinity.isEmpty() || defenderAffinity.isEmpty()) {
			return 1.0F;
		}
		Affinity atk = attackerAffinity.get();
		Affinity def = defenderAffinity.get();
		if (atk.beats(def)) {
			return AttunedConfig.get().advantageMultiplier();
		}
		if (def.beats(atk)) {
			return AttunedConfig.get().disadvantageMultiplier();
		}
		return 1.0F;
	}

	/** Whether a combatant is a player in the Discord stance. */
	private static boolean isDiscord(LivingEntity entity) {
		return entity instanceof Player player && Attunement.isDiscord(player);
	}

	/**
	 * Shows the rock-paper-scissors outcome of a hit: an affinity-coloured spark
	 * and a sharp sound on an advantage, a dull puff and a weak sound on a
	 * disadvantage — so players can read the cycle.
	 */
	private static void matchupFeedback(ServerLevel level, LivingEntity defender,
			DamageSource source, float multiplier, CombatContext context) {
		double x = defender.getX();
		double y = defender.getY() + defender.getBbHeight() * 0.6;
		double z = defender.getZ();
		if (multiplier > 1.0F) {
			LivingEntity attacker = context.attacker();
			int color = attacker != null ? matchupColor(attacker, context) : 0xFFFFFF;
			level.sendParticles(ParticleCompat.dust(color, 1.0F), x, y, z, 10, 0.3, 0.3, 0.3, 0.0);
			level.playSound(null, defender.blockPosition(),
				SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7F, 1.2F);
		} else {
			level.sendParticles(ParticleTypes.POOF, x, y, z, 8, 0.25, 0.25, 0.25, 0.0);
			level.playSound(null, defender.blockPosition(),
				SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8F, 1.0F);
		}
	}

	/** The feedback colour for an attacker: its affinity colour, or the Discord magenta. */
	private static int matchupColor(LivingEntity attacker, CombatContext context) {
		if (context.isDiscord(attacker)) {
			return AffinityColors.DISCORD_RGB;
		}
		return context.affinityOf(attacker).map(AttunedCombat::affinityColor).orElse(0xFFFFFF);
	}

	/** The 24-bit RGB form of an affinity's display colour, for {@code DustParticleOptions}. */
	private static int affinityColor(Affinity affinity) {
		return affinity.argb() & 0x00FFFFFF;
	}

	/**
	 * Post-damage handler: applies the Thornward reflect and Leech lifesteal
	 * procs. {@code dealtDamage} is the damage actually taken after armour and
	 * absorption — both procs scale off the real figure.
	 */
	private static void afterDamage(LivingEntity defender, DamageSource source,
			float originalDamage, float dealtDamage, boolean blocked) {
		if (REFLECTING.get()) {
			return;
		}
		LivingEntity attacker = attackerOf(source);
		if (attacker == null || attacker == defender) {
			return;
		}
		recordCircleCombatContribution(defender, attacker);
		if (dealtDamage <= 0.0F) {
			return;
		}

		// Clamp to the victim's pool before scaling: a one-shot kill can report
		// far more dealt damage than the victim's health (Apex Execute applies a
		// 100000 sentinel), which would otherwise let Thornward/Leech reflect or
		// heal tens of thousands in PvP.
		float pooledDamage = Math.min(dealtDamage, defender.getMaxHealth());

		// Thornward: the defender reflects a fraction of the hit back.
		if (defender instanceof Player defenderPlayer
				&& hasActiveFocus(defenderPlayer, THORNWARD_FOCUS)
				&& isDirectMelee(attacker, source)
				&& CombatTargets.isHostileOrPvpOpponent(attacker, defenderPlayer)
				&& attacker.isAlive()) {
			float reflected = pooledDamage * THORNWARD_REFLECT;
			if (reflected > 0.0F && attacker.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
				REFLECTING.set(true);
				try {
					attacker.hurt(defender.damageSources().thorns(defenderPlayer), reflected);
				} finally {
					REFLECTING.set(false);
				}
			}
		}

		// Leech: the attacker heals for a fraction of the damage it dealt.
		if (attacker instanceof Player attackerPlayer
				&& hasActiveFocus(attackerPlayer, LEECH_FOCUS)
				&& isDirectMelee(attacker, source)
				&& CombatTargets.isHostileOrPvpOpponent(defender, attackerPlayer)
				&& !attackerPlayer.isDeadOrDying()) {
			attackerPlayer.heal(pooledDamage * LEECH_LIFESTEAL);
		}

		// Dreadfang: a fully charged direct-melee hit on a hostile/PvP target plunges the
		// victim into vanilla darkness. Shares the authoritative charge and target guards with
		// the code procs above (no mixin, no new event), mirroring Sunlance and Temper.
		if (attacker instanceof Player attackerPlayer
				&& hasActiveFocus(attackerPlayer, DREADFANG_FOCUS)
				&& isChargedDirectMelee(attackerPlayer, defender, source, DreadfangBehavior.CHARGED_SWING_THRESHOLD)
				&& CombatTargets.isHostileOrPvpOpponent(defender, attackerPlayer)) {
			DreadfangBehavior.applyTo(defender);
		}

		// Palette on-hit behaviors: datapack-defined attuned:on_hit_effect Foci proc here so they
		// share the live charge/target guards with the code procs above (no mixin, no new event).
		PaletteCombat.onMeleeHit(attacker, defender, source, dealtDamage);
	}

	private static void recordCircleCombatContribution(LivingEntity defender, LivingEntity attacker) {
		if (attacker instanceof ServerPlayer attackerPlayer
				&& CombatTargets.isHostileOrPvpOpponent(defender, attackerPlayer)) {
			CircleContributions.recordContribution(attackerPlayer);
		}
		if (defender instanceof ServerPlayer defenderPlayer
				&& CombatTargets.isHostileOrPvpOpponent(attacker, defenderPlayer)) {
			CircleContributions.recordContribution(defenderPlayer);
		}
	}

	/** The committed affinity of a living entity — player attunement or mob mapping. */
	static Optional<Affinity> affinityOf(LivingEntity entity) {
		return CombatTargets.affinityOf(entity);
	}

	/**
	 * The living attacker behind a damage source, or {@code null}. Prefers the
	 * responsible entity (e.g. the shooter) and falls back to the direct entity.
	 */
	public static LivingEntity attackerOf(DamageSource source) {
		Entity entity = source.getEntity();
		if (entity instanceof LivingEntity living) {
			return living;
		}
		Entity direct = source.getDirectEntity();
		if (direct instanceof LivingEntity living) {
			return living;
		}
		return null;
	}

	/**
	 * Whether the player has an active (in-budget, on-affinity) Focus whose item
	 * is registered under {@code targetId}.
	 */
	private static boolean hasActiveFocus(Player player, ResourceLocation targetId) {
		AttunedInv inventory = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			ItemStack stack = inventory.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			Item item = stack.getItem();
			ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
			if (targetId.equals(itemId)) {
				return true;
			}
		}
		return false;
	}
}
