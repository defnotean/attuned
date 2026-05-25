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
- [ ] Phase 6: Implement world-integration loot expansion while keeping every
  Focus globally eligible.
- [ ] Phase 7: Add lightweight milestone advancements.
- [ ] Phase 8: Add altar memory, shard forecasts, and cosmetic ritual polish.
- [ ] Phase 9: Add Pact previews and UI clarity improvements.

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
- [ ] Add a cap forecast when useful.
  - If capacity is not full, show remaining capacity to cap somewhere compact:
    `6 capacity to cap` or `3 shards to cap`.
  - Prefer one short line, not a paragraph.
  - Do not show this if it would collide with the ready-bind preview.
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
  - `screen.attuned.altar.forecast.capacity_left`: `%s capacity to cap.`
  - `screen.attuned.altar.forecast.shards_left`: `%s shard(s) to cap.`
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

### Priority Focus 5 - Galespur Focus

Purpose: Zephyr mount mobility. While the player is riding, the mount moves at
about twice its normal speed. Target examples: happy ghasts, horses, pigs,
striders, camels, donkeys, mules, and other rideable entities.

Recommended design:

- Name: `galespur_focus`
- Cost: `4` or `5`; start at `4` for testing, raise to `5` if 2x mount speed is
  too strong.
- Affinity: `zephyr`
- Unique: `true`
- Behavior id: `attuned:galespur`
- Effect: `Doubles the speed of creatures and vehicles you ride.`

Implementation details:

- [ ] Create `GalespurBehavior`.
- [ ] Add Focus data at
  `src/main/resources/data/attuned/attuned/focus/galespur_focus.json`:

```json
{
	"item": "attuned:galespur_focus",
	"cost": 4,
	"unique": true,
	"affinity": "zephyr",
	"behavior": "attuned:galespur"
}
```

- [ ] Register item and behavior in `AttunedContent`.
- [ ] Add item definition, model, texture, mcmeta, and language keys following
  the shared checklist above.
- [ ] Suggested lore/effect copy:
  - Name: `Galespur Focus`
  - Lore 1: `A bridle for the wind itself.`
  - Lore 2: `Every road arrives sooner beneath you.`
  - Effect: `Doubles the speed of creatures and vehicles you ride.`
- [ ] Generate a custom item asset for this Focus. This is required before the
  Focus is considered implementation-complete.
  - Do not ship a copied placeholder texture.
  - Output path: `src/main/resources/assets/attuned/textures/item/galespur_focus.png`
  - Size: 16x16 pixels.
  - Style: Minecraft-readable pixel art matching the existing Attuned Focus
    icons.
  - Visual brief: a small wind-charged riding spur or bridle charm, with Zephyr
    cyan/white motion streaks and a metallic silver/gold body.
  - Keep the silhouette readable at inventory scale.
  - Avoid text, letters, detailed horse faces, or large transparent empty space.
  - Add/update `galespur_focus.png.mcmeta` only if the icon should use the same
    animated texture metadata pattern as the existing Foci.
  - After generating the asset, inspect it in-game in the inventory and creative
    tab; do not rely only on the raw PNG.
  - If content contract tests are expanded first, make sure they explicitly
    require this texture path to exist.
- [ ] Track the boosted vehicle per player so the modifier can be removed when:
  - the player dismounts
  - the player switches mounts
  - the Focus goes dormant
  - the player disconnects
  - the mount dies or unloads
- [ ] Use `AttunedPlayerCleanup.onForget(...)` for per-player tracking maps.

Living mount path, first implementation:

- [ ] In `onTick`, get the player's vehicle:
  - `Entity vehicle = player.getVehicle();`
  - if `vehicle == null`, remove any previous boost for that player and return.
- [ ] Only boost the vehicle the player is actually riding.
  - Prefer checking the player is the controlling passenger if the mapped API is
    available.
  - If the controlling-passenger helper is not available, require
    `vehicle.hasPassenger(player)` as the fallback.
- [ ] If `vehicle instanceof LivingEntity living`, apply transient attribute
  modifiers:
  - `Attributes.MOVEMENT_SPEED`: amount `1.0`, operation
    `ADD_MULTIPLIED_BASE`, which targets 2x base ground speed.
  - Also check for a flying-speed attribute if the mapped API exposes one, so
    happy ghasts/flying mounts can be covered without velocity hacks.
- [ ] Use stable modifier ids, for example:
  - `attuned:galespur_mount_speed`
  - `attuned:galespur_mount_flying_speed`
