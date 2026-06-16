# Offshore Harpoon Implementation Plan

> **Implementation:** Work tasks in order using the checkbox (`- [ ]`) syntax below.

**Goal:** Add The Offshore faction and Harpoon Focus, whose Focus Ability summons a temporary custom-model trident that cannot be crafted or made permanent.

**Architecture:** Ship `harpoon_focus` as a normal Attuned Focus item and use a marked vanilla `Items.TRIDENT` stack for the temporary Offshore Harpoon. The marker lives in `DataComponents.CUSTOM_DATA`, the held/inventory model is selected through `DataComponents.ITEM_MODEL`, and server cleanup plus a `ThrownTrident` mixin prevent dropped or thrown harpoons from surviving after their timer.

**Tech Stack:** Fabric 26.1.2, Java 25, Minecraft data components, Fabric lifecycle events, Sponge Mixin, JUnit 5 source/asset contract tests, Attuned Focus datapack definitions.

---

## File Structure

- Modify `src/main/java/dev/attuned/content/AttunedContent.java`: register `HARPOON_FOCUS`, add it to `FOCI`, and register the `attuned:harpoon` behavior once the behavior task lands.
- Create `src/main/java/dev/attuned/content/behavior/HarpoonBehavior.java`: owns duration/cooldown, creates marked temporary tridents, removes stale harpoons, and exposes static marker helpers used by mixins.
- Create `src/main/java/dev/attuned/mixin/ThrownTridentMixin.java`: discards marked thrown harpoons when they expire or hit something, and blocks expired pickup.
- Modify `src/main/resources/attuned.mixins.json`: add the common-side trident mixin.
- Create `src/main/resources/data/attuned/attuned/focus/harpoon_focus.json`: neutral unique Offshore Focus definition.
- Create `src/main/resources/assets/attuned/items/harpoon_focus.json` and `src/main/resources/assets/attuned/models/item/harpoon_focus.json`: normal Focus item definition/model.
- Create `src/main/resources/assets/attuned/textures/item/harpoon_focus.png` and `.png.mcmeta`: 8-frame animated Focus icon.
- Create `src/main/resources/assets/attuned/items/offshore_harpoon.json`, `src/main/resources/assets/attuned/models/item/offshore_harpoon.json`, and `src/main/resources/assets/attuned/textures/item/offshore_harpoon.png`: custom 3D temporary trident presentation.
- Modify `src/main/resources/assets/attuned/lang/en_us.json`: Offshore faction, Harpoon Focus, temporary harpoon name, lore/effect, and journal page.
- Modify `src/main/java/dev/attuned/content/AttunementJournalItem.java`: add the Offshore page to written-book fallback.
- Modify `src/client/java/dev/attuned/client/screen/AttunementJournalScreen.java`: add an Offshore chapter/page in the custom codex UI.
- Modify `docs/reference.md`: document `attuned:offshore` and `attuned:harpoon`.
- Modify `src/test/java/dev/attuned/content/FocusDataConsistencyTest.java`: add Offshore contract coverage.
- Create `src/test/java/dev/attuned/content/HarpoonBehaviorContractTest.java`: source/asset contract coverage for temporary harpoon behavior.
- Modify `src/test/java/dev/attuned/content/AttunementJournalUiContractTest.java`: require the Offshore journal page in both journal paths.

---

### Task 1: Ship The Offshore as a Neutral Focus Faction

**Files:**
- Modify: `src/test/java/dev/attuned/content/FocusDataConsistencyTest.java`
- Modify: `src/main/java/dev/attuned/content/AttunedContent.java`
- Create: `src/main/resources/data/attuned/attuned/focus/harpoon_focus.json`
- Create: `src/main/resources/assets/attuned/items/harpoon_focus.json`
- Create: `src/main/resources/assets/attuned/models/item/harpoon_focus.json`
- Create: `src/main/resources/assets/attuned/textures/item/harpoon_focus.png`
- Create: `src/main/resources/assets/attuned/textures/item/harpoon_focus.png.mcmeta`
- Modify: `src/main/resources/assets/attuned/lang/en_us.json`

- [ ] **Step 1: Write the failing Offshore content contract**

In `FocusDataConsistencyTest.java`, add this constant near the other faction sets:

```java
private static final Set<String> OFFSHORE_FOCUS_ITEMS = Set.of(
	"attuned:harpoon_focus");
```

Add this test method after `seafarersFociStayNeutralTranslatedAndGrantOnlyLuckUtility()`:

```java
@Test
void offshoreHarpoonFocusStaysNeutralTranslatedAndTemporaryOnly() throws IOException {
	Set<String> offshoreItems = new TreeSet<>();
	try (Stream<Path> paths = Files.list(FOCUS_DATA_DIR)) {
		for (Path file : paths
				.filter(path -> path.getFileName().toString().endsWith(".json"))
				.sorted()
				.toList()) {
			JsonObject root = focusDefinitionRoot(file);
			JsonElement faction = root.get("faction");
			if (faction == null || !"attuned:offshore".equals(faction.getAsString())) {
				continue;
			}
			String itemId = root.get("item").getAsString();
			offshoreItems.add(itemId);
			assertTrue(!root.has("affinity"), "Offshore Foci must stay neutral: " + file);
			assertTrue(root.has("unique") && root.get("unique").getAsBoolean(),
				"Offshore Harpoon should be unique while active: " + file);
		}
	}

	JsonObject lang = languageRoot();
	assertEquals(OFFSHORE_FOCUS_ITEMS, offshoreItems,
		"The first Offshore release should ship only Harpoon Focus");
	assertLanguageKey(lang, "faction.attuned.offshore");
	assertLanguageKey(lang, "item.attuned.offshore_harpoon");

	String source = Files.readString(CONTENT_SOURCE, StandardCharsets.UTF_8);
	assertTrue(!source.contains("OFFSHORE_HARPOON"),
		"Offshore Harpoon should not be registered as a permanent craftable item");
	assertTrue(!Files.isRegularFile(Path.of("src/main/resources/data/attuned/recipe/offshore_harpoon.json")),
		"Offshore Harpoon should not have a recipe");
}
```

