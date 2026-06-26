package dev.attuned.onboarding;

import dev.attuned.compat.PlayerMessages;
import dev.attuned.AttunedServerCleanup;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.api.focus.Affinity;
import dev.attuned.combat.Apex;
import dev.attuned.combat.Resonance;
import dev.attuned.content.AttunedContent;
import dev.attuned.pacts.Pacts;
import dev.attuned.synergy.Synergies;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * First-time onboarding hints — short, one-shot messages that nudge a player
 * toward the next discovery the first time a triggering condition is met.
 *
 * <p>Each hint is keyed by a stable id and gated through the persistent
 * {@code ONBOARDING} attachment, so a hint fires at most once per player across
 * the lifetime of their save. The shard-presence and altar-sight hints are
 * polled cheaply (every 5 ticks, 4Hz) from the server tick; the bound-altar hint is
 * fired directly from the altar's interaction handler.
 */
public final class Onboarding {
	private Onboarding() {}

	private static final String HINT_FIRST_SHARD = "first_shard";
	private static final String HINT_FIRST_FRAGMENT = "first_fragment";
	private static final String HINT_FIRST_ALTAR = "first_altar";
	private static final String HINT_ALTAR_SIGHT = "altar_sight";
	private static final String HINT_FIRST_DORMANT = "first_dormant";
	private static final String HINT_FIRST_RESONANCE_ARMED = "first_resonance_armed";
	private static final String HINT_FIRST_ABILITY = "first_ability";
	private static final String HINT_FIRST_CONFLUENCE = "first_confluence";
	private static final String HINT_PACT_TRIAL_COMPLETE = "pact_trial_complete";

	/**
	 * How often the shard-presence and altar-sight polls run, in server ticks.
	 *
	 * <p>5 ticks = 4Hz. Chosen so the altar-sight hint reliably catches a quick
	 * glance at an altar (a player can easily look away in under a second), while
	 * the polling cost stays trivial — each tick we do at most one inventory
	 * scan and one short raycast per online player.
	 */
	private static final int POLL_INTERVAL_TICKS = 5;

	/** Reach distance for the altar-sight raycast, in blocks. */
	private static final double ALTAR_SIGHT_REACH = 5.0D;

	private static int tickCounter = 0;
	private static boolean initialized;

	/** Registers the server tick listener that polls for the shard and altar-sight hints. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++tickCounter < POLL_INTERVAL_TICKS) {
				return;
			}
			tickCounter = 0;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!AttunedAttachments.sawOnboarding(player, HINT_FIRST_FRAGMENT) && carriesFragment(player)) {
					tryFragmentHint(player);
				}
				if (!AttunedAttachments.sawOnboarding(player, HINT_FIRST_SHARD) && carriesShard(player)) {
					tryShardHint(player);
				}
				if (!AttunedAttachments.sawOnboarding(player, HINT_ALTAR_SIGHT)
					&& !holdsShard(player)
					&& looksAtAltar(player)) {
					tryAltarSightHint(player);
				}
				if (!AttunedAttachments.sawOnboarding(player, HINT_FIRST_RESONANCE_ARMED)
					&& Apex.capstoneOf(player).isPresent()
					&& Resonance.atApex(player)) {
					tryResonanceArmedHint(player);
				}
				tryPactPreviewHint(player);
				tryConfluencePreviewHint(player);
			}
		});
		AttunedServerCleanup.onStop(() -> tickCounter = 0);
	}

	/**
	 * Fires the first-shard hint once. A no-op if the player has already seen it.
	 *
	 * @param player the player to nudge
	 */
	public static void tryShardHint(ServerPlayer player) {
		fireHint(player, HINT_FIRST_SHARD,
			Component.translatable("onboarding.attuned.first_shard.found").withStyle(ChatFormatting.AQUA)
				.append(Component.translatable("onboarding.attuned.first_shard.detail")
					.withStyle(ChatFormatting.GRAY)));
	}

