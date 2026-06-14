package dev.attuned.api.focus;

import com.mojang.serialization.Codec;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.util.StringRepresentable;

/**
 * The Focus affinities, the load-bearing stance/Pact/Discord/Apex identity. The
 * roster was promoted from the original four-cycle into the full eight-value
 * Wheel of Refusals: Fury, Bastion, Zephyr, Holy, Tide, Forge, Verdant, Umbral.
 *
 * <p>Each affinity is strong against exactly two others and weak to two
 * reciprocal others ({@link #strongAgainst()} / {@link #weakAgainst()}), copied
 * from {@link Aspect} so the two identity layers stay in lockstep. The historic
 * four-cycle (Holy beats Fury, Fury beats Bastion, Bastion beats Zephyr, Zephyr
 * beats Holy) survives as a subset of the expanded matrix. A Focus with no
 * affinity (an affinity-neutral utility Focus) is represented by an empty
 * {@code Optional<Affinity>} on its {@link FocusDefinition}.
 */
public enum Affinity implements StringRepresentable {
	FURY("fury"),
	BASTION("bastion"),
	ZEPHYR("zephyr"),
	HOLY("holy"),
	TIDE("tide"),
	FORGE("forge"),
	VERDANT("verdant"),
	UMBRAL("umbral");

	public static final Codec<Affinity> CODEC = StringRepresentable.fromEnum(Affinity::values);

	private final String serializedName;

	Affinity(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	/** True if this affinity counters {@code other} in the Wheel of Refusals. */
	public boolean beats(Affinity other) {
		Objects.requireNonNull(other, "other");
		return strongAgainst().contains(other);
	}

	/** The two affinities this one is strong against. */
	public EnumSet<Affinity> strongAgainst() {
		return switch (this) {
			case FURY -> EnumSet.of(BASTION, VERDANT);
			case BASTION -> EnumSet.of(ZEPHYR, UMBRAL);
			case ZEPHYR -> EnumSet.of(HOLY, TIDE);
			case HOLY -> EnumSet.of(FURY, UMBRAL);
			case TIDE -> EnumSet.of(FURY, FORGE);
			case FORGE -> EnumSet.of(BASTION, VERDANT);
			case VERDANT -> EnumSet.of(TIDE, HOLY);
			case UMBRAL -> EnumSet.of(ZEPHYR, FORGE);
		};
	}

	/** The two affinities that counter this one. Derived to keep the matrix reciprocal. */
	public EnumSet<Affinity> weakAgainst() {
		EnumSet<Affinity> weak = EnumSet.noneOf(Affinity.class);
		for (Affinity candidate : values()) {
			if (candidate.strongAgainst().contains(this)) {
				weak.add(candidate);
			}
		}
		return weak;
	}

	/** Convenience view for UI/tests that should not mutate the returned set. */
	public Set<Affinity> counters() {
		return Set.copyOf(strongAgainst());
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
			case TIDE -> 0xFF2F7FD0;
			case FORGE -> 0xFFC85A2B;
			case VERDANT -> 0xFF5FC23E;
			case UMBRAL -> 0xFF7A4FB5;
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
