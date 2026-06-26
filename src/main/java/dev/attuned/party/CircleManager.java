package dev.attuned.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure runtime owner for transient Attuned Circles.
 *
 * <p>Networking should wrap this manager instead of trusting client-submitted membership state.
 * It owns the one-Circle-per-player index, delegates per-Circle transitions to
 * {@link CirclePolicy}, and exposes cleanup hooks for disconnect/server-stop integration.
 */
public final class CircleManager {
	private final int maxMembers;
	private final long inviteTtlTicks;
	private final long inviteCooldownTicks;
	private final long createCooldownTicks;
	private final Map<String, CirclePolicy.Circle> circles = new LinkedHashMap<>();
	private final Map<UUID, String> circleByMember = new HashMap<>();
	private final Map<UUID, Long> senderInviteReadyAt = new HashMap<>();
	private final Map<UUID, Long> recipientInviteReadyAt = new HashMap<>();
	private final Map<UUID, Long> createReadyAt = new HashMap<>();
	private long nextCircleId = 1L;

	public CircleManager(int maxMembers, long inviteTtlTicks) {
		this(maxMembers, inviteTtlTicks, 0L, 0L);
	}

	public CircleManager(int maxMembers, long inviteTtlTicks, long inviteCooldownTicks) {
		this(maxMembers, inviteTtlTicks, inviteCooldownTicks, 0L);
	}

	public CircleManager(int maxMembers, long inviteTtlTicks, long inviteCooldownTicks,
			long createCooldownTicks) {
		this.maxMembers = Math.max(1, maxMembers);
		this.inviteTtlTicks = Math.max(0L, inviteTtlTicks);
		this.inviteCooldownTicks = Math.max(0L, inviteCooldownTicks);
		this.createCooldownTicks = Math.max(0L, createCooldownTicks);
	}

	public CirclePolicy.Result create(UUID leader, String name) {
		return create(leader, name, 0L);
	}

	public CirclePolicy.Result create(UUID leader, String name, long nowTick) {
		leader = Objects.requireNonNull(leader, "leader");
		Optional<CirclePolicy.Circle> existing = circleOf(leader);
		if (existing.isPresent()) {
			return CirclePolicy.Result.of(existing.orElseThrow(), CirclePolicy.Status.ALREADY_MEMBER);
		}
		pruneCreateCooldowns(nowTick);
		if (readyAt(createReadyAt, leader) > nowTick) {
			return empty(CirclePolicy.Status.CREATE_COOLDOWN);
		}
		CirclePolicy.Circle circle = CirclePolicy.create(nextCircleId(), leader, name);
		store(circle);
		return CirclePolicy.Result.of(circle, CirclePolicy.Status.OK);
	}

	public CirclePolicy.Result invite(UUID actor, UUID target, long nowTick) {
		Objects.requireNonNull(actor, "actor");
		Objects.requireNonNull(target, "target");
		Optional<CirclePolicy.Circle> circle = circleOf(actor);
		if (circle.isEmpty()) {
			return empty(CirclePolicy.Status.NOT_MEMBER);
		}
		if (circleOf(target).isPresent()) {
			return CirclePolicy.Result.of(circle.orElseThrow(), CirclePolicy.Status.ALREADY_MEMBER);
		}
		CirclePolicy.Circle owned = circle.orElseThrow();
		pruneInviteCooldowns(nowTick);
		if (inviteOnCooldown(actor, target, nowTick)) {
			return CirclePolicy.Result.of(owned, CirclePolicy.Status.INVITE_COOLDOWN);
		}
		CirclePolicy.Result result =
			CirclePolicy.invite(owned, actor, target, nowTick, inviteTtlTicks, maxMembers);
		if (result.status() == CirclePolicy.Status.OK) {
			markInviteCooldown(actor, target, nowTick);
		}
		return apply(owned, result);
	}

	public CirclePolicy.Result accept(UUID target, UUID inviter, long nowTick) {
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(inviter, "inviter");
		Optional<CirclePolicy.Circle> current = circleOf(target);
		if (current.isPresent()) {
			return CirclePolicy.Result.of(current.orElseThrow(), CirclePolicy.Status.ALREADY_MEMBER);
		}
		Optional<CirclePolicy.Circle> circle = circleOf(inviter);
		if (circle.isEmpty() || !circle.orElseThrow().invites().containsKey(target)) {
			return empty(CirclePolicy.Status.NO_INVITE);
		}
		return apply(circle.orElseThrow(), CirclePolicy.accept(circle.orElseThrow(), target, nowTick, maxMembers));
	}

	public CirclePolicy.Result kick(UUID actor, UUID target) {
		Objects.requireNonNull(actor, "actor");
		Objects.requireNonNull(target, "target");
		Optional<CirclePolicy.Circle> circle = circleOf(actor);
		if (circle.isEmpty()) {
			return empty(CirclePolicy.Status.NOT_MEMBER);
		}
		return apply(circle.orElseThrow(), CirclePolicy.kick(circle.orElseThrow(), actor, target));
	}

	public CirclePolicy.Result leave(UUID member) {
		return leave(member, 0L);
	}