	/**
	 * Fires the first-fragment hint once. A no-op if the player has already seen it.
	 *
	 * @param player the player to nudge
	 */
	public static void tryFragmentHint(ServerPlayer player) {
		fireHint(player, HINT_FIRST_FRAGMENT,
			Component.translatable("onboarding.attuned.first_fragment.found").withStyle(ChatFormatting.AQUA)
				.append(Component.translatable("onboarding.attuned.first_fragment.detail")
					.withStyle(ChatFormatting.GRAY)));
	}

	/**
	 * Fires the first-altar hint once. A no-op if the player has already seen it.
	 *
	 * @param player the player to nudge
	 */
	public static void tryAltarHint(ServerPlayer player) {
		fireHint(player, HINT_FIRST_ALTAR,
			Component.translatable("onboarding.attuned.first_altar.bind_here").withStyle(ChatFormatting.AQUA)
				.append(Component.translatable("onboarding.attuned.first_altar.detail")
					.withStyle(ChatFormatting.GRAY)));
	}

	/**
	 * Fires the altar-sight hint once. A no-op if the player has already seen it.
	 *
	 * @param player the player to nudge
	 */
	public static void tryAltarSightHint(ServerPlayer player) {
		fireHint(player, HINT_ALTAR_SIGHT,
			Component.translatable("onboarding.attuned.altar_sight").withStyle(ChatFormatting.AQUA));
	}

	/**
	 * Fires the first-dormant-Focus hint once. A no-op if the player has already
	 * seen it.
	 *
	 * @param player the player to nudge
	 */
	public static void tryDormantHint(ServerPlayer player) {
		fireHint(player, HINT_FIRST_DORMANT,
			Component.translatable("onboarding.attuned.first_dormant.found").withStyle(ChatFormatting.AQUA)
				.append(Component.translatable("onboarding.attuned.first_dormant.detail")
					.withStyle(ChatFormatting.GRAY)));
	}

	/**
	 * Fires the first-armed-Apex hint once. A no-op if the player has already seen it.
	 *
	 * @param player the player to nudge
	 */
	public static void tryResonanceArmedHint(ServerPlayer player) {
		fireHint(player, HINT_FIRST_RESONANCE_ARMED,
			Component.translatable("onboarding.attuned.first_resonance_armed").withStyle(ChatFormatting.AQUA)
				.append(Component.translatable("onboarding.attuned.first_resonance_armed.detail")
					.withStyle(ChatFormatting.GRAY)));
	}

	/**
	 * Fires the first-Focus-Ability hint once. A no-op if the player has already seen it.
	 *
	 * @param player the player to nudge
	 */
	public static void tryAbilityHint(ServerPlayer player) {
		fireHint(player, HINT_FIRST_ABILITY,
			Component.translatable("onboarding.attuned.first_ability").withStyle(ChatFormatting.AQUA)
				.append(Component.translatable("onboarding.attuned.first_ability.detail")
					.withStyle(ChatFormatting.GRAY)));
	}

	/**
	 * Fires the first-Confluence hint once. A no-op if the player has already seen it.
	 *
	 * @param player the player to nudge
	 */
	public static void tryConfluenceHint(ServerPlayer player) {
		fireHint(player, HINT_FIRST_CONFLUENCE,
			Component.translatable("onboarding.attuned.first_confluence").withStyle(ChatFormatting.AQUA)
				.append(Component.translatable("onboarding.attuned.first_confluence.detail")
					.withStyle(ChatFormatting.GRAY)));
	}

	/**
	 * Fires the first pact-trial-completion hint once. A no-op if the player has
	 * already seen it.
	 *
	 * @param player the player to nudge
	 */
	public static void tryPactTrialCompleteHint(ServerPlayer player) {
		fireHint(player, HINT_PACT_TRIAL_COMPLETE,
			Component.translatable("onboarding.attuned.pact_trial_complete").withStyle(ChatFormatting.AQUA)
				.append(Component.translatable("onboarding.attuned.pact_trial_complete.detail")
					.withStyle(ChatFormatting.GRAY)));
	}

