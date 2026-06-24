# Forge Port Version Matrix

Date: 2026-06-24

This matrix is the working checklist for porting the Fabric release branches to Forge. The Fabric source branches are the renamed GitHub branches under `fabric/minecraft-*`; the target branches use `forge/<minecraft-version>`. The end-of-pass summary is in `forge-final-report.md`.

## Sources

- Forge promotions: https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json
- Forge MDKs: `https://maven.minecraftforge.net/net/minecraftforge/forge/<minecraft>-<forge>/forge-<minecraft>-<forge>-mdk.zip`
- Fabric source branches: `origin/fabric/minecraft-*` after the branch rename.

## Target Matrix

| Source Fabric branch | Target Forge branch | Minecraft | Java | Fabric build stack | Current mappings | Target Forge | Target ForgeGradle | Target mappings | Risk |
| --- | --- | ---: | ---: | --- | --- | --- | --- | --- | --- |
| `fabric/minecraft-26.2` | `forge/26.2` | 26.2 | 25 | Loom 1.17.11, Fabric Loader 0.19.3, Fabric API 0.152.1+26.2 | No explicit mapping line in Fabric build; verify before compile migration | 65.0.0 | `[7.0.17,8)` | Forge 26.2 MDK omits explicit `mappings`; use MDK default first | High |
| `fabric/minecraft-26.1.2` | `forge/26.1.2` | 26.1.2 | 25 | Loom 1.17.11, Fabric Loader 0.19.3, Fabric API 0.152.1+26.1.2 | No explicit mapping line in Fabric build; verify before compile migration | 64.0.10 | `[7.0.17,8)` | Forge 26.1.2 MDK omits explicit `mappings`; use MDK default first | High |
| `fabric/minecraft-1.21.11` | `forge/1.21.11` | 1.21.11 | 21 | Loom 1.17.11, Fabric Loader 0.18.4, Fabric API 0.141.4+1.21.11 | Official Mojang via `loom.officialMojangMappings()` | 61.1.8 | `[7.0.3,8)` | Official 1.21.11 | High |
| `fabric/minecraft-1.21.1` | `forge/1.21.1` | 1.21.1 | 21 | Loom 1.14.10, Fabric Loader 0.16.14, Fabric API 0.116.5+1.21.1 | Official Mojang via `loom.officialMojangMappings()` | 52.1.14 | `[7.0.3,8)` | Official 1.21.1 | Medium |
| `fabric/minecraft-1.20.6` | `forge/1.20.6` | 1.20.6 | 21 | Loom 1.17.11, Fabric Loader 0.18.4, Fabric API 0.100.8+1.20.6 | Official Mojang via `loom.officialMojangMappings()` | 50.2.8 | `[7.0.3,8)` | Official 1.20.6 | High |
| `fabric/minecraft-1.20.1` | `forge/1.20.1` | 1.20.1 | 17 | Loom 1.17.11, Fabric Loader 0.18.4, Fabric API 0.92.8+1.20.1 | Official Mojang via `loom.officialMojangMappings()` | 47.4.20 | `[6.0,6.2)` | Official 1.20.1 | High |
| `fabric/minecraft-1.19.4` | `forge/1.19.4` | 1.19.4 | 17 | Loom 1.17.11, Fabric Loader 0.18.4, Fabric API 0.87.2+1.19.4 | Official Mojang via `loom.officialMojangMappings()` | 45.4.3 | `[6.0,6.2)` | Official 1.19.4 | High |
| `fabric/minecraft-1.19.2` | `forge/1.19.2` | 1.19.2 | 17 | Loom 1.17.11, Fabric Loader 0.18.4, Fabric API 0.77.0+1.19.2 | Official Mojang via `loom.officialMojangMappings()` | 43.5.2 | `[6.0,6.2)` | Official 1.19.2 | High |
| `fabric/minecraft-1.18.2` | `forge/1.18.2` | 1.18.2 | 17 | Loom 1.17.11, Fabric Loader 0.18.4, Fabric API 0.77.0+1.18.2 | Official Mojang via `loom.officialMojangMappings()` | 40.3.12 | `[6.0,6.2)` | Official 1.18.2 | High |

