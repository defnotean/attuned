from __future__ import annotations

import json
from pathlib import Path
from typing import Any

MODEL_PATH = Path("src/main/resources/assets/attuned/models/item/ocean_relic_trident.json")
THROWING_MODEL_PATH = Path("src/main/resources/assets/attuned/models/item/ocean_relic_trident_throwing.json")
ITEM_DEFINITION_PATH = Path("src/main/resources/assets/attuned/items/ocean_relic_trident.json")
REPORT_PATH = Path("docs/superpowers/assets/ocean-relic-trident/ocean_relic_trident_voxel_report.json")
SPRITE_PATH = Path("src/main/resources/assets/attuned/textures/item/ocean_relic_trident.png")
PALETTE_PATH = Path("src/main/resources/assets/attuned/textures/item/ocean_relic_trident_voxel_palette.png")
TEXTURE_ID = "attuned:item/ocean_relic_trident_voxel_palette"


def main() -> None:
	elements = build_elements()
	base_model = build_model(elements, base_display(),
		"Built by tools/build_ocean_relic_trident_model.py from the Ocean Relic Trident palette")
	throwing_model = build_model(elements, throwing_display(),
		"Generated throwing/wind-up model by tools/build_ocean_relic_trident_model.py")
	item_definition = build_item_definition()

	MODEL_PATH.write_text(json.dumps(base_model, indent="\t") + "\n", encoding="utf-8")
	THROWING_MODEL_PATH.write_text(json.dumps(throwing_model, indent="\t") + "\n", encoding="utf-8")
	ITEM_DEFINITION_PATH.write_text(json.dumps(item_definition, indent="\t") + "\n", encoding="utf-8")
	REPORT_PATH.write_text(json.dumps(build_report(elements), indent=2) + "\n", encoding="utf-8")
	print(json.dumps(build_report(elements), indent=2))


def build_model(elements: list[dict[str, Any]], display: dict[str, Any], credit: str) -> dict[str, Any]:
	return {
		"credit": credit,
		"gui_light": "front",
		"textures": {
			"palette": TEXTURE_ID,
			"particle": TEXTURE_ID,
		},
		"elements": elements,
		"display": display,
	}


def base_display() -> dict[str, Any]:
	return {
		"gui": transform([15, -25, -5], [2, 1, 0], [0.48, 0.48, 0.48]),
		"firstperson_righthand": transform([12, -24, 0], [2, -1, 0], [0.72, 0.72, 0.72]),
		"firstperson_lefthand": transform([12, 24, 0], [-2, -1, 0], [0.72, 0.72, 0.72]),
		"thirdperson_righthand": transform([18, -32, 0], [0, 1, 0], [1.23, 1.23, 1.23]),
		"thirdperson_lefthand": transform([18, 32, 0], [0, 1, 0], [1.23, 1.23, 1.23]),
		"ground": transform([0, 0, 0], [4, 4, 2], [0.32, 0.32, 0.32]),
		"fixed": transform([0, 180, 0], [-2, 4, -5], [0.42, 0.42, 0.42]),
	}


def throwing_display() -> dict[str, Any]:
	return {
		"gui": transform([15, -25, -5], [2, 1, 0], [0.48, 0.48, 0.48]),
		"firstperson_righthand": transform([0, -90, 25], [-3, 11, 1], [0.62, 0.62, 0.62]),
		"firstperson_lefthand": transform([0, 90, -25], [13, 11, 1], [0.62, 0.62, 0.62]),
		"thirdperson_righthand": transform([0, 90, 0], [8, -15, 9], [0.68, 0.68, 0.68]),
		"thirdperson_lefthand": transform([0, 90, 0], [8, -15, -7], [0.68, 0.68, 0.68]),
		"ground": transform([0, 0, 0], [4, 4, 2], [0.32, 0.32, 0.32]),
		"fixed": transform([0, 180, 0], [-2, 4, -5], [0.42, 0.42, 0.42]),
	}


def build_item_definition() -> dict[str, Any]:
	return {
		"model": {
			"type": "minecraft:select",
			"property": "minecraft:display_context",
			"cases": [
				{
					"when": ["gui", "ground", "fixed", "on_shelf"],
					"model": {
						"type": "minecraft:model",
						"model": "attuned:item/ocean_relic_trident",
					},
				},
			],
			"fallback": {
				"type": "minecraft:condition",
				"property": "minecraft:using_item",
				"on_false": {
					"type": "minecraft:model",
					"model": "attuned:item/ocean_relic_trident",
				},
				"on_true": {
					"type": "minecraft:model",
					"model": "attuned:item/ocean_relic_trident_throwing",
				},
			},
		},
	}


