# Attuned

An original accessory mod for **Minecraft 26.1.2** (Fabric).

Attuned is built around an *attunement economy*. You equip accessory items
called **Foci** into six slots in your inventory, but every player has a limited
**attunement capacity**, and each Focus costs points to keep active. Go over
budget and your lowest-priority Foci fall dormant — so a build is a set of
deliberate choices, not a pile of every bonus at once.

## Features

- **Six Focus slots** built into the inventory and creative-inventory screens,
  with drag-and-drop and shift-click to equip.
- **22 Foci** spanning mobility, defense, combat, and utility.
- **Attunement capacity that grows with you.** You start with a little, and it
  deepens as you play: reaching milestones — your first step into the Nether,
  felling the Wither or the Ender Dragon — permanently raises it, and
  **Attunement Shards** bound at an Altar raise it further.
- **The Attunement Altar** — a craftable home block where you bind shards into
  capacity. It glows with the affinity you are attuned to, and reads out your
  attunement when you use it.
- **Affinities & Discord.** Combat-leaning Foci each carry one of three
  affinities — Fury, Bastion, Zephyr — in a rock-paper-scissors cycle. Commit to
  a single affinity for counter-combat advantage and an **Apex** capstone, or
  run a clashing mix and embrace **Discord**: every Focus stays active, but you
  deal and take extra damage — a glass cannon.
- **Counter-combat** — affinity matchups scale damage, with a coloured spark on
  every advantage and disadvantage so the cycle is legible. Thornward reflects a
  share of every hit; Leech drains life from what you strike.
- **Active abilities** — a Focus can carry a triggered power, fired with the
  Focus-ability keybind. Voidstep blinks you forward.
- **Configurable** — `config/attuned.json` exposes starting capacity, the cap,
  loot rates, and cooldowns.
- **Data-driven** — Foci and mob affinities are defined in datapack JSON and
  tags, so resource and data packs can add or retune their own.

## Affinity cycle

Fury beats Bastion · Bastion beats Zephyr · Zephyr beats Fury

## Adding or changing content

Want to add a Focus or tweak the numbers? The [`docs/`](docs/) folder has
plain-English, step-by-step guides written for non-experts — start with
[docs/README.md](docs/README.md).

## Requirements

- Minecraft 26.1.2
- Fabric Loader 0.19.2 or newer
- Fabric API

## License

Released under the MIT License — see [LICENSE](LICENSE).
