package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract coverage for Tremor's ore-outline reveal. */
class TremorFocusOutlineContractTest {
	private static final Path TREMOR_BEHAVIOR =
		Path.of("src/main/java/dev/attuned/content/behavior/TremorBehavior.java");
	private static final Path TREMOR_PAYLOAD =
		Path.of("src/main/java/dev/attuned/network/TremorOreHintPayload.java");
	private static final Path JOURNAL_NETWORKING =
		Path.of("src/main/java/dev/attuned/network/JournalNetworking.java");
	private static final Path TREMOR_CLIENT =
		Path.of("src/client/java/dev/attuned/client/TremorOreOutlines.java");
	private static final Path TREMOR_RENDER_TYPES =
		Path.of("src/client/java/dev/attuned/client/TremorOreRenderTypes.java");
	private static final Path CLIENT_INIT =
		Path.of("src/client/java/dev/attuned/client/AttunedClient.java");
	private static final Path CLIENT_MIXINS =
		Path.of("src/client/resources/attuned.client.mixins.json");
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void tremorSendsTheActualOrePositionInsteadOfOnlyAParticlePing() throws IOException {
		String source = read(TREMOR_BEHAVIOR);

		assertTrue(source.contains("nearestOre(server, pos)"),
			"Tremor should select the nearest matching ore block.");
		assertTrue(source.contains("collectVein(server, orePos)"),
			"Tremor should expand the nearest ore into its whole connected vein.");
		assertTrue(source.contains("ServerPlayNetworking.send(serverPlayer, new TremorOreHintPayload(vein))"),
			"Tremor should send every vein position to the mining player.");
		assertTrue(source.contains("MAX_VEIN_BLOCKS"),
			"The vein flood fill must be capped so a giant deposit cannot flood the packet.");
		assertTrue(source.contains("path.startsWith(\"deepslate_\")"),
			"Stone and deepslate variants of one ore should outline as a single vein.");
		assertTrue(source.contains("Blocks.BLACKSTONE") && source.contains("Blocks.BASALT"),
			"Ancient-debris tunnels run through blackstone and basalt, so those count as stone-like.");
		assertTrue(!source.contains("private static boolean nearOre"),
			"Tremor should not stop at a boolean near-ore hint.");
	}

	@Test
	void oreHintPayloadIsRegisteredClientbound() throws IOException {
		String payload = read(TREMOR_PAYLOAD);
		String networking = read(JOURNAL_NETWORKING);

		assertTrue(payload.contains("record TremorOreHintPayload(List<BlockPos> orePositions)"),
			"The clientbound payload should carry every vein block position.");
		assertTrue(payload.contains("BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list())"),
			"The payload should use the vanilla BlockPos stream codec in list form.");
		assertTrue(networking.contains("PayloadTypeRegistry.clientboundPlay().register(TremorOreHintPayload.TYPE"),
			"Common networking should register Tremor's clientbound payload type.");
	}

	@Test
	void clientRendersATimedWorldOutlineAroundTheOreBlock() throws IOException {
		String source = read(TREMOR_CLIENT);
		String renderTypes = read(TREMOR_RENDER_TYPES);
		String init = read(CLIENT_INIT);
		String mixins = read(CLIENT_MIXINS);

		assertTrue(source.contains("ClientPlayNetworking.registerGlobalReceiver(TremorOreHintPayload.TYPE"),
			"The client should receive Tremor ore hints.");
		assertTrue(source.contains("LevelRenderEvents.END_MAIN.register"),
			"The outline should render in the world render pass.");
		assertTrue(source.contains("ShapeRenderer.renderShape"),
			"The outline should be drawn around the ore block bounds.");
		assertTrue(source.contains("TremorOreRenderTypes.oreOutline()"),
			"Tremor should use its custom ore-outline render type.");
		assertTrue(renderTypes.contains("RenderPipelines.LINES_TRANSLUCENT"),
			"Tremor's outline should keep vanilla line rendering behavior.");
		assertTrue(renderTypes.contains("new DepthStencilState(CompareOp.ALWAYS_PASS, false)"),
			"Tremor's outline should render through surrounding stone without writing depth.");
		assertTrue(renderTypes.contains("RenderTypeInvoker.attuned$create"),
			"The custom render type should be created through the client mixin invoker.");
		assertTrue(mixins.contains("\"RenderTypeInvoker\""),
			"The RenderType invoker mixin should be registered on the client.");
		assertTrue(source.contains("context.gameRenderer().getMainCamera().position()"),
			"The outline should be rendered relative to the active camera.");
		assertTrue(source.contains("private static ClientLevel highlightedLevel"),
			"Tremor should remember the client level that owns the current outline.");
		assertTrue(source.contains("minecraft.level != highlightedLevel"),
			"Tremor should clear stale outline state when the client changes level or world.");
		assertTrue(source.contains("private static void clear()"),
			"Tremor should centralize clearing ore position, level, and expiry state.");
		assertTrue(init.contains("TremorOreOutlines.init()"),
			"The client initializer should install Tremor's receiver and renderer.");
	}

	@Test
	void tremorTooltipPromisesTheOreOutline() throws IOException {
		String lang = read(LANG_FILE);

		assertTrue(lang.contains("\"item.attuned.tremor_focus.effect\": \"Mining stone outlines the nearest ore vein for a short time.\""),
			"Tremor's tooltip should describe the actual ore-vein outline reveal.");
	}

	private static String read(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), "Expected file to exist: " + path);
		return Files.readString(path, StandardCharsets.UTF_8);
	}
}
