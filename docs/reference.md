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
| `affinity`  | text    | no       | One of the eight wheel values: `fury`, `bastion`, `zephyr`, `holy`, `tide`, `forge`, `verdant`, or `umbral`. Omit for a neutral Focus. |
| `faction`   | text    | no       | Optional story/gameplay family, e.g. `attuned:unseen`. It does not change affinity math. |
| `modifiers` | list    | no ([])  | Stat changes — see [Attribute modifiers](#attribute-modifiers). |
| `behavior`  | text    | no       | A registered behavior id, e.g. `attuned:stormcall`. |

A Focus with no `affinity`, no `modifiers`, and no `behavior` is legal — it just
does nothing. Most Foci use either `modifiers` or `behavior` (or both).

## Affinities

Eight affinities form the counter wheel (the Wheel of Refusals). Each affinity
beats exactly two others and is countered by the two reciprocal others:

| Affinity | Beats | Weak to |
|----------|-------|---------|
| Fury | Bastion, Verdant | Holy, Tide |
| Bastion | Zephyr, Umbral | Fury, Forge |
| Zephyr | Holy, Tide | Bastion, Umbral |
| Holy | Fury, Umbral | Zephyr, Verdant |
| Tide | Fury, Forge | Zephyr, Verdant |
| Forge | Bastion, Verdant | Tide, Umbral |
| Verdant | Tide, Holy | Fury, Forge |
| Umbral | Zephyr, Forge | Bastion, Holy |

The original four-cycle (Holy beats Fury beats Bastion beats Zephyr beats Holy)
survives as a subset of this expanded matrix. The in-game Attunement Journal's
matchup pages carry the same table for players.

- A Focus with an `affinity` pulls its wearer toward that lane. When every
  active affinity-bearing Focus shares one affinity, the wearer is **committed**
  to it — counter-combat and the Apex capstone apply.
- Running active Foci of **two or more** affinities is **Discord**: every Focus
  still works, but the wearer is a glass cannon — dealing and taking extra
  damage. A wide mixed-affinity Discord build can reach **Maelstrom**, a special
  Apex path that keeps the risky mixed-affinity identity.
- A Focus with **no** `affinity` is *neutral* — it never triggers Discord and
  fits any committed lane. Four or more active neutral Foci can reach
  **Stillpoint**, the neutral Apex path.

**Apex capstones.** Each committed affinity arms its own Apex capstone at near-full
capacity and combat Resonance:

| Affinity | Capstone | Effect |
|----------|----------|--------|
| Fury | Execute | Strikes finish off low-health foes. |
| Bastion | Unyielding | No single blow can land hard, and knockback is ignored. |
| Zephyr | Untouchable | A chance to dodge attacks outright while sprinting. |
| Holy | Judgment | Marks wounded Fury-aligned foes for a decisive strike. |
| Tide | Riptide | Apex strikes drag foes into the current (Slowness). |
| Forge | Crucible | Apex strikes sear foes with forge-heat (fire). |
| Verdant | Bloomward | Apex strikes return life to you. |
| Umbral | Gloaming | Apex strikes sap a foe's strength (Weakness). |

The four promoted capstones (Riptide, Crucible, Bloomward, Gloaming) are matchup-scaled
on-hit procs on a landed apex melee: empowered against the affinity they beat, normal
otherwise, and suppressed against an affinity that beats them.

**Apex identity abilities.** The two affinity-less Apex paths each gain an active
ability fired from the **Focus Ability** keybind — but only while armed (at Apex
resonance) and while no awake ability Focus is equipped to claim the key first.
The eight affinity capstones own no such ability; their key press still reports
that no ability Focus is equipped.

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

## Pacts

A **Pact** is a named set bonus that wakes when **three or more active Foci share
one affinity** (no extra attunement cost). Each of the eight affinities owns a
single-affinity pact, and a player is in at most one pact at a time:

| Affinity | Pact |
|----------|------|
| Fury | Pyresworn |
| Bastion | Stoneheart |
| Zephyr | Windrunner |
| Holy | Radiant Covenant |
| Tide | Tidesworn |
| Forge | Forgebound |
| Verdant | Wildroot |
| Umbral | Nightsworn |

The cross-affinity **Untethered** pact wakes instead when the player carries a
diverse spread (four or more distinct active affinities, none stacked three deep);
it hits affinity-bearing foes harder. The Attunement Journal's Pacts chapter
documents all nine.

## Factions

Factions are optional labels for Foci that share a theme. They show on tooltips
and can be used by loot weighting, but they are not affinities and never change
the affinity counter wheel.

When **three or more ACTIVE Foci share a faction**, that faction grants a small,
free passive **set bonus** — no extra attunement cost. The bonus is a modest
vanilla effect (icon-hidden, kept refreshed) and most are conditional on context.
Drop below three active Foci of the faction and the bonus simply stops.

Attuned currently ships these factions:

| Faction id | Theme | Balance role | Set bonus (3 active Foci) |
|------------|-------|--------------|----------------------------|
| `attuned:unseen` | Stealth, stillness, smoke, ambush openings | Rewards setup and positioning without replacing armor, speed, or raw damage builds. | Speed I while sneaking. |
| `attuned:seafarers` | Fishing, shore travel, return points | Peaceful Luck and water utility without PvP pressure. | Luck I while in or near water. |
| `attuned:offshore` | Salvage, storms, wreck maps, deep-water risk | Utility with danger: temporary tools, water pressure, and anti-drowned/guardian space without becoming a permanent weapon line. | Water Breathing while submerged. |
| `attuned:tideborn` | Pull, rain, undertow, and pearl-like defense | Combat-facing Tide tools; their counter identity lives in their `affinity` on the eightfold wheel. | Dolphin's Grace while in or near water. |
| `attuned:forgebound` | Brands, anvils, welding sparks, and tempered pressure | Forge-affinity tools that avoid changing the older Ashen Forge set-bonus roster. | Haste I near a lit forge, magma, or lava. |
| `attuned:wildroot` | Thorn, seed, bramble, and patient natural counterplay | Verdant-affinity tools that avoid changing the older Verdant Choir set-bonus roster. | Slow Falling I while standing on grass. |
| `attuned:radiant` | Holy vows, light, witness, judgment | Reveals and protects in short windows rather than adding broad damage. | A brief Regeneration I in bright light (light level >= 12), on a 60s cooldown. |
| `attuned:reliquary` | Names, relics, rites, thresholds | Utility-side Holy tools that reward preparation and place. | Luck I. |
| `attuned:verdant_choir` | Roots, bloom, moss, patient growth | Broad natural utility with small travel and survival numbers. | A little hunger restored while standing on grass, on a 60s cooldown. |
| `attuned:ashen_forge` | Heat, craft, rivets, tempering | Craft-flavoured Fury/Bastion tools with restrained stat bonuses. | Fire Resistance near a lit forge, magma, or lava. |
| `attuned:revenant` | Unfinished endings, debts, rites, grave-cold reprisals | Utility and controlled combat pressure through short revenge windows, cleansing, slowness, and one active movement ability. | Nearby undead are slowed (Slowness I). |
| `attuned:umbral` | Shadow, darkness, stealth that rewards committing to the dark | Stronger the deeper the dark — movement, armor, and damage that only wake in low light, so it never out-scales a lit build. | Speed I in the dark (light level <= 7). |
| `attuned:deep_lanterns` | Cave light, rescue beacons, and route markers | Group-safe exploration support with no item sharing or global tracking. | Night Vision while in or near water or placed lanterns. |

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
| `minecraft:sneaking_speed`         | Sneaking speed | this is the crouch-speed multiplier (`0.3` vanilla base); use `add_value` for visible multiplier points |
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

## Custom Focus visuals

Some active Foci also have small custom visual motifs on the existing aura cadence.
These are server-sent vanilla particles, not extra gameplay state, and they reuse
already-active Focus slots so they do not add another definition lookup pass.

The first shipped custom-visual batch is:

| Focus | Visual motif |
|-------|--------------|
| Softstep | violet shadow footprints and low ground wisps |
| Aegis | warm gold protective ring |
| Tide | cyan bubbles and splash motes |
| Cinder | compact ember arc and lava sparks |

These shipped motifs are documented by their in-game effect.

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
| `attuned:wellspring_swim` | `focus_behavior/wellspring_swim.json` | 1.19.4 swim fallback: Dolphin's Grace while underwater. |
| `attuned:current_runner_swim` | `focus_behavior/current_runner_swim.json` | 1.19.4 swim fallback: Dolphin's Grace while underwater. |
| `attuned:ebbstride` | `EbbstrideBehavior` | 1.19.4 fall-damage fallback: scales fall damage to match the newer fall-damage-multiplier attribute. |
| `attuned:galespur`   | `GalespurBehavior`       | Doubles the speed of living mounts while riding. |
| `attuned:rainstep`   | `RainstepBehavior`       | Movement speed in rain, water, or waterlogged blocks. |
| `attuned:updraft`    | `UpdraftBehavior`        | Hold jump while gliding with an elytra to boost forward; hold sprint/control to brake. |
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
| `attuned:gloomstride` | `GloomstrideBehavior`   | Movement speed while standing in low light. |
| `attuned:duskward`   | `DuskwardBehavior`       | Extra armor while standing in low light. |
| `attuned:shadowmeld` | `ShadowmeldBehavior`     | Resistance while crouching in low light. |
| `attuned:dreadfang`  | `DreadfangBehavior`      | A fully charged direct-melee hit on a hostile or PvP foe applies Darkness; the proc fires from the combat after-damage hook. |
| `attuned:eclipse`    | `EclipseBehavior`        | Strength while standing in total darkness (light level 4 or lower). |
| `attuned:undertow` | `focus_behavior/undertow.json` | Charged melee hits slow hostile targets. |
| `attuned:riptide_heart` | `focus_behavior/riptide_heart.json` | Keeps Speed refreshed as the Tide starter mobility Focus. |
| `attuned:pearlguard` | `focus_behavior/pearlguard.json` | Grants Resistance while wet, underwater, or in rain. |
| `attuned:slagbrand` | `focus_behavior/slagbrand.json` | Charged melee hits weaken hostile targets. |
| `attuned:anvilheart` | `focus_behavior/anvilheart.json` | Pulses short Resistance windows for a heavy Forge stance. |
| `attuned:sparkweld` | `focus_behavior/sparkweld.json` | Keeps Haste refreshed as a light Forge work-buff. |
| `attuned:thornwake` | `focus_behavior/thornwake.json` | Charged melee hits poison hostile targets. |
| `attuned:seedcall` | `focus_behavior/seedcall.json` | Periodically refreshes a gentle Regeneration pulse. |
| `attuned:bramblegate` | `focus_behavior/bramblegate.json` | Charged melee hits slow hostile targets for longer. |
| `attuned:nullveil` | `focus_behavior/nullveil.json` | Grants Invisibility while standing in low light. |
| `attuned:cinderthief` | `focus_behavior/cinderthief.json` | Charged melee hits steal a short Fire Resistance window. |
| `attuned:snaremoon` | `focus_behavior/snaremoon.json` | Charged melee hits heavily slow hostile targets. |
| `attuned:cavewick` | `focus_behavior/cavewick.json` | Placed lanterns and soul lanterns keep gentle Night Vision refreshed. |
| `attuned:glowline` | `focus_behavior/glowline.json` | Points toward recent same-dimension Circle pings. |
| `attuned:rescueflame` | `focus_behavior/rescueflame.json` | Assists nearby drowning Circle members with Water Breathing. |
| `attuned:depthglass` | `focus_behavior/depthglass.json` | Provides same-dimension navigation hints from a held lodestone compass. |
| `attuned:wildward`   | `WildwardBehavior`       | Confluence (Mossheart + Rootstep): refreshes Resistance I while standing on natural ground. |
| `attuned:sunwarden`  | `SunwardenBehavior`      | Confluence (Votive + Bellwether): refreshes Regeneration I while standing in bright light. |
| `attuned:forgewarded` | `ForgewardedBehavior`   | Confluence (Kilnward + Emberward): refreshes Fire Resistance while near a lit forge, magma, or lava. |

### When a power is not a behavior

A few effects are too special for the tick-based behavior hooks and live in
their own classes — read these if your idea resembles them:

- **Death effects** — `combat/GravebindSave.java` hooks the death event.
- **Affinity combat and hit procs** — `combat/AttunedCombat.java` handles
  damage between affinities plus Cinder, Thornward, Leech, and Dreadfang's
  charged-hit Darkness (its `attuned:dreadfang` behavior is registered for the
  framework but its proc fires from the after-damage hook).
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
build feel cohesive, never out-scale a focused affinity build. Every first-class
affinity has at least one pure-affinity Confluence so each lane has both an Apex
capstone and an earlier buildcraft payoff.

The ones that ship with Attuned:

| Confluence (`id`) | Members | Effect |
|-------------------|---------|--------|
| Immovable (`immovable`) | Anchor + Rivet | +0.1 knockback resistance |
| Bulwark of Light (`bulwark_of_light`) | Votive + Oathguard | +1 armor |
| Hunter's Patience (`hunters_patience`) | Lantern + Veil | +4% movement speed |
| Tempest (`tempest`) | Rainstep + Stormcall | +1 attack damage |
| Razor Tempo (`razor_tempo`) | Edge + Frenzy | +0.25 attack speed |
| Iron Bastion (`iron_bastion`) | Iron + Bulwark | +1 armor toughness |
| Tidal Engine (`tidal_engine`) | Current Runner + Riptide Heart | +0.25 water movement efficiency |
| Sparkbrand (`sparkbrand`) | Sparkweld + Slagbrand | +0.5 attack damage |
| Thornbloom (`thornbloom`) | Seedcall + Thornwake | +1 armor |
| Wildward (`wildward`) | Mossheart + Rootstep | Resistance I while on natural ground |
| Sunwarden (`sunwarden`) | Votive + Bellwether | Regeneration I while standing in bright light |
| Forgewarded (`forgewarded`) | Kilnward + Emberward | Fire Resistance while near a lit forge, magma, or lava |
| Total Eclipse (`total_eclipse`) | Eclipse + Gloomstride + Duskward | +4 max health and +1 attack damage |

## Resonant Combos

A **Resonant Combo** is a small named payoff for a specific pair of active Foci.
Both Foci must be awake; dormant Foci do not count. Combos cost no extra
attunement and stay smaller than Confluences, Pacts, or Apex.

| Combo | Active Foci | Effect |
|-------|-------------|--------|
| Silent Opening | Softstep + Needle | A confirmed Needle opener applies Weakness I for 80 ticks. |

## Tempering Foci

The **Altar of Reweaving** can also *temper* a Focus. Place two copies of the
**same** untempered Focus in the first two Focus slots, leave the third empty,
add an Attunement Shard Fragment as the catalyst, and reweave: the two copies
fuse into one **Tempered** copy and the catalyst is consumed.

A Tempered Focus is a stronger, costlier variant of the same Focus:

- Every declarative attribute modifier is amplified by **+25%** (`× 1.25`).
- It costs **+1 attunement** on top of the base cost, so dormancy and the
  capacity readout account for the surcharge automatically.
- Its name renders in gold and its tooltip carries a **Tempered** line.

Tempering is marked by the `attuned:tempered` data component. Only untempered
copies can be tempered, and a Tempered Focus cannot be tempered again. Reweaving
three different Foci (the standard roll) is unaffected.

## Affinity Loom (Reweaving II)

The **Altar of Reweaving** has a third mode — **Affinity Loom** — that rerolls
one Focus into a *different* Focus while keeping the same affinity lane (or
neutral → neutral).

Layout:

- **Focus slot 0:** the Focus to reroll.
- **Focus slots 1–2:** empty.
- **Catalyst:** Attunement **Shards** (not Shard Fragments).

Output is rolled via `pickSameAffinity`: same `affinity` as the input, or both
neutral when the input has no affinity.

**Escalating cost per menu session:** the first loom roll costs **1** shard, the
second costs **2**, and the third and later rolls cost **3** (cap). The counter
resets when the altar menu closes and you open it again.

**Mode priority** when you commit a reweave (checked in order):

1. **Temper** — two identical untempered Foci + Shard Fragment.
2. **Affinity Loom** — one Focus + enough Shards for the current session cost.
3. **Classic reweave** — three different Foci + Shard Fragment.

The reweaving screen shows an Affinity Loom hint with the current shard cost when
the layout matches.

## Pact Trials and Tier 4

Each **Pact** carries a long-term **trial** that accrues progress only while
that pact is **awake** (three or more active Foci sharing its affinity, or the
Untethered spread rule). Completing a trial is **permanent** for that player:
re-awakening the same pact later unlocks a small **Tier 4** passive bonus for as
long as the pact is active again. The Attunement Journal **Pacts** chapter lists
every trial, its goal, and your current progress.

| Pact | Affinity | Trial goal | What accrues |
|------|----------|------------|--------------|
| Pyresworn | Fury | 40 | Ignite hostiles while Pyresworn is awake. |
| Stoneheart | Bastion | 400 | Absorb foe damage **while blocking** while Stoneheart is awake. |
| Windrunner | Zephyr | 6,400 | Sprint blocks while Windrunner is awake **and** (near hostiles within 16 blocks **or** at Apex resonance). |
| Radiant Covenant | Holy | 25 | Land charged reveals while Radiant Covenant is awake. |
| Tidesworn | Tide | 40 | Slow foes while Tidesworn is awake. |
| Forgebound | Forge | 25 | Sear (ignite) foes while Forgebound is awake **and** near hostiles within 16 blocks. |
| Wildroot | Verdant | 36,000 | Time while Wildroot is awake: full rate with Regeneration; half rate near hostiles; idle safe zones accrue nothing (~30 minutes at full rate). |
| Nightsworn | Umbral | 150 | Absorb foe damage in the dark while Nightsworn is awake. |
| Untethered | Discord spread | 20 | Slay affinity-bearing hostiles at Apex resonance **and** near hostiles within 16 blocks while Untethered is awake. |

**Tier 4 bonuses** (active only while the matching pact is awake *and* its trial
is complete):

| Pact | Tier 4 bonus |
|------|--------------|
| Pyresworn | Ignite duration +1 s. |
| Stoneheart | +1 armor toughness. |
| Windrunner | Speed II while sprinting. |
| Radiant Covenant | +15% damage vs undead. |
| Tidesworn | Slow duration +1 s (+20 ticks). |
| Forgebound | Sear/ignite duration +1 s. |
| Wildroot | Regeneration II. |
| Nightsworn | 15% dark-damage reduction. |
| Untethered | Resonance decays 10% slower. |

Finishing a trial awards a hidden advancement, plays a completion fanfare, and
can trigger a one-time onboarding hint about Tier 4 permanence.

**Trial pressure radius.** Several trials only accrue when you are within **16
blocks** of a hostile mob or PvP-valid opponent (`nearTrialPressure`). Windrunner
also accepts Apex resonance in place of nearby pressure so you can finish long
sprint trials in open terrain once you are fully armed.

**Circle shared trial credit.** Nearby online Circle members with recent
contribution windows can share discrete trial events inside the configured
radius (same dimension by default). Members with the same Pact awake receive the
source trial's normal amount. The first support assist is narrower: when
Stoneheart earns blocking/absorbed-damage trial credit, a contributing Witness
whose Radiant Covenant Pact is awake can receive a 1-point Radiant Covenant
assist tick instead of full Stoneheart damage credit.

## Pact Tacticals

When your awake Pact is at **Apex resonance** and no active ability Focus claims
the **Focus Ability** key, that key fires the Pact's tactical instead of a Focus
ability. Tacticals inherit the Pact identity: Pyresworn ignites, Stoneheart
resists, Windrunner accelerates, Radiant Covenant reveals, Tidesworn slows,
Forgebound sears, Wildroot regenerates, Nightsworn blinds in darkness, and
Untethered knocks threats away.

**Tactical overcharge.** **Crouch** while firing to spend **0.25 resonance** for
an amplified tactical: larger radius, longer durations, and higher effect
potency. Overcharge drops most builds below Apex, so it is a deliberate burst
trade. The action bar shows *Overcharged pact tactical — resonance spent.*

Combat feedback (particles and sounds) also fires on resonance gain and drain,
kill streaks, surge charge, ability casts, Apex capstone procs (including
**Execute** and **Judgment**), and pact tactical use. Kill-streak combat momentum
can shave tactical cooldown while pressure stays active.

## Resonant Surges

During a thunderstorm, Attuned can start one **Resonant Surge** site near an
online player in the thundering dimension. The site broadcasts with beacon-like
feedback, marks approximate coordinates for nearby players, and lures monsters
toward the storm so the event feels like a risky combat beacon rather than a
quiet resource node.

Players standing inside the surge radius gain Resonance faster while the event is
live; Discord earns half Resonance from the surge field. Kills inside the surge
have a chance to return one or two **Attunement Shard Fragments**, rewarding
players who answer the storm and fight inside the pressure zone.

## Updraft Focus

The **Updraft Focus** is an elytra utility Focus. While the Focus is awake and the
player has a functional elytra equipped, holding jump during flight applies
smooth forward thrust based on look direction. Holding sprint/control sends a
brake input that eases the current velocity down instead of snapping it.

Updraft can start gliding when the player is eligible, clears fall distance while
its flight controls are active, and supplies restrained particles, sounds, and
action-bar feedback for boost, brake, and exhaustion states. Sustained PvP
pressure eventually triggers **PvP exhaustion**: Updraft falters briefly, brakes
hard, and applies short Weakness and Slowness so it cannot become endless combat
disengage.

## Build sharing

The **Focus Reliquary** and **Grand Focus Reliquary** screens expose **Share**
and **Import** buttons beside the saved-build controls.

- **Share** copies the selected saved build to the system clipboard.
- **Import** reads the clipboard, validates it, and saves it as a new preset on
  the server (same path as typing a name and saving manually).

**Clipboard format:** `attuned:v1:` followed by URL-safe Base64 of a small JSON
object:

```json
{ "name": "My build", "slots": ["attuned:iron_focus", "", …] }
```

The `slots` array holds up to six Focus **item** ids (empty strings for unused
slots), in equipped priority order.

Optional setup metadata can travel with the build code:

| Field | Type | Meaning |
|-------|------|---------|
| `role` | text | Short archetype label, such as "Support" or "Tank". |
| `note` | text | Short plain-text setup note. |
| `party_size` | integer 0-8 | Preferred party size; `0` means no preference/solo-neutral. |
| `warnings` | text list | Advisory setup warnings to show before use. |
| `requires` | Focus id list | Extra Focus ids that should exist for the build to make sense. |
| `mc` | text | Source Minecraft version. |
| `attuned` | text | Source Attuned version. |

Known metadata fields must be plain strings, string lists, or the integer
`party_size`; chat component-like objects are rejected. Unknown optional fields
are ignored. The server validates both `slots` and advisory `requires` ids
against the server Focus registry before saving. `requires` only produces
readable missing-content warnings; it does not grant or create items.

Freshly saved Reliquary builds infer lightweight setup metadata server-side:
role labels come from active affinity/faction dominance, while advisory warnings
flag uncommitted mixed lanes, dormant over-capacity or duplicate-unique Foci,
and builds with no active Focus Ability. This metadata is informational only and
does not change Focus activation or item movement. When the saver is in a
Circle, the server can also append public-role party-fit suggestions such as
missing reveal, missing Anchor/Vanguard front line, or duplicate Shade pressure;
these use only coarse role labels and honor `party_setup_suggestions_enabled`.

**Validation:** the client decodes through `BuildShareCodec` (prefix, Base64,
JSON shape, name length ≤ 32, slot count ≤ 6). The server receives an
`ImportPresetPayload`, re-normalizes the full `FocusPreset`, and
only accepts the packet while a live reliquary menu is open with the reliquary
still held in the menu hand — spoofed or out-of-menu imports are ignored.

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

Each entry has a `type`. The shipped palette is **passive only** (no Focus Ability
key — active-ability authoring is a later version) and ships nine types:

| `type`                            | Gates on        | Effect |
|-----------------------------------|-----------------|--------|
| `attuned:conditional_mob_effect`  | a [condition](#conditions) | Keep a mob effect refreshed while the condition holds. |
| `attuned:on_hit_effect`           | a charged, hostile-only melee hit | Apply a mob effect to the victim (or self) on the hit. |
| `attuned:periodic_effect`         | a fixed tick cadence | Keep a flat mob effect refreshed — an unconditional buff. |
| `attuned:attribute_while`         | a [condition](#conditions) | Apply an attribute modifier only while the condition holds. |
| `attuned:block_context_effect`    | nearby tagged blocks | Keep a mob effect refreshed while near matching blocks. |
| `attuned:use_item_window`         | server item-use event | Grant a short effect/modifier window after using an item. |
| `attuned:party_assist`            | Circle contribution window | Assist a nearby Circle member with a small effect on cooldown. |
| `attuned:marked_target`           | charged melee hits | Mark a target, then consume the mark for an effect. |
| `attuned:navigation_hint`         | stored target data | Give restrained direction/distance feedback toward a stored point or party target. |

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

### `attuned:on_hit_effect`

Applies a mob effect on a **fully charged, direct-melee** hit. It reuses the same
charge and target guards as the code combat Foci: the swing must be charged past
`charge_threshold`, and (by default) the victim must be a hostile mob or a valid
PvP opponent. Passive only.

| Field             | Type          | Required | Meaning |
|-------------------|---------------|----------|---------|
| `type`            | text          | yes      | `attuned:on_hit_effect` |
| `effect`          | mob effect id | yes      | e.g. `minecraft:weakness` |
| `amplifier`       | int 0–255     | no (0)   | Effect level minus one. |
| `duration_ticks`  | int ≥ 1       | no (60)  | How long the applied effect lasts. |
| `charge_threshold`| float 0–1     | no (0.9) | Swing charge the hit must reach (1.0 = fully charged). |
| `target_self`     | bool          | no (false)| Apply to the attacker instead of the victim (e.g. a hit-and-heal buff). |
| `hostile_only`    | bool          | no (true) | Only proc against hostile mobs / valid PvP opponents. |

The proc runs from Attuned's existing post-damage combat hook — no mixin — so it
never fires on a swing the code combat path would reject (reflected hits, projectiles,
explosions, uncharged taps).

### `attuned:marked_target`

Primes a short-lived server-side mark on a valid charged melee hit, then consumes
that mark on another valid charged melee hit against the same target. It reuses the
same charged-hit and hostile/PvP target guards as `on_hit_effect`. Marks are keyed
by behavior id, attacker, and target; cooldowns are keyed by behavior id and
attacker.

| Field                 | Type          | Required | Meaning |
|-----------------------|---------------|----------|---------|
| `type`                | text          | yes      | `attuned:marked_target` |
| `prime_trigger`       | trigger id    | yes      | Currently `charged_melee_hit`. |
| `consume_trigger`     | trigger id    | yes      | Currently `charged_melee_hit`. |
| `mark_duration_ticks` | int 1-1000000 | yes      | How long the mark can be consumed. |
| `cooldown_ticks`      | int 1-1000000 | yes      | Per-attacker cooldown after a consume. |
| `effect_on_consume`   | object        | yes      | Effect object applied when the mark is consumed. |

`effect_on_consume` fields:

| Field            | Type          | Required | Meaning |
|------------------|---------------|----------|---------|
| `effect`         | mob effect id | yes      | e.g. `minecraft:weakness` |
| `duration_ticks` | int 1-1000000 | yes      | How long the consumed effect lasts. |
| `amplifier`      | int 0-255     | no (0)   | Effect level minus one. |
| `target_self`    | bool          | no (false) | Apply to the attacker instead of the marked target. |

### `attuned:navigation_hint`

Gives restrained action-bar feedback toward a stored point or a party target.
It never locates structures live and never scans chunks. Targets come from
server-accepted Circle pings, the current online Circle leader, or a held vanilla
lodestone compass target.

| Field                    | Type      | Required | Meaning |
|--------------------------|-----------|----------|---------|
| `type`                   | text      | yes      | `attuned:navigation_hint` |
| `target_kind`            | text      | yes      | `circle_ping`, `circle_leader`, or `held_lodestone_compass`. |
| `max_age_ticks`          | int 1-1000000 | yes  | How old a stored target may be before feedback stops. |
| `same_dimension_only`    | bool      | yes      | If true, suppress feedback for cross-dimension targets. |
| `feedback_cadence_ticks` | int 20-1000000 | yes | How often to show the ambient hint. |

`circle_ping` uses only pings that already passed Circle membership, range,
loaded-chunk, and rate-limit checks. `held_lodestone_compass` reads the compass's
existing `LODESTONE_TRACKER` data and keeps that explicit point briefly cached
for the configured age window.

### `attuned:periodic_effect`

Keeps a flat mob effect refreshed on a fixed cadence — an unconditional buff (a
gentle Regeneration, a steady Night Vision). Like `conditional_mob_effect` but with
no gating condition. Passive only.

| Field            | Type          | Required | Meaning |
|------------------|---------------|----------|---------|
| `type`           | text          | yes      | `attuned:periodic_effect` |
| `effect`         | mob effect id | yes      | e.g. `minecraft:regeneration` |
| `amplifier`      | int 0–255     | no (0)   | Effect level minus one. |
| `duration_ticks` | int ≥ 1       | no (80)  | How long each application lasts. |
| `refresh_ticks`  | int ≥ 1       | no (40)  | Re-apply every this many ticks. |

The effect is applied ambient, hidden, and icon-less. Keep `duration_ticks`
comfortably above `refresh_ticks` so the buff never visibly lapses between refreshes.

### `attuned:use_item_window`

Grants a short mob-effect and/or transient attribute window after the player uses a
matching item. The trigger is the server item-use event, so this never scans the
player's inventory every tick. Cooldowns are keyed by player and behavior id.

| Field            | Type             | Required | Meaning |
|------------------|------------------|----------|---------|
| `type`           | text             | yes      | `attuned:use_item_window` |
| `item`           | item id          | one of `item`/`item_tag` | Exact item that triggers the window. |
| `item_tag`       | item tag id      | one of `item`/`item_tag` | Item tag that triggers the window. |
| `duration_ticks` | int 1-1000000    | yes      | How long the granted effect/modifier lasts. |
| `cooldown_ticks` | int 1-1000000    | yes      | Per-player, per-behavior cooldown after a valid use. |
| `effect`         | mob effect id    | no       | Optional effect to grant. |
| `amplifier`      | int 0-255        | no (0)   | Effect level minus one. |
| `modifier`       | modifier object  | no       | Optional transient attribute modifier. |
| `condition`      | condition object | no       | Optional extra gate checked at use time. |

Exactly one of `item` or `item_tag` is required. At least one of `effect` or
`modifier` is required. Effects use the same ambient, hidden, icon-less flags as
the other passive palette effects.

### `attuned:party_assist`

Assists one nearby Circle member with a small beneficial effect. The wearer and
target must both be online, alive, non-spectating, same-dimension Circle members
inside the configured contribution window. The behavior uses its own `radius`
instead of the global shared-credit radius, respects `party_effects_enabled`, and
starts one Circle-level cooldown only after an assist lands.

| Field           | Type          | Required | Meaning |
|-----------------|---------------|----------|---------|
| `type`          | text          | yes      | `attuned:party_assist` |
| `trigger`       | trigger id    | yes      | Currently `periodic_scan` (a once-per-second passive scan). |
| `radius`        | int 1-64      | yes      | Same-dimension block radius around the wearer. |
| `cooldown_ticks`| int 1-1000000 | yes      | Per-Circle, per-behavior cooldown after an assist lands. |
| `target_filter` | filter id     | yes      | `nearby_members`, `wounded_members`, or `drowning_members`. |
| `effect`        | object        | yes      | Beneficial effect object applied to the assisted member. |
| `message_key`   | lang key      | yes      | Translation key shown to the assisted member. |

`effect` fields:

| Field            | Type          | Required | Meaning |
|------------------|---------------|----------|---------|
| `effect`         | mob effect id | yes      | e.g. `minecraft:water_breathing` |
| `duration_ticks` | int 1-1000000 | yes      | How long the assist effect lasts. |
| `amplifier`      | int 0-255     | no (0)   | Effect level minus one. |

`party_assist` is intentionally party-only: it excludes the wearer, refuses valid
PvP opponent targets in either direction, and never widens to cross-dimension
credit even if `party_cross_dimension_credit_enabled` is true. Need-based assists
(`wounded_members` and `drowning_members`) refresh the helper's support
contribution window after the effect lands; generic `nearby_members` pulses do
not refresh contribution credit by themselves.

### `attuned:block_context_effect`

Keeps a mob effect refreshed while the player is on or near blocks matching a block
tag. The scan is passive only, server-side, shallow (`radius` horizontally and one
block vertically), cached by player/block position, and throttled by `refresh_ticks`.
Use it for local context such as leaves, paths, forge blocks, campfires, or crops -
not for structure or chunk searches.

| Field            | Type           | Required | Meaning |
|------------------|----------------|----------|---------|
| `type`           | text           | yes      | `attuned:block_context_effect` |
| `block_tag`      | block tag id   | yes      | e.g. `minecraft:leaves` |
| `radius`         | int 0-5        | no (2)   | Horizontal block radius to scan around the player. |
| `requires_lit`   | bool           | no (false) | If true, matching blocks must have a lit block-state property and be lit. |
| `effect`         | mob effect id  | yes      | e.g. `minecraft:speed` |
| `amplifier`      | int 0-255      | no (0)   | Effect level minus one. |
| `duration_ticks` | int >= 1       | no (80)  | How long each application lasts. |
| `refresh_ticks`  | int 10-1000000 | no (20)  | Scan and refresh cadence; cannot be every tick. |

The effect is applied ambient, hidden, and icon-less. Keep `duration_ticks` above
`refresh_ticks` so the buff has room to bridge between scans.

### `attuned:attribute_while`

Applies a transient attribute modifier only while a [condition](#conditions) holds,
adding it when the condition becomes true and removing it when it stops (and on
unequip). The conditional twin of a Focus's declarative `modifiers`. Passive only.

| Field       | Type             | Required | Meaning |
|-------------|------------------|----------|---------|
| `type`      | text             | yes      | `attuned:attribute_while` |
| `modifier`  | modifier object  | yes      | Same shape as a `modifiers[]` entry: `attribute`, `amount`, `operation`. |
| `condition` | condition object | yes      | When the modifier should apply. |

The `modifier` object matches the Focus `modifiers` schema — `attribute` (an
`minecraft:` attribute id), `amount` (a finite number from -1024.0 to 1024.0), and `operation`
(`add_value`, `add_multiplied_base`, or `add_multiplied_total`). The modifier is
applied under a stable, palette-specific id, so it is removed cleanly the moment the
condition breaks or the Focus is unequipped.

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

#### `on_hit_effect` — Weakness on a charged blow

A Focus that saps the target with Weakness I (5 s) on a fully charged hit against a
hostile mob.

`data/mypack/attuned/focus_behavior/sapping.json`:

```json
{
  "type": "attuned:on_hit_effect",
  "effect": "minecraft:weakness",
  "amplifier": 0,
  "duration_ticks": 100,
  "charge_threshold": 0.9,
  "hostile_only": true
}
```

#### `marked_target` - Weakness after two charged hits

A Focus that marks a hostile on a charged melee hit, then applies Weakness I if the
same attacker lands another charged melee hit before the mark expires.

`data/mypack/attuned/focus_behavior/brand_mark.json`:

```json
{
  "type": "attuned:marked_target",
  "prime_trigger": "charged_melee_hit",
  "consume_trigger": "charged_melee_hit",
  "mark_duration_ticks": 100,
  "cooldown_ticks": 200,
  "effect_on_consume": {
    "effect": "minecraft:weakness",
    "amplifier": 0,
    "duration_ticks": 100
  }
}
```

#### `periodic_effect` — a gentle steady Regeneration

A Focus that drips Regeneration I, refreshed every two seconds.

`data/mypack/attuned/focus_behavior/mending_aura.json`:

```json
{
  "type": "attuned:periodic_effect",
  "effect": "minecraft:regeneration",
  "amplifier": 0,
  "duration_ticks": 80,
  "refresh_ticks": 40
}
```

#### `use_item_window` - route speed after using a map

A Focus that grants Speed I for five seconds after the player uses a filled map,
with a ten-second cooldown.

`data/mypack/attuned/focus_behavior/route_window.json`:

```json
{
  "type": "attuned:use_item_window",
  "item": "minecraft:filled_map",
  "duration_ticks": 100,
  "cooldown_ticks": 200,
  "effect": "minecraft:speed",
  "amplifier": 0
}
```

#### `party_assist` - water breathing for a drowning Circle member

A Focus that checks nearby recent Circle contributors once per second, then grants
Water Breathing I to one drowning member and starts a Circle cooldown.

`data/mypack/attuned/focus_behavior/rescue_assist.json`:

```json
{
  "type": "attuned:party_assist",
  "trigger": "periodic_scan",
  "radius": 24,
  "cooldown_ticks": 300,
  "target_filter": "drowning_members",
  "effect": {
    "effect": "minecraft:water_breathing",
    "duration_ticks": 100,
    "amplifier": 0
  },
  "message_key": "message.mypack.rescue_assist"
}
```

#### `block_context_effect` - Slow Falling under leaves

A Focus that grants Slow Falling I while standing under or near leaves.

`data/mypack/attuned/focus_behavior/canopy_step.json`:

```json
{
  "type": "attuned:block_context_effect",
  "block_tag": "minecraft:leaves",
  "radius": 2,
  "requires_lit": false,
  "effect": "minecraft:slow_falling",
  "amplifier": 0,
  "duration_ticks": 80,
  "refresh_ticks": 20
}
```

#### `attribute_while` — extra armor while crouched

A Focus that grants +2 armor only while the wearer is sneaking.

`data/mypack/attuned/focus_behavior/turtle_stance.json`:

```json
{
  "type": "attuned:attribute_while",
  "modifier": {
    "attribute": "minecraft:armor",
    "amount": 2,
    "operation": "add_value"
  },
  "condition": { "condition": "sneaking" }
}
```

`/attuned validate` checks palette-backed `behavior` ids too, so a typo in any of
these files is caught the same way a missing code behavior is.

## Custom Focus item pool (resource-pack skins)

Minecraft cannot mint new items from a datapack, so a pack that reuses an existing
item also borrows that item's name and model. To give authors a bespoke Focus
identity without a JAR, Attuned ships eight blank, skinnable Focus items:
`attuned:custom_focus_1` through `attuned:custom_focus_8`. They appear in the
Attuned utility creative tab with a neutral default name and a hue-shifted blank
medallion texture.

To use one, point a `focus/<name>.json` at it and skin the rest in a resource pack:

```json
// data/mypack/attuned/focus/frostward_focus.json
{ "item": "attuned:custom_focus_3", "cost": 4, "affinity": "bastion",
  "behavior": "mypack:frostward" }
```

```json
// assets/mypack/lang/en_us.json (resource pack)
{ "item.attuned.custom_focus_3": "Frostward Focus",
  "item.attuned.custom_focus_3.lore": "...",
  "item.attuned.custom_focus_3.lore2": "...",
  "item.attuned.custom_focus_3.effect": "..." }
```

Override `assets/attuned/models/item/custom_focus_3.json` and
`assets/attuned/textures/item/custom_focus_3.png` in the same resource pack to give
it its own art. The HUD, tooltips, and Focus Reliquary render any registered Focus,
so a skinned custom Focus works everywhere automatically. These items ship **no**
bundled `FocusDefinition` — supplying one is the author's job — so they do not count
toward the advertised shipped-Focus total.
For a full walkthrough of authoring Foci as a datapack — without editing the JAR —
see [authoring-foci.md](authoring-foci.md) and the worked
[example-pack](example-pack/).

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
| `party_max_members` | 4 | Maximum members in a Circle, from 1 to 8. |
| `party_shared_credit_radius` | 48.0 | Maximum block distance for Circle shared-credit checks. |
| `party_shared_credit_window_ticks` | 200 | How recently a Circle member must have contributed to qualify for shared credit. |
| `party_invite_ttl_ticks` | 1200 | How long a Circle invite stays valid before expiring. |
| `party_invite_cooldown_ticks` | 200 | Per-sender and per-recipient cooldown after a successful Circle invite. |
| `party_create_cooldown_ticks` | 600 | Cooldown after disbanding a Circle before the same player can create another. |
| `party_cross_dimension_credit_enabled` | false | If true, shared-credit checks may opt into cross-dimension credit; the default requires the same dimension. |
| `party_hud_enabled` | true | If true, the server sends public Circle HUD snapshots; if false, clients receive an empty party HUD snapshot. |
| `party_shared_credit_enabled` | true | If true, Circle shared-credit systems may grant eligible nearby contributors credit; if false, party credit stays disabled separately from the HUD. |
| `party_trial_assists_enabled` | true | If true, eligible Circle contributors can receive Pact Trial assist credit; if false, direct Pact Trial progress still accrues but party fan-out is disabled. |
| `party_effects_enabled` | true | If true, Circle-linked party effects may run through their server cooldown gates; if false, party effects stay disabled separately from the HUD. |
| `party_confluence_hints_enabled` | true | If true, nearby Circle members can receive shared Confluence Journal discovery hints; if false, only the active discoverer records them. |
| `party_setup_suggestions_enabled` | true | If true, saved/imported setup metadata can include advisory role/loadout warnings; if false, those warnings are stripped while non-warning metadata remains. |
| `surge_interval_ticks` | 12000 | Minimum ticks between resonant surges (one surge live at a time, server-wide). |
| `surge_duration_ticks` | 1200 | How long a resonant surge stays live, in ticks. |
| `surge_radius` | 16 | Radius, in blocks, of the resonant surge field that grants 4x resonance. |
| `advantage_multiplier` | 1.33 | Damage multiplier when the attacker's affinity counters the defender's. |
| `disadvantage_multiplier` | 0.75 | Damage multiplier when the defender's affinity counters the attacker's. |
| `discord_damage_multiplier` | 1.20 | Damage multiplier when either combatant is in Discord (softer than a full matchup advantage). |
| `resonance_hit_empowered_gain_per_damage` | 0.012 | Resonance gained per point of damage dealt on an empowered matchup. |
| `resonance_hit_neutralized_loss` | 0.10 | Resonance lost on a single hit taken from a neutralizing matchup. |

Circle shared-credit windows are intentionally transient. They clear on
disconnect, server stop, and successful Circle membership changes
(create/accept/leave/kick/disband), so players cannot carry recent contribution
credit through join/leave loops.
| `resonance_kill_empowered_gain` | 0.30 | Resonance gained when finishing a kill on an empowered matchup. |
| `resonance_decay_per_tick` | 0.00025 | Per-tick idle resonance drain (~200 seconds to zero at rest). |
| `affinity_loom_base_shard_cost` | 1 | Attunement Shards spent on the first Affinity Loom reroll in a Reweaving session. |
| `affinity_loom_max_shard_cost` | 3 | Highest shard cost a single Affinity Loom reroll can reach after escalation. |

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
| Altar of Reweaving | Loom, amethyst, polished deepslate | Reroll Foci: classic three-Focus reweave (Shard Fragment catalyst), **Tempering** (two identical Foci + fragment), or **Affinity Loom** (one Focus + escalating Attunement Shard cost for a same-affinity reroll). |
| Attunement Shard | Diamond surrounded by amethyst shards, or four Attunement Shard Fragments | Raises capacity when bound at an Altar; also the Affinity Loom catalyst at the Altar of Reweaving. |
| Attunement Shard Fragment | Vanilla loot injected alongside Foci | Four craft into one Attunement Shard; using one tells the player their current fragment count. |
| Attunement Journal | Book + amethyst shard | Opens as a readable book with the core Attuned rules. |
| Focus Reliquary | Leather pouch around an amethyst shard | A stack-bound bag for 27 spare Foci. Move Foci between the reliquary grid, the equipped Focus column, and your inventory by dragging, by click-to-grab then click-to-drop, or by shift-click. Type a name and Save the current loadout as a **build**, then click a build to select it and Apply or Delete. Hovering a build name previews its six Foci as item icons, greying any you cannot currently source for an Apply. Builds 1-3 can also be applied anywhere with the unbound **Apply Build** keybinds (Options > Controls); the apply sources Foci from a reliquary in your inventory. |
| Grand Focus Reliquary | Leather around an amethyst block with a Focus Reliquary in the center | A second-tier reliquary with a 54-slot (9x6) grid, twice the storage of the Focus Reliquary. Opens the same screen with the same equipped column, build saving/applying, previews, and quick-swap. Neither reliquary can be stored inside the other. |

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

The **Attunement Sanctum** is Attuned's own exploration site: a small data-driven
jigsaw structure (one 15x8x15 piece of polished deepslate bricks, four amethyst
pillars, and a central chiseled-deepslate altar) that generates rarely in lush
caves, forests, and dark forests. Deep Lanterns use the Attunement Sanctum as their first world clue:
lantern light, depth, and rescue-routing show the faction before a player has
to read an internal data label. Its template is generated
deterministically by `tools/generate_sanctum_template.py` and committed as
`data/attuned/structure/sanctum.nbt`; the datapack triple lives under
`data/attuned/worldgen/{structure,structure_set,template_pool}/` with the biome
list in `data/attuned/tags/worldgen/biome/has_sanctum.json`. The altar chest uses
its own `attuned:chests/sanctum` loot table (not a vanilla injection): two rolls
over light- and depth-themed Foci plus one roll of 1-3 Attunement Shard
Fragments. Find the nearest one with `/locate structure attuned:attunement_sanctum`.

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
| `/attuned validate` | Validates shipped **and** author datapacks file by file: every Focus's `item`, `behavior` (code or palette), and modifier `attribute` ids must resolve, and the `focus_behavior` palette files are walked too. A missing display-name lang key is reported as a *warning*, not a failure — a pack with only warnings still passes, with a warning count. See [authoring-foci.md](authoring-foci.md). |

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
