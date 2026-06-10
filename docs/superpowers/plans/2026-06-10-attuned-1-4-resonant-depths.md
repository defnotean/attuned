# Attuned 1.4 "Resonant Depths" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Work the tasks IN ORDER — later tasks assume earlier ones landed.

**Goal:** Ship Attuned 1.4: a performance/cleanup pass (resolution caching, shared HUD snapshots, proc-timing fixes, dead-code removal), Reliquary polish (build previews, quick-swap keybind, Grand Reliquary), new gameplay systems (faction set bonuses, trial pacts, focus tempering, Apex identity abilities), and content/world features (Attunement Sanctum structure, resonant surge events, affinity inspect).

**Architecture:** Every system follows existing house patterns: server-authoritative state in data attachments (`AttunedAttachments`) or data components (`AttunedComponents`), pure Minecraft-free resolver classes for testable logic (the `BudgetResolver` / `PresetApplicationResolver` pattern), `CustomPacketPayload` records registered in idempotent `init()` classes wired into `Attuned.onInitialize`, per-player static maps that ALWAYS register `AttunedPlayerCleanup.onForget` + `AttunedServerCleanup.onStop`, and behaviors registered in `AttunedFocusBehaviors`.

**Tech Stack:** Fabric API (MC 26.1.2, Java 25), forked client mappings (`GuiGraphicsExtractor`, `extractBackground`/`extractLabels`, `keyPressed(KeyEvent)`, `mouseClicked(MouseButtonEvent, boolean)`), JUnit 5 source-grep contract tests + Minecraft-free behavioral tests, Python 3.11 tooling (Pillow for art, stdlib for validators).

---

## READ THIS FIRST — load-bearing constraints (violating any of these wastes hours)

