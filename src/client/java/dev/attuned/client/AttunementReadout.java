package dev.attuned.client;

import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.AffinityColors;
import dev.attuned.attunement.Attunement;
import dev.attuned.combat.Apex;
import dev.attuned.combat.Resonance;
import dev.attuned.pacts.Pact;
import dev.attuned.pacts.Pacts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

	/** Lines for the Focus-panel hover tooltip: title, budget, slot state, stance, and Apex. */
	public static List<Component> tooltip(Player player) {
		int active = Attunement.activeSlots(player).size();
		int used = Attunement.used(player);
		int capacity = Attunement.capacity(player);
		int remaining = Math.max(0, capacity - used);
		int dormant = Attunement.dormantReasons(player).size();

		List<Component> lines = new ArrayList<>();
		lines.add(title(player).withStyle(ChatFormatting.BOLD));
		lines.add(Component.empty());
		lines.add(Component.literal("Budget: ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(used + " / " + capacity).withStyle(ChatFormatting.AQUA)));
		lines.add(Component.literal("Remaining: ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(Integer.toString(remaining))
				.withStyle(remaining > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)));
		lines.add(Component.literal("Active: ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal(Integer.toString(active))
				.withStyle(active > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
			.append(Component.literal("  Dormant: ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(Integer.toString(dormant))
				.withStyle(dormant > 0 ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY)));
		if (dormant > 0) {
			lines.add(Component.literal("Hover a dormant Focus for details.")
				.withStyle(ChatFormatting.DARK_GRAY));
		}

		if (Attunement.isDiscord(player)) {
			lines.add(Component.literal("Stance: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("Discord").withStyle(ChatFormatting.LIGHT_PURPLE)));
			lines.add(Component.literal("Clashing affinities — you deal and take extra damage.")
				.withStyle(ChatFormatting.GRAY));
		} else {
			Optional<Affinity> affinity = Attunement.committedAffinity(player);
			lines.add(Component.literal("Affinity: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(affinityName(affinity)).withStyle(affinityTextColor(affinity))));
		}

		Optional<Pact> pact = Pacts.activeOf(player);
		if (pact.isPresent()) {
			Pact activePact = pact.get();
			lines.add(Component.literal("Pact: ").withStyle(ChatFormatting.GRAY)
				.append(activePact.displayName().withStyle(activePact.chatColor(), ChatFormatting.BOLD)));
		} else {
			lines.add(Component.literal("Pact: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("None").withStyle(ChatFormatting.DARK_GRAY)));
			Pacts.previewOf(player).ifPresent(preview -> lines.add(
				Component.literal("Next Pact: ").withStyle(ChatFormatting.GRAY).append(preview)));
		}

		Optional<Apex.Capstone> apex = Apex.capstoneOf(player);
		if (apex.isPresent()) {
			Apex.Capstone capstone = apex.get();
			boolean activeApex = Resonance.atApex(player);
			lines.add(Component.literal("Apex: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(capstone.displayName())
					.withStyle(capstone.chatColor(), ChatFormatting.BOLD))
				.append(Component.literal(activeApex ? " - Active" : " - Dormant")
					.withStyle(activeApex ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)));
			lines.add(Component.literal(capstone.description())
				.withStyle(ChatFormatting.GRAY));
			if (!activeApex) {
				lines.add(Component.literal("Build qualifies; raise Resonance to wake it.")
					.withStyle(ChatFormatting.DARK_GRAY));
			}
		}
		return lines;
	}

	/** ARGB colour for the affinity gem and budget-bar fill, the player's stance. */
	public static int stanceArgb(Player player) {
		return AffinityColors.argbOf(Attunement.committedAffinity(player), Attunement.isDiscord(player));
	}

	/**
	 * ARGB colour for the affinity gem and budget-bar fill, computed from
	 * pre-resolved stance inputs. Lets per-frame callers cache the discord and
	 * committed-affinity reads once and reuse them without re-walking the player's
	 * Focus slots through {@link Attunement#isDiscord(Player)} and
	 * {@link Attunement#committedAffinity(Player)}.
	 */
	public static int stanceArgb(boolean discord, Optional<Affinity> committed) {
		return AffinityColors.argbOf(committed, discord);
	}

	/**
	 * ARGB colour for capstone-aware UI accents. Apex capstones own the display
	 * colour while qualified, including Discord and neutral capstones that do not
	 * have a committed affinity.
	 */
	public static int stanceArgb(Optional<Apex.Capstone> capstone, boolean discord,
			Optional<Affinity> committed) {
		return capstone.map(Apex.Capstone::argb)
			.orElseGet(() -> stanceArgb(discord, committed));
	}

	/** Capstone-aware ARGB colour for callers that do not already cache stance state. */
	public static int apexAwareStanceArgb(Player player) {
		return stanceArgb(Apex.capstoneOf(player), Attunement.isDiscord(player),
			Attunement.committedAffinity(player));
	}

	/** Filled width for attunement budget bars, clamped to the painted track. */
	public static int budgetFillWidth(int trackWidth, int used, int capacity) {
		if (trackWidth <= 0 || used <= 0 || capacity <= 0) {
			return 0;
		}
		return Math.min(trackWidth, Math.max(1, Math.round(trackWidth * Math.min(1.0F, used / (float) capacity))));
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
			return "Neutral";
		}
		String lower = affinity.get().name().toLowerCase(Locale.ROOT);
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
			case HOLY -> ChatFormatting.YELLOW;
		};
	}
}