- [ ] **Step 2: Run the focused contract and confirm it fails**

Run:

```powershell
.\gradlew.bat test --no-daemon --tests dev.attuned.content.FocusDataConsistencyTest
```

Expected: FAIL with missing `attuned:offshore` content, missing `harpoon_focus` assets, or missing language keys.

- [ ] **Step 3: Register the Focus item**

Add the new Focus field immediately after the Seafarers fields:

```java
// The Offshore - dangerous water utility for salvage, storms, and things below the waves.
public static final Item HARPOON_FOCUS = register("harpoon_focus");
```

Add `HARPOON_FOCUS` to the `FOCI` list immediately after `DRIFTGLASS_FOCUS`:

```java
LINECAST_FOCUS, NETMENDER_FOCUS, HARBORLIGHT_FOCUS, DRIFTGLASS_FOCUS, HARPOON_FOCUS,
```

Do not register `OFFSHORE_HARPOON` as an `Item`. The temporary harpoon is created as a marked `Items.TRIDENT` stack in Task 2.

- [ ] **Step 4: Add Focus definition data**

Create `src/main/resources/data/attuned/attuned/focus/harpoon_focus.json`:

```json
{
	"item": "attuned:harpoon_focus",
	"cost": 3,
	"unique": true,
	"faction": "attuned:offshore"
}
```

The `behavior` field is intentionally added in Task 2 with the behavior implementation and tests.

- [ ] **Step 5: Add item definition and model**

Create `src/main/resources/assets/attuned/items/harpoon_focus.json`:

```json
{
	"model": {
		"type": "minecraft:model",
		"model": "attuned:item/harpoon_focus"
	}
}
```

Create `src/main/resources/assets/attuned/models/item/harpoon_focus.json`:

```json
{
	"parent": "minecraft:item/generated",
	"textures": {
		"layer0": "attuned:item/harpoon_focus"
	}
}
```

- [ ] **Step 6: Generate the animated Focus texture**

Run this PowerShell script from the repo root to create a readable 8-frame 64x512 Focus sprite:

```powershell
Add-Type -AssemblyName System.Drawing
$out = "src/main/resources/assets/attuned/textures/item/harpoon_focus.png"
$bmp = New-Object System.Drawing.Bitmap 64,512
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::Transparent)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
for ($frame = 0; $frame -lt 8; $frame++) {
  $y = $frame * 64
  $glow = 60 + $frame * 14
  $teal = [System.Drawing.Color]::FromArgb(255, [Math]::Min(110 + $frame * 8, 180), 220, 214)
  $copper = [System.Drawing.Color]::FromArgb(255, 178, 95 + $frame * 4, 46)
  $dark = [System.Drawing.Color]::FromArgb(255, 20, 25, 33)
  $violet = [System.Drawing.Color]::FromArgb(255, 104, 72, [Math]::Min(170 + $frame * 8, 240))
  $ring = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 42, 62, 72)), 3
  $glowPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb($glow, 86, 228, 216)), 2
  $g.FillEllipse((New-Object System.Drawing.SolidBrush $dark), 8, ($y + 8), 48, 48)
  $g.DrawEllipse($ring, 8, ($y + 8), 48, 48)
  $g.DrawEllipse($glowPen, 12, ($y + 12), 40, 40)
  $g.FillRectangle((New-Object System.Drawing.SolidBrush $copper), 30, ($y + 17), 4, 34)
  $g.FillRectangle((New-Object System.Drawing.SolidBrush $teal), 23, ($y + 13), 5, 13)
  $g.FillRectangle((New-Object System.Drawing.SolidBrush $teal), 31, ($y + 10), 4, 17)
  $g.FillRectangle((New-Object System.Drawing.SolidBrush $teal), 38, ($y + 13), 5, 13)
  $g.FillRectangle((New-Object System.Drawing.SolidBrush $violet), 27, ($y + 35), 10, 8)
  $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 235, 255, 252))), 31, ($y + 12), 2, 8)
}
$g.Dispose()
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
```

Create `src/main/resources/assets/attuned/textures/item/harpoon_focus.png.mcmeta`:

```json
{
	"animation": {
		"frametime": 2,
		"interpolate": true
	}
}
```

- [ ] **Step 7: Add translations**

Insert these keys in `src/main/resources/assets/attuned/lang/en_us.json` near the other faction and item keys:

```json
"item.attuned.harpoon_focus": "Harpoon Focus",
"item.attuned.offshore_harpoon": "Offshore Harpoon",
"faction.attuned.offshore": "The Offshore",
```

Insert these lore/effect keys near the Seafarers/utility Focus lore:

```json
"item.attuned.harpoon_focus.lore": "After the harbor lights fail, the deep still keeps tools.",
"item.attuned.harpoon_focus.lore2": "Storm maps, wreck rope, and a barb that remembers return.",
"item.attuned.harpoon_focus.effect": "Press the Focus Ability key to summon a temporary Offshore Harpoon for 30 seconds.",
```

- [ ] **Step 8: Run focused tests and commit**

Run:

```powershell
.\gradlew.bat test --no-daemon --tests dev.attuned.content.FocusDataConsistencyTest
```

Expected: PASS.

Commit:

