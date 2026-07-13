from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw

MODEL_PATH = Path("src/main/resources/assets/attuned/models/item/ocean_relic_trident.json")
THROWING_MODEL_PATH = Path("src/main/resources/assets/attuned/models/item/ocean_relic_trident_throwing.json")
INVENTORY_MODEL_PATH = Path("src/main/resources/assets/attuned/models/item/ocean_relic_trident_inventory.json")
ITEM_DEFINITION_PATH = Path("src/main/resources/assets/attuned/items/ocean_relic_trident.json")
PROJECTILE_ITEM_DEFINITION_PATH = Path("src/main/resources/assets/attuned/items/ocean_relic_trident_projectile.json")
REPORT_PATH = Path("build/asset-previews/ocean-relic-trident/ocean_relic_trident_voxel_report.json")
SPRITE_PATH = Path("src/main/resources/assets/attuned/textures/item/ocean_relic_trident_inventory.png")
OFFSHORE_SPRITE_PATH = Path("src/main/resources/assets/attuned/textures/item/offshore_harpoon.png")
PALETTE_PATH = Path("src/main/resources/assets/attuned/textures/item/ocean_relic_trident_voxel_palette.png")
TEXTURE_ID = "attuned:item/ocean_relic_trident_voxel_palette"
MODEL_MIN_COORD = -16.0
MODEL_MAX_COORD = 32.0

# Curated palette. The first-pass model reused a palette quantized from a concept render,
# which made most cuboids read as the same teal material. This explicit palette gives the
# model clear material bands: sea-glass prongs, salt-dark iron, driftwood, oxidized copper,
# amethyst focus core, coral, kelp, and stormwater glow.
PALETTE: list[tuple[int, int, int, int]] = [
	(222, 255, 250, 255),  # 00 foam_highlight
	(116, 250, 255, 255),  # 01 seafoam_glow
	(42, 218, 228, 255),   # 02 teal_bright
	(27, 153, 168, 255),   # 03 teal_mid
	(14, 83, 96, 255),     # 04 teal_dark
	(178, 255, 252, 255),  # 05 glass_glint
	(31, 103, 128, 255),   # 06 glass_shadow
	(155, 176, 178, 255),  # 07 iron_highlight
	(91, 111, 118, 255),   # 08 iron_mid
	(43, 53, 61, 255),     # 09 iron_dark
	(17, 27, 34, 255),     # 10 iron_outline
	(216, 143, 66, 255),   # 11 copper_highlight
	(157, 91, 42, 255),    # 12 copper_mid
	(86, 50, 31, 255),     # 13 copper_dark
	(83, 178, 137, 255),   # 14 verdigris
	(176, 117, 57, 255),   # 15 wood_highlight
	(136, 87, 45, 255),     # 16 wood_mid
	(90, 58, 36, 255),      # 17 wood_dark
	(49, 35, 28, 255),      # 18 wood_shadow
	(218, 164, 255, 255),  # 19 amethyst_light
	(133, 75, 212, 255),   # 20 amethyst_mid
	(66, 36, 100, 255),    # 21 amethyst_dark
	(243, 128, 110, 255),  # 22 coral_light
	(177, 77, 78, 255),    # 23 coral_mid
	(110, 167, 82, 255),   # 24 kelp_highlight
	(64, 108, 63, 255),    # 25 kelp_mid
	(70, 127, 194, 255),   # 26 storm_blue
	(7, 13, 19, 255),      # 27 deep_shadow
	(255, 211, 97, 255),   # 28 gold_light
	(196, 137, 38, 255),   # 29 gold_mid
	(222, 228, 204, 255),  # 30 pearl
	(126, 139, 126, 255),  # 31 pearl_shadow
	(255, 255, 255, 255),  # 32 white_spark
	(4, 220, 250, 210),    # 33 translucent_stormwater
]