- [ ] Before applying to a new vehicle, remove modifiers from the previously
  tracked vehicle.
- [ ] In `onDeactivate`, always remove modifiers from the tracked vehicle.
- [ ] If the vehicle does not have the relevant attribute instance, skip that
  modifier cleanly.

Generic vehicle path, second implementation:

- [ ] For non-living vehicles such as boats or minecarts, do not blindly multiply
  velocity every tick with no cap. That can explode physics.
- [ ] If generic vehicles are supported, implement a cautious horizontal velocity
  assist:
  - only while the vehicle has player input or is already moving
  - multiply horizontal velocity toward a 2x target
  - clamp the final horizontal speed to a sane maximum per vehicle family
  - never multiply vertical velocity
  - never boost falling, launched, or collision-resolving motion
- [ ] If the generic path is too unstable, ship the first version for living
  mounts only and make the tooltip say `Doubles the speed of creatures you ride.`

Balance notes:

- [ ] Because the requested target is 2x normal speed, keep the Focus unique.
- [ ] Test whether cost 4 is fair beside other Zephyr mobility Foci:
  - Swift cost 2
  - Leap cost 2
  - Drift cost 2
  - Tide cost 3
  - Stormcall cost 4
- [ ] Galespur should be powerful for mounted travel but do nothing on foot.
- [ ] It should not stack with duplicate Galespur Foci.
- [ ] It should not permanently alter a mount after dismounting.

Validation:

- [ ] Test with horse, pig, strider, camel, donkey/mule, and any available happy
  ghast mount in the target Minecraft version.
- [ ] Confirm speed returns to normal after dismounting.
- [ ] Confirm speed returns to normal when the Focus goes dormant.
- [ ] Confirm switching mounts removes the modifier from the old mount.
- [ ] Confirm duplicate Galespur Focus copies do not stack.
- [ ] Confirm non-living vehicle support is either stable or intentionally not
  shipped in the first pass.

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

## Phase 6 - World Integration And Loot Balance

### Goal

Make Foci and shard fragments feel more naturally woven into vanilla exploration
without turning any biome, structure, or activity into the only correct source.
Every shipped Focus should remain possible anywhere Attuned Focus loot can roll.

### Files

- `src/main/java/dev/attuned/AttunedConfig.java`
- `src/main/java/dev/attuned/content/AttunedLoot.java`
- `docs/reference.md`
- `README.md` if the public loot description changes
- `src/test/java/dev/attuned/content/AttunedLootCompatibilityTest.java`
- tests if config parsing or loot weights become more complex

### Current Loot Model To Preserve

- `AttunedLoot` injects one Focus pool and one shard-fragment pool into targeted
  vanilla loot tables.
- Default Focus chances:
  - common: 17.5%
  - rich: 25%
  - treasure: 45%
- Default shard-fragment chances are double the Focus chance.
- Each Focus receives a positive weight in every Focus pool.
- Theme weights bias results but never exclude off-theme Foci.

### Refactor Before Adding More Tables

- [ ] Rename comments and tests that imply all targets are chest-only.
  - Current compatibility test requires `table.getPath().startsWith("chests/")`.
  - After adding fishing/archaeology targets, update the assertion to allow
    approved vanilla target families instead of only chest paths.
- [ ] Keep the `minecraft` namespace requirement.
- [ ] Replace or supplement `chest(String name)` with helpers:

```java
private static Identifier chest(String name) {
	return vanilla("chests/" + name);
}

private static Identifier vanilla(String path) {
	return Identifier.fromNamespaceAndPath("minecraft", path);
}
```

- [ ] Add comments that distinguish:
  - vanilla containers, compatible with Lootr-style per-player table resolution
  - vanilla non-container loot, such as fishing or archaeology
- [ ] Update `docs/reference.md` so it no longer says rewards are only found in
  structure chests after this phase.

### World Integration Targets

- [ ] Add fishing treasure integration.
  - Verify the exact table id in the target Minecraft/Fabric mappings before
    coding. Likely family: `minecraft:gameplay/fishing/treasure`.
  - Recommended tier: `COMMON`.
  - Recommended theme: neutral.
  - Recommended fragment chance: enabled through the same fragment pool, but
    monitor pacing because fishing can be repeated indefinitely.
  - If fishing makes shard farming too easy, add a config multiplier for
    non-structure sources.
  - Recommended first pass: fragments are uncommon, Foci are very rare.
  - Do not make fishing the best way to farm Foci; it should be a pleasant
    surprise while playing vanilla.