```powershell
git add src/test/java/dev/attuned/content/FocusDataConsistencyTest.java src/main/java/dev/attuned/content/AttunedContent.java src/main/resources/data/attuned/attuned/focus/harpoon_focus.json src/main/resources/assets/attuned/items/harpoon_focus.json src/main/resources/assets/attuned/models/item/harpoon_focus.json src/main/resources/assets/attuned/textures/item/harpoon_focus.png src/main/resources/assets/attuned/textures/item/harpoon_focus.png.mcmeta src/main/resources/assets/attuned/lang/en_us.json
git commit -m "feat: add Offshore harpoon focus"
```

---

### Task 2: Implement Temporary Harpoon Ability and Cleanup

**Files:**
- Create: `src/test/java/dev/attuned/content/HarpoonBehaviorContractTest.java`
- Create: `src/main/java/dev/attuned/content/behavior/HarpoonBehavior.java`
- Create: `src/main/java/dev/attuned/mixin/ThrownTridentMixin.java`
- Modify: `src/main/resources/attuned.mixins.json`
- Modify: `src/main/java/dev/attuned/content/AttunedContent.java`
- Modify: `src/main/resources/data/attuned/attuned/focus/harpoon_focus.json`

- [ ] **Step 1: Write the failing harpoon behavior contract**

Create `src/test/java/dev/attuned/content/HarpoonBehaviorContractTest.java`:

```java
package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source-level guardrails for the temporary Offshore Harpoon ability. */
class HarpoonBehaviorContractTest {
	private static final Path HARPOON_BEHAVIOR =
		Path.of("src/main/java/dev/attuned/content/behavior/HarpoonBehavior.java");
	private static final Path TRIDENT_MIXIN =
		Path.of("src/main/java/dev/attuned/mixin/ThrownTridentMixin.java");
	private static final Path MIXIN_CONFIG =
		Path.of("src/main/resources/attuned.mixins.json");
	private static final Path CONTENT_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedContent.java");
	private static final Path HARPOON_FOCUS_DATA =
		Path.of("src/main/resources/data/attuned/attuned/focus/harpoon_focus.json");

	@Test
	void harpoonFocusRegistersAsOneActiveAbilityWithFixedTiming() throws IOException {
		String behavior = read(HARPOON_BEHAVIOR);
		String content = read(CONTENT_SOURCE);
		String data = read(HARPOON_FOCUS_DATA);

		assertTrue(behavior.contains("public final class HarpoonBehavior implements FocusBehavior"),
			"Harpoon should be a focused FocusBehavior implementation");
		assertTrue(behavior.contains("static final int DURATION_TICKS = 600"),
			"Harpoon duration should be 30 seconds");
		assertTrue(behavior.contains("static final int COOLDOWN_TICKS = 1200"),
			"Harpoon cooldown should be 60 seconds");
		assertTrue(behavior.contains("public boolean hasActiveAbility()"),
			"Harpoon should opt into the single active ability slot");
		assertTrue(behavior.contains("public int abilityCooldownTicks()"),
			"Harpoon should expose cooldown to the HUD");
		assertTrue(behavior.contains("public boolean onAbility(ServerPlayer player, ItemStack focus)"),
			"Harpoon should spawn through the server-authoritative ability path");
		assertTrue(content.contains("new HarpoonBehavior()"),
			"AttunedContent should register the harpoon behavior");
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
		assertTrue(behavior.contains("MARKER_ID = \"attuned:offshore_harpoon\""),
			"Temporary harpoon marker id should be stable");
		assertTrue(behavior.contains("OWNER_KEY = \"owner\""),
			"Temporary harpoon should remember its owner");
		assertTrue(behavior.contains("EXPIRES_AT_KEY = \"expires_at\""),
			"Temporary harpoon should remember its expiry tick");
	}

	@Test
	void temporaryHarpoonCleansInventoryDroppedItemsAndProjectiles() throws IOException {
		String behavior = read(HARPOON_BEHAVIOR);
		String mixin = read(TRIDENT_MIXIN);
		String mixinConfig = read(MIXIN_CONFIG);

		assertTrue(behavior.contains("ServerTickEvents.END_SERVER_TICK.register"),
			"Harpoon behavior should scan transient harpoons on server ticks");
		assertTrue(behavior.contains("ServerLifecycleEvents.SERVER_STOPPED.register"),
			"Harpoon behavior should clear cached state on server stop");
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
		assertTrue(mixinConfig.contains("\"ThrownTridentMixin\""),
			"Common mixin config should install the thrown trident guard");
		assertTrue(mixin.contains("@Mixin(ThrownTrident.class)"),
			"Mixin should target vanilla thrown tridents");
		assertTrue(mixin.contains("method = \"tick\""),
			"Mixin should discard expired projectiles before vanilla tick work");
		assertTrue(mixin.contains("method = \"tryPickup\""),
			"Mixin should block expired pickup");
		assertTrue(mixin.contains("method = \"onHitEntity\""),
			"Mixin should discard the temporary harpoon after entity hits");
		assertTrue(mixin.contains("method = \"hitBlockEnchantmentEffects\""),
			"Mixin should discard the temporary harpoon after block hits");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
```

- [ ] **Step 2: Run the focused contract and confirm it fails**

Run:

```powershell
.\gradlew.bat test --no-daemon --tests dev.attuned.content.HarpoonBehaviorContractTest
```

Expected: FAIL because `HarpoonBehavior.java` and `ThrownTridentMixin.java` do not exist and `harpoon_focus.json` has no behavior id.

- [ ] **Step 3: Implement HarpoonBehavior**

Create `src/main/java/dev/attuned/content/behavior/HarpoonBehavior.java`:

```java
package dev.attuned.content.behavior;

import dev.attuned.Attuned;
import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Offshore Harpoon Focus: summons one temporary custom trident on the ability key. */
public final class HarpoonBehavior implements FocusBehavior {
	static final int DURATION_TICKS = 600;
	static final int COOLDOWN_TICKS = 1200;
	private static final String MARKER_ID = "attuned:offshore_harpoon";
	private static final String MARKER_KEY = "marker";
	private static final String OWNER_KEY = "owner";
	private static final String EXPIRES_AT_KEY = "expires_at";
	private static final Identifier HARPOON_MODEL =
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "offshore_harpoon");
	private static final Component HARPOON_NAME =
		Component.translatable("item.attuned.offshore_harpoon");
	private static final Map<UUID, Long> ACTIVE_HARPOONS = new HashMap<>();
	private static boolean initialized;

	public HarpoonBehavior() {
		initLifecycle();
	}

	@Override
	public boolean hasActiveAbility() {
		return true;
	}

	@Override
	public int abilityCooldownTicks() {
		return COOLDOWN_TICKS;
	}

	@Override
	public boolean onAbility(ServerPlayer player, ItemStack focus) {
		long now = player.level().getGameTime();
		removeInvalidInventoryHarpoons(player, now);
		if (ACTIVE_HARPOONS.getOrDefault(player.getUUID(), -1L) > now) {
			player.displayClientMessage(Component.translatable("item.attuned.harpoon_focus.active"), true);
			return false;
		}

		ItemStack harpoon = createHarpoon(player, now + DURATION_TICKS);
		if (!placeHarpoon(player, harpoon)) {
			player.displayClientMessage(Component.translatable("item.attuned.harpoon_focus.no_space"), true);
			return false;
		}

		ACTIVE_HARPOONS.put(player.getUUID(), now + DURATION_TICKS);
		player.level().playSound(null, player.blockPosition(),
			SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.75F, 0.85F);
		return true;
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		long now = player.level().getGameTime();
		Long expiresAt = ACTIVE_HARPOONS.get(player.getUUID());
		if (expiresAt == null || expiresAt <= now) {
			clearPlayerHarpoons(player);
			ACTIVE_HARPOONS.remove(player.getUUID());
			return;
		}
		removeInvalidInventoryHarpoons(player, now);
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		clearPlayerHarpoons(player);
		ACTIVE_HARPOONS.remove(player.getUUID());
		MinecraftServer server = player.getServer();
		if (server != null) {
			clearWorldHarpoonsForOwner(server, player.getUUID());
		}
	}

	public static boolean isTemporaryHarpoon(ItemStack stack) {
		if (!stack.is(Items.TRIDENT)) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return false;
		}
		CompoundTag tag = data.copyTag();
		return MARKER_ID.equals(tag.getStringOr(MARKER_KEY, ""));
	}

	public static boolean shouldDiscardProjectile(ItemStack stack, long now) {
		return isTemporaryHarpoon(stack) && expiresAt(stack) <= now;
	}

	private static void initLifecycle() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(HarpoonBehavior::tickServer);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE_HARPOONS.clear());
		AttunedPlayerCleanup.onForgetPlayer(player -> {
			clearPlayerHarpoons(player);
			MinecraftServer server = player.getServer();
			if (server != null) {
				clearWorldHarpoonsForOwner(server, player.getUUID());
			}
		});
		AttunedPlayerCleanup.onForget(ACTIVE_HARPOONS::remove);
	}

	private static ItemStack createHarpoon(ServerPlayer player, long expiresAt) {
		ItemStack stack = new ItemStack(Items.TRIDENT);
		CompoundTag tag = new CompoundTag();
		tag.putString(MARKER_KEY, MARKER_ID);
		tag.putString(OWNER_KEY, player.getUUID().toString());
		tag.putLong(EXPIRES_AT_KEY, expiresAt);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		stack.set(DataComponents.ITEM_MODEL, HARPOON_MODEL);
		stack.set(DataComponents.CUSTOM_NAME, HARPOON_NAME);
		stack.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
		return stack;
	}

	private static boolean placeHarpoon(ServerPlayer player, ItemStack harpoon) {
		if (player.getMainHandItem().isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, harpoon);
			player.inventoryMenu.broadcastChanges();
			return true;
		}

		Inventory inventory = player.getInventory();
		int slot = inventory.getFreeSlot();
		if (slot == -1) {
			return false;
		}
		inventory.setItem(slot, harpoon);
		inventory.setChanged();
		player.inventoryMenu.broadcastChanges();
		return true;
	}

	private static void tickServer(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			removeInvalidInventoryHarpoons(player, now);
		}
		if (now % 20L != 0L) {
			return;
		}
		removeExpiredActiveEntries(now);
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity itemEntity
						&& shouldDiscardInWorld(itemEntity.getItem(), now)) {
					itemEntity.discard();
				} else if (entity instanceof ThrownTrident trident
						&& shouldDiscardInWorld(trident.getPickupItemStackOrigin(), now)) {
					trident.discard();
				}
			}
		}
	}

	private static void removeExpiredActiveEntries(long now) {
		Iterator<Map.Entry<UUID, Long>> it = ACTIVE_HARPOONS.entrySet().iterator();
		while (it.hasNext()) {
			if (it.next().getValue() <= now) {
				it.remove();
			}
		}
	}

	private static boolean shouldDiscardInWorld(ItemStack stack, long now) {
		if (!isTemporaryHarpoon(stack)) {
			return false;
		}
		if (expiresAt(stack) <= now) {
			return true;
		}
		UUID owner = ownerOf(stack);
		return owner == null || ACTIVE_HARPOONS.getOrDefault(owner, -1L) <= now;
	}

	private static void removeInvalidInventoryHarpoons(ServerPlayer holder, long now) {
		Inventory inventory = holder.getInventory();
		boolean changed = false;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (shouldDiscardForHolder(stack, holder.getUUID(), now)) {
				inventory.setItem(slot, ItemStack.EMPTY);
				changed = true;
			}
		}
		if (changed) {
			inventory.setChanged();
			holder.inventoryMenu.broadcastChanges();
		}
	}

	private static boolean shouldDiscardForHolder(ItemStack stack, UUID holder, long now) {
		if (!isTemporaryHarpoon(stack)) {
			return false;
		}
		UUID owner = ownerOf(stack);
		return owner == null
			|| !owner.equals(holder)
			|| expiresAt(stack) <= now
			|| ACTIVE_HARPOONS.getOrDefault(owner, -1L) <= now;
	}

	private static void clearPlayerHarpoons(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		boolean changed = false;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (isTemporaryHarpoon(inventory.getItem(slot))) {
				inventory.setItem(slot, ItemStack.EMPTY);
				changed = true;
			}
		}
		if (changed) {
			inventory.setChanged();
			player.inventoryMenu.broadcastChanges();
		}
	}

	private static void clearWorldHarpoonsForOwner(MinecraftServer server, UUID owner) {
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof ItemEntity itemEntity
						&& owner.equals(ownerOf(itemEntity.getItem()))) {
					itemEntity.discard();
				} else if (entity instanceof ThrownTrident trident
						&& owner.equals(ownerOf(trident.getPickupItemStackOrigin()))) {
					trident.discard();
				}
			}
		}
	}

	private static UUID ownerOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		String value = data.copyTag().getStringOr(OWNER_KEY, "");
		try {
			return value.isBlank() ? null : UUID.fromString(value);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static long expiresAt(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return -1L;
		}
		return data.copyTag().getLongOr(EXPIRES_AT_KEY, -1L);
	}
}
```