## Current Port Status

All target Forge branches now have first-pass source ports, build verification,
server or game-test runtime smoke coverage, client startup smoke coverage, and
a cross-branch Focus data/asset/mechanics audit. The Forge branches remain
independent from the Fabric maintenance branches.

| Target Forge branch | Minecraft | Status | Last verification | Branch-specific notes |
| --- | ---: | --- | --- | --- |
| `forge/26.2` | 26.2 | First-pass port verified with in-world Focus smoke | `build`, `runServer`, `runClient`, focus audit, quick-play world join, Focus equip/modifier smoke | Reference Forge port on Forge 65.0.0 and Java 25. |
| `forge/26.1.2` | 26.1.2 | First-pass port verified | `build`, `runServer`, `runClient`, focus audit | Same modern ForgeGradle 7 shape as 26.2 with branch-local dependency pins. |
| `forge/1.21.11` | 1.21.11 | First-pass port verified | `build`, `runServer`, `runClient`, focus audit | Mixin configs pin `minVersion` to 0.8.5 and Java compatibility to the recognized runtime level. |
| `forge/1.21.1` | 1.21.1 | First-pass port verified | `build`, `runServer`, `runClient`, focus audit | Uses the newer water/fall attributes while retaining the 1.21.1 `minecraft:generic.*` attribute ids. |
| `forge/1.20.6` | 1.20.6 | First-pass port verified | `build`, `runServer`, `runClient`, focus audit | Uses the 1.20.6 fall-damage attribute and swim-behavior fallbacks for missing water movement efficiency. |
| `forge/1.20.1` | 1.20.1 | First-pass port verified | `build`, `runGameTestServer`, `runClient`, focus audit | Legacy ForgeGradle 6 port with Ebbstride's fall reduction handled by a runtime behavior fallback. |
| `forge/1.19.4` | 1.19.4 | First-pass port verified | `build`, `runGameTestServer`, `runClient`, focus audit | Legacy creative-tab/event wiring plus the same Ebbstride fallback as 1.20.1. |
| `forge/1.19.2` | 1.19.2 | First-pass port verified | `build`, `runGameTestServer`, `runClient`, focus audit | Legacy networking and screen compatibility layer; game-test server loads and exits cleanly. |
| `forge/1.18.2` | 1.18.2 | First-pass port verified | `build`, `runGameTestServer`, `runClient`, focus audit | Widest compatibility layer, with pre-1.19 screen/menu shims and Ebbstride fall fallback. |

## Completed Verification

- `.\gradlew.bat build --no-daemon` passed on every Forge branch after the
  branch-local source, metadata, test, and resource changes.
- `.\gradlew.bat runGameTestServer --no-daemon` passed on `forge/1.18.2`,
  `forge/1.19.2`, `forge/1.19.4`, and `forge/1.20.1`. Those runs loaded
  Forge, initialized Attuned, prepared a world, reported the enabled Attuned
  game-test namespace, and shut down cleanly.
- `.\gradlew.bat runServer --no-daemon` passed on `forge/1.20.6`,
  `forge/1.21.1`, `forge/1.21.11`, `forge/26.1.2`, and `forge/26.2`.
  Those branches use server smoke coverage because the modern game-test
  dev task does not receive named test functions in this workspace.
- `.\gradlew.bat runClient --no-daemon` reached stable client startup on every
  Forge branch. The observed milestone was Forge and Attuned initialization,
  resource reload, OpenAL startup, and texture-atlas creation. Each client was
  then stopped manually, so non-zero `runClient` exits after that point mean
  the verified client process was interrupted rather than a launch failure.
- `forge/26.2` additionally passed a manual quick-play singleplayer smoke:
  the integrated server started, spawn chunks prepared, the player joined,
  `/attuned validate` checked 99 Focus definitions, 17 palette behaviors, and
  13 Confluence definitions, and the survival Focus UI accepted equipped Foci.
