# Attuned TODO / Implementation Playbook

This file is intentionally detailed. The goal is that a future implementation
pass can read one section, open the listed files, and write the code without
having to rediscover the design.

Keep the mod vanilla-esque: build on existing Minecraft actions, keep each Focus
small and readable, avoid new currencies/dimensions/large crafting chains, and
make every feature useful without becoming mandatory.

## Working Rules

- [ ] Work one phase at a time. Do not mix altar UI, dormant reasons, and new
  Foci in the same commit.
- [ ] Before starting a phase, run `git status --short --branch` and make sure
  the tree is clean or only contains work for that phase.
- [ ] After each phase, run `./gradlew test`.
- [ ] If the phase touches client UI, also run a client smoke test and inspect
  GUI scale 2 and 3.
- [ ] When adding a new Focus, add the content contract test first or in the same
  commit so missing data/assets are caught immediately.
- [ ] Preserve the existing loot model: every shipped Focus should remain
  eligible everywhere Attuned Focus loot can roll.

## Recommended Next Pass

- [ ] Phase 1: Fix altar readability and add a capacity preview.
- [ ] Phase 2: Add exact dormant Focus reasons to the resolver, tooltips, and
  dormant chat message.
- [ ] Phase 3: Expand content contract tests before adding more Foci.
- [ ] Phase 4: Add a short "first builds" section to the Attunement Journal.
- [ ] Phase 5: Add two to four vanilla-feeling utility Foci.

## Phase 1 - Altar Readability And Capacity Preview

### Goal

Make the Attunement Altar GUI readable on the custom dark altar texture and make
the shard-binding outcome obvious before the player presses Bind.

### Files

- `src/client/java/dev/attuned/client/screen/AltarScreen.java`
- `src/main/resources/assets/attuned/lang/en_us.json`
- `src/test/java/dev/attuned/client/UiAssetContractTest.java` if a source-level
  contract is useful after the change
- `src/main/resources/assets/attuned/textures/gui/altar.png` only if color
  changes in code are not enough

### Current Analysis

- `AltarScreen` draws the title, inventory label, budget readout, stance label,
  forecast line, and hint text with `LABEL_DARK` (`0xFF404040`).
- `textures/gui/altar.png` is mostly dark violet/stone.
- The dark text has low contrast, especially around the title/header and the
  lower status strip.
- The current `Capacity used / capacity` line is also semantically muddy: it
  shows attunement budget usage, not the capacity value that will increase when
  a shard is bound.

### Implementation Steps

- [ ] In `AltarScreen`, replace the single `LABEL_DARK` constant with a small
  named palette:
  - `TITLE_TEXT = 0xFFEDE6FF`
  - `BODY_TEXT = 0xFFE3D8F5`
  - `MUTED_TEXT = 0xFFB8ACC8`
  - `WARNING_TEXT = 0xFFFFD37A`
  - Keep `BAR_TRACK` dark.
- [ ] Update `extractLabels` so title/body text uses the light palette.
  - Title: `TITLE_TEXT`
  - Inventory label: `MUTED_TEXT`
  - Budget, stance, forecast, and hint: `BODY_TEXT`
  - Use the last `graphics.text(..., shadow)` argument deliberately:
    - First try `false` with the light palette.
    - If the title or hint still blends into the texture, use `true` only for
      body/status labels, not for every text element.
- [ ] Rename the current readout from `Capacity` to `Budget` so it means what it
  actually displays:
  - Current: `Capacity 2 / 8`
  - Target: `Budget 2 / 8`
- [ ] Add a separate binding preview in the hint/status line.
  - Empty slot: `Place a shard to bind.`
  - Ready: `Bind: 8 -> 10 / 20 (3 shards)`
  - Capacity full: `Capacity full (20).`
  - If there is only one shard, use `1 shard`; otherwise use `%s shards`.
- [ ] Use `this.menu.capacityPerShard()` and `this.menu.capacityCap()` for the
  preview. Do not hard-code the default `+2` or cap `20`.
- [ ] In `extractLabels`, compute:
  - `capacity = Attunement.capacity(player)`
  - `cap = this.menu.capacityCap()`
  - `next = Math.min(cap, capacity + this.menu.capacityPerShard())`
  - `shardCount = this.menu.inputStack().getCount()`
