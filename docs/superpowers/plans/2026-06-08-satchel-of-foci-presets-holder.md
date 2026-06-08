# Satchel of Foci, Presets, and Foci Holder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship three connected systems for managing Focus accessories: (1) a reusable **foci holder** value type that a new bag serializes through (parameterizing the size/cap that `AttunedInv` hard-codes); (2) the **Satchel of Foci**, a craftable `stacksTo(1)` bag whose contents live in the codebase's first custom `DataComponentType<FocusHolder>`, with its own screen for moving Foci to/from the six equipped slots and a verified drop/death round-trip; and (3) **Presets**, per-player named loadouts (captured by item-registry id) that re-equip by sourcing real Focus stacks from the satchel and player inventory, with item conservation, missing-focus reporting, and over-capacity tolerance.

**Architecture:** Introduce an immutable, codec-backed `FocusHolder(int size, int maxPerSlot, List<ItemStack> items)` for the satchel. **`AttunedInv` is left entirely unchanged** — it keeps its own self-contained `sizedItems`/`copyItems`/`copyStack`/`get`/`with`/`items`/`requireSlot` bodies and the literal `copy.setCount(Math.min(copy.getCount(), 1));` cap, because `AttunedInvTest` (src/test/java/dev/attuned/attunement/AttunedInvTest.java) pins those exact source strings and `FocusSlotContractTest`/`AttunedAttachmentsContractTest` pin its public surface. `FocusHolder` is a *parallel* generalization (shared logic by intent, not delegation); it carries its own parameterized cap `Math.max(1, maxPerSlot)`. The Satchel stores contents in a `DataComponentType<FocusHolder>` registered **inside** an idempotent `AttunedComponents.init()` (mirroring `AltarMenuType` so the `initialized` guard is real, not cosmetic), wired into `Attuned.onInitialize` strictly before `AttunedContent.init()`. A new `SatchelMenu` (item-opened, no `ContainerLevelAccess`) exposes a component-backed `SatchelContainer` that **reads the held satchel live via the player's `InteractionHand`** (never a cached `ItemStack`) so writes can never land on a stale instance. A serverbound `MoveFocusPayload`/`SatchelNetworking` swaps Foci between satchel and equipped slots, validated entirely server-side, with the pure decision logic extracted into a Minecraft-free `SatchelMoveResolver` so it gets real red→green behavioral tests. Presets live in a new persistent + `targetOnly`-synced + `copyOnDeath` `PRESETS` list attachment (the codebase's first *synced* list attachment); save/apply/delete run server-side, with apply's transactional conservation/sourcing/missing logic extracted into a Minecraft-free `PresetApplicationResolver`. Over-capacity foci are equipped anyway and resolve dormant via the existing read-only budget policy.

