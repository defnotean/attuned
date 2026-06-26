# Quilt 1.19.2 Loader Checklist

Branch: `quilt/1.19.2`

Minecraft: `1.19.2`

Java: `17`

Quilt Loader coordinate: `org.quiltmc:quilt-loader:0.17.8`

Quilt Loom plugin: `org.quiltmc.loom:1.15.1`

Fabric API compatibility coordinate: `net.fabricmc.fabric-api:fabric-api:0.77.0+1.19.2`

Rejected QFAPI coordinate: `org.quiltmc.quilted-fabric-api:quilted-fabric-api:4.0.0-beta.26+0.72.0-1.19.2`

Rejected deprecated QFAPI coordinate: `org.quiltmc.quilted-fabric-api:quilted-fabric-api-deprecated:4.0.0-beta.26+0.72.0-1.19.2`

Original Fabric coordinates:
- `net.fabricmc:fabric-loader:0.19.3`
- `net.fabricmc.fabric-api:fabric-api:0.77.0+1.19.2`

## Loader Scaffold

- [x] Replace Fabric Loom with Quilt Loom.
- [x] Add Quilt Maven to plugin and project repositories.
- [x] Replace Fabric Loader with Quilt Loader.
- [x] Keep the gameplay API surface Fabric-shaped for this 1.19.2 branch and run it through Fabric API compatibility on Quilt.
- [x] Compile against Fabric API and launch the original aggregate Fabric API jar through `loader.addMods`; Quilt Loom's remapped aggregate strips the nested `jars` list, and the split common/client dev graph leaves some Fabric module ids unsatisfied under Quilt Loader 0.17.x.
- [x] Add `src/main/resources/quilt.mod.json`.
- [x] Remove `src/main/resources/fabric.mod.json` from the native Quilt branch.
- [x] Add Quilt common entrypoint adapter `dev.attuned.quilt.AttunedQuilt`.
- [x] Add Quilt client entrypoint adapter `dev.attuned.quilt.AttunedQuiltClient`.
- [x] Declare Quilt Loader 0.17.x Minecraft hook entrypoints `main` and `client`.
- [x] Declare both existing mixin configs in `quilt.mod.json` using the string-list form accepted by Quilt Loader 0.17.x.
- [x] Add an Attuned-owned access widener for the 1.19.2 `MenuType` constructor and screen registration methods, so compile does not rely on third-party access wideners.
- [x] Tag Modrinth uploads as `quilt`.
- [x] Declare Fabric API as the Modrinth dependency.
- [x] Keep Lootr optional by omitting it from Quilt `depends`; Quilt Loader strict metadata rejects Fabric-style `suggests`.
- [x] Align the loader baseline with the official Quilt 1.19.2 template.
- [x] Keep the empty client mixin toggle in `src/client/resources` for branch contracts while also exposing a main-resource copy for Quilt Loader 0.17.x server metadata parsing; jar packaging excludes the duplicate.

## QFAPI Audit Result

QFAPI was tried first because it is the native Quilt-facing Fabric API compatibility layer. It is not the reliable runtime choice for this old 1.19.2 branch:

- `maven.modrinth:qsl:4.0.0-beta.26+0.72.0-1.19.2` remaps into a dev jar whose top-level `quilt.mod.json` loses the nested `jars` list. Quilt Loader then sees the aggregate mod but not the module mods it depends on.
- Quilt Maven split QFAPI modules avoid the aggregate jar problem, but Quilt Loader 0.17.x does not satisfy several Fabric-id dependencies through QFAPI `provides` metadata during dedicated server launch.
- Quilt Loader 0.29.x rejects old 1.19.2-era QFAPI metadata such as `recommends` under strict parsing. It also rejects the old access widener placement that Quilt Loader 0.17.x accepts.

Fabric API's remapped aggregate shows the same stripped nested-jar metadata, so the dev launcher passes the original Fabric API aggregate jar to Quilt Loader with `loader.addMods`.

Decision: use native Quilt Loader metadata and entrypoints, but depend on Fabric API `0.77.0+1.19.2` for this branch's Fabric-shaped compatibility API surface. Revisit QSL/QFAPI on newer Minecraft targets where current Quilt APIs and metadata are available.

## Verification

- `python -m pytest tests/test_quilt_scaffold_contract.py -q`: passed, 4 tests.
- `.\gradlew.bat dependencies --write-locks --no-daemon --stacktrace`: passed.
- `.\gradlew.bat compileJava --no-daemon --stacktrace`: covered by the passing `build`.
- `.\gradlew.bat compileClientJava --no-daemon --stacktrace`: covered by the passing `build`.
- `.\gradlew.bat build --no-daemon --stacktrace`: passed.
- `.\gradlew.bat runServer --no-daemon --stacktrace`: passed. Quilt Loader loaded Attuned with Fabric API compatibility modules, logged `Attuned initializing`, and reached server `Done (23.861s)`.
- Note: the dedicated dev server emits Quilt/Fabric resource-pack warnings for missing `build/classes/java/main/assets` and `build/classes/java/main/data`. The resources remain present under `build/resources/main`, the server finishes loading recipes and advancements, and this should be rechecked during the client smoke before treating it as release-blocking.
- Pending: dedicated Quilt client smoke.
- Pending: hands-on combat HUD smoke.

## Functionality Audit

Current status: native Quilt scaffold candidate with server runtime proof. This branch intentionally keeps the Fabric-shaped gameplay/event/networking API surface and runs it through Fabric API compatibility, because the audited 1.19.2 QFAPI line is not stable in this dev/runtime setup.

### Initialization

Implemented:
- Quilt's `main` entrypoint delegates to Attuned's existing common initializer.
- Quilt's `client` entrypoint delegates to Attuned's existing client initializer.

Verified:
- Dedicated server startup shows Attuned initialization and reaches server `Done`.

Still needs runtime proof:
- Client startup must reach the title screen without entrypoint, mixin, or dependency failures.

### Player State And Resonance HUD

Implemented:
- This branch keeps the 1.19.2 branch-local state sync payload path used before Fabric data attachments.
- Fabric API supplies the networking/event classes that this branch already uses.

Still needs runtime proof:
- Join a Quilt server, damage a valid target, confirm `/attuned status` resonance changes, and verify the Combat/Foci resonance bars fill on the client.
- Disconnect/reconnect and respawn to confirm owner state and resonance do not stale.

### Menus, Keybinds, HUDs, And Tooltips

Implemented:
- Existing Fabric-shaped screen, keybind, tooltip, and HUD hooks remain compiled against Fabric API compatibility.
- Existing client mixin config remains declared with `environment: client`.

Still needs runtime proof:
- Open Altar, Reweaving, Focus Reliquary, Grand Focus Reliquary, and Attunement Journal.
- Exercise save/apply/delete/import build payloads.
- Confirm keybinds appear in Controls and HUD overlays render at the expected anchors.

### Loot And Datapacks

Implemented:
- Existing Fabric loot and dynamic registry compatibility surfaces remain compiled against Fabric API compatibility.

Still needs runtime proof:
- Load a datapack-backed world, reload resources, and scan logs for registry sync or loot callback failures.
- Open vanilla loot targets and confirm Attuned additions do not replace vanilla contents.