- [ ] Add or update translation keys in `en_us.json`:
  - `screen.attuned.altar.budget`: `Budget %s / %s`
  - `screen.attuned.altar.hint.empty`: keep or revise to `Place a shard to bind.`
  - `screen.attuned.altar.hint.cap`: `Capacity full (%s).`
  - `screen.attuned.altar.hint.ready.one`: `Bind: %s -> %s / %s (1 shard).`
  - `screen.attuned.altar.hint.ready.many`: `Bind: %s -> %s / %s (%s shards).`
- [ ] Keep all slot coordinates unchanged:
  - `AltarMenu.INPUT_SLOT_X`
  - `AltarMenu.INPUT_SLOT_Y`
  - `AltarMenu.INVENTORY_X`
  - `AltarMenu.INVENTORY_Y`
- [ ] Inspect the custom `BindButton`.
  - It currently draws a dark face and uses `extractDefaultLabel(...)`.
  - If the label remains hard to read, draw the label manually centered inside
    `BindButton.extractContents`.
  - Suggested colors:
    - Enabled label: `0xFFEDE6FF`
    - Hovered label: `0xFFFFFFFF`
    - Disabled label: `0xFF9A90AA`
- [ ] Do not make the altar screen instructional. The UI should be readable and
  scannable, not filled with tutorial text.

### Validation

- [ ] Run `./gradlew test`.
- [ ] Run the client and open the Altar with:
  - no shard inserted
  - one shard inserted
  - multiple shards inserted
  - capacity already at cap
  - no active affinity
  - Fury committed
  - Bastion committed
  - Zephyr committed
  - Discord
  - Pact active
  - Apex ready
- [ ] Check GUI scale 2 and 3.
- [ ] Check a small window. Text must not overlap the shard slot, Bind button,
  budget bar, or inventory label.

### Definition Of Done

- [ ] Every altar label is readable against the current texture.
- [ ] The budget line and binding preview no longer use the same word for two
  different concepts.
- [ ] The Bind button is readable in enabled, hovered, and disabled states.
- [ ] No slot positions move.

## Phase 2 - Dormant Focus Reasons

### Goal

When a Focus is dormant, tell the player exactly why:

- not enough remaining capacity at that slot's priority
- duplicate of a unique Focus that is already active

Do not call this a generic "priority cutoff." The resolver can skip one expensive
Focus and still activate a cheaper lower-priority Focus.

### Files

- `src/main/java/dev/attuned/attunement/BudgetResolver.java`
- `src/main/java/dev/attuned/attunement/Attunement.java`
- `src/main/java/dev/attuned/effect/AttunedEffects.java`
- `src/client/java/dev/attuned/client/AttunedTooltips.java`
- `src/client/java/dev/attuned/client/AttunementReadout.java` optional
- `src/test/java/dev/attuned/attunement/BudgetResolverTest.java`
- `src/main/resources/assets/attuned/lang/en_us.json` if using translations

### Resolver Implementation

- [ ] Add an enum inside `BudgetResolver`:

```java
public enum DormantReason {
	NOT_ENOUGH_CAPACITY,
	DUPLICATE_UNIQUE
}
```

- [ ] Add a record inside `BudgetResolver`:

```java
public record Resolution(List<Integer> activeSlots, Map<Integer, DormantReason> dormantReasons) {}
```

- [ ] Add `resolveDetailed(List<Candidate<I>> candidates, int budget)`.
  - Iterate candidates in priority order, same as `resolve`.
  - Track `used`.
  - Track `activeUnique`.
  - If `candidate.unique()` and `activeUnique.contains(candidate.identity())`,
    put `DUPLICATE_UNIQUE` for that slot and continue.
  - Else if `used + candidate.cost() > budget`, put `NOT_ENOUGH_CAPACITY` for
    that slot and continue.
  - Else activate the slot, add cost to `used`, and if unique add identity to
    `activeUnique`.
  - Use a `LinkedHashMap` for dormant reasons so debug output preserves slot
    order.
- [ ] Keep the existing `resolve(...)` method and make it delegate:

```java
return resolveDetailed(candidates, budget).activeSlots();
```

- [ ] Do not make affinity part of the dormant reason. Affinity creates stance
  or Discord after activation; it does not gate activation.

### Attunement API

- [ ] Add a private helper in `Attunement` to build candidates once:
  - Suggested name: `candidates(Player player)`
  - Return `List<BudgetResolver.Candidate<Item>>`
  - Use the same slot walk currently inside `activeSlots`.
- [ ] Update `activeSlots(Player player)` to call
  `BudgetResolver.resolve(candidates(player), capacity(player))`.
- [ ] Add:

```java
public static Optional<BudgetResolver.DormantReason> dormantReason(Player player, int slot)
```

  - It should call `BudgetResolver.resolveDetailed(...)`.
  - Return the reason for the requested slot if present.
- [ ] Optional, if repeated calls become noisy:

```java
public static Map<Integer, BudgetResolver.DormantReason> dormantReasons(Player player)
```

  - Then have `dormantReason` delegate to it.

### Tooltip Implementation

- [ ] In `AttunedTooltips`, replace the generic dormant block:
  - Current: `Dormant` + `Raise your capacity or remove a Focus.`
  - Target: `Dormant: Not enough remaining capacity.`
  - Target: `Dormant: Duplicate unique Focus.`
- [ ] Use `Attunement.dormantReason(player, slot)` when this exact stack is in
  an inactive Focus slot.
- [ ] Suggested tooltip copy:
  - `NOT_ENOUGH_CAPACITY`: `Move it higher, bind shards, or lower total cost.`
  - `DUPLICATE_UNIQUE`: `Only the first copy can be active.`
- [ ] Keep the tooltip short. The journal can explain the full rule.

### Dormant Chat Message

- [ ] In `AttunedEffects.announceNewDormantSlots`, include the reason in the
  one-shot chat hint.
- [ ] Suggested messages:
  - Capacity: `A Focus falls dormant: <name>. Not enough remaining capacity.`
  - Duplicate: `A Focus falls dormant: <name>. Only one copy can be active.`
- [ ] Preserve the throttle behavior: announce only newly dormant slots and only
  one per tick.

### Focus Panel / Readout

- [ ] The item tooltip is the primary place for exact per-slot reasons.
- [ ] Optionally add a count to `AttunementReadout.tooltip(player)`:
  - `Dormant: 2`
  - `Hover a dormant Focus for details.`
- [ ] Do not add text labels inside the Focus panel itself. The panel is too
  compact.

### Tests

- [ ] Extend `BudgetResolverTest` with:
  - over-budget slot reports `NOT_ENOUGH_CAPACITY`
  - duplicate unique slot reports `DUPLICATE_UNIQUE`
  - later cheaper Focus still activates after an over-budget Focus
  - a unique Focus that is dormant from budget does not claim the unique identity
  - `resolve(...)` still returns the same active slots as before
- [ ] Keep these tests Minecraft-free.

### Validation

- [ ] Run `./gradlew test`.
- [ ] In-game check:
  - Put a too-expensive Focus in a low slot and confirm reason is capacity.
  - Put two copies of a unique Focus in slots and confirm the second says
    duplicate.
  - Move the duplicate above the original and confirm the active copy changes.
  - Confirm Discord is still stance-only and does not make a Focus dormant.

## Phase 3 - Content Contract Tests

### Goal

Before adding more Foci, make tests catch missing files and bad behavior ids.

### Files

- `src/test/java/dev/attuned/content/FocusDataConsistencyTest.java`
- `src/main/java/dev/attuned/content/AttunedContent.java`
- `src/main/resources/assets/attuned/lang/en_us.json`
- `src/main/resources/assets/attuned/items/*.json`
- `src/main/resources/assets/attuned/models/item/*.json`
- `src/main/resources/assets/attuned/textures/item/*.png`
- `src/main/resources/data/attuned/attuned/focus/*.json`

### Implementation Steps

- [ ] Extend `FocusDataConsistencyTest` to assert every shipped Focus has:
  - an item definition at `assets/attuned/items/<name>.json`
  - an item model at `assets/attuned/models/item/<name>.json`
  - a texture at `assets/attuned/textures/item/<name>.png`
  - language key `item.attuned.<name>`
  - language key `item.attuned.<name>.lore`
  - language key `item.attuned.<name>.lore2`
  - language key `item.attuned.<name>.effect`
