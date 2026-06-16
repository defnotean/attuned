# Attuned 1.5 "Echoes and Accords" Implementation Plan

> **Implementation:** Work tasks in order using the checkbox (`- [ ]`) syntax below.
>
> **PREREQUISITE: the 1.4 plan (`2026-06-10-attuned-1-4-resonant-depths.md`) must be fully landed and released before starting this plan.** Several tasks build on 1.4 outputs (Grand Reliquary, faction set bonuses, Sanctum). The "READ THIS FIRST" constraints section of the 1.4 plan applies VERBATIM here — read it first, every constraint is load-bearing.

**Goal:** Ship Attuned 1.5: Trial Pacts (opt-in timed challenges that pay capacity), build sharing (export/import codes), a journal statistics chapter, the Hollow Chorus faction (six echo/sculk-themed Foci with new art), Sanctum depths (a second, harder structure piece with a warden-elite encounter), and PvP/server balance config flags.

**Architecture / Tech Stack:** identical to the 1.4 plan (attachments + components + pure resolvers + idempotent init()s + cleanup-registered maps + source-grep/Minecraft-free test split).

---

### Task 1: Trial Pacts (opt-in timed challenges)

A player kneels (sneaks) at an Attunement Altar and accepts a Trial: a constraint tracked for N in-game days; success pays capacity (through the existing `Milestones` flow) or a Focus reward; breaking the constraint voids it. THREE trials ship: **Ascetic** (equip at most 2 Foci for 2 days → +1 capacity once per world), **Pacifist** (kill no passive mobs for 1 day → reliquary-themed Focus reward), **Stormwalker** (take no fall damage for 2 days → +1 capacity, once).