- [ ] **Step 4: Register the behavior and wire data**

In `AttunedContent.java`, add the import:

```java
import dev.attuned.content.behavior.HarpoonBehavior;
```

In `AttunedContent.java`, add this behavior registration after `driftglass`:

```java
AttunedRegistries.registerBehavior(
	Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "harpoon"), new HarpoonBehavior());
```

Update `src/main/resources/data/attuned/attuned/focus/harpoon_focus.json`:

```json
{
	"item": "attuned:harpoon_focus",
	"cost": 3,
	"unique": true,
	"faction": "attuned:offshore",
	"behavior": "attuned:harpoon"
}
```

Add these actionbar translations to `en_us.json`:

```json
"item.attuned.harpoon_focus.active": "An Offshore Harpoon is already in your hands.",
"item.attuned.harpoon_focus.no_space": "No room for the Offshore Harpoon.",
```

- [ ] **Step 5: Add thrown trident cleanup mixin**

Create `src/main/java/dev/attuned/mixin/ThrownTridentMixin.java`:

```java
package dev.attuned.mixin;

import dev.attuned.content.behavior.HarpoonBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents temporary Offshore Harpoons from becoming permanent thrown tridents. */
@Mixin(ThrownTrident.class)
public abstract class ThrownTridentMixin {
	@Shadow
	public abstract ItemStack getPickupItemStackOrigin();

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void attuned$discardExpiredHarpoon(CallbackInfo ci) {
		ThrownTrident trident = (ThrownTrident) (Object) this;
		if (HarpoonBehavior.shouldDiscardProjectile(this.getPickupItemStackOrigin(),
				trident.level().getGameTime())) {
			trident.discard();
			ci.cancel();
		}
	}

	@Inject(method = "tryPickup", at = @At("HEAD"), cancellable = true)
	private void attuned$blockExpiredHarpoonPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
		ThrownTrident trident = (ThrownTrident) (Object) this;
		if (HarpoonBehavior.shouldDiscardProjectile(this.getPickupItemStackOrigin(),
				trident.level().getGameTime())) {
			trident.discard();
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "onHitEntity", at = @At("TAIL"))
	private void attuned$discardHarpoonAfterEntityHit(EntityHitResult hitResult, CallbackInfo ci) {
		discardAfterHit();
	}

	@Inject(method = "hitBlockEnchantmentEffects", at = @At("TAIL"))
	private void attuned$discardHarpoonAfterBlockHit(
			ServerLevel level, BlockHitResult hitResult, ItemStack weapon, CallbackInfo ci) {
		discardAfterHit();
	}

	private void discardAfterHit() {
		if (HarpoonBehavior.isTemporaryHarpoon(this.getPickupItemStackOrigin())) {
			((ThrownTrident) (Object) this).discard();
		}
	}
}
```

Modify `src/main/resources/attuned.mixins.json`:

```json
"mixins": [
	"InventoryMenuMixin",
	"FishingHookMixin",
	"FishingRodItemMixin",
	"LivingEntityHurtMixin",
	"LivingEntityKnockbackMixin",
	"ServerGamePacketListenerImplMixin",
	"ThrownTridentMixin"
],
```

- [ ] **Step 6: Run focused tests and compile check**

Run:

```powershell
.\gradlew.bat test --no-daemon --tests dev.attuned.content.HarpoonBehaviorContractTest --tests dev.attuned.content.FocusDataConsistencyTest
```

Expected: PASS.

Run:

```powershell
.\gradlew.bat build --no-daemon
```

Expected: PASS with no mixin compile errors.

- [ ] **Step 7: Commit**

```powershell
git add src/test/java/dev/attuned/content/HarpoonBehaviorContractTest.java src/main/java/dev/attuned/content/behavior/HarpoonBehavior.java src/main/java/dev/attuned/mixin/ThrownTridentMixin.java src/main/resources/attuned.mixins.json src/main/java/dev/attuned/content/AttunedContent.java src/main/resources/data/attuned/attuned/focus/harpoon_focus.json src/main/resources/assets/attuned/lang/en_us.json
git commit -m "feat: summon temporary Offshore harpoon"
```

---

### Task 3: Add 3D Offshore Harpoon Item Presentation

