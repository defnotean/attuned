# Attuned — Datapack-Defined Foci (Author-Extensible Content) Design

Target: Minecraft 26.1.2, Fabric, Java 25. A platform feature, not a content drop;
slots into any minor and is the natural companion to the Confluences design
(`2026-06-12-focus-confluences-design.md`) — both make the `attuned:` namespace a
stable authoring surface.

---

## Theme — turn modpack authors into the content team

Every roadmap adds Foci by hand, in Java. The single biggest *reach* lever is to
let datapack and modpack authors add their **own** Foci without writing Java. This
is the mechanic that made Origins/Apoli explode: a clean data API turns every pack
that includes Attuned into a content multiplier, and turns the mod from "a fixed
set of 61 Foci" into "a Focus *engine*."

### What already works (do not rebuild it)

A precise gap analysis first, because the foundation is better than it looks:

- **Foci are already datapack JSON.** `FocusDefinition.CODEC` loads
  `data/<ns>/attuned/focus/<name>.json` with `cost`, `unique`, `affinity`,
  `faction`, `modifiers`, `behavior`.
- **A Focus can already point at *any* registered item** —
  `BuiltInRegistries.ITEM.holderByNameCodec()`. An author can make
  `minecraft:amethyst_shard` or a *different mod's* item into a Focus today.
- **Attribute modifiers are already fully data-driven.** `ModifierEntry` accepts
  any `minecraft:attribute`, any operation, any finite amount. A Focus granting
  +speed / +armor / +max-health / +attack is **pure JSON right now**, no code.

So the honest framing: ~40% of "custom Focus" desire is *already* achievable and
simply undocumented. The two real walls are below.

### Wall 1 — behaviors are a fixed code palette

`AttunedFocusBehaviors.init()` registers ~40 `FocusBehavior` **singletons** by id
(`tide`, `lantern`, `kilnward`, …). The `behavior` field is an `Optional<Identifier>`
into that map. An author can *reference* a shipped behavior but cannot **author a
new one** or **parameterize** an existing one (Delver's Haste amplifier, Tide's
duration, etc. are all hardcoded). Yet most shipped behaviors are the *same shape*:
"refresh MobEffect X while condition Y holds." Delver=Haste, Nightgaze=NightVision,
Tide=WaterBreathing, Rainstep/Hearth/Mossheart/Kilnward = effect-under-condition.

### Wall 2 — bespoke items need a JAR

`FocusDefinition` needs a `Holder<Item>`, and Minecraft items must be code-registered
(`AttunedContent.registerFocus("swift_focus")`). A pure datapack cannot mint a brand
-new item with its own texture and name. An author can reuse an existing item, but
gets that item's name/model — no bespoke identity.

---

## The three pillars

### Pillar A — a parameterized behavior palette (the high-value core)

Add a **datapack registry of behavior instances** that produce `FocusBehavior`s from
JSON, resolved by the *same* `behavior` id the focus already uses. This keeps
`FocusDefinition` **completely unchanged** and keeps all 40 shipped code behaviors
working as-is; it only adds a second *source* of named behaviors.

- New registry files at `data/<ns>/attuned/focus_behavior/<id>.json`, each naming a
  palette **type** plus its parameters:

```json
// data/mypack/attuned/focus_behavior/frostward.json
{
  "type": "attuned:conditional_mob_effect",
  "effect": "minecraft:resistance",
  "amplifier": 0,
  "duration_ticks": 60,
  "refresh_ticks": 20,
  "condition": { "type": "attuned:in_biome_tag", "tag": "minecraft:is_cold" }
}
```

- `FocusDefinition.behavior` resolution becomes: look up the id in the **code**
  registry first (the 40 shipped singletons win and never change), then the **data**
  registry. A pack references `"behavior": "mypack:frostward"` from its focus JSON.

