package dev.attuned.menu;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure id-string resolver for moving Foci between satchel and equipped slots. */
public final class SatchelMoveResolver {
	private SatchelMoveResolver() {}

	public record Move(boolean applied, int satchelSlot, Optional<String> satchelWrite,
			Optional<String> equippedWrite) {
		public Move {
			satchelWrite = satchelWrite == null ? Optional.empty() : satchelWrite;
			equippedWrite = equippedWrite == null ? Optional.empty() : equippedWrite;
		}

		public static Move none() {
			return new Move(false, -1, Optional.empty(), Optional.empty());
		}
	}

	public static Move satchelToEquipped(List<Optional<String>> satchel, List<Optional<String>> equipped,
			int satchelSlot, int equippedSlot) {
		if (!inRange(satchel, satchelSlot) || !inRange(equipped, equippedSlot)) {
			return Move.none();
		}
		Optional<String> source = normalize(satchel.get(satchelSlot));
		if (source.isEmpty()) {
			return Move.none();
		}
		Optional<String> displaced = normalize(equipped.get(equippedSlot));
		return new Move(true, satchelSlot, displaced, source);
	}

	public static Move equippedToSatchel(List<Optional<String>> satchel, List<Optional<String>> equipped,
			int equippedSlot, int preferredSatchelSlot) {
		if (!inRange(equipped, equippedSlot)) {
			return Move.none();
		}
		Optional<String> source = normalize(equipped.get(equippedSlot));
		if (source.isEmpty()) {
			return Move.none();
		}
		int target = emptyTarget(satchel, preferredSatchelSlot);
		if (target < 0) {
			return Move.none();
		}
		return new Move(true, target, source, Optional.empty());
	}

	private static int emptyTarget(List<Optional<String>> satchel, int preferredSatchelSlot) {
		if (inRange(satchel, preferredSatchelSlot) && normalize(satchel.get(preferredSatchelSlot)).isEmpty()) {
			return preferredSatchelSlot;
		}
		for (int i = 0; i < satchel.size(); i++) {
			if (normalize(satchel.get(i)).isEmpty()) {
				return i;
			}
		}
		return -1;
	}

	private static boolean inRange(List<Optional<String>> slots, int slot) {
		Objects.requireNonNull(slots, "slots");
		return slot >= 0 && slot < slots.size();
	}

	private static Optional<String> normalize(Optional<String> value) {
		if (value == null || value.isEmpty()) {
			return Optional.empty();
		}
		String id = value.get();
		return id == null || id.isBlank() ? Optional.empty() : Optional.of(id);
	}
}
