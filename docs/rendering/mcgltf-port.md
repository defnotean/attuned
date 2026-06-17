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

The original Meshy GLB is about 54 MB because it embeds large PNG textures. The
runtime asset is generated with:

```powershell
python tools\prepare_ocean_relic_gltf.py
```

That script:

- reads the source GLB from the user's Downloads folder by default,
- converts positions and normals into Attuned's Minecraft/Blockbench model space,
- preserves indices and UVs,
- strips embedded images,
- writes `src/main/resources/assets/attuned/gltf/ocean_relic_trident.glb`.

The item definition supplies
`attuned:textures/item/ocean_relic_trident_blockbench.png` as the texture. Its
`.mcmeta` clamps sampling so unused atlas space does not bleed into the mesh.

## Minecraft 26.2 Render Detail

The source mesh is triangles, but the current item/entity cutout render buffers
consume quads. `GltfMeshSpecialRenderer` emits each triangle as `A, B, C, C`.
That makes a degenerate quad whose first triangle is the intended face and whose
second triangle has zero area. Emitting only `A, B, C` causes Minecraft to stitch
unrelated triangle vertices together, producing the shredded diagonal artifacts
seen during testing.

## Minecraft 1.18.2 Port (older generation)

The 1.18.2 maintenance branch predates the special-model SPI entirely: there is no
`SpecialModelRenderers`, no `NoDataSpecialModelRenderer`, no `minecraft:special`
item model, and no `SubmitNodeCollector`. Inspecting the named (`officialMojangMappings`)
1.18.2 client jar confirmed the available render path is the legacy
`VertexConsumer` (`vertex(Matrix4f,x,y,z).color(r,g,b,a).uv(u,v).overlayCoords(o).uv2(light).normal(Matrix3f,nx,ny,nz).endVertex()`)
plus `RenderType.entityCutout(ResourceLocation)`, `DynamicTexture(NativeImage)` (the
single-argument constructor), `NativeImage.read(InputStream)`, `new ResourceLocation(ns,path)`,
and the synchronous `SimpleSynchronousResourceReloadListener.onResourceManagerReload(ResourceManager)`.
JOML is not on the classpath and `com.mojang.math.Matrix4f` lacks the
`transformPosition`/`transformDirection` helpers, so `AttunedGltfModels` keeps a tiny
internal column-major matrix while baking scene-node transforms and only touches the
Mojang math types at the `VertexConsumer` boundary.

Because the SPI is absent, the owned mesh is rendered through Fabric's
`BuiltinItemRendererRegistry`. The temporary harpoon is a vanilla `Items.TRIDENT`
stack carrying Attuned marker NBT (see `HarpoonBehavior`), not a registered item, so
`GltfMeshSpecialRenderer.init()` registers a single dispatcher against `Items.TRIDENT`:
stacks with the `AttunedHarpoon`/`attuned:offshore_harpoon` marker draw the shared
`AttunedGltfModels` mesh through `MultiBufferSource.getBuffer(RenderType.entityCutout(texture))`,
and every other trident falls back to the vanilla `TridentModel` so unmarked tridents
keep rendering normally. The item JSON stays on the legacy `minecraft:model` definition
(no `minecraft:special`).

### Limitations on 1.18.2

- The dispatcher rides the BlockEntityWithoutLevelRenderer hook, which only covers the
  3D held/dropped/thrown trident pose. The inventory/GUI slot keeps the vanilla trident
  sprite (the `ocean_relic_trident.json` item definition's `minecraft:select` /
  `minecraft:condition` model tree is a newer-format data file that this generation does
  not consume), which matches how vanilla tridents already look in the hotbar.
- The thrown-trident projectile keeps the vanilla `ThrownTridentRenderer`. Reworking it
  here would require overwriting the entity render path; the held-mesh override already
  delivers the owned glTF/Blockbench mesh, so the projectile renderer mixin stays an
  inert stub on this branch (`ThrownTridentRendererMixin` is not listed in
  `attuned.client.mixins.json`).
