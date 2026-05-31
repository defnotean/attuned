# Radiant Holy Affinity Expansion Design

## Goal

Add **Holy** as a broad fourth affinity and ship a vanilla-friendly first wave of new factions and Foci around it. Holy becomes a real lane beside Fury, Bastion, and Zephyr, not a neutral upgrade or a narrow anti-undead side rule.

The main Holy faction is **The Radiant**. The Radiant are a lore and gameplay counterweight to **The Unseen**: radiance and memory against concealment and erasure. The Unseen are not villains; they are the hidden tradition that survives outside the Radiant oath-light.

## Affinity Wheel

Replace the three-affinity cycle with a balanced four-affinity wheel:

```text
Holy > Fury > Bastion > Zephyr > Holy
```

Opposite pairs are neutral:

- Holy vs Bastion is normal.
- Fury vs Zephyr is normal.

Every affinity has one favorable matchup, one unfavorable matchup, and one neutral matchup. The existing advantage and disadvantage multipliers stay unchanged unless tests show the wider wheel needs later tuning.

## Holy Identity

Holy is about vows, radiance, cleansing, memory, sanctuary, names, bells, candles, gold, amethyst, and restraint. It stays grounded in vanilla Minecraft materials and rituals: temples, villages, bells, candles, campfires, copper, amethyst, books, sunlight, undead, and defensive clarity.

Holy is not angelic flight, beam spam, free healing, or a damage-only lane. It rewards preparation and commitment, then expresses power through reveal, protection, cleansing, and precise judgment.

## Radiant And Unseen Lore

**The Radiant** keep names, vows, public rites, and sanctuary records. They believe power becomes safer when witnessed.

**The Unseen** keep secrets, masks, hidden routes, and erased histories. They believe power becomes safer when it cannot be owned.

Their conflict is philosophical:

- Radiant reveals; Unseen conceals.
- Radiant remembers names; Unseen removes names.
- Radiant sanctifies thresholds; Unseen slips through them.
- Radiant protects communities; Unseen protects fugitives, spies, and forbidden truths.

This relationship appears in item lore, journal pages, loot bias, and a few mechanics that reveal or resist concealment without making either faction strictly good or evil.

## Holy Pact

Add a Holy single-affinity Pact named **Radiant Covenant**.

Qualification:

- At least 3 active Holy Foci.
- No other active affinity Foci.

Effect:

- Fully charged direct melee hits against hostile mobs reveal the target for 80 ticks.
- Undead targets take 10% extra damage from those fully charged direct melee hits.
- The effect is readable and helpful, but lower raw damage than a Fury build.

The Pact color uses the Holy palette: warm white, gold, and faint amethyst.

## Holy Apex

Add the Holy Apex capstone **Judgment**.

Qualification:

- At least 4 active Holy Foci.
- No other active affinity Foci.
- Capacity is nearly full using the existing Apex slack rule.
- Resonance is at least half full before the capstone fires.

Effect:

- Judgment pressures Fury, the affinity Holy beats.
- Direct melee hits against Fury-affinity non-player enemies at or below 30% health add 40% damage instead of executing outright.
- Zephyr counters Holy, so Zephyr-affinity enemies and players can slip past Judgment's pressure.

Judgment feels like an oath being fulfilled, not a generic smite button.

## First Content Wave

Ship a first wave of **5 faction groups and 16 Foci**. This is large enough to make Holy playable while still bounded enough to test carefully. The wave adds 7 Holy Foci, so the new affinity can reach Pact and Apex without requiring every Holy item in the build.

### The Radiant

Faction id: `attuned:radiant`

Theme: Holy vows, light, bells, sanctuaries, remembered names.

Foci:

- **Votive Focus**: cost 2 Holy Focus. While in light level 12+ or within 3 blocks of lit candles, hostile hits can grant Absorption I for 40 ticks on a 240-tick cooldown. No regeneration.
- **Bellwether Focus**: cost 3 Holy behavior Focus. Ringing or standing within 8 blocks of a bell reveals nearby hostile mobs for 80 ticks, with a 200-tick player cooldown.
- **Oathguard Focus**: cost 4 Holy defensive Focus. Blocking or taking a hostile hit grants Absorption I for 60 ticks on a 240-tick cooldown.
- **Sunlance Focus**: cost 4 Holy combat Focus. Fully charged direct melee hits deal 10% extra damage to undead and Fury-affinity mobs.

### The Reliquary

Faction id: `attuned:reliquary`

Theme: preserved names, altar craft, relics, memory, grave markers, old vows.

Foci:

- **Censer Focus**: cost 3 Holy utility Focus. In light level 12+ or within 4 blocks of a campfire, trims poison or wither by 20 ticks every 100 ticks. It never clears effects instantly like milk.
- **Namesake Focus**: cost 2 Holy Focus. Grants +1 Luck while carrying a custom-named item or within 8 blocks of a custom-named non-player mob. It does not duplicate loot.
- **Threshold Focus**: cost 3 Holy Focus. Moving from light level 7 or lower into light level 12+ grants Absorption I for 80 ticks on a 400-tick cooldown.

### The Verdant Choir

Faction id: `attuned:verdant_choir`

Theme: vanilla nature, bees, moss, crops, rain, living paths.

Foci:

- **Rootstep Focus**: cost 2 Zephyr Focus. Adds modest movement and safe-fall help while on natural blocks, weaker than Swift and Leap in general travel.
- **Bloom Focus**: cost 2 neutral Focus. Adds a rare flower, seed, or bee-adjacent bonus from plant gathering, with lower output than Forager.
- **Mossheart Focus**: cost 3 neutral Focus. Grants Resistance I for 60 ticks while standing on moss, grass, or leaves after a hostile hit, on a 240-tick cooldown.

### The Ashen Forge

Faction id: `attuned:ashen_forge`

Theme: anvils, furnaces, copper, fire, tempered metal, hard choices.

Foci:

- **Temper Focus**: cost 3 Fury Focus. After using a furnace, blast furnace, smithing table, or anvil, fully charged melee hits deal 8% extra damage for 200 ticks.
- **Kilnward Focus**: cost 3 Bastion Focus. While near lit furnaces, magma, or lava, hostile hits can grant Resistance I for 60 ticks on a 240-tick cooldown. It does not grant fire immunity.
- **Rivet Focus**: cost 2 Bastion Focus. Adds grounded knockback resistance while crouching, blocking, or standing on metal blocks, with no full immunity.

### The Unseen Expansion

Faction id remains `attuned:unseen`

Theme: shadow answers to Radiant reveal.

Foci:

- **Mask Focus**: cost 3 Zephyr Focus. After crouching in light level 7 or lower for 40 ticks, resists reveal/glowing effects for 100 ticks.
- **Whisper Focus**: cost 2 neutral Focus. The Focus ability key softens sound and detection for 80 ticks on a 300-tick cooldown.
- **Blackout Focus**: cost 3 neutral Focus. The Focus ability key creates a short smoke pulse that disrupts line of sight for 40 ticks, weaker and shorter than Smoke Focus.

## Balance Rules

Every new Focus must follow these constraints:

- Stay within normal Focus costs, usually 2-5.
- Prefer conditional effects over always-on stat power.
- Avoid free flight, teleport loops, item duplication, ore duplication, inventory automation, permanent regeneration, or permanent immunity.
- Use cooldowns for active or burst effects.
- Keep raw damage bonuses below 10% unless the condition is narrow and temporary.
- Keep defensive effects at Resistance I, Absorption I, or narrow knockback resistance unless a later spec explicitly escalates them.
- Keep travel effects weaker than dedicated Zephyr builds unless terrain or weather gated.
- Keep utility effects useful but not farm-breaking.

## Data And Code Changes

Core model:

- Add `HOLY` to `Affinity`.
- Update affinity colors and UI color helpers.
- Update combat matchup logic for the four-affinity wheel.
- Update mob affinity tags and documentation to include Holy.
- Update commands, tooltips, panel readouts, journal text, and altar memory labels.

Pacts and Apex:

- Add Radiant Covenant to `Pact`.
- Add Holy Apex Judgment to `Apex`.
- Ensure Discord and Neutral Apex work from the expanded Apex spec without conflicting with Holy.

Factions and Foci:

- Add faction language keys for Radiant, Reliquary, Verdant Choir, and Ashen Forge.
- Add new Focus item registrations, data definitions, models, textures, lore, effect text, and behavior classes where needed.
- Extend Focus data consistency tests so every new faction id is translated and every new Focus has assets.

Loot:

- All new Foci stay in the global Focus pool.
- Radiant and Reliquary bias toward temples, villages, strongholds, trial chambers, and ancient city/ruin-style memory locations.
- Verdant Choir biases toward jungle, trail ruins, fishing treasure, and village/farm-adjacent tables.
- Ashen Forge biases toward weaponsmith, armorer, mineshaft, trial, nether, and bastion-style tables.
- Unseen bias remains stealth/ruin-heavy and gains positive counterweight entries in places where Radiant appears, so rival lore can surface in the same exploration routes.

## Art Direction

Every new Focus needs the existing 64x512 animated medallion format with eight 64x64 frames.

Palette anchors:

- Holy/Radiant: warm white, gold, pale amethyst.
- Reliquary: aged gold, parchment, amethyst, soft candlelight.
- Verdant Choir: moss green, honey yellow, leaf shadow.
- Ashen Forge: copper, ember red, iron gray.
- Unseen: smoke gray, violet shadow, muted magenta.

Use image generation for initial concepts where helpful, then normalize into the existing animated pixel-medallion style. Generated art must be committed into the project only after cleanup, dimension checks, and animation checks pass.

## Documentation And Journal

Update:

- `docs/reference.md` with the four-affinity wheel and new factions.
- `docs/adding-a-focus.md` so new examples mention Holy as a valid affinity.
- In-game journal pages for Holy, Radiant Covenant, Radiant vs Unseen lore, and the expanded faction families.
- `/attuned journal` compact guide text.

## Testing

Add or extend tests for:

- Four-affinity matchup rules.
- Holy Focus data parsing.
- Radiant Covenant qualification.
- Judgment Apex qualification and counter behavior.
- New faction translation coverage.
- New Focus registration/list/data/asset consistency.
- Loot bias tables still include every Focus with positive weight.
- Item texture dimensions and animation remain 64x512 with visible frame movement.

## Non-Goals

- Do not make Holy a stronger neutral lane.
- Do not remove or flatten the existing Fury, Bastion, and Zephyr identities.
- Do not turn The Unseen into an evil faction.
- Do not add non-vanilla concepts such as firearms, tech armor, dimensions, custom mobs, or forced mod dependencies.
- Do not implement all possible Holy content in one wave; the first wave must be testable and balanceable.
