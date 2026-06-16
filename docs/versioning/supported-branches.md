# Supported Minecraft Branches

This repository keeps the newest development line on `latest`. Older Minecraft
targets live on independent maintenance branches and should not be merged back
to `latest` unless the change is also intended for the newest target.

| Branch | Minecraft | Status |
| --- | --- | --- |
| `latest` | `26.1.2` | current |
| `maintenance/minecraft-1.21.11` | `1.21.11` | maintenance |
| `maintenance/minecraft-1.20.6` | `1.20.6` | maintenance |
| `maintenance/minecraft-1.19.4` | `1.19.4` | maintenance |
| `maintenance/minecraft-1.18.2` | `1.18.2` | maintenance |

## Verification Results - 2026-06-16

All branches below were pushed to `origin` after their branch-local version
profile, Gradle properties, Fabric metadata, README requirements, dependency
locks, and verification metadata were aligned.

| Branch | Result | Verification |
| --- | --- | --- |
| `latest` | pass | `python -B tools\verify_repository.py`, `python -B -m unittest discover -s tests`, `uv run --with pytest --with pillow -m pytest tests/ -q`, `.\gradlew.bat test build --no-daemon`, and `python -B tools\minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60` passed; smoke reached `Done (0.462s)!`. 1.5.1 platform release to Modrinth, CurseForge, and GitHub also completed from this line. |
| `maintenance/minecraft-1.21.11` | pass | Branch-local profile/build migration passed full Gradle build, automated tests, and dedicated-server launch smoke. |
| `maintenance/minecraft-1.20.6` | pass | `.\gradlew.bat test build --no-daemon` passed; dedicated-server launch smoke passed; 1.20.6 compatibility shims are branch-local. |
| `maintenance/minecraft-1.19.4` | pass | `.\gradlew.bat test build --no-daemon` passed with 565 tests and 8 skipped; dedicated-server launch smoke reached `Done (14.092s)!`. |
| `maintenance/minecraft-1.18.2` | pass | `.\gradlew.bat test build --no-daemon` passed; dedicated-server launch smoke reached `Done (14.122s)!` in world `attuned_smoke_1182_20260616093616`. |

Automated coverage includes repository/resource validation, focus definition and
behavior contracts, asset/model/lang contracts, GUI resource/layout contracts,
and dedicated-server startup. Manual client click-through QA should still be
run before uploading maintenance jars to distribution platforms, especially for
branch-specific client renderer or screen changes.

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
| `26.1.2` | `25` | `0.19.2` | `0.149.0+26.1.2` | `1.16.3` |
| `1.21.11` | `21` | `0.19.3` | `0.141.4+1.21.11` | `1.16.3` |
| `1.20.6` | `21` | `0.19.3` | `0.100.8+1.20.6` | `1.16.3` |
| `1.19.4` | `17` | `0.19.3` | `0.87.2+1.19.4` | `1.16.3` |
| `1.18.2` | `17` | `0.19.3` | `0.77.0+1.18.2` | `1.16.3` |

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
