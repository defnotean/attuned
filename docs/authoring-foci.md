# Authoring Foci in a datapack (no Java)

This guide is for **modpack and content authors** who want to add their own Foci to
a world or a published pack **without** writing Java or rebuilding the mod. Foci are
already data: a Focus is a small JSON file in a datapack, and its name and look come
from a resource pack.

> **Two audiences, two guides.** If you are a *contributor* editing the Attuned JAR
> itself (registering a brand-new item, writing a behavior class), follow
> [`adding-a-focus.md`](adding-a-focus.md) instead. *This* guide is for authors who
> ship Foci as a datapack on top of an unmodified Attuned.

There is a complete, runnable example in [`example-pack/`](example-pack/) — three
Foci showing each lane below. Copy it and adapt.

---

## What a datapack author can and cannot do

A datapack **can**:

- Define new Foci that reuse **any registered item** — a vanilla item, or an
  existing `attuned:*_focus` item, one of the blank `attuned:custom_focus_1`
  through `attuned:custom_focus_8` skinnable Focus items, or another mod's item.
- Grant **attribute modifiers** (armor, speed, max health, attack damage, …) — pure
  data, fully supported.
- Reference any **shipped behavior** id (the special powers the mod ships).
- Add an optional `aspect` such as `attuned:tide` or `attuned:umbral` so the Focus
  participates in the visible counter wheel tooltip without changing affinity math.
