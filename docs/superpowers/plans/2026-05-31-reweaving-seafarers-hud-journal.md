# Reweaving Seafarers HUD Journal Implementation Plan

> **Implementation:** Work tasks in order using the checkbox (`- [ ]`) syntax below.

**Goal:** Build the approved Reweaving expansion: a dedicated Altar of Reweaving, peaceful Seafarers fishing Foci, client HUD toggles, journal button rendering fix, expanded lore pages, and final Minecraft-ready assets.

**Architecture:** Keep each subsystem bounded. Reweaving gets its own block, menu, payload, screen, and result picker. Seafarers stay neutral utility Foci with one fishing hook mixin for catch-time effects and ordinary `FocusBehavior` classes for tick-time effects. HUD settings are client-only and saved in `config/attuned-client.json`.

**Tech Stack:** Java 25, Minecraft 26.1.2, Fabric API 0.149.0, Fabric Loom, Mixin, JUnit 5, JSON datapack/resources, PNG textures.

---

## Scope Check

This spec spans several independent subsystems, so execute it as parallel-safe tracks:

- Track A: Journal button rendering and journal lore copy.
- Track B: Client HUD config and keybind toggles.
- Track C: Reweaving altar block, menu, networking, screen, and result logic.
- Track D: Seafarers Foci, fishing mixin, data, loot weighting, and assets.
- Track E: Final verification.

Do not implement Holy/Radiant mechanics from the earlier spec in this plan. This plan may reference Radiant and Unseen in journal/lore text only.

## File Map

Create:

- `src/client/java/dev/attuned/client/AttunedClientConfig.java`
- `src/main/java/dev/attuned/content/AltarOfReweavingBlock.java`
- `src/main/java/dev/attuned/content/ReweavingResultPicker.java`
- `src/main/java/dev/attuned/menu/ReweavingMenu.java`
- `src/main/java/dev/attuned/menu/ReweavingMenuType.java`
- `src/main/java/dev/attuned/menu/ReweavePayload.java`
- `src/main/java/dev/attuned/menu/ReweavingNetworking.java`
- `src/client/java/dev/attuned/client/screen/ReweavingScreen.java`
- `src/main/java/dev/attuned/content/behavior/HarborlightBehavior.java`
- `src/main/java/dev/attuned/content/behavior/DriftglassBehavior.java`
- `src/main/java/dev/attuned/content/behavior/SeafarersFishing.java`
- `src/main/java/dev/attuned/mixin/FishingHookMixin.java`
- `src/test/java/dev/attuned/content/ReweavingResultPickerTest.java`
- `src/test/java/dev/attuned/content/ReweavingContentContractTest.java`
- `src/test/java/dev/attuned/client/AttunedClientConfigContractTest.java`
- `src/test/java/dev/attuned/client/CombatHudSettingsContractTest.java`
- `src/main/resources/data/attuned/attuned/focus/linecast_focus.json`
- `src/main/resources/data/attuned/attuned/focus/netmender_focus.json`
- `src/main/resources/data/attuned/attuned/focus/harborlight_focus.json`
- `src/main/resources/data/attuned/attuned/focus/driftglass_focus.json`
- `src/main/resources/data/attuned/recipe/altar_of_reweaving.json`
- `src/main/resources/data/attuned/loot_table/blocks/altar_of_reweaving.json`
- `src/main/resources/assets/attuned/blockstates/altar_of_reweaving.json`
- `src/main/resources/assets/attuned/items/altar_of_reweaving.json`
- `src/main/resources/assets/attuned/items/linecast_focus.json`
- `src/main/resources/assets/attuned/items/netmender_focus.json`
- `src/main/resources/assets/attuned/items/harborlight_focus.json`
- `src/main/resources/assets/attuned/items/driftglass_focus.json`
- `src/main/resources/assets/attuned/models/block/altar_of_reweaving.json`
- `src/main/resources/assets/attuned/models/item/altar_of_reweaving.json`
- `src/main/resources/assets/attuned/models/item/linecast_focus.json`
- `src/main/resources/assets/attuned/models/item/netmender_focus.json`
- `src/main/resources/assets/attuned/models/item/harborlight_focus.json`
- `src/main/resources/assets/attuned/models/item/driftglass_focus.json`
- `src/main/resources/assets/attuned/textures/gui/altar_of_reweaving.png`
- `src/main/resources/assets/attuned/textures/block/altar_of_reweaving_base.png`
- `src/main/resources/assets/attuned/textures/block/altar_of_reweaving_top.png`
- `src/main/resources/assets/attuned/textures/block/altar_of_reweaving_gem.png`
- `src/main/resources/assets/attuned/textures/item/linecast_focus.png`
- `src/main/resources/assets/attuned/textures/item/netmender_focus.png`
- `src/main/resources/assets/attuned/textures/item/harborlight_focus.png`
- `src/main/resources/assets/attuned/textures/item/driftglass_focus.png`
- `.png.mcmeta` files for each new Focus item texture.

Modify:

- `src/main/java/dev/attuned/Attuned.java`
- `src/main/java/dev/attuned/content/AttunedContent.java`
- `src/main/java/dev/attuned/content/AttunedLoot.java`
- `src/main/resources/attuned.mixins.json`
- `src/client/java/dev/attuned/client/AttunedClient.java`
- `src/client/java/dev/attuned/client/AttunedKeybinds.java`
- `src/client/java/dev/attuned/client/hud/CombatHud.java`
- `src/client/java/dev/attuned/client/screen/AltarScreens.java`
- `src/client/java/dev/attuned/client/screen/AttunementJournalScreen.java`
- `src/main/resources/assets/attuned/lang/en_us.json`
- `src/test/java/dev/attuned/content/AttunementJournalUiContractTest.java`
- `src/test/java/dev/attuned/content/FocusDataConsistencyTest.java`
- `src/test/java/dev/attuned/content/AttunedLootCompatibilityTest.java`
- `src/test/java/dev/attuned/client/UiAssetContractTest.java`

---

### Task 1: Baseline And Guardrails

**Files:**

- Read-only: all sources
- Test command only

- [ ] **Step 1: Confirm the worktree is clean**

Run:

```powershell
git status --short
```

Expected: no output.

- [ ] **Step 2: Run the current unit tests**

Run:

```powershell
.\gradlew test
```

Expected: `BUILD SUCCESSFUL`. If this fails before edits, stop and fix the baseline failure first.

- [ ] **Step 3: Run a resource sanity scan**

Run:

```powershell
rg -n "altar_of_reweaving|linecast_focus|netmender_focus|harborlight_focus|driftglass_focus|show_own_affinity_hud|show_enemy_affinity_hud" src docs
```

Expected: only the approved spec and plan mention these names before implementation.

---

### Task 2: Journal Button Rendering Fix

**Files:**

- Modify: `src/client/java/dev/attuned/client/screen/AttunementJournalScreen.java`
- Modify: `src/main/resources/assets/attuned/lang/en_us.json`
- Modify: `src/test/java/dev/attuned/content/AttunementJournalUiContractTest.java`

- [ ] **Step 1: Add a failing journal button contract test**

Append assertions to `journalScreenKeepsCustomCodexLayoutContract()`:

```java
assertTrue(screenSource.contains("private static final class JournalButton extends Button"),
	"Journal controls should use a custom renderer instead of stock vanilla button art");
assertTrue(screenSource.contains("extractContents(GuiGraphicsExtractor"),
	"Journal buttons should paint their own codex-style face");
assertTrue(screenSource.contains("Component.translatable(\"screen.attuned.journal.previous\")"),
	"Previous page button should use a translatable label");
assertTrue(screenSource.contains("Component.translatable(\"screen.attuned.journal.next\")"),
	"Next page button should use a translatable label");
```

Run:

