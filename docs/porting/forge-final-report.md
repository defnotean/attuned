# Forge Port Final Report

Date: 2026-06-24

This report summarizes the first-pass Fabric-to-Forge migration across the
supported Minecraft version branches in this repository. It is paired with the
version matrix in `forge-version-matrix.md`.

## Source And Target Branches

| Source Fabric branch | Target Forge branch | Minecraft | Commit pushed |
| --- | --- | ---: | --- |
| `origin/fabric/minecraft-1.18.2` | `origin/forge/1.18.2` | 1.18.2 | `c257ce7d` |
| `origin/fabric/minecraft-1.19.2` | `origin/forge/1.19.2` | 1.19.2 | `3ae2c5bd` |
| `origin/fabric/minecraft-1.19.4` | `origin/forge/1.19.4` | 1.19.4 | `8088cc37` |
| `origin/fabric/minecraft-1.20.1` | `origin/forge/1.20.1` | 1.20.1 | `eb04f1ba` |
| `origin/fabric/minecraft-1.20.6` | `origin/forge/1.20.6` | 1.20.6 | `1be44926` |
| `origin/fabric/minecraft-1.21.1` | `origin/forge/1.21.1` | 1.21.1 | `96df1e96` |
| `origin/fabric/minecraft-1.21.11` | `origin/forge/1.21.11` | 1.21.11 | `a65d50d7` |
| `origin/fabric/minecraft-26.1.2` | `origin/forge/26.1.2` | 26.1.2 | `15ce8d05` |
| `origin/fabric/minecraft-26.2` | `origin/forge/26.2` | 26.2 | `4e93e2e0` |

No detected Fabric version branch was skipped.

## Coordinator Branch

| Branch | Status | Purpose |
| --- | --- | --- |
| `origin/forge/coordinator` | Pushed | Version matrix, first-pass report, final report, and client-smoke evidence. |

## Forge Tooling Choices

| Minecraft | Java | Forge | ForgeGradle | Mappings |
| ---: | ---: | --- | --- | --- |
| 1.18.2 | 17 | 40.3.12 | `[6.0,6.2)` | Official Mojang |
| 1.19.2 | 17 | 43.5.2 | `[6.0,6.2)` | Official Mojang |
| 1.19.4 | 17 | 45.4.3 | `[6.0,6.2)` | Official Mojang |
| 1.20.1 | 17 | 47.4.20 | `[6.0,6.2)` | Official Mojang |
| 1.20.6 | 21 | 50.2.8 | `[7.0.3,8)` | Official Mojang |
| 1.21.1 | 21 | 52.1.14 | `[7.0.3,8)` | Official Mojang |
| 1.21.11 | 21 | 61.1.8 | `[7.0.3,8)` | Official Mojang |
| 26.1.2 | 25 | 64.0.10 | `[7.0.17,8)` | Forge MDK default |
| 26.2 | 25 | 65.0.0 | `[7.0.17,8)` | Forge MDK default |

## Worker-Style Branch Reports

Each row is the required first-pass worker report for its version branch.

