# Attuned Aspect Counter System — Design Draft

Date: 2026-06-13
Status: planning / not yet implemented
Related project: Attuned Minecraft Mod

## Goal

Expand Attuned beyond the current four combat affinities while preserving the mod's core rule: **every Focus type exists inside a counter system**. No new type should be pure flavor, pure power creep, or a standalone category. Every type must:

1. Counter at least two other types.
2. Be countered by at least two other types.
3. Have a clear playstyle identity.
4. Have visible in-game counter language.
5. Have lore that explains why those counters make sense.

This design intentionally starts with an **8-type Aspect wheel** instead of jumping to 12+ types immediately. The current Java `Affinity` enum is load-bearing (`FURY`, `BASTION`, `ZEPHYR`, `HOLY`, `beats()`, Discord, Pacts, HUD colors, Altar states, tests), so the safest expansion is:

- Keep the current four as **Core Affinities / Core Aspects**.
- Add a new player-facing **Aspect** layer for broader Focus types.
- Let Foci carry both:
  - optional `affinity` — existing stance / Pact lane
  - required or defaulted `aspect` — new counter identity
  - optional `faction` — lore family/content group, as today

Example future Focus JSON shape:

```json
{
  "item": "attuned:riptide_heart_focus",
  "cost": 3,
  "affinity": "zephyr",
  "aspect": "attuned:tide",
  "faction": "attuned:tideborn",
  "behavior": "attuned:riptide_heart"
}
```

The player-facing wording can be simple:

```text
Aspect: Tide
Strong vs Fury / Forge
Weak vs Zephyr / Verdant
```

---

## Non-goals

- Do **not** add random fifth/sixth affinities directly to `Affinity` yet.
- Do **not** remove Discord. Discord remains the mixed-affinity stance/risk state.
- Do **not** make counters raw `+X% damage` by default. Counters should interrupt, soften, cleanse, expose, or punish the target Aspect's main mechanic.
- Do **not** ship all future aspects at once. Add them in balanced sets only.

---

## Phase 1 Aspect Wheel

Phase 1 uses eight Aspects:

1. Fury
2. Bastion
3. Zephyr
4. Holy
5. Tide
6. Forge
7. Verdant
8. Umbral

The first four preserve the existing affinity cycle:

```text
Holy > Fury > Bastion > Zephyr > Holy
```

The expanded wheel gives every Aspect exactly two strengths and two weaknesses:

| Aspect | Strong against | Weak against | Why it works |
|---|---|---|---|
| **Fury** | Bastion, Verdant | Holy, Tide | Rage breaks shields and burns growth, but is cleansed by sanctity and drowned by water. |
| **Bastion** | Zephyr, Umbral | Fury, Forge | Walls stop speed and reveal shadows, but crack under raw violence and armor-piercing heat. |
| **Zephyr** | Holy, Tide | Bastion, Umbral | Wind scatters rituals and parts water, but is blocked by fortifications and swallowed by shadow. |
| **Holy** | Fury, Umbral | Zephyr, Verdant | Light purges rage and shadow, but wind disperses auras and nature overgrows dogma. |
| **Tide** | Fury, Forge | Zephyr, Verdant | Water cools anger and quenches heat, but wind redirects currents and roots drink floods. |
| **Forge** | Bastion, Verdant | Tide, Umbral | Heat pierces armor and cuts growth, but water quenches it and shadow steals its spark. |
| **Verdant** | Tide, Holy | Fury, Forge | Roots drink water and reclaim temples, but fire and steel cut them down. |
| **Umbral** | Zephyr, Forge | Bastion, Holy | Shadow snags speed and steals flame, but wards and light reveal it. |

### Counter pairs as data

The matrix above should become the canonical source of truth in code. Suggested shape:

```java
public enum Aspect implements StringRepresentable {
    FURY("fury"),
    BASTION("bastion"),
    ZEPHYR("zephyr"),
    HOLY("holy"),
    TIDE("tide"),
    FORGE("forge"),
    VERDANT("verdant"),
    UMBRAL("umbral");

    public boolean counters(Aspect other) { ... }
}
```