- The 26.2 in-world Focus smoke equipped Wellspring, Current-Runner, and
  Overgrowth, then verified `/attuned status` reported them active at
  `12 / 20` capacity. Attribute probes showed the expected modifier values:
  max health `32.0`, armor `3.0`, movement speed `0.11200000166893005`, and
  water movement efficiency `1.0`. After unequipping, status returned to
  `Active Foci (0)` and the same attributes returned to vanilla baselines.
- A branch-wide Focus audit checked 22 targeted Foci and 22 generated Focus
  textures per branch: Bramblegate, Seedcall, Riptide Heart, Pearlguard,
  Slagbrand, Anvilheart, the Tide/Verdant/Forge/Fury/Bastion modifier wave,
  and Ebbstride's version-specific landing behavior.
- The Focus audit validated definition JSON, item registration, language keys,
  item models, modern item definitions where present, 64x512 animated PNG
  textures, `.png.mcmeta` animation metadata, uniqueness rules for
  behavior-backed Foci, and each branch's expected attribute id namespace.
- Direct console injection for `/attuned validate` is not used as a release
  gate on the legacy dev servers because Forge's old console/stdio handling is
  unreliable in this workspace. The same content surface is covered by
  contract tests, static Focus audit, build, and runtime server launch.

## Focus Compatibility Results

- The user-requested Focus set is present and registered on every Forge branch:
  Riptide Heart, Pearlguard, Slagbrand, Anvilheart, Seedcall, Bramblegate,
  Tidewarden, Wellspring, Current Runner, Saltbrand, Ebbstride, Overgrowth,
  Deeproot, Briarcoat, Fernstride, Sapflow, Cinderplate, Bellowsfury,
  Bloodrush, Ravager, Granitehide, and Hammerward.
- `1.18.2` through `1.20.1` do not expose a usable fall-damage multiplier
  attribute. Ebbstride therefore keeps its attack-speed modifier in data and
  uses `attuned:ebbstride` as a lightweight runtime fall-damage fallback.
- `1.20.6` exposes a fall-damage multiplier attribute but not the water
  movement efficiency attribute used by newer versions. Ebbstride uses data
  for fall reduction, while Wellspring and Current Runner keep swim behaviors.
- `1.21.1` exposes both newer attributes but still uses
  `minecraft:generic.*` ids. `1.21.11`, `26.1.2`, and `26.2` use the newer
  short attribute ids such as `minecraft:armor` and `minecraft:attack_speed`.
- No Focus asset or gameplay-definition gaps remain in the audited set.
- On `forge/26.2`, the Focus inventory UI, HUD mirroring, active-state resolver,
  and attribute modifier cleanup were manually exercised in a joined
  singleplayer world for Wellspring, Current-Runner, and Overgrowth.

## Runtime Notes

- Optional Netty native transport messages can appear on Windows during
  dev-server startup for the newest branches. They did not prevent Attuned
  initialization, server readiness, or clean shutdown.
- Offline/dev-profile Realms authentication errors can appear during newer
  client startup. They did not prevent Attuned initialization, resource reload,
  sound startup, or texture-atlas creation.
- Opening a temporary LAN server in the 26.2 Windows dev client produced Netty
  native transport/logging noise. The release-relevant singleplayer quick-play
  path did not depend on LAN and completed its world join, validation, Focus
  equip, modifier, and cleanup checks.
- The 26.x attachment bridge remains a first-pass Forge compatibility layer:
  clone/respawn copying is covered, but save-file persistence, owner sync, and
  dedicated-server reconnect parity still need release-hardening work before a
  public Forge package.
- Custom trident/harpoon projectile visuals on older branches remain visual
  compatibility work. Core equip, budget, Focus data, and effect behavior
  are not blocked by those renderer gaps.

## Branch Shape