| Branch worked on | Files changed | Migration patterns used | Build status | Remaining errors | Risks or manual testing needed |
| --- | ---: | --- | --- | --- | --- |
| `forge/1.18.2` | 89 files, +2706/-576 | ForgeGradle 6 metadata, `mods.toml`, `@Mod`, deferred registration bridge, legacy Fabric API facades, legacy networking facades, Ebbstride fall-damage behavior fallback, `@Redirect` fishing hook, client compatibility shim | `.\gradlew.bat build --no-daemon` passed; `runGameTestServer` passed; `runClient` startup smoke passed | None blocking build, server launch, or client startup | Interactive world-join playthrough, packet-flow playthrough, older custom trident/harpoon visuals |
| `forge/1.19.2` | 89 files, +2727/-576 | Same legacy pattern as 1.18.2 plus 1.19.2 command/event facades | `.\gradlew.bat build --no-daemon` passed; `runGameTestServer` passed; `runClient` startup smoke passed | None blocking build, server launch, or client startup | Interactive world-join playthrough, packet-flow playthrough, older custom trident/harpoon visuals |
| `forge/1.19.4` | 89 files, +2580/-533 | Legacy ForgeGradle 6 port, creative-tab event registration, legacy networking/rendering facades, Ebbstride fallback | `.\gradlew.bat build --no-daemon` passed; `runGameTestServer` passed; `runClient` startup smoke passed | None blocking build, server launch, or client startup | Interactive world-join playthrough, packet-flow playthrough, older custom trident/harpoon visuals |
| `forge/1.20.1` | 78 files, +2599/-586 | Legacy ForgeGradle 6 port, 1.20.1 creative-tab and menu registration, Ebbstride fallback, Forge metadata replacement | `.\gradlew.bat build --no-daemon` passed; `runGameTestServer` passed; `runClient` startup smoke passed | None blocking build, server launch, or client startup | Interactive world-join playthrough and packet-flow playthrough |
| `forge/1.20.6` | 86 files, +2765/-618 | ForgeGradle 7 metadata, modern payload facades, attachment facade, swim behavior fallback for missing water movement attribute, fall-damage attribute retained | `.\gradlew.bat build --no-daemon` passed; `runServer` passed; `runClient` startup smoke passed | Modern game-test dev task did not receive named test functions in this workspace | Interactive world-join playthrough and packet-flow playthrough |
| `forge/1.21.1` | 85 files, +2769/-675 | ForgeGradle 7 metadata, modern attachment/network facades, newer water/fall attributes with 1.21.1 `minecraft:generic.*` ids | `.\gradlew.bat build --no-daemon` passed; `runServer` passed; `runClient` startup smoke passed | None blocking build, server launch, or client startup | Interactive world-join playthrough and packet-flow playthrough |
| `forge/1.21.11` | 78 files, +3410/-1601 | ForgeGradle 7 metadata, Java compatibility/mixin minVersion correction, modern client event/render facades, modern attribute ids | `.\gradlew.bat build --no-daemon` passed; `runServer` passed; `runClient` startup smoke passed | Optional Windows Netty/Realms dev-environment log noise only | Interactive world-join playthrough and packet-flow playthrough |
| `forge/26.1.2` | 73 files, +3280/-636 | Modern ForgeGradle 7/Java 25 port, attachment bridge, payload registry facade, Forge creative tab and registry bridge | `.\gradlew.bat build --no-daemon` passed; `runServer` passed; `runClient` startup smoke passed | Optional Windows Netty/Realms dev-environment log noise only | Interactive world-join playthrough, packet-flow playthrough, attachment persistence/reconnect parity |
| `forge/26.2` | 73 files, +2477/-438 | Reference Forge port: ForgeGradle 7/Java 25, `@Mod`, deferred registration, payload/attachment/event facades, source-run manifest mixin config, Java compatibility cap | `.\gradlew.bat test --no-daemon`, `build`, `runGameTestServer`, `runServer`, `runClient` startup smoke, and manual in-world Focus smoke passed | Optional Windows Netty/Realms dev-environment log noise only | Wider packet-flow playthrough, attachment persistence/reconnect parity |

## Major Migration Patterns

- Replaced Fabric Loom builds with ForgeGradle builds for each Minecraft line.
- Replaced `fabric.mod.json` with `META-INF/mods.toml`, `pack.mcmeta`, and
  manifest `MixinConfigs` entries.
- Converted loader startup to a Forge `@Mod(Attuned.MOD_ID)` entrypoint while
  preserving the existing common `Attuned` and client `AttunedClient` logic.
- Added a small `ForgeRegistration` bridge so item, block, menu, component,
  and creative-tab registration is deferred through Forge registry timing.
- Added focused Fabric API compatibility facades where keeping the shared
  source shape preserved behavior without large rewrites.
- Kept mixins only where the behavior was not cleanly covered by a Forge event.
- Replaced the fragile fishing `ModifyArgs` hook with a constructor redirect.
- Preserved registry ids, Focus ids, data paths, localization, generated
  textures, and Focus behavior ids.
- Preserved version-specific attribute behavior instead of forcing one data
  shape across all branches.

## Features Confirmed Working

- Forge metadata is present on every target branch.
- Every target branch builds.
- Every target branch starts a Forge server-side dev runtime.
- Every target branch starts a Forge dev client through Attuned initialization,
  resource reload, OpenAL startup, and texture-atlas creation.
- `forge/26.2` joins a quick-play singleplayer world, accepts in-game Attuned
  commands, validates live registries, equips Focus items through the survival
  Focus UI, syncs the HUD, applies the expected player modifiers, and removes
  those modifiers after unequip.
