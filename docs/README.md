# Attuned — Content Guide

This folder explains how to add and change content in **Attuned** without being
a Minecraft modding expert. If you can edit a text file and copy-paste, you can
add a Focus.

## Attuned in one minute

- Players equip accessory items called **Foci** in six **Focus slots** built
  into the inventory screen.
- Every player has an **attunement budget** (their *capacity*). Every Focus has
  a **cost**. The combined cost of equipped Foci has to fit inside the budget.
- Go over budget and your lowest-priority Foci fall **dormant** — still equipped,
  but switched off. Slot order, top to bottom, is the priority order.
- Most combat Foci carry an **affinity**: Fury, Bastion, Zephyr, Holy, Tide,
  Forge, Verdant, or Umbral. Each affinity counters two others and is countered
  by two; tooltips show only the affinity name, while the Attunement Journal
  carries the full counter web so Focus descriptions stay clean. Active affinity
  Foci that share one lane commit the build; mixed active affinities become
  **Discord**, which is risky but still valid.
- Three matching active affinities can awaken a **Pact**. A fully committed,
  high-capacity build can reach **Apex** during combat. Full mixed-affinity and
  neutral builds have their own Apex paths: **Maelstrom** and **Stillpoint**.
- **Attunement Shards** raise capacity at an Altar. **Shard Fragments** are a
  smaller loot reward; four craft into one shard.
- The **Attunement Journal** is a crafted in-game guide and opens like a normal
  readable book.
- The **Focus Reliquary** is a craftable bag that stores spare Foci, shows your
  six equipped slots beside it, and saves named loadout **builds** you can
  re-apply with one click — or **Share**/**Import** via clipboard.
- The **Altar of Reweaving** rerolls Foci: classic three-Focus reweave,
  **Tempering**, and **Affinity Loom** (same-affinity reroll for escalating
  Attunement Shards).
- **Pact Trials** are long-term pact goals; finishing one permanently unlocks a
  Tier 4 passive while that pact is awake again.
- Creative inventory content is split into **Fury & Bastion**, **Zephyr &
  Holy**, **Tide & Forge**, **Verdant & Umbral**, and **Utility & Tools** tabs
  so the full roster is easy to scan.
- Current release headline: **Attuned 1.6.0 — Echoes & Accords** adds Pact
  Trials (Tier 4), Affinity Loom reweaving, build sharing, faction set bonuses
  for Tideborn/Forgebound/Wildroot/Umbral, pact tacticals on the Focus Ability
  key, combat config tuning (Discord damage, resonance in config), resonant surge
  interactivity, onboarding hints, and HUD/journal surfacing — on top of the
  eightfold roster from 1.5.0.

That is the whole idea: a Focus is a trade-off, never a free bonus.

## What do you want to do?

| I want to…                                   | Read this |
|-----------------------------------------------|-----------|
| Add a brand-new Focus                         | [adding-a-focus.md](adding-a-focus.md) |
| Change a Focus's cost, affinity, stats, text  | [adding-a-focus.md](adding-a-focus.md#changing-an-existing-focus) |
| Look up a field, an attribute, a tuning number| [reference.md](reference.md) |
| Check commands, recipes, and setup files      | [reference.md](reference.md#commands) |
| Prepare a newer/older Minecraft version port  | [versioning/minecraft-version-migration.md](versioning/minecraft-version-migration.md) |

## The two kinds of Focus

1. **Stat Foci** — they only change numbers: more armor, faster movement, extra
   hearts. These need **no code at all**, only text files. This is the easy path
   and most Foci should be this kind.
2. **Power Foci** — they do something a number cannot: teleport, call lightning,
   cancel a death. These need **one small Java file** on top of the text files.

The [adding-a-focus](adding-a-focus.md) guide covers both, clearly labelled, so
you can stop after the easy path if that is all you need.

## A rule that saves you every time

Every Focus is a handful of small files that **all share one name**, written in
`lower_case_with_underscores` — for example `stoneskin_focus`. Pick that name
first, then use the exact same spelling in every file. Most mistakes are just a
name that does not match.
