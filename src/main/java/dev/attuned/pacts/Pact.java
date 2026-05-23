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
 * <p>The single-affinity pacts (Pyresworn, Stoneheart, Windrunner) wake up when
 * three or more active Foci share one affinity and none oppose it; the Manifold
 * pact (Untethered) wakes up when the player carries at least one active Focus
 * of every affinity at once. A player is in at most one pact at a time.</p>
 *
 * <p>Each pact reads as a short identity: a Pyresworn is a Pyresworn, not "a
 * Fury build with three Foci." The colour and name surface in the panel readout,
 * the chat announcement and the on-the-feet particle aura.</p>
 */
public enum Pact {
	PYRESWORN("pyresworn", Optional.of(Affinity.FURY), ChatFormatting.RED),
	STONEHEART("stoneheart", Optional.of(Affinity.BASTION), ChatFormatting.GOLD),
	WINDRUNNER("windrunner", Optional.of(Affinity.ZEPHYR), ChatFormatting.AQUA),
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
	 * pact (Untethered) which spans all three.
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

	/** The pact that belongs to a single committed affinity. */
	public static Pact ofAffinity(Affinity affinity) {
		return switch (affinity) {
			case FURY -> PYRESWORN;
			case BASTION -> STONEHEART;
			case ZEPHYR -> WINDRUNNER;
		};
	}
}