- [ ] Add archaeology integration if the target Minecraft version has stable
  archaeology loot tables.
  - Verify exact ids before coding.
  - Candidate families to check:
    - desert pyramid archaeology
    - desert well archaeology
    - trail ruins archaeology
    - ocean ruin archaeology
  - Recommended tier: `COMMON` or a new very-low tier if repeated brushing feels
    too generous.
  - Recommended theme:
    - desert sources: neutral or Fury
    - ocean sources: Zephyr
    - ruins: neutral or Unseen
  - Keep every Focus eligible in each table.
  - Recommended first pass: shard fragments are more common than full Foci.
  - Suspicious sand/gravel sources should feel like finding a splinter of
    attunement history, not a primary progression route.

- [ ] Add trial/challenge loot if available in this version.
  - Verify exact table ids for trial chamber containers, vaults, ominous vaults,
    or reward tables before coding.
  - Recommended tier:
    - regular trial rewards: `RICH`
    - ominous/high-risk rewards: `TREASURE`
  - Recommended theme: mixed or neutral unless the structure itself strongly
    suggests an affinity.
  - Trial rewards can support stronger Focus odds than fishing/archaeology
    because the player is actively taking combat risk.

- [ ] Add stronger structure flavor without exclusivity.
  - Mineshafts: keep or strengthen Unseen/utility bias.
  - Villages: neutral/survival bias through toolsmith, armorer, weaponsmith,
    and temple tables.
  - Nether fortress: Fury bias.
  - Bastions and ancient cities: Bastion bias.
  - Shipwrecks, buried treasure, and end cities: Zephyr bias.
  - Desert pyramid and ruined portal: Unseen or mixed mystery bias.

- [ ] Add village loot identity.
  - Weaponsmith: Fury bias.
  - Armorer: Bastion bias.
  - Toolsmith: neutral utility bias.
  - Temple: Zephyr or neutral support bias.
  - Keep existing village tables in `TARGETS`; tune weights rather than adding a
    separate village-only loot system.

- [ ] Add ruined portal flavor.
  - Recommended theme: mixed/Unseen.
  - Consider a slightly higher chance for shard fragments than common village
    chests because ruined portals are rarer.
  - Do not create "Discord loot" as a separate category until Discord has its
    own item/content identity. For now, "volatile or mixed" means off-theme Foci
    remain possible and Unseen gets a small bump.

- [ ] Add ancient city flavor.
  - Recommended theme: Bastion with Unseen bonus.
  - Rationale: danger, deep stone, silence, and stealth all match the current
    systems.
  - Keep all Foci eligible so ancient cities do not become mandatory for Unseen.

- [ ] Add end city flavor.
  - Recommended theme: Zephyr.
  - Bias movement Foci indirectly through Zephyr weighting; do not special-case
    individual items unless a later loot pass adds item-level tags.

- [ ] Add buried treasure flavor.
  - Recommended theme: neutral or Zephyr.
  - Good place for shard fragments and utility Foci.
  - Keep full Focus chance modest because buried treasure can be map-guided.

- [ ] Add Nether fortress and bastion contrast.
  - Nether fortress: Fury bias, aggressive combat identity.
  - Bastion other/treasure: Bastion bias, heavy defense identity.
  - Avoid making the Nether the fastest shard-fragment farm unless testing
    supports that pacing.

- [ ] Add wandering trader support, very cautiously.
  - Goal: rarely sell an Attunement Shard Fragment or Attunement Journal, not
    full Foci.
  - Verify Fabric API trade helper availability in this Minecraft version before
    coding.
  - Likely file: new helper such as `AttunedTrades.java`, initialized from
    `Attuned.onInitialize()`.
  - Suggested offer:
    - Journal: low price, uncommon, helps onboarding.
    - Shard fragment: moderate emerald cost, rare, limited uses.
  - Do not sell Attunement Shards or Foci by default; that bypasses exploration.

- [ ] Defer cartographer "attuned ruins" maps.
  - Do not implement until Attuned has its own structures or a strong reason to
    point players at vanilla structures.
  - If revisited later, prefer maps to existing vanilla structures rather than
    adding a new structure just for the map.
  - This is explicitly a later idea, not part of the first world-integration
    pass.

### Implementation Steps - Table Additions

- [ ] Expand `AttunedLoot.TARGETS` with the new verified table ids.
- [ ] Keep target additions grouped by source family:
  - vanilla structure chests
  - fishing
  - archaeology
  - trial/challenge rewards
  - trade integration, if added, should live outside `AttunedLoot`
