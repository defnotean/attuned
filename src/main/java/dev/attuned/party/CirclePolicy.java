package dev.attuned.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure, Minecraft-free policy for transient Attuned Circles (small adventuring parties).
 *
 * <p>The runtime/networking layer should stay server-authoritative and wrap this class rather than
 * duplicating membership rules. This policy owns only deterministic state transitions: creation,
 * sanitized names, leader-only invites/kicks, invite expiry, capacity, leaving, and disbanding.
 */
public final class CirclePolicy {
	public static final String DEFAULT_NAME = "Circle";
	public static final int MAX_NAME_LENGTH = 32;

	private CirclePolicy() {}

	public enum Status {
		OK,
		DISBANDED,
		NOT_LEADER,
		ALREADY_MEMBER,
		PARTY_FULL,
		NO_INVITE,
		INVITE_EXPIRED,
		INVITE_COOLDOWN,
		CREATE_COOLDOWN,
		NOT_MEMBER,
		CANNOT_KICK_LEADER
	}

	public record Invite(UUID sender, UUID target, long expiresAtTick) {
		public Invite {
			sender = Objects.requireNonNull(sender, "sender");
			target = Objects.requireNonNull(target, "target");
		}

		public boolean expiredAt(long tick) {
			return tick >= expiresAtTick;
		}
	}

	public record Circle(String id, UUID leader, List<UUID> members, String name,
			Map<UUID, Invite> invites) {
		public Circle {
			id = Objects.requireNonNull(id, "id").trim();
			if (id.isEmpty()) {
				throw new IllegalArgumentException("Circle id must not be blank");
			}
			leader = Objects.requireNonNull(leader, "leader");
			List<UUID> normalizedMembers = uniqueMembers(members);
			if (!normalizedMembers.contains(leader)) {
				throw new IllegalArgumentException("Circle leader must be a member");
			}
			members = List.copyOf(normalizedMembers);
			name = sanitizeName(name);
			invites = immutableInvites(invites);
		}
	}

	public record Result(Optional<Circle> circle, Status status) {
		public Result {
			circle = Objects.requireNonNull(circle, "circle");
			status = Objects.requireNonNull(status, "status");
		}

		public static Result of(Circle circle, Status status) {
			return new Result(Optional.of(circle), status);
		}

		public static Result disbanded() {
			return new Result(Optional.empty(), Status.DISBANDED);
		}
	}

	public static Circle create(String id, UUID leader, String name) {
		return new Circle(id, leader, List.of(leader), name, Map.of());
	}

	public static Result invite(Circle circle, UUID actor, UUID target, long nowTick,
			long ttlTicks, int maxMembers) {
		circle = Objects.requireNonNull(circle, "circle");
		actor = Objects.requireNonNull(actor, "actor");
		target = Objects.requireNonNull(target, "target");
		if (!circle.leader().equals(actor)) {
			return Result.of(circle, Status.NOT_LEADER);
		}
		if (circle.members().contains(target)) {
			return Result.of(circle, Status.ALREADY_MEMBER);
		}
		if (circle.members().size() >= sanitizedMaxMembers(maxMembers)) {
			return Result.of(circle, Status.PARTY_FULL);
		}
		Map<UUID, Invite> invites = new LinkedHashMap<>(circle.invites());
		invites.put(target, new Invite(actor, target, nowTick + Math.max(0L, ttlTicks)));
		return Result.of(withInvites(circle, invites), Status.OK);
	}

	public static Result accept(Circle circle, UUID target, long nowTick, int maxMembers) {
		circle = Objects.requireNonNull(circle, "circle");
		target = Objects.requireNonNull(target, "target");
		if (circle.members().contains(target)) {
			return Result.of(circle, Status.ALREADY_MEMBER);
		}
		Invite invite = circle.invites().get(target);
		if (invite == null) {
			return Result.of(circle, Status.NO_INVITE);
		}
		Map<UUID, Invite> invites = new LinkedHashMap<>(circle.invites());
		invites.remove(target);
		Circle withoutInvite = withInvites(circle, invites);
		if (invite.expiredAt(nowTick)) {
			return Result.of(withoutInvite, Status.INVITE_EXPIRED);
		}
		if (circle.members().size() >= sanitizedMaxMembers(maxMembers)) {
			return Result.of(withoutInvite, Status.PARTY_FULL);
		}
		List<UUID> members = new ArrayList<>(circle.members());
		members.add(target);
		return Result.of(new Circle(circle.id(), circle.leader(), members, circle.name(), invites), Status.OK);
	}