- **Palette types** (each a small, audited `FocusBehavior` factory; ship a starter
  set, grow over releases):
  - `attuned:conditional_mob_effect` — refresh an effect while a **condition**
    holds. This single type subsumes a dozen shipped behaviors and is the 80% case.
  - `attuned:on_hit_effect` — apply an effect to the victim / self on a melee hit
    (gated charge %, hostile-only — reuse `AttunedCombat.isChargedDirectMelee` and
    `CombatTargets` guards).
  - `attuned:periodic_effect` — a flat timed effect refreshed on a cadence
    (unconditional buffs like a gentle Regeneration).
  - `attuned:attribute_while` — an attribute modifier applied only while a condition
    holds (conditional version of `ModifierEntry`).
  - **Conditions** are their own small composable registry/codec: `in_rain`,
    `underwater`, `low_light`, `bright_light`, `on_block_tag`, `in_biome_tag`,
    `sneaking`, `near_block` — modelled on the predicates the shipped behaviors
    already compute (`HarborlightBehavior.nearWater`, `MossheartBehavior` ground
    check, `KilnwardBehavior` heat predicate). Factor those into reusable
    `FocusCondition` predicates so code and data behaviors share one implementation.

- **Active-ability palette is explicitly v2.** Ability-key behaviors (Voidstep,
  Smoke, Harpoon) own the single Focus-Ability slot, have cooldowns, and sync state
  — too much surface for v1. v1 palette is passive only; `hasActiveAbility()` stays
  code-only.

This is the part that needs the most care and the most tests, because it is new
runtime surface — but it is **mixin-free** (pure `FocusBehavior` hooks), so it
avoids the repo's highest-risk failure mode (wrong `@At` strings crashing at load).

### Pillar B — the bespoke-item on-ramp

Two complementary answers, ship the first, document the second:

1. **A pool of generic, resource-pack-skinnable Focus items.** The mod registers N
   blank focus items (`attuned:custom_focus_1..N`, e.g. N=16) with a neutral default
   model. A pack points its `focus/<name>.json` at `attuned:custom_focus_3`, then
   supplies the **name, lore, and texture via a resource pack** (lang +
   `models/item/custom_focus_3` override). The author gets a real, distinct Focus
   with zero Java. The HUD/tooltip/Reliquary already render any registered Focus, so
   these work everywhere automatically.
2. **Document reuse of existing items** for authors who don't mind borrowing an
   item's identity (the already-works path).

> Note: a single component-driven "one item, data-defined name/model" item is
> tempting but fights vanilla's model system (model is keyed by item id, not by a
> component). The N-blank-items pool is the pragmatic, vanilla-aligned answer; revisit
> a component model only if item-model-from-component lands upstream.

### Pillar C — a stable, versioned, validated public contract

A data API is only a multiplier if authors can trust it and debug it.

- **Freeze the public surface.** `api/focus/FocusDefinition`, `ModifierEntry`,
  `FocusBehavior`, the new palette types, and the JSON shapes become a documented,
  semver-tracked contract. Keep them in the existing `dev.attuned.api.focus` package
  (already separated from internals — good sign it was built for this).
- **Extend `/attuned validate`** (the command already exists per `docs/reference.md`:
  "Checks shipped Focus registrations, datapack definitions, and behavior ids") to
  also validate **author** packs: every `focus/*.json` resolves its item + behavior +
  attributes; every `focus_behavior/*.json` names a known palette type with valid
  params; missing lang keys are warned (the raw-key footgun). Make it report
  file-by-file so an author runs one command and sees every problem.
- **Ship a worked example datapack** under `docs/` (a 2–3 Focus mini-pack using a
  generic item + a `conditional_mob_effect` behavior + an attribute Focus) and an
  `docs/authoring-foci.md` walkthrough (extend the existing `docs/adding-a-focus.md`,
  which today targets *contributors* editing the JAR, not external authors).

---

## Worked author example (end state)

A pack author, JAR-free, creates "Frostward Focus":

```
mypack/
  data/mypack/attuned/focus/frostward_focus.json          # cost/affinity/item/behavior
  data/mypack/attuned/focus_behavior/frostward.json       # conditional_mob_effect
  assets/mypack/lang/en_us.json                           # name + .lore/.lore2/.effect
  assets/mypack/models/item/custom_focus_3.json           # skin the generic item
  assets/mypack/textures/item/custom_focus_3.png
```

