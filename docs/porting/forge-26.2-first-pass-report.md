# Forge 26.2 First-Pass Port Report

Date: 2026-06-23

Branch: `forge/26.2`

Reference source: `origin/fabric/minecraft-26.2`

## Status

`forge/26.2` is the current reference Forge port. It builds, launches to an
idle client after resource reload, and passes server runtime smoke coverage.
The client smoke was stopped manually after a stable idle window, so the
Gradle task exits non-zero only because the launched client process was
terminated by the tester.

## Verified Commands

| Command | Result | Notes |
| --- | --- | --- |
| `.\gradlew.bat test --no-daemon` | Passed | Re-run after attachment respawn-copy hardening. |
| `.\gradlew.bat build --no-daemon` | Passed | ForgeGradle 7, Forge 65.0.0, Java 25. Re-run after attachment respawn-copy hardening. |
| `.\gradlew.bat runGameTestServer --no-daemon` | Passed | One required game test passed, then the server shut down cleanly. Re-run after attachment respawn-copy hardening. |
| `.\gradlew.bat runClient --no-daemon` | Smoke passed | Client initialized Attuned, loaded mixins, reloaded resources, created atlases, and stayed idle. Re-run after attachment respawn-copy hardening. The task was manually stopped after the smoke window. |

## Cross-Version Closure

After the 26.2 reference port was stabilized, the same Forge migration pattern
was carried across `forge/26.1.2`, `forge/1.21.11`, `forge/1.21.1`,
`forge/1.20.6`, `forge/1.20.1`, `forge/1.19.4`, `forge/1.19.2`, and
`forge/1.18.2`.

The final verification pass completed:

- `build` on every Forge branch.
- `runServer` on `forge/26.2`, `forge/26.1.2`, `forge/1.21.11`,
  `forge/1.21.1`, and `forge/1.20.6`.
- `runGameTestServer` on `forge/1.20.1`, `forge/1.19.4`,
  `forge/1.19.2`, and `forge/1.18.2`.
- Cross-branch Focus audit covering the 22 targeted new or recently adjusted
  Foci, their language keys, models, animated textures, item definitions, data
  modifiers, behavior ids, and version-specific attribute fallbacks.

The Focus audit explicitly covered Bramblegate, Seedcall, Riptide Heart,
Pearlguard, Slagbrand, Anvilheart, the Tide/Verdant/Forge/Fury/Bastion
modifier wave, and Ebbstride's older-version landing fallback.

## Migration Pattern That Worked

- Build metadata now uses ForgeGradle instead of Fabric Loom.
- `fabric.mod.json` is replaced by `META-INF/mods.toml`, `pack.mcmeta`, and a manifest `MixinConfigs` entry.
- `src/client/java` and `src/client/resources` are folded into the main source set for this first pass, with client initialization gated by runtime dist checks.
- The common entrypoint is `@Mod(Attuned.MOD_ID)`.
- Item, block, menu, component, and creative-tab registration flows through a small Forge `DeferredRegister` bridge.
- `AttunedContent` constructs registered objects inside Forge registry suppliers instead of eagerly constructing them during class load.
- Creative tabs use supplier-backed icons so the tab registry does not capture null item fields before item registration runs.
- Fabric networking calls are shimmed onto one Forge payload channel for the first pass.
- Fabric event and loader APIs are shimmed where that kept the shared source stable.
- The attachment bridge now copies eligible in-memory values across Forge player clone/respawn events, honoring the Fabric-style `copyOnDeath` flag.
- Mixin configs are explicitly listed in the jar manifest and source-run manifest.
- Mixin compatibility is capped at `JAVA_21`, because the active Mixin line does not recognize a `JAVA_25` compatibility level even though the game runs on Java 25.

## Main Files Changed

- `settings.gradle`
- `gradle.properties`
- `build.gradle`
- `gradle/verification-metadata.xml`
- `src/main/java/dev/attuned/Attuned.java`
- `src/main/java/dev/attuned/platform/ForgeRegistration.java`
- `src/main/java/dev/attuned/content/AttunedContent.java`
- `src/main/java/dev/attuned/content/AttunedCreativeTabs.java`
- `src/main/java/dev/attuned/content/AttunedComponents.java`
- `src/main/java/dev/attuned/attunement/AttunedAttachments.java`
- `src/main/java/net/fabricmc/**`
- `src/client/java/net/fabricmc/**`
- `src/main/resources/META-INF/mods.toml`
- `src/main/resources/META-INF/MANIFEST.MF`
- `src/main/resources/pack.mcmeta`
- `src/main/resources/attuned.mixins.json`
- `src/client/resources/attuned.client.mixins.json`
- focused contract tests that assumed Fabric-only metadata or direct attachment calls

## Debugging Notes

1. Forge source-set discovery produced a resources-only mod until `net.minecraftforge.gradle.merge-source-sets=true` was added.
2. The first launch hit locked registry errors from direct registration; the fix was a Forge `DeferredRegister` bridge.
3. The next launch hit frozen item registry errors from eager item construction; the fix was supplier-time construction in `AttunedContent`.
4. Creative tab icons needed suppliers because static item fields are assigned by registry suppliers.
5. Payload channel setup had to keep the flow-specific channel builder instead of casting a builder to a buildable channel.
6. The client initially failed because mixin configs were not applied in source-set runs; adding `META-INF/MANIFEST.MF` fixed that.
7. `JAVA_25` mixin compatibility failed; `JAVA_21` is the highest recognized compatibility level in this Mixin line.

## Known Gaps

- Player attachments are still bridged in memory for this first pass. Clone/respawn copying is covered, but this is not final parity for save-file persistence, owner sync, or dedicated-server reconnects.
- Dynamic registry registration is still shim-backed. It must be reviewed before release packaging.
- The client source set is merged into main for simplicity, so dedicated-server classloading must stay part of the smoke gate.
- The Forge channel bridge is a compatibility layer, not a final hand-tuned network API. It needs packet-flow manual checks before publishing.
- Older-branch custom trident/harpoon projectile visuals remain visual
  compatibility work; they do not block core Focus equip, budget, data, or
  effect behavior.

## Acceptance Gate For Other Forge Branches

Every version branch should report:

1. Build command and result.
2. Game test or server smoke command and result.
3. Client launch smoke result when the branch can launch a client locally.
4. Any branch-specific API differences from the 26.2 template.
5. Any skipped behavior and whether it is visual-only, data-only, or gameplay-affecting.
6. Manual checks still needed for foci equip, HUD, journal, satchel, combat, updraft flight controls, pact trial state, and packet flow.