MATERIALS: dict[str, dict[str, int]] = {
	"glow": {"north": 1, "south": 1, "east": 2, "west": 2, "up": 0, "down": 3},
	"glass": {"north": 2, "south": 3, "east": 3, "west": 4, "up": 5, "down": 6},
	"glass_dark": {"north": 3, "south": 4, "east": 4, "west": 6, "up": 2, "down": 4},
	"iron": {"north": 8, "south": 9, "east": 8, "west": 9, "up": 7, "down": 10},
	"iron_dark": {"north": 9, "south": 10, "east": 9, "west": 10, "up": 8, "down": 27},
	"outline": {"north": 10, "south": 27, "east": 10, "west": 27, "up": 9, "down": 27},
	"copper": {"north": 12, "south": 13, "east": 12, "west": 13, "up": 11, "down": 13},
	"verdigris": {"north": 14, "south": 4, "east": 14, "west": 4, "up": 1, "down": 6},
	"wood": {"north": 16, "south": 17, "east": 16, "west": 17, "up": 15, "down": 18},
	"wood_dark": {"north": 17, "south": 18, "east": 17, "west": 18, "up": 16, "down": 18},
	"amethyst": {"north": 20, "south": 21, "east": 20, "west": 21, "up": 19, "down": 21},
	"amethyst_glow": {"north": 19, "south": 20, "east": 19, "west": 20, "up": 32, "down": 21},
	"coral": {"north": 22, "south": 23, "east": 22, "west": 23, "up": 22, "down": 23},
	"kelp": {"north": 24, "south": 25, "east": 24, "west": 25, "up": 24, "down": 25},
	"pearl": {"north": 30, "south": 31, "east": 30, "west": 31, "up": 32, "down": 31},
	"gold": {"north": 29, "south": 13, "east": 29, "west": 13, "up": 28, "down": 13},
	"storm": {"north": 26, "south": 6, "east": 26, "west": 6, "up": 1, "down": 4},
}


def main() -> None:
	elements = build_elements()
	base_model = build_model(elements, base_display(),
		"Curated ocean relic cuboid model with sea-glass prongs, driftwood grip, copper bands, and amethyst focus core")
	throwing_model = build_model(elements, throwing_display(),
		"Curated ocean relic throwing/wind-up model with prongs oriented forward")
	inventory_model = build_inventory_model()
	assert_hand_anchored_display_contracts(base_model, throwing_model)
	item_definition = build_item_definition()
	projectile_definition = build_projectile_item_definition()

	MODEL_PATH.write_text(json.dumps(base_model, indent="	") + "\n", encoding="utf-8")
	THROWING_MODEL_PATH.write_text(json.dumps(throwing_model, indent="	") + "\n", encoding="utf-8")
	INVENTORY_MODEL_PATH.write_text(json.dumps(inventory_model, indent="	") + "\n", encoding="utf-8")
	ITEM_DEFINITION_PATH.write_text(json.dumps(item_definition, indent="	") + "\n", encoding="utf-8")
	PROJECTILE_ITEM_DEFINITION_PATH.write_text(json.dumps(projectile_definition, indent="	") + "\n", encoding="utf-8")
	write_palette(PALETTE_PATH)
	# The GUI sprite is curated pixel art derived from the final GLB appearance.
	# Keep it as an authored asset instead of replacing it with the older
	# procedural palette sketch whenever this model builder runs.
	if not SPRITE_PATH.is_file():
		raise FileNotFoundError(f"Missing curated Ocean Relic inventory sprite: {SPRITE_PATH}")
	write_sprite(OFFSHORE_SPRITE_PATH)
	REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
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


def build_inventory_model() -> dict[str, Any]:
	return {
		"parent": "minecraft:item/generated",
		"textures": {
			"layer0": "attuned:item/ocean_relic_trident_inventory",
			"particle": "attuned:item/ocean_relic_trident_inventory",
		},
		"display": {
			# The sprite already has the intended Minecraft diagonal.
			"gui": transform([0, 0, 0], [0, 0, 0], [1.08, 1.08, 1.08]),
			"ground": transform([0, 0, -18], [0, 2, 0], [0.5, 0.5, 0.5]),
			"fixed": transform([0, 0, -18], [0, 0, 0], [0.85, 0.85, 0.85]),
		},
	}