	/**
	 * One-shot action-bar nudge when the build is exactly one Focus away from a
	 * pact and budget allows equipping it. Fires once per affinity lane.
	 */
	public static void tryPactPreviewHint(ServerPlayer player) {
		Optional<Affinity> affinity = Pacts.oneAwayAffinity(player);
		if (affinity.isEmpty()) {
			return;
		}
		String key = "pact_one_away_" + affinity.get().name().toLowerCase(Locale.ROOT);
		if (AttunedAttachments.sawOnboarding(player, key)) {
			return;
		}
		AttunedAttachments.markOnboarding(player, key);
		Pacts.previewOf(player).ifPresent(preview -> {
			((ServerLevel) player.level()).playSound(null, player.blockPosition(),
				SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.35F, 1.25F);
			PlayerMessages.overlay(player, preview);
		});
	}

	/**
	 * One-shot action-bar nudge when the build is exactly one Focus away from a
	 * discovered Confluence. Fires once per confluence id.
	 */
	public static void tryConfluencePreviewHint(ServerPlayer player) {
		Optional<String> confluenceId = Synergies.oneAwayConfluence(player);
		if (confluenceId.isEmpty()) {
			return;
		}
		String key = "confluence_one_away_" + confluencePath(confluenceId.get());
		if (AttunedAttachments.sawOnboarding(player, key)) {
			return;
		}
		AttunedAttachments.markOnboarding(player, key);
		Synergies.previewOf(player).ifPresent(preview -> {
			((ServerLevel) player.level()).playSound(null, player.blockPosition(),
				SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.35F, 1.25F);
			PlayerMessages.overlay(player, preview);
		});
	}

	/** Whether the player is carrying at least one Attunement Shard in their main inventory. */
	private static boolean carriesShard(ServerPlayer player) {
		return carries(player, AttunedContent.ATTUNEMENT_SHARD.get());
	}

	/** Whether the player is carrying at least one Attunement Shard Fragment. */
	private static boolean carriesFragment(ServerPlayer player) {
		return carries(player, AttunedContent.ATTUNEMENT_SHARD_FRAGMENT.get());
	}

	private static boolean carries(ServerPlayer player, Item item) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(item)) {
				return true;
			}
		}
		return false;
	}

	/** Whether the player is holding an Attunement Shard in either hand. */
	private static boolean holdsShard(ServerPlayer player) {
		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		return AttunedContent.is(main, AttunedContent.ATTUNEMENT_SHARD)
			|| AttunedContent.is(off, AttunedContent.ATTUNEMENT_SHARD);
	}

	/**
	 * Whether the player's view crosshair is on an Attunement Altar within
	 * {@link #ALTAR_SIGHT_REACH} blocks. Uses the standard entity pick raycast.
	 *
	 * @param player the player whose sight to test
	 * @return {@code true} if the picked block is an Attunement Altar
	 */
	private static boolean looksAtAltar(ServerPlayer player) {
		HitResult hit = player.pick(ALTAR_SIGHT_REACH, 1.0F, false);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockPos pos = ((BlockHitResult) hit).getBlockPos();
		return player.level().getBlockState(pos).is(AttunedContent.ATTUNEMENT_ALTAR.get());
	}

	private static String confluencePath(String confluenceId) {
		int colon = confluenceId.indexOf(':');
		return colon >= 0 ? confluenceId.substring(colon + 1) : confluenceId;
	}

	/**
	 * Shared gate-and-fire for every onboarding hint. Returns immediately if
	 * the player has already seen the hint with this id; otherwise marks the
	 * attachment, sends the styled chat message, and plays a soft amethyst
	 * chime cue for consistent feedback across every onboarding moment —
	 * distinct from the louder pact-awakening toast.
	 */
	private static void fireHint(ServerPlayer player, String id, Component message) {
		if (AttunedAttachments.sawOnboarding(player, id)) {
			return;
		}
		AttunedAttachments.markOnboarding(player, id);
		PlayerMessages.system(player, message);
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.4F);
	}
}
