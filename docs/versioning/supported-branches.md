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
