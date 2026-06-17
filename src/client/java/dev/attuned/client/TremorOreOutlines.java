package dev.attuned.client;

import dev.attuned.network.TremorOreHintPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/** Client-side timed ore outline shown by the Tremor Focus. */
public final class TremorOreOutlines {
	private static final TagKey<Block> ORES =
		TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"));
	private static final int OUTLINE_TICKS = 120;
	private static final int OUTLINE_COLOR = 0xCCB266FF;

	private static final List<BlockPos> orePositions = new ArrayList<>();
	private static ClientLevel highlightedLevel;
	private static long expiresAt;
	private static boolean initialized;

	private TremorOreOutlines() {}

	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		ClientPlayNetworking.registerGlobalReceiver(TremorOreHintPayload.TYPE, (payload, context) ->
			context.client().execute(() -> highlight(payload.orePositions())));
		WorldRenderEvents.END.register(TremorOreOutlines::render);
		// Drop the highlighted-level reference on disconnect; render() does not run
		// on the title screen, so without this the old ClientLevel would stay pinned.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	private static void highlight(List<BlockPos> positions) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || positions.isEmpty()) {
			return;
		}
		orePositions.clear();
		for (BlockPos pos : positions) {
			orePositions.add(pos.immutable());
		}
		highlightedLevel = minecraft.level;
		expiresAt = minecraft.level.getGameTime() + OUTLINE_TICKS;
	}

	private static void render(WorldRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || orePositions.isEmpty()) {
			return;
		}
		if (minecraft.level != highlightedLevel) {
			clear();
			return;
		}
		// Mined-out vein blocks drop off individually; the rest of the outline stays.
		orePositions.removeIf(pos -> !minecraft.level.getBlockState(pos).is(ORES));
		if (minecraft.level.getGameTime() > expiresAt || orePositions.isEmpty()) {
			clear();
			return;
		}

		Vec3 camera = context.gameRenderer().getMainCamera().getPosition();
		RenderType outline = TremorOreRenderTypes.oreOutline();
		float alpha = ((OUTLINE_COLOR >>> 24) & 0xFF) / 255.0F;
		float red = ((OUTLINE_COLOR >>> 16) & 0xFF) / 255.0F;
		float green = ((OUTLINE_COLOR >>> 8) & 0xFF) / 255.0F;
		float blue = (OUTLINE_COLOR & 0xFF) / 255.0F;
		for (BlockPos orePos : orePositions) {
			double x = orePos.getX() - camera.x();
			double y = orePos.getY() - camera.y();
			double z = orePos.getZ() - camera.z();
			LevelRenderer.renderLineBox(
				context.matrixStack(),
				context.consumers().getBuffer(outline),
				x, y, z, x + 1.0D, y + 1.0D, z + 1.0D,
				red, green, blue, alpha);
		}
	}

	private static void clear() {
		orePositions.clear();
		highlightedLevel = null;
		expiresAt = 0L;
	}
}