**Files:**
- Modify: `src/test/java/dev/attuned/content/HarpoonBehaviorContractTest.java`
- Create: `src/main/resources/assets/attuned/items/offshore_harpoon.json`
- Create: `src/main/resources/assets/attuned/models/item/offshore_harpoon.json`
- Create: `src/main/resources/assets/attuned/textures/item/offshore_harpoon.png`

- [ ] **Step 1: Extend the asset contract**

Add this test method to `HarpoonBehaviorContractTest.java`:

```java
@Test
void offshoreHarpoonShipsCustomThreeDimensionalItemAssets() throws IOException {
	Path itemDefinition = Path.of("src/main/resources/assets/attuned/items/offshore_harpoon.json");
	Path itemModel = Path.of("src/main/resources/assets/attuned/models/item/offshore_harpoon.json");
	Path itemTexture = Path.of("src/main/resources/assets/attuned/textures/item/offshore_harpoon.png");

	assertTrue(Files.isRegularFile(itemDefinition),
		"Temporary harpoon should have an item definition selected by DataComponents.ITEM_MODEL");
	assertTrue(Files.isRegularFile(itemModel),
		"Temporary harpoon should have a custom item model");
	assertTrue(Files.isRegularFile(itemTexture),
		"Temporary harpoon should have a custom texture");

	String definition = read(itemDefinition);
	String model = read(itemModel);
	assertTrue(definition.contains("\"model\": \"attuned:item/offshore_harpoon\""),
		"Item definition should point at the Attuned harpoon model");
	assertTrue(model.contains("\"elements\""),
		"Harpoon model should use cuboid elements instead of a flat generated parent");
	assertTrue(model.contains("\"display\""),
		"Harpoon model should define held/inventory transforms");
	assertTrue(model.contains("\"central_barb\""),
		"Harpoon silhouette should include a strong central barb");
	assertTrue(model.contains("\"left_prong\"") && model.contains("\"right_prong\""),
		"Harpoon silhouette should include side prongs");
}
```

- [ ] **Step 2: Run the focused contract and confirm it fails**

Run:

```powershell
.\gradlew.bat test --no-daemon --tests dev.attuned.content.HarpoonBehaviorContractTest
```

Expected: FAIL because the `offshore_harpoon` item definition/model/texture do not exist.

- [ ] **Step 3: Add the item definition**

Create `src/main/resources/assets/attuned/items/offshore_harpoon.json`:

```json
{
	"model": {
		"type": "minecraft:model",
		"model": "attuned:item/offshore_harpoon"
	}
}
```

- [ ] **Step 4: Add a custom cuboid item model**

Create `src/main/resources/assets/attuned/models/item/offshore_harpoon.json`:

```json
{
	"textures": {
		"harpoon": "attuned:item/offshore_harpoon"
	},
	"elements": [
		{
			"name": "shaft",
			"from": [7.1, 0.0, 7.1],
			"to": [8.9, 12.8, 8.9],
			"faces": {
				"north": { "uv": [0, 0, 2, 13], "texture": "#harpoon" },
				"south": { "uv": [0, 0, 2, 13], "texture": "#harpoon" },
				"east": { "uv": [2, 0, 4, 13], "texture": "#harpoon" },
				"west": { "uv": [2, 0, 4, 13], "texture": "#harpoon" },
				"up": { "uv": [4, 0, 6, 2], "texture": "#harpoon" },
				"down": { "uv": [4, 2, 6, 4], "texture": "#harpoon" }
			}
		},
		{
			"name": "copper_wrap",
			"from": [6.5, 4.2, 6.5],
			"to": [9.5, 5.4, 9.5],
			"faces": {
				"north": { "uv": [8, 0, 11, 2], "texture": "#harpoon" },
				"south": { "uv": [8, 0, 11, 2], "texture": "#harpoon" },
				"east": { "uv": [11, 0, 14, 2], "texture": "#harpoon" },
				"west": { "uv": [11, 0, 14, 2], "texture": "#harpoon" },
				"up": { "uv": [8, 2, 11, 5], "texture": "#harpoon" },
				"down": { "uv": [11, 2, 14, 5], "texture": "#harpoon" }
			}
		},
		{
			"name": "storm_core",
			"from": [6.4, 8.1, 6.4],
			"to": [9.6, 10.0, 9.6],
			"faces": {
				"north": { "uv": [16, 0, 20, 3], "texture": "#harpoon" },
				"south": { "uv": [16, 0, 20, 3], "texture": "#harpoon" },
				"east": { "uv": [20, 0, 24, 3], "texture": "#harpoon" },
				"west": { "uv": [20, 0, 24, 3], "texture": "#harpoon" },
				"up": { "uv": [16, 3, 20, 7], "texture": "#harpoon" },
				"down": { "uv": [20, 3, 24, 7], "texture": "#harpoon" }
			}
		},
		{
			"name": "central_barb",
			"from": [6.7, 12.2, 6.7],
			"to": [9.3, 16.0, 9.3],
			"faces": {
				"north": { "uv": [28, 0, 32, 6], "texture": "#harpoon" },
				"south": { "uv": [28, 0, 32, 6], "texture": "#harpoon" },
				"east": { "uv": [32, 0, 36, 6], "texture": "#harpoon" },
				"west": { "uv": [32, 0, 36, 6], "texture": "#harpoon" },
				"up": { "uv": [28, 6, 32, 10], "texture": "#harpoon" },
				"down": { "uv": [32, 6, 36, 10], "texture": "#harpoon" }
			}
		},
		{
			"name": "left_prong",
			"from": [3.8, 11.9, 7.0],
			"to": [6.7, 15.0, 9.0],
			"faces": {
				"north": { "uv": [40, 0, 44, 5], "texture": "#harpoon" },
				"south": { "uv": [40, 0, 44, 5], "texture": "#harpoon" },
				"east": { "uv": [44, 0, 48, 5], "texture": "#harpoon" },
				"west": { "uv": [44, 0, 48, 5], "texture": "#harpoon" },
				"up": { "uv": [40, 5, 44, 9], "texture": "#harpoon" },
				"down": { "uv": [44, 5, 48, 9], "texture": "#harpoon" }
			}
		},
		{
			"name": "right_prong",
			"from": [9.3, 11.9, 7.0],
			"to": [12.2, 15.0, 9.0],
			"faces": {
				"north": { "uv": [40, 0, 44, 5], "texture": "#harpoon" },
				"south": { "uv": [40, 0, 44, 5], "texture": "#harpoon" },
				"east": { "uv": [44, 0, 48, 5], "texture": "#harpoon" },
				"west": { "uv": [44, 0, 48, 5], "texture": "#harpoon" },
				"up": { "uv": [40, 5, 44, 9], "texture": "#harpoon" },
				"down": { "uv": [44, 5, 48, 9], "texture": "#harpoon" }
			}
		}
	],
	"display": {
		"gui": {
			"rotation": [32, -38, 0],
			"translation": [0, 0, 0],
			"scale": [0.9, 0.9, 0.9]
		},
		"firstperson_righthand": {
			"rotation": [0, -88, 25],
			"translation": [3.8, 3.8, -2.0],
			"scale": [1.15, 1.15, 1.15]
		},
		"thirdperson_righthand": {
			"rotation": [0, -90, 55],
			"translation": [0.0, 3.2, 1.0],
			"scale": [0.95, 0.95, 0.95]
		}
	}
}
```

