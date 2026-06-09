from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import textwrap
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
TMP_DIR = ROOT / "tmp/mesh_to_minecraft_item"
ASSET_ROOT = ROOT / "src/main/resources/assets"
CUSTOMIZER_MANIFEST = ROOT / "tools/asset_customizer/asset-manifest.json"
DOC_ASSET_ROOT = ROOT / "docs/superpowers/assets"


def main() -> None:
	args = parse_args()
	source = args.source.resolve()
	if not source.is_file():
		raise SystemExit(f"Source asset does not exist: {source}")

	asset_id = args.asset_id
	namespace = args.namespace
	title = args.name or pretty_name(asset_id)
	blender = find_blender(args.blender)

	work_dir = DOC_ASSET_ROOT / asset_id.replace("_", "-")
	work_dir.mkdir(parents=True, exist_ok=True)
	TMP_DIR.mkdir(parents=True, exist_ok=True)

	render_path = work_dir / f"{asset_id}_render.png"
	glb_path = work_dir / f"{asset_id}.glb"
	report_path = work_dir / f"{asset_id}_report.json"
	run_blender(blender, source, render_path, glb_path, report_path, args.camera)

	texture_path = ASSET_ROOT / namespace / "textures/item" / f"{asset_id}.png"
	model_path = ASSET_ROOT / namespace / "models/item" / f"{asset_id}.json"
	definition_path = ASSET_ROOT / namespace / "items" / f"{asset_id}.json"
	texture_path.parent.mkdir(parents=True, exist_ok=True)
	model_path.parent.mkdir(parents=True, exist_ok=True)
	definition_path.parent.mkdir(parents=True, exist_ok=True)

	write_icon(render_path, texture_path, args.padding)
	write_item_model(model_path, namespace, asset_id)
	write_item_definition(definition_path, namespace, asset_id)
	if args.update_manifest:
		update_manifest(asset_id, title, namespace, model_path, texture_path, definition_path, glb_path)

	print(json.dumps({
		"asset_id": asset_id,
		"source": str(source),
		"render": str(render_path),
		"glb": str(glb_path),
		"report": str(report_path),
		"texture": str(texture_path),
		"model": str(model_path),
		"definition": str(definition_path),
	}, indent=2))


def parse_args() -> argparse.Namespace:
	parser = argparse.ArgumentParser(
		description="Convert a Meshy/Tripo-style 3D source asset into a Minecraft item sprite/model.")
	parser.add_argument("source", type=Path, help="FBX, GLB, glTF, OBJ, or Blender-supported source file.")
	parser.add_argument("--asset-id", required=True, help="Minecraft item asset id, for example frostbound_trident.")
	parser.add_argument("--name", help="Display name for the asset customizer.")
	parser.add_argument("--namespace", default="attuned", help="Minecraft namespace.")
	parser.add_argument("--blender", type=Path, help="Optional path to blender.exe.")
	parser.add_argument("--camera", choices=("diagonal", "front", "side"), default="diagonal")
	parser.add_argument("--padding", type=float, default=0.08, help="Transparent padding around the item icon.")
	parser.add_argument("--update-manifest", action="store_true", help="Add/update this asset in the local customizer.")
	return parser.parse_args()


def find_blender(explicit: Path | None) -> Path:
	candidates: list[Path] = []
	if explicit is not None:
		candidates.append(explicit)
	found = shutil.which("blender")
	if found:
		candidates.append(Path(found))
	candidates.extend([
		Path("C:/Program Files/Blender Foundation/Blender 5.1/blender.exe"),
		Path("C:/Program Files/Blender Foundation/Blender 5.0/blender.exe"),
		Path("C:/Program Files/Blender Foundation/Blender 4.4/blender.exe"),
		Path("C:/Program Files/Blender Foundation/Blender 4.3/blender.exe"),
	])
	for candidate in candidates:
		if candidate.is_file():
			return candidate
	raise SystemExit("Could not find Blender. Install Blender or pass --blender <path>.")


def run_blender(
	blender: Path,
	source: Path,
	render_path: Path,
	glb_path: Path,
	report_path: Path,
	camera: str,
) -> None:
	helper = TMP_DIR / "blender_import_render.py"
	helper.write_text(blender_script(), encoding="utf-8")
	command = [
		str(blender),
		"--background",
		"--python",
		str(helper),
		"--",
		str(source),
		str(render_path),
		str(glb_path),
		str(report_path),
		camera,
	]
	result = subprocess.run(command, cwd=ROOT, text=True, capture_output=True)
	if result.returncode != 0:
		print(result.stdout)
		print(result.stderr, file=sys.stderr)
		raise SystemExit(result.returncode)
	if not render_path.is_file() or not glb_path.is_file() or not report_path.is_file():
		print(result.stdout)
		print(result.stderr, file=sys.stderr)
		raise SystemExit("Blender finished without writing all expected outputs.")