The current `Affinity.beats()` should remain untouched until there is a dedicated migration plan. It answers stance/cycle questions. `Aspect.counters()` answers expanded Focus-type counter questions.

---

## Counter mechanics principles

### 1. Counters attack mechanics, not just health bars

Bad counter:

```text
Tide deals +10% damage to Forge.
```

Good counter:

```text
Tide reduces Heat stacks, weakens Forge armor-tempering, and makes Forge active abilities cool down slightly slower while the target is Wet.
```

### 2. Counters should be readable

Players should know why they won or lost. Every counter effect needs at least one of:

- tooltip wording
- journal explanation
- HUD icon tint/pulse
- particles/sound
- action-bar feedback in PvP/inspection

Example:

```text
Countered: your Heat was quenched by Tide.
```

### 3. No universal hard-locks

Counters should be meaningful but not delete a build. Use partial suppression, stack decay, cooldown pressure, reveal windows, or condition breaks.

### 4. PvE needs pseudo-Aspects

Mobs can be mapped to aspects for PvE counter play:

| Mob / environment | Suggested Aspect |
|---|---|
| Blaze, magma cube, fire hazards | Forge or Fury |
| Drowned, guardians, ocean ruins | Tide |
| Undead, soul sand valleys | Umbral or Grave later |
| Fast flyers / phantoms | Zephyr |
| Armored mobs / shield users | Bastion |
| Illagers/witches/ritual sites | Holy or Umbral depending context |
| Lush caves / overgrowth / poison mobs | Verdant |

This keeps the counter web useful outside PvP.

---

## Aspect dominance rules

Each active Focus contributes one Aspect. Dormant Foci do not count.

Recommended rules:

1. **Dominant Aspect:** the Aspect with the highest active Focus count.
2. **Tie-breaker:** if two or more Aspects tie for highest count, the build is **Mixed** for Aspect-counter purposes and gains no dominant counter advantage.
3. **Committed Aspect:** 3+ active Foci of the same Aspect wakes that Aspect's Pact/Synergy if no stronger affinity Pact owns the slot.
4. **Counter check:** your dominant/committed Aspect can counter a target's dominant/committed Aspect.
5. **Discord remains affinity-only:** mixed `affinity` still creates Discord. Mixed `aspect` creates no Discord by itself; it just forfeits Aspect dominance.

This avoids punishing creative multi-aspect utility builds too hard.

---

## Lore frame: The Wheel of Refusals

The Attunement Altar does not create power. It records **refusals**: what kind of force a soul refuses to yield to.

- Fury refuses stillness.
- Bastion refuses collapse.
- Zephyr refuses capture.
- Holy refuses corruption.
- Tide refuses flame.
- Forge refuses weakness.
- Verdant refuses barrenness.
- Umbral refuses revelation.

The counter wheel is not arbitrary elemental math. Each Aspect beats the things its philosophy can deny.

Suggested journal intro:

> The first scholars named four Affinities because four were all they could survive. Later hands found the rest in broken places: under tides, inside furnaces, beneath roots, and in the shade behind the altar flame. These are not colors. They are refusals.

---

## Aspect dossiers

### Fury

**Color:** crimson / ember red
**Motto:** "Strike before the world can become a wall."
**Identity:** aggression, burst damage, low-health pressure, momentum.
**Strong vs:** Bastion, Verdant.
**Weak vs:** Holy, Tide.

**Counter logic:**

- Beats Bastion by cracking guard, punishing turtling, and scaling with repeated pressure.
- Beats Verdant by burning roots, cutting sustain windows short, and punishing slow regeneration.
- Loses to Holy because cleansing and judgment punish reckless rage.
- Loses to Tide because water slows, drags, and cools Fury's momentum.

**Existing anchor Foci:** `bloodfury_focus`, `frenzy_focus`, `edge_focus`, `cinder_focus`, `emberward_focus`, `temper_focus` if kept Fury-affinity.

**New Focus ideas:**

