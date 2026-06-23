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