def blender_script() -> str:
	return textwrap.dedent(r"""
		from __future__ import annotations

		import json
		import math
		import sys
		from pathlib import Path
		from mathutils import Vector

		import bpy


		def main() -> None:
			args = sys.argv[sys.argv.index("--") + 1:]
			source = Path(args[0])
			render_path = Path(args[1])
			glb_path = Path(args[2])
			report_path = Path(args[3])
			camera_mode = args[4]

			clear_scene()
			import_asset(source)
			meshes = [obj for obj in bpy.context.scene.objects if obj.type == "MESH"]
			if not meshes:
				raise RuntimeError(f"No mesh objects imported from {source}")
			report = collect_report(meshes)
			normalize(meshes)
			setup_material_fallbacks(meshes)
			setup_camera(camera_mode)
			setup_lighting()
			render_path.parent.mkdir(parents=True, exist_ok=True)
			glb_path.parent.mkdir(parents=True, exist_ok=True)
			report_path.parent.mkdir(parents=True, exist_ok=True)
			render(render_path)
			bpy.ops.export_scene.gltf(
				filepath=str(glb_path),
				export_format="GLB",
				use_selection=False,
				export_apply=True,
				export_materials="EXPORT",
				export_animations=False,
			)
			report.update(collect_report([obj for obj in bpy.context.scene.objects if obj.type == "MESH"], prefix="normalized_"))
			report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")


		def clear_scene() -> None:
			bpy.ops.object.select_all(action="SELECT")
			bpy.ops.object.delete()


		def import_asset(source: Path) -> None:
			ext = source.suffix.lower()
			if ext == ".fbx":
				bpy.ops.import_scene.fbx(filepath=str(source), automatic_bone_orientation=True)
			elif ext in {".glb", ".gltf"}:
				bpy.ops.import_scene.gltf(filepath=str(source))
			elif ext == ".obj":
				bpy.ops.wm.obj_import(filepath=str(source))
			elif ext == ".blend":
				with bpy.data.libraries.load(str(source)) as (data_from, data_to):
					data_to.objects = data_from.objects
				for obj in data_to.objects:
					if obj is not None:
						bpy.context.collection.objects.link(obj)
			else:
				raise RuntimeError(f"Unsupported source extension: {ext}")


		def collect_report(meshes: list[bpy.types.Object], prefix: str = "") -> dict[str, object]:
			materials = set()
			vertices = 0
			faces = 0
			names = []
			for obj in meshes:
				names.append(obj.name)
				vertices += len(obj.data.vertices)
				faces += len(obj.data.polygons)
				for slot in obj.material_slots:
					if slot.material:
						materials.add(slot.material.name)
			return {
				prefix + "mesh_count": len(meshes),
				prefix + "object_names": sorted(names),
				prefix + "vertex_count": vertices,
				prefix + "face_count": faces,
				prefix + "material_count": len(materials),
				prefix + "materials": sorted(materials),
			}


		def normalize(meshes: list[bpy.types.Object]) -> None:
			for obj in meshes:
				obj.select_set(True)
			bpy.context.view_layer.objects.active = meshes[0]
			bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
			minimum, maximum = bounds(meshes)
			center = (minimum + maximum) / 2
			size = maximum - minimum
			longest = max(size.x, size.y, size.z)
			scale = 3.2 / longest if longest else 1.0
			for obj in meshes:
				obj.location = (obj.location - center) * scale
				obj.scale *= scale
			for obj in meshes:
				obj.select_set(True)
			bpy.context.view_layer.objects.active = meshes[0]
			bpy.ops.object.transform_apply(location=True, rotation=False, scale=True)


		def bounds(meshes: list[bpy.types.Object]) -> tuple[Vector, Vector]:
			points = []
			for obj in meshes:
				matrix = obj.matrix_world
				points.extend(matrix @ Vector(corner) for corner in obj.bound_box)
			minimum = Vector((min(p.x for p in points), min(p.y for p in points), min(p.z for p in points)))
			maximum = Vector((max(p.x for p in points), max(p.y for p in points), max(p.z for p in points)))
			return minimum, maximum


		def setup_material_fallbacks(meshes: list[bpy.types.Object]) -> None:
			if any(obj.material_slots for obj in meshes):
				return
			material = bpy.data.materials.new("frostbound_fallback")
			material.diffuse_color = (0.34, 0.76, 0.92, 1.0)
			material.use_nodes = True
			principled = material.node_tree.nodes.get("Principled BSDF")
			if principled:
				principled.inputs["Base Color"].default_value = (0.34, 0.76, 0.92, 1.0)
				principled.inputs["Roughness"].default_value = 0.48
				principled.inputs["Metallic"].default_value = 0.18
			for obj in meshes:
				obj.data.materials.append(material)


		def setup_camera(mode: str) -> None:
			bpy.ops.object.camera_add()
			camera = bpy.context.object
			if mode == "front":
				camera.location = (0, -6.0, 1.6)
				camera.rotation_euler = (math.radians(78), 0, 0)
			elif mode == "side":
				camera.location = (6.0, -1.2, 1.7)
				camera.rotation_euler = (math.radians(78), 0, math.radians(78))
			else:
				camera.location = (4.4, -6.2, 3.2)
				camera.rotation_euler = (math.radians(62), 0, math.radians(38))
			camera.data.type = "ORTHO"
			camera.data.ortho_scale = 4.4
			bpy.context.scene.camera = camera


		def setup_lighting() -> None:
			bpy.ops.object.light_add(type="AREA", location=(0, -3, 5))
			key = bpy.context.object
			key.name = "softbox_key"
			key.data.energy = 480
			key.data.size = 5.0
			bpy.ops.object.light_add(type="POINT", location=(-3, 2, 3))
			rim = bpy.context.object
			rim.name = "cyan_rim"
			rim.data.color = (0.48, 0.9, 1.0)
			rim.data.energy = 90


		def render(render_path: Path) -> None:
			scene = bpy.context.scene
			# Blender renamed the engine id across versions (BLENDER_EEVEE ->
			# BLENDER_EEVEE_NEXT in 4.2-4.4); probe instead of hardcoding.
			engine_ids = {
				item.identifier
				for item in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items
			}
			for engine in ("BLENDER_EEVEE", "BLENDER_EEVEE_NEXT", "CYCLES"):
				if engine in engine_ids:
					scene.render.engine = engine
					break
			if hasattr(scene, "eevee"):
				scene.eevee.taa_render_samples = 64
			scene.render.resolution_x = 1024
			scene.render.resolution_y = 1024
			scene.render.film_transparent = True
			scene.view_settings.view_transform = "Standard"
			scene.view_settings.look = "Medium High Contrast"
			scene.render.image_settings.file_format = "PNG"
			scene.render.filepath = str(render_path)
			bpy.ops.render.render(write_still=True)


		if __name__ == "__main__":
			main()
	""")