- Attuned initializes during runtime smoke on every target branch.
- Focus data, item registration, language keys, item models, item definitions
  where present, animated 64x512 textures, and animation metadata pass the
  cross-branch audit for the 22 targeted Foci.
- Bramblegate, Seedcall, Riptide Heart, Pearlguard, Slagbrand, Anvilheart,
  Tidewarden, Wellspring, Current Runner, Saltbrand, Ebbstride, Overgrowth,
  Deeproot, Briarcoat, Fernstride, Sapflow, Cinderplate, Bellowsfury,
  Bloodrush, Ravager, Granitehide, and Hammerward are present across all
  Forge branches.
- `1.18.2` through `1.20.1` use `attuned:ebbstride` as the branch-safe
  fall-damage fallback where the vanilla fall-damage attribute is unavailable.
- `1.20.6` keeps swim behavior fallbacks where the water movement efficiency
  attribute is unavailable.
- `1.21.1`, `1.21.11`, `26.1.2`, and `26.2` use the newer attribute-backed
  profiles where the target Minecraft version exposes those attributes.

## Exact Verification Commands

| Branch | Build command | Server or game-test runtime | Client startup smoke |
| --- | --- | --- | --- |
| `forge/1.18.2` | `.\gradlew.bat build --no-daemon` | `.\gradlew.bat runGameTestServer --no-daemon` | `.\gradlew.bat runClient --no-daemon` |
| `forge/1.19.2` | `.\gradlew.bat build --no-daemon` | `.\gradlew.bat runGameTestServer --no-daemon` | `.\gradlew.bat runClient --no-daemon` |
| `forge/1.19.4` | `.\gradlew.bat build --no-daemon` | `.\gradlew.bat runGameTestServer --no-daemon` | `.\gradlew.bat runClient --no-daemon` |
| `forge/1.20.1` | `.\gradlew.bat build --no-daemon` | `.\gradlew.bat runGameTestServer --no-daemon` | `.\gradlew.bat runClient --no-daemon` |
| `forge/1.20.6` | `.\gradlew.bat build --no-daemon` | `.\gradlew.bat runServer --no-daemon` | `.\gradlew.bat runClient --no-daemon` |
| `forge/1.21.1` | `.\gradlew.bat build --no-daemon` | `.\gradlew.bat runServer --no-daemon` | `.\gradlew.bat runClient --no-daemon` |
| `forge/1.21.11` | `.\gradlew.bat build --no-daemon` | `.\gradlew.bat runServer --no-daemon` | `.\gradlew.bat runClient --no-daemon` |
| `forge/26.1.2` | `.\gradlew.bat build --no-daemon` | `.\gradlew.bat runServer --no-daemon` | `.\gradlew.bat runClient --no-daemon` |
| `forge/26.2` | `.\gradlew.bat test --no-daemon`; `.\gradlew.bat build --no-daemon` | `.\gradlew.bat runGameTestServer --no-daemon`; `.\gradlew.bat runServer --no-daemon` | `.\gradlew.bat runClient --no-daemon` |

## Manual In-World Focus Smoke

`forge/26.2` was also run through a live singleplayer Focus smoke on
2026-06-24. A copied dev save under the ignored `run/saves` directory was used
as the smoke world, with commands enabled only for the local test harness.

| Step | Result |
| --- | --- |
| Quick-play launch | `.\gradlew.bat runClient --no-daemon --args='--quickPlaySingleplayer SmokeWorld-26.2'` launched Forge 65.0.0, loaded Attuned, started the integrated server, prepared spawn chunks, and logged `Dev joined the game`. |
| Live content validation | `/attuned validate` passed in the joined world: `99 Focus definitions`, `17 palette behavior(s)`, and `13 Confluence definition(s)` checked. |
| Focus item setup | `/attuned capacity 30` clamped to the configured cap of `20`; `give` commands produced Cinderplate, Bloodrush, Tidewarden, Wellspring, Current-Runner, and Overgrowth Focus items. |
| Survival Focus UI | Wellspring, Current-Runner, and Overgrowth were moved from the player inventory into the vertical Focus slots. The Focus HUD mirrored the equipped items immediately. |
| Server status | `/attuned status` reported `Capacity: 12 / 20`, `Active Foci (3)`, `wellspring_focus (cost 4, tide)`, `current_runner_focus (cost 3, tide)`, and `overgrowth_focus (cost 5, verdant)`. |
| Modifier values | Attribute queries reported `Max Health = 32.0`, `Armor = 3.0`, `Speed = 0.11200000166893005`, and `Water Movement Efficiency = 1.0`, matching Wellspring, Current-Runner, and Overgrowth. |
| Unequip cleanup | After moving the three Foci back out of the Focus slots, `/attuned status` returned `Active Foci (0)`, and attributes returned to `Max Health = 20.0`, `Armor = 0.0`, `Speed = 0.10000000149011612`, `Water Movement Efficiency = 0.0`. |