- [ ] If non-container sources need gentler rates, add a new tier:

```java
LOW(0.35F)
```

  - Use it for fishing or archaeology if repeated access is too strong.
- [ ] Keep `Drop` as the single target metadata record unless more detail is
  truly needed.
- [ ] If a source should not roll shard fragments, do not special-case that in
  an ad hoc way. Add a field to `Drop`, for example:

```java
record Drop(Tier tier, Affinity theme, boolean unseenTheme, boolean fragments) {}
```

  - Default existing targets to `fragments = true`.
  - Only turn it off after playtesting shows a source is too farmable.
- [ ] Preserve the invariant tested by `AttunedLootCompatibilityTest`: every
  shipped Focus has positive weight in every target.
- [ ] If item-level flavor is needed later, add metadata instead of hand-picking
  items in loot pools.
  - Example future shape: Focus tags or definition fields like `families`.
  - Do not add one-off `if (focus == SWIFT_FOCUS)` rules in `AttunedLoot`.

### Trade Integration Steps

- [ ] Add trader offers only after loot table expansion is stable.
- [ ] Prefer a separate initializer class:

```java
public final class AttunedTrades {
	public static void init() {
		// Register wandering trader offers here after verifying API names.
	}
}
```

- [ ] Call `AttunedTrades.init()` from `Attuned.onInitialize()`.
- [ ] Keep trade quantities low:
  - Journal: 1 item, a few uses.
  - Shard fragment: 1 item, rare, low max uses.
- [ ] Do not let trader trades grant enough fragments to replace structure
  exploration.
- [ ] Add docs in `docs/reference.md` if trades are shipped.

### Balance Config Steps

- [ ] Preserve `focus_loot_chance` as the base default for compatibility.
- [ ] Add optional tier multipliers:
  - `common_focus_loot_multiplier`, default `0.7`
  - `rich_focus_loot_multiplier`, default `1.0`
  - `treasure_focus_loot_multiplier`, default `1.8`
- [ ] If `LOW` is added, add:
  - `low_focus_loot_multiplier`, default `0.35`
- [ ] Add optional fragment multiplier:
  - `shard_fragment_loot_multiplier`, default `2.0`
- [ ] Consider a separate multiplier for repeatable non-structure loot:
  - `repeatable_loot_multiplier`, default `1.0`
  - Apply only if fishing/archaeology/trials make progression too fast in tests.
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
- [ ] New non-chest targets are verified in-game or through generated loot table
  inspection before release.
- [ ] Lootr remains optional. Do not add a direct Lootr dependency.

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

Make the altar feel like the center of the attunement loop without tying power
progression to decorative block placement. Altar extras should be readable,
cosmetic, or informational unless a later design pass explicitly approves
mechanical power.

### Files

- `src/main/java/dev/attuned/content/AttunementAltarBlock.java`
- `src/main/java/dev/attuned/content/AltarAnimations.java`
- `src/client/java/dev/attuned/client/screen/AltarScreen.java`
- `src/main/java/dev/attuned/menu/AltarMenu.java` only if more synced data is
  needed
- `src/main/resources/assets/attuned/lang/en_us.json`
- Potentially a new block entity class only for persistent altar memory

### Memory Altar

- [ ] Decide whether altar memory must persist.
  - If memory only means "the altar glows with the last binder's affinity," this
    already exists through the `AFFINITY` blockstate. Do not add storage.
  - If memory must remember the last player's name/UUID across world reloads,
    add a block entity. Do not store this in a global map.
- [ ] Lightweight version, preferred first:
  - Treat the existing `AFFINITY` blockstate as the altar's memory.
  - Add GUI/status copy that says what the altar last remembers:
    - `Memory: Fury`
    - `Memory: Bastion`
    - `Memory: Zephyr`
    - `Memory: Unbound`
  - Pull this from the altar blockstate through the menu only if the GUI needs
    it. Otherwise keep it as world visual state only.
- [ ] Persistent player-name version, later if still desired:
  - Add `AttunementAltarBlockEntity`.
  - Store `lastBinderUuid` and `lastBinderName`.
  - Register a block entity type in `AttunedContent`.
  - Make `AttunementAltarBlock` implement the block-entity provider pattern.
  - Update `bindShard` to write last binder data when a shard is bound.
  - Sync minimal display data to the client if the GUI shows the name.
  - Keep saved NBT small and optional. Broken/missing data should not block
    shard binding.

