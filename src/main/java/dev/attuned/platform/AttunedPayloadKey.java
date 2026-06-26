package dev.attuned.platform;

import dev.attuned.Attuned;
import net.minecraft.resources.Identifier;

public enum AttunedPayloadKey {
	ABILITY("ability", Direction.SERVERBOUND),
	SAVE_PRESET("save_preset", Direction.SERVERBOUND),
	CIRCLE_SNAPSHOT("circle_snapshot", Direction.CLIENTBOUND);

	private final String path;
	private final Direction direction;

	AttunedPayloadKey(String path, Direction direction) {
		this.path = path;
		this.direction = direction;
	}

	public Identifier id() {
		return Identifier.fromNamespaceAndPath(Attuned.MOD_ID, path);
	}

	public Direction direction() {
		return direction;
	}

	public enum Direction {
		SERVERBOUND,
		CLIENTBOUND
	}
}
