# MCglTF-Inspired Renderer Notes

Attuned's `attuned:gltf_mesh` renderer is an owned implementation inspired by
ModularMods/MCglTF, not a vendored copy of its Forge/OpenGL runtime.

## Reference Checkouts

- `ModularMods/MCglTF`: `5bd2f3ea21494e1801a6077f9823f68f60e6d341`
- `ModularMods/MCglTF-Example`: `1fe390b37a603591ed794327caf823b54592b55c`

MCglTF's useful architectural idea is resource-driven glTF ownership: a renderer
implements a receiver, declares `getModelLocation()`, the client reload listener
groups receivers by model path, loads each glTF asset once, then hands back a
shared rendered model. The upstream code targets Forge 1.19.3 and raw
LWJGL/OpenGL calls, so Attuned keeps that lifecycle but renders through Minecraft
26.2's `SubmitNodeCollector` and `VertexConsumer` APIs.

## Current Scope

The first Attuned renderer intentionally supports the static mesh subset used by
the Ocean Relic Trident:

- Binary glTF 2.0 (`.glb`) with JSON and BIN chunks.
- Scene nodes, child nodes, and matrix/translation/rotation/scale transforms.
- Indexed `TRIANGLES` primitives.
- `POSITION`, `NORMAL`, and `TEXCOORD_0` float accessors.
- Unsigned byte, unsigned short, or unsigned int indices.
- Optional item-definition texture overrides.
- glTF material `baseColorTexture` lookup with embedded/data/external image URIs.

Unsupported features should fail during load instead of rendering corrupted
geometry. Add support deliberately when an asset requires it: animation, skinning,
morph targets, tangents, sampler settings, and normal/metallic/roughness maps are
the obvious next MCglTF parity areas.

## Direct Drop Usage

A model with embedded or material-referenced base color texture can be dropped
into the resource tree and referenced directly:

```json
{
	"type": "attuned:gltf_mesh",
	"model": "attuned:gltf/example_model.glb"
}
```

If the model should use a Minecraft texture resource instead of the material's
texture, provide an override:

```json
{
	"type": "attuned:gltf_mesh",
	"model": "attuned:gltf/example_model.glb",
	"texture": "attuned:textures/item/example_model.png"
}
```

## Ocean Relic Asset Pipeline

The original source GLB can be large because it embeds PNG textures. The
runtime asset is generated with:

```powershell
python tools\prepare_ocean_relic_gltf.py
```

That script:

- reads the source GLB path provided on the command line,
- converts positions and normals into Attuned's Minecraft/Blockbench model space,
- preserves indices and UVs,
- strips embedded images,
- writes `src/main/resources/assets/attuned/gltf/ocean_relic_trident.glb`.

The runtime mesh path uses
`attuned:textures/item/ocean_relic_trident_mesh.png` as the packaged texture. Its
`.mcmeta` clamps sampling so unused atlas space does not bleed into the mesh.
The full-size Blockbench model and texture stay in the repository for editing
and preview tools, but `processResources` excludes those source-only files from
release jars.

## Minecraft 26.2 Render Detail

The source mesh is triangles, but the current item/entity cutout render buffers
consume quads. `GltfMeshSpecialRenderer` emits each triangle as `A, B, C, C`.
That makes a degenerate quad whose first triangle is the intended face and whose
second triangle has zero area. Emitting only `A, B, C` causes Minecraft to stitch
unrelated triangle vertices together, producing the shredded diagonal artifacts
seen during testing.

## Minecraft 1.19.4 Maintenance Port (older generation)

Minecraft 1.19.4 predates the special-model SPI, so the modern render wiring is
not available on this branch. Verified by inspecting the named `1.19.4` mappings
and the bundled Fabric API:

- `SpecialModelRenderers` / `NoDataSpecialModelRenderer` / `SpecialModelRenderer`
  and the `minecraft:special` item-model type are **absent**.
- `SubmitNodeCollector` is **absent**; rendering uses `RenderType` (singular) and
  the legacy chained `VertexConsumer`
  (`vertex(matrix, x, y, z).color(argb).uv(u, v).overlayCoords(o).uv2(light)
  .normal(normalMatrix, nx, ny, nz).endVertex()`).
- `PoseStack.Pose.pose()` / `.normal()` already return `org.joml.Matrix4f` /
  `org.joml.Matrix3f` on 1.19.4 (`com.mojang.math.Matrix4f`/`Matrix3f` no longer
  exist), so the renderer emits through JOML matrices. The GLB parser still keeps
  its own immutable `V3` / column-major `Mat4` helpers so the scene-node transform
  math is identical to the modern build instead of depending on a version-specific
  matrix API.
- The Fabric `BuiltinItemRendererRegistry.DynamicItemRenderer.render(...)` mode
  parameter is `net.minecraft.world.item.ItemDisplayContext` on this branch (the
  standalone enum that replaced `ItemTransforms.TransformType`), and the same type
  is forwarded to the vanilla `BlockEntityWithoutLevelRenderer.renderByItem(...)`.
- Resources reload through
  `SimpleSynchronousResourceReloadListener.onResourceManagerReload(ResourceManager)`
  instead of the modern async `reload(...)`.
- Embedded glTF textures register through `DynamicTexture(NativeImage)` (the
  single-argument 1.19.4 constructor).

### Faithful render path on this branch

`AttunedGltfModels` (the GLB/bbmodel parser, shared-model cache, dynamic-texture
registration, and reload listener) is kept verbatim apart from the API renames
above. The parsed mesh is rendered through Fabric's
`BuiltinItemRendererRegistry.INSTANCE.register(item, DynamicItemRenderer)`,
drawing each primitive through
`MultiBufferSource.getBuffer(RenderType.entityCutout(texture))` with the same
`A, B, C, C` triangle-to-quad safety fix as the modern build.

The temporary Offshore Harpoon is a vanilla `Items.TRIDENT` stack (there is no
owned trident `Item` on this branch), so `GltfMeshSpecialRenderer` is registered
on `Items.TRIDENT` and guards on `HarpoonBehavior.isTemporaryHarpoon(stack)`:
only Attuned temporary harpoons draw the owned glTF mesh, while ordinary vanilla
tridents are delegated straight to the vanilla
`BlockEntityWithoutLevelRenderer` (reached through the
`ItemRendererBlockEntityAccessor` mixin) so they keep their built-in 3D renderer
and never re-enter the Fabric dispatch.

The item-definition JSON stays legacy `minecraft:model` (no `minecraft:special`).

### Thrown-projectile limitation

The modern build replaces vanilla thrown-trident rendering with a
`ThrownTridentRendererMixin` + `ThrownTridentRenderStateMixin` pair that hangs an
`ItemStackRenderState` (a 1.21.4+ type) off the trident render state. Neither
`ItemStackRenderState` nor a per-entity item render-state model exists in 1.19.4,
and the 1.19.4 `ThrownTridentRenderer` renders the vanilla trident model
directly. A faithful custom projectile mesh would require re-architecting the
entity renderer, so on this branch both `ThrownTridentRendererMixin` and
`ThrownTridentRenderStateMixin` remain inert stubs that are **not** listed in
`attuned.client.mixins.json`. Thrown temporary harpoons therefore render with the
vanilla trident model in flight; the held/inventory harpoon still uses the owned
glTF mesh.
