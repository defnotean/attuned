package dev.attuned.onboarding;

import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.content.AttunedContent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
	private static final String HINT_FIRST_ALTAR = "first_altar";
	private static final String HINT_ALTAR_SIGHT = "altar_sight";

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

	/** Registers the server tick listener that polls for the shard and altar-sight hints. */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++tickCounter < POLL_INTERVAL_TICKS) {
				return;
			}
			tickCounter = 0;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!AttunedAttachments.sawOnboarding(player, HINT_FIRST_SHARD) && carriesShard(player)) {
					tryShardHint(player);
				}
				if (!AttunedAttachments.sawOnboarding(player, HINT_ALTAR_SIGHT)
					&& !holdsShard(player)
					&& looksAtAltar(player)) {
					tryAltarSightHint(player);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> tickCounter = 0);
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

	/** Whether the player is carrying at least one Attunement Shard in their main inventory. */
	private static boolean carriesShard(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(AttunedContent.ATTUNEMENT_SHARD)) {
				return true;
			}
		}
		return false;
	}

	/** Whether the player is holding an Attunement Shard in either hand. */
	private static boolean holdsShard(ServerPlayer player) {
		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		return main.is(AttunedContent.ATTUNEMENT_SHARD) || off.is(AttunedContent.ATTUNEMENT_SHARD);
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
		return player.level().getBlockState(pos).is(AttunedContent.ATTUNEMENT_ALTAR);
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
		player.sendSystemMessage(message);
		((ServerLevel) player.level()).playSound(null, player.blockPosition(),
			SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.4F);
	}
}
