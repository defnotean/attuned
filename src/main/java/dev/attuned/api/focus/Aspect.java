package dev.attuned.api.focus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.attuned.Attuned;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * The broader counter identity for a Focus. Affinity remains the old four-part
 * Pact/Discord stance system; Aspect is the expanded Wheel of Refusals that lets
 * new Focus families counter and be countered without expanding the load-bearing
 * affinity enum.
 */
public enum Aspect implements StringRepresentable {
	FURY("fury"),
	BASTION("bastion"),
	ZEPHYR("zephyr"),
	HOLY("holy"),
	TIDE("tide"),
	FORGE("forge"),
	VERDANT("verdant"),
	UMBRAL("umbral");

	public static final Codec<Aspect> CODEC = Codec.STRING.flatXmap(
		Aspect::bySerializedNameOrId,
		aspect -> DataResult.success(aspect.id().toString()));

	private final String serializedName;

	Aspect(String serializedName) {
		this.serializedName = serializedName;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}

	/** The namespaced id written in FocusDefinition JSON and player-facing docs. */
	public Identifier id() {
		return Identifier.fromNamespaceAndPath(Attuned.MOD_ID, serializedName);
	}

	/** True if this Aspect counters {@code other} in the Wheel of Refusals. */
	public boolean beats(Aspect other) {
		Objects.requireNonNull(other, "other");
		return strongAgainst().contains(other);
	}

	/** The two Aspects this one is strong against. */
	public EnumSet<Aspect> strongAgainst() {
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

	/** The two Aspects that counter this one. Derived to keep the matrix reciprocal. */
	public EnumSet<Aspect> weakAgainst() {
		EnumSet<Aspect> weak = EnumSet.noneOf(Aspect.class);
		for (Aspect candidate : values()) {
			if (candidate.strongAgainst().contains(this)) {
				weak.add(candidate);
			}
		}
		return weak;
	}

	/** Convenience view for UI/tests that should not mutate the returned set. */
	public Set<Aspect> counters() {
		return Set.copyOf(strongAgainst());
	}

	private static DataResult<Aspect> bySerializedNameOrId(String name) {
		String normalized = Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
		String path = normalized;
		int separator = normalized.indexOf(':');
		if (separator >= 0) {
			String namespace = normalized.substring(0, separator);
			if (!Attuned.MOD_ID.equals(namespace)) {
				return DataResult.error(() -> "Unknown Aspect namespace: " + namespace
					+ " (expected " + Attuned.MOD_ID + ")");
			}
			path = normalized.substring(separator + 1);
		}
		for (Aspect aspect : values()) {
			if (aspect.serializedName.equals(path)) {
				return DataResult.success(aspect);
			}
		}
		return DataResult.error(() -> "Unknown Aspect: " + name
			+ " (expected attuned:fury, attuned:bastion, attuned:zephyr, attuned:holy,"
			+ " attuned:tide, attuned:forge, attuned:verdant, or attuned:umbral)");
	}
}
