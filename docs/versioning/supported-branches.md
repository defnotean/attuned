# Supported Minecraft Branches

This repository keeps the newest development line on `latest`. Older Minecraft
targets live on independent maintenance branches and should not be merged back
to `latest` unless the change is also intended for the newest target.

Forge ports live on parallel `forge/<minecraft-version>` branches. They are
not replacements for the Fabric maintenance branches; they are the starting
point for Forge packaging, testing, and release hardening.

| Branch | Minecraft | Status |
| --- | --- | --- |
| `latest` | `26.2` | current |
| `maintenance/minecraft-26.1.2` | `26.1.2` | maintenance |
| `maintenance/minecraft-1.21.11` | `1.21.11` | maintenance |
| `maintenance/minecraft-1.20.6` | `1.20.6` | maintenance |
| `maintenance/minecraft-1.19.4` | `1.19.4` | maintenance |
| `maintenance/minecraft-1.18.2` | `1.18.2` | maintenance |

## Forge Port Branches

| Branch | Minecraft | Status |
| --- | --- | --- |
| `forge/26.2` | `26.2` | first-pass verified |
| `forge/26.1.2` | `26.1.2` | first-pass verified |
| `forge/1.21.11` | `1.21.11` | first-pass verified |
| `forge/1.21.1` | `1.21.1` | first-pass verified |
| `forge/1.20.6` | `1.20.6` | first-pass verified |
| `forge/1.20.1` | `1.20.1` | first-pass verified |
| `forge/1.19.4` | `1.19.4` | first-pass verified |
| `forge/1.19.2` | `1.19.2` | first-pass verified |
| `forge/1.18.2` | `1.18.2` | first-pass verified |

## Famous Version Target Audit - 2026-06-17

The next support targets were selected from current Modrinth project search
counts plus the long-running Minecraft modding community version split. Counts
are a prioritization signal only; a branch is not treated as supported until it
has branch-local compile, repository verification, Gradle build, server smoke,
client smoke, and CI evidence.

| Minecraft | Modrinth mod hits | Fabric mod hits | Port track | Starting point |
| --- | ---: | ---: | --- | --- |
| `1.20.1` | 31,375 | 16,157 | candidate Fabric maintenance branch | `maintenance/minecraft-1.20.6` |
| `1.21.1` | 26,144 | 16,574 | candidate Fabric maintenance branch | `maintenance/minecraft-1.21.11` |
| `1.19.2` | 11,747 | 7,154 | candidate Fabric maintenance branch | `maintenance/minecraft-1.19.4` |
| `1.16.5` | 5,509 | 2,802 | high-effort Fabric backport | requires Java 8 bytecode/build-script support and older Fabric API surface |
| `1.17.1` | 3,772 | 2,898 | high-effort Fabric backport | requires Java 16 bytecode/build-script support and older Fabric API surface |
| `1.12.2` | 2,540 | 142 | legacy rewrite track | Forge/legacy architecture, not a branch-local Fabric profile |
| `1.7.10` | 870 | 83 | legacy rewrite track | Forge/legacy architecture, not a branch-local Fabric profile |
| `1.8.9` | 742 | 124 | legacy rewrite track | Forge/PvP legacy architecture, not a branch-local Fabric profile |

Candidate Fabric profiles are kept in `config/minecraft-version-profiles.json`
with `status: "candidate"` until their branches are created and verified. The
legacy rewrite track should become a separate project plan because this Fabric
mod's loader metadata, resource pack layout, Fabric API dependency, and modern
Minecraft APIs do not map to those versions as a small profile change.

## Verification Results - 2026-06-16

All branches below were pushed to `origin` after their branch-local version
profile, Gradle properties, Fabric metadata, README requirements, dependency
locks, and verification metadata were aligned. The second pass added manual
runtime launch coverage and GitHub Actions confirmation for every supported
branch.

