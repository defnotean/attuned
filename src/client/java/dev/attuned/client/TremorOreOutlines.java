package dev.attuned.client;

import dev.attuned.network.TremorOreHintPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/** Client-side timed ore outline shown by the Tremor Focus. */
public final class TremorOreOutlines {
	private static final TagKey<Block> ORES =
		TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));
	private static final int OUTLINE_TICKS = 120;
	private static final int OUTLINE_COLOR = 0xCCB266FF;
	private static final float LINE_WIDTH = 3.0F;

	private static BlockPos orePos;
	private static ClientLevel highlightedLevel;
	private static long expiresAt;

	private TremorOreOutlines() {}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(TremorOreHintPayload.TYPE, (payload, context) ->
			context.client().execute(() -> highlight(payload.orePos())));
		LevelRenderEvents.END_MAIN.register(TremorOreOutlines::render);
	}

	private static void highlight(BlockPos pos) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return;
		}
		orePos = pos.immutable();
		highlightedLevel = minecraft.level;
		expiresAt = minecraft.level.getGameTime() + OUTLINE_TICKS;
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || orePos == null) {
			return;
		}
		if (minecraft.level != highlightedLevel) {
			clear();
			return;
		}
		if (minecraft.level.getGameTime() > expiresAt || !minecraft.level.getBlockState(orePos).is(ORES)) {
			clear();
			return;
		}

		Vec3 camera = context.gameRenderer().getMainCamera().position();
		double x = orePos.getX() - camera.x();
		double y = orePos.getY() - camera.y();
		double z = orePos.getZ() - camera.z();

		RenderType outline = TremorOreRenderTypes.oreOutline();
		ShapeRenderer.renderShape(
			context.poseStack(),
			context.bufferSource().getBuffer(outline),
			Shapes.block(),
			x, y, z,
			OUTLINE_COLOR,
			LINE_WIDTH);
		context.bufferSource().endBatch(outline);
	}

	private static void clear() {
		orePos = null;
		highlightedLevel = null;
		expiresAt = 0L;
	}
}
