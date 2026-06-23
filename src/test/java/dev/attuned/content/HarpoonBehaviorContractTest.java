package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Source-level guardrails for the temporary Offshore Harpoon ability. */
class HarpoonBehaviorContractTest {
	private static final Path HARPOON_BEHAVIOR =
		Path.of("src/main/java/dev/attuned/content/behavior/HarpoonBehavior.java");
	private static final Path TRIDENT_MIXIN =
		Path.of("src/main/java/dev/attuned/mixin/ThrownTridentMixin.java");
	private static final Path MIXIN_CONFIG =
		Path.of("src/main/resources/attuned.mixins.json");
	private static final Path BEHAVIOR_REGISTRATION_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedFocusBehaviors.java");
	private static final Path HARPOON_FOCUS_DATA =
		Path.of("src/main/resources/data/attuned/attuned/focus/harpoon_focus.json");

	@Test
	void harpoonFocusRegistersAsOneActiveAbilityWithFixedTiming() throws IOException {
		String behavior = read(HARPOON_BEHAVIOR);
		String registrations = read(BEHAVIOR_REGISTRATION_SOURCE);
		String data = read(HARPOON_FOCUS_DATA);

		assertTrue(behavior.contains("public final class HarpoonBehavior implements FocusBehavior"),
			"Harpoon should be a focused FocusBehavior implementation");
		assertTrue(behavior.contains("static final int COOLDOWN_TICKS = 1200"),
			"Harpoon cooldown should be 60 seconds");
		assertTrue(behavior.contains("static final int LIFETIME_TICKS = COOLDOWN_TICKS"),
			"A summoned harpoon should live exactly one cooldown so it despawns when the ability is ready again");
		assertTrue(behavior.contains("now + LIFETIME_TICKS"),
			"Harpoon expiry and the active-harpoon gate should both follow the cooldown-bound lifetime");
		assertTrue(behavior.contains("public boolean hasActiveAbility()"),
			"Harpoon should opt into the single active ability slot");
		assertTrue(behavior.contains("public int abilityCooldownTicks()"),
			"Harpoon should expose cooldown to the HUD");
		assertTrue(behavior.contains("public boolean onAbility(ServerPlayer player, ItemStack focus)"),
			"Harpoon should spawn through the server-authoritative ability path");
		assertTrue(registrations.contains("register(\"harpoon\", new HarpoonBehavior())"),
			"AttunedFocusBehaviors should register the harpoon behavior");
		assertTrue(data.contains("\"behavior\": \"attuned:harpoon\""),
			"Harpoon Focus data should point at the behavior id");
	}

	@Test
	void temporaryHarpoonUsesVanillaTridentStackWithAttunedMarkerAndModel() throws IOException {
		String behavior = read(HARPOON_BEHAVIOR);

		assertTrue(behavior.contains("new ItemStack(Items.TRIDENT)"),
			"Temporary harpoon should be a vanilla trident stack");
		assertTrue(behavior.contains("DataComponents.CUSTOM_DATA"),
			"Temporary harpoon should carry an Attuned marker");
		assertTrue(behavior.contains("DataComponents.ITEM_MODEL"),
			"Temporary harpoon should point at the Attuned item model");
		assertTrue(behavior.contains("DataComponents.CUSTOM_NAME"),
			"Temporary harpoon should have custom display text");
		assertTrue(behavior.contains("DataComponents.INTANGIBLE_PROJECTILE"),
			"Thrown temporary harpoon should not become an ordinary pickup through vanilla creative/infinity paths");
		assertTrue(behavior.contains("player.setItemInHand(InteractionHand.MAIN_HAND, harpoon)"),
			"Successful summons should visibly put the temporary trident in the player's hand.");
		assertTrue(behavior.contains("inventory.setItem(freeSlot, player.getMainHandItem().copy())"),
			"If the hand is occupied, the previous held item should move into inventory instead of hiding the trident.");
		assertTrue(behavior.contains("MARKER_ID = \"attuned:offshore_harpoon\""),
			"Temporary harpoon marker id should be stable");
		assertTrue(behavior.contains("OWNER_KEY = \"owner\""),
			"Temporary harpoon should remember its owner");
		assertTrue(behavior.contains("EXPIRES_AT_KEY = \"expires_at\""),
			"Temporary harpoon should remember its expiry tick");
	}

