# Offshore Harpoon Design

## Goal

Add **The Offshore** as a new Attuned faction and ship its first signature tool: the
**Harpoon Focus**. Offshore is the faction of people who leave after the harbor
lights stop helping. Seafarers carry memory shore-to-shore; Offshore salvage
what sank, map storms, and bargain with things below the waves.

The first implementation should prove the faction through a temporary custom
trident. Players cannot craft or keep this weapon. It appears through a Focus
ability, behaves like a trident for a short window, and disappears when its
time is up.

## Faction

Add `attuned:offshore` with the translation **The Offshore**.

Tone:

- Deep-water salvage, storms, wrecks, undertow, and bargains below the waves.
- Riskier and more active than Seafarers, but still not a raw combat faction.
- Foci can include utility with danger: underwater traversal, storm bonuses,
  wreck/ore/container sensing, drowned or guardian counterplay, and cooldown
  active abilities.

Relationship to Seafarers:

- Seafarers are safe return, harbor routes, fishing, and guidance.
- Offshore is what happens past the safe route: salvage lines, storm maps, and
  temporary tools that should not stay in the player's hands forever.

## First Focus: Harpoon Focus

Add `harpoon_focus` as the first Offshore Focus.

Initial behavior:

- Faction: `attuned:offshore`
- Affinity: neutral
- Cost: `3`
- Active ability: summons a temporary named trident, **Offshore Harpoon**
- Cooldown: 60 seconds
- Duration: 30 seconds
- The ability should fail without starting cooldown when the player already has
  an active Offshore Harpoon from this Focus.

The Harpoon Focus should use the existing single-active-ability system:

- It implements `hasActiveAbility()`.
- It exposes `abilityCooldownTicks()`.
- It returns `true` from `onAbility` only after successfully creating the
  temporary harpoon.

## Temporary Trident Rules

The temporary weapon should be a marked vanilla trident stack with a custom name,
custom model data or equivalent model predicate/data component, and server-owned
expiration state.

Rules:

- It cannot be crafted.
- It cannot become permanent.
- It disappears after its time limit.
- It disappears if the Harpoon Focus deactivates.
- It disappears when the player disconnects.
- It should not overwrite a valuable item in the player's hand. If the main hand
  is occupied, put the harpoon in the first available inventory slot; if the
  inventory is full, fail the ability without cooldown.
- If the player drops it, it should still expire.
- If the player throws it, the thrown trident should not become a permanent
  pickup. Prefer making it vanish on hit or after its expiration window.

First pass can keep the thrown effect simple:

- The temporary trident behaves like a normal trident while active.
- It vanishes on timeout.
- Later Offshore Foci can add pull, salvage pulse, or storm behavior.

## Visual Asset

Use the generated concept as direction, not as a final in-game texture.

Visual identity:

- 3D item model, not a flat recolor.
- Three-pronged trident/harpoon silhouette with a stronger central barb.
- Weathered driftwood grip.
- Oxidized copper bands.
- Dark iron or salt-stained metal.
- Teal sea-glass prongs.
- Amethyst or violet Focus core.
- Subtle stormwater glow.

Implementation target:

- A project-local item texture for the Offshore Harpoon.
- A 3D item model JSON that uses cuboids/elements and displays correctly in hand,
  inventory, and when thrown if feasible.
- The temporary stack should visually route to this model through the modern
  Minecraft item model mechanism available in this repo's target version.

If thrown-entity model routing cannot safely be customized in the first pass,
the held/inventory trident must still use the custom 3D model and the thrown
entity may use vanilla visuals until a second pass.

## Data And Text

Add language entries:

- `faction.attuned.offshore`: `The Offshore`
- `item.attuned.harpoon_focus`: `Harpoon Focus`
- Harpoon Focus lore and effect text
- Custom trident name: `Offshore Harpoon`

Suggested copy:

- Lore 1: `A line cast past the last harbor light.`
- Lore 2: `What answers from below is borrowed, never kept.`
- Effect: `Press the Focus Ability key to summon a temporary Offshore Harpoon.`

Update docs and journal/reference text enough that Offshore is understandable
beside Seafarers, without adding a large lore chapter unless needed.

## Testing

Add source-level and behavior-contract tests for:

- Offshore faction translation exists.
- Harpoon Focus data exists and uses `attuned:offshore`.
- Harpoon Focus is neutral and has a behavior id.
- Harpoon behavior opts into the active ability key.
- Harpoon behavior exposes cooldown and returns boolean success.
- The temporary trident uses a marker and expiration logic.
- The ability fails without cooldown when no inventory space is available.
- The temporary trident is removed on timeout/deactivation/disconnect.
- No recipe is added for the temporary trident.
- Asset contracts cover the custom model and texture paths.

Run:

- `.\gradlew.bat test --no-daemon`
- `.\gradlew.bat build --no-daemon`

## Out Of Scope For First Pass

- A full Offshore Pact.
- Multiple Offshore Foci.
- Complex pull/drag projectile physics.
- Permanent custom trident crafting.
- Loot-table acquisition changes beyond adding the Focus to normal Focus pools,
  unless existing loot tests require explicit faction weighting.
