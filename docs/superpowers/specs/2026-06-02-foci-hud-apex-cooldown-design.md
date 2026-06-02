# Foci HUD, Apex Status, and Ability Cooldown Design

## Goal

Add a dedicated, always-available Foci HUD so players can see the actual Foci they have equipped during gameplay. The HUD should make active versus dormant Foci obvious, expose Apex/resonance status clearly, and show when the one active ability Focus is on cooldown.

The current combat HUD resonance strip is too small and visually easy to miss. This design gives build state its own readable surface while keeping matchup/target information in the existing combat HUD.

## Player-Facing Decisions

- Use a new compact **Foci HUD**, separate from the existing combat affinity HUD.
- Show the HUD by default whenever the player has any Focus equipped.
- Add a separate **Toggle Foci HUD** config setting and keybind.
- Arrange equipped Foci as a vertical priority column matching the inventory Focus slot order.
- Render the actual equipped item stack icons in the HUD.
- Active/in-budget Foci glow; dormant or over-budget Foci remain visible but dimmed.
- Put the currently selected active ability Focus in a larger well at the top.
- Show ability cooldown as a ring around that larger ability Focus well.
- Show Apex/resonance as a readable bar with the current capstone icon/status, using the existing Apex icon assets where possible.
- Use generated HUD concept art only as visual direction; the shipped HUD art should be a compact, Minecraft-readable PNG frame with no baked-in item icons or text.

## Architecture

### Client HUD

Create a new `FociHud` client layer registered through Fabric's HUD element API. It owns the equipped-Foci column, ability well, cooldown ring, Apex icon, and resonance bar.

`CombatHud` remains responsible for player/target stance, matchup links, and PvP target affinity markers. Shared placement logic should prevent `FociHud` and `CombatHud` from overlapping: when both are visible, the Foci HUD gets the primary hotbar sidecar position and Combat HUD can dock to the opposite side or the next available sidecar slot.

The HUD should render item stacks through Minecraft's item renderer, not generated stand-ins, so the displayed Foci are always accurate to the player's inventory.

### Ability State

Add a small shared ability-state layer for the one active ability Focus:

- Finds the first active Focus whose behavior opts into the Focus Ability key.
- Exposes the selected slot/item for the HUD.
- Tracks cooldown remaining and total duration server-side.
- Sends a clientbound status payload when the selected ability or cooldown changes.

Refactor active ability behaviors so cooldowns are not hidden entirely inside each behavior. Behaviors should expose their cooldown duration and report whether the ability actually fired. Voidstep should only start cooldown after a successful blink; Smoke should start cooldown when the smoke burst is released.

### Assets

Generate a unique Attuned HUD frame concept in the chosen style: dark deepslate/obsidian, amethyst glow, restrained gold/copper trim, and compact pixel-art bevels. Convert that concept into a practical shipped frame asset under `assets/attuned/textures/gui/`.

The frame asset must not include item icons, cooldown fill, Apex text, or resonance fill. Those are rendered dynamically so the HUD reflects actual gameplay state.

## Data Flow

1. Client presses the Focus Ability key.
2. Server resolves the first active ability-capable Focus.
3. Server checks its cooldown.
4. If ready, server calls the behavior and starts cooldown only if it succeeds.
5. Server sends cooldown/ability status to the client.
6. Client animates the cooldown ring from the received remaining/total ticks.
7. Client renders equipped Focus stacks, active/dormant state, Apex icon, and resonance from synced/local player state.

## Error Handling

- If no ability Focus is active, the ability well is empty/disabled and no cooldown ring is shown.
- If the server sends cooldown state for a slot that no longer contains that Focus, the client drops the stale status.
- If a capstone is unlocked but resonance is below Apex threshold, the Apex icon is dimmed and the bar shows progress toward waking it.
- If the player hides the Foci HUD, ability and Apex behavior still work normally.

## Testing

Add contract coverage for:

- Separate Foci HUD config key and keybind text.
- `FociHud` registration as its own HUD layer.
- Actual item stack rendering for equipped Foci.
- Active/dormant visual branches.
- Cooldown ring driven by server-provided remaining/total ticks.
- Ability cooldowns centralized enough for the HUD to read.
- No overlap between Foci HUD and Combat HUD sidecar placement.
- HUD art asset dimensions and presence.

Run full `test` and `build` after implementation.
