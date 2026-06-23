package dev.attuned.network;

import dev.attuned.Attuned;
import dev.attuned.party.CirclePolicy;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client notice that a Circle member placed a navigation ping. */
public record CirclePingClientPayload(UUID source, String sourceName, double x, double y,
		double z, long expiresAtTick) implements FabricPacket {
	public CirclePingClientPayload {
		source = Objects.requireNonNull(source, "source");
		sourceName = CirclePolicy.sanitizeName(sourceName);
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			throw new IllegalArgumentException("Circle ping coordinates must be finite");
		}
	}

	public static final PacketType<CirclePingClientPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "circle_ping_client"),
			CirclePingClientPayload::new);

	public CirclePingClientPayload(FriendlyByteBuf buf) {
		this(buf.readUUID(),
			buf.readUtf(CirclePolicy.MAX_NAME_LENGTH),
			buf.readDouble(),
			buf.readDouble(),
			buf.readDouble(),
			buf.readVarLong());
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeUUID(source);
		buf.writeUtf(sourceName);
		buf.writeDouble(x);
		buf.writeDouble(y);
		buf.writeDouble(z);
		buf.writeVarLong(expiresAtTick);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
