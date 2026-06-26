# NeoForge 1.20.6 compatibility audit

## Branch inventory

- Worktree: `.worktrees/neoforge-minecraft-1.20.6`
- Branch: `neoforge/1.20.6`
- Minecraft: `1.20.6`
- Java: `21`
- Current loader coordinates: `net.fabricmc:fabric-loader:0.19.3`
- Current Fabric API coordinates: `net.fabricmc.fabric-api:fabric-api:0.100.8+1.20.6`
- NeoForge candidate: `net.neoforged:neoforge:20.6.139`
- NeoGradle candidate: `net.neoforged.moddev` `2.0.141`

## Compatibility result

NeoForge is viable for this Minecraft line: the NeoForged Maven releases API
contains `net.neoforged:neoforge:20.6.139`, matching the branch's Minecraft
version and Java 21 baseline. This branch was checked as the additional
compatibility branch, while the first buildable scaffold proof of concept lives
in `neoforge/1.21.1`.

No Gradle files are changed in this branch yet. The branch is still Fabric/Loom
based and keeps its existing `fabric.mod.json` until the 1.21.1 loader shell
has a runtime-port pattern to copy back.

## Verification evidence

- `git rev-parse --abbrev-ref HEAD`: `neoforge/1.20.6`
- `gradle.properties`: `minecraft_version=1.20.6`, `java_version=21`,
  `loader_version=0.19.3`, `fabric_api_version=0.100.8+1.20.6`
- NeoForged Maven releases query for prefix `20.6.`: latest
  `net.neoforged:neoforge:20.6.139`
- Fabric runtime audit:
  `rg -n "fabric|Fabric|ClientModInitializer|ModInitializer|ServerPlayNetworking|ClientPlayNetworking|PayloadTypeRegistry|HudRenderCallback|ServerTickEvents|ServerLivingEntityEvents|AttachmentRegistry|KeyBindingHelper|CommandRegistrationCallback" src/main/java src/client/java src/main/resources src/client/resources`

## Loader scaffold checklist

- [ ] Replace Fabric Loom plugin with `net.neoforged.moddev`.
- [ ] Replace Fabric repositories/settings with NeoForged Maven and Gradle
      Plugin Portal resolution.
- [ ] Replace `fabric.mod.json` with `META-INF/neoforge.mods.toml`.
- [ ] Change Modrinth metadata from Fabric/Fabric API to NeoForge.
- [ ] Add an `@Mod("attuned")` entrypoint and move common/client setup to
      NeoForge event registration.
- [ ] Port runtime Fabric API boundaries before expecting `compileJava` to pass.

## Functionality audit

### Player attunement state persistence and sync

`src/main/java/dev/attuned/attunement/AttunedAttachments.java` is Fabric-bound:
it imports `AttachmentRegistry`, `AttachmentType`, `ServerPlayerEvents`,
`ServerPlayConnectionEvents`, and `ServerPlayNetworking`. This branch also has
an explicit `AttunementStatePayload` client sync path sent from
`AttunedAttachments` and received in
`src/client/java/dev/attuned/client/AttunementStateClient.java`.

NeoForge needs to replace this with its attachment/data attachment API and
packet registration/distribution while preserving persistent fields, respawn
copy behavior, login/world-change sync, and target-only state updates.

### Resonance HUD fill

Server resonance updates live in
`src/main/java/dev/attuned/combat/Resonance.java`, which uses Fabric
`ServerLivingEntityEvents` and `ServerTickEvents`. The HUD rendering path lives
in `src/client/java/dev/attuned/client/hud/FociHud.java` and
`src/client/java/dev/attuned/client/hud/CombatHud.java`, both registered with
Fabric `HudRenderCallback`.

NeoForge needs event-bus replacements for damage/death/tick handling plus a
client GUI overlay/render event. The HUD cannot be validated until the
attunement/resonance sync path above is ported.

### Focus ability networking

`src/main/java/dev/attuned/network/AttunedNetworking.java` registers C2S
ability, inspect, and updraft payloads through Fabric `PayloadTypeRegistry` and
`ServerPlayNetworking`. Client senders are in `AttunedKeybinds`,
`AffinityInspectClient`, and `UpdraftLiftClient`. Server-to-client ability
status updates are sent from `FocusAbilityState` through
`ServerPlayNetworking`.

NeoForge needs payload registration and packet distribution for the same C2S
and S2C contracts, including server-thread handling and `canSend` capability
checks where the client currently depends on Fabric helpers.

### Journal, satchel, preset, altar, and reweaving networking

`JournalNetworking`, `PresetNetworking`, `AltarNetworking`, and
`ReweavingNetworking` all use Fabric `PayloadTypeRegistry` and
`ServerPlayNetworking`. Their screen clients send through Fabric
`ClientPlayNetworking`.

NeoForge needs equivalent payload registration/distribution. Menu construction
also needs review because `AltarMenuType` documents reliance on Fabric's
menu-api access widening of the vanilla `MenuType` constructor.

### Client HUD registration, keybinds, tooltips, and world overlays

`FociHud` and `CombatHud` use Fabric `HudRenderCallback`; `AttunedKeybinds`
uses `KeyBindingHelper` and `ClientTickEvents`; `AttunedTooltips` uses
`ItemTooltipCallback`; `TremorOreOutlines` uses Fabric world-render and client
connection/network callbacks.

NeoForge needs client setup and event-bus registrations for key mappings,
tooltips, GUI overlays, world render overlays, client ticks, and client
connection lifecycle.

### Server and client init events

`Attuned` implements Fabric `ModInitializer` and registers Fabric dynamic
registries. `AttunedClient` implements `ClientModInitializer`. Server feature
classes register Fabric lifecycle, tick, command, loot, player, block-use,
block-break, damage, and death callbacks throughout `src/main/java`.

NeoForge needs an `@Mod("attuned")` entrypoint, mod/event bus registration,
NeoForge registry/data-pack hooks, command registration, server lifecycle
events, loot modification events, player interaction events, and living
entity damage/death replacements before runtime behavior can compile or run.
