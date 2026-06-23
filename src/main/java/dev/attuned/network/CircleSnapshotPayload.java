package dev.attuned.network;

import dev.attuned.Attuned;
import dev.attuned.party.CirclePolicy;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client public Circle roster snapshot for the party HUD. */
public record CircleSnapshotPayload(String name, List<CircleSnapshotPayload.Member> members)
		implements FabricPacket {
	private static final int MAX_PUBLIC_MEMBERS = 8;
	public static final CircleSnapshotPayload EMPTY = new CircleSnapshotPayload("", List.of());

	public CircleSnapshotPayload {
		name = name == null || name.isBlank() ? "" : CirclePolicy.sanitizeName(name);
		List<CircleSnapshotPayload.Member> rows = Objects.requireNonNull(members, "members");
		members = List.copyOf(rows.subList(0, Math.min(MAX_PUBLIC_MEMBERS, rows.size())));
	}

	public static final PacketType<CircleSnapshotPayload> TYPE =
		PacketType.create(new ResourceLocation(Attuned.MOD_ID, "circle_snapshot"),
			CircleSnapshotPayload::new);

	public CircleSnapshotPayload(FriendlyByteBuf buf) {
		this(buf.readUtf(CirclePolicy.MAX_NAME_LENGTH), buf.readList(Member::read));
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeUtf(name);
		buf.writeCollection(members, (buffer, member) -> member.write(buffer));
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}

	public record Member(UUID id, String name, boolean leader, String stance, String role, int activeCount, int dormantCount) {
		public Member {
			id = Objects.requireNonNull(id, "id");
			name = CirclePolicy.sanitizeName(name);
			stance = sanitizeSummaryLabel(stance);
			if (stance.isBlank()) {
				stance = "None";
			}
			role = sanitizeSummaryLabel(role);
			activeCount = Math.max(0, activeCount);
			dormantCount = Math.max(0, dormantCount);
		}

		public Member(UUID id, String name, boolean leader) {
			this(id, name, leader, "None", "", 0, 0);
		}

		private static Member read(FriendlyByteBuf buf) {
			return new Member(
				buf.readUUID(),
				buf.readUtf(CirclePolicy.MAX_NAME_LENGTH),
				buf.readBoolean(),
				buf.readUtf(CirclePolicy.MAX_NAME_LENGTH),
				buf.readUtf(CirclePolicy.MAX_NAME_LENGTH),
				buf.readVarInt(),
				buf.readVarInt());
		}

		private void write(FriendlyByteBuf buf) {
			buf.writeUUID(id);
			buf.writeUtf(name);
			buf.writeBoolean(leader);
			buf.writeUtf(stance);
			buf.writeUtf(role);
			buf.writeVarInt(activeCount);
			buf.writeVarInt(dormantCount);
		}
	}

	private static String sanitizeSummaryLabel(String raw) {
		String source = raw == null ? "" : raw;
		StringBuilder sanitized = new StringBuilder(source.length());
		for (int index = 0; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '\u00A7') {
				index++;
				continue;
			}
			if (Character.isISOControl(current)) {
				continue;
			}
			sanitized.append(current);
		}
		String value = sanitized.toString().trim();
		return value.length() > CirclePolicy.MAX_NAME_LENGTH
			? value.substring(0, CirclePolicy.MAX_NAME_LENGTH) : value;
	}
}