- [ ] Add a behavior-id test:
  - Read every `behavior` field from Focus definition JSON.
  - Parse `AttunedContent.java` for registered behavior ids in
    `AttunedRegistries.registerBehavior(...)`.
  - Assert every referenced behavior id is registered.
- [ ] Keep this test file-level only. Do not bootstrap Minecraft registries.
- [ ] Preserve existing faction tests.

### Definition Of Done

- [ ] A new Focus cannot be half-added without failing `./gradlew test`.
- [ ] Missing lore/effect text fails fast.
- [ ] A typo like `"behavior": "attuned:lantren"` fails fast.

## Phase 4 - Journal First Builds

### Goal

Add a short in-game guide section with concrete, copyable starter builds. This
helps players understand capacity and affinity without reading external docs.

### Files

- `src/main/java/dev/attuned/content/AttunementJournalItem.java`
- `src/main/resources/assets/attuned/lang/en_us.json`
- Optional: `docs/README.md` if the docs should mention the new journal pages

### Current Focus Costs To Use

- Starting capacity default: `4`
- `swift_focus`: cost 2, Zephyr
- `leap_focus`: cost 2, Zephyr
- `softstep_focus`: cost 2, Zephyr, Unseen
- `drift_focus`: cost 2, Zephyr
- `nightgaze_focus`: cost 2, neutral
- `harvest_focus`: cost 2, neutral
- `beacon_focus`: cost 2, neutral
- `iron_focus`: cost 3, Bastion
- `emberward_focus`: cost 3, Bastion
- `edge_focus`: cost 3, Fury
- `frenzy_focus`: cost 3, Fury
- `vital_focus`: cost 4, Bastion

### Build Examples

- [ ] Add one page for starting capacity 4:
  - `Scout: Swift + Leap = 4, Zephyr mobility.`
  - `Utility: Harvest + Nightgaze = 4, neutral survival.`
  - `Survivor: Vital = 4, simple Bastion durability.`
- [ ] Add one page for early capacity 6:
  - `Bastion: Iron + Emberward = 6.`
  - `Fury: Edge + Frenzy = 6.`
  - `Zephyr: Swift + Leap + Softstep = 6.`
- [ ] Add one page explaining how to adjust:
  - Higher slots wake first.
  - Neutral Foci can fit any lane.
  - If a Focus goes dormant, move it higher or bind more capacity.

### Implementation Steps

- [ ] Add `journal.attuned.page11`, `page12`, and `page13` to `en_us.json`.
- [ ] In `AttunementJournalItem.createGuideContent`, append the three new pages
  after `page10`.
- [ ] If `showGuide(Player player)` should mirror the book, add new summary
  lines or leave it as a compact chat version. Do not spam chat with every page.
- [ ] Keep each written-book page short. Vanilla book pages have limited space.

### Validation

- [ ] Run `./gradlew test`.
- [ ] In-game, right-click the journal and page through all pages.
- [ ] Confirm no page text spills beyond the vanilla book page.

## Phase 5 - Add Vanilla-Feeling Utility Foci

### Shared Checklist For Every New Focus

For a new Focus named `<name>_focus`, add all of these:

- [ ] `src/main/java/dev/attuned/content/AttunedContent.java`
  - Add `public static final Item <NAME>_FOCUS = register("<name>_focus");`
  - Add it to `FOCI`.
  - If it has behavior, register behavior in `init()`:
    `AttunedRegistries.registerBehavior(id("name"), new NameBehavior())`
    using the existing `Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "...")`
    pattern.
- [ ] `src/main/resources/data/attuned/attuned/focus/<name>_focus.json`
  - Set `item`, `cost`, optional `affinity`, optional `faction`, optional
    `modifiers`, and optional `behavior`.
- [ ] `src/main/resources/assets/attuned/items/<name>_focus.json`
  - Copy an existing item definition and update the model path.
- [ ] `src/main/resources/assets/attuned/models/item/<name>_focus.json`
  - Copy an existing generated item model and update `layer0`.
