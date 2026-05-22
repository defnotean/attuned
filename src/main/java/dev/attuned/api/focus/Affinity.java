package dev.attuned.api.focus;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The three Focus affinities, arranged in a rock-paper-scissors counter cycle:
 * Fury beats Bastion, Bastion beats Zephyr, Zephyr beats Fury. A Focus with no
 * affinity (an affinity-neutral utility Focus) is represented by an empty
 * {@code Optional<Affinity>} on its {@link FocusDefinition}.
 */
public enum Affinity implements StringRepresentable {
	FURY("fury"),
	BASTION("bastion"),
	ZEPHYR("zephyr");

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
		return switch (this) {
			case FURY -> other == BASTION;
			case BASTION -> other == ZEPHYR;
			case ZEPHYR -> other == FURY;
		};
	}
}
