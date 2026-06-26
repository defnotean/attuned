# NeoForge 26.1.2 Loader Checklist

Branch: `neoforge/26.1.2`

Minecraft: `26.1.2`

Java: `25`

NeoForge coordinate: `net.neoforged:neoforge:26.1.2.76`

ModDevGradle plugin: `net.neoforged.moddev:2.0.141`

Original Fabric coordinates:
- `net.fabricmc:fabric-loader:0.19.3`
- `net.fabricmc.fabric-api:fabric-api:0.152.1+26.1.2`

## Loader scaffold

- [x] Replace Fabric Loom with ModDevGradle.
- [x] Add `src/main/resources/META-INF/neoforge.mods.toml`.
- [x] Move loader metadata to NeoForge dependency ranges.
- [x] Declare both existing mixin configs with `[[mixins]]`.
- [x] Tag Modrinth uploads as `neoforge`.
- [x] Add NeoForge `@Mod` entrypoint wiring with constructor-injected `IEventBus`.
- [x] Replace direct Fabric entrypoint APIs with static init and client-only reflective init.
- [x] Add NeoForge-backed local Fabric API shims for registry/events/networking/client hooks.
- [x] Add a codec-backed persistent player-state bridge for attachment-shaped Attuned state.
- [x] Add explicit owner-client `AttunementStatePayload` sync and client readout invalidation because the local attachment `syncWith(...)` API is only a compatibility shim on NeoForge.
- [x] Register NeoForge payloads through `RegisterPayloadHandlersEvent`/`PayloadRegistrar`.
- [x] Route key mappings, screens, resource reload listeners, and HUD layers through NeoForge client events.
- [x] Keep Lootr optional in `neoforge.mods.toml`.

## Verification

- `python -m pytest tests/test_neoforge_scaffold_contract.py -q`: pending after port edits.
- `.\gradlew.bat compileJava compileClientJava --no-daemon --stacktrace`: pending.
- `.\gradlew.bat build --no-daemon --stacktrace`: pending.
- `.\gradlew.bat runServer --no-daemon --stacktrace`: pending if compile/build pass.

## Functionality audit

Evidence command:

```powershell
rg -n "fabric|Fabric|ClientModInitializer|ModInitializer|ServerPlayNetworking|ClientPlayNetworking|PayloadTypeRegistry|HudRenderCallback|ServerTickEvents|ServerLivingEntityEvents|AttachmentRegistry|KeyBindingHelper|CommandRegistrationCallback" src\main\java src\client\java src\main\resources src\client\resources
```

Current status: branch scaffold candidate pending compile/build verification.

### player attunement state persistence/sync

Implemented:
- `src/main/java/dev/attuned/attunement/AttunedAttachments.java` keeps Attuned's Fabric-shaped attachment boundary while reading/writing through Attuned-owned `get(...)`/`set(...)` helpers.
- Server-side writes encode persistent attachments with each attachment's codec into player persistent data under the attachment id.
- Server-side owner writes, login, and respawn send `AttunementStatePayload` explicitly because the local attachment `syncWith(...)` API is only a compatibility shim on NeoForge.
- The client registers `AttunementStateClient`, applies the mirrored state, and invalidates `AttunementReadout` so same-tick HUD reads do not use stale resonance.

### resonance HUD fill

Implemented:
- Server combat/tick shims route Fabric-shaped callbacks through NeoForge event buses.
- Client HUD registration routes Fabric-shaped HUD element calls through NeoForge GUI layer events.
- Owner state sync feeds the same client `AttunementStatePayload` path used by Fabric, so Combat/Foci resonance bars are not dependent on an unsynced server-only attachment cache.

### Focus ability networking

Implemented:
- `PayloadTypeRegistry` stores Fabric-shaped payload registrations and emits them during NeoForge `RegisterPayloadHandlersEvent`.
- Client sends use NeoForge packet distribution.
- Server sends use NeoForge packet distribution.
- Server/client receiver contexts are adapted through NeoForge payload contexts.

### server/client init events

Implemented:
- `Attuned` is annotated with `@Mod(Attuned.MOD_ID)` and accepts NeoForge's injected `IEventBus`.
- `AttunedClient` exposes a static `init()` and no longer implements Fabric's client initializer.
- Dynamic registries use NeoForge data pack registry events.
- Command, lifecycle, connection, loot, entity, block interaction, and tick callbacks compile through NeoForge-backed compatibility shims.
