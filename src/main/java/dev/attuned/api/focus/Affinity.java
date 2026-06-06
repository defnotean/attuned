package dev.attuned.api.focus;

import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.util.StringRepresentable;

/**
 * The Focus affinities, arranged in a four-part counter cycle: Holy beats Fury,
 * Fury beats Bastion, Bastion beats Zephyr, and Zephyr beats Holy. A Focus with
 * no affinity (an affinity-neutral utility Focus) is represented by an empty
 * {@code Optional<Affinity>} on its {@link FocusDefinition}.
 */
public enum Affinity implements StringRepresentable {
	FURY("fury"),
	BASTION("bastion"),
	ZEPHYR("zephyr"),
	HOLY("holy");

	public static final Codec<Affinity> CODEC = StringRepresentable.fromEnum(Affinity::values);

	private final String serializedName;

	Affinity(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	/** True if this affinity counters {@code other} in the cycle. */
	public boolean beats(Affinity other) {
		Objects.requireNonNull(other, "other");
		return switch (this) {
			case FURY -> other == BASTION;
			case BASTION -> other == ZEPHYR;
			case ZEPHYR -> other == HOLY;
			case HOLY -> other == FURY;
		};
	}

	/**
	 * The canonical ARGB display colour for this affinity. Routed through
	 * {@link AffinityColors} so every readout (panel gem, HUD gem, pact aura,
	 * combat feedback) shares one source of truth and stays in lockstep when
	 * the palette is retuned.
	 */
	public int argb() {
		return switch (this) {
			case FURY -> 0xFFFF5555;
			case BASTION -> 0xFFFFAA00;
			case ZEPHYR -> 0xFF55FFFF;
			case HOLY -> 0xFFFFF1A8;
		};
	}

	/**
	 * The canonical ARGB display colour for an optional affinity, returning the
	 * neutral grey from {@link AffinityColors#NEUTRAL_ARGB} for an empty value.
	 * The Discord stance is not handled here. Callers that need stance colour
	 * (Discord-or-affinity) should use
	 * {@link AffinityColors#argbOf(Optional, boolean)} instead.
	 */
	public static int argbOrNeutral(Optional<Affinity> affinity) {
		return affinity.map(Affinity::argb).orElse(AffinityColors.NEUTRAL_ARGB);
	}
}
