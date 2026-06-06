package dev.attuned.client;

import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.AffinityColors;
import dev.attuned.api.focus.FocusDefinition;
import dev.attuned.attunement.AttunedAttachments;
import dev.attuned.attunement.AttunedInv;
import dev.attuned.attunement.Attunement;
import dev.attuned.attunement.BudgetResolver;
import dev.attuned.combat.Apex;
import dev.attuned.combat.Resonance;
import dev.attuned.pacts.Pact;
import dev.attuned.pacts.Pacts;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

/**
 * Turns a player's attunement state into player-facing display: a build "Title",
 * the colour used for the affinity gem and budget bar, and the tooltip shown when
 * the Focus panel is hovered.
 *
 * <p>The Title is two words: a count word for how many Foci are active and a
 * rank word for the attunement points they spend, or simply "Unattuned" when
 * nothing is active, so every reachable build reads as a distinct title.</p>
 */
public final class AttunementReadout {
	private AttunementReadout() {}

	public record Snapshot(int capacity, int used, int active,
			Map<Integer, BudgetResolver.DormantReason> dormantReasons,
			Optional<Affinity> committed, boolean discord, Optional<Apex.Capstone> capstone,
			float resonance, Optional<Pact> pact, Optional<Component> pactPreview) {
		public int remaining() {
			return Math.max(0, capacity - used);
		}

		public int dormant() {
			return dormantReasons.size();
		}

		public boolean atApex() {
			return capstone.isPresent() && resonance >= Resonance.APEX_THRESHOLD;
		}

		public int stanceArgb() {
			return AttunementReadout.apexAwareStanceArgb(this);
		}
	}

	public static Snapshot snapshot(Player player) {
		AttunedInv inv = AttunedAttachments.getInventory(player);
		BudgetResolver.Resolution resolution = Attunement.resolution(player);
		List<Integer> activeSlots = resolution.activeSlots();
		int capacity = Attunement.capacity(player);
		Map<Integer, BudgetResolver.DormantReason> dormantReasons = resolution.dormantReasons();
		List<Optional<Affinity>> orderedActiveAffinities = new ArrayList<>(activeSlots.size());
		Set<Affinity> distinctActiveAffinities = EnumSet.noneOf(Affinity.class);
		EnumMap<Affinity, Integer> activeAffinityCounts = new EnumMap<>(Affinity.class);
		int used = 0;
		for (int slot : activeSlots) {
			Optional<FocusDefinition> definition = Attunement.definitionFor(player, inv.get(slot));
			if (definition.isEmpty()) {
				orderedActiveAffinities.add(Optional.empty());
				continue;
			}
			FocusDefinition focus = definition.get();
			used += focus.cost();
			Optional<Affinity> affinity = focus.affinity();
			orderedActiveAffinities.add(affinity);
			affinity.ifPresent(activeAffinity -> {
				distinctActiveAffinities.add(activeAffinity);
				activeAffinityCounts.merge(activeAffinity, 1, Integer::sum);
			});
		}
		boolean discord = distinctActiveAffinities.size() >= 2;
		Optional<Affinity> committed = distinctActiveAffinities.size() == 1
			? Optional.of(distinctActiveAffinities.iterator().next())
			: Optional.empty();
		Optional<Apex.Capstone> capstone =
			Apex.resolveCapstone(orderedActiveAffinities, used, capacity);
		Optional<Pact> pact = Pacts.activeOf(activeAffinityCounts);
		int remaining = Math.max(0, capacity - used);
		return new Snapshot(capacity, used, activeSlots.size(), dormantReasons, committed, discord,
			capstone, Resonance.get(player), pact,
			Pacts.previewOf(player, pact, discord, activeAffinityCounts, remaining));
	}

	/** The player's build title, coloured by rank tier. */
	public static MutableComponent title(Player player) {
		return title(snapshot(player));
	}

