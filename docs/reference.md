# Reference

A lookup for the fields, values, and numbers you will meet while editing Foci.
For the full walkthrough see [adding-a-focus.md](adding-a-focus.md).

## The Focus definition file

Lives in `src/main/resources/data/attuned/attuned/focus/<name>.json`.

| Field       | Type    | Required | Meaning |
|-------------|---------|----------|---------|
| `item`      | text    | yes      | The item id, always `attuned:<name>`. |
| `cost`      | number  | no (1)   | Attunement points the Focus uses. Usually 2–6. |
| `unique`    | boolean | no (false) | If true, only one copy of this Focus can be active at once; a duplicate stays dormant. |
| `affinity`  | text    | no       | `fury`, `bastion`, `zephyr`, or `holy`. Omit for a neutral Focus. |
| `faction`   | text    | no       | Optional story/gameplay family, e.g. `attuned:unseen`. It does not change affinity math. |
| `modifiers` | list    | no ([])  | Stat changes — see [Attribute modifiers](#attribute-modifiers). |
| `behavior`  | text    | no       | A registered behavior id, e.g. `attuned:stormcall`. |

A Focus with no `affinity`, no `modifiers`, and no `behavior` is legal — it just
does nothing. Most Foci use either `modifiers` or `behavior` (or both).

## Affinities

Four affinities form a counter cycle:

```
Holy  beats  Fury  beats  Bastion  beats  Zephyr  beats  Holy
```

- A Focus with an `affinity` pulls its wearer toward that lane. When every
  active affinity-bearing Focus shares one affinity, the wearer is **committed**
  to it — counter-combat and the Apex capstone apply.
- Running active Foci of **two or more** affinities is **Discord**: every Focus
  still works, but the wearer is a glass cannon — dealing and taking extra
  damage. A full every-affinity Discord build can reach **Maelstrom**, a special
  Apex path that keeps the risky mixed-affinity identity.
- A Focus with **no** `affinity` is *neutral* — it never triggers Discord and
  fits any committed lane. Four or more active neutral Foci can reach
  **Stillpoint**, the neutral Apex path.

**Apex identity abilities.** The two affinity-less Apex paths each gain an active
ability fired from the **Focus Ability** keybind — but only while armed (at Apex
resonance) and while no awake ability Focus is equipped to claim the key first.
The affinity capstones (Execute, Unyielding, Untouchable, Judgment) own no such
ability; their key press still reports that no ability Focus is equipped.

- **Maelstrom** unleashes a **chaos nova**: every nearby hostile (and any
  PvP-affectable player) is knocked away from you and hit with Weakness for five
  seconds. On a 30-second cooldown.
- **Stillpoint** spreads a **tranquility field**: nearby hostile monsters lose
  their target and are briefly slowed, buying you a moment of calm. On a
  30-second cooldown.
- Pick an affinity for combat-flavoured Foci; leave it off for utility Foci.

**Reading an opponent (inspect).** Crouch and hold your crosshair on another
player for about 1.5 seconds to read their public stance on your action bar: their
committed affinity, **Discord**, or **Neutral**, plus their Apex capstone and
whether it is currently armed. This reveals nothing the Combat HUD's enemy gem
does not already telegraph — the lookup is server-mediated (attunement state syncs
only to its owner, so it can never be read client-side), range-limited to 24
blocks with line of sight, and rate-limited per onlooker.

## Factions

Factions are optional labels for Foci that share a theme. They show on tooltips
and can be used by loot weighting, but they are not affinities and never change
the Holy/Fury/Bastion/Zephyr cycle.

When **three or more ACTIVE Foci share a faction**, that faction grants a small,
free passive **set bonus** — no extra attunement cost. The bonus is a modest
vanilla effect (icon-hidden, kept refreshed) and most are conditional on context.
Drop below three active Foci of the faction and the bonus simply stops.

Attuned currently ships these factions:

| Faction id | Theme | Balance role | Set bonus (3+) |
|------------|-------|--------------|----------------|
| `attuned:unseen` | Stealth, stillness, smoke, ambush openings | Rewards setup and positioning without replacing armor, speed, or raw damage builds. | Speed I while sneaking. |
| `attuned:seafarers` | Fishing, shore travel, return points | Peaceful Luck and water utility without PvP pressure. | Luck I while in or near water. |
| `attuned:offshore` | Salvage, storms, wreck maps, deep-water risk | Utility with danger: temporary tools, water pressure, and anti-drowned/guardian space without becoming a permanent weapon line. | Water Breathing while submerged. |
| `attuned:radiant` | Holy vows, light, witness, judgment | Reveals and protects in short windows rather than adding broad damage. | A brief Regeneration I in bright light (light level >= 12), on a 60s cooldown. |
| `attuned:reliquary` | Names, relics, rites, thresholds | Utility-side Holy tools that reward preparation and place. | Luck I. |
| `attuned:verdant_choir` | Roots, bloom, moss, patient growth | Broad natural utility with small travel and survival numbers. | A little hunger restored while standing on grass, on a 60s cooldown. |
| `attuned:ashen_forge` | Heat, craft, rivets, tempering | Craft-flavoured Fury/Bastion tools with restrained stat bonuses. | Fire Resistance near a lit forge, magma, or lava. |
| `attuned:revenant` | Unfinished endings, debts, rites, grave-cold reprisals | Utility and controlled combat pressure through short revenge windows, cleansing, slowness, and one active movement ability. | Nearby undead are slowed (Slowness I). |

## Attribute modifiers

Each entry in the `modifiers` list is three fields:

```json
{ "attribute": "minecraft:armor", "amount": 2, "operation": "add_value" }
```

The framework applies these when the Focus becomes active and removes them when
it stops — you never write cleanup code.

### Operations

| Operation              | What `amount` does |
|------------------------|--------------------|
| `add_value`            | Adds a flat amount. `2` on armor = +2 armor. |
| `add_multiplied_base`  | Adds `amount` × the stat's **base** value. `0.15` = +15% of the base. |
| `add_multiplied_total` | Multiplies the **final** total by `1 + amount`. `0.15` = +15% after everything else. |

Use `add_value` for flat bonuses, `add_multiplied_base` for a clean percentage
of the unmodified stat (this is what most percentage Foci want).

### Common attributes

| Attribute                          | Controls | Note |
|------------------------------------|----------|------|
| `minecraft:max_health`             | Maximum health | `2` = one heart |
| `minecraft:armor`                  | Armor points | |
| `minecraft:armor_toughness`        | Armor toughness | |
| `minecraft:knockback_resistance`   | Knockback resistance | `0`–`1`, where `1` is immune |
| `minecraft:movement_speed`         | Walking speed | percentage works best |
| `minecraft:sneaking_speed`         | Sneaking speed | percentage works best |
| `minecraft:attack_damage`          | Melee damage | |
| `minecraft:attack_speed`           | Attack cooldown speed | |
| `minecraft:jump_strength`          | Jump height | percentage works best |
| `minecraft:safe_fall_distance`     | Blocks you can fall unhurt | |
| `minecraft:fall_damage_multiplier` | Fall-damage scale | `-1` cancels all fall damage |
| `minecraft:block_break_speed`      | Mining speed | |
| `minecraft:step_height`            | Auto-step height | |
| `minecraft:luck`                   | Loot luck | |
| `minecraft:scale`                  | Body size | |
| `minecraft:water_movement_efficiency` | Movement speed underwater | |
| `minecraft:oxygen_bonus`           | Underwater breath | |

Any vanilla player attribute works — these are just the common ones.

## Behaviors

A behavior is a Java class for a Focus power that a stat cannot express. It
implements `FocusBehavior`, which has four hooks — all run on the server, and
all are optional:

| Hook            | When it runs |
|-----------------|--------------|
| `onActivate`    | Once, when the Focus becomes active. |
| `onTick`        | Every server tick (20×/second) while active. |
| `onDeactivate`  | Once, when the Focus stops being active. Undo things here. |
| `onAbility`     | When the player presses the Focus Ability keybind, if active. |

"Active" means equipped **and** within the attunement budget. A Focus pushed
over budget goes dormant and counts as deactivated.

Behaviors are registered in `AttunedFocusBehaviors.java` and referenced by the
`behavior` field. The ones that ship with Attuned, smallest first — good
examples to copy from:

| `behavior` id        | Source file              | What it does |
|----------------------|--------------------------|--------------|
| `attuned:delver`     | `DelverBehavior`         | Refreshes a Haste effect. |
| `attuned:nightgaze`  | `NightgazeBehavior`      | Refreshes Night Vision. |
| `attuned:softstep`   | `SoftstepBehavior`       | Makes crouched movement silent. |
| `attuned:emberward`  | `EmberwardBehavior`      | Fire immunity. |
| `attuned:tide`       | `TideBehavior`           | Underwater breathing. |
| `attuned:galespur`   | `GalespurBehavior`       | Doubles the speed of living mounts while riding. |
| `attuned:rainstep`   | `RainstepBehavior`       | Movement speed in rain, water, or waterlogged blocks. |
| `attuned:anchor`     | `AnchorBehavior`         | Knockback resistance while sneaking or blocking. |
| `attuned:rivet`      | `RivetBehavior`          | Grounded knockback resistance while crouching, blocking, or standing on metal blocks. |
| `attuned:kilnward`   | `KilnwardBehavior`       | Hostile hits near lit furnaces, magma, or lava grant brief Resistance. |
| `attuned:temper`     | `TemperBehavior`         | Forge-block use briefly empowers fully charged melee hits. |
| `attuned:hearth`     | `HearthBehavior`         | Campfire-adjacent regeneration while well fed. |
| `attuned:rootstep`   | `RootstepBehavior`       | Movement and fall-damage help while standing on natural blocks. |
| `attuned:harborlight` | `HarborlightBehavior`    | Near water, a held or nearby placed lantern grants gentle night vision in low light. |
| `attuned:driftglass` | `DriftglassBehavior`     | Held compasses point back to the latest fishing or boating return point. |
| `attuned:lantern`    | `LanternBehavior`        | Briefly marks visible threats in darkness while holding a torch or lantern. |
| `attuned:votive`     | `RadiantFocusBehaviors`  | Bright light or lit candles grant a short absorption shield on cooldown. |
| `attuned:bellwether` | `RadiantFocusBehaviors`  | Bells reveal visible threats nearby. |
| `attuned:oathguard`  | `RadiantFocusBehaviors`  | Blocking grants a short absorption shield on cooldown. |
| `attuned:censer`     | `RadiantFocusBehaviors`  | Bright light or campfires trim poison and wither durations. |
| `attuned:namesake`   | `RadiantFocusBehaviors`  | Custom-named carried items or nearby named non-player mobs grant Luck. |
| `attuned:threshold`  | `RadiantFocusBehaviors`  | Crossing from low light into bright light grants a short shield. |
| `attuned:epitaph`    | `RevenantFocusBehaviors` | Pulls nearby experience orbs gently toward the player. |
| `attuned:hollowstep` | `RevenantFocusBehaviors` | Ability-key spectral step up to 5 blocks through entities, never through walls. |
| `attuned:bloodfury`  | `BloodfuryBehavior`      | Attack speed scaled by missing health. |
| `attuned:harvest`    | `HarvestBehavior`        | Speeds up nearby crops. |
| `attuned:forager`    | `ForagerBehavior`        | Sometimes adds small food or seed rewards while gathering plants. |
| `attuned:bloom`      | `BloomBehavior`          | Rare seeds, flowers, or honeycomb while gathering plants. |
| `attuned:mossheart`  | `MossheartBehavior`      | Hostile hits grant brief Resistance while standing on moss, grass, or leaves. |
| `attuned:tremor`     | `TremorBehavior`         | Mining stone briefly outlines the nearest ore vein, including through nether blackstone and basalt. |
| `attuned:aegis`      | `AegisBehavior`          | Periodic absorption shield (uses a cooldown). |
| `attuned:lodestone`  | `LodestoneBehavior`      | Pulls nearby dropped items in. |
| `attuned:beacon`     | `BeaconBehavior`         | Points a held compass at your bed. |
| `attuned:waystone`   | `WaystoneBehavior`       | Points a single held compass at your last death, deferring to Beacon if both are active. |
| `attuned:veil`       | `VeilBehavior`           | Crouch still in low light to become invisible until broken. |
| `attuned:blackout`   | `BlackoutBehavior`       | Ability-key compact smoke pulse that briefly blinds nearby targeting mobs and drops target. |
| `attuned:mask`       | `MaskBehavior`           | Crouching in low light briefly resists reveal and Glowing effects. |
| `attuned:smoke`      | `SmokeBehavior`          | Ability-key smoke burst that drops mobs with broken line of sight. |
| `attuned:whisper`    | `WhisperBehavior`        | Ability-key hush that briefly softens broken-sight mob detection. |
| `attuned:stormcall`  | `StormcallBehavior`      | Lightning while sprinting in rain. |
| `attuned:voidstep`   | `VoidstepBehavior`       | Blinks forward on the Focus Ability keybind. |
| `attuned:harpoon`    | `HarpoonBehavior`       | Ability key summons a temporary custom-model trident for 30 seconds, then removes it from inventory, drops, or projectile state. |
| `attuned:wildward`   | `WildwardBehavior`       | Confluence (Mossheart + Rootstep): refreshes Resistance I while standing on natural ground. |
| `attuned:sunwarden`  | `SunwardenBehavior`      | Confluence (Votive + Bellwether): refreshes Regeneration I while standing in bright light. |
| `attuned:forgewarded` | `ForgewardedBehavior`   | Confluence (Kilnward + Emberward): refreshes Fire Resistance while near a lit forge, magma, or lava. |

### When a power is not a behavior

A few effects are too special for the tick-based behavior hooks and live in
their own classes — read these if your idea resembles them:

- **Death effects** — `combat/GravebindSave.java` hooks the death event.
- **Affinity combat and hit procs** — `combat/AttunedCombat.java` handles
  damage between affinities plus Cinder, Thornward, and Leech.
- **Unseen ambush combat** - `combat/UnseenCombat.java` handles Needle's
  opening-hit bonus and breaks Veil when combat starts.
- **Revenant combat** - `combat/RevenantCombat.java` handles Ashen Debt's
  revenge window, Last Rites cleansing, and Bonechill slowing.
- **Seafarers fishing** — `content/behavior/SeafarersFishing.java` handles
  fishing-Focus perks that hook the catch event: Netmender repairs one point of
  fishing-rod durability on a successful catch (on a cooldown), and Linecast adds a
  chance for a bonus fish plus a Luck of the Sea boost. Neither uses a `behavior`
  id, so they do not appear in the Behaviors table above.

## Confluences

A **Confluence** is a small set bonus that wakes when a specific combination of
2–3 named Foci are all **active at once** (equipped *and* within your attunement
budget — the same "active" rule the rest of the mod uses). A Confluence costs
**no attunement budget**: it is an emergent reward for a build you already paid
for. Wake one and it announces in chat; the first time you wake a given
Confluence it plays a discovery fanfare, records a hidden advancement, and fills
in its row in the Attunement Journal's **Confluences** chapter (undiscovered rows
show only `???` and a member count, so finding them is part of the game).