### Affinity Chime

- [ ] In `AttunementAltarBlock.bindShard`, vary the bind sound pitch by the
  player's committed affinity.
  - None/Discord: keep current neutral chime.
  - Fury: slightly lower, sharper pitch.
  - Bastion: lower, steadier pitch.
  - Zephyr: slightly higher pitch.
- [ ] Keep vanilla sounds. Prefer `AMETHYST_BLOCK_CHIME`, `NOTE_BLOCK_CHIME`,
  or other existing soft sounds.
- [ ] Do not add a loud or repeated sound loop.
- [ ] Validate binding several shards in a row does not become annoying.

### Candle Ring Cosmetic

- [ ] Nearby candles should alter particles/sound only.
- [ ] Scan within radius 3 of the altar.
- [ ] Count lit candles, not unlit candles.
- [ ] Do not scan every server tick.
  - For passive client ambience, use `animateTick`.
  - For bind-time flourish, scan once inside `bindShard` or
    `AltarAnimations.begin`.
- [ ] Suggested behavior:
  - 1-3 lit candles: add a few extra warm particles.
  - 4+ lit candles: add a slightly fuller ring during binding.
  - Candle color can be ignored at first.
- [ ] Do not let candles increase capacity, reduce cost, speed cooldowns, or
  change loot.

### Amethyst Cluster Cosmetic

- [ ] Nearby amethyst clusters may add sparkle particles or a slightly brighter
  bind pulse.
- [ ] Scan radius 3.
- [ ] Include budding amethyst or clusters only if mappings are straightforward.
- [ ] Keep the effect visual/audio only.
- [ ] If both candles and amethyst are nearby, combine them gently rather than
  stacking into visual noise.

### Attuned Player Proximity Pulse

- [ ] When a player with at least one active Focus walks near an altar, the altar
  may emit one quiet particle pulse.
- [ ] Implementation path:
  - Prefer client-side `animateTick` if the pulse can be inferred locally.
  - If player state is required server-side, use a low-frequency server tick
    helper rather than scanning every altar every tick.
- [ ] Recommended behavior:
  - radius 5 blocks
  - cooldown at least 80 ticks per altar position
  - particle color follows the player's committed affinity, Discord, or neutral
  - no sound unless testing shows the visual is too subtle
- [ ] This must be ambience only. It should not reveal hidden players, change
  capacity, or activate Foci.

### Shard Forecast

- [ ] Extend the Phase 1 altar preview with one extra compact forecast:
  - `2 shards to cap`
  - `6 capacity remaining`
- [ ] Use `capacityPerShard` and `capacityCap`.
- [ ] Rounding rule:
  - `shardsToCap = ceil((cap - capacity) / (double) capacityPerShard)`
  - if capacity is already full, show `Capacity full`.
- [ ] Do not show both a long ready-bind line and a long forecast line if they
  compete for space. Prefer the ready-bind line when a shard is inserted.

### Dormant Preview

- [ ] When a shard is inserted, preview whether binding it would wake dormant
  Foci.
- [ ] Implementation path:
  - Add an overload or helper in `Attunement` that can resolve active slots with
    a hypothetical capacity:

```java
public static List<Integer> activeSlots(Player player, int hypotheticalCapacity)
```

  - Current active: `Attunement.activeSlots(player)`
  - Future active: `Attunement.activeSlots(player, nextCapacity)`
  - Newly awake count: future active slots minus current active slots.
- [ ] GUI copy:
  - `Binding wakes 1 dormant Focus.`
  - `Binding wakes %s dormant Foci.`
  - If none wake: omit this line or keep the normal forecast.
- [ ] Do not promise that a specific Focus wakes unless the UI has enough room
  and can show the item name without overlap.
- [ ] Add tests around the hypothetical-capacity resolver if this helper is
  added.

### Altar Status Feedback

- [ ] If adding empty-hand status text, keep it short:
  - `Capacity 8 / 20. 3 active, 1 dormant. Stance: Bastion.`
- [ ] Do not replace the GUI. The GUI remains the main binding surface.
- [ ] Best implementation:
  - Empty hand right-click still opens the GUI as it does now.
  - Put status text in the GUI rather than chat to avoid a noisy interaction.
  - If chat status is added later, gate it behind crouch-right-click so normal
    use does not spam messages.

### Altar Recall Journal Page

- [ ] Add a journal page explaining:
  - Altars bind shards.
  - Altars do not store items after the GUI closes.
  - The altar glow remembers the last bound affinity.
  - Decorative candles/amethyst are cosmetic if implemented.