def write_icon(render_path: Path, out_path: Path, padding: float) -> None:
	image = Image.open(render_path).convert("RGBA")
	bbox = image.getchannel("A").getbbox()
	if bbox is None:
		raise SystemExit(f"Render had no visible pixels: {render_path}")
	left, top, right, bottom = bbox
	width = right - left
	height = bottom - top
	pad = max(1, int(max(width, height) * padding))
	cx = (left + right) / 2
	cy = (top + bottom) / 2
	side = max(width, height) + pad * 2
	crop = image.crop((
		round(cx - side / 2),
		round(cy - side / 2),
		round(cx + side / 2),
		round(cy + side / 2),
	))
	icon = crop.resize((64, 64), Image.Resampling.LANCZOS)
	icon.save(out_path)


def write_item_model(path: Path, namespace: str, asset_id: str) -> None:
	model = {
		"parent": "minecraft:item/generated",
		"textures": {
			"layer0": f"{namespace}:item/{asset_id}",
			"particle": f"{namespace}:item/{asset_id}",
		},
		"display": {
			"gui": {
				"rotation": [0, 0, 0],
				"translation": [0, 0, 0],
				"scale": [1, 1, 1],
			},
			"firstperson_righthand": {
				"rotation": [0, 0, 0],
				"translation": [2.0, -1.0, 0],
				"scale": [1, 1, 1],
			},
			"thirdperson_righthand": {
				"rotation": [0, 0, 0],
				"translation": [0, 1.0, 0],
				"scale": [0.78, 0.78, 0.78],
			},
			"ground": {
				"translation": [0, 2, 0],
				"scale": [0.5, 0.5, 0.5],
			},
			"fixed": {
				"rotation": [0, 0, 0],
				"scale": [0.9, 0.9, 0.9],
			},
		},
	}
	path.write_text(json.dumps(model, indent="\t") + "\n", encoding="utf-8")


def write_item_definition(path: Path, namespace: str, asset_id: str) -> None:
	definition = {
		"model": {
			"type": "minecraft:model",
			"model": f"{namespace}:item/{asset_id}",
		}
	}
	path.write_text(json.dumps(definition, indent="\t") + "\n", encoding="utf-8")


def update_manifest(
	asset_id: str,
	title: str,
	namespace: str,
	model_path: Path,
	texture_path: Path,
	definition_path: Path,
	glb_path: Path,
) -> None:
	manifest = []
	if CUSTOMIZER_MANIFEST.is_file():
		manifest = json.loads(CUSTOMIZER_MANIFEST.read_text(encoding="utf-8"))
	entry = {
		"id": asset_id,
		"name": title,
		"kind": "generated",
		"model": relative_for_manifest(model_path),
		"texture": relative_for_manifest(texture_path),
		"definition": relative_for_manifest(definition_path),
		"source": relative_for_manifest(glb_path),
	}
	manifest = [asset for asset in manifest if asset.get("id") != asset_id]
	manifest.append(entry)
	CUSTOMIZER_MANIFEST.write_text(json.dumps(manifest, indent="\t") + "\n", encoding="utf-8")


def relative_for_manifest(path: Path) -> str:
	return Path("../..", path.relative_to(ROOT)).as_posix()


def pretty_name(asset_id: str) -> str:
	return " ".join(part.capitalize() for part in asset_id.split("_"))


if __name__ == "__main__":
	main()