	public static MutableComponent title(Snapshot snapshot) {
		int activeFoci = snapshot.active();
		if (activeFoci == 0) {
			return Component.literal("Unattuned").withStyle(ChatFormatting.DARK_GRAY);
		}
		int used = snapshot.used();
		return Component.literal(countWord(activeFoci) + " " + rankWord(used))
			.withStyle(rankColor(used));
	}

	/** Lines for the Focus-panel hover tooltip: title, budget, slot state, stance, and Apex. */
	public static List<Component> tooltip(Player player) {
		return tooltip(snapshot(player));
	}

	public static List<Component> tooltip(Snapshot snapshot) {
		int active = snapshot.active();
		int used = snapshot.used();
		int capacity = snapshot.capacity();
		int remaining = snapshot.remaining();
		int dormant = snapshot.dormant();

		List<Component> lines = new ArrayList<>();
		lines.add(title(snapshot).withStyle(ChatFormatting.BOLD));
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

		if (snapshot.discord()) {
			lines.add(Component.literal("Stance: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("Discord").withStyle(ChatFormatting.LIGHT_PURPLE)));
			lines.add(Component.literal("Clashing affinities — you deal and take extra damage.")
				.withStyle(ChatFormatting.GRAY));
		} else {
			Optional<Affinity> affinity = snapshot.committed();
			lines.add(Component.literal("Affinity: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(affinityName(affinity)).withStyle(affinityTextColor(affinity))));
		}

		Optional<Pact> pact = snapshot.pact();
		if (pact.isPresent()) {
			Pact activePact = pact.get();
			lines.add(Component.literal("Pact: ").withStyle(ChatFormatting.GRAY)
				.append(activePact.displayName().withStyle(activePact.chatColor(), ChatFormatting.BOLD)));
		} else {
			lines.add(Component.literal("Pact: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("None").withStyle(ChatFormatting.DARK_GRAY)));
			snapshot.pactPreview().ifPresent(preview -> lines.add(
				Component.literal("Next Pact: ").withStyle(ChatFormatting.GRAY).append(preview)));
		}

		Optional<Apex.Capstone> apex = snapshot.capstone();
		if (apex.isPresent()) {
			Apex.Capstone capstone = apex.get();
			boolean activeApex = snapshot.atApex();
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
		return stanceArgb(snapshot(player));
	}

	public static int stanceArgb(Snapshot snapshot) {
		return stanceArgb(snapshot.discord(), snapshot.committed());
	}

	/**
	 * ARGB colour for the affinity gem and budget-bar fill, computed from
	 * pre-resolved stance inputs.
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
		return apexAwareStanceArgb(snapshot(player));
	}

	public static int apexAwareStanceArgb(Snapshot snapshot) {
		return stanceArgb(snapshot.capstone(), snapshot.discord(), snapshot.committed());
	}

	public static Component stanceLabel(Snapshot snapshot) {
		if (snapshot.capstone().isPresent()) {
			Apex.Capstone capstone = snapshot.capstone().get();
			return Component.literal(capstone.displayName()).withStyle(capstone.chatColor());
		}
		if (snapshot.discord()) {
			return Component.literal("Discord").withStyle(ChatFormatting.LIGHT_PURPLE);
		}
		Optional<Affinity> affinity = snapshot.committed();
		if (affinity.isEmpty()) {
			return Component.literal("None").withStyle(ChatFormatting.GRAY);
		}
		return switch (affinity.get()) {
			case FURY -> Component.literal("Fury").withStyle(ChatFormatting.RED);
			case BASTION -> Component.literal("Bastion").withStyle(ChatFormatting.GOLD);
			case ZEPHYR -> Component.literal("Zephyr").withStyle(ChatFormatting.AQUA);
			case HOLY -> Component.literal("Holy").withStyle(ChatFormatting.YELLOW);
		};
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
		return affinityName(affinity.get());
	}

	private static String affinityName(Affinity affinity) {
		String lower = affinity.name().toLowerCase(Locale.ROOT);
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
