# NeoForge 1.21.1 Loader Checklist

Branch: `neoforge/1.21.1`

Minecraft: `1.21.1`

Java: `21`

NeoForge coordinate: `net.neoforged:neoforge:21.1.234`

ModDevGradle plugin: `net.neoforged.moddev:2.0.141`

Original Fabric coordinates:
- `net.fabricmc:fabric-loader:0.16.14`
- `net.fabricmc.fabric-api:fabric-api:0.116.5+1.21.1`

## Loader scaffold

- [x] Replace Fabric Loom with ModDevGradle.
- [x] Add `src/main/resources/META-INF/neoforge.mods.toml`.
- [x] Move loader metadata to NeoForge dependency ranges.
- [x] Declare both existing mixin configs with `[[mixins]]`.
- [x] Tag Modrinth uploads as `neoforge`.
- [x] Resolve NeoForge Gradle task graph and resources.
- [x] Add NeoForge `@Mod` entrypoint wiring with constructor-injected `IEventBus`.
- [x] Replace direct Fabric entrypoint APIs with static init and client-only reflective init.
- [x] Add NeoForge-backed local Fabric API shims for registry/events/networking/client hooks.
- [x] Add a codec-backed persistent player-state bridge for attachment-shaped Attuned state.
- [x] Add explicit owner-client `AttunementStatePayload` sync and client readout invalidation because the local attachment `syncWith(...)` API is only a compatibility shim on NeoForge.
- [x] Register NeoForge payloads through `RegisterPayloadHandlersEvent`/`PayloadRegistrar`.
- [x] Route key mappings, screens, resource reload listeners, and HUD layers through NeoForge client events.
- [x] Keep Lootr optional in `neoforge.mods.toml`.

## Verification

- `python -m pytest tests/test_neoforge_scaffold_contract.py -q`: passed, 6 tests.
- `.\gradlew.bat --write-verification-metadata sha256 tasks --all`: passed after adding ModDevGradle verification metadata.
- `.\gradlew.bat tasks --all`: passed and listed NeoForge mod development tasks including `runClient`, `runServer`, `runData`, `createMinecraftArtifacts`, and `neoForgeIdeSync`.
- `.\gradlew.bat processResources`: passed and generated `build/resources/main/META-INF/neoforge.mods.toml` with concrete version ranges.
- `.\gradlew.bat dependencies --configuration compileClasspath --write-locks`: passed and persisted `net.neoforged:neoforge:21.1.234` for `compileClasspath`.
- `.\gradlew.bat dependencies --write-locks --no-daemon`: passed after removing stale Fabric/Loom lock state from NeoForge configurations.
- `.\gradlew.bat compileJava --no-daemon --stacktrace`: passed.
- `.\gradlew.bat compileClientJava --no-daemon --stacktrace`: passed.
- `.\gradlew.bat build --no-daemon --stacktrace`: passed.
- `.\gradlew.bat runServer --no-daemon --stacktrace`: reached `Attuned initializing`, generated a world, and reached server `Done (4.336s)` on 2026-06-25 after the explicit owner-state payload registration. The Gradle task later reported failure only because the running server process had to be stopped after startup when stdin was unavailable.
- Pending: dedicated NeoForge client smoke.
- Pending: hands-on combat HUD smoke: damage a valid target, confirm `/attuned status` resonance changes, and confirm the Combat/Foci resonance bar fills.

## Functionality audit

Evidence command:

```powershell
rg -n "fabric|Fabric|ClientModInitializer|ModInitializer|ServerPlayNetworking|ClientPlayNetworking|PayloadTypeRegistry|HudRenderCallback|ServerTickEvents|ServerLivingEntityEvents|AttachmentRegistry|KeyBindingHelper|CommandRegistrationCallback" src\main\java src\client\java src\main\resources src\client\resources
```

Current status: branch build/server-smoke candidate. The branch now compiles, builds, and reaches a running dedicated NeoForge server with a generated world. Release is still blocked on client smoke and hands-on HUD verification because server startup does not prove that client event bus wiring, networking, and HUD timing all behave correctly under a launched NeoForge client/server.