- [ ] Files:
  - `AttunementJournalItem.java`
  - `en_us.json`
- [ ] Keep it short enough for one vanilla book page.

### Validation

- [ ] Bind a shard with no decorative blocks nearby.
- [ ] Bind a shard with lit candles nearby.
- [ ] Bind a shard with unlit candles nearby.
- [ ] Bind a shard with amethyst nearby.
- [ ] Confirm decorations do not affect capacity, cost, loot, cooldown, or
  active Focus resolution.
- [ ] Confirm altar blockstate affinity still updates correctly after binding
  with no committed affinity, each committed affinity, and Discord.

## Phase 9 - Pact Preview And UI Clarity

### Goal

Make the player understand what their build is doing now and what one small
change would unlock next. The UI should clarify, not tutor through paragraphs.

### Files

- `src/client/java/dev/attuned/client/AttunementReadout.java`
- `src/client/java/dev/attuned/client/AttunedTooltips.java`
- `src/client/java/dev/attuned/client/FocusPanel.java` only for visual cues
- `src/client/java/dev/attuned/client/hud/CombatHud.java` if Pact/Apex HUD cues
  need adjustment
- `src/main/java/dev/attuned/pacts/Pacts.java`
- `src/main/resources/assets/attuned/lang/en_us.json`
- `src/main/resources/assets/attuned/textures/gui/*.png` only if art is needed

### Focus Panel Tooltip Clarity

- [ ] Expand `AttunementReadout.tooltip(player)` to show compact build facts:
  - title
  - `Budget: used / capacity`
  - `Remaining: capacity - used`
  - `Active: count`
  - `Dormant: count`
  - stance or affinity
  - Pact status if active or close
  - Apex status if unlocked/ready
- [ ] Suggested layout:

```text
Bound Adept

Budget: 8 / 10
Remaining: 2
Active: 3
Dormant: 1
Affinity: Bastion
Pact: Stoneheart
```

- [ ] If dormant count is greater than zero, add one short hint:
  - `Hover a dormant Focus for details.`
- [ ] Do not add individual Focus names to the panel tooltip. Item tooltips own
  per-slot details.

### Equipped Focus Tooltip Clarity

- [ ] In `AttunedTooltips`, when the hovered stack is one of the player's Focus
  slots, add:
  - `Equipped: Slot %s`
  - `Status: Active`
  - or `Status: Dormant - <reason>`
- [ ] Slot numbers should be player-facing 1-6, not zero-based.
- [ ] If the stack is not equipped in a Focus slot, do not show equipped/status
  lines.
- [ ] Reuse the Phase 2 dormant reason helper.
- [ ] Keep metadata order consistent:
  - affinity
  - faction
  - cost
  - unique
  - equipped/status
  - dormant reason if applicable
- [ ] Preserve lore and effect lines.

### Status Words

- [ ] Standardize short status words across tooltips/readouts:
  - `Awake` for active Foci if a more flavorful word is desired.
  - `Active` is clearer than `Awake`; prefer `Active` in mechanical tooltips.
  - `Dormant` for equipped but inactive Foci.
  - `Unique` for only-one-active Foci.
  - `Neutral` for no affinity.
- [ ] Do not use multiple words for the same mechanical state in the same UI
  surface.
- [ ] Suggested final wording:
  - Tooltip status line: `Status: Active`
  - Tooltip status line: `Status: Dormant - not enough remaining capacity`
  - Focus metadata: `Affinity Neutral`
  - Unique metadata: `Unique - only one can be active`
- [ ] Reserve flavor words like `Awake` for journal prose or advancement titles,
  not precision UI.

### One-Time Onboarding Message

- [ ] Add or extend onboarding for the first dormant Focus.
- [ ] Best location:
  - `AttunedEffects.announceNewDormantSlots` already detects new dormancy.
  - Use `Onboarding` or a player attachment if the message must be shown only
    once per player.
- [ ] Suggested copy:
  - `Lower Focus slots sleep first when capacity is tight. Move it higher or bind shards.`
- [ ] Show this only once per player.
- [ ] Do not repeat it every time a Focus becomes dormant; the normal dormant
  reason message handles repeat cases.
- [ ] If adding persistent onboarding state, update attachment tests/docs as
  needed.

### Capacity Remaining Line

- [ ] Add a `Remaining` line anywhere budget is shown as a summary:
  - Focus panel tooltip
  - optional altar readout
  - `/attuned status` only if command output needs parity