**Tech Stack:** Fabric API attachments/networking/menu-api/creative-tab/component registration, Minecraft 26.1.2 (`DataComponentType`, `MenuType`, `AbstractContainerScreen`, this fork's `GuiGraphicsExtractor` render hooks, `ItemStack.OPTIONAL_CODEC`/`OPTIONAL_LIST_STREAM_CODEC`), Java 25 records, JUnit 5 source-grep + Minecraft-free behavioral tests, deterministic Pillow-generated PNG GUI assets.

**Test-classpath constraint (load-bearing — read before writing any test):** The `test` task has the Minecraft jar on its classpath but **does not run `Bootstrap.bootStrap()`/`SharedConstants`**. Static constants like `ItemStack.EMPTY` resolve, but `new ItemStack(item)`, the item registry, and `ItemStack.OPTIONAL_CODEC` round-trips will fail. Therefore: behavioral tests must exercise **Minecraft-free pure logic** (the pattern used by `BudgetResolver`/`ReweavingResultPicker`, which model items as `String` ids). All runtime-only wiring (component registration, menu/screen, networking receivers) is covered by source-grep contract tests plus the whole-tree guards. This is why Tasks 6 and 8 extract `SatchelMoveResolver`/`PresetApplicationResolver`: the hard logic lives in id-string-based resolvers that are unit-tested directly, while the thin server handlers that call them are grep-guarded.

**Bootstrap ordering reference (verified, src/main/java/dev/attuned/Attuned.java):** the current `onInitialize` runs, in order: `AttunedConfig.load()`, `DynamicRegistries.registerSynced(...)`, `AttunedAttachments.init()`, `AttunedPlayerCleanup.init()`, `AttunedServerCleanup.init()`, `AttunedEffects.init()`, `AttunedContent.init()`, … , `AltarMenuType.init()`, `AltarNetworking.init()`, `ReweavingMenuType.init()`, `ReweavingNetworking.init()`, `GravebindSave.init()`, `Milestones.init()`, `Onboarding.init()`. New inits slot in relative to these real neighbors, NOT to a non-adjacent line.

---

### Task 1: Generalize The Foci Holder (Parallel, Not Delegated)

Add a reusable, immutable, codec-backed `FocusHolder` that parameterizes the size/cap `AttunedInv` hard-codes, plus a Minecraft-free behavioral test of its capping/immutability. **`AttunedInv` is NOT modified** — it stays self-contained so its pinned source strings and public surface survive. `FocusHolder` shares `AttunedInv`'s shape by reimplementation, deliberately not by delegation.

> **Why no delegation (verified):** `AttunedInvTest` asserts the literal presence of `list.add(copyStack(stack));`, `private static ItemStack copyStack(ItemStack stack)`, `copy.set(requireSlot(slot), copyStack(stack));`, `public List<ItemStack> items()` + `return copyItems(items);`, `return copyStack(items.get(requireSlot(slot)));`, and `copy.setCount(Math.min(copy.getCount(), 1));`. Any real delegation deletes those bodies and red-fails that test. So the holders co-exist; the new test below does **not** require `AttunedInv` to reference `FocusHolder` (that would force a vestigial string-match line — a test-gaming smell). `AttunedInv.java` is left out of the modify list entirely.

**Files:**
- Create: `src/test/java/dev/attuned/attunement/FocusHolderTest.java`
- Create after red: `src/main/java/dev/attuned/attunement/FocusHolder.java`

- [ ] **Step 1: Write the failing test**

Create `FocusHolderTest` mixing a real behavioral check (Minecraft-free — uses only `ItemStack.EMPTY`, which is a static constant safe without bootstrap) with source-grep assertions on the codec idioms. Do **not** assert anything about `AttunedInv.java`.

```java
package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

class FocusHolderTest {
	private static final Path HOLDER = Path.of("src/main/java/dev/attuned/attunement/FocusHolder.java");

	@Test
	void emptyHolderNormalizesToRequestedSize() {
		FocusHolder holder = FocusHolder.empty(27, 1);
		assertEquals(27, holder.items().size(), "empty(size, cap) should pad to exactly size slots.");
		for (int i = 0; i < 27; i++) {
			assertEquals(ItemStack.EMPTY, holder.get(i), "Every empty slot should read back EMPTY.");
		}
	}

	@Test
	void withProducesANewInstanceAndDoesNotMutateOriginal() {
		FocusHolder original = FocusHolder.empty(6, 1);
		FocusHolder updated = original.with(0, ItemStack.EMPTY);
		assertNotSame(original, updated, "with(...) must be copy-on-write, returning a fresh instance.");
		assertEquals(6, updated.items().size(), "with(...) preserves the configured size.");
	}

	@Test
	void invalidSlotsThrowWithBounds() {
		FocusHolder holder = FocusHolder.empty(6, 1);
		assertThrows(IllegalArgumentException.class, () -> holder.get(-1));
		assertThrows(IllegalArgumentException.class, () -> holder.get(6));
		assertThrows(IllegalArgumentException.class, () -> holder.with(6, ItemStack.EMPTY));
	}

	@Test
	void holderIsAParameterizedImmutableRecordWithCappingAndCodecs() throws IOException {
		String holder = read(HOLDER);
		assertTrue(holder.contains("public record FocusHolder(int size, int maxPerSlot, List<ItemStack> items)"),
			"FocusHolder should be a record parameterizing size and per-slot cap.");
		assertTrue(holder.contains("public static FocusHolder empty(int size, int maxPerSlot)"),
			"FocusHolder should expose an empty(size, maxPerSlot) factory.");
		assertTrue(holder.contains("public FocusHolder with(int slot, ItemStack stack)"),
			"FocusHolder should mutate copy-on-write via with(slot, stack).");
		assertTrue(holder.contains("public ItemStack get(int slot)"),
			"FocusHolder should expose a defensive get(slot).");
		assertTrue(holder.contains("copy.setCount(Math.min(copy.getCount(), Math.max(1, maxPerSlot)))"),
			"FocusHolder should cap each stored stack to maxPerSlot (its OWN parameterized cap).");
		assertTrue(holder.contains("public static Codec<FocusHolder> codec(int size, int maxPerSlot)"),
			"FocusHolder should build a size/cap-bound persistence Codec.");
		assertTrue(holder.contains("ItemStack.OPTIONAL_CODEC.listOf()"),
			"FocusHolder persistence should reuse the OPTIONAL_CODEC list pattern.");
		assertTrue(holder.contains(
			"public static StreamCodec<RegistryFriendlyByteBuf, FocusHolder> streamCodec(int size, int maxPerSlot)"),
			"FocusHolder should build a size/cap-bound network StreamCodec.");
		assertTrue(holder.contains("ItemStack.OPTIONAL_LIST_STREAM_CODEC"),
			"FocusHolder sync should reuse the OPTIONAL_LIST_STREAM_CODEC pattern.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
Remove-Item -LiteralPath "C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\build\reports\problems\problems-report.html" -ErrorAction SilentlyContinue
.\gradlew.bat test --tests dev.attuned.attunement.FocusHolderTest --no-daemon
```

Expected: failure because `FocusHolder` does not exist.

- [ ] **Step 3: Create `FocusHolder`**

Create `FocusHolder` modeled on `AttunedInv`'s structure but parameterized and with its own state. The canonical constructor normalizes `items` to exactly `size` entries via a `sizedItems(size, maxPerSlot, source)` helper; `copyStack(stack, maxPerSlot)` returns `ItemStack.EMPTY` for null/empty else `stack.copy()` with `copy.setCount(Math.min(copy.getCount(), Math.max(1, maxPerSlot)))`. Implement `empty(int size, int maxPerSlot)`, `get(int slot)` (defensive copy, bounds-checked via a `requireSlot(slot, size)` that throws `IllegalArgumentException`), `with(int slot, ItemStack stack)` (returns a new instance), and `items()` (defensive copy). Build the codecs as static factories so size/cap are captured at registration time:

```java
public static Codec<FocusHolder> codec(int size, int maxPerSlot) {
	return ItemStack.OPTIONAL_CODEC.listOf().xmap(
		items -> new FocusHolder(size, maxPerSlot, items),
		FocusHolder::items);
}

public static StreamCodec<RegistryFriendlyByteBuf, FocusHolder> streamCodec(int size, int maxPerSlot) {
	return ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(
		items -> new FocusHolder(size, maxPerSlot, items),
		FocusHolder::items);
}
```

**Codec round-trip semantics (document in a class comment):** decode reconstructs with the `size`/`maxPerSlot` captured by *this* codec instance, so the satchel component is always decoded with `SATCHEL_SIZE`/`1` (Task 2). This means an absent component decodes to nothing (the item supplies `emptyContents()` as its default — Task 3), and any persisted list longer/shorter than `size` is truncated/padded with no migration. That truncation is **intentional**: `SATCHEL_SIZE` is a stable constant that must never shrink (pinned by a test in Task 2). Over-cap stacks clamp to 1 on every load, which is correct for single-count Foci.

- [ ] **Step 4: Run focused tests to green**

```powershell
.\gradlew.bat test --tests dev.attuned.attunement.FocusHolderTest --tests dev.attuned.attunement.AttunedInvTest --tests dev.attuned.attunement.AttunedAttachmentsContractTest --tests dev.attuned.content.FocusSlotContractTest --no-daemon
```

Expected: `FocusHolderTest` passes; `AttunedInvTest`, `AttunedAttachmentsContractTest`, `FocusSlotContractTest` stay green (AttunedInv untouched).

---

### Task 2: Register The Satchel Contents Data Component

Add the codebase's first custom `DataComponentType<FocusHolder>` for the satchel's contents, in a new idempotent `AttunedComponents` registrar wired into `Attuned.onInitialize` strictly before `AttunedContent.init()`. **The `Registry.register(...)` call lives INSIDE `init()` behind the guard** (mirroring `AltarMenuType` exactly), so the idempotency guard protects a real side-effecting write rather than a no-op — and so that registration is guaranteed to run before any item class-loads `SATCHEL_CONTENTS`.

> **Why register inside `init()` (verified against AltarMenuType):** `DataComponentType` is NOT a whole-tree bootstrap marker (BootstrapRegistrationContractTest enforces only command/loot/`CREATIVE_MODE_TAB`/`MENU`). A `static final` field initializer would register lazily on first class-touch — which could be triggered by `SatchelItem`'s constructor during `AttunedContent.init()` *before* `AttunedComponents.init()` runs, an ordering hazard. Putting the register call in a guarded `init()` (assigned to a non-final static field) makes the `componentsInitRunsBeforeContentInit` ordering test correspond to actual runtime behavior.

**Files:**
- Create: `src/test/java/dev/attuned/content/AttunedComponentsContractTest.java`
- Create after red: `src/main/java/dev/attuned/content/AttunedComponents.java`
- Modify after red: `src/main/java/dev/attuned/Attuned.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AttunedComponentsContractTest {
	private static final Path COMPONENTS = Path.of("src/main/java/dev/attuned/content/AttunedComponents.java");
	private static final Path BOOTSTRAP = Path.of("src/main/java/dev/attuned/Attuned.java");

	@Test
	void satchelContentsComponentRegistersInsideTheIdempotentGuard() throws IOException {
		String components = read(COMPONENTS);
		assertTrue(components.contains("private static boolean initialized;"),
			"Component registration should be idempotent.");
		assertTrue(components.contains("if (initialized)"),
			"Component registration should skip repeated init calls.");
		assertTrue(components.contains("initialized = true;"),
			"Component registration should set its init guard.");
		assertTrue(components.contains("public static DataComponentType<FocusHolder> SATCHEL_CONTENTS;"),
			"SATCHEL_CONTENTS should be an assignable (non-final) field populated in init(), like AltarMenuType.TYPE.");
		assertTrue(components.contains("Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE"),
			"The component should register into the DATA_COMPONENT_TYPE registry.");
		// The register call must run INSIDE init(), AFTER the guard is set — proving the guard is real.
		assertBefore(components, "initialized = true;", "Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE");
		assertTrue(components.contains("\"satchel_contents\""),
			"The component id path should be satchel_contents.");
		assertTrue(components.contains(".persistent(FocusHolder.codec(SATCHEL_SIZE, 1))"),
			"The component should persist via the holder codec.");
		assertTrue(components.contains(".networkSynchronized(FocusHolder.streamCodec(SATCHEL_SIZE, 1))"),
			"The component should sync via the holder stream codec.");
		assertTrue(components.contains("public static final int SATCHEL_SIZE = 27;"),
			"Satchel capacity must be a stable pinned constant (27 = a 9x3 foci grid) that never shrinks.");
		assertTrue(components.contains("public static FocusHolder emptyContents()"),
			"AttunedComponents should expose an emptyContents() default.");
	}

	@Test
	void componentsInitRunsBeforeContentInit() throws IOException {
		String bootstrap = read(BOOTSTRAP);
		int components = bootstrap.indexOf("AttunedComponents.init()");
		int content = bootstrap.indexOf("AttunedContent.init()");
		assertTrue(components >= 0, "Bootstrap should initialize AttunedComponents.");
		assertTrue(content >= 0, "Bootstrap should initialize AttunedContent.");
		assertTrue(components < content,
			"Components must register before items so the satchel can attach a default component.");
	}

	private static void assertBefore(String source, String earlier, String later) {
		int e = source.indexOf(earlier);
		int l = source.indexOf(later);
		assertTrue(e >= 0 && l >= 0 && e < l, "Expected " + earlier + " before " + later);
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
Remove-Item -LiteralPath "C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\build\reports\problems\problems-report.html" -ErrorAction SilentlyContinue
.\gradlew.bat test --tests dev.attuned.content.AttunedComponentsContractTest --no-daemon
```

Expected: failure because `AttunedComponents` does not exist and the bootstrap ordering line is missing.

- [ ] **Step 3: Implement `AttunedComponents`**

Create `AttunedComponents` as a `final` class with a private ctor, a `private static boolean initialized;` guard, a public `static final int SATCHEL_SIZE = 27;`, and an **assignable** `public static DataComponentType<FocusHolder> SATCHEL_CONTENTS;`. Register the component inside `init()` after the guard, exactly like `AltarMenuType.init()` does for `MENU`:

```java
public static final int SATCHEL_SIZE = 27; // 9x3 foci grid; stable — must never shrink (no migration path).

public static DataComponentType<FocusHolder> SATCHEL_CONTENTS;

public static FocusHolder emptyContents() {
	return FocusHolder.empty(SATCHEL_SIZE, 1);
}

public static void init() {
	if (initialized) {
		return;
	}
	initialized = true;
	SATCHEL_CONTENTS = Registry.register(
		BuiltInRegistries.DATA_COMPONENT_TYPE,
		Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "satchel_contents"),
		DataComponentType.<FocusHolder>builder()
			.persistent(FocusHolder.codec(SATCHEL_SIZE, 1))
			.networkSynchronized(FocusHolder.streamCodec(SATCHEL_SIZE, 1))
			.build());
}
```

> **API note:** `DataComponentType.builder().persistent(Codec).networkSynchronized(StreamCodec).build()` is the intended 26.1.2 surface; the codebase has no precedent (only vanilla `DataComponents` usage in `AttunementJournalItem`). If the compiler reports a different builder method name, correct both the implementation and the pinned strings in `AttunedComponentsContractTest` in the same commit (house rule: test and code move together).

- [ ] **Step 4: Wire bootstrap ordering**

In `Attuned.onInitialize`, insert `AttunedComponents.init();` on the line **directly before** `AttunedContent.init();` (currently line 43). It may sit anywhere after `AttunedAttachments.init();` (line 38); placing it immediately before `AttunedContent.init();` is the clearest. Add `import dev.attuned.content.AttunedComponents;`.

- [ ] **Step 5: Run focused test to green**

```powershell
.\gradlew.bat test --tests dev.attuned.content.AttunedComponentsContractTest --no-daemon
```

Expected: pass.

---

### Task 3: Register The Satchel Item, Recipe, And Assets

Register the `SATCHEL_OF_FOCI` item (a `stacksTo(1)` `SatchelItem` that attaches an empty contents component and opens its menu on use), add it to the utility creative tab's core-items block, and ship its item-definition/model/recipe/lang resources. The menu open path comes in Task 4; for now `use()` is stubbed to a server-side comment that the next task fills in.

> **Verified wiring facts:** The private `register(String, Function<Item.Properties, Item>)` helper in `AttunedContent` (line 152) passes an id-bearing `Item.Properties` to the factory, so `SatchelItem`'s constructor signature **must be exactly `SatchelItem(Item.Properties properties)`** (single arg) and apply `stacksTo(1)` + `component(...)` *inside that constructor* — the contract test pins those calls to `SatchelItem.java`. The creative-tab line goes in the `if (includeCoreItems)` block of `registerFocusCreativeTab` (the only items-appending hook; `includeCoreItems=true` is passed only for the `attuned_utility` tab), alongside `ATTUNEMENT_JOURNAL`. `AttunedCreativeTabs` is a package-private `final class`; one more `output.accept(...)` line compiles cleanly.

**Files:**
- Create: `src/test/java/dev/attuned/content/SatchelItemContractTest.java`
- Create after red: `src/main/java/dev/attuned/content/SatchelItem.java`
- Modify after red: `src/main/java/dev/attuned/content/AttunedContent.java`
- Modify after red: `src/main/java/dev/attuned/content/AttunedCreativeTabs.java`
- Create after red: `src/main/resources/assets/attuned/items/satchel_of_foci.json`
- Create after red: `src/main/resources/assets/attuned/models/item/satchel_of_foci.json`
- Create after red: `src/main/resources/assets/attuned/textures/item/satchel_of_foci.png`
- Create after red: `src/main/resources/data/attuned/recipe/satchel_of_foci.json`
- Modify after red: `src/main/resources/assets/attuned/lang/en_us.json`
- Modify after red: `tools/generate_ui_art.py` (add a deterministic `satchel_item()` PNG generator for the 16x16 item texture)

- [ ] **Step 1: Write the failing test**

```java
package dev.attuned.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SatchelItemContractTest {
	private static final Path ITEM = Path.of("src/main/java/dev/attuned/content/SatchelItem.java");
	private static final Path CONTENT = Path.of("src/main/java/dev/attuned/content/AttunedContent.java");
	private static final Path TABS = Path.of("src/main/java/dev/attuned/content/AttunedCreativeTabs.java");
	private static final Path LANG = Path.of("src/main/resources/assets/attuned/lang/en_us.json");
	private static final Path RECIPE = Path.of("src/main/resources/data/attuned/recipe/satchel_of_foci.json");

	@Test
	void satchelItemAttachesEmptyContentsAndStacksToOne() throws IOException {
		String item = read(ITEM);
		assertTrue(item.contains("class SatchelItem extends Item"),
			"SatchelItem should be a custom Item subclass.");
		assertTrue(item.contains("public SatchelItem(Item.Properties properties)") || item.contains("public SatchelItem(Properties properties)"),
			"SatchelItem must take a single Item.Properties arg to satisfy the register helper's Function<Properties,Item>.");
		assertTrue(item.contains("properties.stacksTo(1)"),
			"A bag holding a per-stack component must not stack.");
		assertTrue(item.contains(".component(AttunedComponents.SATCHEL_CONTENTS, AttunedComponents.emptyContents())"),
			"The satchel should attach an empty contents component by default, inside its constructor.");
		assertTrue(item.contains("public InteractionResult use(Level level, Player player, InteractionHand hand)"),
			"The satchel should open its screen on use.");
	}

	@Test
	void satchelIsRegisteredAsAStandardPublicItemInTheUtilityTab() throws IOException {
		String content = read(CONTENT);
		assertTrue(content.contains("public static final Item SATCHEL_OF_FOCI = register(\"satchel_of_foci\", SatchelItem::new);"),
			"Satchel should be a public field using the plain register helper, never registerFocus.");
		String tabs = read(TABS);
		// The accept must sit in the includeCoreItems block, next to the journal — pin it by proximity.
		int journal = tabs.indexOf("output.accept(AttunedContent.ATTUNEMENT_JOURNAL)");
		int satchel = tabs.indexOf("output.accept(AttunedContent.SATCHEL_OF_FOCI)");
		assertTrue(journal >= 0 && satchel >= 0 && Math.abs(journal - satchel) < 400,
			"Satchel should appear in the utility tab's includeCoreItems block, near the journal.");
	}

	@Test
	void satchelHasLangAndRecipe() throws IOException {
		JsonObject lang = JsonParser.parseString(read(LANG)).getAsJsonObject();
		assertTrue(lang.has("item.attuned.satchel_of_foci"), "Satchel item needs a display name.");
		assertTrue(lang.has("container.attuned.satchel"), "Satchel menu needs a window title key.");
		JsonObject recipe = JsonParser.parseString(read(RECIPE)).getAsJsonObject();
		assertTrue(recipe.getAsJsonObject("result").get("id").getAsString().equals("attuned:satchel_of_foci"),
			"Recipe should produce the satchel item.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
Remove-Item -LiteralPath "C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\build\reports\problems\problems-report.html" -ErrorAction SilentlyContinue
.\gradlew.bat test --tests dev.attuned.content.SatchelItemContractTest --no-daemon
```

Expected: failure — `SatchelItem`, the registration line, the tab entry, lang keys, and recipe do not exist.

- [ ] **Step 3: Implement `SatchelItem` and register it**

Create `SatchelItem extends Item` with a single-arg constructor `public SatchelItem(Item.Properties properties)` calling `super(properties.stacksTo(1).component(AttunedComponents.SATCHEL_CONTENTS, AttunedComponents.emptyContents()))`. Implement `use(Level, Player, InteractionHand)` modeled on `AttunementJournalItem.use`: guard `if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer)` and (Task 4) `serverPlayer.openMenu(SatchelMenuType.provider(serverPlayer, hand));`; for now leave a `// Task 4: serverPlayer.openMenu(SatchelMenuType.provider(serverPlayer, hand));` comment (do **not** use the literal token `TODO`/`FIXME`/`HACK` — `verify_repository.py` rejects them). Return `InteractionResult.SUCCESS`.

In `AttunedContent`, add `public static final Item SATCHEL_OF_FOCI = register("satchel_of_foci", SatchelItem::new);` near the other core items (after `ATTUNEMENT_JOURNAL`). In `AttunedCreativeTabs.registerFocusCreativeTab`'s `if (includeCoreItems)` block, add `output.accept(AttunedContent.SATCHEL_OF_FOCI);` right after the `ATTUNEMENT_JOURNAL` accept.

> Note: attaching a default empty component means every satchel carries an explicit empty `FocusHolder` on the wire (heavier than a vanilla bundle, which stores nothing when empty). This is harmless and the contract test requires it; the read path in Task 4 still falls back to `emptyContents()` for safety.

- [ ] **Step 4: Create resources**

- `assets/attuned/items/satchel_of_foci.json`: `{ "model": { "type": "minecraft:model", "model": "attuned:item/satchel_of_foci" } }`.
- `assets/attuned/models/item/satchel_of_foci.json`: `{ "parent": "minecraft:item/generated", "textures": { "layer0": "attuned:item/satchel_of_foci" } }`.
- `assets/attuned/textures/item/satchel_of_foci.png`: a 16x16 item texture. Add a deterministic `satchel_item()` function to `tools/generate_ui_art.py` (modeled on the existing `journal()` generator, saving to `TEXTURES / "item/satchel_of_foci.png"`) and call it from `__main__`; run the script so `verify_repository.py` IHDR/CRC checks pass.
- `data/attuned/recipe/satchel_of_foci.json` (note the directory is `recipe`, singular — verified): a `minecraft:crafting_shaped` recipe with `"result": { "id": "attuned:satchel_of_foci" }`, modeled on `data/attuned/recipe/attunement_shard.json` (which uses `"type"`, `"key"`, `"pattern"`, `"result": { "id": ... }`). Use leather + an attuned material (e.g. leather frame around an amethyst shard) as the recipe shape.
- `assets/attuned/lang/en_us.json`: add `"item.attuned.satchel_of_foci"`, `"container.attuned.satchel"`, `"screen.attuned.satchel.empty"`, `"screen.attuned.satchel.full"` (place them with the other item/container keys; keep the file valid JSON).

- [ ] **Step 5: Run focused test to green**

```powershell
.\gradlew.bat test --tests dev.attuned.content.SatchelItemContractTest --no-daemon
```

Expected: pass.

---

### Task 4: Satchel Drop / Death Round-Trip Coverage

Lock in the single most important property of a bag: **its contents ride along when the stack is dropped or kept on death**, because they live on the `ItemStack`'s `DataComponentType`, not on a player attachment. Because the test classpath cannot bootstrap Minecraft (no `new ItemStack(...)`/real codec round-trip), this is verified through (a) a Minecraft-free behavioral round-trip of `FocusHolder.items()` ↔ holder reconstruction (the same value the component persists), and (b) source-grep guards asserting the component declares both `.persistent(...)` and `.networkSynchronized(...)` so the stack survives both the chunk-save (death/keepInventory) and the entity-drop network paths.

> **Why this task exists:** the prompt enumerates "drop-on-death / dropped-item behavior" as a required, verified deliverable. The architecture gives it for free (no `copyOnDeath` needed for the satchel itself — the component travels with the stack), but it must be *stated and tested*, not assumed.

**Files:**
- Create: `src/test/java/dev/attuned/attunement/FocusHolderRoundTripTest.java`
- (No new production files; this task asserts existing Task 1/2 behavior and the component flags.)

- [ ] **Step 1: Write the failing test**

```java
package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

class FocusHolderRoundTripTest {
	private static final Path COMPONENTS = Path.of("src/main/java/dev/attuned/content/AttunedComponents.java");

	@Test
	void holderPreservesEmptySlotsAcrossItemsRoundTrip() {
		// The exact value the satchel component persists/syncs is FocusHolder.items().
		// Reconstructing from that list (what decode does) must be size-stable and lossless for empties.
		FocusHolder empty = FocusHolder.empty(27, 1);
		FocusHolder rebuilt = new FocusHolder(27, 1, empty.items());
		assertEquals(empty.items().size(), rebuilt.items().size(), "Empty satchel must round-trip its slot count.");
		for (int i = 0; i < 27; i++) {
			assertTrue(rebuilt.get(i).isEmpty(), "Empty slots survive the items() round-trip.");
		}
	}

	@Test
	void holderTruncatesOversizedSourceToTheConfiguredSize() {
		// A persisted list longer than size is deliberately truncated (documented, no migration).
		List<ItemStack> oversized = java.util.Collections.nCopies(40, ItemStack.EMPTY);
		FocusHolder holder = new FocusHolder(27, 1, oversized);
		assertEquals(27, holder.items().size(), "Decode must clamp an over-long persisted list to the configured size.");
	}

	@Test
	void satchelComponentSurvivesBothSaveAndDropPaths() throws IOException {
		String components = read(COMPONENTS);
		assertTrue(components.contains(".persistent(FocusHolder.codec(SATCHEL_SIZE, 1))"),
			"Contents must persist so a kept-on-death / chunk-saved satchel keeps its foci.");
		assertTrue(components.contains(".networkSynchronized(FocusHolder.streamCodec(SATCHEL_SIZE, 1))"),
			"Contents must network-sync so a dropped ItemEntity carries its foci to clients.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
Remove-Item -LiteralPath "C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\build\reports\problems\problems-report.html" -ErrorAction SilentlyContinue
.\gradlew.bat test --tests dev.attuned.attunement.FocusHolderRoundTripTest --no-daemon
```

Expected: behavioral asserts pass once Task 1 is done; the component-flag asserts fail until Task 2 is done. (If running tasks in order, this whole test goes green immediately after Task 2 — it documents and guards, adding no new production code.)

- [ ] **Step 3: No implementation — verify green**

Confirm the round-trip and flag assertions pass against the Task 1/2 output. State in the architecture (already done above) that the satchel needs no `copyOnDeath` because the component lives on the stack.

```powershell
.\gradlew.bat test --tests dev.attuned.attunement.FocusHolderRoundTripTest --no-daemon
```

Expected: pass.

---

### Task 5: Satchel Menu, Container, And Type

Build the item-opened menu. `SatchelContainer` is a component-backed `Container` that **reads the live held satchel via the player's `InteractionHand` on every access** (`player.getItemInHand(hand)`), never a cached `ItemStack`, closing the stale-reference / desync dupe vector. Every mutation (`setItem`, `removeItem`, `removeItemNoUpdate`, `clearContent`) routes the updated holder through `stack.set(AttunedComponents.SATCHEL_CONTENTS, ...)` on that live stack; `setChanged()` is a deliberate no-op (correctness comes from the write-through, not from `setChanged`). `SatchelMenu` exposes the satchel grid plus the player inventory, refuses to accept the satchel item into its own grid (no nesting), and validates that the hand still holds a satchel. `SatchelMenuType` registers the `MenuType` (auto-covered by `BootstrapRegistrationContractTest`'s `Registry.register(BuiltInRegistries.MENU` marker) with a `provider(Player, InteractionHand)`.