| Source Fabric branch | Main Java | Client Java | Mixins | Foci | Data behaviors | Synergies | Payload classes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `fabric/minecraft-26.2` | 188 | 36 | 21 | 99 | 17 | 13 | 23 |
| `fabric/minecraft-26.1.2` | 188 | 38 | 22 | 99 | 17 | 13 | 23 |
| `fabric/minecraft-1.21.11` | 188 | 38 | 22 | 99 | 17 | 13 | 23 |
| `fabric/minecraft-1.21.1` | 189 | 31 | 16 | 99 | 17 | 13 | 23 |
| `fabric/minecraft-1.20.6` | 193 | 38 | 22 | 99 | 19 | 13 | 24 |
| `fabric/minecraft-1.20.1` | 197 | 39 | 26 | 99 | 19 | 13 | 24 |
| `fabric/minecraft-1.19.4` | 197 | 40 | 26 | 99 | 19 | 13 | 24 |
| `fabric/minecraft-1.19.2` | 205 | 41 | 26 | 99 | 19 | 13 | 24 |
| `fabric/minecraft-1.18.2` | 205 | 40 | 25 | 99 | 19 | 13 | 24 |

## Major Systems Present

- Loader entrypoints: `dev.attuned.Attuned` and `dev.attuned.client.AttunedClient`.
- Registries and data: custom focus, focus behavior, and synergy registries; 99 focus definitions on every branch; worldgen structure and loot content.
- Player state: Fabric attachments on the modern lines, older custom sync/state patterns on legacy lines.
- Combat and movement: pacts, apex/resonance/synergy combat, updraft elytra control, knockback, fall, trident/harpoon hooks.
- Networking: ability, inspect, updraft, journal, altar, reweaving, preset, and circle payloads.
- Client UI: foci HUD, combat HUD, party HUD, journal, altar, reweaving, satchel, keybindings, tooltips.
- Rendering: custom foci/item resources, altar variants, trident/harpoon rendering, GLTF or Blockbench-backed item rendering where supported.
- Mixins: inventory UI injection, combat hooks, fishing hooks, elytra movement, packet handling, invisibility render-layer suppression, custom trident rendering.

## Dependency Deltas

- Replace Fabric Loom with ForgeGradle.
- Replace `fabric.mod.json` with `META-INF/mods.toml` and Forge pack metadata expansion.
- Replace Fabric Loader config directory lookups with Forge config paths through `ModLoadingContext`, `FMLPaths`, or a small compatibility helper.
- Replace Fabric events with Forge event bus subscribers where a Forge event exists.
- Replace Fabric networking with the Forge networking API appropriate to the branch:
  - 26.x through 1.20.6: start from the ForgeGradle 7 MDK and current Forge networking for those lines.
  - 1.20.1 through 1.18.2: use the ForgeGradle 6 MDK and `SimpleChannel`-style packet registration.
- Replace Fabric dynamic registry bootstrap with Forge-compatible registry/data-pack bootstrap.
- Replace Fabric creative tab helpers with Forge creative tab registration.
- Replace Fabric resource reload/HUD/keybinding hooks with Forge client setup, register-key-mapping, render GUI, and reload listener events.
- Keep mixins only where Forge events cannot preserve behavior.

## First Port Order

1. `forge/26.2`: current reference port because it is the default branch and newest supported line.
2. `forge/1.20.1`: compatibility anchor because it uses the older ForgeGradle 6 layout and has the widest legacy surface.
3. `forge/1.21.11`, `forge/26.1.2`, `forge/1.21.1`, `forge/1.20.6`: apply the current-reference pattern with version-specific adjustments.
4. `forge/1.19.4`, `forge/1.19.2`, `forge/1.18.2`: apply the compatibility-anchor pattern with legacy networking/rendering adjustments.

## Checks Per Branch

- `./gradlew build`
- `./gradlew compileJava`
- `./gradlew processResources`
- `./gradlew runClient` or the ForgeGradle run task for that branch
- `./gradlew runServer` for server-only classloading and networking smoke coverage
- Manual smoke checklist: Forge main menu, world join, item/block registration, focus equip, foci HUD, commands, config load/save, singleplayer packet flow, dedicated server packet flow, client-only class safety, updraft/elytra controls, pact/combat feedback, satchel and journal screens.

## Coordination Rules

- `forge/coordinator` owns this matrix and the final port report.
- Version work happens only on the matching `forge/<minecraft-version>` branch.
- Shared migration patterns are copied forward after a branch builds; branch-specific Forge APIs stay branch-specific.
- No feature is removed just to satisfy compilation. If a feature cannot be ported cleanly, document the limitation and keep the nearest supported behavior.