| Focus | Cost | Type | Concept |
|---|---:|---|---|
| `breakplate_focus` | 4 | Power | Hits against armored/guarding targets build Rupture; Rupture is stronger vs Bastion. |
| `scorchroot_focus` | 3 | Passive | Melee hits briefly reduce regeneration/root effects; strong vs Verdant. |
| `redline_focus` | 3 | Charge | Sprint/combat builds Redline; spend on next attack for burst, but Tide slows Redline gain. |
| `warbrand_focus` | 5 | Relic | More damage while wounded; Holy counters by reducing its low-health amplification. |

**Lore hook:** Fury foci are made from things that broke while still moving: snapped blades, cracked hunt-bells, burned pennants.

---

### Bastion

**Color:** gold / iron / slate
**Motto:** "If the gate holds, history continues."
**Identity:** armor, guard, anti-burst, knockback resistance, deliberate positioning.
**Strong vs:** Zephyr, Umbral.
**Weak vs:** Fury, Forge.

**Counter logic:**

- Beats Zephyr by grounding movement, blocking lunge windows, and punishing overextension.
- Beats Umbral by warding/revealing stealth and protecting against ambush.
- Loses to Fury because relentless pressure cracks passive defense.
- Loses to Forge because heat and tools pierce armor.

**Existing anchor Foci:** `aegis_focus`, `bulwark_focus`, `iron_focus`, `anchor_focus`, `gravebind_focus`, `kilnward_focus` if Bastion-affinity.

**New Focus ideas:**

| Focus | Cost | Type | Concept |
|---|---:|---|---|
| `watchtower_focus` | 3 | Passive | Standing ground builds Ward; Ward reveals nearby Umbral users/mobs. |
| `grounding_plate_focus` | 4 | Passive | Reduces launch/pull effects; strong vs Zephyr movement chains. |
| `gatekeeper_focus` | 5 | Active | Briefly creates a frontal guard cone; Fury/Forge have partial answers. |
| `oathstone_focus` | 4 | Charge | Blocking/taking hits charges a pulse that slows Zephyr users. |

**Lore hook:** Bastion foci are not shields; they are promises made heavy enough to stand behind.

---

### Zephyr

**Color:** cyan / white / pale sky blue
**Motto:** "No chain has learned the shape of wind."
**Identity:** speed, repositioning, evasion, air, escape, tempo resets.
**Strong vs:** Holy, Tide.
**Weak vs:** Bastion, Umbral.

**Counter logic:**

- Beats Holy by dispersing aura zones and outranging support rituals.
- Beats Tide by redirecting currents, escaping pulls, and crossing wet terrain quickly.
- Loses to Bastion because walls/guards punish speed lanes.
- Loses to Umbral because shadow hides traps and snags movement.

**Existing anchor Foci:** `swift_focus`, `leap_focus`, `galespur_focus`, `rainstep_focus`, `softstep_focus`, `hollowstep_focus`, `gloomstride_focus`, `shadowmeld_focus` if kept Zephyr-affinity but Umbral-aspect.

**New Focus ideas:**

| Focus | Cost | Type | Concept |
|---|---:|---|---|
| `crosswind_focus` | 3 | Passive | Moving sideways or jumping weakens Holy aura effects on you. |
| `mistbreaker_focus` | 3 | Passive | Escaping Tide pull/slow grants brief Speed. |
| `skyhook_focus` | 4 | Active | Short burst dash; Bastion grounding cuts range. |
| `featherwake_focus` | 2 | Triggered | Landing from falls creates a tiny speed pulse. |

**Lore hook:** Zephyr foci are carried, not worn. They are debts owed by birds, banners, and last breaths.

---

### Holy

**Color:** ivory / gold / soft sun
**Motto:** "What is named in light cannot stay a curse."
**Identity:** cleansing, healing, anti-corruption, anti-rage, anti-shadow.
**Strong vs:** Fury, Umbral.
**Weak vs:** Zephyr, Verdant.

**Counter logic:**