	@Test
	void offshoreHarpoonShipsTemporaryItemAssets() throws IOException {
		Path focusDefinition = Path.of("src/main/resources/assets/attuned/items/harpoon_focus.json");
		Path itemDefinition = Path.of("src/main/resources/assets/attuned/items/ocean_relic_trident.json");
		Path projectileDefinition = Path.of("src/main/resources/assets/attuned/items/ocean_relic_trident_projectile.json");
		Path itemModel = Path.of("src/main/resources/assets/attuned/models/item/ocean_relic_trident.json");
		Path throwingModel = Path.of("src/main/resources/assets/attuned/models/item/ocean_relic_trident_throwing.json");
		Path blockbenchModel = Path.of("src/main/resources/assets/attuned/blockbench/ocean_relic_trident.bbmodel");
		Path gltfModel = Path.of("src/main/resources/assets/attuned/gltf/ocean_relic_trident.glb");
		Path blockbenchTexture = Path.of("src/main/resources/assets/attuned/textures/item/ocean_relic_trident_blockbench.png");
		Path blockbenchTextureMeta = Path.of("src/main/resources/assets/attuned/textures/item/ocean_relic_trident_blockbench.png.mcmeta");
		Path meshTexture = Path.of("src/main/resources/assets/attuned/textures/item/ocean_relic_trident_mesh.png");
		Path meshTextureMeta = Path.of("src/main/resources/assets/attuned/textures/item/ocean_relic_trident_mesh.png.mcmeta");
		Path itemTexture = Path.of("src/main/resources/assets/attuned/textures/item/ocean_relic_trident.png");
		Path itemPalette = Path.of("src/main/resources/assets/attuned/textures/item/ocean_relic_trident_voxel_palette.png");

		assertTrue(Files.isRegularFile(focusDefinition),
			"Harpoon Focus should keep its own item definition separate from the temporary harpoon");
		assertTrue(Files.isRegularFile(itemDefinition),
			"Temporary harpoon should have an item definition selected by DataComponents.ITEM_MODEL");
		assertTrue(Files.isRegularFile(itemModel),
			"Temporary harpoon should keep a base item model for display transforms");
		assertTrue(Files.isRegularFile(throwingModel),
			"Temporary harpoon should keep a base throwing/wind-up model for display transforms");
		assertTrue(Files.isRegularFile(blockbenchModel),
			"Temporary harpoon should keep the editable Blockbench source mesh");
		assertTrue(Files.isRegularFile(gltfModel),
			"Temporary harpoon should ship the compact GLB mesh used by the actual held/thrown harpoon");
		assertTrue(Files.isRegularFile(blockbenchTexture),
			"Temporary harpoon should ship the texture used by the actual held/thrown harpoon");
		assertTrue(Files.isRegularFile(blockbenchTextureMeta),
			"Temporary harpoon should clamp the texture used by the custom mesh renderer");
		assertTrue(Files.isRegularFile(meshTexture),
			"Temporary harpoon should ship a compact runtime mesh texture");
		assertTrue(Files.isRegularFile(meshTextureMeta),
			"Temporary harpoon runtime mesh texture should keep clamped custom-mesh sampling");
		assertTrue(Files.isRegularFile(itemTexture),
			"Temporary harpoon should keep a custom flat inventory texture");
		assertTrue(Files.isRegularFile(itemPalette),
			"Temporary harpoon should keep the old palette texture for source/preview compatibility");

		JsonObject focus = json(focusDefinition).getAsJsonObject("model");
		JsonObject definition = json(itemDefinition);
		JsonObject model = json(itemModel);
		JsonObject projectile = json(projectileDefinition).getAsJsonObject("model");
		JsonObject blockbench = json(blockbenchModel);
		assertEquals("minecraft:model", focus.get("type").getAsString(),
			"Harpoon Focus itself should stay on its normal focus item model");
		assertEquals("attuned:item/harpoon_focus", focus.get("model").getAsString(),
			"Harpoon Focus should not be replaced by the trident GLB mesh");
		JsonObject definitionModel = definition.getAsJsonObject("model");
		assertEquals("minecraft:select", definitionModel.get("type").getAsString(),
			"Item definition should split GUI/held contexts like a vanilla trident");
		assertEquals("minecraft:display_context", definitionModel.get("property").getAsString(),
			"Item definition should preserve inventory rendering while allowing held state switching");
		JsonObject fallback = definitionModel.getAsJsonObject("fallback");
		assertEquals("minecraft:condition", fallback.get("type").getAsString(),
			"Held temporary trident should switch models while the player is using it");
		assertEquals("minecraft:using_item", fallback.get("property").getAsString(),
			"Throw wind-up should use the actual vanilla item-use state");
		assertGltfSpecial(fallback.getAsJsonObject("on_false"), "attuned:item/ocean_relic_trident");
		assertGltfSpecial(fallback.getAsJsonObject("on_true"), "attuned:item/ocean_relic_trident_throwing");
		assertGltfSpecial(projectile, "attuned:item/ocean_relic_trident_throwing");
		assertTrue(model.has("display"),
			"Harpoon base model should define held/inventory transforms for the glTF special renderer");
		JsonObject display = model.getAsJsonObject("display");
		assertTrue(display.has("gui"),
			"Harpoon base model should define an inventory transform");
		assertTrue(display.has("firstperson_righthand"),
			"Harpoon base model should define a first-person right-hand transform");
		assertTrue(display.has("thirdperson_righthand"),
			"Harpoon base model should define a third-person right-hand transform");
		assertEquals("free", blockbench.getAsJsonObject("meta").get("model_format").getAsString(),
			"Actual temporary harpoon mesh should stay in Blockbench free-model format");
		JsonObject mesh = blockbench.getAsJsonArray("elements").get(0).getAsJsonObject();
		assertEquals("mesh", mesh.get("type").getAsString(),
			"Editable source should stay a real mesh, not a wrapped cuboid approximation");
		assertTrue(mesh.getAsJsonObject("vertices").size() >= 9000,
			"Blockbench harpoon mesh should preserve the detailed model vertices");
		assertTrue(mesh.getAsJsonObject("faces").size() >= 6000,
			"Blockbench harpoon mesh should preserve the detailed model faces");
		for (JsonElement texture : blockbench.getAsJsonArray("textures")) {
			assertFalse(texture.getAsJsonObject().has("source"),
				"Shipped Blockbench model should not embed the massive base64 texture source");
		}

		BufferedImage image = ImageIO.read(itemTexture.toFile());
		assertNotNull(image, "Harpoon texture should decode as a PNG");
		assertEquals(64, image.getWidth(), "Harpoon texture should be 64 pixels wide");
		assertEquals(64, image.getHeight(), "Harpoon texture should be 64 pixels tall");
		assertTrue(hasVisiblePixels(image), "Harpoon texture should have non-transparent pixels");
		assertTransparentCorners(image, "Harpoon texture should be isolated on transparency");
		assertNoVisibleChromaKey(image, "Harpoon texture should not keep visible chroma-key pixels");
		BufferedImage blockbenchImage = ImageIO.read(blockbenchTexture.toFile());
		assertNotNull(blockbenchImage, "Blockbench harpoon texture should decode as a PNG");
		assertEquals(1024, blockbenchImage.getWidth(),
			"Blockbench harpoon texture should be downsampled to a game-scale atlas size");
		assertEquals(1024, blockbenchImage.getHeight(),
			"Blockbench harpoon texture should be downsampled to a game-scale atlas size");
		assertTrue((blockbenchImage.getRGB(0, 0) & 0x00FFFFFF) != 0,
			"Blockbench harpoon texture should pad unused UV space so atlas filtering does not bleed black into the mesh");
		BufferedImage meshImage = ImageIO.read(meshTexture.toFile());
		assertNotNull(meshImage, "Runtime mesh texture should decode as a PNG");
		assertEquals(512, meshImage.getWidth(),
			"Runtime mesh texture should stay compact for released jars");
		assertEquals(512, meshImage.getHeight(),
			"Runtime mesh texture should stay compact for released jars");
		assertTrue((meshImage.getRGB(0, 0) & 0x00FFFFFF) != 0,
			"Runtime mesh texture should preserve padded UV space for atlas filtering");
	}