- [ ] **Step 5: Generate the harpoon texture**

Use this repo-local texture file path for the generated in-game asset:

```powershell
Add-Type -AssemblyName System.Drawing
$out = "src/main/resources/assets/attuned/textures/item/offshore_harpoon.png"
$bmp = New-Object System.Drawing.Bitmap 64,64
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::Transparent)
$wood = [System.Drawing.Color]::FromArgb(255, 79, 58, 42)
$wood2 = [System.Drawing.Color]::FromArgb(255, 113, 82, 52)
$copper = [System.Drawing.Color]::FromArgb(255, 179, 94, 44)
$copperDark = [System.Drawing.Color]::FromArgb(255, 94, 56, 38)
$teal = [System.Drawing.Color]::FromArgb(255, 73, 211, 203)
$tealDark = [System.Drawing.Color]::FromArgb(255, 31, 113, 125)
$violet = [System.Drawing.Color]::FromArgb(255, 110, 79, 198)
$white = [System.Drawing.Color]::FromArgb(255, 235, 255, 252)
$g.FillRectangle((New-Object System.Drawing.SolidBrush $wood), 0, 0, 8, 16)
$g.FillRectangle((New-Object System.Drawing.SolidBrush $wood2), 2, 0, 3, 16)
$g.FillRectangle((New-Object System.Drawing.SolidBrush $copper), 8, 0, 8, 8)
$g.FillRectangle((New-Object System.Drawing.SolidBrush $copperDark), 8, 6, 8, 2)
$g.FillRectangle((New-Object System.Drawing.SolidBrush $violet), 16, 0, 8, 8)
$g.FillRectangle((New-Object System.Drawing.SolidBrush $white), 18, 1, 2, 2)
$g.FillRectangle((New-Object System.Drawing.SolidBrush $teal), 28, 0, 8, 10)
$g.FillRectangle((New-Object System.Drawing.SolidBrush $tealDark), 28, 8, 8, 2)
$g.FillRectangle((New-Object System.Drawing.SolidBrush $teal), 40, 0, 8, 9)
$g.FillRectangle((New-Object System.Drawing.SolidBrush $tealDark), 40, 7, 8, 2)
$g.FillRectangle((New-Object System.Drawing.SolidBrush $copper), 0, 18, 64, 6)
$g.FillRectangle((New-Object System.Drawing.SolidBrush $teal), 0, 28, 64, 6)
$g.FillRectangle((New-Object System.Drawing.SolidBrush $violet), 0, 38, 64, 6)
$g.Dispose()
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
```

The generated image concept that inspired this is:

```text
C:\Users\Eating\.codex\generated_images\019e7dce-5130-7f43-b5e9-cfe2334c31ae\ig_0b462bcee81ed9f8016a1e4c0ea10c8195be5ff5d1889fe056.png
```

- [ ] **Step 6: Run tests and commit**

Run:

```powershell
.\gradlew.bat test --no-daemon --tests dev.attuned.content.HarpoonBehaviorContractTest
```

Expected: PASS.

Commit:

```powershell
git add src/test/java/dev/attuned/content/HarpoonBehaviorContractTest.java src/main/resources/assets/attuned/items/offshore_harpoon.json src/main/resources/assets/attuned/models/item/offshore_harpoon.json src/main/resources/assets/attuned/textures/item/offshore_harpoon.png
git commit -m "feat: add Offshore harpoon model"
```

---

### Task 4: Add Offshore Journal and Reference Documentation

**Files:**
- Modify: `src/test/java/dev/attuned/content/AttunementJournalUiContractTest.java`
- Modify: `src/main/java/dev/attuned/content/AttunementJournalItem.java`
- Modify: `src/client/java/dev/attuned/client/screen/AttunementJournalScreen.java`
- Modify: `src/main/resources/assets/attuned/lang/en_us.json`
- Modify: `docs/reference.md`

- [ ] **Step 1: Write the failing journal contract update**

In `AttunementJournalUiContractTest.java`, add these assertions to `journalScreenKeepsCustomCodexLayoutContract()` after the Seafarers/HUD assertions:

```java
assertTrue(lang.contains("\"journal.attuned.page32\""),
	"Journal UI should include the Offshore Harpoon page");
assertTrue(screenSource.contains("journal.attuned.page32"),
	"Journal UI should route to the Offshore Harpoon page");
assertTrue(screenSource.contains("new Chapter(\"Offshore\""),
	"Journal UI should expose Offshore as its own chapter");
assertTrue(itemSource.contains("\"journal.attuned.page32\""),
	"Written-book fallback should include the Offshore Harpoon page");
```

- [ ] **Step 2: Run the focused contract and confirm it fails**

Run:

```powershell
.\gradlew.bat test --no-daemon --tests dev.attuned.content.AttunementJournalUiContractTest
```

Expected: FAIL because `journal.attuned.page32` is not present in the journal item, screen, or language file.

- [ ] **Step 3: Add the journal page to the written-book fallback**

In `AttunementJournalItem.java`, insert `"journal.attuned.page32"` between page27 and page28:

```java
"journal.attuned.page26",
"journal.attuned.page27",
"journal.attuned.page32",
"journal.attuned.page28"
```

- [ ] **Step 4: Add the custom journal page and chapter**

In `AttunementJournalScreen.java`, insert this page between the Seafarers pages and HUD:

```java
new Page("Offshore", "journal.attuned.page32", 0xFF56D8CF, null),
```

Update the chapter list so Offshore starts at the inserted page index and HUD shifts by one:

```java
new Chapter("Seafarers", 28),
new Chapter("Offshore", 30),
new Chapter("HUD", 31)
```

- [ ] **Step 5: Add journal language text**

Add this translation to `en_us.json` near page26 and page27:

```json
"journal.attuned.page32": "The Offshore\n\nWhen harbor lights stop helping, Offshore crews salvage what sank and bargain with storms.\n\nHarpoon Focus calls a temporary trident for dangerous water work. It fades after thirty seconds and cannot be crafted.",
```

- [ ] **Step 6: Update docs/reference.md**

In the faction table, add:

```markdown
| `attuned:offshore` | Salvage, storms, wreck maps, deep-water risk | Utility with danger: temporary tools, water pressure, and anti-drowned/guardian space without becoming a permanent weapon line. |
```

In the behavior table, add:

```markdown
| `attuned:harpoon`    | `HarpoonBehavior`       | Ability key summons a temporary custom-model trident for 30 seconds, then removes it from inventory, drops, or projectile state. |
```

- [ ] **Step 7: Run tests and commit**

Run:

```powershell
.\gradlew.bat test --no-daemon --tests dev.attuned.content.AttunementJournalUiContractTest
```

Expected: PASS.

Commit:

```powershell
git add src/test/java/dev/attuned/content/AttunementJournalUiContractTest.java src/main/java/dev/attuned/content/AttunementJournalItem.java src/client/java/dev/attuned/client/screen/AttunementJournalScreen.java src/main/resources/assets/attuned/lang/en_us.json docs/reference.md
git commit -m "docs: document Offshore harpoon focus"
```

---

### Task 5: Full Verification and Dev Client Smoke Test

**Files:**
- No new files.
- Verify: all files changed in Tasks 1-4.

- [ ] **Step 1: Run the full unit suite**

Run:

```powershell
.\gradlew.bat test --no-daemon
```

Expected: PASS.

- [ ] **Step 2: Run the full build**

Run:

```powershell
.\gradlew.bat build --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Relaunch the dev client**

Run:

```powershell
.\gradlew.bat runClient --no-daemon
```

Expected: Minecraft dev client starts with no mixin crash. Leave the process running for the visual/manual smoke test.

- [ ] **Step 4: In-game smoke test**

Use cheats/op in the dev world and run:

```text
/give @s attuned:harpoon_focus
/attuned capacity set @s 20
```

Equip `Harpoon Focus` in an active Focus slot, press the Focus Ability key, and verify:

- A named `Offshore Harpoon` appears in main hand if the hand is empty.
- If main hand is occupied, the harpoon appears in the first free inventory slot.
- If inventory is full, no cooldown starts and the actionbar says `No room for the Offshore Harpoon.`
- Pressing the ability again while the temporary harpoon is live does not create a second harpoon.
- Throwing the harpoon makes it vanish after hitting an entity or block.
- Dropping the harpoon makes it vanish when the 30 second timer expires.
- Unequipping or dormancy of `Harpoon Focus` removes the harpoon.
- The Foci HUD cooldown indicator counts down after a successful spawn.

- [ ] **Step 5: Commit any verification fixes**

If Task 5 required a code or asset correction, run the relevant focused test again, then:

```powershell
git add <changed-files>
git commit -m "fix: polish Offshore harpoon verification"
```

If Task 5 required no corrections, do not create an empty commit.

---

## Self-Review Notes

- Spec coverage: Task 1 adds `attuned:offshore`, `harpoon_focus`, cost 3, neutral faction metadata, translations, and normal Focus assets. Task 2 adds the 30 second temporary vanilla trident stack, 60 second cooldown, single active ability integration, no-cooldown failure when already active/full inventory, disconnect/deactivate/server cleanup, and thrown/dropped safeguards. Task 3 adds the custom 3D item model and texture. Task 4 adds journal/reference presentation. Task 5 covers tests, build, dev client, and in-game behavior checks.
- Completeness scan: no task uses deferred wording; code snippets and commands are concrete.
- Type consistency: `HarpoonBehavior.shouldDiscardProjectile(ItemStack, long)` and `HarpoonBehavior.isTemporaryHarpoon(ItemStack)` are defined before `ThrownTridentMixin` calls them; behavior id `attuned:harpoon` is registered in `AttunedContent` and referenced by `harpoon_focus.json`; item model id `attuned:offshore_harpoon` matches `DataComponents.ITEM_MODEL` and `assets/attuned/items/offshore_harpoon.json`.