- Beats Fury by cleansing rage, reducing self-harm amplifiers, and punishing reckless low-health play.
- Beats Umbral by revealing stealth, breaking fades, and strengthening target clarity.
- Loses to Zephyr because mobile builds leave Holy zones and disperse aura effects.
- Loses to Verdant because nature outlasts formal sanctity and overgrows static rituals.

**Existing anchor Foci:** `sunlance_focus`, `votive_focus`, `oathguard_focus`, `bellwether_focus`, `last_rites_focus`, `threshold_focus`, `namesake_focus`, `censer_focus`.

**New Focus ideas:**

| Focus | Cost | Type | Concept |
|---|---:|---|---|
| `cleansing_sun_focus` | 4 | Active | Removes Fury/Umbral stacks from yourself; long cooldown. |
| `dawnbell_focus` | 3 | Triggered | First hit from stealth/Umbral marks the attacker. |
| `martyr_lamp_focus` | 5 | Relic | Redirects small damage from allies, but Verdant counters by rooting you in place. |
| `vowlight_focus` | 3 | Charge | Healing charges a light pulse that weakens Fury. |

**Lore hook:** Holy foci are not nice. They are witnesses, and witnesses can condemn.

---

### Tide

**Color:** deep teal / seafoam / pearl
**Motto:** "Every fire becomes steam. Every blade rusts."
**Identity:** water, drag, rain, slow, pull, pressure, salvage, anti-heat.
**Strong vs:** Fury, Forge.
**Weak vs:** Zephyr, Verdant.

**Counter logic:**

- Beats Fury by slowing rushdown and cooling rage/Redline effects.
- Beats Forge by quenching Heat stacks and weakening tempering windows.
- Loses to Zephyr because wind redirects currents and escapes pull effects.
- Loses to Verdant because roots drink, bind, and stabilize flooded ground.

**Existing anchor Foci / likely migrations:** `tide_focus`, `harpoon_focus`, `driftglass_focus`, `harborlight_focus`, `netmender_focus`, `linecast_focus`.

**New Focus ideas:**

| Focus | Cost | Type | Concept |
|---|---:|---|---|
| `undertow_focus` | 4 | Active | Brief pull/slow field; strong vs Fury sprint chains and Forge heat windows. |
| `riptide_heart_focus` | 3 | Charge | Sprint-swimming/rain movement builds Current; spend for a lunge. |
| `pearlguard_focus` | 3 | Passive | Defense while wet/raining; Zephyr can disperse the wet advantage. |
| `wrecklight_focus` | 2 | Utility | Reveals underwater containers/mobs; lore/scavenger Focus. |
| `brine_lungs_focus` | 2 | Passive | Water breathing below sea level; small recovery after surfacing. |
| `anchorwake_focus` | 4 | Triggered | Sneaking briefly roots you and pulls drops/enemies slightly. |

**Pact candidate:** **Undertow** — 3+ Tide Aspect Foci. Grants water/rain pressure bonuses and a once-per-cooldown anti-lethal underwater effect.

**Lore hook:** Tide foci are made from things the ocean returned with an opinion: bells, anchors, drowned pearls, driftglass.

---

### Forge

**Color:** ember orange / black iron / brass
**Motto:** "Everything bends when heated correctly."
**Identity:** heat, metal, tempering, armor-pierce, crafting, durability, anti-turtle.
**Strong vs:** Bastion, Verdant.
**Weak vs:** Tide, Umbral.

**Counter logic:**

- Beats Bastion by softening armor, piercing guard, and punishing stationary defense.
- Beats Verdant by cutting roots, burning growth, and converting sustain to fuel.
- Loses to Tide because heat is quenched and metal rusts.
- Loses to Umbral because shadow steals spark, hides targets, and interrupts careful craft.

**Existing anchor Foci / likely migrations:** `temper_focus`, `rivet_focus`, `kilnward_focus`, `ashen_debt_focus`, maybe `cinder_focus` or `emberward_focus` depending final taxonomy.

**New Focus ideas:**

