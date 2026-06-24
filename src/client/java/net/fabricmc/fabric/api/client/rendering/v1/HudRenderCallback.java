package net.fabricmc.fabric.api.client.rendering.v1;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.mojang.blaze3d.vertex.PoseStack;

public final class HudRenderCallback {
	public static final Event EVENT = new Event();

	private HudRenderCallback() {}

	public static final class Event {
		private final List<Callback> callbacks = new ArrayList<>();

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
		}
	}

	@FunctionalInterface
	public interface Callback {
		void onHudRender(PoseStack poseStack, float tickDelta);
	}
}