- [ ] `src/main/resources/assets/attuned/textures/item/<name>_focus.png`
  - Add a 16x16 texture.
  - Temporary implementation can copy an existing texture, but final release
    should have distinct art.
- [ ] `src/main/resources/assets/attuned/textures/item/<name>_focus.png.mcmeta`
  - Copy an existing Focus mcmeta if animated item metadata is expected.
- [ ] `src/main/resources/assets/attuned/lang/en_us.json`
  - Add `item.attuned.<name>_focus`
  - Add `.lore`
  - Add `.lore2`
  - Add `.effect`
- [ ] If it has behavior:
  - Add `src/main/java/dev/attuned/content/behavior/<Name>Behavior.java`
  - Use `AttunedPlayerCleanup.onForget(...)` for any per-player maps.
  - Remove transient modifiers/effects/state in `onDeactivate`.
- [ ] Run `./gradlew test`.
- [ ] In-game, confirm:
  - item exists in the Attuned creative tab
  - tooltip shows cost/affinity/effect
  - Focus can be equipped
  - Focus activates only while in budget
  - loot tests still pass, meaning the Focus is eligible in the shared loot pool

### Priority Focus 1 - Hearth Focus

Purpose: Cozy vanilla survival utility around lit campfires.

Recommended design:

- Name: `hearth_focus`
- Cost: `2`
- Affinity: neutral
- Behavior id: `attuned:hearth`
- Effect: While near a lit campfire, slowly recover health if hunger is high
  enough.

Implementation details:

- [ ] Create `HearthBehavior`.
- [ ] Use `onTick`, throttled by a per-player counter.
- [ ] Every 80 ticks, scan a small cube around the player:
  - radius 4 horizontally
  - radius 2 vertically
  - look for `Blocks.CAMPFIRE` or `Blocks.SOUL_CAMPFIRE`
  - require `CampfireBlock.LIT` to be true
- [ ] If a lit campfire is nearby and the player is hurt:
  - require `player.getFoodData().getFoodLevel() >= 12`
  - apply `MobEffects.REGENERATION` for 80 ticks, amplifier 0
  - use ambient/visible flags similar to existing behavior effects
- [ ] Do not heal in a way that beats potions or golden apples.
- [ ] On deactivate, remove the per-player tick counter. Do not forcibly remove
  Regeneration; short duration is enough.
- [ ] Suggested tooltip effect:
  - `Near lit campfires, slowly recover health while well fed.`

### Priority Focus 2 - Anchor Focus

Purpose: Defensive Bastion Focus that rewards bracing instead of always-on armor.

Recommended design:

- Name: `anchor_focus`
- Cost: `3`
- Affinity: `bastion`
- Behavior id: `attuned:anchor`
- Effect: Gain knockback resistance while sneaking or blocking.

Implementation details:

- [ ] Create `AnchorBehavior`.
- [ ] Use a transient attribute modifier on `Attributes.KNOCKBACK_RESISTANCE`.
- [ ] Use a stable modifier id, for example:
  `Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "anchor_focus_braced")`
- [ ] In `onTick`, check:
  - `player.isShiftKeyDown()` for sneaking/crouching
  - or `player.isBlocking()` for shield use, if available in the mapped API
- [ ] If braced and modifier is missing, add:
  - amount `0.45`
  - operation `AttributeModifier.Operation.ADD_VALUE`
- [ ] If not braced and modifier is present, remove it.
- [ ] In `onDeactivate`, always remove the modifier.
- [ ] Suggested tooltip effect:
  - `Gain knockback resistance while sneaking or blocking.`
- [ ] Balance check:
  - It should help shield play and cliff safety.
  - It should not grant permanent knockback immunity while sprinting.

### Priority Focus 3 - Lantern Focus

Purpose: Exploration utility that makes darkness readable without becoming x-ray.

Recommended design:

- Name: `lantern_focus`
- Cost: `2`
- Affinity: neutral or Bastion. Prefer neutral unless testing says it needs a
  lane.
- Behavior id: `attuned:lantern`
- Effect: Holding a torch or lantern in low light reveals nearby visible hostile
  mobs with subtle particles or a very short Glowing effect.