| Focus | Cost | Type | Concept |
|---|---:|---|---|
| `slagbrand_focus` | 4 | Power | Attacks apply Heat; Heat weakens Bastion armor effects. |
| `anvilheart_focus` | 4 | Passive | Taking blocked/reduced damage charges a heavy counter-hit. |
| `sparkweld_focus` | 3 | Utility | Repairs a little durability after combat streaks; weak in rain/water. |
| `bellows_focus` | 3 | Charge | Fire/lava proximity builds Heat; Tide drains it quickly. |
| `blackiron_focus` | 5 | Relic | Big armor-pierce window after standing near a furnace/campfire; Umbral can snuff it. |

**Pact candidate:** **Tempered** — 3+ Forge Aspect Foci. Heat stacks become a managed resource; overheat creates risk.

**Lore hook:** Forge foci remember the hand that shaped them. Some remember being the hand.

---

### Verdant

**Color:** moss green / leaf gold / pale flower pink
**Motto:** "Stone is patient. Roots are worse."
**Identity:** growth, sustain, roots, poison resistance, food, overgrowth, nature control.
**Strong vs:** Tide, Holy.
**Weak vs:** Fury, Forge.

**Counter logic:**

- Beats Tide by drinking floods, stabilizing currents, and turning wet ground into roots.
- Beats Holy by overgrowing static sanctuaries and outlasting formal ritual healing.
- Loses to Fury because fire/rage burns through slow sustain.
- Loses to Forge because tools and heat cut cultivated growth.

**Existing anchor Foci / likely migrations:** `bloom_focus`, `mossheart_focus`, `rootstep_focus`, `harvest_focus`, `forager_focus`, `thornward_focus`.

**New Focus ideas:**

| Focus | Cost | Type | Concept |
|---|---:|---|---|
| `thornwake_focus` | 3 | Triggered | Attackers take chip/root slow after hitting you; Fury/Forge reduce it. |
| `seedcall_focus` | 2 | Utility | Crops/flowers/grass nearby grant tiny recovery pulses. |
| `greenmantle_focus` | 4 | Passive | Poison/food/sustain Focus; counters Tide slow by rooting yourself. |
| `bramblegate_focus` | 4 | Active | Creates a short-lived slowing bramble patch. |
| `wildtithe_focus` | 3 | Charge | Eating/harvesting charges a heal pulse; weak to Fury burn. |

**Pact candidate:** **Overgrowth** — 3+ Verdant Aspect Foci. Sustain and rooting effects improve, but fire/forge counters remain scary.

**Lore hook:** Verdant foci are not peaceful. A garden is a conquest with flowers on it.

---

### Umbral

**Color:** black / indigo / violet
**Motto:** "A locked door still casts a shadow."
**Identity:** stealth, darkness, misdirection, eclipse, ambush, anti-heat, anti-speed.
**Strong vs:** Zephyr, Forge.
**Weak vs:** Bastion, Holy.

**Counter logic:**

- Beats Zephyr by hiding traps, snagging escape routes, and punishing fast movement into darkness.
- Beats Forge by snuffing Heat windows, hiding targets, and stealing line-of-sight.
- Loses to Bastion because wards reveal and deny ambush angles.
- Loses to Holy because light exposes and cleanses shadow effects.

**Existing anchor Foci:** `gloomstride_focus`, `duskward_focus`, `shadowmeld_focus`, `dreadfang_focus`, `eclipse_focus`, plus `blackout_focus`, `mask_focus`, `whisper_focus`, `veil_focus` if assigned Unseen/Umbral overlap.

**New Focus ideas:**

| Focus | Cost | Type | Concept |
|---|---:|---|---|
| `nullveil_focus` | 4 | Passive | Brief projectile/targeting disruption after sneaking in darkness. |
| `cinderthief_focus` | 3 | Counter | Hitting a Forge target drains Heat and gives you a short shadow-glint. |
| `snaremoon_focus` | 4 | Active | Places a small shadow snare; strong vs Zephyr. |
| `eclipsed_name_focus` | 5 | Relic | After taking lethal-ish burst, fade briefly instead of dying if off cooldown. |

**Pact candidate:** **Eclipse** — 3+ Umbral Aspect Foci. Fade/reveal games become central; Bastion/Holy remain the intended answers.