	public static Result kick(Circle circle, UUID actor, UUID target) {
		circle = Objects.requireNonNull(circle, "circle");
		actor = Objects.requireNonNull(actor, "actor");
		target = Objects.requireNonNull(target, "target");
		if (!circle.leader().equals(actor)) {
			return Result.of(circle, Status.NOT_LEADER);
		}
		if (!circle.members().contains(target)) {
			return Result.of(circle, Status.NOT_MEMBER);
		}
		if (circle.leader().equals(target)) {
			return Result.of(circle, Status.CANNOT_KICK_LEADER);
		}
		return removeMember(circle, target, Status.OK);
	}

	public static Result leave(Circle circle, UUID member) {
		circle = Objects.requireNonNull(circle, "circle");
		member = Objects.requireNonNull(member, "member");
		if (!circle.members().contains(member)) {
			return Result.of(circle, Status.NOT_MEMBER);
		}
		return removeMember(circle, member, Status.OK);
	}

	public static Result disband(Circle circle, UUID actor) {
		circle = Objects.requireNonNull(circle, "circle");
		actor = Objects.requireNonNull(actor, "actor");
		if (!circle.leader().equals(actor)) {
			return Result.of(circle, Status.NOT_LEADER);
		}
		return Result.disbanded();
	}

	public static String sanitizeName(String raw) {
		String source = raw == null ? "" : raw;
		StringBuilder sanitized = new StringBuilder(source.length());
		for (int index = 0; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '\u00A7') {
				index++; // Drop the Minecraft formatting introducer and its format code.
				continue;
			}
			if (Character.isISOControl(current)) {
				continue;
			}
			sanitized.append(current);
		}
		String name = sanitized.toString().trim();
		if (name.isEmpty()) {
			name = DEFAULT_NAME;
		}
		return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
	}

	private static Result removeMember(Circle circle, UUID member, Status status) {
		List<UUID> members = new ArrayList<>(circle.members());
		members.remove(member);
		if (members.isEmpty()) {
			return Result.disbanded();
		}
		UUID leader = circle.leader().equals(member) ? members.get(0) : circle.leader();
		Map<UUID, Invite> invites = new LinkedHashMap<>(circle.invites());
		invites.remove(member);
		if (!leader.equals(circle.leader())) {
			invites.clear();
		}
		return Result.of(new Circle(circle.id(), leader, members, circle.name(), invites), status);
	}

	private static Circle withInvites(Circle circle, Map<UUID, Invite> invites) {
		return new Circle(circle.id(), circle.leader(), circle.members(), circle.name(), invites);
	}

	private static int sanitizedMaxMembers(int maxMembers) {
		return Math.max(1, maxMembers);
	}

	private static List<UUID> uniqueMembers(List<UUID> source) {
		List<UUID> members = new ArrayList<>();
		if (source == null) {
			return members;
		}
		for (UUID member : source) {
			UUID id = Objects.requireNonNull(member, "member");
			if (!members.contains(id)) {
				members.add(id);
			}
		}
		return members;
	}

	private static Map<UUID, Invite> immutableInvites(Map<UUID, Invite> source) {
		Map<UUID, Invite> normalized = new LinkedHashMap<>();
		if (source != null) {
			for (Map.Entry<UUID, Invite> entry : source.entrySet()) {
				UUID target = Objects.requireNonNull(entry.getKey(), "invite target");
				Invite invite = Objects.requireNonNull(entry.getValue(), "invite");
				if (target.equals(invite.target())) {
					normalized.put(target, invite);
				}
			}
		}
		return Collections.unmodifiableMap(normalized);
	}
}