points `frostward_focus.json` at `"item": "attuned:custom_focus_3"` and
`"behavior": "mypack:frostward"`, runs `/attuned validate`, and the Focus appears in
loot, the Reliquary, tooltips, the HUD, and Confluence authoring — all with no code.

---

## Tests

- `FocusBehaviorPaletteTest` (behavioral where possible): each palette type's
  param codec round-trips; condition predicates evaluate correctly against modelled
  inputs (keep the predicate logic Minecraft-free where feasible, like
  `BudgetResolver`).
- `PaletteResolutionContractTest`: `behavior` id resolves code-first then data; a
  data behavior id reaches a constructed `FocusBehavior`; unknown type rejected with
  a clear error.
- `GenericFocusItemContractTest`: N blank items registered, accepted in the creative
  tab, default model/texture present (`verify_repository.py` PNG gate).
- `ValidateCommandContractTest`: `/attuned validate` reports per-file results,
  catches a bad behavior type, a missing item, and a missing lang key.
- `ExampleDatapackContractTest`: the shipped example pack passes `validate` and every
  referenced id resolves (guards against the docs rotting).
- Backward-compat: every existing `focus/*.json` still loads unchanged; the 40 code
  behaviors still resolve (pin a few).

---

## Risk

**Medium.** Higher than Confluences because it adds new runtime behavior-construction
surface and a public contract that is costly to change later. Mitigations:

- Start the palette **small** (the 3–4 types above) and passive-only; grow it across
  releases once the shape is proven. A small, correct palette beats a big leaky one.
- The budget core is untouched (custom Foci flow through the exact same
  `FocusDefinition` → `BudgetResolver` path; nothing special-cases them).
- Mixin-free — no `@At`-string risk.
- Versioning discipline: treat the JSON shapes as API from day one; additive-only
  changes; a `pack_format`-style or explicit `"attuned_api": 1` marker on author
  files so future breaking changes can be gated.

---

## Cross-cutting checklist (gated by `verify_repository.py`)

- `CHANGELOG.md`: `### Added` — datapack Focus authoring (palette + generic items +
  validate).
- `docs/authoring-foci.md` (new) + update `docs/adding-a-focus.md` to cross-link
  contributor vs author paths; `docs/reference.md` palette-type + condition tables.
- `README.md`: a "Make your own Foci" section — this is a headline selling point.
- Lang: names/lore for the N generic items' *defaults* (so they're not raw keys out
  of the box), palette-related tooltips if any.

---

## Phasing (each slice independently shippable)

| Phase | Contents | Risk |
|---|---|---|
| 1 | **Document the already-works path**: existing-item Foci + attribute-only Foci, `/attuned validate` for author packs, `docs/authoring-foci.md`, example pack | low — mostly docs + validation |
| 2 | Generic skinnable Focus item pool (Pillar B.1) | low |
| 3 | Behavior palette v1: `conditional_mob_effect` + condition registry (covers the 80% case) | med — new runtime |
| 4 | Palette breadth: `on_hit_effect`, `periodic_effect`, `attribute_while` | med |
| 5 | (later) active-ability palette, ability cooldown/sync surface | higher — defer |

Phase 1 alone is a real release note ("you can now build Foci in a datapack") and
de-risks everything after it by surfacing what authors actually ask for first.

---

## Explicit non-goals (v1)

- No active-ability authoring (cooldowns, sync, the single Focus-Ability slot).
- No datapack-registered *items* (vanilla can't; the generic-item pool is the answer).
- No new affinities or factions from datapack beyond the existing optional fields
  (the four-affinity cycle is load-bearing and pinned; authors tag into it, not
  past it).
- No Java SPI / mod-facing extension point — this is for **datapack/resource-pack**
  authors; other *mods* can already register items and call the same API directly.
- No bespoke combat procs outside the audited palette (Thornward/Leech/Needle-class
  effects stay code-only for balance and safety).
