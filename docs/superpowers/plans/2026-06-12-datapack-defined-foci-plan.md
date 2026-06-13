# Attuned — Datapack-Defined Foci — Implementation Plan

## Goal

Make Foci author-extensible from a datapack so every modpack that bundles Attuned becomes a content multiplier rather than a fixed catalog of 61 Foci. This plan ships in four ordered phases: (1) **document the already-data-driven path** (Foci are already `data/<ns>/attuned/focus/<name>.json`, can already point at any registered item via `BuiltInRegistries.ITEM.holderByNameCodec()`, and already accept fully data-driven attribute modifiers via `ModifierEntry`) and harden `/attuned validate` to check author packs file-by-file plus ship a worked example datapack; (2) add a **pool of resource-pack-skinnable generic Focus items** (`attuned:custom_focus_1..16`) so authors get bespoke item identity without a JAR; (3) add a **parameterized behavior palette + `FocusCondition` registry** resolved through the single `AttunedRegistries.getBehavior` funnel as a code-first-then-data lookup, starting with `conditional_mob_effect`; and (4) **broaden the palette** to `on_hit_effect`, `periodic_effect`, and `attribute_while`, then ship the versioned public contract (`docs/reference.md` palette + condition tables, `docs/authoring-foci.md`, README authoring section) validated by `/attuned validate`, and run the full release gate.

## Architecture / Tech Stack

Attachments (persistent / target-only-synced / copy-on-death `List<String>` via `AttachmentRegistry`) + components (`FocusHolder` codec/streamCodec) + **pure resolvers** (`FocusCondition` predicates and palette param decoding modelled as Minecraft-free value snapshots, in the `PresetApplicationResolver`/`BudgetResolver`/`SynergyResolver` lineage) + **idempotent inits** (`if (initialized) return;` guards, registration in `Attuned.onInitialize()` before content init) + **cleanup-registered maps** (`AttunedPlayerCleanup.onForget(MAP::remove)` / `AttunedServerCleanup.onStop(MAP::clear)`) + a **source-grep / Minecraft-free test split** (behavioral truth-table and NBT-codec round-trip tests that never Bootstrap, plus `*ContractTest` source-string pins and file-scan consistency sweeps) — and it is **mixin-free** (palette behaviors hang off existing `FocusBehavior` hooks and the existing `ServerLivingEntityEvents.AFTER_DAMAGE` handler only).

---

## READ THIS FIRST — load-bearing constraints

1. **Test classpath CANNOT Bootstrap Minecraft.** `new ItemStack(item)` / registry construction / registry-codecs FAIL with `"Components not bound yet"`. Behavioral tests must be Minecraft-free, modelling Foci as `String` ids (copy `PresetApplicationResolverTest`). Everything runtime-flavored uses source-grep contract tests that assert the source file `contains(...)` literal strings, plus the smoke check.
2. **Before editing ANY existing source file, grep `src/test` for strings it contains.** The suite pins literal source strings; if an edit changes a pinned string, update the pinning test in the SAME commit, preserving the assertion intent (never delete it).
3. **Mixin `@At` targets are strings the compiler cannot validate.** This feature is mixin-FREE (`FocusBehavior` hooks + the existing `AFTER_DAMAGE` handler only) — keep it that way. Any mixin change would require `python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60`.
4. **Verify unfamiliar Minecraft API names in this fork by extracting strings from `~/.gradle/caches/fabric-loom/26.1.2/minecraft-client.jar`** (no `javap`). Forked client mappings: `GuiGraphicsExtractor`/`extractBackground`/`extractLabels`, `keyPressed(KeyEvent)`, `mouseClicked(MouseButtonEvent, boolean)`, `event.modifiers()` returns an `int` GLFW bitmask.
5. **Clicks outside a screen logical `imageWidth x imageHeight` window drop the carried item** — only relevant if touching screens; journal/HUD are render-only. This feature touches neither, so it is not a concern here.
6. **Never `git add -A` / `git add .`** — repo root has scratch dirs (`.codex-remote-attachments/`, `.superpowers/`, `assets/`, `tmp/`) and `docs/superpowers/assets/` is Git LFS. Stage explicit paths only.
7. **Run order per task:** write failing test → focused red run (`.\gradlew.bat test --tests dev.attuned.<FQCN> --no-daemon`) → implement → focused green run. **Full gate before release:** `.\gradlew.bat cleanTest build --no-daemon ; python tools/verify_repository.py ; python -m unittest discover -s tests ; python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60 ; git diff --check`.
8. **Per-player static maps MUST register cleanup in `init()`:** `AttunedPlayerCleanup.onForget(MAP::remove)` and `AttunedServerCleanup.onStop(MAP::clear)` (see `Pacts.init` / `MossheartBehavior.initLifecycle`). Prefer **stateless** palette factories so this is N/A.
9. **Lang + docs ride along:** every player-visible addition needs `assets/attuned/lang/en_us.json` keys AND a row/sentence in `docs/reference.md`. `AttunedTooltips` auto-appends `item.attuned.<path>.lore`/`.lore2`/`.effect` for every attuned item — any new item missing those shows RAW KEYS in game.
10. **Changelog:** append bullets under a `## Attuned <version>` heading at the TOP of `CHANGELOG.md` as part of the work. The Modrinth task and `tools/publish_curseforge.py` parse it. Do not bump `mod_version` until the final release task. Never attribute Claude in any VCS artifact.
11. **Known hard facts from source (use verbatim):** `AttunedRegistries.FOCUS_DEFINITIONS` is a `ResourceKey<Registry<FocusDefinition>>`. Behaviors are a hand-rolled `Map<Identifier,FocusBehavior>` via `AttunedRegistries.registerBehavior`/`getBehavior` — `getBehavior(Identifier)` is the SINGLE behavior-id resolution point (5 call sites: `AttunedCommands:153`, `AttunedEffects:273` & `:291`, `FocusAbilityState:90`, `FociHud:182`). `AttunedAttachments.MILESTONES` is a `List<String>` persistent+copyOnDeath attachment (precedent for a discovery set). `AttunedAttachments` has `sawOnboarding`/`markOnboarding` used by `Pacts.maybeFanfare`. `FocusDefinition`: `Holder<Item> item`, `int cost(0..64)`, `boolean unique`, `Optional<Affinity> affinity`, `Optional<Identifier> faction`, `List<ModifierEntry> modifiers`, `Optional<Identifier> behavior`; CODEC uses `optionalFieldOf` (field order: `item, cost, unique, affinity, faction, modifiers, behavior`). `ModifierEntry(Holder<Attribute>, double amount, Operation)`. `Attunement.activeSlots(player)`/`definitionFor(player,stack)`/`resolution(player cached server-side)`. `Pacts` is the set-bonus precedent (tick %, transitions, announce, advancements, fanfare, previewOf, cleanup).

