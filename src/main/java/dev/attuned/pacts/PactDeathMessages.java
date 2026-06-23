package dev.attuned.pacts;

import dev.attuned.AttunedConfig;
import dev.attuned.combat.AttunedCombat;
import dev.attuned.combat.CombatTargets;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Per-pact PvP death announcements.
 *
 * <p>When a player kills another player while in a pact, the pact gets top
 * billing in chat: the message names the pact rather than the attacker, with a
 * per-pact verb that fits the pact's flavour (Pyresworn burns, Stoneheart
 * crushes, Windrunner outpaces, Untethered unmakes). The attacker's name still
 * lives in the message as a hover tooltip on the pact name, so spectators can
 * see who was actually swinging without it cluttering the line.</p>
 *
 * <p>This sits on top of the vanilla death message rather than replacing it.
 * Two lines is intentional for now: vanilla says who killed whom, the pact line
 * says what they were when they did it.</p>
 */
public final class PactDeathMessages {
	private PactDeathMessages() {}

	private static boolean initialized;

	/**
	 * Registers the AFTER_DEATH handler. Called from the mod initializer, after
	 * {@code Pacts.init()} so that the pact-detection cache is in place.
	 */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		ServerLivingEntityEvents.AFTER_DEATH.register(PactDeathMessages::afterDeath);
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!AttunedConfig.get().broadcastPactDeaths()) return;
		if (!(entity instanceof ServerPlayer victim)) {
			return;
		}
		LivingEntity attacker = AttunedCombat.attackerOf(source);
		if (!(attacker instanceof Player attackerPlayer) || attacker == victim) {
			return;
		}
		if (!CombatTargets.canCreditPvpKill(attackerPlayer, victim)) {
			return;
		}
		Optional<Pact> pact = Pacts.activeOf(attackerPlayer);
		if (pact.isEmpty()) {
			return;
		}
		broadcast(victim, attackerPlayer, pact.get());
	}

	private static void broadcast(ServerPlayer victim, Player attacker, Pact pact) {
		Component pactName = pact.displayName()
			.withStyle(s -> s
				.applyFormats(pact.chatColor(), ChatFormatting.BOLD)
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, attacker.getDisplayName())));
		Component message = Component.translatable(
			"pact.attuned.death." + pact.name().toLowerCase(Locale.ROOT),
			victim.getDisplayName(),
			pactName
		);
		victim.level().getServer().getPlayerList().broadcastSystemMessage(message, false);
	}
}
