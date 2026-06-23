package dev.attuned.menu;

import dev.attuned.attunement.FocusPreset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Pure validation for imported build codes before they are saved server-side. */
public final class PresetImportValidator {
	private PresetImportValidator() {}

	public record Result(List<String> missing) {
		public Result {
			missing = List.copyOf(missing);
		}
	}

	public static Result validate(List<String> slots, Set<String> registeredFocusIds) {
		Set<String> registered = registeredFocusIds == null ? Set.of() : registeredFocusIds;
		List<String> missing = new ArrayList<>();
		if (slots == null) {
			return new Result(missing);
		}
		for (String raw : slots) {
			String id = raw == null ? "" : raw.trim();
			if (id.isEmpty() || registered.contains(FocusPreset.slotId(id)) || missing.contains(id)) {
				continue;
			}
			missing.add(id);
		}
		return new Result(missing);
	}

	public static Result validate(FocusPreset preset, Set<String> registeredFocusIds) {
		if (preset == null) {
			return new Result(List.of());
		}
		List<String> ids = new ArrayList<>(preset.slots());
		ids.addAll(preset.requires());
		return validate(ids, registeredFocusIds);
	}
}