Implementation details:

- [ ] Create `LanternBehavior`.
- [ ] Use `onTick`, throttled to every 20 ticks.
- [ ] Trigger only when:
  - main hand or offhand is `Items.TORCH`, `Items.SOUL_TORCH`,
    `Items.LANTERN`, or `Items.SOUL_LANTERN`
  - local brightness at the player is low, target threshold around 7
- [ ] Search `Monster` entities in an AABB around the player:
  - range 8 blocks
  - require `mob.isAlive()`
  - require `player.hasLineOfSight(mob)` if using Glowing
- [ ] Preferred feedback:
  - send a small number of Dust or WITCH particles around visible mobs
  - avoid applying Glowing if particles read well enough
- [ ] If using Glowing:
  - duration 30 to 40 ticks
  - amplifier 0
  - ambient true
  - particles false
  - line of sight required so it does not reveal mobs through walls
- [ ] Suggested tooltip effect:
  - `Holding a torch or lantern in darkness reveals nearby visible threats.`

### Priority Focus 4 - Rainstep Focus

Purpose: Situational Zephyr mobility that shines in weather and wet terrain.

Recommended design:

- Name: `rainstep_focus`
- Cost: `2`
- Affinity: `zephyr`
- Behavior id: `attuned:rainstep`
- Effect: Move slightly faster in rain, shallow water, or waterlogged terrain.

Implementation details:

- [ ] Create `RainstepBehavior`.
- [ ] Use a transient movement-speed modifier.
- [ ] Stable modifier id:
  `Identifier.fromNamespaceAndPath(Attuned.MOD_ID, "rainstep_focus_wet_speed")`
- [ ] In `onTick`, apply the modifier only when one of these is true:
  - `level.isRainingAt(player.blockPosition())`
  - player is touching water/rain according to available mapped helper
  - player is standing in a waterlogged block
- [ ] Suggested modifier:
  - attribute `Attributes.MOVEMENT_SPEED`
  - amount `0.12`
  - operation `ADD_MULTIPLIED_BASE`
- [ ] Remove the modifier when the player is dry.
- [ ] Always remove it in `onDeactivate`.
- [ ] Suggested tooltip effect:
  - `Move faster through rain and wet ground.`
- [ ] Balance check:
  - It should not replace Swift Focus in dry terrain.
  - It should feel good during rain, rivers, swamps, and caves with water.

### Later Focus Ideas

- [ ] Cinder Focus
  - Fury, cost 3 or 4.
  - Bonus melee damage against burning enemies.
  - Best implementation path: extend combat event handling, likely in
    `AttunedCombat` or a new combat helper initialized from `Attuned`.
  - Avoid another always-on raw damage Focus.

- [ ] Forager Focus
  - Neutral, cost 2.
  - Small chance for extra seeds/berries/food when breaking leaves, grass, or
    crops.
  - Best implementation path: Fabric block-break event if available; otherwise
    defer until the correct event hook is confirmed.
  - Keep output low so it does not replace farms.

- [ ] Tremor Focus
  - Neutral or Bastion, cost 3.
  - Mining stone occasionally hints at nearby ores with sound/particles.
  - Do not draw outlines, reveal exact blocks, or behave like x-ray.
  - Best implementation path: block-break event, scan a small radius for ore
    tags, emit ambiguous particles/sound from the player or mined block.

- [ ] Waystone Focus
  - Neutral, cost 2 or 3.
  - Compass-style recovery utility for last death, bed, or bound altar.
  - Compare against Beacon Focus before implementing so the two do not overlap.

## Phase 6 - Loot And Balance Config

### Goal

Make loot progression tunable without changing the default feel.

### Files

- `src/main/java/dev/attuned/AttunedConfig.java`
- `src/main/java/dev/attuned/content/AttunedLoot.java`
- `docs/reference.md`
- tests if config parsing or loot weights become more complex

### Implementation Steps

- [ ] Preserve `focus_loot_chance` as the base default for compatibility.
- [ ] Add optional tier multipliers:
  - `common_focus_loot_multiplier`, default `0.7`
  - `rich_focus_loot_multiplier`, default `1.0`
  - `treasure_focus_loot_multiplier`, default `1.8`
