package dev.attuned.network;

import dev.attuned.Attuned;
import dev.attuned.party.CirclePolicy;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client public Circle roster snapshot for the party HUD. */
public record CircleSnapshotPayload(String name, List<CircleSnapshotPayload.Member> members)
		implements CustomPacketPayload {
	private static final int MAX_PUBLIC_MEMBERS = 8;
	public static final CircleSnapshotPayload EMPTY = new CircleSnapshotPayload("", List.of());

	public CircleSnapshotPayload {
		name = name == null || name.isBlank() ? "" : CirclePolicy.sanitizeName(name);
		List<CircleSnapshotPayload.Member> rows = Objects.requireNonNull(members, "members");
		members = List.copyOf(rows.subList(0, Math.min(MAX_PUBLIC_MEMBERS, rows.size())));
	}

	public static final Type<CircleSnapshotPayload> TYPE =
		new Type<>(new ResourceLocation(Attuned.MOD_ID, "circle_snapshot"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CircleSnapshotPayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, CircleSnapshotPayload::name,
			Member.STREAM_CODEC.apply(ByteBufCodecs.list()), CircleSnapshotPayload::members,
			CircleSnapshotPayload::new).cast();

	@Override
	public Type<CircleSnapshotPayload> type() {
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

		static final StreamCodec<RegistryFriendlyByteBuf, Member> STREAM_CODEC =
			new StreamCodec<>() {
				@Override
				public Member decode(RegistryFriendlyByteBuf buffer) {
					UUID id = UUIDUtil.STREAM_CODEC.decode(buffer);
					String name = ByteBufCodecs.STRING_UTF8.decode(buffer);
					boolean leader = ByteBufCodecs.BOOL.decode(buffer);
					String stance = ByteBufCodecs.STRING_UTF8.decode(buffer);
					String role = ByteBufCodecs.STRING_UTF8.decode(buffer);
					int activeCount = ByteBufCodecs.VAR_INT.decode(buffer);
					int dormantCount = ByteBufCodecs.VAR_INT.decode(buffer);
					return new Member(id, name, leader, stance, role, activeCount, dormantCount);
				}

				@Override
				public void encode(RegistryFriendlyByteBuf buffer, Member member) {
					UUIDUtil.STREAM_CODEC.encode(buffer, member.id());
					ByteBufCodecs.STRING_UTF8.encode(buffer, member.name());
					ByteBufCodecs.BOOL.encode(buffer, member.leader());
					ByteBufCodecs.STRING_UTF8.encode(buffer, member.stance());
					ByteBufCodecs.STRING_UTF8.encode(buffer, member.role());
					ByteBufCodecs.VAR_INT.encode(buffer, member.activeCount());
					ByteBufCodecs.VAR_INT.encode(buffer, member.dormantCount());
				}
			};
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