- Define **palette behaviors** — parameterized passive behaviors built from data, no
  code. See [the behavior palette](#define-a-palette-behavior).

A datapack **cannot** mint a brand-new *item* (Minecraft registers items only from
mod code). To give a Focus a bespoke item identity you reuse an existing item and
re-skin its name/lore/texture in a resource pack.

---

## Where the files go

| What | Where |
|------|-------|
| Focus definition | `data/<your_namespace>/attuned/focus/<name>.json` |
| Palette behavior (optional) | `data/<your_namespace>/attuned/focus_behavior/<id>.json` |
| Name / lore / effect text | `assets/<your_namespace>/lang/en_us.json` (resource pack) |
| Custom model / texture (optional) | `assets/<your_namespace>/models/item/…` + `textures/item/…` (resource pack) |

`<your_namespace>` is yours — e.g. `mypack`. Use it everywhere so your content never
collides with `attuned:` or another pack.

---

## Lane 1 — an attribute-only Focus

The simplest Focus. Reuse an item and grant stat modifiers.

`data/mypack/attuned/focus/example_warding_focus.json`:

```json
{
  "item": "minecraft:turtle_helmet",
  "cost": 4,
  "affinity": "bastion",
  "aspect": "attuned:bastion",
  "modifiers": [
    { "attribute": "minecraft:armor", "amount": 2, "operation": "add_value" }
  ]
}
```

- `item` — **required.** Any registered item id. This Focus *is* that item, so the
  item still keeps its own name unless you re-skin it (see [Naming](#name-it)).
- `cost` — attunement points it uses, usually 2–6. Defaults to 1.
- `affinity` — `fury`, `bastion`, `zephyr`, or `holy`. Leave it out for a neutral
  utility Focus.
- `aspect` — optional visible counter identity (`attuned:fury`, `attuned:bastion`,
  `attuned:zephyr`, `attuned:holy`, `attuned:tide`, `attuned:forge`,
  `attuned:verdant`, or `attuned:umbral`). Tooltips show what it counters and
  what counters it; affinity/Pact/Discord behavior is unchanged.
- `unique` — set `true` to allow only one copy active at a time.
- `faction` — optional story/family tag such as `attuned:unseen`. Add a
  `faction.<namespace>.<path>` lang key if you invent a new one.
- `modifiers` — the stat changes. See
  [reference.md#attribute-modifiers](reference.md#attribute-modifiers) for the full
  attribute list and what each `operation` means.

The framework applies and removes attribute modifiers for you while the Focus is
active. No code, no cleanup.

## Lane 2 — reuse a shipped behavior

Point `behavior` at any behavior id Attuned ships. The full list is in
[reference.md](reference.md). Example: water breathing via `attuned:tide`.

`data/mypack/attuned/focus/example_tide_focus.json`:

```json
{
  "item": "minecraft:nautilus_shell",
  "cost": 3,
  "affinity": "zephyr",
  "behavior": "attuned:tide"
}
```

A Focus may carry both `modifiers` and a `behavior`.

## Lane 3 — define a palette behavior

The **behavior palette** lets you author a *new* passive behavior from data, then
reference it like any other behavior id. v1 ships one palette type,
`attuned:conditional_mob_effect`: keep a mob effect refreshed while a
[condition](reference.md#conditions) holds.

`data/mypack/attuned/focus_behavior/warmth.json`:

```json
{
  "type": "attuned:conditional_mob_effect",
  "effect": "minecraft:fire_resistance",
  "amplifier": 0,
  "duration_ticks": 60,
  "refresh_ticks": 20,
  "condition": { "condition": "in_biome_tag", "tag": "minecraft:is_nether" }
}
```

`data/mypack/attuned/focus/example_warmth_focus.json`:

```json
{
  "item": "minecraft:magma_cream",
  "cost": 3,
  "behavior": "mypack:warmth"
}
```

The behavior id is `<your_namespace>:<file_name>` — here `mypack:warmth`, matching
the file `focus_behavior/warmth.json`. Resolution is **code-first-then-data**: if a
code behavior is registered under that id it wins, otherwise Attuned builds the
behavior from your palette file. See
[reference.md#behavior-palette](reference.md#behavior-palette-no-java)
for every palette type and condition, the field tables, and the value ranges.

---

## Name it

A Focus that reuses `minecraft:turtle_helmet` shows up as "Turtle Shell" unless you
re-skin it. Override its display name, the two italic lore lines, and the green
effect line in a **resource pack** lang file:

`assets/mypack/lang/en_us.json`:

```json
{
  "item.minecraft.turtle_helmet": "Warding Focus"
}
```

> Re-skinning a vanilla item this way renames **every** copy of that item, not just
> the ones acting as a Focus. If that matters, reuse a less common item, or wait for
> the generic skinnable Focus-item pool (a later phase — see the design doc).

Attuned auto-appends `<key>.lore`, `<key>.lore2`, and `<key>.effect` to any Focus
tooltip, so add those too:

```json
{
  "item.minecraft.turtle_helmet": "Warding Focus",
  "item.minecraft.turtle_helmet.lore": "Old shell, older patience.",
  "item.minecraft.turtle_helmet.lore2": "It takes the blow so you need not.",
  "item.minecraft.turtle_helmet.effect": "Grants +2 armor."
}
```

To change the item's **texture/model** too, override its model files in the resource
pack the normal vanilla way (`assets/minecraft/models/item/...`).

---

## Validate it

In a running game (operator / game-master permission), run:

```
/attuned validate
```

It walks every `focus/*.json` and `focus_behavior/*.json` **file by file** and
reports a pass/fail summary:

- Each Focus's `item` must resolve to a real registered item.
- Each `behavior` id must resolve code-first-then-data.
- Each modifier's `attribute` id must resolve to a real attribute.
- A **missing display-name lang key** is reported as a **warning**, not a failure —
  the Focus still works, it just shows a raw key until you add the lang entry.

A pack with only warnings still passes; the command prints the warning count so you
can clean them up. `/reload` re-reads datapack files, so you can edit and re-validate
without restarting.

---

## Current shipped authoring support

This guide covers the no-Java lanes that work today. Attuned now ships the blank
`attuned:custom_focus_1` through `attuned:custom_focus_8` item pool for packs that
need bespoke resource-pack art without registering a new item, the passive
behavior palette (`conditional_mob_effect`, `on_hit_effect`, `periodic_effect`,
and `attribute_while`), and optional `aspect` metadata for the counter-wheel
tooltip. Active-ability authoring (cooldowns and Focus Ability key handlers) is
still code-only.
