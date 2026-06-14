package dev.attuned.pacts;

import dev.attuned.api.focus.Affinity;
import dev.attuned.api.focus.AffinityColors;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A named set bonus that activates from the player's spread of active Foci.
 *
 * <p>The single-affinity pacts (Pyresworn, Stoneheart, Windrunner, Radiant
 * Covenant, Tidesworn, Forgebound, Wildroot, Nightsworn) wake up when three or
 * more active Foci share one affinity; the Manifold pact (Untethered) wakes up
 * when the player carries a diverse spread of active affinities (four or more
 * distinct, none stacked three deep). A player is in at most one pact at a time.</p>
 *
 * <p>Each pact reads as a short identity: a Pyresworn is a Pyresworn, not "a
 * Fury build with three Foci." The colour and name surface in the panel readout,
 * the chat announcement and the on-the-feet particle aura.</p>
 */
public enum Pact {
	PYRESWORN("pyresworn", Optional.of(Affinity.FURY), ChatFormatting.RED),
	STONEHEART("stoneheart", Optional.of(Affinity.BASTION), ChatFormatting.GOLD),
	WINDRUNNER("windrunner", Optional.of(Affinity.ZEPHYR), ChatFormatting.AQUA),
	RADIANT_COVENANT("radiant_covenant", Optional.of(Affinity.HOLY), ChatFormatting.YELLOW),
	TIDESWORN("tidesworn", Optional.of(Affinity.TIDE), ChatFormatting.BLUE),
	FORGEBOUND("forgebound", Optional.of(Affinity.FORGE), ChatFormatting.DARK_RED),
	WILDROOT("wildroot", Optional.of(Affinity.VERDANT), ChatFormatting.GREEN),
	NIGHTSWORN("nightsworn", Optional.of(Affinity.UMBRAL), ChatFormatting.DARK_PURPLE),
	UNTETHERED("untethered", Optional.empty(), ChatFormatting.LIGHT_PURPLE);

	private final String displayNameKey;
	private final String descriptionKey;
	private final Optional<Affinity> affinity;
	private final ChatFormatting chatColor;

	Pact(String id, Optional<Affinity> affinity, ChatFormatting chatColor) {
		this.displayNameKey = "pact.attuned." + id + ".name";
		this.descriptionKey = "pact.attuned." + id + ".description";
		this.affinity = affinity;
		this.chatColor = chatColor;
	}

	/** Translated display name, ready to style and append. */
	public MutableComponent displayName() {
		return Component.translatable(displayNameKey);
	}

	/** Translated rules-text description for the chat announcement. */
	public MutableComponent description() {
		return Component.translatable(descriptionKey);
	}

	/**
	 * The single affinity this pact is bound to, or empty for the Manifold
	 * pact (Untethered) which spans a diverse spread of affinities rather than
	 * one committed lane.
	 */
	public Optional<Affinity> affinity() { return affinity; }

	/** {@link ChatFormatting} colour used to style the pact's name in chat. */
	public ChatFormatting chatColor() { return chatColor; }

	/**
	 * The pact's ARGB display colour. Derived from the bound affinity so the
	 * palette stays in one place; Untethered has no affinity and renders in
	 * the Discord magenta to mark it as the mixed-affinity path.
	 */
	public int argb() {
		return affinity.map(Affinity::argb).orElse(AffinityColors.DISCORD_ARGB);
	}

	/** The pact that belongs to a single committed affinity. Total over all eight. */
	public static Pact ofAffinity(Affinity affinity) {
		return switch (affinity) {
			case FURY -> PYRESWORN;
			case BASTION -> STONEHEART;
			case ZEPHYR -> WINDRUNNER;
			case HOLY -> RADIANT_COVENANT;
			case TIDE -> TIDESWORN;
			case FORGE -> FORGEBOUND;
			case VERDANT -> WILDROOT;
			case UMBRAL -> NIGHTSWORN;
		};
	}
}