> **setChanged() ruling (resolved — no waffling):** `setChanged()` IS a no-op. Because `getItem` returns a *defensive copy* (`FocusHolder.get`), no caller may mutate-then-`setChanged`; all real writes go through `setItem`/`removeItem`/`removeItemNoUpdate`/`clearContent`, each of which calls `stack.set(...)`. This mirrors `FocusContainer`'s no-op exactly. Do NOT add a component write inside `setChanged()`.
>
> **Stale-stack ruling (resolved):** the container stores `hand` (an `InteractionHand`), not a captured `ItemStack`. A private `satchel()` accessor returns `player.getItemInHand(hand)` fresh each call, so reads and writes always hit the current instance even if the player shuffles inventory. `stillValid` checks the held item is `SATCHEL_OF_FOCI`; Task 6's networking independently re-reads via the same hand, so both paths converge on one live stack.

**Files:**
- Create: `src/test/java/dev/attuned/menu/SatchelMenuContractTest.java`
- Create after red: `src/main/java/dev/attuned/menu/SatchelContainer.java`
- Create after red: `src/main/java/dev/attuned/menu/SatchelMenu.java`
- Create after red: `src/main/java/dev/attuned/menu/SatchelMenuType.java`
- Modify after red: `src/main/java/dev/attuned/content/SatchelItem.java`
- Modify after red: `src/main/java/dev/attuned/Attuned.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SatchelMenuContractTest {
	private static final Path CONTAINER = Path.of("src/main/java/dev/attuned/menu/SatchelContainer.java");
	private static final Path MENU = Path.of("src/main/java/dev/attuned/menu/SatchelMenu.java");
	private static final Path TYPE = Path.of("src/main/java/dev/attuned/menu/SatchelMenuType.java");
	private static final Path BOOTSTRAP = Path.of("src/main/java/dev/attuned/Attuned.java");

	@Test
	void satchelContainerReadsLiveHeldStackAndIsFociOnly() throws IOException {
		String container = read(CONTAINER);
		assertTrue(container.contains("implements Container"),
			"SatchelContainer should adapt the held stack to a Container.");
		assertTrue(container.contains("player.getItemInHand(hand)"),
			"SatchelContainer must resolve the held satchel live via the hand, never a cached ItemStack.");
		assertTrue(container.contains("Attunement.definitionFor(player, stack).isEmpty()"),
			"SatchelContainer.setItem must reject non-Focus stacks like FocusContainer.");
		assertTrue(container.contains("satchel().set(AttunedComponents.SATCHEL_CONTENTS"),
			"Writes must persist the holder back into the live held stack's component.");
		assertTrue(container.contains("satchel().get(AttunedComponents.SATCHEL_CONTENTS"),
			"Reads must pull the holder from the live held stack's component.");
		assertTrue(container.contains("public int getMaxStackSize()"),
			"Satchel slots hold single-count Foci.");
		assertTrue(container.contains("public void setChanged()"),
			"setChanged must be declared (a documented no-op; write-through happens in the mutators).");
	}

	@Test
	void satchelMenuValidatesHeldStackRefusesNestingAndRoutesFociOnly() throws IOException {
		String menu = read(MENU);
		assertTrue(menu.contains("extends AbstractContainerMenu"),
			"SatchelMenu should be a real container menu.");
		assertTrue(menu.contains("public SatchelMenu(int containerId, Inventory inventory)"),
			"A client (int, Inventory) constructor is required by the MenuType factory.");
		assertTrue(menu.contains("addStandardInventorySlots"),
			"SatchelMenu should expose the player inventory for transfers.");
		assertTrue(menu.contains("public boolean stillValid(Player player)"),
			"SatchelMenu must validate the satchel is still in hand.");
		assertTrue(menu.contains("getItemInHand"),
			"Validity should check the held satchel stack rather than a block position.");
		assertTrue(menu.contains("!= AttunedContent.SATCHEL_OF_FOCI"),
			"Satchel slots must refuse the satchel item itself (no nested-bag duplication).");
	}

	@Test
	void satchelMenuTypeRegistersInsideGuardAndProvidesFromHand() throws IOException {
		String type = read(TYPE);
		assertTrue(type.contains("private static boolean initialized;"),
			"MenuType registration should be idempotent.");
		assertTrue(type.contains("initialized = true;"),
			"MenuType registration should set its guard before registering.");
		assertBefore(type, "initialized = true;", "Registry.register(BuiltInRegistries.MENU");
		assertTrue(type.contains("new MenuType<>(SatchelMenu::new, FeatureFlags.VANILLA_SET)"),
			"SatchelMenuType should use the plain MenuType ctor like AltarMenuType.");
		assertTrue(type.contains("public static MenuProvider provider(Player player, InteractionHand hand)"),
			"Provider should close over the player's hand, not a BlockPos.");
		assertTrue(read(BOOTSTRAP).contains("SatchelMenuType.init()"),
			"SatchelMenuType must be initialized at bootstrap.");
	}

	private static void assertBefore(String source, String earlier, String later) {
		int e = source.indexOf(earlier);
		int l = source.indexOf(later);
		assertTrue(e >= 0 && l >= 0 && e < l, "Expected " + earlier + " before " + later);
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
Remove-Item -LiteralPath "C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\build\reports\problems\problems-report.html" -ErrorAction SilentlyContinue
.\gradlew.bat test --tests dev.attuned.menu.SatchelMenuContractTest --no-daemon
```

