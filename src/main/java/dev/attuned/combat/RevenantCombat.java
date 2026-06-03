package dev.attuned.combat;

import dev.attuned.Attuned;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Combat hooks for The Revenant faction: debts, rites, and bone-cold reprisals. */
public final class RevenantCombat {
	private static final Identifier ASHEN_DEBT_FOCUS =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "ashen_debt_focus");
	private static final Identifier LAST_RITES_FOCUS =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "last_rites_focus");
	private static final Identifier BONECHILL_FOCUS =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "bonechill_focus");

	private static final int DEBT_WINDOW_TICKS = 160;
	private static final float DEBT_MULTIPLIER = 1.25F;
	private static final int BONECHILL_TICKS = 70;
	private static final int LAST_RITES_CLEANSE_COOLDOWN = 160;

	private static final Map<UUID, Debt> DEBTS = new HashMap<>();
	private static final Map<UUID, Long> LAST_RITES = new HashMap<>();

	private RevenantCombat() {}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(RevenantCombat::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(RevenantCombat::afterDeath);
		ServerTickEvents.END_SERVER_TICK.register(RevenantCombat::tick);
		AttunedPlayerCleanup.onForget(uuid -> {
			DEBTS.remove(uuid);
			LAST_RITES.remove(uuid);
		});
	}

	public static float adjustDamage(LivingEntity defender, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer attacker)
				|| source.getDirectEntity() != attacker
				|| !isDirectMelee(source)
				|| defender == attacker
				|| !hasActiveFocus(attacker, ASHEN_DEBT_FOCUS)) {
			return amount;
		}
		Debt debt = DEBTS.get(attacker.getUUID());
		if (debt == null || debt.target() != defender.getUUID()
				|| attacker.level().getGameTime() > debt.expiresAt()) {
			return amount;
		}
		DEBTS.remove(attacker.getUUID());
		ashenDebtFeedback(attacker, defender);
		return amount * DEBT_MULTIPLIER;
	}

	private static void afterDamage(LivingEntity defender, DamageSource source,
			float originalDamage, float dealtDamage, boolean blocked) {
		if (dealtDamage <= 0.0F) {
			return;
		}
		LivingEntity attacker = AttunedCombat.attackerOf(source);
		if (attacker == null || attacker == defender) {
			return;
		}
		if (defender instanceof ServerPlayer player && hasActiveFocus(player, ASHEN_DEBT_FOCUS)) {
			DEBTS.put(player.getUUID(), new Debt(attacker.getUUID(), player.level().getGameTime() + DEBT_WINDOW_TICKS));
		}
		if (defender instanceof ServerPlayer player && hasActiveFocus(player, BONECHILL_FOCUS)
				&& canChillAttacker(player, attacker)) {
			chill(attacker);
		}
		if (attacker instanceof ServerPlayer player && hasActiveFocus(player, BONECHILL_FOCUS)
				&& defender.typeHolder().is(EntityTypeTags.UNDEAD)) {
			chill(defender);
		}
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		DEBTS.values().removeIf(debt -> debt.target().equals(entity.getUUID()));
		if (!(source.getEntity() instanceof ServerPlayer player)
				|| !hasActiveFocus(player, LAST_RITES_FOCUS)
				|| !CombatTargets.isHostileOrPvpOpponent(entity, player)) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = LAST_RITES.get(player.getUUID());
		if (last != null && now - last < LAST_RITES_CLEANSE_COOLDOWN) {
			return;
		}
		boolean wasOnFire = player.isOnFire();
		boolean cleansed = player.removeEffect(MobEffects.POISON)
			| player.removeEffect(MobEffects.WITHER)
			| player.removeEffect(MobEffects.SLOWNESS)
			| wasOnFire;
		if (!cleansed) {
			return;
		}
		if (wasOnFire) {
			player.clearFire();
		}
		LAST_RITES.put(player.getUUID(), now);
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
			player.getX(), player.getY() + 1.0D, player.getZ(), 24, 0.35D, 0.5D, 0.35D, 0.03D);
		level.playSound(null, player.blockPosition(),
			SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.35F);
	}

	private static boolean canChillAttacker(ServerPlayer player, LivingEntity attacker) {
		return attacker.typeHolder().is(EntityTypeTags.UNDEAD)
			|| CombatTargets.isHostileOrPvpOpponent(attacker, player);
	}

	private static void chill(LivingEntity target) {
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BONECHILL_TICKS, 0, true, false, true));
		if (target.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SNOWFLAKE,
				target.getX(), target.getY() + target.getBbHeight() * 0.55D, target.getZ(),
				9, 0.22D, 0.3D, 0.22D, 0.01D);
		}
	}

	private static void ashenDebtFeedback(ServerPlayer attacker, LivingEntity defender) {
		ServerLevel level = (ServerLevel) attacker.level();
		level.sendParticles(ParticleTypes.ASH,
			defender.getX(), defender.getY() + defender.getBbHeight() * 0.65D, defender.getZ(),
			18, 0.25D, 0.3D, 0.25D, 0.02D);
		level.playSound(null, defender.blockPosition(),
			SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7F, 0.75F);
	}

	private static boolean isDirectMelee(DamageSource source) {
		return !source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION);
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % 40L != 0L) {
			return;
		}
		Iterator<Map.Entry<UUID, Debt>> entries = DEBTS.entrySet().iterator();
		while (entries.hasNext()) {
			if (entries.next().getValue().expiresAt() <= now) {
				entries.remove();
			}
		}
	}

	private static boolean hasActiveFocus(Player player, Identifier targetId) {
		AttunedInv inventory = AttunedAttachments.getInventory(player);
		for (int slot : Attunement.activeSlots(player)) {
			ItemStack stack = inventory.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			Item item = stack.getItem();
			Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
			if (targetId.equals(itemId)) {
				return true;
			}
		}
		return false;
	}

	private record Debt(UUID target, long expiresAt) {
	}
}