	public CirclePolicy.Result leave(UUID member, long nowTick) {
		Objects.requireNonNull(member, "member");
		Optional<CirclePolicy.Circle> circle = circleOf(member);
		if (circle.isEmpty()) {
			return empty(CirclePolicy.Status.NOT_MEMBER);
		}
		CirclePolicy.Result result = apply(circle.orElseThrow(), CirclePolicy.leave(circle.orElseThrow(), member));
		if (result.status() == CirclePolicy.Status.DISBANDED) {
			markCreateCooldown(member, nowTick);
		}
		return result;
	}

	public CirclePolicy.Result disband(UUID actor, long nowTick) {
		Objects.requireNonNull(actor, "actor");
		Optional<CirclePolicy.Circle> circle = circleOf(actor);
		if (circle.isEmpty()) {
			return empty(CirclePolicy.Status.NOT_MEMBER);
		}
		CirclePolicy.Result result = apply(circle.orElseThrow(),
			CirclePolicy.disband(circle.orElseThrow(), actor));
		if (result.status() == CirclePolicy.Status.DISBANDED) {
			markCreateCooldown(actor, nowTick);
		}
		return result;
	}

	public void forgetPlayer(UUID player) {
		Objects.requireNonNull(player, "player");
		circleOf(player).ifPresent(circle -> apply(circle, CirclePolicy.leave(circle, player)));
		pruneInvitesTo(player);
		senderInviteReadyAt.remove(player);
		recipientInviteReadyAt.remove(player);
	}

	public void clear() {
		circles.clear();
		circleByMember.clear();
		senderInviteReadyAt.clear();
		recipientInviteReadyAt.clear();
		createReadyAt.clear();
		nextCircleId = 1L;
	}

	public Optional<CirclePolicy.Circle> circleOf(UUID member) {
		Objects.requireNonNull(member, "member");
		String circleId = circleByMember.get(member);
		return circleId == null ? Optional.empty() : Optional.ofNullable(circles.get(circleId));
	}

	public Optional<CirclePolicy.Circle> circleById(String id) {
		return Optional.ofNullable(circles.get(Objects.requireNonNull(id, "id")));
	}

	public int circleCount() {
		return circles.size();
	}

	private CirclePolicy.Result apply(CirclePolicy.Circle previous, CirclePolicy.Result result) {
		if (result.status() == CirclePolicy.Status.DISBANDED) {
			remove(previous);
		} else {
			result.circle().ifPresent(circle -> replace(previous, circle));
		}
		return result;
	}

	private void store(CirclePolicy.Circle circle) {
		circles.put(circle.id(), circle);
		for (UUID member : circle.members()) {
			circleByMember.put(member, circle.id());
		}
	}

	private void replace(CirclePolicy.Circle previous, CirclePolicy.Circle updated) {
		remove(previous);
		store(updated);
	}

	private void remove(CirclePolicy.Circle circle) {
		circles.remove(circle.id());
		for (UUID member : circle.members()) {
			circleByMember.remove(member, circle.id());
		}
	}

	private void pruneInvitesTo(UUID player) {
		List<CirclePolicy.Circle> current = new ArrayList<>(circles.values());
		for (CirclePolicy.Circle circle : current) {
			if (!circle.invites().containsKey(player)) {
				continue;
			}
			Map<UUID, CirclePolicy.Invite> invites = new LinkedHashMap<>(circle.invites());
			invites.remove(player);
			replace(circle, new CirclePolicy.Circle(circle.id(), circle.leader(),
				circle.members(), circle.name(), invites));
		}
	}

	private boolean inviteOnCooldown(UUID actor, UUID target, long nowTick) {
		if (inviteCooldownTicks <= 0L) {
			return false;
		}
		return readyAt(senderInviteReadyAt, actor) > nowTick
			|| readyAt(recipientInviteReadyAt, target) > nowTick;
	}

	private void markInviteCooldown(UUID actor, UUID target, long nowTick) {
		if (inviteCooldownTicks <= 0L) {
			return;
		}
		long readyAt = nowTick + inviteCooldownTicks;
		senderInviteReadyAt.put(actor, readyAt);
		recipientInviteReadyAt.put(target, readyAt);
	}

	private void pruneInviteCooldowns(long nowTick) {
		senderInviteReadyAt.entrySet().removeIf(entry -> entry.getValue() <= nowTick);
		recipientInviteReadyAt.entrySet().removeIf(entry -> entry.getValue() <= nowTick);
	}

	private void markCreateCooldown(UUID player, long nowTick) {
		if (createCooldownTicks <= 0L) {
			return;
		}
		createReadyAt.put(player, nowTick + createCooldownTicks);
	}

	private void pruneCreateCooldowns(long nowTick) {
		createReadyAt.entrySet().removeIf(entry -> entry.getValue() <= nowTick);
	}

	private static long readyAt(Map<UUID, Long> cooldowns, UUID player) {
		return cooldowns.getOrDefault(player, 0L);
	}

	private String nextCircleId() {
		return "circle-" + nextCircleId++;
	}

	private static CirclePolicy.Result empty(CirclePolicy.Status status) {
		return new CirclePolicy.Result(Optional.empty(), status);
	}
}