def base_display() -> dict[str, Any]:
	return {
		"gui": transform([15, -25, -5], [2, 1, 0], [0.48, 0.48, 0.48]),
		"firstperson_righthand": transform([0, -90, 25], [1.5, 5.5, 1], [0.88, 0.88, 0.88]),
		"firstperson_lefthand": transform([0, 90, -25], [1.5, 5.5, 1], [0.88, 0.88, 0.88]),
		"thirdperson_righthand": transform([0, 60, 0], [0.0718, 0, 0.9282], [1, 1, 1]),
		"thirdperson_lefthand": transform([0, 60, 0], [0.0718, 0, 1.0718], [1, 1, 1]),
		"ground": transform([0, 0, 0], [4, 4, 2], [0.49, 0.49, 0.49]),
		"fixed": transform([0, 180, 0], [-2, 4, -5], [0.42, 0.42, 0.42]),
	}


def throwing_display() -> dict[str, Any]:
	return {
		"gui": transform([15, -25, -5], [2, 1, 0], [0.48, 0.48, 0.48]),
		"firstperson_righthand": transform([0, -90, 25], [1.5, 5.5, 1], [0.88, 0.88, 0.88]),
		"firstperson_lefthand": transform([0, 90, -25], [1.5, 5.5, 1], [0.88, 0.88, 0.88]),
		"thirdperson_righthand": transform([0, 90, 180], [0, 0, 1], [1, 1, 1]),
		"thirdperson_lefthand": transform([0, 90, 180], [0, 0, 1], [1, 1, 1]),
		"ground": transform([0, 0, 0], [4, 4, 2], [0.49, 0.49, 0.49]),
		"fixed": transform([0, 180, 0], [-2, 4, -5], [0.42, 0.42, 0.42]),
	}


