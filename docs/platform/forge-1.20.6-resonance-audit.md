# Forge 1.20.6 Resonance HUD Audit

Date: 2026-06-25

Branch: `forge/1.20.6`

## Runtime Data Path

1. Server combat changes write resonance through `Resonance.set(...)`.
2. `AttunedAttachments.syncToClient(player)` sends `AttunementStatePayload` to
   the owning client.
3. `AttunementStateClient` receives that payload on the client thread and calls
   `AttunedAttachments.applySyncedState(local, payload)`.
4. The receiver now calls `AttunementReadout.invalidate(local)` immediately
   after applying the payload.
5. `CombatHud` and `FociHud` render through the branch-local
   `HudRenderCallback` shim, which registers callbacks into Forge
   `AddGuiOverlayLayersEvent`.
6. `AttunementReadout.displayResonance(player)` rebuilds the readout after the
   invalidation and eases the visible bar toward the newly synced value.

## Root Cause Found

The Forge 1.20.6 HUD render bridge was already present, and the branch already
had an explicit owner-client `AttunementStatePayload`. The freshness gap was
inside the client receiver: applying synced attachment state did not invalidate
the per-player `AttunementReadout` cache, so a payload received between client
ticks could leave the HUD reading the old same-tick snapshot.

The fix adds `AttunementReadout.invalidate(Player)` and calls it after
`AttunedAttachments.applySyncedState(local, payload)`. The branch also pins
ForgeGradle to `7.0.29`; the previous dynamic plugin range began resolving to
`7.0.30`, which was not in the branch's verification metadata.

## Verification

Focused contract:

```powershell
.\gradlew.bat test --tests dev.attuned.client.ForgeStateSyncHudContractTest --no-daemon
```

Expected result: pass. Current observed result: pass on 2026-06-25.

Full branch build:

```powershell
.\gradlew.bat build --no-daemon
```

Expected result: pass. Current observed result: pass on 2026-06-25.

Release hardening still needs a hands-on combat smoke: equip a committed build,
damage a valid hostile or PvP target, confirm `/attuned status` resonance
changes, and confirm the Combat/Foci HUD resonance bar fills on the Forge
client.