- [ ] Add optional fragment multiplier:
  - `shard_fragment_loot_multiplier`, default `2.0`
- [ ] In `AttunedLoot`, replace hard-coded enum multipliers only after config
  values exist.
- [ ] Clamp all final chances to `[0.0, 1.0]`.
- [ ] Update `docs/reference.md` with the new config keys and defaults.

### Validation

- [ ] Existing default config produces the same effective chances:
  - common Focus: 17.5%
  - rich Focus: 25%
  - treasure Focus: 45%
  - fragments: double each Focus chance
- [ ] Malformed config still falls back safely, matching current behavior.

## Phase 7 - Advancement Hooks

### Goal

Reward players for discovering the system without adding quests.

### Files

- `src/main/java/dev/attuned/AttunedAdvancements.java`
- `src/main/java/dev/attuned/effect/AttunedEffects.java`
- `src/main/java/dev/attuned/attunement/Attunement.java` if helper state is
  needed
- `src/main/resources/data/attuned/advancement/attunement/*.json`
- existing systems:
  - `Pacts.java` already awards Pact advancements
  - `Apex.java` already awards Apex
  - `Resonance.java` already awards favored matchup

### Implementation Steps

- [ ] Add advancement JSON files using the existing impossible-criterion style:
  - `first_focus.json`
  - `first_dormant_focus.json`
  - `first_discord.json`
- [ ] Parent them under `attuned:attunement/root`.
- [ ] Award `first_focus` in `AttunedEffects.tickPlayer` when the player has at
  least one active Focus.
- [ ] Award `first_dormant_focus` when `dormantSlots` is non-empty.
- [ ] Award `first_discord` when `Attunement.isDiscord(player)` becomes true.
- [ ] Do not add chat spam. Advancement toast is enough.
- [ ] Ensure each award path also awards root through `AttunedAdvancements.award`.

### Validation

- [ ] In-game, equip first Focus and confirm toast.
- [ ] Force dormancy and confirm toast.
- [ ] Equip mixed active affinities and confirm Discord toast.
- [ ] Confirm existing Pact/Apex advancements still trigger.

## Phase 8 - Altar Ritual Polish

### Goal

Make the altar feel alive without tying power progression to decorative block
placement.

### Cosmetic Hooks

- [ ] Nearby candles and amethyst clusters may alter particles/sound only.
- [ ] Committed affinity may tint particles more strongly.
- [ ] Decorative blocks must not increase capacity, reduce cost, or change loot.

### Implementation Path

- [ ] Keep this in `AttunementAltarBlock` or `AltarAnimations`.
- [ ] For passive ambience, prefer `animateTick`.
- [ ] For bind-time flourish, use `AltarAnimations.begin(...)`.
- [ ] Scan only a small radius, such as 3 blocks, and only occasionally.
- [ ] Avoid expensive per-tick world scans on the server.

### Altar Status Feedback

- [ ] If adding empty-hand status text, keep it short:
  - `Capacity 8 / 20. 3 active, 1 dormant. Stance: Bastion.`
- [ ] Do not replace the GUI. The GUI remains the main binding surface.

## Manual QA Checklist

- [ ] Fresh world, new player, starting capacity 4.
- [ ] Equip two low-cost Foci and verify budget display.
- [ ] Equip over budget and verify dormant reason.
- [ ] Bind a shard through direct right-click.
- [ ] Bind a shard through the Altar GUI.
- [ ] Close the Altar GUI with a shard still inserted and verify it returns to
  inventory.
- [ ] Open inventory with recipe book hidden and visible; Focus panel should hide
  only when the recipe book is open.
- [ ] Open creative survival tab and verify Focus panel placement.
- [ ] Loot a targeted vanilla structure chest and verify Foci/fragments can roll.
- [ ] Test with Lootr if available.

## Release Checklist

- [ ] `./gradlew test`
- [ ] `./gradlew build`
- [ ] Update `build.gradle` Modrinth changelog before release.
- [ ] Update `README.md` only if the player-facing feature list changes.
- [ ] Update `docs/reference.md` for config or rule changes.
- [ ] Update `docs/adding-a-focus.md` if the Focus checklist changes.
