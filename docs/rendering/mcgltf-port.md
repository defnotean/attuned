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

The original source GLB is about 54 MB because it embeds large PNG textures. The
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

## Minecraft 1.21.1 Port (older generation)

The 1.21.1 maintenance branch predates the special-model SPI: there is no
`SpecialModelRenderers`, no `NoDataSpecialModelRenderer`, no `minecraft:special`
item model, and no `SubmitNodeCollector`. Inspecting the named (`layered`
1.21.1) client jar confirmed the available render path is the *builder*
`VertexConsumer`
(`addVertex(PoseStack.Pose,x,y,z).setColor(r,g,b,a).setUv(u,v).setOverlay(o).setLight(light).setNormal(PoseStack.Pose,nx,ny,nz)`)
plus `RenderType.entityCutout(ResourceLocation)`, `DynamicTexture(NativeImage)`,
`NativeImage.read(InputStream)`, `ResourceLocation.fromNamespaceAndPath(ns,path)`,
the `Optional<Resource>` `ResourceManager.getResource` + `Resource.open()` pair,
and the synchronous `SimpleSynchronousResourceReloadListener.onResourceManagerReload(ResourceManager)`.
Unlike 1.18.2, JOML is on the classpath and `PoseStack.Pose` exposes
`pose()`/`normal()` JOML matrices, so the builder `addVertex(Pose,..)` /
`setNormal(Pose,..)` overloads fold the transform in for us and `AttunedGltfModels`
keeps its own tiny column-major matrix only while baking scene-node transforms.

Because the SPI is absent, the owned mesh is rendered through Fabric's
`BuiltinItemRendererRegistry`. The temporary harpoon is a vanilla `Items.TRIDENT`
stack carrying Attuned marker data in `DataComponents.CUSTOM_DATA` (see
`HarpoonBehavior`), not a registered item, so `GltfMeshSpecialRenderer.init()`
registers a single dispatcher against `Items.TRIDENT`: stacks for which
`HarpoonBehavior.isTemporaryHarpoon` is true draw the shared `AttunedGltfModels`
mesh through `MultiBufferSource.getBuffer(RenderType.entityCutout(texture))`, and
every other trident falls back to the vanilla `TridentModel` so unmarked tridents
keep rendering normally. The item JSON stays on the legacy `minecraft:model`
definition (no `minecraft:special`).

### Limitations on 1.21.1

- The dispatcher rides the BlockEntityWithoutLevelRenderer hook, which only covers
  the 3D held/dropped/thrown trident pose. The inventory/GUI slot keeps the flat
  trident sprite (the harpoon stack also carries `DataComponents.CUSTOM_MODEL_DATA`
  `7123001`, which the vanilla `trident`/`trident_in_hand` model overrides route to
  the Attuned inventory art), matching how vanilla tridents look in the hotbar.
- The thrown-trident projectile keeps the vanilla `ThrownTridentRenderer`; this
  branch ships no `ThrownTridentRendererMixin`/`ThrownTridentRenderStateMixin`
  (those are the 1.21.11 render-state path). The server-side `ThrownTridentMixin`
  only handles harpoon expiry/cleanup, not rendering. The held-mesh override already
  delivers the owned glTF/Blockbench mesh.