Expected: failure — none of the menu classes exist yet.

- [ ] **Step 3: Implement `SatchelContainer`**

Model on `FocusContainer` but back it by the live held stack. Constructor `SatchelContainer(Player player, InteractionHand hand)` stores both. Add `private ItemStack satchel() { return player.getItemInHand(hand); }`. `holder()` reads `satchel().get(AttunedComponents.SATCHEL_CONTENTS)`, falling back to `AttunedComponents.emptyContents()` when null. `getContainerSize()` returns `AttunedComponents.SATCHEL_SIZE`. `getItem(slot)` returns `holder().get(slot)` (a defensive copy). `setItem(slot, stack)` rejects out-of-range slots, treats null/empty as a clear (`satchel().set(SATCHEL_CONTENTS, holder().with(slot, ItemStack.EMPTY))`), rejects non-Foci via `Attunement.definitionFor(player, stack).isEmpty()`, else writes `satchel().set(SATCHEL_CONTENTS, holder().with(slot, cappedStack(stack)))`. Implement `removeItem`/`removeItemNoUpdate` to also write through `satchel().set(...)` (copy `FocusContainer`'s shapes but target the component). `getMaxStackSize()` returns `1`. `setChanged()` is a documented no-op. `clearContent()` loops `setItem(i, ItemStack.EMPTY)` for every slot. `stillValid(who)` returns `true` (menu enforces real validity). Import `AttunedComponents`, `Attunement`, `InteractionHand`.

- [ ] **Step 4: Implement `SatchelMenu`**

Extend `AbstractContainerMenu` with the dual-constructor shape from `AltarMenu`. Client ctor `SatchelMenu(int containerId, Inventory inventory)` delegates to the full ctor with a throwaway `SimpleContainer(AttunedComponents.SATCHEL_SIZE)` and `InteractionHand.MAIN_HAND` (the client only needs slot layout; the authoritative container lives server-side). Full ctor `SatchelMenu(int containerId, Inventory inventory, Container satchel, InteractionHand hand)` calls `super(SatchelMenuType.TYPE, containerId)`, stores `hand`, adds `AttunedComponents.SATCHEL_SIZE` satchel slots (each a `Slot` whose `getMaxStackSize()` returns 1 and whose `mayPlace(stack)` returns `Attunement.definitionFor(inventory.player, stack).isPresent() && stack.getItem() != AttunedContent.SATCHEL_OF_FOCI` — Foci only, and never a nested satchel), then `addStandardInventorySlots(inventory, X, Y)`. Add a `public InteractionHand hand()` accessor (used by Task 6). Implement `quickMoveStack(player, slotIndex)` mirroring `AltarMenu.quickMoveStack`: satchel-slot stacks move into the player inventory range; inventory stacks move into the satchel range only if `Attunement.definitionFor(player, stack).isPresent()` and the item is not the satchel itself. Implement `stillValid(Player player)` as `player.getItemInHand(hand).getItem() == AttunedContent.SATCHEL_OF_FOCI`. Do **not** override `removed()` to drain — the component IS the storage.

- [ ] **Step 5: Implement `SatchelMenuType` and the item open path**

Model `SatchelMenuType` on `AltarMenuType`: a `private static boolean initialized;` guard, `public static MenuType<SatchelMenu> TYPE;`, `DISPLAY_NAME = Component.translatable("container.attuned.satchel")`, and an `init()` that sets the guard then `TYPE = Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "satchel_of_foci"), new MenuType<>(SatchelMenu::new, FeatureFlags.VANILLA_SET));`. Add `provider(Player player, InteractionHand hand)` returning `new SimpleMenuProvider((containerId, inv, p) -> new SatchelMenu(containerId, inv, new SatchelContainer(p, hand), hand), DISPLAY_NAME)`. In `SatchelItem.use`, replace the Task 3 comment with `serverPlayer.openMenu(SatchelMenuType.provider(serverPlayer, hand));`. In `Attuned.onInitialize`, add `SatchelMenuType.init();` right after `AltarMenuType.init();` (line 56). Add the import.

- [ ] **Step 6: Run focused tests to green**

```powershell
.\gradlew.bat test --tests dev.attuned.menu.SatchelMenuContractTest --tests dev.attuned.content.SatchelItemContractTest --tests dev.attuned.BootstrapRegistrationContractTest --no-daemon
```

Expected: pass (including the whole-tree `MENU` idempotency guard).

---

### Task 6: Satchel Move Logic And Networking (Extracted Resolver + Receiver)

Add the move action: swap a Focus between a satchel slot and an equipped slot. The hard decision logic — swap vs. clear vs. overflow-reject, what counts as a valid move, item conservation — is extracted into a **Minecraft-free `SatchelMoveResolver`** unit-tested directly (since the test classpath cannot bootstrap real `ItemStack`s). A thin serverbound `MoveFocusPayload`/`SatchelNetworking` (modeled on `AltarNetworking`) rebuilds authority server-side and applies the resolver's decision: the equip write goes through `AttunedAttachments.setSlot`, the satchel write through the component.

> **Resolver-over-grep rationale:** the prompt requires genuine edge-case coverage for satchel-full overflow and the swap path (which must not destroy the displaced focus). Source-grep cannot execute that. The resolver models slots as `Optional<String>` ids and returns a decision object; the receiver maps ids↔stacks. This is the `BudgetResolver`/`ReweavingResultPicker` pattern.
>
> **Sentinel handling (resolved):** `MoveFocusPayload` clamps out-of-range slots to `-1`; the receiver returns early on any negative slot **before** touching any container, so `-1` never reaches `FocusHolder.with(-1, ...)` (which throws) — uniform with `AttunedAttachments.setSlot`'s `slot<0` no-op.
> **Tick source (resolved):** the rate-limit map uses `player.level().getGameTime()` (server level game time), read/written on the server thread after the `execute()` hop, mirroring `AltarNetworking`.

**Files:**
- Create: `src/test/java/dev/attuned/menu/SatchelMoveResolverTest.java`
- Create: `src/test/java/dev/attuned/menu/SatchelNetworkingContractTest.java`
- Create after red: `src/main/java/dev/attuned/menu/SatchelMoveResolver.java`
- Create after red: `src/main/java/dev/attuned/menu/MoveFocusPayload.java`
- Create after red: `src/main/java/dev/attuned/menu/SatchelNetworking.java`
- Modify after red: `src/main/java/dev/attuned/Attuned.java`

- [ ] **Step 1: Write the failing tests**

`SatchelMoveResolverTest` (Minecraft-free behavioral — the real edge cases):

```java
package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SatchelMoveResolverTest {
	// Helpers: a slot is Optional<String> (focus id or empty). Resolver returns a Move describing the writes.

	@Test
	void satchelToEquippedOnEmptyTargetClearsSatchelSlot() {
		List<Optional<String>> satchel = List.of(Optional.of("attuned:edge_focus"), Optional.empty());
		List<Optional<String>> equipped = List.of(Optional.empty(), Optional.empty());
		SatchelMoveResolver.Move move = SatchelMoveResolver.satchelToEquipped(satchel, equipped, 0, 0);
		assertTrue(move.applied(), "A valid satchel->equipped move applies.");
		assertEquals(Optional.of("attuned:edge_focus"), move.equippedWrite(), "Focus equips into the target slot.");
		assertEquals(Optional.empty(), move.satchelWrite(), "Source satchel slot clears when target was empty.");
	}

	@Test
	void satchelToEquippedOnOccupiedTargetSwapsAndPreservesDisplacedFocus() {
		List<Optional<String>> satchel = List.of(Optional.of("attuned:edge_focus"));
		List<Optional<String>> equipped = List.of(Optional.of("attuned:iron_focus"));
		SatchelMoveResolver.Move move = SatchelMoveResolver.satchelToEquipped(satchel, equipped, 0, 0);
		assertEquals(Optional.of("attuned:edge_focus"), move.equippedWrite());
		assertEquals(Optional.of("attuned:iron_focus"), move.satchelWrite(),
			"The displaced equipped focus must be written back into the satchel slot — never destroyed.");
	}

	@Test
	void satchelToEquippedRejectsEmptySource() {
		List<Optional<String>> satchel = List.of(Optional.empty());
		List<Optional<String>> equipped = List.of(Optional.empty());
		assertTrue(!SatchelMoveResolver.satchelToEquipped(satchel, equipped, 0, 0).applied(),
			"Moving from an empty satchel slot is a no-op.");
	}

	@Test
	void equippedToSatchelFindsFreeSlotAndClearsEquip() {
		List<Optional<String>> satchel = List.of(Optional.of("attuned:edge_focus"), Optional.empty());
		List<Optional<String>> equipped = List.of(Optional.of("attuned:iron_focus"));
		SatchelMoveResolver.Move move = SatchelMoveResolver.equippedToSatchel(satchel, equipped, 0, -1);
		assertTrue(move.applied());
		assertEquals(1, move.satchelSlot(), "Focus goes into the first free satchel slot.");
		assertEquals(Optional.of("attuned:iron_focus"), move.satchelWrite());
		assertEquals(Optional.empty(), move.equippedWrite(), "Equipped slot clears.");
	}

	@Test
	void equippedToSatchelOnFullSatchelReturnsNoMoveSoNothingIsLost() {
		List<Optional<String>> satchel = List.of(Optional.of("a"), Optional.of("b"));
		List<Optional<String>> equipped = List.of(Optional.of("attuned:iron_focus"));
		assertTrue(!SatchelMoveResolver.equippedToSatchel(satchel, equipped, 0, -1).applied(),
			"A full satchel leaves the focus equipped rather than eating it.");
	}
}
```

`SatchelNetworkingContractTest` (wiring grep guards):

```java
package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SatchelNetworkingContractTest {
	private static final Path PAYLOAD = Path.of("src/main/java/dev/attuned/menu/MoveFocusPayload.java");
	private static final Path NET = Path.of("src/main/java/dev/attuned/menu/SatchelNetworking.java");
	private static final Path BOOTSTRAP = Path.of("src/main/java/dev/attuned/Attuned.java");

	@Test
	void moveFocusPayloadCarriesSanitizedSlots() throws IOException {
		String payload = read(PAYLOAD);
		assertTrue(payload.contains("record MoveFocusPayload(int direction, int satchelSlot, int equippedSlot)"),
			"Payload should carry direction + both slot indices.");
		assertTrue(payload.contains("StreamCodec.composite"), "Payload should use a composite codec.");
		assertTrue(payload.contains("ByteBufCodecs.VAR_INT"), "Payload ints should serialize as VAR_INT.");
		assertTrue(payload.contains(".cast()"),
			"A composite over ByteBufCodecs.* is ByteBuf-typed; cast() to the RegistryFriendlyByteBuf field type.");
		assertTrue(payload.contains("public MoveFocusPayload {"),
			"Payload should validate/clamp in its canonical constructor.");
	}

	@Test
	void receiverIsIdempotentServerAuthoritativeRateLimitedAndUsesTheResolver() throws IOException {
		String net = read(NET);
		assertTrue(net.contains("private static boolean initialized;"), "Networking should be idempotent.");
		assertBefore(net, "initialized = true;", "PayloadTypeRegistry.serverboundPlay().register");
		assertTrue(net.contains("ServerPlayNetworking.registerGlobalReceiver(MoveFocusPayload.TYPE"),
			"Networking should register the move receiver.");
		assertTrue(net.contains("player.level().getServer().execute("), "Handlers must hop to the server thread.");
		assertTrue(net.contains("player.containerMenu instanceof SatchelMenu"),
			"Handler must validate the satchel menu is open.");
		assertTrue(net.contains("player.getItemInHand(menu.hand())"),
			"Handler must re-read the held satchel live via the menu's hand (same instance as the container).");
		assertTrue(net.contains("payload.satchelSlot() < 0 || payload.equippedSlot() < 0"),
			"Handler must reject negative sentinel slots before any container access.");
		assertTrue(net.contains("SatchelMoveResolver."),
			"Handler must delegate the swap/overflow decision to the pure resolver.");
		assertTrue(net.contains("AttunedAttachments.setSlot(player"),
			"Equip side must write through the validated setSlot boundary.");
		assertTrue(net.contains("AttunedComponents.SATCHEL_CONTENTS"),
			"Satchel side must write through the contents component.");
		assertTrue(net.contains("player.level().getGameTime()"),
			"Rate limit must use server-level game time.");
		assertTrue(net.contains("AttunedPlayerCleanup.onForget"), "Rate-limit map cleaned on disconnect.");
		assertTrue(net.contains("AttunedServerCleanup.onStop"), "Rate-limit map cleared on server stop.");
		assertTrue(net.contains("Attunement.definitionFor(player"),
			"Handler must re-validate the moved stack is a real Focus.");
		assertTrue(net.contains("menu.broadcastChanges()"), "Handler must broadcast after a successful move.");
		assertTrue(read(BOOTSTRAP).contains("SatchelNetworking.init()"), "Bootstrap wiring.");
	}

	private static void assertBefore(String source, String earlier, String later) {
		int e = source.indexOf(earlier);
		int l = source.indexOf(later);
		assertTrue(e >= 0 && l >= 0 && e < l, "Expected " + earlier + " before " + later);
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
```

- [ ] **Step 2: Run tests to verify they fail**

```powershell
Remove-Item -LiteralPath "C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\build\reports\problems\problems-report.html" -ErrorAction SilentlyContinue
.\gradlew.bat test --tests dev.attuned.menu.SatchelMoveResolverTest --tests dev.attuned.menu.SatchelNetworkingContractTest --no-daemon
```

Expected: failure — resolver, payload, and networking do not exist.

- [ ] **Step 3: Implement `SatchelMoveResolver`**

Create a Minecraft-free `final` class with a private ctor, a `public record Move(boolean applied, int satchelSlot, Optional<String> satchelWrite, Optional<String> equippedWrite)` (plus a static `Move.none()`), and two pure methods:
- `satchelToEquipped(List<Optional<String>> satchel, List<Optional<String>> equipped, int satchelSlot, int equippedSlot)`: bounds-check both indices; if the satchel source is empty, return `Move.none()`. The `equippedWrite` is the source focus; the `satchelWrite` is whatever the equipped slot held before (a swap — preserves the displaced focus) or empty if it was empty. Returns `applied=true`, `satchelSlot` = the source slot.
- `equippedToSatchel(List<Optional<String>> satchel, List<Optional<String>> equipped, int equippedSlot, int preferredSatchelSlot)`: bounds-check; if the equipped slot is empty, return `Move.none()`. Choose `preferredSatchelSlot` if it is in range and empty, else the first empty satchel slot; if none is free (**overflow**), return `Move.none()` so the focus stays equipped. Set `satchelWrite` = the equipped focus, `equippedWrite` = empty.

Keep it id-string based and registry-free so it is fully unit-testable.

- [ ] **Step 4: Implement `MoveFocusPayload`**

Model on `FocusAbilityStatusPayload`: `record MoveFocusPayload(int direction, int satchelSlot, int equippedSlot) implements CustomPacketPayload`. `direction` is `0` (satchel→equipped) or `1` (equipped→satchel). Canonical constructor clamps `direction` to `{0,1}` and clamps each slot to sentinel `-1` when outside `[0, AttunedComponents.SATCHEL_SIZE)` / `[0, AttunedInv.SIZE)`. `TYPE = new Type<>(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "move_focus"))`. `CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, MoveFocusPayload::direction, ByteBufCodecs.VAR_INT, MoveFocusPayload::satchelSlot, ByteBufCodecs.VAR_INT, MoveFocusPayload::equippedSlot, MoveFocusPayload::new).cast()`.

- [ ] **Step 5: Implement `SatchelNetworking`**

Model on `AltarNetworking`. `init()` sets `initialized = true;` then registers `PayloadTypeRegistry.serverboundPlay().register(MoveFocusPayload.TYPE, MoveFocusPayload.CODEC)` and `ServerPlayNetworking.registerGlobalReceiver(MoveFocusPayload.TYPE, (payload, context) -> { ServerPlayer player = context.player(); player.level().getServer().execute(() -> tryMove(player, payload)); })`. Add `MOVE_COOLDOWN_TICKS`, a `Map<UUID, Long> LAST_MOVE_TICK`, and register `AttunedPlayerCleanup.onForget(LAST_MOVE_TICK::remove)` + `AttunedServerCleanup.onStop(LAST_MOVE_TICK::clear)`. In `tryMove`:
  - Reject unless `player.containerMenu instanceof SatchelMenu menu`.
  - Reject if `payload.satchelSlot() < 0 || payload.equippedSlot() < 0`.
  - Read the live satchel via `ItemStack satchel = player.getItemInHand(menu.hand());` — return if `satchel.getItem() != AttunedContent.SATCHEL_OF_FOCI`.
  - Apply the per-player rate limit using `player.level().getGameTime()` (defense in depth).
  - Build the two `List<Optional<String>>` views: the satchel from `satchel.getOrDefault(SATCHEL_CONTENTS, emptyContents())` mapped per slot to `Optional` of `BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()` (empty for empty); the equipped from `AttunedAttachments.getInventory(player)` likewise. Re-validate each non-empty moved stack with `Attunement.definitionFor(player, ...)` (a removed-datapack focus id resolves empty → treat as not movable).
  - Call `SatchelMoveResolver.satchelToEquipped(...)` for direction 0, `equippedToSatchel(...)` for direction 1.
  - If `!move.applied()`, return.
  - Apply writes: equip side via `AttunedAttachments.setSlot(player, equippedSlot, stackFor(move.equippedWrite()))` (where `stackFor` rebuilds a `new ItemStack(item)` from the id via `BuiltInRegistries.ITEM.getValue(identifier(id))`, or `ItemStack.EMPTY`); satchel side via `satchel.set(SATCHEL_CONTENTS, holder.with(move.satchelSlot(), stackFor(move.satchelWrite())))`. Copy the `identifier(String)` split helper from `ReweavingNetworking` (`id.split(":", 2)`, minecraft-namespace fallback).
  - Update `LAST_MOVE_TICK`, then `menu.broadcastChanges()`.
  - Add `SatchelNetworking.init();` to `Attuned.onInitialize` right after `AltarNetworking.init();` (line 57). Add the import.

- [ ] **Step 6: Run focused tests to green**

```powershell
.\gradlew.bat test --tests dev.attuned.menu.SatchelMoveResolverTest --tests dev.attuned.menu.SatchelNetworkingContractTest --tests dev.attuned.network.NetworkingRegistrationContractTest --no-daemon
```

Expected: pass.

---

### Task 7: Preset Record And Persistence

Define the Minecraft-free `FocusPreset(String name, List<String> slots)` record (each entry an item-registry id string, `""` for an empty slot) with its own `Codec`/`StreamCodec` and a canonical constructor that clamps to exactly `AttunedInv.SIZE` slots and trims/bounds the name. Store loadouts in a new persistent + `targetOnly`-synced + `copyOnDeath` `PRESETS` attachment in `AttunedAttachments`, with save/delete/get helpers next to the milestone/onboarding ones.

> **Storage identity (resolved naming):** presets store **item-registry ids** (`BuiltInRegistries.ITEM.getKey(item)`), the exact identity `ReweavingNetworking` uses; each focus item maps 1:1 to a `FocusDefinition`. The phrase "focus definition id" elsewhere refers to this same string — do NOT serialize a `FocusDefinition` `ResourceKey`.
> **First synced list attachment (flagged):** `PRESETS` is the codebase's first `.syncWith(...)` on a *list* attachment (existing `MILESTONES`/`ONBOARDING` are persistent-only; only scalar/record attachments sync today). The Task 9 UI depends on this client-side sync.
> **Stream-codec buffer type (resolved):** `ByteBufCodecs.STRING_UTF8`/`.list()` are typed over plain `ByteBuf`, so `StreamCodec.composite(...)` yields `StreamCodec<ByteBuf, FocusPreset>`. To declare the field as `StreamCodec<RegistryFriendlyByteBuf, FocusPreset>`, append `.cast()` (mirroring `FocusAbilityStatusPayload.CODEC`). `FocusPreset.STREAM_CODEC.apply(ByteBufCodecs.list())` then composes into `.syncWith(...)` (a `StreamCodec<ByteBuf,...>` satisfies the `? super RegistryFriendlyByteBuf` bound).

**Files:**
- Create: `src/test/java/dev/attuned/attunement/FocusPresetTest.java`
- Create after red: `src/main/java/dev/attuned/attunement/FocusPreset.java`
- Modify after red: `src/main/java/dev/attuned/attunement/AttunedAttachments.java`

- [ ] **Step 1: Write the failing test**

```java
package dev.attuned.attunement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FocusPresetTest {
	private static final Path ATTACHMENTS = Path.of("src/main/java/dev/attuned/attunement/AttunedAttachments.java");

	@Test
	void presetNormalizesToSixSlotsAndTrimsName() {
		FocusPreset preset = new FocusPreset("  Brawler  ", List.of("attuned:edge_focus"));
		assertEquals("Brawler", preset.name(), "Preset name should be trimmed.");
		assertEquals(AttunedInv.SIZE, preset.slots().size(), "Preset should always carry exactly SIZE slots.");
		assertEquals("attuned:edge_focus", preset.slots().get(0));
		assertEquals("", preset.slots().get(5), "Missing slots should pad to empty ids.");
	}

	@Test
	void presetTruncatesOverlongSlotLists() {
		FocusPreset preset = new FocusPreset("x", List.of("a", "b", "c", "d", "e", "f", "g", "h"));
		assertEquals(AttunedInv.SIZE, preset.slots().size(), "Extra slots beyond SIZE are truncated.");
	}

	@Test
	void presetRejectsBlankNames() {
		FocusPreset preset = new FocusPreset("   ", List.of());
		assertTrue(!preset.name().isEmpty(), "Blank preset names should fall back to a placeholder, not persist empty.");
	}

	@Test
	void presetsAttachmentMatchesInventoryPersistenceContract() throws IOException {
		String attachments = read(ATTACHMENTS);
		assertTrue(attachments.contains("AttachmentType<List<FocusPreset>> PRESETS"),
			"Presets should be a per-player list attachment.");
		assertTrue(attachments.contains("FocusPreset.CODEC.listOf()"),
			"Presets should persist via the preset codec list.");
		assertTrue(attachments.contains("FocusPreset.STREAM_CODEC.apply(ByteBufCodecs.list())"),
			"Presets should sync via a list-wrapped preset stream codec (first synced list attachment).");
		assertTrue(attachments.contains("AttachmentSyncPredicate.targetOnly()"),
			"Presets should sync only to the owning client, like INVENTORY.");
		assertTrue(attachments.contains(".copyOnDeath()"), "Presets should survive death.");
		assertTrue(attachments.contains("public static List<FocusPreset> getPresets(Player player)"),
			"There should be a read helper.");
		assertTrue(attachments.contains("public static void savePreset(Player player, FocusPreset preset)"),
			"There should be a save helper.");
		assertTrue(attachments.contains("public static void deletePreset(Player player, int index)"),
			"There should be a delete helper.");
		assertTrue(attachments.contains("List.copyOf("),
			"Preset writes should persist an immutable defensive snapshot, like milestones/onboarding.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
Remove-Item -LiteralPath "C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\build\reports\problems\problems-report.html" -ErrorAction SilentlyContinue
.\gradlew.bat test --tests dev.attuned.attunement.FocusPresetTest --no-daemon
```

Expected: failure — `FocusPreset` and the `PRESETS` attachment do not exist.

- [ ] **Step 3: Implement `FocusPreset`**

Create the record `FocusPreset(String name, List<String> slots)`. Canonical constructor: trim `name`, fall back to a placeholder (`"Preset"`) if blank, clamp to a sane max (32 chars); normalize `slots` to exactly `AttunedInv.SIZE` entries, padding with `""`, truncating extras, mapping null entries to `""`. Provide `CODEC = RecordCodecBuilder.create(...)` over `Codec.STRING` and `Codec.STRING.listOf()`. Provide `STREAM_CODEC` as `StreamCodec.composite(ByteBufCodecs.STRING_UTF8, FocusPreset::name, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), FocusPreset::slots, FocusPreset::new).cast()` — declared as `StreamCodec<RegistryFriendlyByteBuf, FocusPreset>` (the `.cast()` is required, exactly like `FocusAbilityStatusPayload`). Keep it registry-free so it stays unit-testable.

> If the compiler reports that `ByteBufCodecs.list()` does not exist or has a different name in this 26.1.2/Fabric mapping, fall back to the proven `ItemStack.OPTIONAL_LIST_STREAM_CODEC`-style list idiom, and update the pinned strings in `FocusPresetTest` / `AttunedAttachmentsContractTest`-adjacent assertions in the same commit.

- [ ] **Step 4: Add the `PRESETS` attachment + helpers**

In `AttunedAttachments`, register (note: this is the first synced *list* attachment — call it out in a class comment):

```java
public static final AttachmentType<List<FocusPreset>> PRESETS = AttachmentRegistry.create(
	Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "presets"),
	builder -> builder
		.initializer(() -> List.of())
		.persistent(FocusPreset.CODEC.listOf())
		.syncWith(FocusPreset.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly())
		.copyOnDeath());
```

Add a `MAX_PRESETS` constant (e.g. 9). Add `getPresets(Player)` (`return player.getAttachedOrElse(PRESETS, List.of());`), `savePreset(Player, FocusPreset)` (replace the same-named preset in place, else append; if appending would exceed `MAX_PRESETS`, drop the request — do not silently evict; persist `List.copyOf(updated)` via `setAttached`), and `deletePreset(Player, int index)` (bounds-checked; rebuild and `setAttached(List.copyOf(...))`). Follow the milestone/onboarding immutable-snapshot pattern (never `modifyAttached`). Add the `import dev.attuned.attunement.FocusPreset;`-equivalent (same package, so no import needed) and the `ByteBufCodecs` import is already present.

- [ ] **Step 5: Run focused test to green**

```powershell
.\gradlew.bat test --tests dev.attuned.attunement.FocusPresetTest --tests dev.attuned.attunement.AttunedAttachmentsContractTest --no-daemon
```

Expected: pass.

---

### Task 8: Preset Save / Apply / Delete (Extracted Resolver + Networking)

Add serverbound payloads to capture the current equipped loadout, apply a stored preset, and delete one. Apply is the heart of the feature and the highest dupe/loss risk, so its **transactional logic is extracted into a Minecraft-free `PresetApplicationResolver`** with conservation guarantees, unit-tested directly. The thin `PresetNetworking` receiver maps ids↔stacks and writes through validated boundaries.

> **Conservation (resolved blocker):** apply must not destroy currently-equipped foci. The resolver takes a snapshot of {current equipped ids, satchel ids, player-inventory focus-id multiset, target preset ids} and returns, in one shot: per-slot equip writes, the resulting satchel id-list, the resulting consumed-inventory multiset, and a `missing` id list. It threads a single mutable working pool so each slot's sourcing sees prior removals (no double-count), and it returns displaced/previously-equipped foci back into the pool first so nothing is deleted. The receiver applies the whole result atomically on the server thread.
> **Resolution APIs (resolved):** `Registry<FocusDefinition> registry = player.level().registryAccess().lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS);` then `FocusLookup.forItem(registry, item)` — identical to `ReweavingNetworking.tryReweave`. `FocusLookup.forItem` already returns empty for AIR/non-Focus; the explicit `item == Items.AIR` branch exists only to short-circuit unknown/removed ids that `BuiltInRegistries.ITEM.getValue` maps to AIR before the lookup (belt-and-suspenders, not an independent gate).

**Files:**
- Create: `src/test/java/dev/attuned/menu/PresetApplicationResolverTest.java`
- Create: `src/test/java/dev/attuned/menu/PresetNetworkingContractTest.java`
- Create after red: `src/main/java/dev/attuned/menu/PresetApplicationResolver.java`
- Create after red: `src/main/java/dev/attuned/menu/SavePresetPayload.java`
- Create after red: `src/main/java/dev/attuned/menu/ApplyPresetPayload.java`
- Create after red: `src/main/java/dev/attuned/menu/DeletePresetPayload.java`
- Create after red: `src/main/java/dev/attuned/menu/PresetNetworking.java`
- Modify after red: `src/main/java/dev/attuned/Attuned.java`

- [ ] **Step 1: Write the failing tests**

`PresetApplicationResolverTest` (Minecraft-free behavioral — the enumerated edge cases):

```java
package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PresetApplicationResolverTest {
	// A "known focus id" is any id present in the registeredFocusIds set passed in (models FocusLookup).

	@Test
	void sourcesFromSatchelThenInventoryAndConservesTotalFocusCount() {
		// preset wants edge in slot 0; edge lives in the satchel.
		PresetApplicationResolver.Result r = PresetApplicationResolver.apply(
			/* preset */ List.of("attuned:edge_focus", "", "", "", "", ""),
			/* equipped */ List.of("", "", "", "", "", ""),
			/* satchel */ List.of("attuned:edge_focus"),
			/* inventoryFocusCounts */ Map.of(),
			/* registeredFocusIds */ java.util.Set.of("attuned:edge_focus"));
		assertEquals("attuned:edge_focus", r.equips().get(0), "Slot 0 equips the sourced focus.");
		assertTrue(!r.satchel().contains("attuned:edge_focus"), "The sourced focus is consumed from the satchel.");
		assertTrue(r.missing().isEmpty(), "Nothing missing when the focus is available.");
	}

	@Test
	void returnsPreviouslyEquippedFociToThePoolSoTheyAreNotDestroyed() {
		// slot 0 currently holds iron; preset wants edge (in satchel). iron must survive.
		PresetApplicationResolver.Result r = PresetApplicationResolver.apply(
			List.of("attuned:edge_focus", "", "", "", "", ""),
			List.of("attuned:iron_focus", "", "", "", "", ""),
			List.of("attuned:edge_focus"),
			Map.of(),
			java.util.Set.of("attuned:edge_focus", "attuned:iron_focus"));
		long ironInSatchel = r.satchel().stream().filter("attuned:iron_focus"::equals).count();
		assertTrue(ironInSatchel >= 1, "The displaced iron focus is returned to the satchel pool, not deleted.");
	}

	@Test
	void absentItemLeavesSlotEmptyAndRecordsMissing() {
		PresetApplicationResolver.Result r = PresetApplicationResolver.apply(
			List.of("attuned:edge_focus", "", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of(), Map.of(),
			java.util.Set.of("attuned:edge_focus"));
		assertEquals("", r.equips().get(0), "An unsourced focus leaves the slot empty.");
		assertTrue(r.missing().contains("attuned:edge_focus"), "An unsourced focus is recorded as missing.");
	}

	@Test
	void unknownOrRemovedFocusIdIsTreatedAsMissingNotEquipped() {
		PresetApplicationResolver.Result r = PresetApplicationResolver.apply(
			List.of("attuned:deleted_focus", "", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of("attuned:deleted_focus"), Map.of(),
			/* registeredFocusIds — deliberately omits deleted_focus */ java.util.Set.of("attuned:edge_focus"));
		assertEquals("", r.equips().get(0), "An id not in the focus registry is never equipped.");
		assertTrue(r.missing().contains("attuned:deleted_focus"), "Removed/unknown ids are reported missing.");
	}

	@Test
	void duplicateUniqueIdAcrossTwoSlotsEquipsBothLeavingDormancyToTheBudget() {
		PresetApplicationResolver.Result r = PresetApplicationResolver.apply(
			List.of("attuned:edge_focus", "attuned:edge_focus", "", "", "", ""),
			List.of("", "", "", "", "", ""),
			List.of("attuned:edge_focus", "attuned:edge_focus"), Map.of(),
			java.util.Set.of("attuned:edge_focus"));
		assertEquals("attuned:edge_focus", r.equips().get(0));
		assertEquals("attuned:edge_focus", r.equips().get(1),
			"Two copies both equip (storage); uniqueness/budget dormancy is resolved elsewhere.");
	}
}
```

`PresetNetworkingContractTest` (wiring grep guards):

```java
package dev.attuned.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PresetNetworkingContractTest {
	private static final Path SAVE = Path.of("src/main/java/dev/attuned/menu/SavePresetPayload.java");
	private static final Path APPLY = Path.of("src/main/java/dev/attuned/menu/ApplyPresetPayload.java");
	private static final Path DELETE = Path.of("src/main/java/dev/attuned/menu/DeletePresetPayload.java");
	private static final Path NET = Path.of("src/main/java/dev/attuned/menu/PresetNetworking.java");
	private static final Path BOOTSTRAP = Path.of("src/main/java/dev/attuned/Attuned.java");

	@Test
	void payloadsCarryMinimalSanitizedData() throws IOException {
		assertTrue(read(SAVE).contains("record SavePresetPayload(String name)"), "Save carries only a name.");
		assertTrue(read(SAVE).contains("ByteBufCodecs.STRING_UTF8"), "Save name serializes as UTF-8.");
		assertTrue(read(SAVE).contains(".cast()"), "STRING_UTF8 composite must cast to the RegistryFriendlyByteBuf field type.");
		assertTrue(read(APPLY).contains("record ApplyPresetPayload(int index)"), "Apply carries only an index.");
		assertTrue(read(DELETE).contains("record DeletePresetPayload(int index)"), "Delete carries only an index.");
	}

	@Test
	void presetNetworkingIsIdempotentServerAuthoritativeAndUsesTheResolver() throws IOException {
		String net = read(NET);
		assertTrue(net.contains("private static boolean initialized;"), "Idempotent init.");
		assertBefore(net, "initialized = true;", "PayloadTypeRegistry.serverboundPlay().register");
		assertTrue(net.contains("ServerPlayNetworking.registerGlobalReceiver(SavePresetPayload.TYPE"), "Save receiver.");
		assertTrue(net.contains("ServerPlayNetworking.registerGlobalReceiver(ApplyPresetPayload.TYPE"), "Apply receiver.");
		assertTrue(net.contains("ServerPlayNetworking.registerGlobalReceiver(DeletePresetPayload.TYPE"), "Delete receiver.");
		assertTrue(net.contains("player.level().getServer().execute("), "Server-thread hop.");
		assertTrue(net.contains("PresetApplicationResolver."), "Apply must delegate to the pure resolver.");
		assertTrue(read(BOOTSTRAP).contains("PresetNetworking.init()"), "Bootstrap wiring.");
	}

	@Test
	void applyAndSaveResolveAgainstTheRegistryAndRebuildStacksFromIds() throws IOException {
		String net = read(NET);
		assertTrue(net.contains("lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS)"),
			"Apply must resolve focus ids against the world registry server-side.");
		assertTrue(net.contains("AttunedAttachments.setSlot(player"),
			"Apply must re-equip through the validated setSlot boundary.");
		assertTrue(net.contains("BuiltInRegistries.ITEM.getValue"),
			"Apply must rebuild items from their registry ids.");
		assertTrue(net.contains("Items.AIR"),
			"Apply must short-circuit unknown ids (getValue->AIR) rather than equipping air.");
		assertTrue(net.contains("BuiltInRegistries.ITEM.getKey"),
			"Save must capture equipped foci by their registry id.");
		assertTrue(net.contains("AttunedComponents.SATCHEL_CONTENTS"),
			"Apply must write the consumed satchel pool back to the component.");
		assertTrue(net.contains("menu.broadcastChanges()"),
			"Apply must broadcast so the open satchel grid reflects consumed foci.");
	}

	private static void assertBefore(String source, String earlier, String later) {
		int e = source.indexOf(earlier);
		int l = source.indexOf(later);
		assertTrue(e >= 0 && l >= 0 && e < l, "Expected " + earlier + " before " + later);
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
```

- [ ] **Step 2: Run tests to verify they fail**

```powershell
Remove-Item -LiteralPath "C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\build\reports\problems\problems-report.html" -ErrorAction SilentlyContinue
.\gradlew.bat test --tests dev.attuned.menu.PresetApplicationResolverTest --tests dev.attuned.menu.PresetNetworkingContractTest --no-daemon
```

Expected: failure — resolver, payloads, and networking do not exist.

- [ ] **Step 3: Implement `PresetApplicationResolver`**

Create a Minecraft-free `final` class with a private ctor, a `public record Result(List<String> equips, List<String> satchel, Map<String,Integer> consumedInventory, List<String> missing)`, and one method:

```java
public static Result apply(
    List<String> preset,                 // SIZE ids, "" = empty
    List<String> equippedNow,            // SIZE ids currently equipped
    List<String> satchel,                // current satchel ids
    Map<String,Integer> inventoryFocusCounts, // id -> count available in player inventory
    java.util.Set<String> registeredFocusIds) // ids known to FOCUS_DEFINITIONS
```

Algorithm (single pass, conserving): start a working pool = satchel ids (mutable list) + inventory multiset (mutable copy), then **return every currently-equipped id into the pool** (so nothing is destroyed). For each of the SIZE preset slots: if the id is `""` → equip `""`. Else if the id is not in `registeredFocusIds` → record missing, equip `""`. Else try to consume one from the pool (satchel first, then inventory multiset); if found → equip the id and remove it from that source; if not found → record missing, equip `""`. Duplicate ids consume independently (both equip if two copies exist in the pool). Build `Result` with the final equips, the residual satchel list (padded to its original length with `""`), the residual inventory multiset, and the missing list. Keep it pure and registry-free.

- [ ] **Step 4: Implement the three payloads**

Model on `BindShardPayload`/`FocusAbilityStatusPayload`:
- `SavePresetPayload(String name)` — `CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SavePresetPayload::name, SavePresetPayload::new).cast()`; canonical constructor trims and length-clamps the name (blank is allowed here — `FocusPreset` defaults it; the UI may guard separately).
- `ApplyPresetPayload(int index)` and `DeletePresetPayload(int index)` — single-`VAR_INT` composite codecs ending in `.cast()`; canonical constructors clamp negative indices to `-1`.
- Each defines a unique `TYPE` id (`"save_preset"`, `"apply_preset"`, `"delete_preset"`).

- [ ] **Step 5: Implement `PresetNetworking`**

Model on `AltarNetworking`. `init()` sets the guard then registers all three payload types and receivers (each hopping to the server thread via `player.level().getServer().execute(...)`), plus an apply rate-limit map keyed on `player.level().getGameTime()`, cleaned via `AttunedPlayerCleanup`/`AttunedServerCleanup`. Handlers:
  - **Save:** snapshot the six equipped slots — for each slot read `AttunedAttachments.getInventory(player).get(slot)`; if `Attunement.definitionFor(player, stack).isPresent()` store `BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()`, else `""`. Persist via `AttunedAttachments.savePreset(player, new FocusPreset(payload.name(), ids))`.
  - **Apply:** validate `index` against `AttunedAttachments.getPresets(player)` (out-of-range → return). Resolve `Registry<FocusDefinition> registry = player.level().registryAccess().lookupOrThrow(AttunedRegistries.FOCUS_DEFINITIONS)`. Build `registeredFocusIds` = the registry's focus item ids (`registry.stream().map(def -> BuiltInRegistries.ITEM.getKey(def.item().value()).toString())`). Build the satchel id-list from the live held satchel component (if a `SatchelMenu` is open and the hand holds a satchel; else an empty list) and the inventory focus-count map from the player's main inventory (counting only stacks whose item id is in `registeredFocusIds`). Call `PresetApplicationResolver.apply(...)`. Then apply the result on the server thread: for each slot equip via `AttunedAttachments.setSlot(player, slot, stackFor(id))` where `stackFor("")` is `ItemStack.EMPTY` and otherwise rebuilds `new ItemStack(BuiltInRegistries.ITEM.getValue(identifier(id)))` (short-circuiting `Items.AIR`); rewrite the satchel component from the residual list; consume the residual inventory difference from the player's real inventory (remove the foci the resolver marked consumed); if a `SatchelMenu` is open call `menu.broadcastChanges()`. Optionally `player.sendSystemMessage(Component.translatable("screen.attuned.preset.missing", ...))` listing `result.missing()`. Copy the `identifier(String)` helper from `ReweavingNetworking`.
  - **Delete:** `AttunedAttachments.deletePreset(player, payload.index())`.
  - Add `PresetNetworking.init();` to `Attuned.onInitialize` right after `SatchelNetworking.init();`. Add the import.

- [ ] **Step 6: Run focused tests to green**

```powershell
.\gradlew.bat test --tests dev.attuned.menu.PresetApplicationResolverTest --tests dev.attuned.menu.PresetNetworkingContractTest --tests dev.attuned.network.NetworkingRegistrationContractTest --no-daemon
```

Expected: pass.

---

### Task 9: Satchel Screen, Texture, And Preset UI

Bind a client `SatchelScreen` (an `AbstractContainerScreen<SatchelMenu>`) to the menu type via `MenuScreens.register`, using this fork's `extractBackground`/`extractLabels(GuiGraphicsExtractor)` render hooks (NOT vanilla `render`/`renderBg`). Ship a deterministic 176x166 PNG (pinned by ImageIO). Surface preset management with Save / Apply / Delete buttons (modeled on `AltarScreen.BindButton`) that send the Task 8 payloads, plus a compact list of saved presets read from the synced `PRESETS` attachment, with empty/locked states wired. This makes all three deliverables reachable end-to-end without commands.

> **Registration home (flagged):** `MenuScreens.register(SatchelMenuType.TYPE, SatchelScreen::new)` is added inside `AltarScreens.init()` (already guarded with `initialized = true;` before its existing `MenuScreens.register` calls, and invoked from client init). This piggybacks on a class named `AltarScreens` — a minor altitude smell, acceptable for scope. `AltarScreens.java` needs `import dev.attuned.menu.SatchelMenuType;` added (`SatchelScreen` is same-package as `AltarScreen`, no import needed). `ClientRegistrationContractTest` stays green because the new line lands in an already-guarded file.

**Files:**
- Create: `src/test/java/dev/attuned/client/SatchelScreenContractTest.java`
- Create after red: `src/client/java/dev/attuned/client/screen/SatchelScreen.java`
- Modify after red: `src/client/java/dev/attuned/client/screen/AltarScreens.java`
- Create after red: `src/main/resources/assets/attuned/textures/gui/satchel.png`
- Modify after red: `tools/generate_ui_art.py`
- Modify after red: `src/main/resources/assets/attuned/lang/en_us.json`

- [ ] **Step 1: Write the failing test**

```java
package dev.attuned.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SatchelScreenContractTest {
	private static final Path SCREEN = Path.of("src/client/java/dev/attuned/client/screen/SatchelScreen.java");
	private static final Path SCREENS = Path.of("src/client/java/dev/attuned/client/screen/AltarScreens.java");
	private static final Path TEXTURE = Path.of("src/main/resources/assets/attuned/textures/gui/satchel.png");
	private static final Path LANG = Path.of("src/main/resources/assets/attuned/lang/en_us.json");

	@Test
	void satchelScreenUsesForkRenderHooksAndTexture() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("extends AbstractContainerScreen<SatchelMenu>"),
			"SatchelScreen should be a container screen over the satchel menu.");
		assertTrue(screen.contains("extractBackground(GuiGraphicsExtractor"),
			"SatchelScreen must use the fork background render hook, not vanilla renderBg.");
		assertTrue(screen.contains("extractLabels(GuiGraphicsExtractor"),
			"SatchelScreen must use the fork label render hook.");
		assertTrue(screen.contains("textures/gui/satchel.png"),
			"SatchelScreen must reference its background texture.");
		assertTrue(read(SCREENS).contains("MenuScreens.register(SatchelMenuType.TYPE, SatchelScreen::new)"),
			"The satchel screen must be registered against its menu type.");
	}

	@Test
	void satchelScreenExposesPresetActionsAndReadsSyncedPresets() throws IOException {
		String screen = read(SCREEN);
		assertTrue(screen.contains("new SavePresetPayload"), "Save button should send a SavePresetPayload.");
		assertTrue(screen.contains("new ApplyPresetPayload"), "Apply button should send an ApplyPresetPayload.");
		assertTrue(screen.contains("new DeletePresetPayload"), "Delete button should send a DeletePresetPayload.");
		assertTrue(screen.contains("ClientPlayNetworking.send"), "Preset buttons should send over client networking.");
		assertTrue(screen.contains("AttunedAttachments.getPresets"),
			"The screen should read presets from the synced attachment each frame (no caching).");
	}

	@Test
	void satchelTextureHasPinnedSize() throws IOException {
		assertTrue(Files.isRegularFile(TEXTURE), "Satchel GUI texture must exist.");
		BufferedImage image = ImageIO.read(TEXTURE.toFile());
		assertNotNull(image, "Satchel GUI texture must be a readable PNG.");
		assertEquals(176, image.getWidth(), "Satchel texture width must be pinned.");
		assertEquals(166, image.getHeight(), "Satchel texture height must be pinned.");
	}

	@Test
	void presetLangKeysExist() throws IOException {
		JsonObject lang = JsonParser.parseString(read(LANG)).getAsJsonObject();
		assertTrue(lang.has("screen.attuned.preset.save"), "Save label.");
		assertTrue(lang.has("screen.attuned.preset.apply"), "Apply label.");
		assertTrue(lang.has("screen.attuned.preset.delete"), "Delete label.");
		assertTrue(lang.has("screen.attuned.preset.missing"), "Missing-focus feedback string.");
	}

	private static String read(Path file) throws IOException {
		assertTrue(Files.isRegularFile(file), "Expected file to exist: " + file);
		return Files.readString(file, StandardCharsets.UTF_8);
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
Remove-Item -LiteralPath "C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\build\reports\problems\problems-report.html" -ErrorAction SilentlyContinue
.\gradlew.bat test --tests dev.attuned.client.SatchelScreenContractTest --no-daemon
```

Expected: failure — screen, registration, texture, and lang keys do not exist.

- [ ] **Step 3: Generate the texture**

Add a deterministic `satchel_gui()` function to `tools/generate_ui_art.py` (modeled on the `altar()` generator's bevel/inset/slot_well helpers) that draws a 176x166 panel into `src/main/resources/assets/attuned/textures/gui/satchel.png`, and call it from `__main__`. Run the script so `verify_repository.py` IHDR/CRC checks pass.

- [ ] **Step 4: Implement `SatchelScreen` and register it**

Create `SatchelScreen extends AbstractContainerScreen<SatchelMenu>` modeled on `AltarScreen`: a `BACKGROUND_TEXTURE = Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "textures/gui/satchel.png")`, `IMAGE_WIDTH = 176`, `IMAGE_HEIGHT = 166`, constructor passing those to `super(...)`. Override `extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)` to blit via `graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, ...)`. In `init()`, add Save / Apply / Delete `Button` widgets (inner-class subclasses overriding `extractContents(GuiGraphicsExtractor ...)` like `AltarScreen.BindButton`), each `OnPress` calling `ClientPlayNetworking.send(new SavePresetPayload(...))` / `new ApplyPresetPayload(selectedIndex)` / `new DeletePresetPayload(selectedIndex)`. Track a `selectedIndex` and clamp it against the preset list size in `containerTick()`; disable Apply/Delete when no preset is selected or the list is empty (locked state), and surface the empty/full satchel states via the `screen.attuned.satchel.empty`/`.full` lang keys. In `extractLabels`, read `AttunedAttachments.getPresets(this.minecraft.player)` **every call (no caching)** so server-side mutations (which arrive via the `targetOnly` sync) and the open grid (refreshed by `broadcastChanges()` in Tasks 6/8) stay current; draw the preset list with the selected entry highlighted. Add `MenuScreens.register(SatchelMenuType.TYPE, SatchelScreen::new);` inside `AltarScreens.init()` and the `import dev.attuned.menu.SatchelMenuType;`.

- [ ] **Step 5: Add lang keys**

Add `"screen.attuned.preset.save"`, `"screen.attuned.preset.apply"`, `"screen.attuned.preset.delete"`, and `"screen.attuned.preset.missing"` to `en_us.json` (the satchel/empty/full keys were added in Task 3). Keep the file valid JSON.

- [ ] **Step 6: Run focused test to green**

```powershell
.\gradlew.bat test --tests dev.attuned.client.SatchelScreenContractTest --tests dev.attuned.client.ClientRegistrationContractTest --no-daemon
```

Expected: pass.

---

### Task 10: Full Verification

**Files:**
- All changed files.

- [ ] **Step 1: Run all focused new tests**

```powershell
Remove-Item -LiteralPath "C:\Users\Eating\Desktop\01_Projects\Minecraft_and_Game_Dev\Minecraft Mod\build\reports\problems\problems-report.html" -ErrorAction SilentlyContinue
.\gradlew.bat test --tests dev.attuned.attunement.FocusHolderTest --tests dev.attuned.attunement.FocusHolderRoundTripTest --tests dev.attuned.content.AttunedComponentsContractTest --tests dev.attuned.content.SatchelItemContractTest --tests dev.attuned.menu.SatchelMenuContractTest --tests dev.attuned.menu.SatchelMoveResolverTest --tests dev.attuned.menu.SatchelNetworkingContractTest --tests dev.attuned.attunement.FocusPresetTest --tests dev.attuned.menu.PresetApplicationResolverTest --tests dev.attuned.menu.PresetNetworkingContractTest --tests dev.attuned.client.SatchelScreenContractTest --no-daemon
```

Expected: all pass.

- [ ] **Step 2: Run the full test suite**

```powershell
.\gradlew.bat test --no-daemon
```

Expected: all pass, including the whole-tree guards (`BootstrapRegistrationContractTest`, `NetworkingRegistrationContractTest`, `ClientRegistrationContractTest`), `FocusSlotContractTest`, `AttunedInvTest`, and `AttunedAttachmentsContractTest` (the last three must be unchanged-green, proving `AttunedInv` was left intact).

- [ ] **Step 3: Run the build**

```powershell
.\gradlew.bat build --no-daemon
```

Expected: success.

- [ ] **Step 4: Run the repository verifier and whitespace check**

```powershell
python tools/verify_repository.py
git diff --check
```

Expected: `verify_repository.py` passes (no `TODO`/`FIXME`/`HACK` tokens in new source, all new PNG/JSON structurally valid); no whitespace errors. CRLF warnings are acceptable on this workspace.
