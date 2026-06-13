package dev.attuned.client.hud;

import dev.attuned.client.AttunedClientConfig;
import dev.attuned.client.AttunedClientConfig.HudLayout;

/**
 * Shared anchor math for the HUD cluster. The Foci HUD is the cluster primary:
 * its corner placement comes from the configured anchor plus pixel offsets.
 * Defaults ({@code bottom_right}, no offset, scale 1) reproduce the historical
 * hard-coded layout exactly, so existing installs see no movement.
 */
final class HudAnchor {
	static final int SCREEN_MARGIN = 4;

	private HudAnchor() {}

	/** X for a HUD block of {@code width} under the configured anchor/offset. */
	static int x(int screenW, int width, HudLayout layout) {
		int base = layout.anchor().left()
			? SCREEN_MARGIN
			: screenW - width - SCREEN_MARGIN;
		return clamp(base + layout.offsetX(), screenW, width);
	}

	/** Y for a HUD block of {@code height} under the configured anchor/offset. */
	static int y(int screenH, int height, HudLayout layout) {
		int base = layout.anchor().top()
			? SCREEN_MARGIN
			: screenH - height - SCREEN_MARGIN;
		return clamp(base + layout.offsetY(), screenH, height);
	}

	/** Keeps the block fully on screen no matter how large the offsets are. */
	private static int clamp(int position, int screenSpan, int blockSpan) {
		int max = Math.max(SCREEN_MARGIN, screenSpan - blockSpan - SCREEN_MARGIN);
		return Math.max(SCREEN_MARGIN, Math.min(max, position));
	}

	static HudLayout layout() {
		return AttunedClientConfig.get().hudLayout();
	}
}