```powershell
.\gradlew test --tests dev.attuned.content.AttunementJournalUiContractTest
```

Expected: FAIL because the custom class and translatable page labels do not exist.

- [ ] **Step 2: Replace stock journal buttons with custom buttons**

In `AttunementJournalScreen`, change chapter and page buttons from `Button.builder(...)` to a private helper:

```java
private JournalButton addJournalButton(Component label, int x, int y, int width, int height,
		boolean chapterButton, Button.OnPress onPress) {
	JournalButton button = new JournalButton(x, y, width, height, label, chapterButton, onPress);
	this.addRenderableWidget(button);
	return button;
}
```

Use it in `init()`:

```java
JournalButton button = addJournalButton(
	Component.literal(chapter.name()),
	navX,
	y + i * (CHAPTER_BUTTON_HEIGHT + 4),
	NAV_WIDTH - 10,
	CHAPTER_BUTTON_HEIGHT,
	true,
	btn -> setPage(chapter.firstPage()));
this.chapterButtons.add(button);
```

Use translatable page labels:

```java
this.previousButton = addJournalButton(
	Component.translatable("screen.attuned.journal.previous"),
	contentLeft,
	buttonY,
	PAGE_BUTTON_WIDTH,
	PAGE_BUTTON_HEIGHT,
	false,
	btn -> setPage(this.pageIndex - 1));
this.nextButton = addJournalButton(
	Component.translatable("screen.attuned.journal.next"),
	contentLeft + contentWidth - PAGE_BUTTON_WIDTH,
	buttonY,
	PAGE_BUTTON_WIDTH,
	PAGE_BUTTON_HEIGHT,
	false,
	btn -> setPage(this.pageIndex + 1));
```

- [ ] **Step 3: Add the `JournalButton` renderer**

Add this inner class near the bottom of `AttunementJournalScreen`, before the records:

```java
private static final class JournalButton extends Button {
	private static final int OUTLINE = 0xFF18131F;
	private static final int FACE = 0xD82A2333;
	private static final int FACE_HOVER = 0xE83A3048;
	private static final int FACE_DISABLED = 0x80211C28;
	private static final int TRIM = 0xFF7D66A3;
	private static final int TRIM_HOVER = 0xFFB995FF;
	private static final int TRIM_DISABLED = 0xFF51465F;
	private final boolean chapterButton;

	private JournalButton(int x, int y, int width, int height, Component message,
			boolean chapterButton, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		this.chapterButton = chapterButton;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x0 = getX();
		int y0 = getY();
		int x1 = x0 + getWidth();
		int y1 = y0 + getHeight();
		int face = !this.active ? FACE_DISABLED : (isHoveredOrFocused() ? FACE_HOVER : FACE);
		int trim = !this.active ? TRIM_DISABLED : (isHoveredOrFocused() ? TRIM_HOVER : TRIM);

		graphics.fill(x0, y0, x1, y1, OUTLINE);
		graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, trim);
		graphics.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, face);
		if (this.chapterButton) {
			graphics.fill(x0 + 3, y0 + 4, x0 + 6, y1 - 4, trim);
		} else {
			graphics.fill(x0 + 4, y0 + 3, x1 - 4, y0 + 4, 0xFFE0C6FF);
		}
		extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
	}
}
```

- [ ] **Step 4: Move the progress indicator away from page buttons**

In `drawPage`, replace the progress positioning block with:

```java
String progress = (this.pageIndex + 1) + " / " + PAGES.size();
int progressY = top() + PANEL_HEIGHT - 45;
graphics.text(this.font, Component.literal(progress), x + (w - this.font.width(progress)) / 2,
	progressY - 8, TEXT_MUTED, false);
int progressWidth = w - PAGE_BUTTON_WIDTH * 2 - 18;
int progressX = x + PAGE_BUTTON_WIDTH + 9;
int progressFill = Math.max(4, Math.round(progressWidth * ((this.pageIndex + 1) / (float) PAGES.size())));
graphics.fill(progressX, progressY, progressX + progressWidth, progressY + 2, CONTENT_INSET);
graphics.fill(progressX, progressY, progressX + progressFill, progressY + 2, accent);
```

- [ ] **Step 5: Add language keys**

Add to `en_us.json` near the journal screen keys:

```json
"screen.attuned.journal.previous": "Previous",
"screen.attuned.journal.next": "Next",
```

- [ ] **Step 6: Verify the focused test passes**

Run:

```powershell
.\gradlew test --tests dev.attuned.content.AttunementJournalUiContractTest
```

Expected: PASS.

- [ ] **Step 7: Commit the journal fix**

Run:

```powershell
git add src/client/java/dev/attuned/client/screen/AttunementJournalScreen.java src/main/resources/assets/attuned/lang/en_us.json src/test/java/dev/attuned/content/AttunementJournalUiContractTest.java
git commit -m "Fix journal button rendering"
```

---

### Task 3: Client HUD Config And Toggle Keybinds

**Files:**

- Create: `src/client/java/dev/attuned/client/AttunedClientConfig.java`
- Modify: `src/client/java/dev/attuned/client/AttunedClient.java`
- Modify: `src/client/java/dev/attuned/client/AttunedKeybinds.java`
- Modify: `src/client/java/dev/attuned/client/hud/CombatHud.java`
- Modify: `src/main/resources/assets/attuned/lang/en_us.json`
- Create: `src/test/java/dev/attuned/client/AttunedClientConfigContractTest.java`
- Create: `src/test/java/dev/attuned/client/CombatHudSettingsContractTest.java`

- [ ] **Step 1: Add failing source-level client config tests**

Create `AttunedClientConfigContractTest.java`:

```java
package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AttunedClientConfigContractTest {
	private static final Path CONFIG_SOURCE =
		Path.of("src/client/java/dev/attuned/client/AttunedClientConfig.java");
	private static final Path KEYBINDS_SOURCE =
		Path.of("src/client/java/dev/attuned/client/AttunedKeybinds.java");
	private static final Path CLIENT_INIT_SOURCE =
		Path.of("src/client/java/dev/attuned/client/AttunedClient.java");
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void clientHudConfigDefaultsBothHudSectionsOnAndSavesClientSide() throws IOException {
		String source = Files.readString(CONFIG_SOURCE, StandardCharsets.UTF_8);
		assertTrue(source.contains("show_own_affinity_hud"));
		assertTrue(source.contains("show_enemy_affinity_hud"));
		assertTrue(source.contains("resolve(\"attuned-client.json\")"));
		assertTrue(source.contains("DEFAULT = new AttunedClientConfig(true, true)"));
		assertTrue(source.contains("save()"));
	}

	@Test
	void keybindsExposeSeparateHudToggles() throws IOException {
		String keybinds = Files.readString(KEYBINDS_SOURCE, StandardCharsets.UTF_8);
		String client = Files.readString(CLIENT_INIT_SOURCE, StandardCharsets.UTF_8);
		String lang = Files.readString(LANG_FILE, StandardCharsets.UTF_8);
		assertTrue(client.contains("AttunedClientConfig.load()"));
		assertTrue(keybinds.contains("key.attuned.toggle_own_affinity_hud"));
		assertTrue(keybinds.contains("key.attuned.toggle_enemy_affinity_hud"));
		assertTrue(keybinds.contains("InputConstants.UNKNOWN"));
		assertTrue(lang.contains("\"key.attuned.toggle_own_affinity_hud\""));
		assertTrue(lang.contains("\"key.attuned.toggle_enemy_affinity_hud\""));
	}
}
```

Run:

```powershell
.\gradlew test --tests dev.attuned.client.AttunedClientConfigContractTest
```

Expected: FAIL because the file does not exist.

- [ ] **Step 2: Add failing CombatHud toggle contract**

Create `CombatHudSettingsContractTest.java`:

```java
package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CombatHudSettingsContractTest {
	private static final Path HUD_SOURCE =
		Path.of("src/client/java/dev/attuned/client/hud/CombatHud.java");

	@Test
	void combatHudReadsOwnAndEnemyVisibilitySeparately() throws IOException {
		String source = Files.readString(HUD_SOURCE, StandardCharsets.UTF_8);
		assertTrue(source.contains("AttunedClientConfig.get().showOwnAffinityHud()"));
		assertTrue(source.contains("AttunedClientConfig.get().showEnemyAffinityHud()"));
		assertTrue(source.contains("if (!showOwn && !showEnemy)"));
		assertTrue(source.contains("targetAffinity = Optional.empty()"));
		assertTrue(source.contains("showOwn"));
		assertTrue(source.contains("showEnemy"));
	}
}
```

Run:

```powershell
.\gradlew test --tests dev.attuned.client.CombatHudSettingsContractTest
```

Expected: FAIL because HUD does not read client config.

- [ ] **Step 3: Implement client config**

Create `AttunedClientConfig.java`:

```java
package dev.attuned.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public record AttunedClientConfig(boolean showOwnAffinityHud, boolean showEnemyAffinityHud) {
	public static final AttunedClientConfig DEFAULT = new AttunedClientConfig(true, true);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static AttunedClientConfig current = DEFAULT;

	public static AttunedClientConfig get() {
		return current;
	}

	public static void load() {
		Path path = path();
		if (!Files.isRegularFile(path)) {
			current = DEFAULT;
			save();
			return;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
			current = new AttunedClientConfig(
				bool(root, "show_own_affinity_hud", true),
				bool(root, "show_enemy_affinity_hud", true));
			save();
		} catch (RuntimeException | IOException ex) {
			current = DEFAULT;
			save();
		}
	}

	public static void setShowOwnAffinityHud(boolean value) {
		current = new AttunedClientConfig(value, current.showEnemyAffinityHud());
		save();
	}

	public static void setShowEnemyAffinityHud(boolean value) {
		current = new AttunedClientConfig(current.showOwnAffinityHud(), value);
		save();
	}

	public static void toggleOwnAffinityHud() {
		setShowOwnAffinityHud(!current.showOwnAffinityHud());
	}

	public static void toggleEnemyAffinityHud() {
		setShowEnemyAffinityHud(!current.showEnemyAffinityHud());
	}

	static void save() {
		try {
			Files.createDirectories(path().getParent());
			JsonObject root = new JsonObject();
			root.addProperty("show_own_affinity_hud", current.showOwnAffinityHud());
			root.addProperty("show_enemy_affinity_hud", current.showEnemyAffinityHud());
			Files.writeString(path(), GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
		}
	}

	private static boolean bool(JsonObject root, String key, boolean fallback) {
		return root.has(key) && root.get(key).isJsonPrimitive()
			? root.get(key).getAsBoolean()
			: fallback;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("attuned-client.json");
	}
}
```

- [ ] **Step 4: Load config in client init**

In `AttunedClient.init()` call the config before keybinds and HUD:

```java
AttunedClientConfig.load();
AttunedTooltips.init();
AttunedKeybinds.init();
CombatHud.init();
AltarScreens.init();
AttunementJournalScreen.initNetworking();
```

- [ ] **Step 5: Add unbound keybind toggles**

In `AttunedKeybinds`, add imports:

```java
import com.mojang.blaze3d.platform.InputConstants;
```

Add fields:

```java
private static KeyMapping toggleOwnHudKey;
private static KeyMapping toggleEnemyHudKey;
```

Register after `abilityKey`:

```java
toggleOwnHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
	"key.attuned.toggle_own_affinity_hud",
	InputConstants.UNKNOWN,
	KeyMapping.Category.GAMEPLAY));
toggleEnemyHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
	"key.attuned.toggle_enemy_affinity_hud",
	InputConstants.UNKNOWN,
	KeyMapping.Category.GAMEPLAY));
```

Consume them in the tick callback:

```java
while (toggleOwnHudKey.consumeClick()) {
	AttunedClientConfig.toggleOwnAffinityHud();
}
while (toggleEnemyHudKey.consumeClick()) {
	AttunedClientConfig.toggleEnemyAffinityHud();
}
```

- [ ] **Step 6: Gate CombatHud by config**

At the top of `draw(...)`, after player state reads:

```java
boolean showOwn = AttunedClientConfig.get().showOwnAffinityHud();
boolean showEnemy = AttunedClientConfig.get().showEnemyAffinityHud();
if (!showOwn && !showEnemy) {
	return;
}
```

Only resolve target affinity when `showEnemy` is true:

```java
LivingEntity target = showEnemy ? targetedLiving(minecraft, player) : null;
Optional<Affinity> targetAffinity = target == null ? Optional.empty() : MobAffinities.of(target);
if (!showEnemy) {
	targetAffinity = Optional.empty();
}
```

Change the unattuned skip gate to:

```java
if (showOwn && committed.isEmpty() && !discord && resonance <= 0.0F && targetAffinity.isEmpty()) {
	return;
}
if (!showOwn && targetAffinity.isEmpty()) {
	return;
}
```

Draw own sections only when `showOwn`:

```java
if (showOwn) {
	drawResonanceBar(graphics, screenW, rowY - BAR_GAP - RESONANCE_BAR_H, playerColor, resonance, apexArmed);
	drawGem(graphics, rowX, rowY, PLAYER_GEM_SIZE, playerAffinity.orElse(null), discord, false, apexArmed);
	if (matchup == Matchup.EMPOWERED) {
		drawEmpoweredHalo(graphics, rowX, rowY, PLAYER_GEM_SIZE);
	} else if (matchup == Matchup.NEUTRALIZED) {
		drawNeutralizedTint(graphics, rowX, rowY, PLAYER_GEM_SIZE);
	}
}
```

When `showOwn` is false and `showEnemy` is true, set `rowWidth = TARGET_GEM_SIZE` and draw only the target gem at `rowX`.

- [ ] **Step 7: Add language keys**

Add:

```json
"key.attuned.toggle_own_affinity_hud": "Toggle Own Affinity HUD",
"key.attuned.toggle_enemy_affinity_hud": "Toggle Enemy Affinity HUD",
```

- [ ] **Step 8: Verify HUD tests**

Run:

```powershell
.\gradlew test --tests dev.attuned.client.AttunedClientConfigContractTest --tests dev.attuned.client.CombatHudSettingsContractTest
```

Expected: PASS.

- [ ] **Step 9: Commit HUD settings**

Run:

```powershell
git add src/client/java/dev/attuned/client/AttunedClientConfig.java src/client/java/dev/attuned/client/AttunedClient.java src/client/java/dev/attuned/client/AttunedKeybinds.java src/client/java/dev/attuned/client/hud/CombatHud.java src/main/resources/assets/attuned/lang/en_us.json src/test/java/dev/attuned/client/AttunedClientConfigContractTest.java src/test/java/dev/attuned/client/CombatHudSettingsContractTest.java
git commit -m "Add affinity HUD toggles"
```

---

### Task 4: Reweaving Result Picker

**Files:**

- Create: `src/main/java/dev/attuned/content/ReweavingResultPicker.java`
- Create: `src/test/java/dev/attuned/content/ReweavingResultPickerTest.java`

- [ ] **Step 1: Write failing picker tests**

Create:

```java
package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.attuned.content.ReweavingResultPicker.Candidate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class ReweavingResultPickerTest {
	private static final List<Candidate> POOL = List.of(
		new Candidate("attuned:edge_focus", Optional.of("fury")),
		new Candidate("attuned:iron_focus", Optional.of("bastion")),
		new Candidate("attuned:swift_focus", Optional.of("zephyr")),
		new Candidate("attuned:forager_focus", Optional.empty()),
		new Candidate("attuned:linecast_focus", Optional.empty()));

	@Test
	void avoidsSacrificedIdsWhenAlternativesExist() {
		String result = ReweavingResultPicker.pick(
			POOL,
			Set.of("attuned:edge_focus", "attuned:iron_focus", "attuned:swift_focus"),
			Optional.empty(),
			new FixedRandom(0)).orElseThrow();

		assertNotEquals("attuned:edge_focus", result);
		assertNotEquals("attuned:iron_focus", result);
		assertNotEquals("attuned:swift_focus", result);
	}

	@Test
	void allowsSacrificedIdsOnlyWhenPoolHasNoAlternative() {
		String result = ReweavingResultPicker.pick(
			List.of(new Candidate("attuned:edge_focus", Optional.of("fury"))),
			Set.of("attuned:edge_focus"),
			Optional.empty(),
			new FixedRandom(0)).orElseThrow();

		assertEquals("attuned:edge_focus", result);
	}

	@Test
	void committedAffinityReceivesWeightBonusButDoesNotExcludeOthers() {
		List<ReweavingResultPicker.WeightedCandidate> weighted =
			ReweavingResultPicker.weightedCandidates(POOL, Set.of(), Optional.of("fury"));

		int fury = weighted.stream().filter(c -> c.id().equals("attuned:edge_focus")).findFirst().orElseThrow().weight();
		int neutral = weighted.stream().filter(c -> c.id().equals("attuned:forager_focus")).findFirst().orElseThrow().weight();
		int zephyr = weighted.stream().filter(c -> c.id().equals("attuned:swift_focus")).findFirst().orElseThrow().weight();
		assertTrue(fury > neutral);
		assertTrue(neutral > zephyr);
	}

	private record FixedRandom(int value) implements RandomGenerator {
		@Override public int nextInt(int bound) { return Math.floorMod(value, bound); }
		@Override public long nextLong() { return value; }
		@Override public boolean nextBoolean() { return false; }
		@Override public float nextFloat() { return 0.0F; }
		@Override public double nextDouble() { return 0.0; }
		@Override public double nextGaussian() { return 0.0; }
	}
}
```

Run:

```powershell
.\gradlew test --tests dev.attuned.content.ReweavingResultPickerTest
```

Expected: FAIL because the picker does not exist.

- [ ] **Step 2: Implement picker**

Create `ReweavingResultPicker.java`:

```java
package dev.attuned.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class ReweavingResultPicker {
	private static final int WEIGHT_MATCHING_COMMITTED = 4;
	private static final int WEIGHT_NEUTRAL = 2;
	private static final int WEIGHT_OTHER_AFFINITY = 1;

	private ReweavingResultPicker() {}

	public record Candidate(String id, Optional<String> affinity) {}
	public record WeightedCandidate(String id, int weight) {}

	public static Optional<String> pick(List<Candidate> pool, Set<String> sacrificedIds,
			Optional<String> committedAffinity, RandomGenerator random) {
		List<WeightedCandidate> weighted = weightedCandidates(pool, sacrificedIds, committedAffinity);
		if (weighted.isEmpty() && !sacrificedIds.isEmpty()) {
			weighted = weightedCandidates(pool, Set.of(), committedAffinity);
		}
		int total = weighted.stream().mapToInt(WeightedCandidate::weight).sum();
		if (total <= 0) {
			return Optional.empty();
		}
		int roll = random.nextInt(total);
		for (WeightedCandidate candidate : weighted) {
			roll -= candidate.weight();
			if (roll < 0) {
				return Optional.of(candidate.id());
			}
		}
		return Optional.empty();
	}

	static List<WeightedCandidate> weightedCandidates(List<Candidate> pool, Set<String> sacrificedIds,
			Optional<String> committedAffinity) {
		List<WeightedCandidate> weighted = new ArrayList<>();
		for (Candidate candidate : pool) {
			if (sacrificedIds.contains(candidate.id())) {
				continue;
			}
			weighted.add(new WeightedCandidate(candidate.id(), weight(candidate, committedAffinity)));
		}
		return weighted;
	}

	private static int weight(Candidate candidate, Optional<String> committedAffinity) {
		if (candidate.affinity().isEmpty()) {
			return WEIGHT_NEUTRAL;
		}
		if (committedAffinity.isPresent() && committedAffinity.get().equals(candidate.affinity().get())) {
			return WEIGHT_MATCHING_COMMITTED;
		}
		return WEIGHT_OTHER_AFFINITY;
	}
}
```

- [ ] **Step 3: Verify picker tests**

Run:

```powershell
.\gradlew test --tests dev.attuned.content.ReweavingResultPickerTest
```

Expected: PASS.

- [ ] **Step 4: Commit picker**

Run:

```powershell
git add src/main/java/dev/attuned/content/ReweavingResultPicker.java src/test/java/dev/attuned/content/ReweavingResultPickerTest.java
git commit -m "Add Reweaving result picker"
```

---

### Task 5: Altar Of Reweaving Content, Menu, Networking, And Screen

**Files:**

- Create and modify the Reweaving files listed in the file map.
- Modify: `src/main/java/dev/attuned/Attuned.java`
- Modify: `src/main/java/dev/attuned/content/AttunedContent.java`
- Modify: `src/client/java/dev/attuned/client/screen/AltarScreens.java`
- Modify: `src/main/resources/assets/attuned/lang/en_us.json`
- Create: `src/test/java/dev/attuned/content/ReweavingContentContractTest.java`

- [ ] **Step 1: Add failing content contract**

Create `ReweavingContentContractTest.java`:

```java
package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReweavingContentContractTest {
	private static final Path CONTENT_SOURCE =
		Path.of("src/main/java/dev/attuned/content/AttunedContent.java");
	private static final Path ATTUNED_INIT_SOURCE =
		Path.of("src/main/java/dev/attuned/Attuned.java");
	private static final Path MENU_TYPE_SOURCE =
		Path.of("src/main/java/dev/attuned/menu/ReweavingMenuType.java");
	private static final Path NETWORKING_SOURCE =
		Path.of("src/main/java/dev/attuned/menu/ReweavingNetworking.java");
	private static final Path SCREEN_REGISTRATION_SOURCE =
		Path.of("src/client/java/dev/attuned/client/screen/AltarScreens.java");
	private static final Path LANG_FILE =
		Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void altarOfReweavingIsRegisteredAsSeparateBlockMenuAndNetworkPath() throws IOException {
		assertTrue(Files.readString(CONTENT_SOURCE, StandardCharsets.UTF_8).contains("ALTAR_OF_REWEAVING"));
		assertTrue(Files.readString(CONTENT_SOURCE, StandardCharsets.UTF_8).contains("\"altar_of_reweaving\""));
		assertTrue(Files.readString(ATTUNED_INIT_SOURCE, StandardCharsets.UTF_8).contains("ReweavingMenuType.init()"));
		assertTrue(Files.readString(ATTUNED_INIT_SOURCE, StandardCharsets.UTF_8).contains("ReweavingNetworking.init()"));
		assertTrue(Files.readString(MENU_TYPE_SOURCE, StandardCharsets.UTF_8).contains("new MenuType<>(ReweavingMenu::new"));
		assertTrue(Files.readString(NETWORKING_SOURCE, StandardCharsets.UTF_8).contains("ReweavePayload.TYPE"));
		assertTrue(Files.readString(SCREEN_REGISTRATION_SOURCE, StandardCharsets.UTF_8).contains("ReweavingMenuType.TYPE"));
	}

	@Test
	void altarOfReweavingHasDataAssetsAndLanguage() throws IOException {
		assertTrue(Files.isRegularFile(Path.of("src/main/resources/data/attuned/recipe/altar_of_reweaving.json")));
		assertTrue(Files.isRegularFile(Path.of("src/main/resources/data/attuned/loot_table/blocks/altar_of_reweaving.json")));
		assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/attuned/blockstates/altar_of_reweaving.json")));
		assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/attuned/models/block/altar_of_reweaving.json")));
		assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/attuned/models/item/altar_of_reweaving.json")));
		assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/attuned/textures/gui/altar_of_reweaving.png")));
		String lang = Files.readString(LANG_FILE, StandardCharsets.UTF_8);
		assertTrue(lang.contains("\"item.attuned.altar_of_reweaving\""));
		assertTrue(lang.contains("\"container.attuned.reweaving_altar\""));
		assertTrue(lang.contains("\"screen.attuned.reweaving_altar.reweave\""));
	}
}
```