	@Test
	void temporaryHarpoonCleansInventoryDroppedItemsAndProjectiles() throws IOException {
		String behavior = read(HARPOON_BEHAVIOR);
		String mixin = read(TRIDENT_MIXIN);
		String mixinConfig = read(MIXIN_CONFIG);

		assertTrue(behavior.contains("ServerTickEvents.END_SERVER_TICK.register"),
			"Harpoon behavior should scan transient harpoons on server ticks");
		assertTrue(behavior.contains("private static final int ENTITY_CLEANUP_INTERVAL_TICKS = 20"),
			"Broad entity cleanup should run on a bounded one-second cadence");
		assertTrue(behavior.contains("private static boolean shouldSweepEntities(long now)"),
			"Broad entity cleanup cadence should be isolated in a named helper");
		assertTrue(behavior.contains("return now % ENTITY_CLEANUP_INTERVAL_TICKS == 0L;"),
			"Broad entity cleanup should not scan every tick while a harpoon is active");
		String tickServer = methodBody(behavior, "private static void tickServer(MinecraftServer server)");
		assertBefore(tickServer, "if (ACTIVE_HARPOONS.isEmpty())", "cleanupActiveInventories(server, now);");
		assertBefore(tickServer, "cleanupActiveInventories(server, now);", "pruneActiveHarpoons(now);");
		assertBefore(tickServer, "cleanupEntities(server, now);", "pruneActiveHarpoons(now);");
		assertTrue(behavior.contains("if (ACTIVE_HARPOONS.isEmpty())"),
			"Global harpoon cleanup should skip player/entity scans when no temporary harpoon is active");
		assertTrue(behavior.contains("cleanupActiveInventories(server, now);"),
			"Global harpoon cleanup should scan active owners instead of every online player");
		assertTrue(!behavior.contains("for (ServerPlayer player : server.getPlayerList().getPlayers()) {\n\t\t\tremoveInvalidInventoryHarpoons(player, now);"),
			"Global harpoon cleanup should not scan every online player inventory every server tick");
		assertTrue(behavior.contains("cleanupEntities(server, now);"),
			"Tick cleanup should still scan entities when the broad-scan gate allows it");
		assertTrue(behavior.contains("cleanupTransferredInventories(server, now);"),
			"Broad tick cleanup should sweep online inventories for temporary harpoons that changed hands");
		assertBefore(tickServer, "cleanupTransferredInventories(server, now);", "cleanupEntities(server, now);");
		String transferredInventoryCleanup =
			methodBody(behavior, "private static void cleanupTransferredInventories(MinecraftServer server, long now)");
		assertTrue(transferredInventoryCleanup.contains("for (ServerPlayer player : server.getPlayerList().getPlayers())"),
			"Transferred harpoon cleanup should inspect online player inventories on the bounded broad-scan cadence");
		assertTrue(transferredInventoryCleanup.contains("removeInventoryHarpoons(player, player.getUUID(), now, false)"),
			"Transferred harpoon cleanup should remove expired own harpoons and still-active foreign harpoons");
		assertTrue(behavior.contains(
				"AttunedServerCleanup.onStopServer(HarpoonBehavior::removeAllTemporaryHarpoons)"),
			"Server stop cleanup should use the central coordinator while still receiving the stopping server");
		assertTrue(!behavior.contains("ServerLifecycleEvents.SERVER_STOPPED.register"),
			"Harpoon behavior should not own a raw server-stop hook");
		assertTrue(behavior.contains("private static void removeAllTemporaryHarpoons(MinecraftServer server)"),
			"Server stop cleanup should use a named full-sweep helper");
		assertTrue(behavior.contains("for (ServerPlayer player : server.getPlayerList().getPlayers())"),
			"Server stop cleanup should inspect online player inventories");
		assertTrue(behavior.contains("removeInventoryHarpoons(player, player.getUUID(), Long.MAX_VALUE, true)"),
			"Server stop cleanup should force-remove marked inventory harpoons");
		String inventoryCleanup = methodBody(behavior, "private static void removeInventoryHarpoons(");
		assertTrue(inventoryCleanup.contains("removeMarkedStack(player.getOffhandItem(), owner, now, force, true)"),
			"Inventory cleanup should remove expired/deactivated temporary harpoons after players move them to offhand");
		String ownerCleanup = methodBody(behavior, "private static void removeForPlayer(ServerPlayer player)");
		assertTrue(ownerCleanup.contains("removePlayerInventoriesForOwner(level.getServer(), owner)"),
			"Owner cleanup should remove that player's temporary harpoons even after another online player picks them up");
		String ownerInventoryCleanup =
			methodBody(behavior, "private static void removePlayerInventoriesForOwner(MinecraftServer server, UUID owner)");
		assertTrue(ownerInventoryCleanup.contains("for (ServerPlayer player : server.getPlayerList().getPlayers())"),
			"Owner cleanup should inspect online player inventories for transferred owner-marked stacks");
		assertTrue(ownerInventoryCleanup.contains("removeInventoryHarpoonsForOwner(player, owner, Long.MAX_VALUE, true)"),
			"Owner cleanup should force-remove only stacks that belong to the deactivating/disconnecting owner");
		String ownedInventoryCleanup =
			methodBody(behavior, "private static void removeInventoryHarpoonsForOwner(");
		assertTrue(ownedInventoryCleanup.contains("removeMarkedStack(player.getOffhandItem(), owner, now, force, false)"),
			"Owner-specific cleanup should not remove unrelated players' own temporary harpoons");
		assertTrue(behavior.contains("AttunedPlayerCleanup.onForgetPlayer"),
			"Harpoon behavior should remove the item when a player disconnects");
		assertTrue(behavior.contains("server.getAllLevels()"),
			"Cleanup should inspect every loaded server level");
		assertTrue(behavior.contains("level.getAllEntities()"),
			"Cleanup should scan loaded entity instances");
		assertTrue(behavior.contains("entity instanceof ItemEntity"),
			"Cleanup should remove dropped marked harpoon items");
		assertTrue(behavior.contains("entity instanceof ThrownTrident"),
			"Cleanup should remove thrown marked harpoons");
		assertTrue(behavior.contains("private static boolean isOwnedTemporaryHarpoon(ItemStack stack, UUID owner)"),
			"Owner cleanup should use a marker-aware ownership helper");
		assertTrue(behavior.contains("CompoundTag tag = temporaryHarpoonTag(stack);"),
			"Owner cleanup should read and validate the marker data once");
		assertTrue(behavior.contains("return tag != null && owner.equals(ownerOf(tag));"),
			"Owner cleanup should require the Attuned marker before consulting the owner key");
		assertTrue(behavior.contains("entity instanceof ItemEntity item && isOwnedTemporaryHarpoon(item.getItem(), owner)"),
			"Owner cleanup should not discard unmarked dropped items just because they have an owner key");
		assertTrue(behavior.contains(
				"&& isOwnedTemporaryHarpoon(trident.getPickupItemStackOrigin(), owner)"),
			"Owner cleanup should not discard unmarked thrown tridents just because they have an owner key");
		assertTrue(mixinConfig.contains("\"ThrownTridentMixin\""),
			"Common mixin config should install the thrown trident guard");
		assertTrue(mixin.contains("@Mixin(ThrownTrident.class)"),
			"Mixin should target vanilla thrown tridents");
		assertFalse(mixin.contains("@Shadow"),
			"ThrownTrident mixin should not shadow inherited AbstractArrow methods");
		assertTrue(mixin.contains("import net.minecraft.world.entity.projectile.arrow.AbstractArrow;"),
			"Mixin should access inherited pickup state through AbstractArrow");
		assertTrue(mixin.contains("private ItemStack attuned$pickupStack()"),
			"Mixin should centralize pickup stack access in a helper");
		assertTrue(mixin.contains("return ((AbstractArrow) (Object) this).getPickupItemStackOrigin();"),
			"Mixin helper should call the inherited AbstractArrow pickup accessor");
		assertTrue(mixin.contains("method = \"tick\""),
			"Mixin should discard expired projectiles before vanilla tick work");
		assertTrue(mixin.contains("method = \"tryPickup\""),
			"Mixin should intercept pickup attempts");
		assertTrue(mixin.contains("cir.setReturnValue(false)"),
			"A temporary harpoon should never be collectable, so pickup always returns false");
		assertFalse(mixin.contains("onHitEntity") || mixin.contains("hitBlockEnchantmentEffects"),
			"A thrown harpoon should stay stuck where it lands, not be discarded on impact");
		assertTrue(mixin.contains("ATTUNED_TEMPORARY_HARPOON") && mixin.contains("method = \"defineSynchedData\""),
			"Mixin should sync a temporary-harpoon flag to clients (mirroring vanilla ID_FOIL) so the renderer can swap in the custom mesh");
		assertTrue(mixin.contains("attuned$isTemporaryHarpoon()"),
			"Mixin should expose the synced harpoon flag to the client renderer");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}