The Foci HUD paints a small pip per active Confluence, and the Focus panel tooltip
hints when you are exactly one Focus away from a Confluence you have **already
discovered** (undiscovered ones are never hinted, so the meta is not spoiled).

Confluences are data-driven, loaded from
`data/<namespace>/attuned/synergy/<name>.json`:

| Field | Type | Notes |
|-------|------|-------|
| `members` | list of item ids | The Focus **item** ids (e.g. `attuned:lantern_focus`) that must all be active. Author 2–3. |
| `modifiers` | list | Attribute modifiers granted while active, same shape as a Focus's `modifiers`. Optional. |
| `behavior` | text | Optional `behavior` id into the same registry Foci use, for effects a modifier can't express. |

Member ids are Focus **item** ids, not behavior ids. A Confluence may only
reference Foci that exist. Keep effects modest — a Confluence should make a themed
build feel cohesive, never out-scale a focused affinity build.

The ones that ship with Attuned:

| Confluence (`id`) | Members | Effect |
|-------------------|---------|--------|
| Immovable (`immovable`) | Anchor + Rivet | +0.1 knockback resistance |
| Bulwark of Light (`bulwark_of_light`) | Votive + Oathguard | +1 armor |
| Hunter's Patience (`hunters_patience`) | Lantern + Veil | +4% movement speed |
| Tempest (`tempest`) | Rainstep + Stormcall | +1 attack damage |
| Wildward (`wildward`) | Mossheart + Rootstep | Resistance I while on natural ground |
| Sunwarden (`sunwarden`) | Votive + Bellwether | Regeneration I while standing in bright light |
| Forgewarded (`forgewarded`) | Kilnward + Emberward | Fire Resistance while near a lit forge, magma, or lava |
## Behavior palette (no Java)