Run:

```powershell
.\gradlew test --tests dev.attuned.content.ReweavingContentContractTest
```

Expected: FAIL because files are missing.

- [ ] **Step 2: Register the block in `AttunedContent`**

Add:

```java
public static final Block ALTAR_OF_REWEAVING = registerReweavingAltar();
```

Add registration method:

```java
private static Block registerReweavingAltar() {
	Identifier id = Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "altar_of_reweaving");
	ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
	Block block = new AltarOfReweavingBlock(BlockBehaviour.Properties.of()
		.setId(blockKey)
		.strength(3.5F, 6.0F)
		.sound(SoundType.DEEPSLATE)
		.lightLevel(state -> 6)
		.noOcclusion());
	Registry.register(BuiltInRegistries.BLOCK, blockKey, block);

	ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
	Registry.register(BuiltInRegistries.ITEM, itemKey,
		new BlockItem(block, new Item.Properties().setId(itemKey)));
	return block;
}
```

Add `output.accept(ALTAR_OF_REWEAVING);` after `ATTUNEMENT_ALTAR`.

- [ ] **Step 3: Add `AltarOfReweavingBlock`**

Create a block that mirrors the existing altar shape and opens the new menu:

```java
package dev.attuned.content;

import com.mojang.serialization.MapCodec;
import dev.attuned.menu.ReweavingMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AltarOfReweavingBlock extends Block {
	public static final MapCodec<AltarOfReweavingBlock> CODEC = simpleCodec(AltarOfReweavingBlock::new);
	private static final VoxelShape SHAPE = Shapes.or(
		Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0),
		Block.box(1.0, 2.0, 1.0, 15.0, 4.0, 15.0),
		Block.box(2.0, 4.0, 2.0, 14.0, 14.0, 14.0),
		Block.box(1.0, 14.0, 1.0, 15.0, 16.0, 15.0));

	public AltarOfReweavingBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
			Player player, BlockHitResult hitResult) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		player.openMenu(ReweavingMenuType.provider(level, pos));
		return InteractionResult.SUCCESS_SERVER;
	}
}
```

- [ ] **Step 4: Add menu type and payload init**

In `Attuned.java`, import and call:

```java
ReweavingMenuType.init();
ReweavingNetworking.init();
```

Place those next to the existing altar menu/networking init calls.

Create `ReweavingMenuType.java` patterned after `AltarMenuType`, with:

```java
public static MenuType<ReweavingMenu> TYPE;
public static final Component DISPLAY_NAME =
	Component.translatable("container.attuned.reweaving_altar");
```

Register id `attuned:altar_of_reweaving` and provider:

```java
new ReweavingMenu(containerId, inventory, new SimpleContainer(ReweavingMenu.CONTAINER_SIZE), access)
```

Create `ReweavePayload.java`:

```java
package dev.attuned.menu;

import dev.attuned.Attuned;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ReweavePayload() implements CustomPacketPayload {
	public static final Type<ReweavePayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "reweave"));
	public static final StreamCodec<FriendlyByteBuf, ReweavePayload> CODEC =
		StreamCodec.unit(new ReweavePayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
```

- [ ] **Step 5: Add `ReweavingMenu`**

Create a menu with constants:

```java
public static final int FOCUS_INPUTS = 3;
public static final int CATALYST_SLOT = 3;
public static final int OUTPUT_SLOT = 4;
public static final int CONTAINER_SIZE = 5;
public static final int INPUT_START = 0;
public static final int INVENTORY_X = 27;
public static final int INVENTORY_Y = 108;
```

Slot rules:

```java
new Slot(container, index, x, y) {
	@Override
	public boolean mayPlace(ItemStack stack) {
		return AttunedContent.FOCI.contains(stack.getItem());
	}
}
```

Catalyst:

```java
return stack.is(AttunedContent.ATTUNEMENT_SHARD_FRAGMENT);
```

Output:

```java
@Override public boolean mayPlace(ItemStack stack) { return false; }
```

Expose:

```java
public Container container() { return this.container; }
public ContainerLevelAccess access() { return this.access; }
public boolean hasAllInputs() { ... }
public ItemStack outputStack() { return this.container.getItem(OUTPUT_SLOT); }
```

Implement `removed(Player player)` to return all five container slots to the player, excluding empty stacks.

- [ ] **Step 6: Add `ReweavingNetworking`**

Create server-side handler:

```java
PayloadTypeRegistry.serverboundPlay().register(ReweavePayload.TYPE, ReweavePayload.CODEC);
ServerPlayNetworking.registerGlobalReceiver(ReweavePayload.TYPE, (payload, context) -> {
	ServerPlayer player = context.player();
	player.level().getServer().execute(() -> tryReweave(player));
});
```

Validation inside `tryReweave`:

```java
if (!(player.containerMenu instanceof ReweavingMenu menu)) return;
menu.access().execute((level, pos) -> {
	if (!(level instanceof ServerLevel serverLevel)) return;
	if (!serverLevel.getBlockState(pos).is(AttunedContent.ALTAR_OF_REWEAVING)) return;
	if (!player.isWithinBlockInteractionRange(pos, 4.0)) return;
	Container c = menu.container();
	if (!hasThreeFociAndFragment(c)) return;
	if (!c.getItem(ReweavingMenu.OUTPUT_SLOT).isEmpty()) return;
	ItemStack result = rollResult(player, serverLevel, c);
	if (result.isEmpty()) return;
	for (int i = 0; i < ReweavingMenu.FOCUS_INPUTS; i++) c.getItem(i).shrink(1);
	c.getItem(ReweavingMenu.CATALYST_SLOT).shrink(1);
	c.setItem(ReweavingMenu.OUTPUT_SLOT, result);
	menu.broadcastChanges();
});
```

Map registry definitions to picker candidates:

```java
List<ReweavingResultPicker.Candidate> candidates =
	registry.listElements()
		.map(holder -> holder.value())
		.map(def -> new ReweavingResultPicker.Candidate(
			BuiltInRegistries.ITEM.getKey(def.item().value()).toString(),
			def.affinity().map(affinity -> affinity.getSerializedName())))
		.toList();
```

Resolve the result id back to an item through `BuiltInRegistries.ITEM.get(Identifier.parse(id))`.

- [ ] **Step 7: Add screen registration and `ReweavingScreen`**

In `AltarScreens.init()`:

```java
MenuScreens.register(ReweavingMenuType.TYPE, ReweavingScreen::new);
```

Create `ReweavingScreen` by adapting `AltarScreen` with these differences:

- texture `textures/gui/altar_of_reweaving.png`
- button label `screen.attuned.reweaving_altar.reweave`
- send `ClientPlayNetworking.send(new ReweavePayload())`
- active button only when `menu.hasAllInputs()` and output slot is empty
- status labels for missing Foci, missing catalyst, output blocked, ready

- [ ] **Step 8: Add data and language assets**