| Branch | Result | Verification |
| --- | --- | --- |
| `latest` | pass locally; CI pending | Retargeted to Minecraft 26.2 Chaos Cubed. Local gates passed: profile validation, repository verification, Python unittest discovery, pytest/Pillow, Gradle `test build`, dedicated-server smoke on `attuned_smoke_262_20260616`, client `runClient` smoke through Attuned initialization/resource startup, GUI previews, CurseForge dry-run metadata, and Modrinth dry-run. |
| `maintenance/minecraft-26.1.2` | pass | Preserves the previous 26.1.2 latest line from GitHub CI run `27632626151`; requires branch-local CI confirmation after branch creation. |
| `maintenance/minecraft-1.21.11` | pass | Same gate passed; server smoke used fresh world `attuned_smoke_12111_20260616103713`; GitHub CI run `27631781707` passed. |
| `maintenance/minecraft-1.20.6` | pass | Same gate passed after one local client asset-cache retry; server smoke used fresh world `attuned_smoke_1206_20260616103909`; GitHub CI run `27631829397` passed. |
| `maintenance/minecraft-1.19.4` | pass | Same gate passed after fixing the client mixin Java compatibility and adding Linux LWJGL native verification; server smoke used fresh world `attuned_smoke_1194_fix_20260616105514`; GitHub CI run `27632282182` passed. |
| `maintenance/minecraft-1.18.2` | pass | Same gate passed after fixing the client mixin Java compatibility; server smoke used fresh world `attuned_smoke_1182_20260616105835`; GitHub CI run `27631931192` passed. |

Manual runtime coverage included:

- 95-Focus catalog asset sanity: every FocusDefinition resolves to a unique
  item with item definition, model, and texture.
- Repository/resource validation, Python unittest discovery, pytest/Pillow,
  and Gradle `test build`.
- GUI preview rendering for altar, reweaving, satchel, and journal screens,
  with the generated contact sheet at
  `build/manual-qa/contact-sheets/all-branch-gui-previews.png` visually
  inspected for blank panels, HUD/resource breakage, and slot-overlay drift.
- Dedicated-server smoke on every branch with fatal log scanning.
- Client `runClient` smoke on every branch through Attuned initialization,
  resource reload/audio initialization, atlas creation, and fatal log scanning.

This is not a full human playthrough of every Focus effect. It does verify that
all shipped Focus definitions and behavior contracts load, all branch jars
build, every supported branch launches in server and client dev runtime, and
the HUD/GUI resources render without the issues targeted by this QA pass.

## Version-Specific Notes

- `1.21.11` keeps the latest-line gameplay surface but pins Java 21 and
  1.21.11 Fabric dependencies on its maintenance branch.
- `1.20.6` carries branch-local compatibility for pre-1.21 renderer and
  component differences.
- `1.19.4` carries branch-local networking, attachment/state sync, attribute,
  and item/menu compatibility changes for the older API surface.
- `1.18.2` carries the largest compatibility layer: legacy networking bridges,
  dynamic-registry and living-entity-event facades, pre-1.19 text/menu/client
  screen APIs, last-death tracking fallback, older creative-tab organization,
  and 1.18 worldgen structure JSON.

## Dependency Targets

| Minecraft | Java | Fabric Loader | Fabric API | Loom |
| --- | --- | --- | --- | --- |
| `26.2` | `25` | `0.19.3` | `0.152.1+26.2` | `1.17.11` |
| `26.1.2` | `25` | `0.19.3` | `0.152.1+26.1.2` | `1.17.11` |
| `1.21.11` | `21` | `0.19.3` | `0.141.4+1.21.11` | `1.17.11` |
| `1.20.6` | `21` | `0.19.3` | `0.100.8+1.20.6` | `1.17.11` |
| `1.19.4` | `17` | `0.19.3` | `0.87.2+1.19.4` | `1.17.11` |
| `1.18.2` | `17` | `0.19.3` | `0.77.0+1.18.2` | `1.17.11` |

CI uses `build_java_version` from `tools/minecraft_version_profile.py` for the
Gradle/Loom JVM. That value is at least Java 21 because current Loom releases
require a Java 21+ build runtime; the mod bytecode target and platform metadata
still use the branch `java_version` shown above.

## Branch Policy

- `latest` remains the only branch for newest-version development.
- Maintenance branches are stable backport branches for their target Minecraft
  version.
- Apply a version profile on its matching branch with
  `python tools/minecraft_version_profile.py apply <minecraft-version>`.
- Run repository verification, Python tests, Gradle build, and server smoke on
  each branch before publishing or tagging a maintenance build.
- Keep version-specific fixes on the maintenance branch unless the same fix is
  required on `latest`.