def build_item_definition() -> dict[str, Any]:
	inventory_model = {
		"type": "minecraft:model",
		"model": "attuned:item/ocean_relic_trident_inventory",
	}
	return {
		"model": {
			"type": "minecraft:select",
			"property": "minecraft:display_context",
			"cases": [
				{
					"when": ["gui", "ground", "fixed", "on_shelf"],
					"model": inventory_model,
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


def build_projectile_item_definition() -> dict[str, Any]:
	return {
		"model": {
			"type": "minecraft:model",
			"model": "attuned:item/ocean_relic_trident_throwing",
		},
	}


def build_elements() -> list[dict[str, Any]]:
	elements: list[dict[str, Any]] = []

	def box(name: str, from_: list[float], to: list[float], material: str | int | dict[str, int], shade: bool = True) -> None:
		from_ = round_triplet(from_)
		to = round_triplet(to)
		assert_model_bounds(name, from_, to)
		elements.append({
			"name": name,
			"from": from_,
			"to": to,
			"shade": shade,
			"faces": faces(material),
		})

	# Minecraft-style item geometry: a slim extruded silhouette with a few bold
	# material blocks, not a dense 3D sculpture. The flat inventory icon carries the
	# tiny pixel-art detail; the held/projectile cuboids only need to read at arm scale.
	box("shaft_outline", [6.72, -13.4, 7.0], [9.28, 12.0, 10.15], "outline")
	box("driftwood_grip_core", [7.18, -13.0, 7.35], [8.82, 11.55, 9.8], "wood")
	box("driftwood_grip_highlight", [7.52, -12.4, 9.8], [8.16, 10.8, 10.45], "wood", shade=False)
	box("driftwood_lower_shadow_notch", [6.9, -8.6, 7.15], [7.35, -5.1, 9.65], "wood_dark")
	box("driftwood_upper_shadow_notch", [8.65, 1.0, 7.15], [9.1, 4.7, 9.65], "wood_dark")

	# Chunky bands create readable Minecraft-style pixels without making the model bulky.
	for index, y in enumerate([-10.8, -6.6, -2.4, 1.8, 6.2]):
		material = "verdigris" if index in {1, 4} else "copper"
		box(f"band_{index:02}_core", [6.45, y, 7.1], [9.55, y + 0.8, 10.15], material)
		if index in {1, 3}:
			box(f"band_{index:02}_front_glint", [7.0, y + 0.08, 10.15], [9.0, y + 0.3, 10.55],
				"glow" if material == "verdigris" else "gold", shade=False)

	box("pommel_iron_cap", [6.25, -15.3, 6.9], [9.75, -13.1, 10.2], "iron_dark")
	box("pommel_storm_glow", [7.2, -15.8, 7.55], [8.8, -14.65, 9.55], "glow", shade=False)

	# Head, crossbar, and focus: broad blocks with front highlights, like a vanilla item
	# sprite extruded into a thin third dimension.
	box("neck_iron_socket", [6.1, 11.1, 6.95], [9.9, 14.55, 10.2], "iron")
	box("neck_front_glow_channel", [7.2, 11.65, 10.2], [8.8, 13.9, 10.75], "storm", shade=False)
	box("head_socket_outline", [5.0, 14.1, 6.85], [11.0, 18.4, 10.15], "iron_dark")
	box("head_socket_seaglass", [5.8, 14.85, 7.15], [10.2, 18.1, 10.55], "glass_dark")
	box("crossbar_left_iron", [1.9, 16.2, 7.05], [7.1, 18.55, 10.1], "iron")
	box("crossbar_right_iron", [8.9, 16.2, 7.05], [14.1, 18.55, 10.1], "iron")
	box("crossbar_left_glass_inlay", [2.55, 16.85, 10.1], [6.5, 17.65, 10.7], "glass", shade=False)
	box("crossbar_right_glass_inlay", [9.5, 16.85, 10.1], [13.45, 17.65, 10.7], "glass", shade=False)
	box("crossbar_center_gold_ring", [6.35, 16.7, 6.95], [9.65, 19.2, 10.25], "gold")
	box("focus_core_frame", [6.65, 15.05, 10.05], [9.35, 17.95, 10.75], "iron_dark")
	box("focus_core_amethyst", [7.15, 15.6, 10.35], [8.85, 17.45, 10.95], "amethyst_glow", shade=False)

	# Three clear tines. Fewer blocks and shallow depth keep the silhouette closer to
	# Minecraft's item language while staying custom for held and thrown rendering.
	prong("left", box, 2.55, 4.85, 18.15, 30.35)
	prong("center", box, 6.75, 9.25, 18.0, 31.2)
	prong("right", box, 11.15, 13.45, 18.15, 30.35)

	# Harpoon hooks and asymmetric accents keep it distinct from the vanilla trident.
	box("left_outer_barb_iron", [1.65, 23.6, 7.25], [3.15, 25.45, 10.0], "iron_dark")
	box("left_outer_barb_glass", [1.95, 24.15, 10.0], [2.95, 25.05, 10.65], "glass", shade=False)
	box("right_outer_barb_iron", [12.85, 23.6, 7.25], [14.35, 25.45, 10.0], "iron_dark")
	box("right_outer_barb_glass", [13.05, 24.15, 10.0], [14.05, 25.05, 10.65], "glass", shade=False)
	box("left_inner_hook", [4.6, 21.0, 7.25], [5.95, 22.65, 9.9], "verdigris")
	box("right_inner_hook", [10.05, 21.0, 7.25], [11.4, 22.65, 9.9], "verdigris")

	box("coral_left_badge", [4.35, 8.7, 7.0], [5.45, 10.4, 8.35], "coral")
	box("kelp_right_wrap", [10.75, 10.2, 8.7], [11.65, 12.6, 9.8], "kelp")
	box("pearl_lower_inset", [6.3, -4.85, 7.05], [7.35, -3.75, 8.1], "pearl", shade=False)
	box("pearl_upper_inset", [8.65, 7.15, 8.95], [9.7, 8.25, 10.0], "pearl", shade=False)
	box("storm_rune_lower", [7.15, -0.95, 10.05], [8.85, -0.35, 10.65], "storm", shade=False)
	box("storm_rune_upper", [7.05, 12.55, 10.65], [8.95, 13.15, 10.95], "glow", shade=False)

	return elements


def prong(
	name: str,
	box: Any,
	x0: float,
	x1: float,
	y0: float,
	y1: float,
) -> None:
	# Broad dark backer plus a front sea-glass blade: readable like pixel art, not noisy
	# like a miniature block build.
	box(f"{name}_prong_dark_base", [x0, y0, 7.0], [x1, y0 + 3.5, 10.0], "iron_dark")
	box(f"{name}_prong_dark_rib", [x0 + 0.15, y0 + 3.3, 7.15], [x1 - 0.15, y1 - 2.0, 9.85], "iron")
	box(f"{name}_prong_glass_front", [x0 + 0.42, y0 + 0.65, 9.85], [x1 - 0.42, y1 - 2.0, 10.75], "glass", shade=False)
	box(f"{name}_prong_tapered_tip", [x0 + 0.62, y1 - 2.0, 7.35], [x1 - 0.62, y1, 9.8], "glow", shade=False)


def transform(rotation: list[float], translation: list[float], scale: list[float]) -> dict[str, list[float]]:
	return {
		"rotation": rotation,
		"translation": translation,
		"scale": scale,
	}


def assert_hand_anchored_display_contracts(base_model: dict[str, Any], throwing_model: dict[str, Any]) -> None:
	"""Fail fast if held/throwing transforms drift back to floating vanilla offsets."""
	display = base_model["display"]
	held = display["thirdperson_righthand"]
	assert_translation_between(held, 0, 0.0, 0.15, "held third-person X grip-centred anchor")
	assert_translation_between(held, 1, -0.05, 0.05, "held third-person Y grip-centred anchor")
	assert_translation_between(held, 2, 0.85, 1.0, "held third-person Z hand plane")
	assert_translation_between(display["firstperson_righthand"], 1, 5.0, 7.0, "held first-person Y hand anchor")
	assert_scale_between(held, 0.99, 1.01, "held third-person vanilla-length GLB scale")

	throwing = throwing_model["display"]["thirdperson_righthand"]
	assert_translation_between(throwing, 0, -0.05, 0.05, "throwing third-person X raised-hand pivot")
	assert_translation_between(throwing, 1, -0.05, 0.05, "throwing third-person Y raised-hand pivot")
	assert_translation_between(throwing, 2, 0.95, 1.05, "throwing third-person Z hand plane")
	assert_scale_between(throwing, 0.99, 1.01, "throwing third-person vanilla-length GLB scale")


def assert_translation_between(display: dict[str, list[float]], index: int, minimum: float, maximum: float, label: str) -> None:
	value = display["translation"][index]
	if value < minimum or value > maximum:
		raise ValueError(f"{label} should be in [{minimum}, {maximum}], got {value}")


def assert_scale_between(display: dict[str, list[float]], minimum: float, maximum: float, label: str) -> None:
	for axis, value in enumerate(display["scale"]):
		if value < minimum or value > maximum:
			raise ValueError(f"{label} axis {axis} should be in [{minimum}, {maximum}], got {value}")


def faces(material: str | int | dict[str, int]) -> dict[str, dict[str, Any]]:
	if isinstance(material, str):
		material = MATERIALS[material]
	if isinstance(material, int):
		material = {face: material for face in ("north", "south", "east", "west", "up", "down")}
	return {face: {"texture": "#palette", "uv": uv(material[face])}
		for face in ("north", "south", "east", "west", "up", "down")}


def uv(swatch: int) -> list[int]:
	return [swatch % 16, swatch // 16, swatch % 16 + 1, swatch // 16 + 1]


def write_palette(path: Path) -> None:
	path.parent.mkdir(parents=True, exist_ok=True)
	image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	for index, rgba in enumerate(PALETTE):
		image.putpixel((index % 16, index // 16), rgba)
	image.save(path)


def write_sprite(path: Path) -> None:
	path.parent.mkdir(parents=True, exist_ok=True)
	image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
	draw = ImageDraw.Draw(image, "RGBA")
	p = PALETTE

	def line(points: list[tuple[int, int]], color: tuple[int, int, int, int], width: int) -> None:
		draw.line(points, fill=color, width=width, joint="curve")

	def diamond(cx: int, cy: int, r: int, color: tuple[int, int, int, int]) -> None:
		draw.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], fill=color)

	# Soft stormwater halo, then crisp pixel-art silhouette.
	line([(18, 56), (36, 28), (47, 8)], p[33], 8)
	line([(7, 57), (18, 52)], p[27], 5)
	line([(8, 57), (18, 52)], p[1], 2)
	line([(18, 56), (36, 28), (47, 8)], p[27], 6)
	line([(18, 56), (35, 30)], p[18], 4)
	line([(19, 54), (36, 27)], p[16], 2)

	# Grip bands and pommel.
	for x, y in [(23, 48), (27, 42), (30, 36), (33, 31)]:
		draw.rectangle([x - 3, y - 2, x + 3, y + 1], fill=p[13])
		draw.rectangle([x - 2, y - 2, x + 2, y - 2], fill=p[11])
	diamond(17, 57, 4, p[10])
	diamond(17, 57, 2, p[1])

	# Head socket and crossbar.
	diamond(36, 27, 8, p[10])
	diamond(36, 27, 6, p[8])
	diamond(36, 27, 4, p[20])
	diamond(36, 27, 2, p[19])
	line([(29, 25), (43, 18)], p[10], 8)
	line([(30, 25), (42, 19)], p[8], 5)
	line([(31, 24), (41, 20)], p[2], 2)

	# Three prongs with a longer central tine and visible sea-glass highlights.
	for start, end, width in [((33, 22), (39, 7), 5), ((39, 19), (48, 4), 5), ((44, 17), (55, 8), 5)]:
		line([start, end], p[10], width + 2)
		line([start, end], p[3], width)
		line([(start[0] + 1, start[1] - 1), (end[0] + 1, end[1] - 1)], p[1], max(1, width - 3))
		diamond(end[0], end[1], 2, p[32])

	# Harpoon barbs, coral/kelp/story details.
	line([(31, 19), (25, 20)], p[10], 5)
	line([(31, 19), (25, 20)], p[3], 3)
	line([(48, 14), (54, 15)], p[10], 5)
	line([(48, 14), (54, 15)], p[3], 3)
	diamond(29, 35, 2, p[23])
	diamond(39, 23, 2, p[24])
	diamond(41, 31, 2, p[30])
	diamond(31, 39, 1, p[1])
	image.save(path)


def build_report(elements: list[dict[str, Any]]) -> dict[str, Any]:
	mins = [min(element["from"][axis] for element in elements) for axis in range(3)]
	maxs = [max(element["to"][axis] for element in elements) for axis in range(3)]
	size = [round(maxs[axis] - mins[axis], 3) for axis in range(3)]
	return {
		"source_sprite": str(SPRITE_PATH).replace("\\", "/"),
		"inventory_sprite": str(SPRITE_PATH).replace("\\", "/"),
		"minecraft_model": str(MODEL_PATH).replace("\\", "/"),
		"minecraft_throwing_model": str(THROWING_MODEL_PATH).replace("\\", "/"),
		"minecraft_inventory_model": str(INVENTORY_MODEL_PATH).replace("\\", "/"),
		"item_definition": str(ITEM_DEFINITION_PATH).replace("\\", "/"),
		"projectile_item_definition": str(PROJECTILE_ITEM_DEFINITION_PATH).replace("\\", "/"),
		"palette_texture": str(PALETTE_PATH).replace("\\", "/"),
		"strategy": "curated_trident_silhouette_from_concept_palette",
		"enhancement_strategy": "material_separated_curated_trident_silhouette",
		"reason": "The raw concept-sprite voxelization and first curated pass had weak material separation and vanilla trident hand transforms made the oversized cuboid geometry float beside the player; this pass adds explicit material colors, face-aware shading, brighter readable prongs, driftwood grip, copper/verdigris bands, an amethyst focus core, a stronger dark silhouette, grip-centred GLB held/throwing transforms, a curated inventory sprite derived from the mesh, and a projectile item definition used by the custom thrown-harpoon renderer.",
		"palette_colors": len(PALETTE),
		"cuboids": len(elements),
		"bbox_min": mins,
		"bbox_max": maxs,
		"bbox_size": size,
	}


def round_triplet(values: list[float]) -> list[float]:
	return [round(value, 3) for value in values]


def assert_model_bounds(name: str, from_: list[float], to: list[float]) -> None:
	for label, values in (("from", from_), ("to", to)):
		for axis, value in enumerate(values):
			if value < MODEL_MIN_COORD or value > MODEL_MAX_COORD:
				raise ValueError(
					f"{name} {label}[{axis}]={value} exceeds Minecraft's "
					f"cuboid element bounds [{MODEL_MIN_COORD}, {MODEL_MAX_COORD}]"
				)


if __name__ == "__main__":
	main()