Create recipe:

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    "SDS",
    "DLD",
    "SFS"
  ],
  "key": {
    "S": "minecraft:string",
    "D": "minecraft:polished_deepslate",
    "L": "minecraft:loom",
    "F": "attuned:attunement_shard_fragment"
  },
  "result": {
    "id": "attuned:altar_of_reweaving"
  }
}
```

Create block loot table:

```json
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "attuned:altar_of_reweaving"
        }
      ],
      "conditions": [
        {
          "condition": "minecraft:survives_explosion"
        }
      ]
    }
  ]
}
```

Add language:

```json
"item.attuned.altar_of_reweaving": "Altar of Reweaving",
"item.attuned.altar_of_reweaving.lore": "Worked deepslate, amethyst dust, and the patience to remember differently.",
"item.attuned.altar_of_reweaving.lore2": "Its basin turns old patterns until they catch another light.",
"item.attuned.altar_of_reweaving.effect": "Consumes three Foci and a Shard Fragment to reweave one new Focus.",
"container.attuned.reweaving_altar": "Altar of Reweaving",
"screen.attuned.reweaving_altar.reweave": "Reweave",
"screen.attuned.reweaving_altar.hint.missing_foci": "Add three Foci.",
"screen.attuned.reweaving_altar.hint.missing_fragment": "Add a Shard Fragment.",
"screen.attuned.reweaving_altar.hint.output_blocked": "Take the result first.",
"screen.attuned.reweaving_altar.hint.ready": "Change the nature of the natural forces."
```

- [ ] **Step 9: Generate final block and GUI textures**

Use the committed concept image `docs/superpowers/assets/reweaving-seafarers/altar-of-reweaving-concept.png` as the visual reference and produce Minecraft-ready PNGs at the exact resource paths below. The final assets must be committed to resource paths, not left in `docs/superpowers/assets`.

Required dimensions:

- `altar_of_reweaving.png`: 216x190
- `altar_of_reweaving_base.png`: 16x16
- `altar_of_reweaving_top.png`: 16x16
- `altar_of_reweaving_gem.png`: 16x16

Run:

```powershell
.\gradlew test --tests dev.attuned.client.UiAssetContractTest --tests dev.attuned.content.ReweavingContentContractTest
```

Expected: PASS after `UiAssetContractTest` includes the new GUI texture assertion.

- [ ] **Step 10: Verify and commit Reweaving altar**

Run:

```powershell
.\gradlew test --tests dev.attuned.content.ReweavingContentContractTest --tests dev.attuned.content.ReweavingResultPickerTest
```

Expected: PASS.

Commit:

```powershell
git add src/main/java/dev/attuned/Attuned.java src/main/java/dev/attuned/content/AttunedContent.java src/main/java/dev/attuned/content/AltarOfReweavingBlock.java src/main/java/dev/attuned/menu/ReweavingMenu.java src/main/java/dev/attuned/menu/ReweavingMenuType.java src/main/java/dev/attuned/menu/ReweavePayload.java src/main/java/dev/attuned/menu/ReweavingNetworking.java src/client/java/dev/attuned/client/screen/AltarScreens.java src/client/java/dev/attuned/client/screen/ReweavingScreen.java src/main/resources/data/attuned/recipe/altar_of_reweaving.json src/main/resources/data/attuned/loot_table/blocks/altar_of_reweaving.json src/main/resources/assets/attuned src/test/java/dev/attuned/content/ReweavingContentContractTest.java src/test/java/dev/attuned/client/UiAssetContractTest.java
git commit -m "Add Altar of Reweaving"
```

---

### Task 6: Seafarers Foci, Behaviors, Fishing Mixin, And Loot Bias

**Files:**

- Modify: `src/main/java/dev/attuned/content/AttunedContent.java`
- Modify: `src/main/java/dev/attuned/content/AttunedLoot.java`
- Modify: `src/main/resources/attuned.mixins.json`
- Create: `src/main/java/dev/attuned/content/behavior/HarborlightBehavior.java`
- Create: `src/main/java/dev/attuned/content/behavior/DriftglassBehavior.java`
- Create: `src/main/java/dev/attuned/content/behavior/SeafarersFishing.java`
- Create: `src/main/java/dev/attuned/mixin/FishingHookMixin.java`
- Modify: `src/test/java/dev/attuned/content/FocusDataConsistencyTest.java`
- Modify: `src/test/java/dev/attuned/content/AttunedLootCompatibilityTest.java`
- Add all Seafarers data/assets/lang files.

- [ ] **Step 1: Extend tests for Seafarers**

In `FocusDataConsistencyTest`, add:

```java
private static final Set<String> SEAFARERS_FOCUS_ITEMS = Set.of(
	"attuned:driftglass_focus",
	"attuned:harborlight_focus",
	"attuned:linecast_focus",
	"attuned:netmender_focus");
```

Add test:

```java
@Test
void seafarersFociStayNeutralTranslatedAndNonCombat() throws IOException {
	Set<String> seafarersItems = new TreeSet<>();
	try (Stream<Path> paths = Files.list(FOCUS_DATA_DIR)) {
		for (Path file : paths.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList()) {
			JsonObject root = focusDefinitionRoot(file);
			JsonElement faction = root.get("faction");
			if (faction == null || !"attuned:seafarers".equals(faction.getAsString())) {
				continue;
			}
			seafarersItems.add(root.get("item").getAsString());
			assertTrue(!root.has("affinity"), "Seafarers Foci must stay neutral: " + file);
			assertTrue(!root.has("modifiers"), "Seafarers first wave must not add combat modifiers: " + file);
		}
	}
	assertEquals(SEAFARERS_FOCUS_ITEMS, seafarersItems);
}
```

In `AttunedLootCompatibilityTest`, add an assertion that fishing treasure exists and Seafarers faction gets a higher fishing weight than neutral non-faction when the drop is fishing-themed.

Run:

```powershell
.\gradlew test --tests dev.attuned.content.FocusDataConsistencyTest --tests dev.attuned.content.AttunedLootCompatibilityTest
```

Expected: FAIL until Seafarers data and loot weighting exist.

- [ ] **Step 2: Register Seafarers items and behaviors**

In `AttunedContent`, add fields:

```java
public static final Item LINECAST_FOCUS = register("linecast_focus");
public static final Item NETMENDER_FOCUS = register("netmender_focus");
public static final Item HARBORLIGHT_FOCUS = register("harborlight_focus");
public static final Item DRIFTGLASS_FOCUS = register("driftglass_focus");
```

Add them to `FOCI` near other neutral utility Foci.

Register behaviors:

```java
AttunedRegistries.registerBehavior(
	Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "harborlight"), new HarborlightBehavior());
AttunedRegistries.registerBehavior(
	Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "driftglass"), new DriftglassBehavior());
```

`Linecast` and `Netmender` are handled by `SeafarersFishing` through the fishing mixin and do not need behavior ids.

- [ ] **Step 3: Add Seafarers FocusDefinition JSON**

Create `linecast_focus.json`:

```json
{
  "item": "attuned:linecast_focus",
  "cost": 2,
  "unique": true,
  "faction": "attuned:seafarers"
}
```

Create `netmender_focus.json`:

```json
{
  "item": "attuned:netmender_focus",
  "cost": 2,
  "unique": true,
  "faction": "attuned:seafarers"
}
```

Create `harborlight_focus.json`:

```json
{
  "item": "attuned:harborlight_focus",
  "cost": 2,
  "unique": true,
  "faction": "attuned:seafarers",
  "behavior": "attuned:harborlight"
}
```

Create `driftglass_focus.json`:

```json
{
  "item": "attuned:driftglass_focus",
  "cost": 3,
  "unique": true,
  "faction": "attuned:seafarers",
  "behavior": "attuned:driftglass"
}
```

- [ ] **Step 4: Implement Harborlight**

Create:

```java
package dev.attuned.content.behavior;