**Files:**
- Create: `src/test/java/dev/attuned/pacts/TrialResolverTest.java` (behavioral, Minecraft-free)
- Create after red: `src/main/java/dev/attuned/pacts/TrialResolver.java`
- Create: `src/test/java/dev/attuned/pacts/TrialPactsContractTest.java`
- Create after red: `src/main/java/dev/attuned/pacts/TrialPacts.java`
- Modify after red: `src/main/java/dev/attuned/attunement/AttunedAttachments.java` (a persistent `TRIALS` attachment: `List<ActiveTrial(String id, long startedTick, long endsTick)>` codec'd like `PRESETS` — copy its builder shape exactly, `targetOnly` synced + `copyOnDeath`)
- Modify after red: `src/main/java/dev/attuned/menu/AltarMenu.java` or `AltarNetworking.java` (the accept entry point — read both first and put the serverbound accept payload where Bind/altar payloads already live)
- Modify after red: `src/client/java/dev/attuned/client/screen/AltarScreen.java` (a "Trials" button + minimal list UI, following the SatchelScreen builds-list button pattern)
- Modify after red: lang + `docs/reference.md`

- [ ] **Step 1: Pure resolver first.** `TrialResolver` holds the trial table (id → duration ticks, reward kind) and pure checks: `isViolated(String trialId, TrialEvent event)` where `TrialEvent` is a small enum+payload record (EQUIPPED_COUNT_CHANGED(n), KILLED_PASSIVE, TOOK_FALL_DAMAGE), and `isComplete(long now, long endsTick)`. Behavioral tests: each trial's violation matrix, completion boundary (now == endsTick completes), unknown-id rejection. Red → green.
- [ ] **Step 2: Runtime wiring.** `TrialPacts.init()` (idempotent, wired in `Attuned.onInitialize` after `Pacts.init()`): hook the existing events — equip changes via the attachment write path (find where `AttunedAttachments.setSlot` is called server-side and add a post-write notifier, or poll equipped count in an existing `% 20` tick), passive kills via `ServerLivingEntityEvents.AFTER_DEATH` (filter `!CombatTargets.isHostileOrPvpOpponent`), fall damage via `ServerLivingEntityEvents.AFTER_DAMAGE` + `source.is(DamageTypes.FALL)` (verify the damage-type key name per constraint #4). Violation → clear trial + red chat line. Completion check in the tick: pay via `Milestones`/`AttunedAttachments.addMilestone` (one-shot rewards key off milestone ids like `"trial:ascetic"` — read `Milestones.java` first for the award idiom) and capacity via `AttunedAttachments.setCapacity(player, getCapacity(player) + 1)` clamped by config cap.
- [ ] **Step 3: Contract test:** attachment registered with persistent+sync+copyOnDeath; init wired; all three event hooks registered; rewards route through milestones (no double-award); altar screen has the trials button; lang keys exist.
- [ ] **Step 4:** Red → implement → green → full suite → smoke check → `runClient` sanity (accept Ascetic with 3 Foci equipped → instant violation message; accept with 2 → survives).
- [ ] **Step 5:** Changelog `### Added`; reference.md section "Trial Pacts".

### Task 2: Build sharing codes

Export a saved build as a chat-copyable code string; import a code into your build list. Pure string codec — no networking changes (import = the existing SavePresetPayload with a decoded name+slots... NO: SavePresetPayload captures CURRENT equipped. Add a dedicated `ImportBuildPayload`).

**Files:**
- Create: `src/test/java/dev/attuned/menu/BuildCodeResolverTest.java` (behavioral)
- Create after red: `src/main/java/dev/attuned/menu/BuildCodeResolver.java`
- Create: `src/test/java/dev/attuned/menu/BuildShareContractTest.java`
- Create after red: `src/main/java/dev/attuned/menu/ImportBuildPayload.java`
- Modify after red: `src/main/java/dev/attuned/menu/PresetNetworking.java` (register + validate: every id resolved against the FOCUS_DEFINITIONS registry like `applyPreset` does; unknown ids dropped with a chat warning; respects MAX_PRESETS)
- Modify after red: `src/client/java/dev/attuned/client/screen/SatchelScreen.java` (Copy button → `setClipboard` on the selected build (verify the fork's clipboard accessor: grep the client jar for `keyboardHandler` / `setClipboard`); Import button → reads clipboard, client-side `BuildCodeResolver.decode`, sends payload)
- Lang + reference.md

- [ ] **Step 1: Codec design (fixed, do not improvise):** `attuned-b1.<name-base64url>.<csv of focus PATHS base64url>` — paths only (strip the `attuned:` namespace, rejoin on decode), name length-clamped to 32 exactly like `FocusPreset.normalizeName`. `BuildCodeResolver.encode(FocusPresetLike)` / `decode(String) -> Optional<DecodedBuild>`: reject wrong prefix, bad base64, >6 slots, names that decode empty. Behavioral tests: round-trip, hostile inputs (garbage, oversized, empty-slot preservation), namespace stripping. Red → green.
- [ ] **Step 2:** Wire payload + buttons (the builds panel from 1.4 Task 5 has the layout; add Copy/Import as half-width buttons in a new row — keep ALL widgets inside the logical window bounds, constraint #5).
- [ ] **Step 3:** Contract pins: payload registered serverbound; server re-validates ids against the registry (assert the registry lookup string); MAX_PRESETS honored; rate-limit map with cleanup.
- [ ] **Step 4:** Red → green → full suite → smoke → `runClient` (export, delete, re-import the same build).
- [ ] **Step 5:** Changelog; reference.md.

### Task 3: Journal statistics chapter

A new Attunement Journal chapter showing lifetime stats: foci found, reweaves, trials completed, resonance peak, apex activations.

**Files:**
- Create: `src/test/java/dev/attuned/attunement/AttunedStatsContractTest.java`
- Modify after red: `src/main/java/dev/attuned/attunement/AttunedAttachments.java` (a persistent `STATS` attachment: `Map<String, Integer>` codec — `Codec.unboundedMap(Codec.STRING, Codec.INT)`; synced targetOnly; NOT copyOnDeath-exempt — stats persist, keep copyOnDeath)
- Create after red: `src/main/java/dev/attuned/attunement/AttunedStats.java` (static `increment(player, key)` + `max(player, key, value)` helpers; ALL writes server-side)
- Modify after red: increment call sites — read each first: focus loot grant (`AttunedLoot`), reweave take (`ReweavingMenu`), trial completion (Task 1's `TrialPacts`), apex activation (`Apex` — wherever the capstone first activates), resonance peak (`Resonance` tick: `max("resonance_peak_pct", (int)(value*100))`)
- Modify after red: `src/client/java/dev/attuned/client/screen/AttunementJournalScreen.java` + journal chapter wiring (read how chapters/pages are declared — `AttunementJournalScreen` has a chapter-drift fail-fast added in June 2026; find the chapter registry it validates and extend BOTH sides together)
- Lang (the chapter title + stat labels), reference.md

- [ ] **Step 1:** Contract test pinning: attachment builder shape, `AttunedStats` helper signatures, each increment call site (one distinctive string per site), journal chapter registered and the drift guard updated, lang keys.
- [ ] **Step 2:** Red → implement → green. The journal screen renders the stats chapter from the synced attachment (client read like `AttunedAttachments.getPresets`).
- [ ] **Step 3:** Full suite → smoke → `runClient` (stats page renders zeros on a fresh world; reweave once and see the counter tick).
- [ ] **Step 4:** Changelog; reference.md journal section.

### Task 4: Hollow Chorus faction (six new Foci)

Echo/sculk-themed faction: `attuned:hollow_chorus`. Six Foci — **Echolink** (sculk sensors don't trigger from your steps), **Resonant Shell** (hostile hit in darkness grants brief Resistance, deepslate-gated), **Murmur** (ability: 5s of complete silence — no sound events from you, 400t cooldown), **Wakeless** (warden anger toward you accrues at half rate), **Chorusgrasp** (teleport-style ability: swap positions with your latest thrown ender pearl... TOO COMPLEX — replace with: ender pearls you throw take no fall damage and cost no hunger), **Deepheart** (below y=0, +2 armor and Darkness immunity).

**Files (per-focus, repeat the standard recipe for each):**
- Data: `src/main/resources/data/attuned/attuned/focus/<name>_focus.json` (faction `attuned:hollow_chorus`, costs 2–4, `"unique": true` for every behavior-bearing one)
- Item registration in `AttunedContent` (copy an existing focus item registration; creative tab accept in the focus block)
- Behavior class in `content/behavior/` where a stat modifier can't express it (Echolink, Murmur, Wakeless, Deepheart darkness-immunity part), registered in `AttunedFocusBehaviors`
- Textures: extend `tools/generate_ui_art.py` with a `hollow_chorus_focus_items()` generator producing all six 16×16 PNGs via palette-shifted variants of the existing focus-texture generator functions (READ the existing focus texture generators first — if existing foci textures are static files not generated, then instead copy the nearest-shaped existing focus PNG per item and recolor it deterministically with a small new `tools/recolor_focus_textures.py` that maps palettes; commit generator + outputs)
- Items/model JSONs per item (copy an existing focus pair)
- Lang: name + `.lore` + `.lore2` + `.effect` per focus (constraint #9)
- Tests: one `HollowChorusContractTest` (data: all six JSONs parse, faction string consistent, behavior ids registered, unique flags; lang complete) + behavioral tests for any pure logic (e.g. Wakeless anger arithmetic if expressible purely)
- `docs/reference.md`: faction table row + behavior table rows

- [ ] **Step 1: Verify feasibility per focus BEFORE writing code** (constraint #4 jar greps): sculk sensor step-trigger suppression (Echolink) — look for the vibration/game-event system (`GameEvent`, `VibrationSystem`); if suppressing requires a mixin into the vibration listener, write the mixin + MANDATORY smoke check; if it looks like >1 mixin or fragile, DOWNGRADE Echolink to "sculk sensors within 8 blocks are calmed (deactivated) while you sneak" via block-state writes, which needs no mixin. Same diligence for Murmur's silence (likely a mixin on the player's sound emission — if fragile, downgrade to "mobs' detection range halved", reusing Whisper's mechanism) and Wakeless (warden anger API: `Warden.increaseAngerAt` — a mixin halving the amount for tagged players; verify the method exists). Record each decision as a comment in the focus JSON's behavior class.
- [ ] **Step 2:** Implement focus-by-focus: data+item+lang+texture first (renders in creative tab), then behavior test-first, then `docs/reference.md`. Run `python tools/verify_repository.py` after the texture step (PNG validation).
- [ ] **Step 3:** Loot: confirm `AttunedLoot` auto-includes any registered focus with definition data (reference.md says every registered Focus with data joins every pool — verify the code does this dynamically; if themed weighting is desired, add hollow-chorus weighting for ancient-city/deep-dark tables following the unseen-weighting precedent).
- [ ] **Step 4:** Full suite → smoke → `runClient` (each focus equips, tooltip text correct, one behavior spot-check each).
- [ ] **Step 5:** Changelog (per-focus bullets); set-bonus row for the new faction IF 1.4's faction set bonuses landed (perk suggestion: Hollow Chorus 3+ → Darkness ticks on you expire 50% faster).

### Task 5: Sanctum Depths

Extend the 1.4 Sanctum: a second jigsaw piece below the main one (depth 2 jigsaw), holding a better loot chest guarded by elite mobs (no new entity class — equipment-buffed vanilla spawns via the template's spawner or structure mob overrides).

**Files:**
- Modify: `tools/generate_sanctum_template.py` (add a `depths` piece: 11×7×11 sealed deepslate vault with a `minecraft:spawner` block entity configured for 2 reinforced zombies — verify spawner NBT shape from a vanilla template/wiki via the extracted reference, and a second chest with `attuned:chests/sanctum_depths`)
- Modify: `tests/test_sanctum_template_contract.py` (cover the new piece: spawner present, loot id correct, determinism)
- Create: `src/main/resources/data/attuned/worldgen/template_pool/sanctum/depths.json`; modify the main pool/structure JSON for `size: 2` with a jigsaw block connecting down (the main template needs a `minecraft:jigsaw` block entry pointing at the depths pool — this is the hardest part: study a vanilla multi-piece pool (e.g. trail ruins) extracted from the jar FIRST, and copy the jigsaw block's NBT representation exactly)
- Create: `src/main/resources/data/attuned/loot_table/chests/sanctum_depths.json` (richer: 3 focus rolls + 1 shard)
- [ ] **Steps:** vanilla-reference study → template generator red/green → datapack JSONs → smoke check (resource errors) → `runClient` `/locate` + teleport + verify both pieces generate connected, spawner works, chest filled. If the jigsaw connection fails repeatedly (>3 attempts), FALL BACK to a single taller template containing both rooms (size 1) — ship the content, not the tech.
- [ ] Changelog; reference.md.

### Task 6: Server balance config flags

PvP-sensitive numbers become config keys so server owners can tune without a datapack: Thornward reflect %, Leech heal %, Needle opener %, Apex execute threshold, resonance gain multiplier.

**Files:**
- Create: `src/test/java/dev/attuned/AttunedBalanceConfigContractTest.java`
- Modify after red: `src/main/java/dev/attuned/AttunedConfig.java` (5 new keys with current values as defaults — read the existing key pattern + `AttunedConfigContractTest` pins first)
- Modify after red: `src/main/java/dev/attuned/combat/AttunedCombat.java`, `UnseenCombat.java`, `Apex.java`, `Resonance.java` (replace the literals with config reads — each is pinned by tests; update pins preserving intent. NOTE: lang tooltips state these percentages (e.g. "25%"); add a sentence to each affected `.effect` lang string: "(server-configurable)" rather than trying to template numbers into lang)
- Modify after red: `docs/reference.md` config table

- [ ] Red → implement → green → full suite → smoke. Changelog `### Added` ("server balance config").

### Task 7: Release 1.5.0

Same shape as the 1.4 release task: docs sweep, `mod_version=1.5.0`, full gate (build, verify_repository, python tests, smoke, `git diff --check`), manual playtest checklist covering each task above, commit `release: Attuned 1.5.0 - Echoes and Accords`, push, CI fully green. **Publish only when the user explicitly says to.**
