package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Contract coverage for the "complete invisibility" client render mixins. Two Foci grant
 * invisibility (Veil sets the synced entity flag, Nullveil applies a particle-less
 * Invisibility effect), but vanilla still draws worn armor and held items on an invisible
 * entity. The mixins below cancel each equipment render layer's submit pass when the render
 * state reports the entity is invisible to the viewing player, so an invisible player shows
 * no gear at all. Render itself cannot be unit-tested without a client Bootstrap, so this is
 * a source-grep contract: it pins the targeted layer classes, the cancellable HEAD inject
 * shape, and the per-viewer invisibility gate.
 */
class InvisibilityEquipmentMixinContractTest {
	private static final Path MIXIN_DIR = Path.of("src/client/java/dev/attuned/client/mixin");
	private static final Path CLIENT_MIXINS =
		Path.of("src/client/resources/attuned.client.mixins.json");

	private static final Path ARMOR_MIXIN = MIXIN_DIR.resolve("HumanoidArmorLayerInvisibilityMixin.java");
	private static final Path ITEM_MIXIN = MIXIN_DIR.resolve("ItemInHandLayerInvisibilityMixin.java");
	private static final Path HEAD_MIXIN = MIXIN_DIR.resolve("CustomHeadLayerInvisibilityMixin.java");
	private static final Path WINGS_MIXIN = MIXIN_DIR.resolve("WingsLayerInvisibilityMixin.java");

	@Test
	void everyEquipmentInvisibilityMixinIsRegisteredOnTheClient() throws IOException {
		String config = read(CLIENT_MIXINS);
		assertTrue(config.contains("\"HumanoidArmorLayerInvisibilityMixin\""),
			"Worn-armor invisibility mixin should be registered in the client mixin config.");
		assertTrue(config.contains("\"ItemInHandLayerInvisibilityMixin\""),
			"Held-item invisibility mixin should be registered in the client mixin config.");
		assertTrue(config.contains("\"CustomHeadLayerInvisibilityMixin\""),
			"Worn-head invisibility mixin should be registered in the client mixin config.");
		assertTrue(config.contains("\"WingsLayerInvisibilityMixin\""),
			"Worn-elytra invisibility mixin should be registered in the client mixin config.");
	}

	@Test
	void wornArmorLayerIsCancelledForInvisibleEntities() throws IOException {
		assertEquipmentLayerMixin(
			ARMOR_MIXIN,
			"HumanoidArmorLayer.class",
			"net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer",
			"Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;");
	}

	@Test
	void heldItemLayerIsCancelledForInvisibleEntities() throws IOException {
		// PlayerItemInHandLayer extends ItemInHandLayer and only overrides submitArmWithItem,
		// so targeting the base layer's submit covers players, mobs, and armor stands alike.
		assertEquipmentLayerMixin(
			ITEM_MIXIN,
			"ItemInHandLayer.class",
			"net.minecraft.client.renderer.entity.layers.ItemInHandLayer",
			"Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;");
	}

	@Test
	void wornHeadLayerIsCancelledForInvisibleEntities() throws IOException {
		assertEquipmentLayerMixin(
			HEAD_MIXIN,
			"CustomHeadLayer.class",
			"net.minecraft.client.renderer.entity.layers.CustomHeadLayer",
			"Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;");
	}

	@Test
	void wornElytraLayerIsCancelledForInvisibleEntities() throws IOException {
		assertEquipmentLayerMixin(
			WINGS_MIXIN,
			"ElytraLayer.class",
			"net.minecraft.client.renderer.entity.layers.ElytraLayer",
			"Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;");
	}

	@Test
	void invisibilityGateUsesThePerViewerRenderStateFlagNotTheRawEntityFlag() throws IOException {
		// isInvisibleToPlayer = isInvisible && entity.isInvisibleTo(viewer), so teammates and
		// spectators still see the gear. Gating on the bare isInvisible flag would regress that.
		for (Path mixin : new Path[] {ARMOR_MIXIN, ITEM_MIXIN, HEAD_MIXIN, WINGS_MIXIN}) {
			String source = read(mixin);
			assertTrue(source.contains("state.isInvisibleToPlayer")
					|| source.contains("entity.isInvisibleTo(Minecraft.getInstance().player)"),
				mixin + " should gate on per-viewer invisibility.");
			assertTrue(!source.contains("state.isInvisible)") && !source.contains("state.isInvisible "),
				mixin + " should not gate on the raw isInvisible flag, which would hide gear from "
					+ "teammates and spectators.");
		}
	}

	private static void assertEquipmentLayerMixin(
			Path mixin, String mixinTarget, String layerImport, String stateDescriptor) throws IOException {
		String source = read(mixin);
		assertTrue(source.contains("@Mixin(" + mixinTarget + ")"),
			mixin + " should target " + mixinTarget + ".");
		assertTrue(source.contains("import " + layerImport + ";"),
			mixin + " should import the mapped layer class " + layerImport + ".");
		assertTrue(source.contains("at = @At(\"HEAD\")"),
			mixin + " should inject at HEAD so the equipment never starts rendering.");
		assertTrue(source.contains("cancellable = true"),
			mixin + " should declare a cancellable inject.");
		assertTrue(source.contains("ci.cancel()"),
			mixin + " should cancel the submit pass when the entity is invisible.");
		assertTrue(source.contains(stateDescriptor)
				|| source.contains("Lnet/minecraft/world/entity/LivingEntity;FFFFFF"),
			mixin + " should target the branch's equipment-render overload.");
		assertTrue(source.contains("\"submit(") || source.contains("\"render("),
			mixin + " should target the layer's equipment render method.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
