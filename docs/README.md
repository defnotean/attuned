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
- Most combat Foci carry an **affinity**: Holy, Fury, Bastion, or Zephyr. Active
  affinity Foci that share one lane commit the build; mixed active affinities
  become **Discord**, which is risky but still valid.
- Three matching active affinities can awaken a **Pact**. A fully committed,
  high-capacity build can reach **Apex** during combat. Full mixed-affinity and
  neutral builds have their own Apex paths: **Maelstrom** and **Stillpoint**.
- **Attunement Shards** raise capacity at an Altar. **Shard Fragments** are a
  smaller loot reward; four craft into one shard.
- The **Attunement Journal** is a crafted in-game guide and opens like a normal
  readable book.
- The **Focus Reliquary** is a craftable bag that stores spare Foci, shows your
  six equipped slots beside it, and saves named loadout **builds** you can
  re-apply with one click.
- Creative inventory content is split into **Attuned: Affinity Foci** and
  **Attuned: Utility Foci** so combat builds and neutral tools are easier to
  scan.

That is the whole idea: a Focus is a trade-off, never a free bonus.

## What do you want to do?

| I want to…                                   | Read this |
|-----------------------------------------------|-----------|
| Add a brand-new Focus                         | [adding-a-focus.md](adding-a-focus.md) |
| Change a Focus's cost, affinity, stats, text  | [adding-a-focus.md](adding-a-focus.md#changing-an-existing-focus) |
| Look up a field, an attribute, a tuning number| [reference.md](reference.md) |
| Check commands, recipes, and setup files      | [reference.md](reference.md#commands) |

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