- [ ] Compute as `Math.max(0, Attunement.capacity(player) - Attunement.used(player))`.
- [ ] Do not call this "free slots"; it is remaining budget, not slot count.

### Pact Preview

- [ ] Add a preview helper, preferably in `Pacts`.
- [ ] Goal: tell the player when they are one active matching Focus away from a
  Pact.
- [ ] Existing rule to verify before coding:
  - Pacts wake when the player has enough matching active Foci/pattern for that
    affinity.
  - Read `Pacts.activeOf(player)` and the existing activation logic before
    adding preview code.
- [ ] Suggested helper:

```java
public static Optional<Component> previewOf(Player player)
```

  - Return empty if a Pact is already active.
  - Return empty if player is in Discord.
  - Count active affinity-bearing Foci by affinity.
  - If exactly one more matching Focus would wake a Pact, return a short line:
    `1 more Bastion Focus can awaken Stoneheart.`
- [ ] Keep preview conservative.
  - If the rules are more complex than "three matching active Foci," only show
    preview when the condition is certain.
  - Do not overpromise if capacity would prevent the next Focus from activating.
- [ ] Add this preview to `AttunementReadout.tooltip(player)`, not as persistent
  HUD text.
- [ ] Possible translations:
  - `screen.attuned.readout.pact_preview.bastion`
  - `screen.attuned.readout.pact_preview.fury`
  - `screen.attuned.readout.pact_preview.zephyr`

### Pact Stability Glow

- [ ] When a Pact is active, make same-affinity active Focus slots read more
  clearly in the Focus panel.
- [ ] Preferred implementation:
  - Keep the current active glow logic in `FocusPanel`.
  - If `Pacts.activeOf(player).isPresent()`, slightly increase glow alpha or add
    a tiny tick/accent on active Foci matching the Pact affinity.
  - Do not add text labels inside the panel.
- [ ] Avoid visual overload:
  - Active Focus glow, dormant dim, resonance ring, and Pact cue must not fight
    each other.
  - If the panel becomes too busy, keep Pact information in the hover tooltip
    only.
- [ ] Validation:
  - active Pact with all matching Foci
  - active Pact plus neutral active Foci
  - dormant matching Focus
  - Discord, where no Pact glow should show

### Pact Fade Reason

- [ ] When a Pact fades, explain why if the cause is clear.
- [ ] Existing place to inspect: `Pacts.java`, especially fade/deactivation
  logic and current `pact.attuned.fades` language keys.
- [ ] Suggested reason categories:
  - capacity changed and one required Focus went dormant
  - active affinities changed or Discord began
  - required Focus was removed
  - no longer enough active matching Foci
- [ ] Keep the message short:
  - `Stoneheart fades: not enough active Bastion Foci.`
  - `Windrunner fades: Discord breaks the pattern.`
- [ ] If the code cannot reliably know the exact cause without adding fragile
  state tracking, use one generic but useful line:
  - `%s fades as your active pattern changes.`
- [ ] Do not emit fade messages repeatedly. Only message on actual transition
  from active Pact to no Pact/different Pact.

### Pact Journal Pages

- [ ] Add one short page per Pact after the first-build pages.
- [ ] Each page should include:
  - Pact name
  - affinity/pattern requirement in plain language
  - effect summary
  - one sample build if it fits
- [ ] Suggested pages:
  - Pyresworn: Fury, melee hits ignite
  - Stoneheart: Bastion, incoming damage dulled
  - Windrunner: Zephyr, sprint and step mobility
  - Untethered: special/neutral or cross-pattern, verify current rule in
    `Pacts.java` before writing copy
- [ ] Files:
  - `AttunementJournalItem.java`
  - `en_us.json`
- [ ] Keep each page within vanilla book limits.

### Pact Challenge Advancements

- [ ] Add optional advancements that reward using each Pact in its natural role.
- [ ] Use impossible criteria awarded from code, matching existing advancement
  style.
- [ ] Candidate advancements:
  - Pyresworn: defeat a burning hostile after the Pact ignites it.
  - Stoneheart: survive a heavy hit while Stoneheart is active.
  - Windrunner: sprint a sustained distance while Windrunner is active.
  - Untethered: defeat an affinity-bearing foe while Untethered is active.
- [ ] Keep these as achievements, not required progression.
- [ ] Files:
  - `src/main/resources/data/attuned/advancement/attunement/*.json`
  - `Pacts.java`
  - possibly `AttunedAdvancements.java`