## Client Startup Smoke

All client startup smokes were run on 2026-06-24 with
`.\gradlew.bat runClient --no-daemon`. Each client was stopped manually after
the stable startup milestone, so the task exits non-zero only because the
batch process is interrupted after verification.

| Branch | Result | Evidence observed |
| --- | --- | --- |
| `forge/1.18.2` | Passed | Forge 40.3.12 loaded the `attuned` mod, `Attuned initializing` logged, resources reloaded, OpenAL started, and vanilla atlases were created. |
| `forge/1.19.2` | Passed | Forge 43.5.2 loaded the `attuned` mod, `Attuned initializing` logged, resources reloaded, OpenAL started, atlases were created, and recipe/advancement loading appeared during shutdown. |
| `forge/1.19.4` | Passed | Forge 45.4.3 loaded the `attuned` mod, `Attuned initializing` logged, resources reloaded, OpenAL started, atlases were created, and recipe/advancement loading appeared during shutdown. |
| `forge/1.20.1` | Passed | Forge 47.4.20 loaded the `attuned` mod, `Attuned initializing` logged, early display initialized, resources reloaded, OpenAL started, and atlases were created. |
| `forge/1.20.6` | Passed | Forge 50.2.8 launched on Java 21 after first-run asset downloads, loaded Attuned, reloaded resources, started OpenAL, and created GUI/item/block atlases. |
| `forge/1.21.1` | Passed | Forge 52.1.14 launched on Java 21, loaded Attuned, reloaded resources, started OpenAL, and created GUI/item/block atlases. |
| `forge/1.21.11` | Passed | Forge 61.1.8 launched with the corrected mixin config, loaded Attuned, reloaded resources, started OpenAL, and created GUI/item/block atlases. |
| `forge/26.1.2` | Passed | Forge 64.0.10 launched on Java 25, loaded Attuned, reloaded resources, started OpenAL, and created GUI/item/block atlases. |
| `forge/26.2` | Passed | Forge 65.0.0 launched on Java 25, loaded Attuned, reloaded resources, started OpenAL, and created GUI/item/block atlases. |

## Branches Skipped

None of the detected Fabric branches were skipped. Older non-branch candidate
versions such as 1.16.5, 1.17.1, 1.12.2, 1.8.9, and 1.7.10 are not present as
Fabric source branches in this repository and were not part of this first-pass
Forge port.

## Known Limitations

- Full client startup smoke is recorded for every Forge branch. A 26.2
  singleplayer Focus equip/modifier cleanup smoke is also recorded. Older and
  intermediate branches still need interactive world-join, menu-click, and
  packet-flow playtests before publishing Forge packages.
- The 26.x attachment bridge copies in-memory values across clone/respawn, but
  save-file persistence, owner sync, and dedicated-server reconnect parity need
  release-hardening work.
- Dynamic registry registration remains compatibility-layer backed and needs a
  focused release review.
- The Forge networking bridge preserves packet shape for first-pass startup,
  but packet-flow behavior still needs a multiplayer and singleplayer playtest.
- Older-branch custom trident/harpoon projectile visuals remain visual
  compatibility work; core equip, budget, Focus data, and effect behavior are
  not blocked by those renderer gaps.
- Direct console injection for `/attuned validate` is not a release gate on the
  legacy dev servers because old Forge console/stdio handling was unreliable in
  this workspace. The same content surface is covered by contract tests, the
  static Focus audit, build, and runtime server launch.

## Next Release Gate

Before publishing Forge artifacts, run this narrower second-pass checklist:

1. A singleplayer world-join smoke on every non-26.2 branch, plus a repeated
   spot check on 26.2 after any attachment or networking changes.
2. A focused packet-flow playtest for inspect, updraft, journal, altar,
   reweaving, satchel, presets, and party/circle payloads.
3. A dedicated-server join/reconnect smoke on 26.x to harden attachment state.
4. A visual check for older trident/harpoon renderer fallbacks.
