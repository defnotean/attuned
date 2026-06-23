package dev.attuned.menu;

import dev.attuned.api.focus.Affinity;
import dev.attuned.attunement.BudgetResolver;
import dev.attuned.attunement.FocusPreset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Pure setup metadata inference for saved Focus builds. */
public final class PresetMetadataResolver {
	private PresetMetadataResolver() {}

	public record ResolvedFocus(
			Optional<Affinity> affinity,
			Optional<ResourceLocation> faction,
			boolean activeAbility,
			Optional<BudgetResolver.DormantReason> dormantReason) {
		public ResolvedFocus {
			affinity = Objects.requireNonNull(affinity, "affinity");
			faction = Objects.requireNonNull(faction, "faction");
			dormantReason = Objects.requireNonNull(dormantReason, "dormantReason");
		}
	}

	public static FocusPreset.SetupMetadata infer(List<ResolvedFocus> foci) {
		return infer(foci, true);
	}

	public static FocusPreset.SetupMetadata infer(List<ResolvedFocus> foci, boolean includeWarnings) {
		List<ResolvedFocus> normalized = normalized(foci);
		List<ResolvedFocus> active = activeFoci(normalized);
		List<String> warnings = includeWarnings ? warnings(normalized, active) : List.of();
		return new FocusPreset.SetupMetadata(role(active), "", 0, warnings, List.of(), "", "");
	}

	public static String inferRole(List<ResolvedFocus> foci) {
		return role(activeFoci(normalized(foci)));
	}

	public static FocusPreset withWarningsEnabled(FocusPreset preset, boolean enabled) {
		Objects.requireNonNull(preset, "preset");
		if (enabled || preset.warnings().isEmpty()) {
			return preset;
		}
		FocusPreset.SetupMetadata metadata = preset.metadata();
		return new FocusPreset(preset.name(), preset.slots(), new FocusPreset.SetupMetadata(
			metadata.role(),
			metadata.note(),
			metadata.preferredPartySize(),
			List.of(),
			metadata.requires(),
			metadata.minecraftVersion(),
			metadata.attunedVersion()));
	}

	private static List<ResolvedFocus> normalized(List<ResolvedFocus> foci) {
		return foci == null ? List.of() : foci.stream()
			.filter(Objects::nonNull)
			.toList();
	}

	private static List<ResolvedFocus> activeFoci(List<ResolvedFocus> foci) {
		return foci.stream()
			.filter(focus -> focus.dormantReason().isEmpty())
			.toList();
	}

	private static List<String> warnings(List<ResolvedFocus> foci, List<ResolvedFocus> active) {
		List<String> warnings = new ArrayList<>();
		if (missingCommitment(active)) {
			warnings.add("No committed affinity lane.");
		}
		if (foci.stream().anyMatch(focus ->
				focus.dormantReason().filter(reason -> reason == BudgetResolver.DormantReason.NOT_ENOUGH_CAPACITY)
					.isPresent())) {
			warnings.add("Over capacity: dormant Foci will sleep at current cap.");
		}
		if (foci.stream().anyMatch(focus ->
				focus.dormantReason().filter(reason -> reason == BudgetResolver.DormantReason.DUPLICATE_UNIQUE)
					.isPresent())) {
			warnings.add("Duplicate unique Focus is dormant.");
		}
		if (!active.isEmpty() && active.stream().noneMatch(ResolvedFocus::activeAbility)) {
			warnings.add("No active Focus Ability.");
		}
		return warnings;
	}

	private static boolean missingCommitment(List<ResolvedFocus> active) {
		Map<Affinity, Integer> counts = affinityCounts(active);
		if (counts.size() <= 1 || counts.size() >= 4) {
			return false;
		}
		return counts.values().stream().noneMatch(count -> count >= 3);
	}

	private static String role(List<ResolvedFocus> active) {
		Map<Affinity, Integer> affinityCounts = affinityCounts(active);
		long neutralCount = active.stream()
			.filter(focus -> focus.affinity().isEmpty())
			.count();
		if (affinityCounts.size() >= 4 || (affinityCounts.isEmpty() && neutralCount >= 4)) {
			return "Nomad";
		}
		Optional<String> factionRole = dominantFactionRole(active);
		if (factionRole.isPresent()) {
			return factionRole.get();
		}
		return affinityCounts.entrySet().stream()
			.filter(entry -> entry.getValue() >= 3)
			.findFirst()
			.map(entry -> roleFor(entry.getKey()))
			.orElse("");
	}

	private static Map<Affinity, Integer> affinityCounts(List<ResolvedFocus> active) {
		Map<Affinity, Integer> counts = new EnumMap<>(Affinity.class);
		for (ResolvedFocus focus : active) {
			focus.affinity().ifPresent(affinity -> counts.merge(affinity, 1, Integer::sum));
		}
		return counts;
	}

	private static Optional<String> dominantFactionRole(List<ResolvedFocus> active) {
		Map<String, Integer> counts = new HashMap<>();
		for (ResolvedFocus focus : active) {
			focus.faction()
				.map(ResourceLocation::toString)
				.ifPresent(id -> counts.merge(id, 1, Integer::sum));
		}
		return counts.entrySet().stream()
			.filter(entry -> entry.getValue() >= 3)
			.map(entry -> roleForFaction(entry.getKey()))
			.filter(Optional::isPresent)
			.map(Optional::get)
			.findFirst();
	}

	private static Optional<String> roleForFaction(String faction) {
		return switch (faction) {
			case "attuned:unseen", "attuned:umbral" -> Optional.of("Shade");
			case "attuned:radiant" -> Optional.of("Witness");
			case "attuned:ashen_forge", "attuned:forgebound" -> Optional.of("Smith");
			case "attuned:verdant_choir", "attuned:wildroot" -> Optional.of("Warden");
			default -> Optional.empty();
		};
	}

	private static String roleFor(Affinity affinity) {
		return switch (affinity) {
			case FURY -> "Vanguard";
			case BASTION -> "Anchor";
			case ZEPHYR -> "Gale";
			case HOLY -> "Witness";
			case TIDE -> "Current";
			case FORGE -> "Smith";
			case VERDANT -> "Warden";
			case UMBRAL -> "Shade";
		};
	}
}
