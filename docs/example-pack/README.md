# Attuned example pack — eight Foci, no Java

This is a complete, working example of authoring Foci with **only** a datapack and
a resource pack — no mod code, no JAR rebuild. It ships eight Foci that show the
core authoring lanes:

| Focus | Item it reuses | Lane it demonstrates |
|-------|----------------|----------------------|
| **Warding Focus** | `minecraft:turtle_helmet` | Attribute-only — `modifiers` grant +2 armor. |
| **Tide Focus** | `minecraft:nautilus_shell` | A **shipped** behavior — points `behavior` at `attuned:tide` (water breathing). |
| **Warmth Focus** | `minecraft:magma_cream` | A **palette** behavior the pack itself defines — Fire Resistance while in the Nether. |
| **Canopy Focus** | `minecraft:oak_leaves` | A **block-context palette** behavior — Slow Falling while near leaves. |
| **Route Focus** | `minecraft:compass` | An **item-use palette** behavior — Speed after using a filled map. |
| **Rescue Focus** | `minecraft:heart_of_the_sea` | A **party-assist palette** behavior — help a drowning Circle member on cooldown. |
| **Brand Focus** | `minecraft:blaze_powder` | A **marked-target palette** behavior — Weakness after two charged hits. |
| **Windrose Focus** | `minecraft:compass` | A **navigation palette** behavior — direction to the latest accepted Circle ping. |

Every Focus reuses an existing vanilla item, so nothing here mints a new item (a
datapack cannot). The display name, lore, and effect text come from the resource
pack's `lang` file.

See [`docs/authoring-foci.md`](../authoring-foci.md) for the full walkthrough of
what each field means.

## Install it

This pack is split the way Minecraft expects: gameplay data is a **datapack**, and
the names/lore are a **resource pack**.

1. **Datapack** — copy the `data/` folder into a new folder inside your world's
   `datapacks/` directory, e.g.:

   ```
   <your world>/datapacks/attuned-example/data/...
   ```

   Add a `pack.mcmeta` next to that `data/` folder if your launcher needs one (any
   recent `pack_format` works; the pack only ships JSON the game already accepts).

2. **Resource pack** — copy the `assets/` folder into a resource pack folder under
   `.minecraft/resourcepacks/attuned-example/`, add a `pack.mcmeta`, and enable it
   in **Options > Resource Packs**. Without it the eight Foci show raw translation
   keys instead of their names.

3. In game, run `/reload` to load the datapack, then run **`/attuned validate`**.
   It checks every Focus and palette file in this pack one by one and prints a
   per-file pass/fail summary. The eight example Foci should report a clean pass.

4. Find the Foci in loot, or grab the vanilla items directly, equip one in a Focus
   slot, and check the tooltip and effect.

## Make it your own

- Change a Focus by editing its file in `data/example/attuned/focus/` and running
  `/reload`.
- Point a Focus at a different item by changing its `item` field to any registered
  item id (vanilla or another mod's).
- Rename it by editing `assets/example/lang/en_us.json` (asset changes need the
  resource pack reloaded — press F3+T or re-enter the world).