import dev.attuned.AttunedPlayerCleanup;
import dev.attuned.api.focus.FocusBehavior;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class HarborlightBehavior implements FocusBehavior {
	private static final int CHECK_INTERVAL = 40;
	private static final int DURATION = 80;
	private static final int MAX_LIGHT = 10;
	private final Map<UUID, Integer> ticks = new HashMap<>();

	public HarborlightBehavior() {
		AttunedPlayerCleanup.onForget(ticks::remove);
	}

	@Override
	public void onTick(ServerPlayer player, ItemStack focus) {
		UUID id = player.getUUID();
		int tick = ticks.getOrDefault(id, 0) + 1;
		if (tick < CHECK_INTERVAL) {
			ticks.put(id, tick);
			return;
		}
		ticks.put(id, 0);
		if (!player.isInWaterOrRain() && !nearWater(player)) {
			return;
		}
		if (!holdsLantern(player) || player.level().getMaxLocalRawBrightness(player.blockPosition()) > MAX_LIGHT) {
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, DURATION, 0, true, false, false));
	}

	@Override
	public void onDeactivate(ServerPlayer player, ItemStack focus) {
		ticks.remove(player.getUUID());
	}

	private static boolean holdsLantern(ServerPlayer player) {
		return isLantern(player.getMainHandItem().getItem()) || isLantern(player.getOffhandItem().getItem());
	}

	private static boolean isLantern(Item item) {
		return item == Items.LANTERN || item == Items.SOUL_LANTERN;
	}

	private static boolean nearWater(ServerPlayer player) {
		return player.level().getFluidState(player.blockPosition()).is(net.minecraft.tags.FluidTags.WATER)
			|| player.level().getFluidState(player.blockPosition().below()).is(net.minecraft.tags.FluidTags.WATER);
	}
}
```

- [ ] **Step 5: Implement Driftglass**

Use the compass snapshot pattern from `WaystoneBehavior`, but store one `GlobalPos` per player when boating or fishing. Use `net.minecraft.world.entity.vehicle.boat.AbstractBoat`.

Core tracking rule:

```java
if (player.getVehicle() instanceof AbstractBoat || player.fishing != null) {
	points.put(player.getUUID(), GlobalPos.of(player.level().dimension(), player.blockPosition()));
}
```

Apply a lodestone tracker to held compasses when a stored point exists, name it `item.attuned.driftglass_compass`, and restore original trackers/names on deactivate/disconnect.

- [ ] **Step 6: Implement Seafarers fishing hook**

Create `SeafarersFishing.java` with:

```java
public static int afterRetrieve(ServerPlayer player, ItemStack rod, int damage) {
	if (damage != 1) {
		return damage;
	}
	if (hasActive(player, AttunedContent.LINECAST_FOCUS) && player.getRandom().nextFloat() < 0.20F) {
		ItemEntity extra = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(),
			new ItemStack(Items.COD));
		player.level().addFreshEntity(extra);
	}
	if (hasActive(player, AttunedContent.NETMENDER_FOCUS) && player.getRandom().nextFloat() < 0.35F) {
		return Math.max(0, damage - 1);
	}
	return damage;
}
```

Add `hasActive(ServerPlayer, Item)` by scanning `AttunedAttachments.getInventory(player)` and `Attunement.activeSlots(player)`.

Create `FishingHookMixin.java`:

```java
package dev.attuned.mixin;

import dev.attuned.content.behavior.SeafarersFishing;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin {
	@Inject(method = "retrieve", at = @At("RETURN"), cancellable = true)
	private void attuned$seafarersRetrieve(ItemStack rod, CallbackInfoReturnable<Integer> cir) {
		FishingHook hook = (FishingHook) (Object) this;
		Player owner = hook.getPlayerOwner();
		if (owner instanceof ServerPlayer serverPlayer) {
			cir.setReturnValue(SeafarersFishing.afterRetrieve(serverPlayer, rod, cir.getReturnValue()));
		}
	}
}
```

Add `"FishingHookMixin"` to `attuned.mixins.json`.

- [ ] **Step 7: Add Seafarers loot weighting**

In `AttunedLoot`, add:

```java
private static final Identifier SEAFARERS_FACTION =
	Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "seafarers");