def build_elements() -> list[dict[str, Any]]:
	elements: list[dict[str, Any]] = []

	def box(name: str, from_: list[float], to: list[float], swatch: int, shade: bool = True) -> None:
		elements.append({
			"name": name,
			"from": round_triplet(from_),
			"to": round_triplet(to),
			"shade": shade,
			"faces": faces(swatch),
		})

	# Long readable shaft. Short color-banded cuboids keep the generated palette visible
	# without destroying the silhouette at Minecraft hand distance.
	shaft_colors = [27, 26, 24, 22, 21, 16, 13, 11, 10, 5, 2, 1, 7, 8]
	y = -12.0
	for index, swatch in enumerate(shaft_colors):
		next_y = y + 2.0
		width = 1.28 if index % 3 else 1.45
		x0 = 8.0 - width / 2.0
		z0 = 8.0 - width / 2.0
		box(f"shaft_{index:02}", [x0, y, z0], [16.0 - x0, next_y, 16.0 - z0], swatch)
		y = next_y

	# Pommel and grip bindings.
	box("pommel_core", [6.75, -13.5, 6.75], [9.25, -11.25, 9.25], 21)
	box("pommel_glow", [7.2, -14.5, 7.2], [8.8, -13.2, 8.8], 1)
	box("lower_wrap_gold", [6.55, -9.8, 6.55], [9.45, -8.8, 9.45], 0)
	box("lower_wrap_violet", [6.35, -7.1, 6.35], [9.65, -6.1, 9.65], 20)
	box("upper_wrap_gold", [6.55, -3.6, 6.55], [9.45, -2.7, 9.45], 0)
	box("upper_wrap_violet", [6.35, 1.5, 6.35], [9.65, 2.4, 9.65], 18)

	# Head socket and crossbar, deliberately wider than the shaft so the trident
	# reads instantly in third person.
	box("socket_dark", [5.6, 14.4, 6.55], [10.4, 18.7, 9.45], 24)
	box("socket_teal", [6.1, 15.0, 6.25], [9.9, 19.2, 9.75], 10)
	box("crossbar_left", [2.8, 17.3, 6.8], [7.35, 19.6, 9.2], 5)
	box("crossbar_right", [8.65, 17.3, 6.8], [13.2, 19.6, 9.2], 5)
	box("crossbar_center_glow", [6.7, 17.9, 6.35], [9.3, 20.4, 9.65], 1)

	# Three prongs with separated lanes and bright tips.
	prong_layers = [
		("base", 18.8, 22.8, 2),
		("mid", 22.8, 26.8, 1),
		("tip", 26.8, 30.8, 7),
	]
	for suffix, y0, y1, swatch in prong_layers:
		box(f"left_prong_{suffix}", [3.25, y0, 7.05], [4.95, y1, 8.95], swatch)
		box(f"center_prong_{suffix}", [7.05, y0, 6.9], [8.95, min(31.6, y1 + 1.1), 9.1], swatch)
		box(f"right_prong_{suffix}", [11.05, y0, 7.05], [12.75, y1, 8.95], swatch)
	box("left_prong_point", [3.55, 30.8, 7.35], [4.65, 32.0, 8.65], 8)
	box("center_prong_point", [7.35, 31.6, 7.25], [8.65, 32.0, 8.75], 8)
	box("right_prong_point", [11.35, 30.8, 7.35], [12.45, 32.0, 8.65], 8)

	# Outer barbs make the side prongs unmistakable without becoming bulky.
	box("left_outer_barb", [2.25, 24.0, 7.25], [3.45, 26.0, 8.75], 7)
	box("right_outer_barb", [12.55, 24.0, 7.25], [13.75, 26.0, 8.75], 7)
	box("left_inner_barb", [4.8, 21.6, 7.15], [6.05, 23.2, 8.85], 9)
	box("right_inner_barb", [9.95, 21.6, 7.15], [11.2, 23.2, 8.85], 9)

	# Small offshore relic details. These are kept shallow so they accent the
	# source art without changing the clear trident outline.
	box("coral_left", [4.8, 10.8, 6.4], [6.0, 12.2, 7.5], 20)
	box("coral_right", [10.0, 12.2, 8.5], [11.2, 13.8, 9.6], 20)
	box("kelp_left", [3.9, 15.0, 8.55], [4.7, 17.2, 9.35], 19)
	box("kelp_right", [11.3, 14.0, 6.65], [12.1, 16.2, 7.45], 19)
	box("pearl_lower", [6.2, -5.2, 6.25], [7.3, -4.1, 7.35], 0)
	box("pearl_upper", [8.8, 7.2, 8.65], [9.9, 8.3, 9.75], 0)
	box("rune_core", [7.1, 15.6, 9.45], [8.9, 17.4, 10.35], 18)

	return elements


def transform(rotation: list[float], translation: list[float], scale: list[float]) -> dict[str, list[float]]:
	return {
		"rotation": rotation,
		"translation": translation,
		"scale": scale,
	}


def faces(swatch: int) -> dict[str, dict[str, Any]]:
	uv = [swatch % 16, swatch // 16, swatch % 16 + 1, swatch // 16 + 1]
	return {
		"north": {"texture": "#palette", "uv": uv},
		"south": {"texture": "#palette", "uv": uv},
		"east": {"texture": "#palette", "uv": uv},
		"west": {"texture": "#palette", "uv": uv},
		"up": {"texture": "#palette", "uv": uv},
		"down": {"texture": "#palette", "uv": uv},
	}


def build_report(elements: list[dict[str, Any]]) -> dict[str, Any]:
	mins = [min(element["from"][axis] for element in elements) for axis in range(3)]
	maxs = [max(element["to"][axis] for element in elements) for axis in range(3)]
	size = [round(maxs[axis] - mins[axis], 3) for axis in range(3)]
	return {
		"source_sprite": str(SPRITE_PATH).replace("\\", "/"),
		"minecraft_model": str(MODEL_PATH).replace("\\", "/"),
		"minecraft_throwing_model": str(THROWING_MODEL_PATH).replace("\\", "/"),
		"item_definition": str(ITEM_DEFINITION_PATH).replace("\\", "/"),
		"palette_texture": str(PALETTE_PATH).replace("\\", "/"),
		"strategy": "curated_trident_silhouette_from_concept_palette",
		"reason": "Raw sprite voxelization looked compact and noisy in third-person hand rendering.",
		"palette_colors": 28,
		"cuboids": len(elements),
		"bbox_min": mins,
		"bbox_max": maxs,
		"bbox_size": size,
	}


def round_triplet(values: list[float]) -> list[float]:
	return [round(value, 3) for value in values]


if __name__ == "__main__":
	main()
