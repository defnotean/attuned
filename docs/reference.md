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
- Pick an affinity for combat-flavoured Foci; leave it off for utility Foci.

## Factions

Factions are optional labels for Foci that share a theme. They show on tooltips
and can be used by loot weighting, but they are not affinities and never change
the Holy/Fury/Bastion/Zephyr cycle.

Attuned currently ships these factions:

| Faction id | Theme | Balance role |
|------------|-------|--------------|
| `attuned:unseen` | Stealth, stillness, smoke, ambush openings | Rewards setup and positioning without replacing armor, speed, or raw damage builds. |
| `attuned:seafarers` | Fishing, shore travel, return points | Peaceful Luck and water utility without PvP pressure. |
| `attuned:offshore` | Salvage, storms, wreck maps, deep-water risk | Utility with danger: temporary tools, water pressure, and anti-drowned/guardian space without becoming a permanent weapon line. |
| `attuned:radiant` | Holy vows, light, witness, judgment | Reveals and protects in short windows rather than adding broad damage. |
| `attuned:reliquary` | Names, relics, rites, thresholds | Utility-side Holy tools that reward preparation and place. |
| `attuned:verdant_choir` | Roots, bloom, moss, patient growth | Broad natural utility with small travel and survival numbers. |
| `attuned:ashen_forge` | Heat, craft, rivets, tempering | Craft-flavoured Fury/Bastion tools with restrained stat bonuses. |
| `attuned:revenant` | Unfinished endings, debts, rites, grave-cold reprisals | Utility and controlled combat pressure through short revenge windows, cleansing, slowness, and one active movement ability. |

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
| `attuned:tremor`     | `TremorBehavior`         | Mining stone may hint when ore is nearby. |
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
| Focus Reliquary | Leather pouch around an amethyst shard | A stack-bound bag for spare Foci. Move Foci between the reliquary grid, the equipped Focus column, and your inventory by dragging, by click-to-grab then click-to-drop, or by shift-click. Type a name and Save the current loadout as a **build**, then click a build to select it and Apply or Delete. |

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
