package dev.attuned;

import dev.attuned.compat.PlayerMessages;
import dev.attuned.attunement.AttunedAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Milestone-based attunement progression: reaching a notable point in the game
 * permanently raises a player's attunement capacity, once for each milestone.
 *
 * <p>Each milestone is granted at most once per player — the claimed set is a
 * persistent data attachment — and announces itself with a message and a sound.
 * Capacity is still clamped to the configured cap; a milestone reached while
 * already at the cap is recorded but grants nothing.
 */
public final class Milestones {
	private Milestones() {}

	private static boolean initialized;

	/** A one-time capacity gain tied to a notable game event. */
	private enum Milestone {
		NETHER("nether", 3, "stepped into the Nether"),
		WITHER("wither", 3, "felled the Wither"),
		END("end", 3, "reached the End"),
		DRAGON("dragon", 4, "slew the Ender Dragon"),
		ELDER_GUARDIAN("elder_guardian", 3, "bested an Elder Guardian"),
		APEX_ARMED("apex_armed", 2, "awoke your Apex in combat"),
		FIRST_CONFLUENCE("first_confluence", 2, "discovered your first Confluence"),
		PACT_TRIAL("pact_trial", 3, "completed a Pact Trial");

		private final String id;
		private final int capacity;
		private final String feat;

		Milestone(String id, int capacity, String feat) {
			this.id = id;
			this.capacity = capacity;
			this.feat = feat;
		}
	}

	/** Registers the milestone detection hooks. Called from the mod initializer. */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			if (destination.dimension() == Level.NETHER) {
				grant(player, Milestone.NETHER);
			} else if (destination.dimension() == Level.END) {
				grant(player, Milestone.END);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			Milestone milestone = bossMilestone(entity);
			if (milestone != null && damageSource.getEntity() instanceof ServerPlayer player) {
				grant(player, milestone);
			}
		});
	}

	/** One-time capacity for the first time resonance crosses the Apex threshold. */
	public static void onApexArmed(ServerPlayer player) {
		grant(player, Milestone.APEX_ARMED);
	}

	/** One-time capacity for discovering any Confluence. */
	public static void onFirstConfluence(ServerPlayer player) {
		grant(player, Milestone.FIRST_CONFLUENCE);
	}

	/** One-time capacity for finishing any Pact Trial. */
	public static void onPactTrialComplete(ServerPlayer player) {
		grant(player, Milestone.PACT_TRIAL);
	}

	/** The boss-kill milestone for an entity type, or {@code null} if it is not a milestone boss. */
	private static Milestone bossMilestone(LivingEntity entity) {
		EntityType<?> type = entity.getType();
		if (type == EntityType.WITHER) {
			return Milestone.WITHER;
		}
		if (type == EntityType.ENDER_DRAGON) {
			return Milestone.DRAGON;
		}
		if (type == EntityType.ELDER_GUARDIAN) {
			return Milestone.ELDER_GUARDIAN;
		}
		return null;
	}

	private static void grant(ServerPlayer player, Milestone milestone) {
		if (AttunedAttachments.hasMilestone(player, milestone.id)) {
			return;
		}
		AttunedAttachments.addMilestone(player, milestone.id);

		int cap = AttunedConfig.get().capacityCap();
		int before = AttunedAttachments.getCapacity(player);
		if (before >= cap) {
			return; // Recorded, but already at full attunement — nothing to grant.
		}
		int after = Math.min(cap, before + milestone.capacity);
		AttunedAttachments.setCapacity(player, after);

		((ServerLevel) player.getLevel()).playSound(null, player.blockPosition(),
			SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.2F);
		PlayerMessages.system(player, new net.minecraft.network.chat.TextComponent("Your attunement deepens — you " + milestone.feat + ". ")
			.withStyle(ChatFormatting.GRAY)
			.append(new net.minecraft.network.chat.TextComponent("(+" + (after - before) + " capacity)")
				.withStyle(ChatFormatting.AQUA)));
	}
}