private static final int WEIGHT_SEAFARERS_FISHING_BONUS = 3;
```

Extend `Drop` to include `boolean fishingTheme`:

```java
record Drop(Tier tier, Affinity theme, boolean unseenTheme, boolean fishingTheme) {}
```

Update constructors and make fishing treasure:

```java
Map.entry(vanilla("gameplay/fishing/treasure"), fishing(Tier.LOW))
```

Add:

```java
private static Drop fishing(Tier tier) {
	return new Drop(tier, null, false, true);
}
```

In `weightForMeta`, add:

```java
if (drop.fishingTheme() && SEAFARERS_FACTION.equals(faction)) {
	weight += WEIGHT_SEAFARERS_FISHING_BONUS;
}
```

- [ ] **Step 8: Add Seafarers item assets and language**

Add language:

```json
"faction.attuned.seafarers": "Seafarers",
"item.attuned.linecast_focus": "Linecast Focus",
"item.attuned.linecast_focus.lore": "They carry no banner inland; the tide is witness enough.",
"item.attuned.linecast_focus.lore2": "A patient hook remembers generous water.",
"item.attuned.linecast_focus.effect": "Fish catches may bring up one extra ordinary fish.",
"item.attuned.netmender_focus": "Netmender Focus",
"item.attuned.netmender_focus.lore": "Salt in the hinge, starlight in the glass.",
"item.attuned.netmender_focus.lore2": "A careful knot keeps the tool from wearing thin.",
"item.attuned.netmender_focus.effect": "Fish catches may prevent a point of rod damage.",
"item.attuned.harborlight_focus": "Harborlight Focus",
"item.attuned.harborlight_focus.lore": "A dock light kept for anyone still on the water.",
"item.attuned.harborlight_focus.lore2": "It guides without calling for war.",
"item.attuned.harborlight_focus.effect": "Near water at night, holding a lantern grants brief night vision.",
"item.attuned.driftglass_focus": "Driftglass Focus",
"item.attuned.driftglass_focus.lore": "Lost is a direction, not a verdict.",
"item.attuned.driftglass_focus.lore2": "The tide writes the way back in glass.",
"item.attuned.driftglass_focus.effect": "A held compass points to your latest fishing or boating return point.",
"item.attuned.driftglass_compass": "Driftglass Compass"
```

Create item definitions/models following existing Focus files. Use the committed concept image `docs/superpowers/assets/reweaving-seafarers/seafarers-focus-concepts.png` as the visual reference and generate these exact `64x512` animated PNGs:

- `src/main/resources/assets/attuned/textures/item/linecast_focus.png`
- `src/main/resources/assets/attuned/textures/item/netmender_focus.png`
- `src/main/resources/assets/attuned/textures/item/harborlight_focus.png`
- `src/main/resources/assets/attuned/textures/item/driftglass_focus.png`

Add a matching `.png.mcmeta` for each texture with:

```json
{
  "animation": {
    "frametime": 2,
    "interpolate": true
  }
}
```

- [ ] **Step 9: Verify and commit Seafarers**

Run:

```powershell
.\gradlew test --tests dev.attuned.content.FocusDataConsistencyTest --tests dev.attuned.content.AttunedLootCompatibilityTest
```

Expected: PASS.

Commit:

```powershell
git add src/main/java/dev/attuned/content/AttunedContent.java src/main/java/dev/attuned/content/AttunedLoot.java src/main/java/dev/attuned/content/behavior/HarborlightBehavior.java src/main/java/dev/attuned/content/behavior/DriftglassBehavior.java src/main/java/dev/attuned/content/behavior/SeafarersFishing.java src/main/java/dev/attuned/mixin/FishingHookMixin.java src/main/resources/attuned.mixins.json src/main/resources/data/attuned/attuned/focus src/main/resources/assets/attuned src/test/java/dev/attuned/content/FocusDataConsistencyTest.java src/test/java/dev/attuned/content/AttunedLootCompatibilityTest.java
git commit -m "Add Seafarers fishing Foci"
```

---

### Task 7: Journal Lore Pages

**Files:**

- Modify: `src/client/java/dev/attuned/client/screen/AttunementJournalScreen.java`
- Modify: `src/main/java/dev/attuned/content/AttunementJournalItem.java`
- Modify: `src/main/resources/assets/attuned/lang/en_us.json`
- Modify: `src/test/java/dev/attuned/content/AttunementJournalUiContractTest.java`

- [ ] **Step 1: Add journal page sync assertions**

Extend `AttunementJournalUiContractTest` to assert:

```java
assertTrue(screenSource.contains("journal.attuned.page20"));
assertTrue(screenSource.contains("journal.attuned.page28"));
assertTrue(lang.contains("\"journal.attuned.page20\""));
assertTrue(lang.contains("\"journal.attuned.page28\""));
```

Run:

```powershell
.\gradlew test --tests dev.attuned.content.AttunementJournalUiContractTest
```

Expected: FAIL until pages exist.

- [ ] **Step 2: Add pages to custom screen and written book fallback**

In `AttunementJournalScreen.PAGES`, append:

```java
new Page("Lore", "journal.attuned.page20", 0xFFAEEAFF, null),
new Page("Lore", "journal.attuned.page21", 0xFFAEEAFF, null),
new Page("Lore", "journal.attuned.page22", 0xFFAEEAFF, null),
new Page("Radiant", "journal.attuned.page23", 0xFFFFD37A, null),
new Page("Unseen", "journal.attuned.page24", 0xFFB995FF, null),
new Page("Lore", "journal.attuned.page25", 0xFFFFD37A, null),
new Page("Seafarers", "journal.attuned.page26", 0xFF70D7FF, null),
new Page("Seafarers", "journal.attuned.page27", 0xFF70D7FF, null),
new Page("HUD", "journal.attuned.page28", 0xFF95E6B3, null)
```

Add chapters:

```java
new Chapter("Lore", 19),
new Chapter("Radiant", 22),
new Chapter("Seafarers", 25),
new Chapter("HUD", 27)
```

In `AttunementJournalItem.createGuideContent()`, add translatable pages 20 through 28 to the written book page list.

- [ ] **Step 3: Add journal copy**

Add concise page strings:

```json
"journal.attuned.page20": "The Altar of Reweaving\n\nSome altars do not deepen the soul's room.\n\nThey turn an old pattern until it catches the light differently.",
"journal.attuned.page21": "What Reweaving Is Not\n\nReweaving does not make a Focus louder, stronger, or freer.\n\nIt changes the way a bound pattern is remembered.",
"journal.attuned.page22": "Altar Memory\n\nEvery altar keeps a trace of what was last bound there.\n\nReweaving listens to that trace before it answers.",
"journal.attuned.page23": "The Radiant\n\nThe Radiant keep vows in light.\n\nThey believe power is safest when named, witnessed, and remembered.",
"journal.attuned.page24": "The Unseen\n\nThe Unseen keep masks and hidden roads.\n\nThey believe power is safest when no one can claim or own it.",
"journal.attuned.page25": "Witness and Veil\n\nRadiant and Unseen oppose each other like lantern and shadow.\n\nBoth exist to keep attunement from becoming careless.",
"journal.attuned.page26": "The Seafarers\n\nThe Seafarers carry fragments, journals, and half-remembered routes from shore to shore.\n\nThey trade in guidance, not conquest.",
"journal.attuned.page27": "Tide Records\n\nA Seafarer's best map is return.\n\nWhere a wreck sank. Where a bell was heard. Where a shard washed clean.",
"journal.attuned.page28": "Reading the HUD\n\nThe marks near your hand are not commands from the altar.\n\nThey are the shape your active Foci make when the world pushes back."
```

- [ ] **Step 4: Verify and commit journal lore**

Run:

```powershell
.\gradlew test --tests dev.attuned.content.AttunementJournalUiContractTest
```

Expected: PASS.

Commit:

```powershell
git add src/client/java/dev/attuned/client/screen/AttunementJournalScreen.java src/main/java/dev/attuned/content/AttunementJournalItem.java src/main/resources/assets/attuned/lang/en_us.json src/test/java/dev/attuned/content/AttunementJournalUiContractTest.java
git commit -m "Expand journal lore"
```

---

### Task 8: Asset Contract Updates

**Files:**

- Modify: `src/test/java/dev/attuned/client/UiAssetContractTest.java`
- Add final PNG assets listed in prior tasks.

- [ ] **Step 1: Extend UI asset assertions**

Add:

```java
assertPngSize("gui/altar_of_reweaving.png", 216, 190);
```

Add source wiring:

```java
private static final Path REWEAVING_SCREEN_SOURCE =
	Path.of("src/client/java/dev/attuned/client/screen/ReweavingScreen.java");
```

And:

```java
assertSourceContains(REWEAVING_SCREEN_SOURCE, "textures/gui/altar_of_reweaving.png");
```

Run:

```powershell
.\gradlew test --tests dev.attuned.client.UiAssetContractTest
```

Expected: PASS once assets and screen exist.

- [ ] **Step 2: Check generated assets are project-bound**

Run:

```powershell
Get-ChildItem -Path src\main\resources\assets\attuned\textures -Recurse -Include *reweaving*,*linecast*,*netmender*,*harborlight*,*driftglass* | Select-Object FullName,Length
```

Expected: final assets exist under `src/main/resources/assets/attuned/textures`, not only under `.codex` or `docs/superpowers/assets`.

---

### Task 9: Full Verification

**Files:**

- All touched files
- No new edits unless tests fail

- [ ] **Step 1: Run full tests**

Run:

```powershell
.\gradlew test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Build the mod jar**

Run:

```powershell
.\gradlew build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Source scan for accidental scope creep**

Run:

```powershell
rg -n "ModMenu|Cloth|YACL|damage.*Seafarers|seafarers.*damage|TODO|TBD" src docs
```

Expected: no hard settings dependencies, no Seafarers combat damage text, and no TODO/TBD left in implementation files.

- [ ] **Step 4: Manual client smoke test**

Run the development client:

```powershell
.\gradlew runClient
```

Manual checks:

- Creative tab contains Altar of Reweaving and four Seafarers Foci.
- Altar of Reweaving opens its own screen.
- Reweave button stays disabled until 3 Foci and 1 Shard Fragment are present.
- Reweaving consumes inputs and produces one Focus.
- Closing the menu returns all unconsumed inputs.
- Own HUD toggle hides and restores the player affinity HUD.
- Enemy HUD toggle hides and restores target affinity display and matchup hints.
- Journal buttons are visible, clickable, and not overlapped by progress text.
- Journal pages 20-28 render without text overflow.
- Fishing with Seafarers Foci gives peaceful utility only.

- [ ] **Step 5: Final commit**

If Task 9 needed any fixes, commit them:

```powershell
git add .
git commit -m "Verify reweaving expansion"
```

If no fixes were needed after the prior task commits, do not create an empty commit.

---

## Execution Strategy

Use parallel workstreams with disjoint ownership:

- Stream 1: Task 2 journal button rendering and Task 7 journal lore.
- Stream 2: Task 3 HUD config/keybinds/HUD renderer.
- Stream 3: Task 4 and Task 5 Reweaving picker/block/menu/network/screen.
- Stream 4: Task 6 Seafarers Foci/fishing/loot/assets.
- Integrator: merge streams, resolve conflicts, run Task 8 and Task 9 verification.

Each stream must preserve changes from other streams and must not revert unrelated edits.
