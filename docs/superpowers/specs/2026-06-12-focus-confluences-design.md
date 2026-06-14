# Attuned — Focus Confluences Design

Target: Minecraft 26.1.2, Fabric, Java 25. Slots into a future minor (1.4.x or
1.5.x); independent of the Tideborn / Cursed-Foci / Trial-Pact roadmaps and can
ship beside any of them.

Working name: **Confluence** (plural Confluences) — "where two currents meet."
The name is a placeholder; it must not collide with the existing vocabulary
(Attunement, Affinity, Discord, Pact, **Resonance**, Apex, Reweaving). `Resonance`
is taken by the combat-momentum system, so do not call these "resonances."
Alternatives if the name doesn't land: Conjunctions, Confluences, Harmonies.

---

## Theme — depth, not breadth

The mod has 61 Foci. Every roadmap on disk (`2026-06-09-...roadmap`,
`2026-06-10-1.4-resonant-depths`, `2026-06-10-1.5-echoes-and-accords`) adds *more*
Foci, factions, and Pacts. Each Focus is still an island: it does its one thing,
and a build is "fit the most value under budget."

Confluences make the **relationships between Foci** the game. A Confluence is a
small bonus that wakes when a specific *combination* of Foci is active at once.
Suddenly 61 Foci is a combinatorial space, not a checklist — and the bonus comes
from Foci the player already owns, so it costs **zero new items, textures, or
loot plumbing**. It also turns the journal into a collection meta ("Confluences
discovered: 7 / 24"), which is the cheapest retention lever available.

This is deliberately the *opposite* lever from the content roadmaps, and composes
with all of them: every new Focus a future release adds is new Confluence surface.

---

## Core mechanic

A **Confluence** is a named, data-defined set of 2–3 member Foci plus a modest
granted effect. It is **active** for a player exactly when *all* of its member
Foci are simultaneously **active** (equipped and within the attunement budget —
the existing `Attunement.activeSlots` notion, not merely equipped). When active,
it grants its effect; when any member drops (unequipped, or pushed dormant by the
budget), the effect is removed.

Design rules that keep it safe and legible:

- **Confluences cost no budget.** They are emergent rewards for a build you already
  paid for, never budget items. This keeps the budget core (the highest-risk
  subsystem in the repo) completely untouched.
- **Members must all be *active*, not just equipped.** This ties Confluences to the
  same dormancy rules everything else respects and means a player can't cheat one
  by over-stuffing slots past capacity.
- **Effects are modest, on the scale of a Pact tier** — a small attribute nudge, a
  conditional short buff, a quality-of-life amplification of what the member Foci
  already do. Never a new build-defining power; that is what Foci and Apex are for.
- **Natural cap.** Budget already limits how many Foci are active, so the number of
  simultaneous Confluences is self-limiting; no artificial cap needed.

---

## Architecture

This mirrors the **Pacts** system (`pacts/Pacts.java` + `pacts/Pact.java`) almost
beat for beat — Pacts are the proven precedent for "detect a set bonus from the
active Foci, apply tick/damage effects, announce transitions, award an
advancement, and fanfare the first time." Reuse every one of those patterns.

### 1. Pure resolver — `SynergyResolver` (Minecraft-free)

The testable core, in the `BudgetResolver` spirit (no Minecraft types, modelled on
`String` ids):

```java
// active: ids of currently-active Foci (e.g. "attuned:lantern"); defs: the loaded table
public static Set<String> activeConfluences(Set<String> activeFocusIds, List<SynergyDef> defs)
```

A Confluence is active iff `activeFocusIds.containsAll(def.members())`. `SynergyDef`
is a pure record (`String id`, `List<String> members`). This is the entire policy;
everything else is plumbing. Behavioral tests cover: exact-member match, missing
one member, superset (extra Foci present), 2-member vs 3-member, empty inputs,
and overlapping Confluences that share a member both waking together.

> Test constraint (load-bearing, per project conventions): the test classpath
> cannot Bootstrap Minecraft. `SynergyResolver` and its test stay Minecraft-free,
> modelling Foci as `String` ids exactly like `PresetApplicationResolverTest`.

### 2. Data-driven registry — `SynergyDefinition`

Confluences are a datapack registry, parallel to `FocusDefinition` and registered
through `AttunedRegistries` (copy the `FOCUS_DEFINITIONS` registration shape).
Files live at `data/<ns>/attuned/synergy/<id>.json`. This is intentional: it makes
Confluences **author-extensible from a datapack**, which is the natural on-ramp to
the companion datapack-Foci design — a pack that adds Foci can add Confluences for
them in the same pack with no code.

```json
{
  "members": ["attuned:lantern", "attuned:veil"],
  "modifiers": [
    { "attribute": "minecraft:movement_speed", "operation": "add_multiplied_base", "amount": 0.05 }
  ],
  "behavior": "attuned:hunters_patience"
}
```

The effect surface reuses the **exact two mechanisms a Focus already has**:

- `modifiers`: a `List<ModifierEntry>` applied while the Confluence is active and
  removed when it drops — identical to how `FocusDefinition.modifiers()` flow
  through `AttunedEffects`. Most Confluences need only this and require **no new
  Java at all**.
- `behavior` (optional): an `Identifier` into the same `AttunedFocusBehaviors`
  registry Foci use, for the few Confluences whose payoff is conditional or
  tick-based (e.g. "ability-use cross-refresh"). Reuses `FocusBehavior`'s
  `onActivate`/`onDeactivate`/`onTick` hooks verbatim.

The codec mirrors `FocusDefinition.CODEC` (members required, modifiers
`optionalFieldOf` default `List.of()`, behavior `optionalFieldOf`). A
`SynergyHolderCodecRoundTripTest` pins it like `FocusHolderCodecRoundTripTest`.

### 3. Runtime — `Synergies` (mirrors `Pacts`)

An idempotent `Synergies.init()` wired in `Attuned.onInitialize` after
`Pacts.init()`:

- `ServerTickEvents.END_SERVER_TICK`, throttled `% 20` (Pacts uses `% AURA_TICK`;
  there is no per-tick work here): diff each player's active-Confluence set against
  last tick's (`Map<UUID, Set<String>>`, cleanup-registered per constraint #8),
  fire `onActivate`/`onDeactivate` for behavior-bearing ones, apply/remove the
  declarative modifiers, and announce transitions in chat.
- Active-set derivation reuses `Attunement.activeSlots` + `definitionFor(...).item()`
  to build the active id set, then calls `SynergyResolver.activeConfluences`.
- **First-discovery fanfare**: reuse the `AttunedAttachments.sawOnboarding` /
  `markOnboarding` persistent marker (the same one `Pacts.maybeFanfare` uses) keyed
  `"confluence_first_<id>"`, plus a one-shot advancement
  `attunement/confluence_<id>` and the discovery write (below). Survives death.
- All per-player maps register `AttunedPlayerCleanup.onForget` +
  `AttunedServerCleanup.onStop` (constraint #8).

Modifier application/removal must use a stable modifier id per Confluence (the
Confluence id) so removal fully undoes it — the same discipline
`Pacts.applyWindrunnerStepHeight`/`removeWindrunnerStepHeight` follow, including the
on-join reconcile (strip-then-reapply) so a stale NBT modifier from a previous
session can't accumulate.

### 4. Discovery state + journal

- A persistent attachment `DISCOVERED_CONFLUENCES : Set<String>` on the player
  (codec like `PRESETS`, synced `targetOnly`, `copyOnDeath` — discoveries persist
  through death). Written the first time each Confluence activates.
- A new Attunement Journal chapter "Confluences": for each known Confluence, show
  its name + members + effect if discovered, or a redacted `???` row with only the
  member *count* if not — the collection hook. Reuses the journal chapter registry
  and the June-2026 chapter-drift fail-fast guard in `AttunementJournalScreen`
  (append at the end of `PAGES`, extend the guard in the same commit).
- Lang: `confluence.attuned.<id>.name` / `.desc`, `journal.attuned.confluence.*`,
  `confluence.attuned.discovered` ("Confluence discovered: %s").

### 5. Readout / HUD (optional in v1)

`AttunementReadout.snapshot` can gain an `activeConfluences` field so the Foci
panel shows a small "✦ N" indicator. Keep this behind the shared snapshot the 1.4
plan already routes everyone through; do not add a sixth hand-rolled aggregation.
Shippable without any HUD change — the chat announcement + journal carry it.

### 6. Build-craft hint (optional, high value)

Mirror `Pacts.previewOf`: when a player is exactly one active member away from a
Confluence *they have already discovered*, surface a one-line panel hint ("Hunter's
Patience needs Veil"). Only for discovered Confluences, so it never spoils the meta.

---

## Example Confluence table (v1 candidates — all real shipped Foci)

Grounded in the current behavior roster (`docs/reference.md`). Final numbers tuned
during planning; effects shown as intent.

| Confluence | Members | Effect (modest) | Impl |
|---|---|---|---|
| **Hunter's Patience** | `lantern` + `veil` | While Veil-invisible, Lantern-marked threats are revealed a little longer; tiny move speed while cloaked. | behavior |
| **Forgewarded** | `kilnward` + `emberward` | Near lit forge/lava, Kilnward's Resistance lasts +1 s and is fire-immune *and* briefly regenerates. | behavior |
| **Gravewell** | `lodestone` + `epitaph` | Item and XP-orb pull radius +50%; a kill briefly magnetizes both. | modifiers + behavior |
| **Cartographer's Trust** | `beacon` + `waystone` + `driftglass` | Held compass cleanly cycles bed / last-death / return-point instead of one winning. | behavior |
| **Verdant Hand** | `harvest` + `bloom` | Plant-gather bonus rolls are modestly more frequent while both active. | behavior |
| **Tempest** | `rainstep` + `stormcall` | Sprinting in rain grants a brief Speed surge on each lightning call. | behavior |
| **Immovable** | `anchor` + `rivet` | Grounded knockback resist combines into a brief Resistance when you *are* knocked. | modifiers |
| **Vanishing Act** | `smoke` + `whisper` (or `blackout`) | Using one stealth ability shaves a slice off the other's cooldown. | behavior |
| **Wildward** | `mossheart` + `rootstep` | On natural ground, Mossheart's Resistance window is slightly longer. | modifiers |
| **Bulwark of Light** | `votive` + `oathguard` | The two Radiant shields can stack one extra point of absorption. | modifiers |

Ship **6–8** for v1 (a mix of pure-`modifiers` and `behavior` ones to exercise both
paths); the rest are an authored backlog. Several deliberately reward a *theme*
(stealth, forge, plant, navigation) so they read as "oh, these go together."

---

## Balance & counterplay

- Effects sit at or below a Pact tier in power. A Confluence should make a themed
  build feel *cohesive*, never out-DPS a focused affinity build.
- PvP-sensitive Confluence numbers route through `AttunedConfig` if any combat math
  is involved (the 1.5 plan already establishes the server-balance-config pattern).
- No Confluence may hard-disable another player (no stuns/roots) — same restraint
  Apex/Pacts observe.

---

## Tests (every feature lands with its contract tests, repo convention)

- `SynergyResolverTest` (behavioral, Minecraft-free): membership matrix above.
- `SynergyHolderCodecRoundTripTest`: JSON round-trip incl. empty modifiers/behavior.
- `SynergyDefinitionContractTest`: registry registered in `AttunedRegistries`;
  every shipped `synergy/*.json` parses; members reference real registered Focus
  ids; behavior ids (if present) exist in `AttunedFocusBehaviors`.
- `SynergiesRuntimeContractTest` (source-grep): `init()` wired after `Pacts.init()`;
  `% 20` throttle; `SynergyResolver.activeConfluences` used; cleanup registrations
  present; first-discovery uses `sawOnboarding`/`markOnboarding`.
- `ConfluenceDiscoveryContractTest`: attachment registered persistent + sync +
  copyOnDeath; journal chapter registered and drift guard updated; lang keys exist.
- `FocusDataConsistencyTest`-style sweep: every Confluence has name/desc lang keys
  and a `docs/reference.md` row.

---

## Risk

**Low.** The budget core is untouched (Confluences are free). The only novel
surface is a new registry + a Pacts-shaped runtime, both with strong precedents.
The behavior-bearing Confluences carry the usual mixin-free risk profile (they use
`FocusBehavior` hooks, no `@At` strings). Main effort is content authoring + tuning,
not new systems.

---

## Cross-cutting checklist (gated by `verify_repository.py`)

- `CHANGELOG.md`: `### Added` Confluences bullets under the release heading.
- `docs/reference.md`: a "Confluences" section (mechanic + table) and a row in any
  systems overview; keep code/lang/docs in sync (audits found drift repeatedly).
- `README.md`: one line on the Confluence meta if a headline feature.
- Lang: ~3 keys per Confluence + chapter/journal keys (~30 total for v1).
- Modrinth gallery: one optional "Confluences" panel; not required.

---

## Phasing (each slice independently shippable)

| Phase | Contents | Risk |
|---|---|---|
| 1 | `SynergyResolver` + registry + codec + 4 pure-`modifiers` Confluences + chat announce | low |
| 2 | Discovery attachment + journal "Confluences" chapter + first-discovery fanfare | low |
| 3 | `behavior`-bearing Confluences (the 4 above needing conditional logic) | low-med |
| 4 | Readout indicator + `previewOf` build hint | low (pure client) |

Phase 1 alone is a real, satisfying feature.

---

## Explicit non-goals

- No budget cost or new budget interaction (keep the core untouched).
- No Confluence-specific items, loot, or textures.
- No build-defining power spikes — modest effects only.
- No more than 3 members per Confluence in v1 (resolver supports N; authoring
  stays to pairs and a few triads for legibility).
- No in-game "Confluence editor" — datapack JSON is the authoring surface.
