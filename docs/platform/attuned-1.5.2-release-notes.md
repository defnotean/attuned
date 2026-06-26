## Attuned 1.5.2 - Minecraft 26.2 Chaos Cubed

### Changed
- **Minecraft 26.2 Chaos Cubed support** - the `latest` line now targets Minecraft 26.2 with Fabric Loader 0.19.3, Fabric API 0.152.1+26.2, Loom 1.17.11, and Gradle 9.5.1. The port updates 26.2 entity/knockback APIs, client render submit nodes, and screen/toast hooks so server and client dev runtimes launch cleanly.
- **Release line split** - Minecraft 26.1.2 remains preserved on `maintenance/minecraft-26.1.2`, while `latest` carries the current Minecraft 26.2 release train.
- **Loader note** - this release note describes the Fabric file. Quilt compatibility requires Quilt Loader + QFAPI smoke evidence, and Quilt/NeoForge/Forge support requires separate loader metadata and smoke checklists before publication.
- **Updraft release carried forward** - Attuned 1.5.2 includes the 1.5.1 Updraft Focus, smoother boost/brake controls, flight feedback, and PvP exhaustion safeguard.

### Internal
- Refreshed dependency locking, Gradle verification metadata, version-profile docs, and release upload metadata for the Minecraft 26.2 toolchain.