**Lore hook:** Umbral foci are made from absences: the missing name from a grave, the unlit half of a bell, the shadow of a sword that was never drawn.

---

## Starter release shape

Recommended first real implementation should be smaller than this whole catalog.

### Phase A — Aspect foundation

- Add `Aspect` model and codec.
- Add optional/defaulted `aspect` to `FocusDefinition`.
- Migrate existing Foci to one of eight Aspects.
- Add tooltip/journal counter chart.
- No new active counter mechanics yet, except display/resolution.

### Phase B — Counter mechanics MVP

Implement 1-2 concrete counter interactions per new Aspect:

| Aspect | MVP counter mechanic |
|---|---|
| Tide | Reduces Forge Heat / Fury Redline-style stacks. |
| Forge | Adds armor-pierce/guard pressure against Bastion/Verdant root effects. |
| Verdant | Converts Wet/slow zones into root/sustain pulses. |
| Umbral | Suppresses Forge Heat gain and creates anti-Zephyr snares. |
| Fury | Burns Verdant sustain and pressures Bastion guard. |
| Bastion | Reveals Umbral and grounds Zephyr bursts. |
| Zephyr | Escapes Tide pull and disperses Holy zones. |
| Holy | Reveals Umbral and cleanses Fury/Blood-like rage effects. |

### Phase C — First content batch

Add only 12 new Foci first:

| Aspect | New Foci |
|---|---|
| Tide | `undertow_focus`, `riptide_heart_focus`, `pearlguard_focus` |
| Forge | `slagbrand_focus`, `anvilheart_focus`, `sparkweld_focus` |
| Verdant | `thornwake_focus`, `seedcall_focus`, `bramblegate_focus` |
| Umbral | `nullveil_focus`, `cinderthief_focus`, `snaremoon_focus` |

This gives each new Aspect at least three fresh pieces while existing four keep their current content base.

### Phase D — Pact layer

Add four new Aspect Pacts after the MVP feels readable:

| Pact | Wakes from | Fantasy |
|---|---|---|
| Undertow | 3+ Tide | Drag, wet defense, anti-heat. |
| Tempered | 3+ Forge | Heat stacks, armor-pierce, overheat risk. |
| Overgrowth | 3+ Verdant | Sustain, roots, terrain control. |
| Eclipse | 3+ Umbral | Fade, snare, reveal/anti-reveal tension. |

Existing Pacts remain tied to existing affinities unless/until renamed.

---

## Future expansion candidates

Do not add these until the 8-Aspect wheel is implemented and tested. Each future addition must rebalance the full matrix.

| Future Aspect | Identity | Likely counters | Likely weaknesses |
|---|---|---|---|
| Blood | sacrifice, lifesteal, wounds | Bastion, Verdant | Holy, Grave/Future Death |
| Grave | souls, death, anti-mobility | Blood, Zephyr | Holy, Verdant |
| Storm | lightning, charge, disruption | Tide, Zephyr | Bastion, Forge |
| Astral | fate, night, prediction | Storm, Holy | Umbral, Void/Future Null |
| Frost | slow, shatter, preservation | Verdant, Tide | Forge, Fury |
| Void | deletion, silence, instability | Astral, Forge | Holy, Blood |

If adding these later, prefer expanding from 8 → 12 or 8 → 16 in one deliberate matrix pass, not one-off additions.

---

## UX / documentation requirements

### Tooltips

Every Focus with an Aspect should show:

```text
Aspect: Tide
Strong vs Fury / Forge
Weak vs Zephyr / Verdant
```

For compact mode:

```text
Tide: + vs Fury/Forge · - vs Zephyr/Verdant
```

### Journal chapters

Add a chapter: **The Wheel of Refusals**.

Pages:

1. What Aspects are.
2. How Aspect dominance works.
3. The counter chart.
4. Fury + Bastion lore.
5. Zephyr + Holy lore.
6. Tide + Forge lore.
7. Verdant + Umbral lore.
8. Practical build examples.

### Creative tabs

Long term, split tabs by player intent:

- Attuned: Core Foci
- Attuned: Aspect Foci
- Attuned: Utility Foci
- Attuned: Relics / Curses, if those systems ship

Do not do this in Phase A unless the current two-tab list becomes unreadable.

---

## Build examples

### Tide anti-Forge build

- `undertow_focus`
- `pearlguard_focus`
- `riptide_heart_focus`
- `harpoon_focus`
- `driftglass_focus`
- `netmender_focus`

Identity: cools Heat, slows rushdown, plays best in rain/water.

Countered by: Zephyr escape builds and Verdant root/sustain builds.

### Forge anti-Bastion build

- `slagbrand_focus`
- `anvilheart_focus`
- `temper_focus`
- `kilnward_focus`
- `rivet_focus`
- `sparkweld_focus`

Identity: heat stacks, armor-pierce, stand-and-swing pressure.

Countered by: Tide quench and Umbral snuff/ambush.

### Verdant anti-Tide sustain build

- `thornwake_focus`
- `seedcall_focus`
- `bloom_focus`
- `mossheart_focus`
- `rootstep_focus`
- `harvest_focus`

Identity: roots convert flood pressure into sustain and slows.

Countered by: Fury burn and Forge cutting heat.

### Umbral anti-Zephyr trap build

- `nullveil_focus`
- `snaremoon_focus`
- `gloomstride_focus`
- `shadowmeld_focus`
- `blackout_focus`
- `veil_focus`

Identity: snare fast targets, fade from danger, punish predictable escape lines.

Countered by: Bastion wards and Holy reveal.

---

## Implementation notes for later

Likely files touched when implementing:

- `src/main/java/dev/attuned/api/focus/Aspect.java` — new enum/model.
- `src/main/java/dev/attuned/api/focus/FocusDefinition.java` — add `Optional<Aspect>` or required/defaulted `Aspect` field.
- `src/main/java/dev/attuned/api/focus/AspectColors.java` — palette source of truth, parallel to `AffinityColors`.
- `src/main/java/dev/attuned/attunement/Attunement.java` or adjacent resolver — active Aspect counts/dominance.
- `src/main/java/dev/attuned/pacts/Pacts.java` or new `AspectPacts.java` — 3+ Aspect Pact wake logic.
- `src/main/java/dev/attuned/client/*` HUD/tooltips/journal surfaces.
- `src/main/resources/data/attuned/attuned/focus/*.json` — add `aspect` to all Foci.
- `src/main/resources/assets/attuned/lang/en_us.json` — Aspect names, tooltips, journal pages.
- `src/test/java/...` contract tests for exact matrix, JSON coverage, tooltip strings, journal chapter indices.
- `tests/` Python checks if repository verification learns about `aspect` coverage.

Testing strategy:

1. Contract-test `Aspect.counters()` for the exact matrix above.
2. Contract-test every Focus JSON has an Aspect after migration.
3. Test dominance counts ignore dormant Foci.
4. Test ties produce no Aspect dominance.
5. Test tooltips list strengths/weaknesses.
6. Test journal includes the full counter chart.
7. Run `./gradlew test`, `./gradlew build`, `python tools/verify_repository.py`, and Python tests.

---

## Open questions

1. Should player-facing text call these **Aspects**, **Domains**, or **Focus Types**?
   - Recommendation: **Aspects** in code/lore, **Focus Type** as casual tooltip wording if needed.
2. Should every Focus require an Aspect, including pure utility Foci?
   - Recommendation: yes, default old uncategorized utility Foci to `neutral` only temporarily; final state should classify all shipped Foci.
3. Should Aspect dominance have its own HUD gem?
   - Recommendation: not at first. Start with tooltips/journal/build inspect. Add HUD only if players need it.
4. Should old Pacts be renamed from affinity Pacts to Aspect Pacts?
   - Recommendation: no for Phase A. Preserve existing vocabulary until the new system proves fun.
5. How many new Foci should ship in the first Aspect update?
   - Recommendation: 12 new Foci max for the first pass, plus migration of existing Foci into Aspects.
