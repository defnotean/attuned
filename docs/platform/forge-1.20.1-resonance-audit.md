# Forge 1.20.1 Resonance HUD Audit

Date: 2026-06-25

Branch: `forge/1.20.1`

## Runtime Data Path

1. Server combat changes call `Resonance.set(player, clamped)`.
2. `Resonance.set` writes through `AttunedAttachments.setResonance(player, clamped)`.
3. `AttunedAttachments.setResonance` stores the clamped value in the branch-local
   player state map and sends `AttunedStatePayload(state(serverPlayer).toTag())`
   to the owner client.
4. `AttunedStateClientSync` receives `AttunedStatePayload`, applies it with
   `AttunedAttachments.applySync(local, payload.tag())`, and invalidates
   `AttunementReadout` for the local player.
5. `CombatHud` and `FociHud` read `AttunementReadout.displayResonance(player)`.
6. `displayResonance` reads the synced resonance from
   `AttunementReadout.cached(player).resonance()` and eases the visible bar
   toward that value.

## Root Cause Found

The server mutation and owner-sync packet path were present, but the Forge
1.20.1 client HUD bridge was incomplete. `CombatHud`, `FociHud`, and `PartyHud`
registered through the Fabric-style `HudRenderCallback.EVENT`, while the
Forge shim only stored callbacks. It did not subscribe to Forge's GUI overlay
render event, so the registered HUD layers were not invoked by Forge rendering.

The fix bridges `HudRenderCallback` to `RenderGuiEvent.Post` and
dispatches each registered callback with the event `GuiGraphics` and partial
tick. A second targeted fix invalidates the same-tick `AttunementReadout`
cache after `AttunedStateClientSync` applies synced state, so packets received
between client ticks can be reflected by the next HUD render.

## Producer Boundary Checked

The combat producer path is packaged for this branch:

- `Resonance.init()` registers `AfterDamageCallback.EVENT.register(Resonance::afterDamage)`.
- `LivingEntityHurtMixin` invokes `AfterDamageCallback.EVENT.invoker().afterDamage(...)`
  after successful server-side damage.
- `attuned.mixins.json` lists `LivingEntityHurtMixin`.
- `META-INF/mods.toml` lists `attuned.mixins.json`.

If a runtime still shows no server-side resonance gain, the next check is a
source-run mixin application log or a direct combat smoke around
`LivingEntity.hurt`.

## Verification

Focused contracts:

```powershell
.\gradlew.bat test --tests dev.attuned.client.ForgeHudBridgeContractTest --tests dev.attuned.client.ForgeResonancePipelineContractTest
```

Expected result: pass. Current observed result: pass on 2026-06-25.

Full branch build:

```powershell
.\gradlew.bat build --no-daemon
```

Expected result: pass. Current observed result: pass on 2026-06-25.

Release hardening still needs:

- A hands-on combat smoke: equip a committed build, damage a valid hostile or
  PvP target, confirm `/attuned status` resonance changes, and confirm the
  Combat/Foci HUD resonance bar fills on the Forge client.