### player attunement state persistence/sync

Implemented:
- `src/main/java/dev/attuned/attunement/AttunedAttachments.java` keeps Attuned's Fabric-shaped attachment boundary but now reads/writes through Attuned-owned `get(...)`/`set(...)` helpers.
- Server-side writes encode persistent attachments with each attachment's codec into player persistent data under the attachment id.
- Server-side first reads decode persisted values before defaulting.
- Server-side owner writes, login, and respawn now send `AttunementStatePayload` explicitly because the local attachment `syncWith(...)` API is only a compatibility shim on NeoForge.
- The client registers `AttunementStateClient`, applies the mirrored state, and invalidates `AttunementReadout` so same-tick HUD reads do not use stale resonance.
- Player cleanup and server stop cleanup clear the in-memory cache.

Still needs runtime proof:
- Confirm target-only owner state reaches the NeoForge client after login, respawn, menu sync, and reconnect.
- Confirm resonance persists across disconnect/reconnect on an actual NeoForge server.

### resonance HUD fill

Implemented:
- Server combat/tick shims now use NeoForge living damage/death and server tick events.
- Client HUD registration now routes Fabric-shaped `HudRenderCallback`/HUD element calls through `RegisterGuiLayersEvent`.
- `VanillaHudElements.HOTBAR` maps to `VanillaGuiLayers.HOTBAR`.
- Owner state sync now feeds the same client `AttunementStatePayload` path used by Fabric, so the Combat/Foci resonance bars are no longer dependent on an unsynced server-only attachment cache.

Still needs runtime proof:
- In a launched NeoForge client, damage a valid target, verify the server resonance value changes, and verify both Combat/Foci resonance bars visually fill without a stale readout.

### Focus ability networking

Implemented:
- `PayloadTypeRegistry` stores Fabric-shaped payload registrations and emits them during NeoForge `RegisterPayloadHandlersEvent`.
- Client sends use `PacketDistributor.sendToServer`.
- Server sends use `PacketDistributor.sendToPlayer`.
- Server/client receiver contexts are adapted through NeoForge `IPayloadContext`.

Still needs runtime proof:
- Exercise Focus ability, inspect, updraft, and owner-state payloads in a launched client/server.

### journal/satchel/preset networking

Implemented:
- Menu and journal payloads go through the same NeoForge-backed payload registry and packet distributor shims.
- Screen registration moved from private vanilla `MenuScreens.register` calls to NeoForge `RegisterMenuScreensEvent`.

Still needs runtime proof:
- Open Altar, Reweaving, Focus Reliquary, Grand Focus Reliquary, and Attunement Journal in client smoke.
- Save/apply/delete/import a build and confirm server-side validation still rejects impossible requests.

### client HUD registration

Implemented:
- Key mappings use `RegisterKeyMappingsEvent`.
- HUD layers use `RegisterGuiLayersEvent`.
- Tooltip callbacks use NeoForge player tooltip events through the copied compatibility shim.
- Client reload listeners use `RegisterClientReloadListenersEvent`.

Still needs runtime proof:
- Confirm keybinds appear in Controls, HUD renders, tooltips render, and client config loads in a launched NeoForge client.

### server/client init events

Implemented:
- `Attuned` is annotated with `@Mod(Attuned.MOD_ID)` and accepts NeoForge's injected `IEventBus`.
- `AttunedClient` exposes a static `init()` and no longer implements Fabric's client initializer.
- Dynamic registries use NeoForge `DataPackRegistryEvent.NewRegistry`.
- Command, lifecycle, connection, loot, entity, block interaction, and tick callbacks compile through NeoForge-backed compatibility shims.

Runtime proof:
- Dedicated server startup reaches Attuned initialization completion and world `Done`.

Still needs runtime proof:
- Launch client, reload tags/datapacks, run `/attuned`, and scan logs for event registration or payload failures.