Most behaviors are Java classes. The **behavior palette** lets a datapack define
some behaviors as data instead — no code, no rebuild. A palette entry is a small,
parameterized behavior instance loaded from a synced datapack registry at:

`data/<namespace>/attuned/focus_behavior/<id>.json`

A Focus references it the same way it references a code behavior — with its
`behavior` field. Resolution is **code-first-then-data**: if a code behavior is
registered under that id it wins; otherwise Attuned builds the behavior from the
matching `focus_behavior` entry. So a code behavior and a palette entry can never
collide, and existing Foci are unaffected.

Each entry has a `type`. v1 ships one type:

### `attuned:conditional_mob_effect`

Keeps a mob effect refreshed on the wearer for as long as a [condition](#conditions)
holds. Passive only (it owns no Focus Ability key).

| Field            | Type            | Required | Meaning |
|------------------|-----------------|----------|---------|
| `type`           | text            | yes      | `attuned:conditional_mob_effect` |
| `effect`         | mob effect id   | yes      | e.g. `minecraft:speed` |
| `amplifier`      | int 0–255       | no (0)   | Effect level minus one (0 = level I). |
| `duration_ticks` | int ≥ 1         | no (40)  | How long each application lasts (20 ticks = 1 s). |
| `refresh_ticks`  | int ≥ 1         | no (20)  | Re-apply once the remaining duration drops to this. |
| `condition`      | condition object| yes      | When the effect should be kept up. |

The effect is applied ambient, hidden, and icon-less (like Tide's Water Breathing)
so it stays unobtrusive. When the condition stops holding the effect is left to
lapse on its own short duration — keep `duration_ticks` modest.

### Conditions

A `condition` is a small composable predicate. It dispatches on a `condition` field:

| `condition`     | Extra fields           | Holds when… |
|-----------------|------------------------|-------------|
| `in_rain`       | —                      | The player is in rain or otherwise wet from the sky. |
| `underwater`    | —                      | The player's head is submerged. |
| `low_light`     | `max_light` (0–15, def 7) | Local light level ≤ `max_light`. |
| `sneaking`      | —                      | The player is crouching. |
| `on_block_tag`  | `tag` (block tag id)   | The block at the player's feet is in the tag. |
| `in_biome_tag`  | `tag` (biome tag id)   | The player stands in a biome in the tag. |

### Worked example

A Focus that grants Speed I while standing in the rain — no Java.

`data/attuned/attuned/focus_behavior/rainspeed.json`:

```json
{
  "type": "attuned:conditional_mob_effect",
  "effect": "minecraft:speed",
  "amplifier": 0,
  "duration_ticks": 40,
  "refresh_ticks": 20,
  "condition": { "condition": "in_rain" }
}
```

`data/attuned/attuned/focus/rainspeed_focus.json`:

```json
{
  "item": "attuned:tide_focus",
  "cost": 2,
  "affinity": "zephyr",
  "behavior": "attuned:rainspeed"
}
```

`/attuned validate` checks palette-backed `behavior` ids too, so a typo in either
file is caught the same way a missing code behavior is.

## Numbers you can tune

Server-side settings live in `config/attuned.json`, written on first launch.
Every key is optional and falls back to a built-in default:

| Key | Default | Meaning |
|-----|---------|---------|
| `starting_capacity` | 4 | Attunement capacity a new player begins with. |
| `capacity_cap` | 20 | Highest capacity an Attunement Shard can reach. |
| `capacity_per_shard` | 2 | Capacity each Attunement Shard grants. |
| `focus_loot_chance` | 0.25 | Base chance a targeted loot table yields a Focus. |
| `low_loot_multiplier` | 1.0 | Extra multiplier for low-tier repeatable/exploration loot, after the built-in 0.35 tier scale. |
| `common_loot_multiplier` | 1.0 | Extra multiplier for common loot, after the built-in 0.7 tier scale. |
| `rich_loot_multiplier` | 1.0 | Extra multiplier for rich loot, after the built-in 1.0 tier scale. |
| `treasure_loot_multiplier` | 1.0 | Extra multiplier for treasure loot, after the built-in 1.8 tier scale. |
| `shard_fragment_loot_multiplier` | 1.0 | Extra multiplier for shard fragment rolls, after their normal 2x Focus chance scale. |
| `voidstep_cooldown_ticks` | 200 | Cooldown of the Voidstep blink, in ticks. |
| `gravebind_cooldown_ticks` | 1200 | Cooldown of the Gravebind death-save, in ticks. |
| `broadcast_pact_deaths` | true | If true, Pact death messages are broadcast to nearby players; if false, only the dying player sees them. |

Loot chances are clamped between 0 and 1 after all scales are applied. With
defaults, the appended Focus-pool roll chances stay unchanged: low 8.75%,
common 17.5%, rich 25%, and treasure 45%. Shard fragment rolls stay twice the
matching Focus chance, also clamped. Archaeology tables preserve vanilla
single-stack brushable generation by adding entries to existing pools, so their
actual odds depend on the vanilla pool as well as the configured roll chance.

A couple of values are still set in code:

| Number | Where |
|--------|-------|
| A Focus's cost | the `cost` field in its `data/.../focus/<name>.json` |
| Number of Focus slots (6) | `SIZE` in `attunement/AttunedInv.java` — the inventory UI assumes 6, so changing this is involved |

The `/attuned capacity` command reads or sets a player's capacity for testing.

## Items and recipes

| Item | Recipe / source | Purpose |
|------|-----------------|---------|
| Attunement Altar | Amethyst block, diamond, polished deepslate | Opens the shard-binding GUI and binds shards into capacity. |
| Attunement Shard | Diamond surrounded by amethyst shards, or four Attunement Shard Fragments | Raises capacity when bound at an Altar. |
| Attunement Shard Fragment | Vanilla loot injected alongside Foci | Four craft into one Attunement Shard; using one tells the player their current fragment count. |
| Attunement Journal | Book + amethyst shard | Opens as a readable book with the core Attuned rules. |
| Focus Reliquary | Leather pouch around an amethyst shard | A stack-bound bag for spare Foci. Move Foci between the reliquary grid, the equipped Focus column, and your inventory by dragging, by click-to-grab then click-to-drop, or by shift-click. Type a name and Save the current loadout as a **build**, then click a build to select it and Apply or Delete. Hovering a build name previews its six Foci as item icons, greying any you cannot currently source for an Apply. Builds 1-3 can also be applied anywhere with the unbound **Apply Build** keybinds (Options > Controls); the apply sources Foci from a reliquary in your inventory. |

## Loot and Lootr compatibility

Attuned injects Foci and shard fragments into reviewed vanilla loot tables in
`content/AttunedLoot.java`: structure chests, fishing treasure, archaeology, and
trial rewards. It also adds rare data-driven wandering trader offers for the
Attunement Journal and shard fragments, but not full Foci or full shards. That
keeps the rewards vanilla-friendly and also works with Lootr-style per-player
chests because those mods resolve the same chest loot tables for each player
instead of needing custom Attuned chest blocks.

Every registered item backed by FocusDefinition data is added to every Attuned
Focus loot pool with a positive weight. Theme weights bias the roll; they never
remove a Focus from the pool. That means adding a registered Focus item and its
definition data keeps it findable anywhere Attuned Focus loot can roll,
including Lootr per-player containers.

The Unseen Foci are weighted a little higher in stealth-flavoured structures and
ruins such as mineshafts, strongholds, outposts, ancient cities, end cities, and
archaeology sites, but they remain possible anywhere Attuned Focus loot can roll.

Lootr stays an optional/suggested dependency in `fabric.mod.json`. Attuned only
needs Lootr's native behavior for vanilla loot-table containers; a direct Lootr
API dependency would only be needed if Attuned added custom loot containers.

## Commands

Player-facing commands:

| Command | Purpose |
|---------|---------|
| `/attuned journal` | Prints the same compact guide text as the Attunement Journal. |
| `/attuned focus up <slot>` | Swaps a Focus one slot higher in the priority order. |
| `/attuned focus down <slot>` | Swaps a Focus one slot lower in the priority order. |
| `/attuned focus move <from> <to>` | Swaps two Focus slots directly. |

Operator commands require game-master permission:

| Command | Purpose |
|---------|---------|
| `/attuned capacity` | Prints your current attunement capacity. |
| `/attuned capacity <amount>` | Sets your current attunement capacity for testing. |
| `/attuned status` | Dumps active Foci, stance, Pact, resonance, and Apex state. |
| `/attuned validate` | Checks shipped Focus registrations, datapack definitions, and behavior ids. |

## Where everything lives

| Folder / file | Holds |
|---------------|-------|
| `content/AttunedContent.java` | Item registration, creative tabs, behavior registration |
| `content/AttunementJournalItem.java` | The readable in-game guide item |
| `content/AttunementShardFragmentItem.java` | Fragment progress hint behavior |
| `content/behavior/` | Behavior classes |
| `data/attuned/attuned/focus/` | Focus definition JSON files |
| `data/attuned/attuned/synergy/` | Confluence definition JSON files |
| `synergy/` | Confluence resolver (`SynergyResolver`), runtime (`Synergies`), and the `SynergyDefinition` registry record |
| `data/attuned/recipe/` | Altar, shard, fragment, and journal recipes |
| `data/attuned/advancement/attunement/` | Code-awarded Attuned progression advancements |
| `assets/attuned/items/` + `assets/attuned/models/item/` | Item model files |
| `assets/attuned/textures/item/` | Item textures (`.png`) |
| `assets/attuned/textures/gui/` | Custom Altar, Focus panel, and HUD UI textures |
| `assets/attuned/lang/en_us.json` | All names, lore, and effect text |
| `attunement/` | The budget, the six slots, active-vs-dormant logic |
| `combat/` | Affinity combat and the Gravebind death-save |
| `network/` | The Voidstep teleport packet |
| `client/` | Inventory screens, tooltips, the Focus panel readout |
