package dev.attuned.client;

import dev.attuned.api.focus.Affinity;
import dev.attuned.attunement.Attunement;
import dev.attuned.combat.Apex;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns a player's attunement state into player-facing display: a build "Title",
 * the colour used for the affinity gem and budget bar, and the tooltip shown when
 * the Focus panel is hovered.
 *
 * <p>The Title is two words — a count word for how many Foci are active and a
 * rank word for the attunement points they spend — or simply "Unattuned" when
 * nothing is active, so every reachable build reads as a distinct title.</p>
 */
public final class AttunementReadout {
	private AttunementReadout() {}

	// ARGB colours for the affinity gem and budget-bar fill.
	private static final int FURY_ARGB = 0xFFFF5555;
	private static final int BASTION_ARGB = 0xFFFFAA00;
	private static final int ZEPHYR_ARGB = 0xFF55FFFF;
	/** Used when no affinity is committed — a neutral, unlit grey. */
	private static final int NEUTRAL_ARGB = 0xFF8B8B8B;

	/** The player's build title, coloured by rank tier. */
	public static MutableComponent title(Player player) {
		int activeFoci = Attunement.activeSlots(player).size();
		if (activeFoci == 0) {
			return Component.literal("Unattuned").withStyle(ChatFormatting.DARK_GRAY);
		}
		int used = Attunement.used(player);
		return Component.literal(countWord(activeFoci) + " " + rankWord(used))
			.withStyle(rankColor(used));
	}

	/** Lines for the Focus-panel hover tooltip: title, budget, affinity, and Apex. */
	public static List<Component> tooltip(Player player) {
		int used = Attunement.used(player);
		int capacity = Attunement.capacity(player);
		Optional<Affinity> affinity = Attunement.committedAffinity(player);

		List<Component> lines = new ArrayList<>();
		lines.add(title(player).withStyle(ChatFormatting.BOLD));
		lines.add(Component.empty());
		lines.add(Component.literal("Attunement: ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(used + " / " + capacity).withStyle(ChatFormatting.AQUA)));
		lines.add(Component.literal("Affinity: ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(affinityName(affinity)).withStyle(affinityTextColor(affinity))));

		Optional<Affinity> apex = Apex.affinityOf(player);
		if (apex.isPresent()) {
			Affinity capstone = apex.get();
			lines.add(Component.literal("Apex: " + Apex.capstoneName(capstone))
				.withStyle(affinityTextColor(apex), ChatFormatting.BOLD));
			lines.add(Component.literal(Apex.capstoneDescription(capstone))
				.withStyle(ChatFormatting.GRAY));
		}
		return lines;
	}

	/** ARGB colour for the affinity gem and budget-bar fill. */
	public static int affinityArgb(Optional<Affinity> affinity) {
		if (affinity.isEmpty()) {
			return NEUTRAL_ARGB;
		}
		return switch (affinity.get()) {
			case FURY -> FURY_ARGB;
			case BASTION -> BASTION_ARGB;
			case ZEPHYR -> ZEPHYR_ARGB;
		};
	}

	// How many Foci are active: one, a few, many, or the full six.
	private static String countWord(int activeFoci) {
		if (activeFoci <= 1) {
			return "Lone";
		}
		if (activeFoci <= 3) {
			return "Bound";
		}
		if (activeFoci <= 5) {
			return "Woven";
		}
		return "Manifold";
	}

	// How strong the build is, by attunement points spent.
	private static String rankWord(int used) {
		if (used <= 5) {
			return "Initiate";
		}
		if (used <= 10) {
			return "Adept";
		}
		if (used <= 15) {
			return "Channeler";
		}
		return "Paragon";
	}

	private static ChatFormatting rankColor(int used) {
		if (used <= 5) {
			return ChatFormatting.WHITE;
		}
		if (used <= 10) {
			return ChatFormatting.GREEN;
		}
		if (used <= 15) {
			return ChatFormatting.AQUA;
		}
		return ChatFormatting.GOLD;
	}

	private static String affinityName(Optional<Affinity> affinity) {
		if (affinity.isEmpty()) {
			return "None";
		}
		String lower = affinity.get().name().toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static ChatFormatting affinityTextColor(Optional<Affinity> affinity) {
		if (affinity.isEmpty()) {
			return ChatFormatting.GRAY;
		}
		return switch (affinity.get()) {
			case FURY -> ChatFormatting.RED;
			case BASTION -> ChatFormatting.GOLD;
			case ZEPHYR -> ChatFormatting.AQUA;
		};
	}
}