- [ ] Do not add noisy tracking if the condition is hard to detect cleanly.

### Pact Audio Identity

- [ ] Give each Pact awakening a slightly different vanilla sound/pitch combo.
- [ ] Keep the current one-shot feedback structure.
- [ ] Suggested mapping:
  - Pyresworn: warmer/lower chime or fire-adjacent soft sound
  - Stoneheart: deeper chime
  - Windrunner: higher chime
  - Untethered: neutral/mysterious chime
- [ ] Avoid loud combat sounds for UI/state changes.
- [ ] Validate by repeatedly toggling Pact state in a test world; it should feel
  informative, not irritating.

### Soft Pact Feedback

- [ ] Review current Pact awaken/fade messages in `Pacts.java`.
- [ ] Keep one clean moment when a Pact awakens:
  - one short chat/system message
  - one sound
  - one small particle burst
- [ ] Avoid repeated reminders every tick or every combat event.
- [ ] If adding particles:
  - use affinity color
  - emit around the player once
  - keep count low enough for servers
- [ ] Confirm Pact fade remains understandable but not noisy.

### Discord Identity

- [ ] Add a journal page explaining when Discord is useful.
  - It deals more damage and takes more damage.
  - It is a risky stance, not a mistake state.
  - It can be useful for short fights or aggressive builds.
- [ ] Add one tooltip line in `AttunementReadout.tooltip(player)` when Discord:
  - `Discord: higher damage dealt and taken.`
- [ ] Keep Discord visually distinct through existing magenta/gem language.
- [ ] Do not punish Discord beyond existing damage tradeoff unless a balance
  pass proves it is too strong.
- [ ] Add one sample Discord build only if it fits in the journal:
  - Example shape: one strong Fury Focus plus one defensive Bastion Focus.
  - Explain that this is a risky burst stance, not a long-term default.
- [ ] Ensure Pact preview is hidden during Discord so the player does not see
  contradictory "one more Focus" guidance.

### Combat HUD Clarity

- [ ] Review `CombatHud` after Pact/Apex preview work.
- [ ] HUD should answer only immediate combat questions:
  - my stance
  - target stance
  - resonance/Apex readiness
- [ ] Do not add long Pact preview text to the combat HUD.
- [ ] If a Pact icon is added later, use a tiny symbol/gem-style cue with a
  hover tooltip elsewhere, not readable prose in the HUD.

### Validation

- [ ] Hover Focus panel with:
  - no active Foci
  - active neutral Foci
  - one committed affinity
  - Discord
  - one dormant Focus
  - active Pact
  - one Focus away from Pact
  - Apex unlocked but dormant
  - Apex firing
- [ ] Hover equipped Focus stacks in every slot and confirm slot number/status.
- [ ] Confirm non-equipped Attuned items do not claim they are equipped.
- [ ] Confirm Pact preview disappears once the Pact is active.
- [ ] Confirm Pact preview is hidden in Discord.
- [ ] Confirm active Pact glow/cue is not shown when the player has no Pact.
- [ ] Confirm Pact fade reason appears once per actual fade, not every tick.
- [ ] Confirm Pact journal pages fit in the vanilla book UI.
- [ ] Confirm Pact challenge advancements trigger only from real Pact use.
- [ ] Confirm Discord preview/journal text does not imply Discord is always bad.

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
- [ ] If Phase 6 is implemented, verify fishing treasure, archaeology, and
  trial/challenge targets only after their exact vanilla table ids are confirmed.
- [ ] If trader support is implemented, verify wandering traders can rarely sell
  a journal or shard fragment and never sell full Foci by default.
- [ ] Test with Lootr if available.
- [ ] Hover the Focus panel and equipped Foci to confirm UI clarity lines.
- [ ] Force first dormancy on a new player and confirm the one-time onboarding
  message appears once.
- [ ] Check Pact preview with a build that is one Focus away from a Pact.
- [ ] Toggle each Pact and confirm awakening feedback, fade reason, audio
  identity, and any Pact glow are readable but not noisy.
- [ ] Bind at an altar with candles/amethyst nearby and confirm cosmetic-only
  behavior.

## Release Checklist

- [ ] `./gradlew test`
- [ ] `./gradlew build`
- [ ] Update `build.gradle` Modrinth changelog before release.
- [ ] Update `README.md` only if the player-facing feature list changes.
- [ ] Update `docs/reference.md` for config or rule changes.
- [ ] Update `docs/adding-a-focus.md` if the Focus checklist changes.
