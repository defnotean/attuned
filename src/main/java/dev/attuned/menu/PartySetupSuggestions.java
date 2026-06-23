package dev.attuned.menu;

import dev.attuned.party.CirclePolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Pure advisory policy for Circle-aware saved-build setup warnings. */
public final class PartySetupSuggestions {
	private static final String NO_REVEAL = "No one is carrying reveal.";
	private static final String NO_FRONT_LINE = "Party has no Anchor/Vanguard front line.";
	private static final String DUPLICATE_SHADE =
		"Two members are both low-light Shade builds; bright caves may be safer with Witness support.";

	private PartySetupSuggestions() {}

	public record MemberRole(UUID id, String role) {
		public MemberRole {
			id = Objects.requireNonNull(id, "id");
			role = sanitizeRole(role);
		}
	}

	public static List<String> warnings(UUID selfId, String candidateRole, List<MemberRole> members) {
		if (selfId == null || members == null || members.size() < 2) {
			return List.of();
		}
		List<String> roles = previewRoles(selfId, candidateRole, members);
		if (roles.size() < 2) {
			return List.of();
		}
		boolean hasWitness = containsRole(roles, "Witness");
		boolean hasFrontLine = containsRole(roles, "Anchor") || containsRole(roles, "Vanguard");
		long shadeCount = roles.stream()
			.filter(role -> sameRole(role, "Shade"))
			.count();
		List<String> warnings = new ArrayList<>();
		if (!hasWitness) {
			warnings.add(NO_REVEAL);
		}
		if (!hasFrontLine) {
			warnings.add(NO_FRONT_LINE);
		}
		if (shadeCount >= 2L && !hasWitness) {
			warnings.add(DUPLICATE_SHADE);
		}
		return List.copyOf(warnings);
	}

	private static List<String> previewRoles(UUID selfId, String candidateRole, List<MemberRole> members) {
		String candidate = sanitizeRole(candidateRole);
		List<String> roles = new ArrayList<>(members.size());
		boolean sawSelf = false;
		for (MemberRole member : members) {
			if (member == null) {
				continue;
			}
			String role = member.id().equals(selfId) ? candidate : member.role();
			if (member.id().equals(selfId)) {
				sawSelf = true;
			}
			if (!role.isBlank()) {
				roles.add(role);
			}
		}
		if (!sawSelf && !candidate.isBlank()) {
			roles.add(candidate);
		}
		return roles;
	}

	private static boolean containsRole(List<String> roles, String expected) {
		return roles.stream().anyMatch(role -> sameRole(role, expected));
	}

	private static boolean sameRole(String role, String expected) {
		return role.trim().equalsIgnoreCase(expected);
	}

	private static String sanitizeRole(String role) {
		String value = role == null ? "" : role;
		StringBuilder sanitized = new StringBuilder(value.length());
		for (int index = 0; index < value.length(); index++) {
			char current = value.charAt(index);
			if (current == '\u00A7') {
				index++;
				continue;
			}
			if (!Character.isISOControl(current)) {
				sanitized.append(current);
			}
		}
		String normalized = sanitized.toString().trim();
		if (normalized.length() > CirclePolicy.MAX_NAME_LENGTH) {
			normalized = normalized.substring(0, CirclePolicy.MAX_NAME_LENGTH);
		}
		return titleCaseKnownRole(normalized);
	}

	private static String titleCaseKnownRole(String role) {
		String normalized = role.toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "witness" -> "Witness";
			case "anchor" -> "Anchor";
			case "vanguard" -> "Vanguard";
			case "shade" -> "Shade";
			default -> role;
		};
	}
}