1. **The test classpath cannot Bootstrap Minecraft.** `new ItemStack(item)`, the item registry, and registry-backed codecs FAIL in tests with "Components not bound yet". Behavioral tests must be Minecraft-free: model foci as `String` registry ids (copy the style of `src/test/java/dev/attuned/menu/PresetApplicationResolverTest.java`). Everything runtime-flavored is covered by source-grep contract tests (assert the source file `contains(...)` strings) plus the smoke check.
2. **Before editing ANY existing source file, grep `src/test` for strings it contains.** The suite pins literal source strings. If your edit changes a pinned string, update the pinning test in the same commit — with the assertion *intent* preserved, never deleted.
3. **Mixin `@At` targets are strings — the compiler and unit tests can NOT validate them.** After ANY mixin change you MUST run the local smoke check (`python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60`) before committing. A wrong target crashes the game at class load. (This exact failure shipped once: `PlayerAttackMixin` anchored on an INVOKE that does not exist in 26.1.2's `Player.attack`.)
4. **To verify an unfamiliar Minecraft API name in this fork**, extract strings from the loom jars; there is no javap:
   `unzip -p ~/.gradle/caches/fabric-loom/26.1.2/minecraft-client.jar path/to/Class.class | tr -c '[:print:]' '\n' | grep -aE "^candidateName$"`.
   Vanilla datapack layouts live inside the same jars under `data/minecraft/...` — list them with `unzip -l`.
5. **Clicks outside a screen's logical `imageWidth × imageHeight` window drop the carried item.** Every slot must sit inside the bounds passed to `super(...)` in the screen constructor. `SatchelScreen` uses a 252×200 logical window with the 176×166 texture blitted in the left half — copy that approach.
6. **Never `git add -A` or `git add .`** — the repo root contains scratch dirs (`.codex-remote-attachments/`, `.superpowers/`, `assets/`, `tmp/`). Stage explicit paths only. `docs/superpowers/assets/` is Git LFS.
7. **Run order for every task:** write failing test → focused red run → implement → focused green run. Focused runs:
   ```powershell
   .\gradlew.bat test --tests dev.attuned.<FQCN> --no-daemon
   ```
8. **Per-player static maps in new code MUST register cleanup** in their `init()`/constructor: `AttunedPlayerCleanup.onForget(MAP::remove); AttunedServerCleanup.onStop(MAP::clear);` (see `MossheartBehavior.initLifecycle()` for the idiom).
9. **Lang + docs ride along:** every player-visible addition needs `src/main/resources/assets/attuned/lang/en_us.json` keys AND a row/sentence in `docs/reference.md`. `AttunedTooltips` auto-appends `item.attuned.<path>.lore`, `.lore2`, `.effect` for every `attuned` item — any new item missing those keys shows RAW KEYS in-game.
10. **Changelog:** append your bullets under a `## Attuned 1.4.0 - Resonant Depths` heading at the TOP of `CHANGELOG.md` as part of each task (create the heading in Task 1). Do not bump `mod_version` until the final task.

---

### Task 1: Server-side resolution cache

The hottest server inefficiency: `Attunement.resolution(player)` is recomputed up to ~10× per damage event and 2–3× per player-tick, and every `AttunedInv.get(slot)` read copies an `ItemStack`. `AttunedInv` is immutable and replaced wholesale on change, so its object identity is a valid cache key.

**Files:**
- Create: `src/test/java/dev/attuned/attunement/ResolutionCacheContractTest.java`
- Modify after red: `src/main/java/dev/attuned/attunement/Attunement.java`

- [ ] **Step 1: Write the failing test.** Assert `Attunement.java` contains: a private static final per-player cache map (`"private static final Map<UUID, CachedResolution>"`), a private record `CachedResolution` holding the `AttunedInv` instance, the capacity int, and the resolved result; a fast path in `resolution(` that returns the cached value when `cached.inv() == inv && cached.capacity() == capacity` (identity comparison — assert the literal `"cached.inv() == inv"`); and cleanup registration (`"AttunedPlayerCleanup.onForget"` and `"AttunedServerCleanup.onStop"`). Also read `src/main/java/dev/attuned/AttunedPlayerCleanup.java` first to confirm the exact registration signature before pinning it.
- [ ] **Step 2: Run focused test red.**
  ```powershell
  .\gradlew.bat test --tests dev.attuned.attunement.ResolutionCacheContractTest --no-daemon
  ```
- [ ] **Step 3: Implement.** Read `Attunement.java` FIRST and grep `src/test` for every string in the method you touch (`AttunementSourceContractTest` pins internals of `Attunement` — update its pins if your refactor moves them, preserving intent). Wrap the existing resolution body: read the attachment ONCE (`AttunedAttachments.getInventory(player)` returns the immutable instance), check the cache, compute + store on miss. CRITICAL: the cached resolved object must be immutable or defensively copied — inspect what `resolution` returns today and keep its exposure semantics identical.
- [ ] **Step 4: Focused green + full test run.** Also run the full suite once here, since `Attunement` is the most-pinned class in the repo:
  ```powershell
  .\gradlew.bat cleanTest test --no-daemon
  ```
- [ ] **Step 5: Changelog.** Create the `## Attuned 1.4.0 - Resonant Depths` section with an `### Internal` bullet about the resolution cache.

### Task 2: Shared client attunement snapshot

Four hand-rolled copies of the same per-frame stance aggregation exist: `FociHud.java` (~lines 102–167), `CombatHud.ownStance` (~232–260), `FocusPanel.java` (~109–141), and the canonical `AttunementReadout.snapshot`. Route everyone through a per-tick cached `AttunementReadout.snapshot`.

**Files:**
- Create: `src/test/java/dev/attuned/client/AttunementSnapshotCacheContractTest.java`
- Modify after red: `src/client/java/dev/attuned/client/AttunementReadout.java`
- Modify after red: `src/client/java/dev/attuned/client/hud/FociHud.java`
- Modify after red: `src/client/java/dev/attuned/client/hud/CombatHud.java`
- Modify after red: `src/client/java/dev/attuned/client/FocusPanel.java`
- Modify after red: `src/client/java/dev/attuned/client/screen/AltarScreen.java`

- [ ] **Step 1: Read all five files COMPLETELY first.** Grep `src/test` for pinned strings in each (at minimum `CombatHudSettingsContractTest`, `FociHudContractTest`, `UiAssetContractTest`, and any `FocusPanel`/`AltarScreen` pins exist — find them all before touching anything).
- [ ] **Step 2: Write the failing test.** Assert `AttunementReadout.java` contains a memoized accessor `"public static Snapshot cached(Player player)"` keyed on `player.tickCount` (assert `"tickCount"`), and that `FociHud.java`, `CombatHud.java`, `FocusPanel.java`, and `AltarScreen.java` all contain `"AttunementReadout.cached("`. Assert `CombatHud.java` no longer contains its private duplicate aggregation (pick one distinctive line of `ownStance`'s body from your read and assert absence).
- [ ] **Step 3: Red run, implement, green run.** The cache: `private static int cachedTick = -1; private static Snapshot cachedSnapshot;` refreshed when `player.tickCount != cachedTick` — single-player-singleton is fine, the client only has one local player; also key on the player UUID to survive respawn. `AltarScreen` currently calls `AttunementReadout.snapshot(player)` in BOTH `extractBackground` (~line 141) and `extractLabels` (~line 212) — both become `cached(player)`. In `CombatHud.targetedStance`, build ONE snapshot for the targeted player instead of the ~5 separate `Attunement.*` calls (note: `cached()` is for the local player; for the target just call `AttunementReadout.snapshot(targetPlayer)` once and derive committed/discord/capstone from it).
- [ ] **Step 4: Visual verification.** Run the dev client, open inventory (Focus panel), enable both HUDs, open the Attunement Table:
  ```powershell
  .\gradlew.bat runClient --no-daemon
  ```
  Confirm the Focus panel, both HUDs, and the altar readout render exactly as before.
- [ ] **Step 5: Changelog** `### Internal` bullet.

### Task 3: Consumable procs survive dodged hits

`LivingEntityHurtMixin` shapes damage at `@At("HEAD")` of `hurtServer` — BEFORE vanilla's invulnerability-window discard and Fabric's `ALLOW_DAMAGE` veto (used by Apex Untouchable). Right now a dodged/i-framed hit still: breaks the attacker's Veil + burns the 120-tick Needle cooldown (`UnseenCombat.adjustDamage` ~lines 66–72), and consumes the Ashen Debt (`RevenantCombat.adjustDamage` `DEBTS.remove`). Move the CONSUMPTION to the after-damage stage; keep the damage SHAPING where it is.

**Files:**
- Create: `src/test/java/dev/attuned/combat/ConsumableProcTimingContractTest.java`
- Modify after red: `src/main/java/dev/attuned/combat/UnseenCombat.java`
- Modify after red: `src/main/java/dev/attuned/combat/RevenantCombat.java`

- [ ] **Step 1: Read both files + `LivingEntityHurtMixin` + every test pinning them** (`ThornwardReflectionContractTest` pins method bodies via a brace-matching `methodBody` helper; `ChargedMeleeSnapshotContractTest` pins `isChargedDirectMelee` strings; there may be Needle/Unseen-specific pins — grep `UnseenCombat` and `RevenantCombat` across `src/test`).
- [ ] **Step 2: Design (follow exactly).** In `adjustDamage` (HEAD stage): COMPUTE whether the proc applies and shape the damage, but record the pending consumption in a per-player "pending" map (`Map<UUID, PendingProc>` with the target UUID + game time) instead of mutating cooldowns/debts. In the existing `AFTER_DAMAGE` handlers: if `dealtDamage > 0` and a pending entry matches (same attacker, same tick), THEN spend it (remove debt, set Needle cooldown, break Veil). If `AFTER_DAMAGE` never fires for that tick (hit discarded), a stale pending entry is overwritten by the next swing — also clear pendings in the existing cleanup registrations. Edge case to preserve: the pending damage multiplier must still be applied at the HEAD stage (you cannot retro-multiply), so a dodged hit "wastes" the multiplier but KEEPS the debt/cooldown — that asymmetry is the entire point.
- [ ] **Step 3: Write the failing test.** Assert: `UnseenCombat.java` contains a pending map (`"PENDING_NEEDLE"` or similar) and that the literal `LAST_NEEDLE.put` no longer appears inside the `adjustDamage` method body (reuse the `methodBody` helper pattern from `ThornwardReflectionContractTest`); `RevenantCombat.java`'s `adjustDamage` body no longer contains `DEBTS.remove` and its `afterDamage` body does. Assert cleanup registration for each new map.
- [ ] **Step 4: Red, implement, green.** Then run the FULL local gate including the smoke check (combat classes are loaded at server boot):
  ```powershell
  .\gradlew.bat cleanTest build --no-daemon
  python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60
  ```
- [ ] **Step 5: Changelog** `### Fixed` bullet: "Dodged or invulnerability-frame hits no longer consume Ashen Debt, the Needle opener cooldown, or break Veil."

### Task 4: Delete the dead click-move stack + small perf gates

The Reliquary moved to native slot drag/drop; the old click-move server path is unreachable (the screen asserts it does NOT send it) but still registers a serverbound payload.

**Files:**
- Delete: `src/main/java/dev/attuned/menu/MoveFocusPayload.java`, `src/main/java/dev/attuned/menu/SatchelMoveResolver.java`, `src/main/java/dev/attuned/menu/SatchelNetworking.java`
- Delete: `src/test/java/dev/attuned/menu/SatchelNetworkingContractTest.java`, `src/test/java/dev/attuned/menu/SatchelMoveResolverTest.java`
- Modify: `src/main/java/dev/attuned/Attuned.java` (remove the `SatchelNetworking.init();` line and its import)
- Modify: `src/main/java/dev/attuned/content/behavior/LodestoneBehavior.java`
- Modify: `src/main/java/dev/attuned/combat/Resonance.java`
- Modify: `src/main/java/dev/attuned/combat/AttunedCombat.java`

- [ ] **Step 1: Confirm deadness before deleting.** `grep -rn "MoveFocusPayload\|SatchelMoveResolver\|SatchelNetworking" src/` — the ONLY hits must be the files above, `Attuned.java`'s wiring, and the absence-assertions in `SatchelScreenContractTest` (those survive). If anything else references them, STOP and reassess.
- [ ] **Step 2: Delete + unwire.** Use `git rm` for the five files. Compile check: `.\gradlew.bat compileJava compileTestJava --no-daemon`.
- [ ] **Step 3: Small perf fixes with one new contract test** (`src/test/java/dev/attuned/combat/CombatPerfGatesContractTest.java`): (a) `LodestoneBehavior.onTick` — gate the `getEntitiesOfClass` query with `player.tickCount % 2 != 0` exactly like `RevenantFocusBehaviors` Epitaph does (assert the gate string); (b) `Resonance` decay — apply every 20 ticks at 20× the per-tick rate instead of writing the synced attachment every tick (read `Resonance.java` ~lines 160–175 first; assert `"% 20"` in the tick body and preserve the curve); (c) clamp Thornward reflect and Leech heal against the victim's pool in `AttunedCombat.afterDamage` (~lines 343, 363): wrap `dealtDamage` as `Math.min(dealtDamage, defender.getMaxHealth())` before the 0.25/0.20 multipliers, so Apex Execute's 100000 sentinel cannot reflect ~25k damage in PvP (assert the `Math.min(dealtDamage, defender.getMaxHealth())` string).
- [ ] **Step 4: Red, implement, green; full suite; smoke check** (payload registration changed → boot the server):
  ```powershell
  .\gradlew.bat cleanTest build --no-daemon
  python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60
  ```
- [ ] **Step 5: Changelog** bullets under `### Fixed` (Thornward/Leech clamp) and `### Internal` (dead path removal, decay batching).

### Task 5: Build previews in the Reliquary

Hovering a build name shows its six Foci as item icons with missing ones greyed.

**Files:**
- Create: `src/test/java/dev/attuned/client/BuildPreviewContractTest.java`
- Create after red: `src/main/java/dev/attuned/menu/BuildPreviewResolver.java`
- Create after red: `src/test/java/dev/attuned/menu/BuildPreviewResolverTest.java`
- Modify after red: `src/client/java/dev/attuned/client/screen/SatchelScreen.java`

- [ ] **Step 1: Pure resolver first (Minecraft-free, REAL behavioral test).** `BuildPreviewResolver.availability(List<String> buildSlots, List<String> equippedIds, List<String> satchelIds, Map<String,Integer> inventoryCounts) -> List<Availability>` where `Availability` is an enum `OWNED`/`MISSING` per slot (empty-string slots are `OWNED`). Sourcing rule must MATCH `PresetApplicationResolver`: a build slot counts OWNED if the id can be sourced from (equipped ∪ satchel ∪ inventory) with multiset semantics — two slots wanting the same id need two copies. Write `BuildPreviewResolverTest` with: both-missing, duplicate-id-one-copy (second slot MISSING), satchel+inventory pooling, and empty-slot cases. Red → implement → green.
- [ ] **Step 2: Screen wiring contract test.** Assert `SatchelScreen.java` contains: `"BuildPreviewResolver.availability"`, a hover branch in the build-button render or a `renderTooltip`-style hover path that draws six item icons (assert `"graphics.renderItem"` — VERIFY the fork's item-draw method name first via constraint #4; `FociHud.java` already renders item stacks, copy its call), and a greyed treatment for MISSING (assert your chosen overlay constant name). Client-side stacks for icons: resolve each build slot id via `BuiltInRegistries.ITEM.getValue(Identifier.parse(id))` — this runs in the CLIENT at runtime (registry available), NOT in unit tests.
- [ ] **Step 3: Red, implement, green.** Layout: draw the 6 icons in a single row (6 × 18 = 108px) anchored inside the logical window, below the builds list — REUSE the existing `WELL_*` constants for backing wells; do not let the row escape the 252×200 bounds (constraint #5).
- [ ] **Step 4: Visual check via `runClient`** — save two builds, hover both, verify icons + grey state; confirm clicks while hovered still select normally.
- [ ] **Step 5: Changelog** `### Added` bullet; add a sentence to the Reliquary row in `docs/reference.md`.

### Task 6: Quick-swap keybind

A keybind applies the NEXT saved build without opening the Reliquary, as long as a Focus Reliquary is anywhere in the player's inventory.

**Files:**
- Create: `src/test/java/dev/attuned/menu/QuickSwapContractTest.java`
- Create after red: `src/main/java/dev/attuned/menu/QuickApplyPayload.java`
- Modify after red: `src/main/java/dev/attuned/menu/PresetNetworking.java`
- Modify after red: `src/client/java/dev/attuned/client/AttunedKeybinds.java`
- Modify after red: `src/main/resources/assets/attuned/lang/en_us.json`

- [ ] **Step 1: Write the failing test.** Assert: `QuickApplyPayload.java` is a `record QuickApplyPayload(int index)` with `TYPE` + `CODEC` and a `.cast()` on the stream codec composite (copy `ApplyPresetPayload.java` exactly — read it first); `PresetNetworking.java` registers it serverbound and has a `quickApply` handler that (a) hops to the server thread via `player.level().getServer().execute`, (b) scans the player inventory for the FIRST `AttunedContent.SATCHEL_OF_FOCI` stack instead of requiring the open menu (assert `"firstReliquaryInInventory"` helper name), (c) reuses the SAME apply pipeline as `applyPreset` (assert the shared private method name you extract — extract the common body into `applyPresetToPlayer(player, index, satchelStack)` and assert both handlers call it), (d) honors the existing `LAST_APPLY_TICK` cooldown; `AttunedKeybinds.java` registers `"key.attuned.quick_swap"` (unbound default — copy the `InputConstants.UNKNOWN` idiom of the HUD toggles) and sends `QuickApplyPayload` guarded by `client.player != null`; lang has `"key.attuned.quick_swap"`.
- [ ] **Step 2: Red.** Note `PresetNetworkingContractTest` pins many strings of `PresetNetworking` — read it fully before refactoring; the extraction in (c) WILL move pinned strings; update those pins preserving intent.
- [ ] **Step 3: Implement.** Client-side, the keybind cycles: send `QuickApplyPayload(-1)` meaning "next" — server resolves: track the last-applied index per player in a cleanup-registered map; `-1` → `(last + 1) % presets.size()`; an explicit `>= 0` index applies that index (bounds-checked exactly like `applyPreset`). Send the applied build's name back as an actionbar message (`player.displayClientMessage(..., true)` — verify the method name via constraint #4) so the player sees what equipped.
- [ ] **Step 4: Green; full suite; smoke check** (new payload registration). Then `runClient`: bind the key, save 2 builds, press the key twice with the reliquary in inventory (not held) — loadout must alternate; press with NO reliquary anywhere — must no-op silently.
- [ ] **Step 5: Changelog** `### Added`; document the keybind in `docs/reference.md` (Commands/keybind area).

### Task 7: Grand Focus Reliquary (54 slots)

A second-tier reliquary. `FocusHolder(size, maxPerSlot, items)` and its codec were parameterized for exactly this.

**Files:**
- Create: `src/test/java/dev/attuned/content/GrandSatchelContractTest.java`
- Modify after red: `src/main/java/dev/attuned/content/AttunedComponents.java` (add `GRAND_SATCHEL_SIZE = 54` and a second component `GRAND_SATCHEL_CONTENTS` registered inside the SAME guarded `init()`)
- Modify after red: `src/main/java/dev/attuned/content/SatchelItem.java` (parameterize: constructor takes the component type + size)
- Modify after red: `src/main/java/dev/attuned/content/AttunedContent.java` (register `GRAND_SATCHEL_OF_FOCI`, creative-tab accept adjacent to the existing satchel accept)
- Modify after red: `src/main/java/dev/attuned/menu/SatchelContainer.java`, `SatchelMenu.java`, `SatchelMenuType.java` (size-parameterized: menu rows = `size / 9`; `EQUIPPED_*`/builds panel shift down for 6 rows; `INVENTORY_Y` moves to `18 + rows*18 + 14`)
- Modify after red: `src/client/java/dev/attuned/client/screen/SatchelScreen.java` (logical height grows with rows; texture still blitted at top-left; extra grid rows drawn as programmatic wells like the equipped grid)
- Create after red: `src/main/resources/assets/attuned/items/grand_satchel_of_foci.json`, `src/main/resources/assets/attuned/models/item/grand_satchel_of_foci.json` (copy the satchel pair, swap texture name)
- Create after red: texture via `tools/generate_ui_art.py` — add `grand_satchel_item()` as a palette-shifted variant of `satchel_item()` (deterministic, gold-trim palette), call it from `__main__`
- Create after red: `src/main/resources/data/attuned/recipe/grand_satchel_of_foci.json` (shaped: existing satchel center, amethyst block top, leather corners)
- Modify after red: `src/main/resources/assets/attuned/lang/en_us.json` (display name + `.lore`/`.lore2`/`.effect` — constraint #9)

- [ ] **Step 1: Read first:** `AttunedComponents.java`, `SatchelItem.java`, `SatchelMenu.java`, `SatchelMenuType.java`, `SatchelContainer.java`, `SatchelScreen.java`, and EVERY test pinning them (`AttunedComponentsContractTest`, `SatchelItemContractTest`, `SatchelMenuContractTest`, `SatchelScreenContractTest`, `SatchelGeneratedAssetContractTest`, `SatchelDefinitionlessStackContractTest`, `FocusHolderRoundTripTest`). This task touches the most-pinned subsystem in the repo — budget half the effort for test updates.
- [ ] **Step 2: Write the failing test** asserting: `GRAND_SATCHEL_SIZE = 54`; second component registered inside `init()` after `initialized = true;`; `GRAND_SATCHEL_OF_FOCI` registered and accepted in the creative tab within 400 chars of the satchel accept; the menu computes rows from container size (assert `"size / 9"` or your row-derivation string); both items open the same screen class; recipe JSON exists and `"attuned:satchel_of_foci"` appears as an ingredient; lang keys exist (parse the JSON, `has(...)` checks).
- [ ] **Step 3: Red → implement → green.** Key correctness points: `SatchelMenu.EQUIPPED_START` must derive from the ACTUAL container size, not the constant 27 (grep for every use of `AttunedComponents.SATCHEL_SIZE` in menu/screen/networking and replace with a per-instance size where it describes THIS container, keeping the constant where it means the small satchel); `quickMoveStack` boundaries derive from instance size; nested-bag guard must reject BOTH satchel items in BOTH menus' `mayPlace` (and `PresetNetworking.firstReliquaryInInventory` from Task 6 accepts either).
- [ ] **Step 4: Regenerate art + validate:**
  ```powershell
  python tools/generate_ui_art.py
  python tools/verify_repository.py
  ```
- [ ] **Step 5: Full suite green; smoke check; `runClient`:** craft path untestable in dev quickly — use creative tab; verify open/store/equip/shift-click in BOTH tiers, and that a Grand Reliquary cannot be stored inside a Reliquary or vice versa.
- [ ] **Step 6: Changelog** `### Added`; `docs/reference.md` items table row.

### Task 8: Faction set bonuses

3+ ACTIVE Foci sharing a faction grant a small passive perk. Pure resolver + a tick applier.

**Files:**
- Create: `src/test/java/dev/attuned/content/FactionSetBonusResolverTest.java` (behavioral, Minecraft-free)
- Create after red: `src/main/java/dev/attuned/content/FactionSetBonusResolver.java`
- Create: `src/test/java/dev/attuned/content/FactionSetBonusContractTest.java` (wiring greps)
- Create after red: `src/main/java/dev/attuned/content/behavior/FactionSetBonuses.java`
- Modify after red: `src/main/java/dev/attuned/Attuned.java` (init wiring, directly after `AttunedEffects.init()`)
- Modify after red: `src/main/resources/assets/attuned/lang/en_us.json`, `docs/reference.md`

- [ ] **Step 1: Resolver test first.** `FactionSetBonusResolver.activeSetFactions(List<String> activeFactionIds, int threshold) -> Set<String>` — input is the faction id (or "" for none) of each ACTIVE focus; output is factions with count >= threshold (default 3). Behavioral cases: exactly 3, 2 (no bonus), mixed factions both >= 3, empty strings ignored. Red → implement → green.
- [ ] **Step 2: Perk table (code-driven, in `FactionSetBonuses`).** One modest perk per faction, all implemented with vanilla `MobEffectInstance`s refreshed on an interval (copy the `PassiveEffectRefresher` idiom — read it first), every effect ambient+icon-hidden like `MossheartBehavior` uses:
  - `attuned:unseen` → Speed I while sneaking; `attuned:seafarers` → Luck I while in/near water (reuse `HarborlightBehavior.nearWater` logic by copy); `attuned:offshore` → Water Breathing 10s refresh while submerged; `attuned:radiant` → Regeneration I for 4s when light level >= 12, 60s cooldown; `attuned:reliquary` → Hero of the Villages... NO (too strong) → +2 luck while a named item is held (mirrors Namesake; keep simple: Luck I); `attuned:verdant_choir` → Saturation tick (Hunger restore 1) every 60s while on grass; `attuned:ashen_forge` → Fire Resistance 4s when hostile-hit near heat (reuse Kilnward's heat predicate by copy), 120s cooldown; `attuned:revenant` → Slowness I applied to undead attackers (reuse Bonechill's `chill` shape).
  Implement the tick handler with `ServerTickEvents.END_SERVER_TICK` iterating players (copy the iteration idiom from `Resonance.tick` — read it), throttled `% 20`, deriving faction ids of active slots from `Attunement.resolution` + `definitionFor(...).faction()` (read `FocusDefinition.java` for the accessor name FIRST). Per-player cooldown maps with cleanup registration (constraint #8).
- [ ] **Step 3: Contract test:** init wired in `Attuned.java` after `AttunedEffects.init()`; `"% 20"` throttle; `"FactionSetBonusResolver.activeSetFactions"` used; cleanup registrations present; every faction id string in the perk table exists in `docs/reference.md`'s faction table (read the doc in the test and assert each).
- [ ] **Step 4: Red → implement → green → full suite → smoke check.** In-game sanity (`runClient`, creative): equip 3 seafarers foci, stand in water, confirm Luck appears; unequip one, confirm it stops.
- [ ] **Step 5: Changelog** `### Added`; document the mechanic + per-faction perks in `docs/reference.md` (extend the Factions table with a "Set bonus (3+)" column).

### Task 9: Focus tempering

Reweave TWO copies of the same Focus into one "Tempered" copy: +25% attribute modifier strength, +1 cost, marked by a data component.

**Files:**
- Create: `src/test/java/dev/attuned/content/TemperingResolverTest.java` (behavioral)
- Create after red: `src/main/java/dev/attuned/content/TemperingResolver.java`
- Create: `src/test/java/dev/attuned/content/TemperingContractTest.java`
- Modify after red: `src/main/java/dev/attuned/content/AttunedComponents.java` (a `TEMPERED` boolean/unit component, registered in the guarded `init()`)
- Modify after red: `src/main/java/dev/attuned/menu/ReweavingMenu.java` and `src/main/java/dev/attuned/content/ReweavingResultPicker.java` (read BOTH first — the reweave flow and its tests `RevenantFocusContractTest`/reweaving pins)
- Modify after red: `src/main/java/dev/attuned/effect/AttunedEffects.java` (modifier application ×1.25 when tempered), `src/main/java/dev/attuned/attunement/BudgetResolver.java` or its cost source (+1 cost when tempered — find where per-slot cost is read from `FocusDefinition.cost()` and apply the surcharge there)
- Modify after red: `src/client/java/dev/attuned/client/AttunedTooltips.java` (a "Tempered" line + gold name styling), lang keys

- [ ] **Step 1: Resolver test.** `TemperingResolver.canTemper(String idA, String idB, boolean aTempered, boolean bTempered) -> boolean` (same non-empty id, NEITHER already tempered) and `temperedCost(int baseCost) -> baseCost + 1`. Trivial but pins the rules. Red → green.
- [ ] **Step 2: Reweaving integration.** Read `ReweavingMenu` end-to-end first. Add the second input path: when both inputs are the same focus id and untempered, the result preview is one copy with the `TEMPERED` component set; on take, consume both inputs (mirror how the existing reweave consumes inputs — copy its exact slot/take flow). The result stack must preserve nothing else (fresh `new ItemStack(item)` + component, server-side only).
- [ ] **Step 3: Effects + budget.** In `AttunedEffects` where `ModifierEntry` amounts are applied, multiply `amount * 1.25` when the stack has `TEMPERED` (grep where modifiers get applied — the framework comment in `docs/reference.md` says modifiers are applied on activate and removed on deactivate; find that code path and confirm REMOVAL uses the same id so the amplified modifier is fully removed). In the cost path, `+1` when tempered — and `dormantReasons`/budget output must reflect it automatically since it flows through the same resolver.
- [ ] **Step 4: Contract test:** component registered in guard; `"* 1.25"` (or `TEMPERED_MULTIPLIER` constant) in `AttunedEffects`; `+ 1` surcharge at the cost source; tooltip line key `"tooltip.attuned.tempered"` in lang; `ReweavingMenu` contains the same-id gate via `"TemperingResolver.canTemper"`.
- [ ] **Step 5: Red → implement → green → full suite → smoke.** `runClient`: temper two Rivets at the Reweaving Altar, equip, check tooltip + that knockback resist feels stronger (attribute screen via F3 or effect inspection), check cost went up in the altar readout, and that reweaving two DIFFERENT foci still behaves exactly as before.
- [ ] **Step 6: Changelog** `### Added`; `docs/reference.md` section under the Altar of Reweaving.

### Task 10: Apex identity abilities

When the Focus Ability key is pressed with NO active ability Focus, an Apex capstone may fire its identity ability instead: Maelstrom = chaos nova (knockback + weakness pulse around the player), Stillpoint = tranquility field (nearby hostiles lose target + brief pacify), affinity capstones = no ability (unchanged).

**Files:**
- Create: `src/test/java/dev/attuned/combat/ApexAbilityContractTest.java`
- Modify after red: `src/main/java/dev/attuned/network/FocusAbilityState.java`
- Modify after red: `src/main/java/dev/attuned/combat/Apex.java`
- Modify after red: lang (`"apex.attuned.maelstrom_nova"` actionbar text etc.), `docs/reference.md`

- [ ] **Step 1: Read `FocusAbilityState.java` fully** (it resolves `firstActiveAbility`, owns cooldown maps, syncs `FocusAbilityStatusPayload`) and `Apex.java`'s capstone model (`capstoneOf`, the `Capstone` enum — confirm Maelstrom/Stillpoint enum names by reading, do not guess). Grep tests pinning both (`AbilityCooldownStateContractTest`, `ApexCapstoneContractTest` etc.).
- [ ] **Step 2: Write the failing test.** Assert: `FocusAbilityState` falls through to `"Apex.tryIdentityAbility(player)"` when no ability Focus is active (assert the call string inside the trigger method body); `Apex.java` contains `tryIdentityAbility` with a per-player cooldown map (cleanup-registered), a `MAELSTROM_NOVA_COOLDOWN_TICKS` and `STILLPOINT_FIELD_COOLDOWN_TICKS` of 600 each, radius constants, and that Stillpoint's pacify sets `setTarget(null)` on nearby `Monster` instances (verify the class/method names against the jar per constraint #4 — likely `net.minecraft.world.entity.monster.Monster` and `setTarget`).
- [ ] **Step 3: Implement.** Maelstrom nova: `level.getEntitiesOfClass(LivingEntity.class, box, hostile-or-pvp filter)` (copy the filter usage from `BlackoutBehavior` — read it first), apply knockback away from player + Weakness 100 ticks; particles/sound via the same feedback helpers `AttunedCombat.matchupFeedback`-style (copy one existing feedback method's particle/sound emission). Stillpoint field: drop targets + Slowness I 60 ticks, gentler sound. Both only when `Resonance.atApex(player)` AND the matching capstone — read how `Apex` gates capstone perks today and reuse that exact gate.
- [ ] **Step 4: Red → green → full suite → smoke.** `runClient` with cheats: `/attuned capacity 20`, build a Maelstrom (mixed affinities) loadout, raise resonance in combat, press the ability key with no ability focus equipped — nova fires + cooldown actionbar; repeat for a neutral Stillpoint build.
- [ ] **Step 5: Changelog** `### Added`; document both abilities in `docs/reference.md` (Apex section).

### Task 11: Attunement Sanctum structure

A small data-driven jigsaw structure (one 15×8×15 piece) in lush caves/forest biomes with a loot chest using a dedicated table that rolls 2 themed Foci + shard fragments. The NBT template is GENERATED by a deterministic Python tool (no in-game structure-block workflow).

**Files:**
- Create: `tools/generate_sanctum_template.py` + `tests/test_sanctum_template_contract.py`
- Create after red: `src/main/resources/data/attuned/structure/sanctum.nbt` (generated)
- Create after red: `src/main/resources/data/attuned/worldgen/structure/attunement_sanctum.json`, `src/main/resources/data/attuned/worldgen/structure_set/attunement_sanctum.json`, `src/main/resources/data/attuned/worldgen/template_pool/sanctum/main.json`
- Create after red: `src/main/resources/data/attuned/tags/worldgen/biome/has_sanctum.json`
- Create after red: `src/main/resources/data/attuned/loot_table/chests/sanctum.json`
- Create: `src/test/java/dev/attuned/content/SanctumDataContractTest.java`

- [ ] **Step 0 (MANDATORY discovery — paths differ per MC version, do not guess):**
  ```powershell
  python -c "import zipfile; names=zipfile.ZipFile(r'C:/Users/<USER>/.gradle/caches/fabric-loom/26.1.2/minecraft-merged.jar').namelist(); print([n for n in names if 'worldgen/structure/' in n][:5]); print([n for n in names if n.startswith('data/minecraft/structure') and n.endswith('.nbt')][:5])"
  ```
  Use the EXACT directory names vanilla uses (`structure/` vs `structures/`, `worldgen/structure/` etc.). Then extract ONE small vanilla template (e.g. an igloo piece) and one vanilla structure/structure_set/template_pool JSON triple (e.g. `igloo`) into `tmp/` as your schema reference. Read them.
- [ ] **Step 1: Python NBT writer test-first.** `tests/test_sanctum_template_contract.py`: import the generator via the `importlib.util.spec_from_file_location` idiom used by `tests/test_verify_repository_contract.py`; assert the generator (a) reads `DataVersion` from the extracted vanilla template rather than hardcoding (the test passes a fixture path), (b) produces a gzipped compound whose decoded structure (write a minimal NBT reader inside the test — tag ids 0–12, big-endian; ~60 lines) contains `size` [15,8,15], a `palette` including `minecraft:chiseled_deepslate` and `minecraft:amethyst_block`, exactly one `minecraft:chest` block entity with `LootTable` = `attuned:chests/sanctum`, and (c) is byte-identical across two runs (determinism).
- [ ] **Step 2: Implement the generator.** Stdlib only (struct+gzip — the repo already hand-rolls PNG encoding in `verify_repository.py`, mirror that spirit). Layout: deepslate-brick floor 15×15, four amethyst pillars, a center "altar" of chiseled deepslate with the chest on top, air elsewhere, NO entities, `palette`/`blocks`/`size`/`DataVersion` keys exactly as the vanilla template you extracted. Write output to the repo data path. Run it; commit the generated `.nbt`.
- [ ] **Step 3: Datapack JSONs**, copying the vanilla triple's field-for-field shape: structure JSON (`"type": "minecraft:jigsaw"`, `start_pool` = `attuned:sanctum/main`, `size: 1`, `"biomes": "#attuned:has_sanctum"`, `step: surface_structures`, `terrain_adaptation: beard_thin`); structure_set (spacing 48, separation 24, salt = any fixed int); template_pool with one `single_pool_element` pointing at `attuned:sanctum` template, `projection: rigid`; biome tag listing `minecraft:lush_caves`, `minecraft:forest`, `minecraft:dark_forest` (validate every biome id exists in the jar's `data/minecraft/worldgen/biome/`). Loot table: copy the structure of an existing table in `src/main/resources/data/attuned/loot_table/` and read `content/AttunedLoot.java` to see how Focus pools are injected — if injection keys off table ids, ADD the sanctum table to its reviewed-tables list so themed weighting applies; otherwise give the table two direct pools (2 rolls focus-item entries reusing whatever loot entry helper the JSONs use + 1–3 shard fragments).
- [ ] **Step 4: Java contract test:** every JSON above parses (Gson), the structure JSON references the tag, the tag file lists only biomes that exist in the vanilla jar (test may read the jar path from `gradle.properties`-derived loom cache — if that's too machine-specific, pin the biome ids as literals and validate JSON syntax only), and the loot table id matches the template's chest `LootTable` string (read the .nbt bytes and assert the literal `attuned:chests/sanctum` substring appears — it is stored uncompressed inside the gzip, so gunzip first in the test).
- [ ] **Step 5: Verify in-game (REQUIRED — datapack errors are silent in unit tests):**
  ```powershell
  python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60
  ```
  The smoke check fails on resource-load errors — broken worldgen JSON shows up here. Then `runClient`, create a world, and run `/locate structure attuned:attunement_sanctum` — it must resolve and teleporting there must show the sanctum with a filled chest. If `/locate` errors "not found", the biome tag or structure_set registration is wrong — recheck against the vanilla triple.
- [ ] **Step 6: Changelog** `### Added`; `docs/reference.md` (Loot section paragraph + structure mention).

### Task 12: Resonant surge events

During thunderstorms, a "resonance surge" site may activate near a random online player: for 60s, players inside a 16-block radius gain resonance at 4× rate but the surge pings hostile mobs toward it. Server-only, no new blocks.

**Files:**
- Create: `src/test/java/dev/attuned/combat/ResonantSurgeResolverTest.java` (behavioral)
- Create after red: `src/main/java/dev/attuned/combat/ResonantSurgeResolver.java`
- Create: `src/test/java/dev/attuned/combat/ResonantSurgeContractTest.java`
- Create after red: `src/main/java/dev/attuned/combat/ResonantSurges.java`
- Modify after red: `src/main/java/dev/attuned/Attuned.java` (init after `Resonance.init()`), `src/main/java/dev/attuned/AttunedConfig.java` (+ `surge_interval_ticks` default 12000, `surge_duration_ticks` default 1200, `surge_radius` default 16 — read the config class and `docs/reference.md` config table FIRST; `AttunedConfigContractTest` pins config keys), lang (actionbar strings), `docs/reference.md` config table rows
- [ ] **Step 1: Pure resolver.** `ResonantSurgeResolver` with: `shouldStart(long now, long lastSurgeEnd, long intervalTicks, boolean isThundering, int onlinePlayers) -> boolean`; `isInside(double dx, double dz, int radius)`; `resonanceGainMultiplier() = 4.0F`. Behavioral tests for interval gating, no-players case, thundering requirement, radius edge (exactly radius = inside). Red → green.
- [ ] **Step 2: Server module `ResonantSurges`:** `ServerTickEvents.END_SERVER_TICK`, throttled `% 20`; one active surge max, stored as (dimension key, BlockPos, endTick); start: pick a random online player in a thundering dimension, offset 24–48 blocks horizontally, surface height via the heightmap accessor (verify name per constraint #4: `level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)`); while active: every second, for players inside, call the resonance-grant path multiplied — READ `Resonance.java` to find how combat grants resonance and add a public hook `Resonance.grantSurge(player, amount)` rather than poking the attachment directly; emit `ELECTRIC_SPARK` particles + ambient sound at the site; mobs: every 4s, `getEntitiesOfClass(Monster.class, 32-box)` get a target-position nudge ONLY if they have no current target (verify pathfinding API: prefer just applying Speed I toward... if pathing is fiddly, SKIP the mob lure and keep the risk-reward as "the surge is loud": play a global-ish sound and spawn a visible beacon-beam-like particle column; do NOT sink time into mob AI). End: actionbar to players inside.
- [ ] **Step 3: Contract test:** config keys present in `AttunedConfig` + defaults documented in `docs/reference.md`; init wired; `"% 20"` throttle; cleanup/`onStop` clears the active surge; `"Resonance.grantSurge"` used (and exists in `Resonance.java`).
- [ ] **Step 4: Red → green → full suite → smoke.** `runClient`: `/weather thunder`, temporarily set `surge_interval_ticks` to 200 in the dev `run/config/attuned.json`, confirm a surge starts (particles/sound), stand inside, watch the resonance bar climb fast on the Foci HUD, confirm expiry message. RESET the dev config after.
- [ ] **Step 5: Changelog** `### Added`; config table rows in `docs/reference.md`.

### Task 13: Affinity inspect

Crouch + look at another player for 1.5s shows their committed affinity / Discord / Apex state as an actionbar line. Server-mediated because attachments sync `targetOnly` (you can NOT read another player's attunement client-side — do not try).

**Files:**
- Create: `src/test/java/dev/attuned/network/InspectContractTest.java`
- Create after red: `src/main/java/dev/attuned/network/InspectRequestPayload.java` (serverbound, `record InspectRequestPayload(int targetEntityId)`)
- Modify after red: `src/main/java/dev/attuned/network/AttunedNetworking.java` (register + handle: validate the target is a `ServerPlayer` within 24 blocks and line-of-sight of the requester; rate-limit 20 ticks per requester with a cleanup-registered map; reply via `player.displayClientMessage(..., true)` actionbar — committed affinity name/Discord/Stillpoint/Maelstrom + Apex-ready flag from `Apex.capstoneOf` + `Resonance.atApex`)
- Modify after red: `src/client/java/dev/attuned/client/hud/CombatHud.java` OR a small new client tick handler in `src/client/java/dev/attuned/client/AttunedClient.java`-adjacent file: while `player.isShiftKeyDown()` and the crosshair target is a player (reuse how `CombatHud` finds its target — read it; it already resolves a targeted `LivingEntity`), accumulate hover ticks; at 30 ticks send ONE `InspectRequestPayload`, reset on target change
- Lang: the actionbar format strings; `docs/reference.md` PvP/inspect paragraph

- [ ] **Step 1: Write the failing test:** payload record + `.cast()` codec + serverbound registration in `AttunedNetworking.init()` (copy `AbilityPayload` registration shape exactly — read it first); handler hops to server thread; `"24"` range check + rate-limit map + cleanup; client sends only once per hover (assert the accumulator field name and the `"== INSPECT_HOLD_TICKS"` equality so it can't spam).
- [ ] **Step 2: Red → implement → green → full suite → smoke** (payload registration). Two-player verification is impractical solo — verify the client path fires by pointing at any player entity (a second account or just confirm via logs that no packet sends for mobs), and unit-pin the rest.
- [ ] **Step 3: Changelog** `### Added`; reference doc paragraph.

### Task 14: Release 1.4.0

- [ ] **Step 1: Docs sweep.** Re-read `docs/reference.md` top to bottom against everything added; fix drift. Confirm every new lang key is used and every new item has `.lore`/`.lore2`/`.effect`.
- [ ] **Step 2: Bump `mod_version=1.4.0`** in `gradle.properties`. Verify `CHANGELOG.md` has the complete `## Attuned 1.4.0 - Resonant Depths` section (it gates the publish tooling).
- [ ] **Step 3: Full verification gate (ALL must pass):**
  ```powershell
  .\gradlew.bat cleanTest build --no-daemon
  python tools/verify_repository.py
  python -m unittest discover -s tests
  python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60
  git diff --check
  ```
  (CRLF warnings are normal; anything else is not.)
- [ ] **Step 4: Manual playtest checklist (runClient):** Reliquary previews + quick-swap + Grand tier; temper a focus; trigger one set bonus; fire one Apex ability; `/locate structure attuned:attunement_sanctum`; surge under `/weather thunder`. Fix anything broken BEFORE committing the release commit.
- [ ] **Step 5: Commit `release: Attuned 1.4.0 - Resonant Depths`, push, watch CI** (`gh run watch <id> --exit-status`). CI must be FULLY green including the smoke step before any publish.
- [ ] **Step 6: Publish ONLY when the user explicitly says to:** `./gradlew modrinth` (needs `MODRINTH_TOKEN` in env) and `python tools/publish_curseforge.py` (dry-run first: `--dry-run`). Do not publish without an explicit instruction.
