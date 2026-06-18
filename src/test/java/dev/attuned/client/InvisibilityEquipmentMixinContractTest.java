package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Contract coverage for the "complete invisibility" client render mixins. Two Foci grant
 * invisibility (Veil sets the synced entity flag, Nullveil applies a particle-less
 * Invisibility effect), but vanilla still draws worn armor and held items on an invisible
 * entity. The mixins below cancel each equipment render layer's render pass when the entity
 * is invisible to the viewing player, so an invisible player shows no gear at all.
 *
 * <p>Minecraft 1.20.1 predates the render-state API used by 1.21+, so these mixins target
 * the legacy {@code render(PoseStack, MultiBufferSource, int, LivingEntity, ...)} overload
 * and gate on {@link net.minecraft.world.entity.LivingEntity#isInvisibleTo}.</p>
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
		assumeInvisibilityMixinsEnabled();
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
		assumeInvisibilityMixinsEnabled();
		assertEquipmentLayerMixin(
			ARMOR_MIXIN,
			"HumanoidArmorLayer.class",
			"net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer",
			"Lnet/minecraft/world/entity/LivingEntity;");
	}

	@Test
	void heldItemLayerIsCancelledForInvisibleEntities() throws IOException {
		assumeInvisibilityMixinsEnabled();
		assertEquipmentLayerMixin(
			ITEM_MIXIN,
			"ItemInHandLayer.class",
			"net.minecraft.client.renderer.entity.layers.ItemInHandLayer",
			"Lnet/minecraft/world/entity/LivingEntity;");
	}

	@Test
	void wornHeadLayerIsCancelledForInvisibleEntities() throws IOException {
		assumeInvisibilityMixinsEnabled();
		assertEquipmentLayerMixin(
			HEAD_MIXIN,
			"CustomHeadLayer.class",
			"net.minecraft.client.renderer.entity.layers.CustomHeadLayer",
			"Lnet/minecraft/world/entity/LivingEntity;");
	}

	@Test
	void wornElytraLayerIsCancelledForInvisibleEntities() throws IOException {
		assumeInvisibilityMixinsEnabled();
		assertEquipmentLayerMixin(
			WINGS_MIXIN,
			"ElytraLayer.class",
			"net.minecraft.client.renderer.entity.layers.ElytraLayer",
			"Lnet/minecraft/world/entity/LivingEntity;");
	}

	@Test
	void invisibilityGateUsesThePerViewerEntityFlagNotTheRawEntityFlag() throws IOException {
		assumeInvisibilityMixinsEnabled();
		for (Path mixin : new Path[] {ARMOR_MIXIN, ITEM_MIXIN, HEAD_MIXIN, WINGS_MIXIN}) {
			String source = read(mixin);
			assertTrue(source.contains("isInvisibleTo"),
				mixin + " should gate on the per-viewer isInvisibleTo check.");
			assertTrue(!source.contains("entity.isInvisible())") && !source.contains("entity.isInvisible() "),
				mixin + " should not gate on the raw isInvisible flag, which would hide gear from "
					+ "teammates and spectators.");
		}
	}

	private static void assertEquipmentLayerMixin(
			Path mixin, String mixinTarget, String layerImport, String entityDescriptor) throws IOException {
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
			mixin + " should cancel the render pass when the entity is invisible.");
		assertTrue(source.contains(entityDescriptor),
			mixin + " should target the render overload taking " + entityDescriptor + ".");
		assertTrue(source.contains("MultiBufferSource"),
			mixin + " should target the legacy render method using MultiBufferSource.");
	}

	private static void assumeInvisibilityMixinsEnabled() throws IOException {
		assumeTrue(read(CLIENT_MIXINS).contains("HumanoidArmorLayerInvisibilityMixin"),
			"Invisibility equipment mixins are not enabled for this maintenance target.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