	private static JsonObject json(Path file) throws IOException {
		return JsonParser.parseString(read(file)).getAsJsonObject();
	}

	private static void assertGltfSpecial(JsonObject model, String base) {
		assertEquals("minecraft:special", model.get("type").getAsString(),
			"Actual temporary harpoon render path should use a special glTF renderer");
		assertEquals(base, model.get("base").getAsString(),
			"Special renderer should keep the expected base model for transforms");
		JsonObject special = model.getAsJsonObject("model");
		assertEquals("attuned:gltf_mesh", special.get("type").getAsString(),
			"Special renderer should be Attuned's glTF mesh renderer");
		assertEquals("attuned:gltf/ocean_relic_trident.glb", special.get("model").getAsString(),
			"Special renderer should load the compact GLB model");
		assertEquals("attuned:textures/item/ocean_relic_trident_mesh.png", special.get("texture").getAsString(),
			"Special renderer should use the exported game-scale texture");
	}

	private static boolean hasVisiblePixels(BufferedImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if (((image.getRGB(x, y) >>> 24) & 0xFF) > 0) {
					return true;
				}
			}
		}
		return false;
	}

	private static void assertTransparentCorners(BufferedImage image, String message) {
		assertEquals(0, alpha(image, 0, 0), message);
		assertEquals(0, alpha(image, image.getWidth() - 1, 0), message);
		assertEquals(0, alpha(image, 0, image.getHeight() - 1), message);
		assertEquals(0, alpha(image, image.getWidth() - 1, image.getHeight() - 1), message);
	}

	private static void assertNoVisibleChromaKey(BufferedImage image, String message) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getRGB(x, y);
				int alpha = (argb >>> 24) & 0xFF;
				int red = (argb >>> 16) & 0xFF;
				int green = (argb >>> 8) & 0xFF;
				int blue = argb & 0xFF;
				assertTrue(alpha <= 16 || green <= 150 || red >= 90 || blue >= 120, message);
			}
		}
	}

	private static int alpha(BufferedImage image, int x, int y) {
		return (image.getRGB(x, y) >>> 24) & 0xFF;
	}

	private static void assertBefore(String source, String earlier, String later) {
		int earlierIndex = source.indexOf(earlier);
		int laterIndex = source.indexOf(later);
		assertTrue(earlierIndex >= 0, "Expected source to contain: " + earlier);
		assertTrue(laterIndex >= 0, "Expected source to contain: " + later);
		assertTrue(earlierIndex < laterIndex, "Expected " + earlier + " before " + later);
	}

	private static String methodBody(String source, String signaturePrefix) {
		int signatureStart = source.indexOf(signaturePrefix);
		assertTrue(signatureStart >= 0, "Expected method signature: " + signaturePrefix);
		int bodyStart = source.indexOf('{', signatureStart);
		assertTrue(bodyStart >= 0, "Expected method body: " + signaturePrefix);
		int depth = 0;
		for (int index = bodyStart; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '{') {
				depth++;
			} else if (current == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(bodyStart, index + 1);
				}
			}
		}
		throw new AssertionError("Unterminated method body: " + signaturePrefix);
	}
}
