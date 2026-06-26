package net.fabricmc.fabric.api.client.rendering.v1.level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class LevelRenderEvents {
	public static final Event END_MAIN = new Event();

	private LevelRenderEvents() {}

	public static final class Event {
		private final List<Callback> callbacks = new ArrayList<>();
		private boolean subscribed;

		public void register(Callback callback) {
			callbacks.add(Objects.requireNonNull(callback, "callback"));
			if (!subscribed) {
				subscribed = true;
				NeoForge.EVENT_BUS.addListener((SubmitCustomGeometryEvent event) -> {
					LevelRenderContext context = new LevelRenderContext() {
						@Override
						public com.mojang.blaze3d.vertex.PoseStack poseStack() {
							return event.getPoseStack();
						}

						@Override
						public net.minecraft.client.renderer.GameRenderer gameRenderer() {
							return Minecraft.getInstance().gameRenderer;
						}

						@Override
						public SubmitNodeCollector submitNodeCollector() {
							return event.getSubmitNodeCollector();
						}
					};
					for (Callback registered : List.copyOf(callbacks)) {
						registered.render(context);
					}
				});
			}
		}
	}

	@FunctionalInterface
	public interface Callback {
		void render(LevelRenderContext context);
	}
}