**Fork API names already VERIFIED for this plan (extracted from the loom jar — do not re-derive):**
- `net/minecraft/core/Holder.class` → **`isBound`** exists (no `isValid`). Use `!def.item().isBound()` as the unresolved-item guard.
- `AttributeModifier$Operation` enum constants → **`add_value`**, **`add_multiplied_base`**, **`add_multiplied_total`**.
- `net/minecraft/locale/Language.class` → **`getInstance`**, **`has`**, **`getOrDefault`** all exist server-side. Use `net.minecraft.locale.Language.getInstance().has(key)` for the lang-key probe.
- `net/minecraft/core/registries/BuiltInRegistries.class` → **`MOB_EFFECT`** field exists; use `BuiltInRegistries.MOB_EFFECT.holderByNameCodec()`.
- `net/minecraft/world/entity/Entity.class` → **`isUnderWater`**, **`isEyeInFluid`**, **`isInWater`** all exist. Canonical `underwater` semantics for this feature = **`player.isUnderWater()`** (eye-submersion), documented in `FocusCondition` javadoc; the unit test only exercises the boolean field.
- `net/minecraft/world/level/LevelReader.class` → **`getMaxLocalRawBrightness`** and **`getRawBrightness`** exist (declared on `LevelReader`, not `Level`). Use `player.level().getMaxLocalRawBrightness(blockPos)` in the production `Context` builder.
- `items/<name>.json` client item-definition schema in this fork is **`{"model": {"type": "minecraft:model", "model": "attuned:item/<name>"}}`** (NOT a `layer0` form — that's the `models/item/<name>.json` shape).

---

### Task 1: Public contract docs + `/attuned validate` for author packs + example datapack (Phase 1)

This is **Phase 1** of `docs/superpowers/specs/2026-06-12-datapack-defined-foci-design.md`: no new runtime behavior, no palette, no generic items. It (a) documents the already-works path (existing-item Foci + attribute-only Foci via `ModifierEntry`), (b) extends `AttunedCommands.validateContent` (`AttunedCommands.java:141-173`, the only `/attuned validate` code) to validate **author** packs file-by-file, and (c) ships a 2–3 Focus worked example datapack under `docs/` plus `docs/authoring-foci.md`.

**Load-bearing notes for this task:**
- Tests cannot Bootstrap — `ValidateCommandContractTest` and `ExampleDatapackContractTest` are **source-grep + file-scan** tests. Do **not** call `validateContent` directly from a test (it needs a `CommandSourceStack` + a live registry).
- The example pack lives under `docs/` so it ships in the repo but **not** in the mod jar, and is **not** under `src/main/resources/data/attuned/attuned/focus/` — so `verify_repository.py`'s focus-count gate (counts `*.json` there, must equal README "N Foci") does **not** fire. The example pack JSONs under `docs/` are intentionally **not** scanned by `verify_repository.py`'s `check_src_json()` (which scans only `src/main/resources/**/*.json`); authors own their correctness. Keep the example JSONs strict (no comments/trailing commas) in case the gate ever widens.
- **Example-pack naming decision (resolved):** the design doc (§ "Worked author example", lines 153–164) uses `frostward_focus.json` under `data/mypack/…` with `"item": "attuned:custom_focus_3"`. Follow that pattern: example foci live under a **non-`attuned` namespace** `data/example/attuned/focus/` with descriptive ids; in Phase 1 (no generic items yet) they reuse already-shipped `attuned:*` and `minecraft:*` items. File names below use an `example_` prefix purely for in-repo clarity.

**Files:**
- Create: `src/test/java/dev/attuned/command/ValidateCommandContractTest.java`
- Create: `src/test/java/dev/attuned/content/ExampleDatapackContractTest.java`
- Modify after red: `src/main/java/dev/attuned/command/AttunedCommands.java` (extend `validateContent`)
- Modify (only if a new player-visible literal is added): `src/main/resources/assets/attuned/lang/en_us.json`
- Create: `docs/authoring-foci.md`
- Modify: `docs/adding-a-focus.md` (cross-link contributor vs author paths — do not rewrite)
- Modify: `docs/reference.md` (expand the `/attuned validate` row + add an authoring pointer)
- Create: `docs/example-pack/data/example/attuned/focus/example_warding_focus.json`
- Create: `docs/example-pack/data/example/attuned/focus/example_swift_focus.json`
- Create: `docs/example-pack/data/example/attuned/focus/example_tide_focus.json`
- Create: `docs/example-pack/assets/example/lang/en_us.json`
- Create: `docs/example-pack/README.md`
- Modify: `CHANGELOG.md` (`### Added` bullet under the active version heading)

Steps:

- [ ] **Read + grep before touching `validateContent`.** Read `AttunedCommands.java:141-173` and `src/main/java/dev/attuned/api/focus/FocusDefinition.java` in full (codec field order `item, cost, unique, affinity, faction, modifiers, behavior`). Grep `src/test/java` for `validateContent`, `"Attuned validation"`, `Missing behavior`, `Duplicate FocusDefinition`. There is no current pin on `validateContent`, but if any exists, your new test must coexist and you must update pins with intent preserved in the same commit.
- [ ] **Write the failing `ValidateCommandContractTest` (source-grep, Minecraft-free).** Create `src/test/java/dev/attuned/command/ValidateCommandContractTest.java`. Read `AttunedCommands.java` once via `Files.readString(Path.of("src/main/java/dev/attuned/command/AttunedCommands.java"), StandardCharsets.UTF_8)` and isolate the `validateContent` body with `methodBody(source, "private static int validateContent(CommandSourceStack source)")` (copy the brace-matcher verbatim from `ThornwardReflectionContractTest.java:75-93`). Assert (`import static org.junit.jupiter.api.Assertions.*;`):
  - **Per-focus reporting against the item key.** Body still contains `registry.listElements().forEach(` and qualifies each problem with `BuiltInRegistries.ITEM.getKey(def.item().value())`. Message: `"validate must report each Focus problem against its item key so authors can locate the failing file."`
  - **Behavior resolution via the single lookup point.** Body contains `AttunedRegistries.getBehavior(behaviorId) == null`. Message: `"validate must resolve every FocusDefinition behavior id through AttunedRegistries.getBehavior."`
  - **NEW: unresolved-item path.** Body contains the air/unbound guard pinned as `!def.item().isBound()` (VERIFIED: `Holder.isBound` exists in this fork; no `isValid`). Message: `"validate must flag a Focus whose item failed to resolve to a real item."`
  - **NEW: missing-lang warning, not failure.** Body declares a separate `List<String> warnings` collection (assert literal `List<String> warnings`) and contains the lang-key prefix `"item." +`. Message: `"Missing lang keys must be warnings, not validation failures."`
  - **NEW: success surfaces warnings.** The success branch still contains `"Attuned validation passed: "` and the body emits a line containing `warnings.size()`. Message: `"A pack that passes with only lang warnings must still surface the warning count."`
  - **Truncation preserved.** Body keeps `Math.min(8, problems.size())`. Message: `"validate must keep capping the printed problem list."`
- [ ] **Write the failing `ExampleDatapackContractTest` (file-scan, Minecraft-free).** Create `src/test/java/dev/attuned/content/ExampleDatapackContractTest.java`. Copy the path-constant + `Files.list(...).filter(p -> p.toString().endsWith(".json")).sorted().toList()` sweep idiom from `FocusDataConsistencyTest.java:52-65`. Declare:
  ```java
  private static final Path EXAMPLE_FOCUS_DIR = Path.of("docs/example-pack/data/example/attuned/focus");
  private static final Path EXAMPLE_LANG = Path.of("docs/example-pack/assets/example/lang/en_us.json");
  private static final Path SHIPPED_BEHAVIORS = Path.of("src/main/java/dev/attuned/content/AttunedFocusBehaviors.java");
  private static final Path ATTUNED_CONTENT = Path.of("src/main/java/dev/attuned/content/AttunedContent.java");
  ```
  Assert:
  - **Pack exists, 2–3 foci.** `assertTrue(Files.isDirectory(EXAMPLE_FOCUS_DIR), ...)`; `int n = files.size(); assertTrue(n >= 2 && n <= 3, "The worked example pack ships 2–3 foci so authors get a real but minimal template.")`.
  - **Every `item` points at a shipped registered Focus item OR a `minecraft:` item.** Parse each focus JSON with `JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()`; read `root.get("item").getAsString()`. Build the shipped item-id set from `AttunedContent.java` using the `REGISTERED_FOCUS` pattern (reuse from `FocusDataConsistencyTest.java:67-68` → `"attuned:" + matcher.group(2)`). Assert each referenced `item` is in that set or starts with `minecraft:`. For the one vanilla example, pin it explicitly (`assertEquals("minecraft:feather", ...)` for `example_swift_focus.json`). Message: `"Every example Focus must reference a real registered item so the pack loads without a JAR change."`
  - **One attribute-only Focus and one behavior Focus.** Assert one focus JSON has a `modifiers` array whose entry has a `minecraft:` `attribute` and an `operation` in `{add_value, add_multiplied_base, add_multiplied_total}` (VERIFIED operation spellings). Assert one focus JSON has a `behavior` string whose bare id (after stripping namespace) resolves in `AttunedFocusBehaviors.java` via the `REGISTERED_BEHAVIOR` pattern (`FocusDataConsistencyTest.java:69-71`). Message: `"The example pack must show both the attribute-only lane and the shipped-behavior lane."`
  - **Every focus has name + lore + lore2 + effect in the pack lang file.** Load `EXAMPLE_LANG` as a `JsonObject`. The pack keys its own display names; pin the convention to match the lang file you ship in the next step (key = `"item.example.<focus_file_basename>"`). Assert name + `.lore` + `.lore2` + `.effect` per focus. Message: `"The example pack must ship name + lore + effect keys so it has no raw-key footgun out of the box."`
  - **No example focus references an unregistered behavior.** Collect every `behavior` id; assert each bare id is in the shipped behavior set. Message: `"Every behavior the example pack references must be a shipped, registered behavior."`
- [ ] **Run both new tests red.**
  ```powershell
  .\gradlew.bat test --tests dev.attuned.command.ValidateCommandContractTest --tests dev.attuned.content.ExampleDatapackContractTest --no-daemon
  ```
  Both must fail for the right reason (missing `validateContent` extensions; missing `docs/example-pack/`), not a compile error in the tests.
- [ ] **Implement the example datapack (greens `ExampleDatapackContractTest`).** Under `docs/example-pack/data/example/attuned/focus/`:
  - `example_warding_focus.json` (attribute-only lane, reuses a shipped item — confirm `aegis_focus` is registered via `Grep` `registerFocus("aegis_focus")` in `AttunedContent.java`):
    ```json
    { "item": "attuned:aegis_focus", "cost": 4, "affinity": "bastion",
      "modifiers": [ { "attribute": "minecraft:armor", "amount": 2, "operation": "add_value" } ] }
    ```
  - `example_swift_focus.json` (attribute-only lane, vanilla item — cross-item path):
    ```json
    { "item": "minecraft:feather", "cost": 2,
      "modifiers": [ { "attribute": "minecraft:movement_speed", "amount": 0.1, "operation": "add_multiplied_base" } ] }
    ```
  - `example_tide_focus.json` (shipped-behavior lane — confirm `register("tide"` in `AttunedFocusBehaviors.java`):
    ```json
    { "item": "attuned:tide_focus", "cost": 3, "behavior": "attuned:tide" }
    ```
  Create `docs/example-pack/assets/example/lang/en_us.json` with `item.example.example_warding_focus` (+`.lore`/`.lore2`/`.effect`) and likewise for `example_swift_focus` and `example_tide_focus`. Create `docs/example-pack/README.md`: copy `data/` into `<world>/datapacks/attuned-example/`, copy `assets/` into a resource pack, `/reload`, then `/attuned validate`. Re-run only `ExampleDatapackContractTest`:
  ```powershell
  .\gradlew.bat test --tests dev.attuned.content.ExampleDatapackContractTest --no-daemon
  ```
- [ ] **Implement the `validateContent` extension (greens `ValidateCommandContractTest`).** Edit `AttunedCommands.java`, method `validateContent` (`141-173`). Keep the existing duplicate-item and behavior-existence checks. Inside the `registry.listElements().forEach(...)` loop, per focus:
  - `var itemKey = BuiltInRegistries.ITEM.getKey(def.item().value());` — reuse in every message for that focus.
  - **Item resolution check:** `if (!def.item().isBound()) { problems.add("Focus item failed to resolve: " + itemKey); }` (VERIFIED `Holder.isBound`).
  - **Lang-key warning:** declare `List<String> warnings = new ArrayList<>();` alongside `problems`. Compute the display-name key `String key = "item." + itemKey.getNamespace() + "." + itemKey.getPath();` and probe with the VERIFIED server-side accessor: `if (!net.minecraft.locale.Language.getInstance().has(key)) { warnings.add("Missing display-name lang key (expected " + key + ") for " + itemKey); }`. (VERIFIED: `Language.getInstance().has(...)` exists server-side in this fork — no client-only fallback needed.)
  - **Output:** on `problems.isEmpty()`, keep `"Attuned validation passed: " + byItem.size() + " Focus definitions checked."`, then if `!warnings.isEmpty()` emit a non-failing line `source.sendSuccess(() -> Component.literal("Attuned validation: " + warnings.size() + " warning(s) (missing lang keys)."), false);` and print up to `Math.min(8, warnings.size())` warnings. On failure, keep the existing `problems` print with the `Math.min(8, problems.size())` cap and additionally print warnings under the same cap. Return `byItem.size()` on pass, `0` on fail (both unchanged).
- [ ] **Run both tests green.**
  ```powershell
  .\gradlew.bat test --tests dev.attuned.command.ValidateCommandContractTest --tests dev.attuned.content.ExampleDatapackContractTest --no-daemon
  ```
- [ ] **Lang + docs ride-along.** The `validateContent` messages stay as admin `Component.literal(...)` strings (matching the rest of the method), so **no** new `en_us.json` keys are needed — note this explicitly and skip the lang edit. Then:
  - `docs/reference.md`: expand the `/attuned validate` row (around line 279) to say it now validates author packs file-by-file (item, behavior, and attribute resolution; missing lang keys warned) and add a one-line pointer to `docs/authoring-foci.md`.
  - Create `docs/authoring-foci.md`: the **author** (JAR-free) walkthrough, distinct from the contributor-facing `docs/adding-a-focus.md`. Cover only the Phase-1 already-works lanes: (a) reuse an existing item, (b) attribute-only Foci via `modifiers`/`ModifierEntry` (link `reference.md#attribute-modifiers`), (c) reference a shipped `behavior` id, (d) supply name/lore via a resource-pack lang file, (e) run `/attuned validate` and read its file-by-file output. Point at `docs/example-pack/`. Explicitly defer the palette + generic-item pool to later phases (cite the design doc phasing).
  - `docs/adding-a-focus.md`: add a short top note distinguishing "editing the JAR (this guide, for contributors)" from "datapack authoring without a JAR (see `docs/authoring-foci.md`)". Do **not** rewrite the recipe.
- [ ] **Full suite + repo gate (no smoke/runClient — Phase 1 adds no runtime behavior or mixins).**
  ```powershell
  .\gradlew.bat cleanTest test --no-daemon
  python tools/verify_repository.py
  ```
  Confirm the focus-count gate is unchanged (example pack lives under `docs/`, not `src/main/resources/data/.../focus/`), all JSON under `docs/example-pack/` parses, and no `TODO/FIXME/HACK` markers were introduced. There is **no** `runClient`/`minecraft_runtime_smoke.py` gate for this task: `validateContent` is admin-command-only, command registration shape is unchanged, and no mixin `@At` target was touched.
- [ ] **Changelog.** Add an `### Added` bullet under the active version heading at the TOP of `CHANGELOG.md`: **"Datapack Focus authoring (Phase 1)"** — `/attuned validate` now checks author packs file-by-file (item, behavior, and attribute resolution; missing-lang-key warnings), plus a worked example datapack (`docs/example-pack/`) and an author walkthrough (`docs/authoring-foci.md`). Do not bump `mod_version`.

---

### Task 2: Generic skinnable Focus item pool (Phase 2)

Registers 16 blank, resource-pack-skinnable Focus items `attuned:custom_focus_1..16` so a datapack author can point a `focus/<name>.json` at one and skin its name/model/texture/lore in a resource pack — no Java, no JAR. These items intentionally ship **no** `data/attuned/attuned/focus/<name>.json` definition (that's the author's job, and shipping one would inflate the README focus-count gate), so they need their own explicit creative-tab accept path and their own consistency test.

**Vanilla constraint (justifies the pool):** MC 26.1.2 cannot register `Item`s from datapack — items are minted only via `Registry.register(BuiltInRegistries.ITEM, key, item)` in `AttunedContent` (`registerFocus`/`register`, `AttunedContent.java:136-159`). A pure datapack can only reuse an existing item's name/model. A single "one item, data-defined model" approach is rejected because vanilla keys the item model by item id, not by a data component — so N pre-registered blank items, each with its own id and therefore its own resource-pack-overridable `models/item/custom_focus_N.json`, is the only vanilla-aligned answer.

**Files:**
- Modify: `src/main/java/dev/attuned/content/AttunedContent.java` (register the 16 via `registerFocus`; expose `public static final List<Item> CUSTOM_FOCI`)
- Modify: `src/main/java/dev/attuned/content/AttunedCreativeTabs.java` (explicitly `output.accept()` the pool in the utility tab)
- Modify: `src/main/resources/assets/attuned/lang/en_us.json` (default name + `.lore`/`.lore2`/`.effect` for all 16)
- Modify: `tools/generate_ui_art.py` (add `generate_custom_focus_textures()` rendering 16 distinct 16x16 PNGs + the model/item JSONs)
- Modify: `tools/verify_repository.py` (add a count gate asserting exactly 16 `custom_focus_*` artifacts of each kind)
- Create (×16, generated): `src/main/resources/assets/attuned/items/custom_focus_N.json`
- Create (×16, generated): `src/main/resources/assets/attuned/models/item/custom_focus_N.json`
- Create (×16, generated): `src/main/resources/assets/attuned/textures/item/custom_focus_N.png` (16x16, static)
- Create: `src/test/java/dev/attuned/content/GenericFocusItemContractTest.java`

Steps:

- [ ] **Read the exact item-definition schema before generating any files.** Read `src/main/resources/assets/attuned/items/swift_focus.json` and `src/main/resources/assets/attuned/models/item/swift_focus.json`. (VERIFIED for this fork: the `items/<name>.json` shape is `{"model": {"type": "minecraft:model", "model": "attuned:item/<name>"}}` — NOT a `layer0` form. The `models/item/<name>.json` shape is `{"parent": "minecraft:item/generated", "textures": {"layer0": "attuned:item/<name>"}}`.) The `ITEM_DEFINITION_DIR` constant at `FocusDataConsistencyTest.java:54-55` confirms one `items/` file ships per Focus.
- [ ] **RED — write `GenericFocusItemContractTest` with exact pinned assertions.** Create `src/test/java/dev/attuned/content/GenericFocusItemContractTest.java` (package `dev.attuned.content`, JUnit 5, `import static org.junit.jupiter.api.Assertions.*;`, source-grep style — Minecraft-free, mirroring `FocusDataConsistencyTest`). Declare:
  - `private static final int POOL_SIZE = 16;`
  - `CONTENT_SOURCE = Path.of("src/main/java/dev/attuned/content/AttunedContent.java")`, `CREATIVE_TABS_SOURCE = Path.of("src/main/java/dev/attuned/content/AttunedCreativeTabs.java")`, `ITEM_DEFINITION_DIR = Path.of("src/main/resources/assets/attuned/items")`, `ITEM_MODEL_DIR = Path.of("src/main/resources/assets/attuned/models/item")`, `ITEM_TEXTURE_DIR = Path.of("src/main/resources/assets/attuned/textures/item")`, `LANG_FILE = Path.of("src/main/resources/assets/attuned/lang/en_us.json")`.

  Test methods (exact messages):
  - `poolRegistersSixteenGenericFociViaRegisterFocus()` — read `CONTENT_SOURCE`; for `n` in `1..16` assert `source.contains("registerFocus(\"custom_focus_" + n + "\")")` msg `"Generic Focus custom_focus_" + n + " must be registered via the registerFocus idiom"`. Then `Pattern.compile("registerFocus\\(\"custom_focus_\\d+\"\\)")` must find exactly `POOL_SIZE` matches — `assertEquals(POOL_SIZE, matches, "The generic Focus pool must register exactly 16 custom_focus items")`.
  - `genericFociAreExposedInACreativeTab()` — read `CREATIVE_TABS_SOURCE`; `assertTrue(source.contains("AttunedContent.CUSTOM_FOCI"), "The generic Focus pool must be accepted into a creative tab; they have no FocusDefinition so fociInDisplayOrder will not surface them")`.
  - `genericFociHaveItemDefinitionModelAndTextureAssets()` — for `n` in `1..16`, `name = "custom_focus_" + n`: `assertTrue(Files.isRegularFile(ITEM_DEFINITION_DIR.resolve(name + ".json")), "Generic Focus should have an item definition asset: " + name)`; same for `ITEM_MODEL_DIR.resolve(name + ".json")` and `ITEM_TEXTURE_DIR.resolve(name + ".png")`. **Do NOT** assert a `.png.mcmeta` and **do NOT** call `assertAnimatedFocusTexture` — the pool ships static 16x16 textures, not 64x512 animated ones.
  - `genericFocusModelPointsAtItsOwnTexture()` — for each `n`, parse `ITEM_MODEL_DIR.resolve("custom_focus_" + n + ".json")`; assert `parent` equals `"minecraft:item/generated"` and `textures.layer0` equals `"attuned:item/custom_focus_" + n`, msg `"Generic Focus model must reference its own per-item texture so a resource pack can override it independently: " + name`.
  - `genericFocusItemDefinitionPointsAtItsOwnModel()` — for each `n`, parse `ITEM_DEFINITION_DIR.resolve("custom_focus_" + n + ".json")`; assert it has a `model` object whose nested `model` equals `"attuned:item/custom_focus_" + n` and whose `type` equals `"minecraft:model"` (matches the VERIFIED fork schema). Msg `"Generic Focus item definition must point at its own model: " + name`.
  - `genericFociShipDefaultLangSoTheyAreNotRawKeys()` — load `LANG_FILE` as a `JsonObject`; for each `n`, `key = "item.attuned.custom_focus_" + n`, assert the object has `key`, `key + ".lore"`, `key + ".lore2"`, `key + ".effect"`, msg e.g. `"Generic Focus must ship a default name so it is not a raw translation key: " + key`.
  - `genericFociShipNoBundledFocusDefinition()` — for each `n`, assert `!Files.exists(Path.of("src/main/resources/data/attuned/attuned/focus/custom_focus_" + n + ".json"))` msg `"Generic Focus items must NOT ship a bundled FocusDefinition; authors supply it, and a shipped one would inflate the README Focus-count gate"`.
- [ ] **RED run.** `.\gradlew.bat test --tests dev.attuned.content.GenericFocusItemContractTest --no-daemon`. Expect failures on missing source/assets/lang, not compile errors. Fix any test compile error first.
- [ ] **GREEN — register the 16 items in `AttunedContent`.** The `FOCI`/`FOCI_SET` snapshots are taken at `AttunedContent.java:118-119` (`public static final List<Item> FOCI = List.copyOf(REGISTERED_FOCI);` / `private static final Set<Item> FOCI_SET = Set.copyOf(REGISTERED_FOCI);`). **The pool field must be assigned BEFORE line 118** so the generic items are inside `REGISTERED_FOCI` when the snapshots freeze (otherwise `isFocus(...)` at `:142-147` silently excludes them). Add, above line 118:
  ```java
  /** Sixteen blank, resource-pack-skinnable Focus items for datapack authors. */
  public static final List<Item> CUSTOM_FOCI = registerCustomFocusPool();

  private static List<Item> registerCustomFocusPool() {
  	List<Item> pool = new ArrayList<>();
  	for (int n = 1; n <= 16; n++) {
  		pool.add(registerFocus("custom_focus_" + n));
  	}
  	return List.copyOf(pool);
  }
  ```
  They use `registerFocus(...)` so they land in `REGISTERED_FOCI`/`FOCI_SET` and `isFocus(...)` returns true (HUD/tooltip/Reliquary gate on `isFocus`). Confirm the emitted source string matches the test regex literally (`registerFocus("custom_focus_1")` …).
- [ ] **GREEN — accept the pool into the utility creative tab.** In `AttunedCreativeTabs` the `displayItems` lambda only emits items from `fociInDisplayOrder(lookup, include)`, sourced from `FOCUS_DEFINITIONS` — the generic items have **no** `FocusDefinition`, so they never appear there. Add an explicit accept inside the `if (includeCoreItems)` block (the utility tab), after the existing core `output.accept(...)` lines:
  ```java
  for (Item customFocus : AttunedContent.CUSTOM_FOCI) {
  	output.accept(customFocus);
  }
  ```
  This satisfies `genericFociAreExposedInACreativeTab()`'s `contains("AttunedContent.CUSTOM_FOCI")`.
- [ ] **VERIFY the generator entrypoint, then GREEN — generate the 16 default textures + model + item JSON deterministically.** First read `tools/generate_ui_art.py`'s `__main__`/`main()` block and confirm whether it auto-calls each generator; if there is a `main()` that dispatches, you must wire `generate_custom_focus_textures()` into it (the function will not run otherwise). Add `generate_custom_focus_textures()` that, for `n` in `1..16`, draws a distinct 16x16 RGBA PNG (reuse the existing `bevel`/`inset`/`deterministic_speckles`/amethyst palette helpers; vary the accent hue per `n` deterministically, e.g. `hue = (n * 360 // 16)`, so all 16 differ and re-running is byte-stable) to `TEXTURES / "item" / f"custom_focus_{n}.png"`. In the same run emit the model JSON to `models/item/custom_focus_{n}.json`:
  ```json
  {"parent": "minecraft:item/generated", "textures": {"layer0": "attuned:item/custom_focus_N"}}
  ```
  and the item-definition JSON to `items/custom_focus_{n}.json` using the VERIFIED fork schema:
  ```json
  {"model": {"type": "minecraft:model", "model": "attuned:item/custom_focus_N"}}
  ```
  Run `python tools/generate_ui_art.py` and confirm 16 PNGs + 16 model JSONs + 16 item JSONs exist.
- [ ] **GREEN — add default lang for all 16.** In `src/main/resources/assets/attuned/lang/en_us.json` (flat, dot-keyed) add four keys per item, e.g. for `custom_focus_1`:
  ```json
  "item.attuned.custom_focus_1": "Custom Focus 1",
  "item.attuned.custom_focus_1.lore": "A blank Focus awaiting a maker's hand.",
  "item.attuned.custom_focus_1.lore2": "Skin its name, art, and lore with a resource pack.",
  "item.attuned.custom_focus_1.effect": "Behavior and attributes are defined by your datapack.",
  ```
  Repeat for `2..16`. These satisfy `genericFociShipDefaultLangSoTheyAreNotRawKeys()`.
- [ ] **GREEN — add the pool count gate to `verify_repository.py`.** Add `check_custom_focus_pool()` (registered into the same checks list as `check_readme_focus_count`) asserting exactly 16 of each artifact and that counts agree: `len(glob "assets/attuned/textures/item/custom_focus_*.png") == 16`, same for `models/item/custom_focus_*.json` and `items/custom_focus_*.json`; raise `CheckFailed("Custom Focus pool", [...])` on mismatch; return `"Custom Focus pool: 16 generic Foci"`. NOTE: the existing `check_png_resources()` already validates PNG headers + the 8192 dimension cap — no change there. NOTE: `check_readme_focus_count()` counts only `data/attuned/attuned/focus/*.json`; the pool ships **no** such file, so the README "61 Foci" number does not move — confirm `python tools/verify_repository.py` still reports the same Focus count.
- [ ] **GREEN run + regression guard.** `.\gradlew.bat test --tests dev.attuned.content.GenericFocusItemContractTest --no-daemon` is green. Then `.\gradlew.bat test --tests dev.attuned.content.FocusDataConsistencyTest --no-daemon`. The `REGISTERED_FOCUS` regex (`FocusDataConsistencyTest.java:67-68`) matches only `[A-Z0-9_]+_FOCUS = registerFocus("[a-z0-9_]+_focus")` — i.e. the *field* must end `_FOCUS` and the *id* must end `_focus`. The pool's ids `custom_focus_N` **end with a digit**, which fails the `_focus`-suffix requirement of the `REGISTERED_FOCUS` regex; that digit suffix is the exclusion mechanism. The field `CUSTOM_FOCI` also does not match the `*_FOCUS` field pattern. So the pool is invisible to that sweep and is not forced to ship animated textures or `FocusDefinition`s. Confirm `FocusDataConsistencyTest` stays green; do **not** weaken the regex if it unexpectedly catches the pool — that would be a real failure to resolve.
- [ ] **Repository gate.** `python tools/verify_repository.py` passes: `check_custom_focus_pool` reports `16 generic Foci`, PNG header count rises by 16, README Focus count unchanged.
- [ ] **Smoke / runClient gate.** Run `.\gradlew.bat runClient` (or `python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60` if that is the wired headless gate — VERIFY which). In a creative world: confirm the 16 `custom_focus_N` items appear in the Attuned utility tab with their default name/texture, with no missing-texture (magenta/black) placeholder and no purple-and-black model error. Then drop a dev datapack `focus/test_custom.json` pointing `"item": "attuned:custom_focus_3"` and confirm it resolves as a Focus (HUD/tooltip render via `isFocus`). If `runClient` is not feasible here, substitute the headless smoke and note the visual check is deferred to manual QA. Tear down the dev datapack so no test resource leaks into the count gate.

---

### Task 3: Behavior palette v1 — `conditional_mob_effect` + `FocusCondition` registry + code-first-then-data resolution (Phase 3)

Adds a parameterized behavior palette as a **second source** of named `FocusBehavior`s, resolved by the *same* `behavior` id a `FocusDefinition` already carries. `FocusDefinition` is **not** modified (stays the 7-field record at `FocusDefinition.java:21-52`). Resolution becomes code-first-then-data by changing the **single** `AttunedRegistries.getBehavior(Identifier)` method (`AttunedRegistries.java:35-37`) — the one funnel through which all 5 call sites flow, so no per-site edits are needed. v1 is **passive-only** (`hasActiveAbility()` stays `false`, code-only). The `FocusCondition` predicate logic is factored from `HarborlightBehavior.nearWater`, `MossheartBehavior.isGreenFooting`/`onGreenFooting`, and `KilnwardBehavior.nearHeat`/`isHeat` so code and data behaviors share one impl.

**Package decision (resolved):** place `ConditionalMobEffectBehavior` directly in `dev.attuned.content.behavior` (same package as `PassiveEffectRefresher`), **not** in a `.palette` subpackage, so `PassiveEffectRefresher` stays package-private. The palette **dispatch/type registry** (`PaletteType`) and the datapack payload record (`FocusBehaviorDefinition`) may live in `dev.attuned.content.behavior.palette`; only the behavior class that calls the package-private refresher must co-locate with it.

**Files:**
- Create `src/main/java/dev/attuned/api/focus/FocusCondition.java` — composable predicate over a `FocusCondition.Context`; `Codec<FocusCondition>` dispatched on a `"type"` field; built-in types `in_rain`, `underwater`, `low_light`, `bright_light`, `on_block_tag`, `in_biome_tag`, `sneaking`, `near_block`.
- Create `src/main/java/dev/attuned/api/focus/FocusConditionContext.java` (or a nested `FocusCondition.Context`) — a thin, mostly-Minecraft-free value snapshot (`boolean raining`, `boolean underwater`, `int lightLevel`, `boolean sneaking`, plus functional tag/block probes) so truth-table tests stay Bootstrap-free.
- Create `src/main/java/dev/attuned/content/behavior/ConditionalMobEffectBehavior.java` — v1 palette behavior; constructed from `(Holder<MobEffect> effect, int amplifier, int durationTicks, int refreshTicks, FocusCondition condition)`; `onTick` evaluates the condition and refreshes via `PassiveEffectRefresher` (co-located so the package-private refresher is reachable).
- Create `src/main/java/dev/attuned/content/behavior/palette/PaletteType.java` — registry of palette type ids → factory building a `FocusBehavior`; entry `attuned:conditional_mob_effect`.
- Create `src/main/java/dev/attuned/content/behavior/palette/FocusBehaviorDefinition.java` — the datapack registry payload record + `Codec`; `type` field + per-type params; `build()` → `FocusBehavior`.
- Modify `src/main/java/dev/attuned/AttunedRegistries.java` — add `FOCUS_BEHAVIORS` datapack `ResourceKey<Registry<FocusBehaviorDefinition>>` (registry path `"focus_behavior"`); change `getBehavior(Identifier)` to code-first then data fallback.
- Modify `src/main/java/dev/attuned/Attuned.java` — add `DynamicRegistries.registerSynced(AttunedRegistries.FOCUS_BEHAVIORS, FocusBehaviorDefinition.CODEC);` immediately after the existing line 40 `FOCUS_DEFINITIONS` registration (both register in `onInitialize()` before content init at line 47).
- Create `src/test/java/dev/attuned/api/focus/FocusConditionTruthTableTest.java` — Minecraft-free predicate truth tables.
- Create `src/test/java/dev/attuned/content/behavior/palette/FocusBehaviorDefinitionCodecRoundTripTest.java` — **condition-only** Minecraft-free codec round-trip + unknown-type rejection (do NOT Bootstrap; the `Holder<MobEffect>` effect field is pinned by the contract test, not round-tripped here).
- Create `src/test/java/dev/attuned/content/behavior/palette/PaletteResolutionContractTest.java` — source-grep contract pinning code-first-then-data and back-compat.

Steps:

- [ ] **(red — condition truth tables)** Create `FocusConditionTruthTableTest.java` (JUnit 5, `import static org.junit.jupiter.api.Assertions.*;`, no Bootstrap, mirror `PresetApplicationResolverTest`). Build `FocusCondition.Context` instances by hand from primitive inputs; assert each predicate with these EXACT messages:
  - `inRainTrueOnlyWhenRaining()`: `assertTrue(FocusCondition.inRain().test(ctx(/*raining*/ true, ...)), "in_rain is true while the player stands in rain.");` and `assertFalse(..., "in_rain is false under clear sky.");`
  - `underwaterTrueOnlyWhenEyesSubmerged()`: `"underwater is true only when the player's eyes are submerged."`
  - `lowLightTrueAtOrBelowThreshold()`: `"low_light is true at or below its light threshold."` — include the **inclusive boundary** case: `assertTrue(FocusCondition.lowLight(5).test(ctx(/*light*/ 5, ...)), "low_light is true AT the threshold (5).")`.
  - `brightLightTrueAtOrAboveThreshold()`: `"bright_light is true at or above its light threshold."` — include `assertTrue(FocusCondition.brightLight(5).test(ctx(/*light*/ 5, ...)), "bright_light is true AT the threshold (5).")`. (Both true at the same value pins `<=`/`>=`, defeating a future `<`/`>` regression.)
  - `sneakingTracksThePose()`: `"sneaking mirrors the player's crouch pose."`
  - `nearBlockUsesTheProbe()` / `onBlockTagUsesTheProbe()` / `inBiomeTagUsesTheProbe()`: drive a stub probe lambda returning `true`/`false` and assert the condition forwards it, e.g. `"near_block delegates to the block probe within its radius."`
  - VERIFIED for production builder (not under test): `underwater` = `player.isUnderWater()`; light = `player.level().getMaxLocalRawBrightness(blockPos)` (declared on `LevelReader`). Keep the `Context` field a plain `int lightLevel`/`boolean underwater` so the test stays Minecraft-free.
- [ ] **(red gradle)** `.\gradlew.bat test --tests dev.attuned.api.focus.FocusConditionTruthTableTest --no-daemon` — confirm it fails to **compile** (classes absent).
- [ ] **(green — implement `FocusCondition`)** Create `FocusConditionContext.java` (or nested `FocusCondition.Context`) carrying `boolean raining`, `boolean underwater`, `int lightLevel`, `boolean sneaking`, and functional probes (`Predicate<Identifier>`-style `blockTagProbe`/`biomeTagProbe`, plus a `nearBlockProbe` taking block id + radius), returning `boolean`. Create `FocusCondition.java`: `@FunctionalInterface boolean test(Context)` plus static factories `inRain()`, `underwater()`, `lowLight(int)`, `brightLight(int)`, `onBlockTag(Identifier)`, `inBiomeTag(Identifier)`, `sneaking()`, `nearBlock(Identifier, int)`. Add `public static final Codec<FocusCondition> CODEC` dispatched on `"type"` (mirror the proven in-repo idioms — `Affinity.CODEC = StringRepresentable.fromEnum(...)` at `Affinity.java:20` and the `ModifierEntry.CODEC` `RecordCodecBuilder`; if using `Identifier.CODEC.dispatch(...)`, confirm the dispatch helper compiles before relying on it). Document the canonical `underwater = isUnderWater()` choice in the class javadoc. Keep the production `Context.of(ServerPlayer)` factory in a **separate, untested** method so the test classpath never bootstraps Minecraft. Re-run the truth-table `--tests`; confirm **green**.
- [ ] **(red — palette codec round-trip + unknown-type rejection, Minecraft-free)** Create `FocusBehaviorDefinitionCodecRoundTripTest.java` following the `FocusHolderCodecRoundTripTest` template (`import com.mojang.serialization.Codec; import net.minecraft.nbt.NbtOps; import net.minecraft.nbt.Tag;`, `encodeStart(NbtOps.INSTANCE, x).result().orElseThrow(...)` then `parse(...).result().orElseThrow(...)`). **Do NOT call `Bootstrap.bootStrap()` anywhere** (MEMORY: no-Bootstrap constraint). The `effect` field is a `Holder<MobEffect>` which needs bound registries, so it is **not** round-tripped here — the condition sub-codec is:
  - `conditionRoundTripsThroughTheCodec()`: encode→decode a `FocusCondition` of type `attuned:in_biome_tag` with tag `minecraft:is_cold` and assert the decoded type id and tag id survive — `assertEquals("attuned:in_biome_tag", ...)` / `assertEquals("minecraft:is_cold", ...)`, message `"A condition's type id and tag survive a codec round-trip."` Also round-trip `low_light(5)` and assert the threshold survives.
  - `unknownConditionTypeIsRejectedWithAClearError()`: parse a condition JSON with `"type":"attuned:does_not_exist"`; assert the decode is a failure (`result.error().isPresent()` / `result.result().isEmpty()`) and `result.error().orElseThrow().message().contains("does_not_exist")`. Message: `"An unknown condition type is rejected, not silently dropped."`
  - (The `conditional_mob_effect` param field names `effect`/`amplifier`/`duration_ticks`/`refresh_ticks` are pinned by string in `PaletteResolutionContractTest` below, since their `Holder<MobEffect>` codec requires runtime registries.)
- [ ] **(red gradle)** `.\gradlew.bat test --tests dev.attuned.content.behavior.palette.FocusBehaviorDefinitionCodecRoundTripTest --no-daemon` — confirm compile failure (palette classes absent).
- [ ] **(green — implement palette type + behavior + definition codec)** Create:
  - `PaletteType.java`: a static `Map<Identifier, Factory>` keyed by palette type id, seeded with `Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "conditional_mob_effect")`. `Factory` builds a `FocusBehavior` from decoded params.
  - `ConditionalMobEffectBehavior.java implements FocusBehavior` (in `dev.attuned.content.behavior`): constructor `(Holder<MobEffect> effect, int amplifier, int durationTicks, int refreshTicks, FocusCondition condition)`; `onTick(ServerPlayer, ItemStack)` builds `Context.of(player)`, evaluates `condition.test(ctx)`, and on success calls `PassiveEffectRefresher.refresh(player, effect, durationTicks, amplifier, true, false, false)` — the exact refresh idiom from `HarborlightBehavior:51`. The `effect` field codec is `BuiltInRegistries.MOB_EFFECT.holderByNameCodec()` (VERIFIED `MOB_EFFECT` exists), analogous to `BuiltInRegistries.ITEM.holderByNameCodec()` (`FocusDefinition.java:45`) and `BuiltInRegistries.ATTRIBUTE.holderByNameCodec()` (`ModifierEntry.java:36`). The param field names in JSON are `effect`, `amplifier`, `duration_ticks`, `refresh_ticks`, `condition`.
  - `FocusBehaviorDefinition.java`: record `(Identifier type, …params…)` with a `Codec` that dispatches on `"type"` to a per-type params codec, plus `FocusBehavior build()` delegating to `PaletteType`. Re-run the round-trip `--tests`; confirm **green**.
- [ ] **(red — resolution code-first-then-data contract)** Create `PaletteResolutionContractTest.java` (source-grep; `Files.readString`; `assertTrue(src.contains(...), msg)` + the `methodBody(source, signaturePrefix)` and `assertBefore(source, earlier, later)` helpers copied from `FocusDataConsistencyTest`). Pin:
  - `getBehaviorResolvesCodeFirstThenData()`: extract the body of `public static FocusBehavior getBehavior(` from `AttunedRegistries.java` via `methodBody`, and `assertBefore(body, "BEHAVIORS.get(", "FOCUS_BEHAVIORS")` (substitute the exact data-lookup token you introduce) with message `"getBehavior must consult the code registry before the data behavior registry."`
  - `getBehaviorIsTheSingleResolutionFunnel()`: assert no call site bypasses it — read `AttunedEffects.java`, `FociHud.java`, `FocusAbilityState.java`; assert each does NOT contain `"FOCUS_BEHAVIORS"`, message `"Effect/HUD/ability resolution must route through AttunedRegistries.getBehavior, not the data registry directly."`
  - `focusDefinitionRecordIsUnchanged()`: read `FocusDefinition.java`; `assertTrue(src.contains("Optional<Identifier> behavior"), "FocusDefinition.behavior stays Optional<Identifier> — data behaviors are named, not embedded.")` and `assertTrue(src.contains(".optionalFieldOf(\"behavior\")"), "FocusDefinition codec is unchanged so all existing focus JSON still loads.")`.
  - `conditionalMobEffectPinsItsParamFieldNames()`: read `ConditionalMobEffectBehavior.java` (or wherever the param codec lives); assert it contains `"effect"`, `"amplifier"`, `"duration_ticks"`, `"refresh_ticks"`, and `BuiltInRegistries.MOB_EFFECT.holderByNameCodec(` — message `"conditional_mob_effect's param field names and MobEffect Holder codec are pinned (round-trip needs registries, so this is a source pin)."`
  - `registrationOrderRegistersBehaviorRegistryInOnInitialize()`: read `Attuned.java`; `assertBefore(src, "DynamicRegistries.registerSynced(AttunedRegistries.FOCUS_BEHAVIORS", "AttunedContent.init();")` message `"FOCUS_BEHAVIORS must be registered in onInitialize before content init."`
  - `paletteShipsConditionalMobEffectType()`: read `PaletteType.java`; `assertTrue(src.contains("\"conditional_mob_effect\""), "v1 palette ships the conditional_mob_effect type.")`.
  - `paletteV1IsPassiveOnly()`: read `ConditionalMobEffectBehavior.java`; `assertTrue(!src.contains("hasActiveAbility"), "Palette v1 is passive-only — no active-ability override.")`.
- [ ] **(red gradle)** `.\gradlew.bat test --tests dev.attuned.content.behavior.palette.PaletteResolutionContractTest --no-daemon` — confirm failures (resolution edit + wiring absent).
- [ ] **(green — resolution-site edit + registry wiring)** In `AttunedRegistries.java`:
  - Add `public static final ResourceKey<Registry<FocusBehaviorDefinition>> FOCUS_BEHAVIORS = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "focus_behavior"));`
  - Change `getBehavior(Identifier id)` to: return `BEHAVIORS.get(id)` if non-null (**code-first** — the 40 singletons always win and never change), else fall back to the data registry. The data lookup needs a registry handle, but `getBehavior` is parameterless static — resolve by caching the loaded `FOCUS_BEHAVIORS` registry into a `static volatile Registry<FocusBehaviorDefinition>` populated from a server-lifecycle hook (so the signature stays compatible with all 5 call sites and the `Optional.map(AttunedRegistries::getBehavior)` method-reference uses). On the data branch, look up the `FocusBehaviorDefinition` by id and return a `def.build()` result **cached per id** (so the `FocusBehavior` is not rebuilt every tick). Null the cache on stop via the existing `AttunedServerCleanup.onStop` pattern.
  - **VERIFY the registry-loaded hook:** populate the cache where the server already obtains `registryAccess().lookupOrThrow(FOCUS_DEFINITIONS)` — confirm a suitable Fabric event by extracting strings: `unzip -p ~/.gradle/caches/fabric-loom/26.1.2/<fabric-api-jar> net/fabricmc/fabric/api/event/lifecycle/v1/ServerLifecycleEvents.class | tr -c '[:print:]' '\n' | grep -aiE "SERVER_STARTED|SERVER_STOPPING"`. If no registry-sync event is exposed, populate on `SERVER_STARTED` from `server.registryAccess().lookupOrThrow(FOCUS_BEHAVIORS)` and clear on stop. (Client-side data behaviors are out of scope for v1 — v1 palette behaviors run server-side via `onTick`.)
  - In `Attuned.java`, add `DynamicRegistries.registerSynced(AttunedRegistries.FOCUS_BEHAVIORS, FocusBehaviorDefinition.CODEC);` immediately after line 40. Re-run the contract `--tests`; confirm **green**.
- [ ] **(green — back-compat: all 40 code behaviors + existing foci unchanged)** Run `.\gradlew.bat test --tests dev.attuned.content.FocusDataConsistencyTest --no-daemon` (its `focusBehaviorIdsAreRegisteredInBehaviorRegistry` / `registeredBehaviorIds` checks must still pass — the data fallback must not break the code-registry assertions) and `.\gradlew.bat test --tests dev.attuned.content.RadiantFocusBehaviorContractTest --no-daemon`. Confirm **green** — every existing `focus/*.json` resolves its behavior code-first.
- [ ] **(full red→green suite + compile gate)** Run the whole unit suite plus client compile to catch the method-reference call sites (`AttunedEffects:273`/`:291`, `FocusAbilityState:90`, `FociHud:182` all use `AttunedRegistries::getBehavior` and must still compile against the unchanged signature): `.\gradlew.bat test compileJava compileTestJava --no-daemon`, then resolve and run the client compile task — **VERIFY** its name via `.\gradlew.bat tasks --all | findstr /i compileClient` (likely `compileClientJava`). Confirm **green**.
- [ ] **(smoke / runClient gate)** This task adds new runtime behavior-construction surface (new `FocusBehavior`s built from data). Drive a smoke from a **dev-only** datapack (e.g. `run/saves/<world>/datapacks/`) so the shipped Focus count gate is not perturbed: a `focus_behavior/frostward.json` `conditional_mob_effect` granting `minecraft:resistance` while `attuned:in_biome_tag` `minecraft:is_cold`, and a `focus/frostward_focus.json` pointing at `attuned:custom_focus_3` with `"behavior":"<devns>:frostward"`. Launch `.\gradlew.bat runClient`, create a world, equip the Focus within budget, and confirm: (1) the conditioned MobEffect refreshes only while the condition holds (resistance inside an `is_cold` biome, lapses outside), (2) no `getBehavior` data-resolution log error, (3) `/attuned validate` still passes (the `AttunedCommands:153` `getBehavior(...) == null` check now also accepts data behaviors). Tear the dev datapack down afterward.

**Back-compat pinned by this task:** `FocusDefinition` record + `CODEC` are byte-for-byte unchanged (`behavior` stays `Optional<Identifier>`), so all 61 existing `focus/*.json` load identically. The 40 code behaviors keep winning because `getBehavior` checks the code map **first**; the data registry is a pure fallback. All 5 `getBehavior` call sites are untouched. v1 adds no `hasActiveAbility` surface.

---

### Task 4: Palette breadth — `on_hit_effect` + `periodic_effect` + `attribute_while` (Phase 4)

Adds the three remaining v1 palette types on top of Task 3's condition/`conditional_mob_effect` core: `attuned:on_hit_effect`, `attuned:periodic_effect`, `attuned:attribute_while`. Each is a small, audited `FocusBehavior` factory built from a param codec and resolved through the *same* data behavior registry Task 3 wired (code-first-then-data in `getBehavior`). `on_hit_effect` reuses the live combat guards verbatim — `AttunedCombat.isChargedDirectMelee(Player, LivingEntity, DamageSource, float)` and `CombatTargets.isHostileOrPvpOpponent(LivingEntity, Player)` — and must NOT add a new mixin (it hangs off the existing `ServerLivingEntityEvents.AFTER_DAMAGE` handler in `AttunedCombat.init()`). `attribute_while` applies a `ModifierEntry` only while a `FocusCondition` holds, reusing the transient-modifier apply/remove shape from `AttunedEffects`. Active-ability authoring stays explicitly out (v2): these three are passive-only and never touch `hasActiveAbility()`. This task closes with the full release gate, the README "Make your own Foci" section, the `docs/reference.md` palette/condition tables, and the CHANGELOG.

**Prerequisite (BLOCKING):** Task 3 must be complete. Before proceeding, confirm these Task-3 classes exist: `FocusCondition` (interface with `boolean test(Context)`), `FocusConditionContext` (the context type), `ConditionalMobEffectBehavior`, `PaletteType` (the dispatch registry), and `FocusBehaviorDefinition`. Grep `src/test` for the Task-3 contract tests (`FocusConditionTruthTableTest`, `FocusBehaviorDefinitionCodecRoundTripTest`, `PaletteResolutionContractTest`). If any Task-3 class is missing, STOP and complete Task 3 first. **Read the actual `FocusCondition.test(...)` context-parameter type and `PaletteType` registration idiom from disk — do not guess them.**

**Package decision (resolved, consistent with Task 3):** behavior classes that call the package-private `PassiveEffectRefresher` live in `dev.attuned.content.behavior`; the dispatch registry `PaletteType` lives in `dev.attuned.content.behavior.palette`. `PeriodicEffectBehavior` (calls the refresher) goes in `dev.attuned.content.behavior`; `OnHitEffectBehavior` and `AttributeWhileBehavior` (do not call the refresher) may live in `.palette`. Pin the chosen package per class in the Step-3 contract pins to match reality. Do **not** widen `PassiveEffectRefresher` to public.

**Architectural constants (VERIFIED — do not re-derive):**
- Combat charge guard: `AttunedCombat.isChargedDirectMelee(Player attacker, LivingEntity defender, DamageSource source, float threshold)` — public, `boolean` (`AttunedCombat.java:187`).
- Target guard: `CombatTargets.isHostileOrPvpOpponent(LivingEntity target, Player player)` — public (`CombatTargets.java:27`).
- Effect refresh idiom: `PassiveEffectRefresher.refresh(ServerPlayer, Holder<MobEffect>, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon)` — package-private in `dev.attuned.content.behavior`.
- Transient modifier apply/remove: `ai.addTransientModifier(new AttributeModifier(id, entry.amount(), entry.operation()))` then `ai.removeModifier(id)` with a stable `Identifier` id — copy `AttunedEffects.applyFocus`/`removeFocus` (`AttunedEffects.java:259-296`); guard `if (ai == null) ...`.
- `ModifierEntry.CODEC` fields: `attribute` (`Holder<Attribute>` via `BuiltInRegistries.ATTRIBUTE.holderByNameCodec()`), `amount` (finite double), `operation` (`AttributeModifier.Operation`) (`ModifierEntry.java:35-39`).
- `MobEffectInstance` constructor: the `(Holder<MobEffect> effect, int duration, int amplifier)` form is present in this fork (VERIFIED `<init>` exists; if the compiler objects to arity, fall back to the 7-arg form matching `PassiveEffectRefresher`). VERIFY the exact arity at implementation time with `unzip -p ~/.gradle/caches/fabric-loom/26.1.2/minecraft-client.jar net/minecraft/world/effect/MobEffectInstance.class | tr -c '[:print:]' '\n' | grep -aE "^<init>$"` and read the descriptor if needed.
- Focus-count gate: `tools/verify_repository.py` matches `README_FOCI_PATTERN = re.compile(r"\b(?P<count>\d+)\s+Foci\b")` against `data/attuned/attuned/focus/*.json` — palette types add **behaviors**, not Foci, so the count is unchanged (the example pack ships under `docs/`).

**Files:**
- Create: `src/main/java/dev/attuned/content/behavior/PeriodicEffectBehavior.java`
- Create: `src/main/java/dev/attuned/content/behavior/palette/OnHitEffectBehavior.java`
- Create: `src/main/java/dev/attuned/content/behavior/palette/AttributeWhileBehavior.java`
- Create: `src/main/java/dev/attuned/combat/PaletteCombat.java` (or a method on an existing combat helper — the static on-hit dispatch called from `afterDamage`)
- Create: `src/test/java/dev/attuned/content/behavior/palette/PaletteBreadthCodecTest.java` (behavioral, Minecraft-free)
- Create: `src/test/java/dev/attuned/content/behavior/palette/PaletteBreadthContractTest.java` (source-grep)
- Modify after red: `src/main/java/dev/attuned/content/behavior/palette/PaletteType.java` (register the three new type ids)
- Modify after red: `src/main/java/dev/attuned/combat/AttunedCombat.java` (route palette on-hit procs through the existing `afterDamage` handler)
- Modify: `README.md` (new "Making Your Own Foci" section)
- Modify: `docs/reference.md` (palette-type table + condition table)
- Modify: `CHANGELOG.md` (`### Added` under the active version heading)
- Modify (only if a new player-visible string is added — palette behaviors are data-named, so likely none): `src/main/resources/assets/attuned/lang/en_us.json`
- Modify: `src/test/java/dev/attuned/content/ExampleDatapackContractTest.java` (Task 1's test — extend to cover the new `focus_behavior/*.json`)

Steps:

- [ ] **Step 1: Read first, then pin the Task-3 surface you build on.** Read COMPLETELY, in order: the Task-3 palette classes under `src/main/java/dev/attuned/content/behavior/` and `…/behavior/palette/` (`PaletteType` dispatch + registration idiom, `FocusCondition` interface + its `test(...)` signature + codec, `ConditionalMobEffectBehavior` for the param-codec idiom), `src/main/java/dev/attuned/combat/AttunedCombat.java` (the `afterDamage(LivingEntity defender, DamageSource source, float originalDamage, float dealtDamage, boolean blocked)` handler, `isChargedDirectMelee`, and `hasActiveFocus` at line 392 which is `private`), `src/main/java/dev/attuned/combat/CombatTargets.java`, `src/main/java/dev/attuned/effect/AttunedEffects.java` (`applyFocus`/`removeFocus`/`modifierId`), `src/main/java/dev/attuned/content/behavior/PassiveEffectRefresher.java`, and `src/main/java/dev/attuned/api/focus/ModifierEntry.java`. Then grep `src/test` for every string each touched file pins: at minimum `ThornwardReflectionContractTest` (pins `AttunedCombat.applyAffinity`/`isReflecting` method bodies via the brace-matching `methodBody` helper), `ChargedMeleeSnapshotContractTest` (pins `isChargedDirectMelee`), and the Task-3 palette tests (DO NOT break their type-id assertions), and `FocusDataConsistencyTest`. If your edits to `AttunedCombat.java` move any pinned string, update that pin in the SAME step with intent preserved. **Do not write code yet.**
- [ ] **Step 2: Write the failing codec round-trip test (behavioral, Minecraft-free).** Create `PaletteBreadthCodecTest.java` following `FocusHolderCodecRoundTripTest` exactly. Model effect/attribute params as **string `Identifier`s** to stay Minecraft-free (copy `PresetApplicationResolverTest`'s id modelling), because `BuiltInRegistries.MOB_EFFECT.holderByNameCodec()` / `BuiltInRegistries.ATTRIBUTE.holderByNameCodec()` need bound registries. Split:
  - `onHitEffectParamsRoundTrip()` — round-trip the **plain** fields only: effect modelled as string `"minecraft:weakness"`, `amplifier=0`, `duration_ticks=60`, `charge_threshold=0.9`, `target=victim`, `hostile_only=true`. Assert each survives. The real `Holder<MobEffect>` resolution is deferred to runtime and pinned in Step 3.
  - `periodicEffectParamsRoundTrip()` — effect `"minecraft:regeneration"` (string), `amplifier=0`, `duration_ticks=80`, `refresh_ticks=40`; assert round-trip.
  - `attribute_while` is NOT in this Minecraft-free test (its `ModifierEntry.CODEC` requires a bound `Holder<Attribute>`). Instead, Step 3's contract test source-pins that `AttributeWhileBehavior`'s codec reuses `ModifierEntry.CODEC.fieldOf("modifier")` and `FocusCondition.CODEC.fieldOf("condition")`.
- [ ] **Step 3: Write the failing contract test (source-grep wiring + guard reuse + collision guard).** Create `PaletteBreadthContractTest.java`. Read each source once via `Files.readString(..., StandardCharsets.UTF_8)`; use the `methodBody(source, signaturePrefix)` brace-matcher copied from `ThornwardReflectionContractTest` for guard-reuse pins. Assert, with these EXACT strings:
  - **Type-id registration** (in `PaletteType.java`): `.contains("attuned:on_hit_effect")`, `.contains("attuned:periodic_effect")`, `.contains("attuned:attribute_while")`, each adjacent to its behavior class name (`"OnHitEffectBehavior"`, `"PeriodicEffectBehavior"`, `"AttributeWhileBehavior"`).
  - **On-hit reuses the live guards, adds no mixin:** `OnHitEffectBehavior.java` `.contains("AttunedCombat.isChargedDirectMelee(")` AND `.contains("CombatTargets.isHostileOrPvpOpponent(")`; AND does NOT contain `"@At"` and does NOT contain `"Mixin"`. Assert the on-hit dispatch lives on the existing event: `AttunedCombat.java`'s `afterDamage` method body (via `methodBody`) `.contains("PaletteCombat.onMeleeHit(")` (pin the exact static call you introduce).
  - **Periodic reuses the refresher:** `PeriodicEffectBehavior.java` `.contains("PassiveEffectRefresher.refresh(")` AND `.contains("package dev.attuned.content.behavior;")` (proves it sits where the package-private refresher is reachable).
  - **`attribute_while` reuses the transient-modifier shape AND uses a non-colliding id prefix:** `AttributeWhileBehavior.java` `.contains("addTransientModifier(")` AND `.contains("removeModifier(")` AND `.contains("FocusCondition")` AND `.contains(".test(")` (condition-gated). Assert the modifier id is a stable `Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "palette_attr_while` (pin the literal prefix) and assert it does **NOT** contain the slot scheme `"slot_"` — message `"attribute_while must use a distinct modifier-id prefix (palette_attr_while_*) so it never collides with AttunedEffects' slot_N_mod_N scheme."`
  - **Reuses `ModifierEntry`/`FocusCondition` codecs:** `AttributeWhileBehavior.java` `.contains("ModifierEntry.CODEC")` AND `.contains("FocusCondition.CODEC")`, message `"attribute_while's codec reuses ModifierEntry.CODEC and FocusCondition.CODEC (the Holder<Attribute> field is registry-bound, so it is source-pinned, not round-tripped)."`
  - **Active-ability stays out (v2 guard):** none of the three behavior files contain `"hasActiveAbility"` or `"onAbility"` (assert absence in all three).
  - **Cleanup discipline (only if a file introduces a per-player `static Map`):** if `OnHitEffectBehavior` or `AttributeWhileBehavior` keeps per-player state, assert `"AttunedPlayerCleanup.onForget"` and `"AttunedServerCleanup.onStop"` appear in that file (constraint #8). Prefer stateless factories so this is N/A.
- [ ] **Step 4: Run both tests RED.**
  ```powershell
  .\gradlew.bat test --tests dev.attuned.content.behavior.palette.PaletteBreadthCodecTest --tests dev.attuned.content.behavior.palette.PaletteBreadthContractTest --no-daemon
  ```
  Both must fail for the right reason (missing classes / missing pinned strings), not a compile error in the tests.
- [ ] **Step 5: Implement the three factories + wiring (make it green).**
  - `PeriodicEffectBehavior` (simplest, do first), in `dev.attuned.content.behavior`: `onTick(ServerPlayer player, ItemStack focus)` calls `PassiveEffectRefresher.refresh(player, effect, durationTicks, amplifier, true, false, false)` gated by `player.tickCount % refreshTicks == 0` (ambient + icon-hidden, like `MossheartBehavior`).
  - `AttributeWhileBehavior`, in `dev.attuned.content.behavior.palette`: holds a `ModifierEntry modifier` + a `FocusCondition condition`. In `onTick`, build the `FocusCondition.Context` (read its exact constructor/factory from Task-3 source), then: `AttributeInstance ai = player.getAttribute(modifier.attribute()); if (ai == null) return;`. The stable id is `Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "palette_attr_while")` (distinct prefix → no collision with `AttunedEffects.modifierId`'s `"slot_N_mod_N"`). If `condition.test(ctx)` and `ai.getModifier(id) == null`, `ai.addTransientModifier(new AttributeModifier(id, modifier.amount(), modifier.operation()))`; if the condition is false and the modifier is present, `ai.removeModifier(id)`. Implement `onDeactivate` to remove the modifier unconditionally (so unequipping while the condition holds does not strand it).
  - `OnHitEffectBehavior`, in `dev.attuned.content.behavior.palette`: does NOT use `onTick`. Introduce a tiny static dispatch `PaletteCombat.onMeleeHit(Player attacker, LivingEntity defender, DamageSource source, float dealtDamage)` invoked from the EXISTING `AttunedCombat.afterDamage` handler (no new event, no mixin), next to the Thornward/Leech procs, guarded `if (dealtDamage <= 0.0F) return;`. `onMeleeHit` iterates the attacker's active Foci — copy the active-slot loop verbatim (it already exists at `AttunedCombat.java:392-406`; `hasActiveFocus` is `private`, so copy its body rather than calling it):
    ```java
    var inventory = AttunedAttachments.getInventory(attacker);
    for (int slot : Attunement.activeSlots(attacker)) {
        ItemStack stack = inventory.get(slot);
        if (stack.isEmpty()) continue;
        FocusBehavior behavior = Attunement.definitionFor(attacker, stack)
            .flatMap(FocusDefinition::behavior)
            .map(AttunedRegistries::getBehavior)
            .orElse(null);
        if (!(behavior instanceof OnHitEffectBehavior onHit)) continue;
        if (!AttunedCombat.isChargedDirectMelee(attacker, defender, source, onHit.chargeThreshold())) continue;
        if (onHit.hostileOnly() && !CombatTargets.isHostileOrPvpOpponent(defender, attacker)) continue;
        LivingEntity recipient = onHit.targetIsSelf() ? attacker : defender;
        recipient.addEffect(new MobEffectInstance(onHit.effect(), onHit.durationTicks(), onHit.amplifier()));
    }
    ```
    (The `Attunement.definitionFor(...).flatMap(FocusDefinition::behavior).map(AttunedRegistries::getBehavior)` chain is exactly what `FocusAbilityState.firstActiveAbility` uses.) VERIFY `MobEffectInstance` arity if the compiler objects, per the jar-extract command in the Architectural constants block; fall back to the 7-arg form if the 3-arg overload is absent.
  - Register all three type ids in `PaletteType` so `behavior` resolution reaches them code-first-then-data exactly as Task 3 set up.
- [ ] **Step 6: Run both tests GREEN, then the FULL suite** (you edited `AttunedCombat.java`, a heavily pinned combat class):
  ```powershell
  .\gradlew.bat cleanTest test --no-daemon
  ```
  Fix any pin you moved in `ThornwardReflectionContractTest` / `ChargedMeleeSnapshotContractTest` with intent preserved.
- [ ] **Step 7: Smoke gate (MANDATORY — you added a combat-path proc + new behavior factories loaded at server boot, and on-hit runs inside the live `AFTER_DAMAGE` handler).**
  ```powershell
  .\gradlew.bat build --no-daemon
  python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60
  ```
  A wrong effect/attribute Holder resolution or a broken palette codec surfaces here as a resource-load/class-load failure. Do not proceed until clean.
- [ ] **Step 8: Worked-example coverage + docs tables.** Extend the `docs/` example pack (Task 1) to exercise all three new types: add a `data/example/attuned/focus_behavior/*.json` for each of `on_hit_effect`, `periodic_effect`, `attribute_while`, and a `focus/*.json` referencing each (under `docs/`, NOT `src/main/resources/data/`, so they do NOT bump the Focus count). Update `ExampleDatapackContractTest` (Task 1) to scan a new `EXAMPLE_BEHAVIOR_DIR = Path.of("docs/example-pack/data/example/attuned/focus_behavior")`, parse each `focus_behavior/*.json`, and assert its `type` is one of `{attuned:conditional_mob_effect, attuned:on_hit_effect, attuned:periodic_effect, attuned:attribute_while}` — message `"Every example focus_behavior must name a shipped palette type."` Then write the reference tables in `docs/reference.md`: extend the Behaviors section (after line 108) with a **palette-type table** (columns: `type` | params | effect) listing `attuned:conditional_mob_effect`, `attuned:on_hit_effect`, `attuned:periodic_effect`, `attuned:attribute_while`; and a **condition table** listing the actual shipped `FocusCondition` set from Task 3's source (`in_rain`, `underwater`, `low_light`, `bright_light`, `on_block_tag`, `in_biome_tag`, `sneaking`, `near_block` — confirm the shipped set, do not list any that were cut). Note explicitly that active-ability palette is v2/not-yet-available.
- [ ] **Step 9: README "Making Your Own Foci" section.** Add a `## Making Your Own Foci` section after the Quick Start section, **approximately 200–250 words max**. Structure: (1) JAR-free workflow — point a `focus/<name>.json` at a generic `attuned:custom_focus_N` item (Task 2); author a `focus_behavior/<id>.json` with one of the four palette types (see the `docs/reference.md` palette table); supply name/lore/texture via a resource pack; run `/attuned validate` to check syntax. (2) Link to `docs/reference.md` for palette params and condition tables. Do NOT duplicate reference docs in the README. **Before editing, confirm `src/main/resources/data/attuned/attuned/focus/` has NO new `.json` files relative to baseline** (all example Foci must reside under `docs/` only — if new focus JSONs exist under `src/`, the count increased and `verify_repository.py` will fail; that is a Task-1/Task-2 error to fix, not a Task-4 error). Leave the README's `\d+ Foci` count phrasing untouched (palette behaviors are not Foci) — if it is currently `61 Foci`, keep it `61`.
- [ ] **Step 10: CHANGELOG.** Under the active version heading (`## Attuned X.Y.Z - <title>`), add `### Added`: "**Datapack Focus behavior palette** — author passive Foci in pure JSON via `conditional_mob_effect`, `on_hit_effect`, `periodic_effect`, and `attribute_while` behavior types, with a composable condition registry. No Java required." Do NOT bump `mod_version` (that is the Release task).

---

### Task: Release

Final gate for the whole Datapack-Defined Foci feature. Publish ONLY on explicit user instruction.

- [ ] **Docs sweep.** Re-read `docs/reference.md` + `docs/authoring-foci.md` (Task 1) top to bottom against the final palette + condition set; fix drift. Confirm every generic-item default lang key (Task 2) is present (no raw keys), and that the palette-type/condition tables match the shipped code exactly. Confirm the `docs/example-pack/` exercises all four palette types and that `ExampleDatapackContractTest` covers each.
- [ ] **Manual `runClient` checklist (creative + cheats).** Load the shipped example pack into `run/saves/<world>/datapacks/` (or `run/datapacks/`), `/reload`, run `/attuned validate` and confirm zero problems. Then:
  - 16 generic items appear in the Attuned utility tab with default name/texture, no missing-texture/model error; skinning a `custom_focus_N` via a resource pack overrides name/model/texture.
  - `conditional_mob_effect`: effect refreshes only while its condition holds (e.g. resistance in an `is_cold` biome, lapses outside).
  - `on_hit_effect`: land a fully charged hit on a hostile mob → effect applies; a non-charged or PvP-disabled swing does NOT.
  - `periodic_effect`: buff refreshes on cadence.
  - `attribute_while`: satisfy then break the condition → the modifier toggles (F3 attribute readout or the altar cost readout), and unequipping while the condition holds strands no modifier.
- [ ] **Full verification gate (ALL must pass).**
  ```powershell
  .\gradlew.bat cleanTest build --no-daemon
  python tools/verify_repository.py
  python -m unittest discover -s tests
  python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60
  git diff --check
  ```
  (CRLF warnings are normal; anything else is not.) `verify_repository.py` MUST still pass its Focus-count gate **unchanged** and its 8 Modrinth gallery PNG checks — no new gallery PNG is required (palette is not a new affinity); the new `check_custom_focus_pool` reports `16 generic Foci`.
- [ ] **Version + changelog.** Bump `mod_version` in `gradle.properties` to the Datapack-Foci release version; confirm `CHANGELOG.md` has the complete section (it gates the publish tooling). Never attribute Claude in any VCS artifact.
- [ ] **Commit + CI.** Commit `release: Datapack-Defined Foci - <title>` with **explicit paths only** (never `git add -A`; the repo root has scratch dirs and `docs/superpowers/assets/` is Git LFS), push, and watch CI: `gh run watch <id> --exit-status`. CI must be FULLY green including the smoke step.
- [ ] **Publish ONLY when the user explicitly says to.** Dry-run first: `python tools/publish_curseforge.py --dry-run`, then `.\gradlew.bat modrinth` (needs `MODRINTH_TOKEN`) and `python tools/publish_curseforge.py`. Do not publish without an explicit instruction.

---

## Open questions / VERIFY-before-coding

Resolve these by jar-string extraction / a quick source read **before** pinning the corresponding code. Items already verified during planning are marked ✅ and need no further work.

1. ✅ **Holder unresolved-item API** — `net/minecraft/core/Holder.class` exposes **`isBound`** (no `isValid`). Use `!def.item().isBound()` (Task 1).
2. ✅ **`AttributeModifier.Operation` enum spellings** — `add_value`, `add_multiplied_base`, `add_multiplied_total` (Tasks 1, 4).
3. ✅ **Server-side lang lookup** — `net.minecraft.locale.Language.getInstance().has(key)` exists server-side (`has`, `getInstance`, `getOrDefault` all present). Use it for the `/attuned validate` lang-key warning (Task 1).
4. ✅ **`BuiltInRegistries.MOB_EFFECT`** — exists; use `BuiltInRegistries.MOB_EFFECT.holderByNameCodec()` for the `effect` field (Tasks 3, 4).
5. ✅ **Underwater semantics** — canonical `underwater = player.isUnderWater()` (eye-submersion; `isUnderWater`/`isEyeInFluid`/`isInWater` all present). Documented in `FocusCondition` javadoc; unit test exercises only the boolean (Task 3).
6. ✅ **Light accessor** — `getMaxLocalRawBrightness` (and `getRawBrightness`) declared on `LevelReader`; production `Context` builder uses `player.level().getMaxLocalRawBrightness(blockPos)` (Task 3).
7. ✅ **`items/<name>.json` schema** — `{"model": {"type": "minecraft:model", "model": "attuned:item/<name>"}}` in this fork (Task 2).
8. **`verify_repository.py` `docs/**` JSON scanning** — confirmed `check_src_json()` scans only `src/main/resources/**/*.json`, so `docs/example-pack/` is NOT validated by the gate; keep the example JSONs strict regardless (Task 1, Step "Full suite + repo gate").
9. **`tools/generate_ui_art.py` entrypoint** — read the `__main__`/`main()` block and confirm `generate_custom_focus_textures()` is actually dispatched; wire it into `main()` if not (Task 2).
10. **Fabric registry-loaded / server-lifecycle hook** — confirm the event used to populate the `static volatile Registry<FocusBehaviorDefinition>` cache for the data branch of `getBehavior` (`ServerLifecycleEvents.SERVER_STARTED`/`SERVER_STOPPING` or a registry-sync event). Extract from the fabric-api jar; if no registry-sync event is exposed, populate on `SERVER_STARTED` from `server.registryAccess().lookupOrThrow(FOCUS_BEHAVIORS)` and clear via `AttunedServerCleanup.onStop` (Task 3).
11. **Codec dispatch helper** — confirm `Identifier.CODEC.dispatch(...)` / `Codec.dispatch` compiles in this fork; otherwise mirror the proven in-repo `StringRepresentable.fromEnum` (`Affinity.java:20`) / `RecordCodecBuilder` (`ModifierEntry.java`) idioms for `FocusCondition.CODEC` and `FocusBehaviorDefinition.CODEC` (Task 3).
12. **Client compile task name** — resolve via `.\gradlew.bat tasks --all | findstr /i compileClient` (likely `compileClientJava`) before running the cross-sourceset compile gate that proves the `AttunedRegistries::getBehavior` method-reference call sites still compile (Task 3).
13. **`MobEffectInstance` constructor arity** — the `(Holder<MobEffect>, int duration, int amplifier)` form is present; if the compiler objects, fall back to the 7-arg form matching `PassiveEffectRefresher`. Re-extract `<init>` descriptors at implementation time if needed (Task 4).
14. **Headless smoke vs `runClient`** — confirm whether `tools/minecraft_runtime_smoke.py` is the wired headless gate or whether `runClient` is expected for the visual no-missing-texture check (Task 2 smoke step; the full release gate already pins the headless smoke command).