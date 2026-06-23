from __future__ import annotations

"""Deterministic 3D model generator for the showcase Attuned models.

Rebuilds, with significantly richer silhouettes:
  - models/item/ocean_relic_trident.json + _throwing.json (elements only; the
    hand-tuned display transforms, textures, and credit keys are preserved)
  - models/item/frostbound_trident.json (flat sprite -> full voxel model) plus
    its generated ice palette texture
  - models/block/attunement_altar.json (affinity variants inherit the geometry)
  - models/block/altar_of_reweaving.json

House rules honored: the attunement altar keeps a named "top_plate" element
with a down face, no altar face uses cullface down, the trident keeps
>= 36 elements and its palette/particle texture refs, and every output is
byte-stable across runs.
"""

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
MODELS_ITEM = ROOT / "src/main/resources/assets/attuned/models/item"
MODELS_BLOCK = ROOT / "src/main/resources/assets/attuned/models/block"
TEXTURES_ITEM = ROOT / "src/main/resources/assets/attuned/textures/item"


def cube(name, frm, to, faces, rotation=None, shade=True):
	element = {"name": name, "from": list(frm), "to": list(to), "shade": shade, "faces": faces}
	if rotation is not None:
		element["rotation"] = rotation
	return element


def pal(x, y):
	"""All six faces mapped to one palette pixel."""
	face = {"texture": "#palette", "uv": [x, y, x + 1, y + 1]}
	return {side: dict(face) for side in ("north", "south", "east", "west", "up", "down")}


def pal_shaded(light, dark):
	"""Vertical surfaces lit/dark split for cheap depth: up + two faces light."""
	lx, ly = light
	dx, dy = dark
	lit = {"texture": "#palette", "uv": [lx, ly, lx + 1, ly + 1]}
	dim = {"texture": "#palette", "uv": [dx, dy, dx + 1, dy + 1]}
	return {"north": dict(lit), "east": dict(lit), "up": dict(lit),
		"south": dict(dim), "west": dict(dim), "down": dict(dim)}


def yrot(angle, origin):
	return {"origin": list(origin), "axis": "y", "angle": angle}


def zrot(angle, origin):
	return {"origin": list(origin), "axis": "z", "angle": angle}


# --- Ocean Relic Trident -----------------------------------------------------
# Palette cells (x, y) in ocean_relic_trident_voxel_palette.png:
#   aqua glows   (0,0) bright .. (4,0) deep, (5,0)/(6,0) teal tones
#   steels       (7,0) light .. (10,0) near-black
#   bronzes      (11,0) bright, (12,0) mid, (13,0) dark, (15,0) warm
#   driftwood    (0,1) light, (1,1) mid, (2,1) dark
#   accent       (3,1) violet pearl, (14,0) sea green

def ocean_relic_elements():
	# Scaled to the original showcase envelope (~47 tall, ~11 wide, bbox_min y -16)
	# because the hand-tuned display transforms and the voxel-report thresholds
	# (height >= 40, prong spread >= 10) were calibrated against that size.
	e = []
	e.append(cube("shaft_outline", (6.72, -14.6, 7.0), (9.28, 16.0, 10.15), pal(10, 0)))
	e.append(cube("shaft_core", (7.18, -14.2, 7.35), (8.82, 15.6, 9.8), pal_shaded((0, 1), (1, 1))))
	# Butt: bronze counterweight with a glow stud.
	e.append(cube("butt_cap", (6.5, -15.2, 6.8), (9.5, -13.9, 10.3), pal_shaded((12, 0), (13, 0))))
	e.append(cube("butt_stud", (7.5, -16.0, 7.8), (8.5, -15.2, 9.3), pal(2, 0)))
	# Grip: alternating leather wraps proud of the core, plus two bronze studs.
	for i, y in enumerate(range(-12, -2, 2)):
		tone = pal_shaded((2, 1), (1, 1)) if i % 2 == 0 else pal_shaded((13, 0), (2, 1))
		e.append(cube(f"grip_wrap_{i}", (7.0, y, 7.2), (9.0, y + 1.1, 10.0), tone))
	e.append(cube("grip_stud_a", (6.8, -9.6, 8.2), (7.2, -8.8, 9.0), pal(11, 0)))
	e.append(cube("grip_stud_b", (8.8, -5.6, 8.2), (9.2, -4.8, 9.0), pal(11, 0)))
	# Kelp ribbons spiraling the upper shaft.
	e.append(cube("kelp_ribbon_a", (6.6, -0.5, 8.3), (9.4, 10.5, 8.9),
		pal(14, 0), rotation=zrot(22.5, (8, 5.0, 8.6))))
	e.append(cube("kelp_ribbon_b", (6.6, 2.5, 8.3), (9.4, 13.5, 8.9),
		pal(5, 0), rotation=zrot(-22.5, (8, 8.0, 8.6))))
	# Collar: bronze socket joining shaft to head, ringed with teal light.
	e.append(cube("collar", (6.2, 15.6, 6.5), (9.8, 17.6, 10.6), pal_shaded((12, 0), (13, 0))))
	e.append(cube("collar_ring", (6.45, 17.6, 6.75), (9.55, 18.2, 10.35), pal(3, 0)))
	# Crown gem: bright aqua core with a rotated shell halo.
	e.append(cube("crown_gem_core", (7.25, 18.4, 7.8), (8.75, 20.2, 9.3), pal(1, 0)))
	e.append(cube("crown_gem_shell", (7.25, 18.4, 7.8), (8.75, 20.2, 9.3), pal(0, 0),
		rotation=yrot(45, (8, 19.3, 8.55))))
	# Head plate: a stepped bronze crossbar carrying the three tines.
	e.append(cube("head_plate", (3.2, 20.2, 7.4), (12.8, 21.8, 9.7), pal_shaded((12, 0), (13, 0))))
	e.append(cube("head_plate_trim", (3.8, 21.8, 7.7), (12.2, 22.2, 9.4), pal(15, 0)))
	e.append(cube("head_step_w", (3.6, 19.3, 7.6), (5.2, 20.2, 9.5), pal(13, 0)))
	e.append(cube("head_step_e", (10.8, 19.3, 7.6), (12.4, 20.2, 9.5), pal(13, 0)))
	# Central tine: three tapering steel segments to a glowing tip.
	e.append(cube("center_tine_base", (7.3, 21.8, 7.9), (8.7, 26.2, 9.2), pal_shaded((7, 0), (8, 0))))
	e.append(cube("center_tine_mid", (7.55, 26.2, 8.1), (8.45, 29.4, 9.0), pal_shaded((7, 0), (9, 0))))
	e.append(cube("center_tine_tip", (7.8, 29.4, 8.3), (8.2, 31.0, 8.8), pal(0, 0)))
	# Side prongs: posts, tapered tips, inward barbs, outer glow fins.
	for side, x0 in (("w", 2.9), ("e", 11.7)):
		mirror = 1 if side == "w" else -1
		e.append(cube(f"prong_{side}_post", (x0, 20.6, 7.9), (x0 + 1.4, 26.6, 9.2),
			pal_shaded((7, 0), (8, 0))))
		e.append(cube(f"prong_{side}_tip", (x0 + 0.25, 26.6, 8.1), (x0 + 1.15, 28.8, 9.0),
			pal_shaded((8, 0), (9, 0))))
		e.append(cube(f"prong_{side}_point", (x0 + 0.4, 28.8, 8.3), (x0 + 0.95, 29.9, 8.8), pal(1, 0)))
		barb_x = x0 + 1.4 if side == "w" else x0 - 1.0
		e.append(cube(f"prong_{side}_barb", (barb_x, 24.4, 8.15), (barb_x + 1.0, 25.3, 8.95), pal(7, 0)))
		fin_x = x0 - 0.55 if side == "w" else x0 + 1.4
		e.append(cube(f"prong_{side}_fin", (fin_x, 21.4, 8.25), (fin_x + 0.55, 26.2, 8.85), pal(14, 0)))
		# Curved shoulder connecting the head plate to each prong.
		shoulder_x = x0 + 0.3 if side == "w" else x0 - 1.3
		e.append(cube(f"shoulder_{side}", (shoulder_x, 19.4, 7.7), (shoulder_x + 2.4, 20.8, 9.4),
			pal(12, 0), rotation=zrot(-22.5 * mirror, (shoulder_x + 1.2, 20.1, 8.55))))
	# Violet pearls set into the head plate face.
	e.append(cube("pearl_n", (5.4, 20.7, 7.15), (6.2, 21.5, 7.45), pal(3, 1)))
	e.append(cube("pearl_s", (9.8, 20.7, 9.65), (10.6, 21.5, 9.95), pal(3, 1)))
	return e


# --- Frostbound Trident -------------------------------------------------------
# Generated ice palette, row 0, pixel (i, 0):
FROST_PALETTE = [
	(240, 252, 255, 255),  # 0 frost white
	(190, 232, 250, 255),  # 1 pale ice
	(140, 205, 242, 255),  # 2 ice blue
	(90, 162, 222, 255),   # 3 sky steel
	(48, 112, 182, 255),   # 4 deep ice
	(26, 72, 132, 255),    # 5 navy
	(14, 40, 84, 255),     # 6 abyss
	(205, 222, 232, 255),  # 7 silver light
	(152, 172, 188, 255),  # 8 silver
	(96, 116, 132, 255),   # 9 dark steel
	(52, 66, 82, 255),     # 10 iron shadow
	(170, 244, 255, 255),  # 11 glow cyan
	(118, 220, 255, 255),  # 12 glow mid
	(66, 178, 235, 255),   # 13 glow deep
	(222, 240, 250, 255),  # 14 rime
	(36, 52, 96, 255),     # 15 frozen night
]


def write_frost_palette():
	img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	for x, color in enumerate(FROST_PALETTE):
		img.putpixel((x, 0), color)
	# Fill the unused rows with the abyss tone so accidental UVs stay on-palette.
	for y in range(1, 16):
		for x in range(16):
			img.putpixel((x, y), FROST_PALETTE[6])
	out = TEXTURES_ITEM / "frostbound_trident_voxel_palette.png"
	img.save(out)
	return out


def frostbound_elements():
	# Same showcase envelope as the Ocean Relic so the shared display poses fit.
	e = []
	# Frozen dark-steel haft sheathed in rime.
	e.append(cube("haft_outline", (6.72, -14.6, 7.0), (9.28, 15.4, 10.15), pal(10, 0)))
	e.append(cube("haft_core", (7.18, -14.2, 7.35), (8.82, 15.0, 9.8), pal_shaded((9, 0), (10, 0))))
	e.append(cube("butt_spike", (7.4, -16.0, 7.7), (8.6, -14.4, 9.4), pal_shaded((4, 0), (5, 0))))
	# Frost crust nubs climbing the haft.
	for i, (y, dx, dz) in enumerate(((-11.0, 6.7, 8.0), (-6.0, 8.6, 7.4), (-1.0, 6.8, 8.9), (5.0, 8.7, 8.1))):
		e.append(cube(f"frost_nub_{i}", (dx, y, dz), (dx + 0.7, y + 1.5, dz + 0.7), pal(14, 0)))
	# Grip wraps in frozen leather blues.
	for i, y in enumerate(range(-12, -4, 2)):
		tone = pal_shaded((5, 0), (6, 0)) if i % 2 == 0 else pal_shaded((15, 0), (6, 0))
		e.append(cube(f"grip_wrap_{i}", (7.0, y, 7.2), (9.0, y + 1.1, 10.0), tone))
	# Glacier collar with a glowing ice ring.
	e.append(cube("collar", (6.2, 15.4, 6.5), (9.8, 17.4, 10.6), pal_shaded((3, 0), (4, 0))))
	e.append(cube("collar_ring", (6.45, 17.4, 6.75), (9.55, 18.0, 10.35), pal(11, 0)))
	# Heart of winter: glowing core with rotated crystalline shell.
	e.append(cube("ice_heart_core", (7.25, 18.2, 7.8), (8.75, 20.0, 9.3), pal(12, 0)))
	e.append(cube("ice_heart_shell", (7.25, 18.2, 7.8), (8.75, 20.0, 9.3), pal(11, 0),
		rotation=yrot(45, (8, 19.1, 8.55))))
	# Glacial crossbar.
	e.append(cube("head_plate", (3.2, 20.0, 7.4), (12.8, 21.6, 9.7), pal_shaded((2, 0), (4, 0))))
	e.append(cube("head_plate_trim", (3.8, 21.6, 7.7), (12.2, 22.0, 9.4), pal(1, 0)))
	# Central shard: jagged offset segments ending in frost white.
	e.append(cube("center_shard_base", (7.3, 21.6, 7.9), (8.7, 26.0, 9.2), pal_shaded((2, 0), (3, 0))))
	e.append(cube("center_shard_mid", (7.45, 26.0, 8.0), (8.35, 29.2, 9.0), pal_shaded((1, 0), (2, 0))))
	e.append(cube("center_shard_tip", (7.75, 29.2, 8.25), (8.2, 31.0, 8.75), pal(0, 0)))
	e.append(cube("center_shard_glint", (8.2, 26.8, 8.15), (8.6, 28.2, 8.7), pal(11, 0)))
	# Side icicle prongs: a frozen claw built from stepped, inward-drifting
	# segments (element rotations only allow 22.5-degree steps, far too coarse
	# for a 1px-scale lean, so the jag comes from offsetting each segment).
	for side, x0 in (("w", 2.9), ("e", 11.7)):
		step = 0.4 if side == "w" else -0.4
		e.append(cube(f"icicle_{side}_post", (x0, 20.2, 7.9), (x0 + 1.4, 26.0, 9.2),
			pal_shaded((2, 0), (4, 0))))
		e.append(cube(f"icicle_{side}_tip", (x0 + 0.25 + step, 26.0, 8.1), (x0 + 1.15 + step, 28.4, 9.0),
			pal_shaded((1, 0), (2, 0))))
		e.append(cube(f"icicle_{side}_point", (x0 + 0.4 + 2 * step, 28.4, 8.3),
			(x0 + 0.95 + 2 * step, 29.6, 8.8), pal(0, 0)))
		drip_x = x0 + 0.4
		e.append(cube(f"icicle_{side}_drip", (drip_x, 18.6, 8.2), (drip_x + 0.6, 20.2, 8.9), pal(1, 0)))
		fin_x = x0 - 0.55 if side == "w" else x0 + 1.4
		e.append(cube(f"icicle_{side}_glow", (fin_x, 21.0, 8.25), (fin_x + 0.55, 25.6, 8.85), pal(13, 0)))
	# Aurora ribbon drifting around the upper haft.
	e.append(cube("aurora_ribbon", (6.5, 2.0, 8.3), (9.5, 12.0, 8.85), pal(12, 0),
		rotation=zrot(-22.5, (8, 7.0, 8.6))))
	return e


# --- Attunement Altar ---------------------------------------------------------

def attunement_altar_elements():
	def face(tex, uv, *sides):
		return {side: {"texture": tex, "uv": list(uv)} for side in sides}

	all_sides = ("north", "south", "east", "west", "up", "down")
	e = []
	# Three-step plinth.
	e.append(cube("base_slab", (0, 0, 0), (16, 2, 16), face("#base", (0, 0, 16, 16), *all_sides)))
	e.append(cube("base_step", (1, 2, 1), (15, 3.5, 15), face("#base", (0, 2, 16, 14), *all_sides)))
	e.append(cube("base_molding", (2, 3.5, 2), (14, 4.5, 14), face("#base", (2, 4, 14, 12), *all_sides)))
	# Central column, slimmer than before so the corner pillars read separately.
	e.append(cube("solid_core", (3.5, 4.5, 3.5), (12.5, 14, 12.5), face("#pillar", (0, 0, 16, 16), *all_sides)))
	# Inset glowing rune panels on each face of the core.
	e.append(cube("rune_panel_n", (5.5, 6, 3.2), (10.5, 12.5, 3.6), face("#gem", (4, 2, 12, 14), *all_sides)))
	e.append(cube("rune_panel_s", (5.5, 6, 12.4), (10.5, 12.5, 12.8), face("#gem", (4, 2, 12, 14), *all_sides)))
	e.append(cube("rune_panel_w", (3.2, 6, 5.5), (3.6, 12.5, 10.5), face("#gem", (4, 2, 12, 14), *all_sides)))
	e.append(cube("rune_panel_e", (12.4, 6, 5.5), (12.8, 12.5, 10.5), face("#gem", (4, 2, 12, 14), *all_sides)))
	# Free-standing corner pillars with gem caps and feet.
	for name, (x, z) in (("nw", (0.8, 0.8)), ("ne", (12.9, 0.8)), ("sw", (0.8, 12.9)), ("se", (12.9, 12.9))):
		e.append(cube(f"pillar_{name}_foot", (x - 0.3, 3, z - 0.3), (x + 2.6, 4.2, z + 2.6),
			face("#base", (4, 10, 12, 16), *all_sides)))
		e.append(cube(f"pillar_{name}", (x, 4.2, z), (x + 2.3, 14.2, z + 2.3),
			face("#pillar", (4, 0, 10, 16), *all_sides)))
		e.append(cube(f"pillar_{name}_cap", (x - 0.35, 14.2, z - 0.35), (x + 2.65, 15.2, z + 2.65),
			face("#gem", (4, 4, 12, 12), *all_sides)))
	# Capital and the (pinned) top plate.
	e.append(cube("stepped_capital", (1.5, 14, 1.5), (14.5, 15, 14.5), face("#base", (1, 1, 15, 15), *all_sides)))
	e.append(cube("top_plate", (0, 15, 0), (16, 16, 16),
		{"north": {"texture": "#base", "uv": [0, 0, 16, 1]},
		 "south": {"texture": "#base", "uv": [0, 0, 16, 1]},
		 "east": {"texture": "#base", "uv": [0, 0, 16, 1]},
		 "west": {"texture": "#base", "uv": [0, 0, 16, 1]},
		 "up": {"texture": "#top", "uv": [0, 0, 16, 16]},
		 "down": {"texture": "#base", "uv": [0, 0, 16, 16]}}))
	# Corner finials above the plate.
	for name, (x, z) in (("nw", (0.4, 0.4)), ("ne", (13.4, 0.4)), ("sw", (0.4, 13.4)), ("se", (13.4, 13.4))):
		e.append(cube(f"finial_{name}", (x, 16, z), (x + 2.2, 17.4, z + 2.2),
			face("#gem", (6, 6, 10, 10), *all_sides)))
	# Floating gem: axis-aligned core, rotated shell, slim halo ring.
	e.append(cube("float_gem_core", (6.8, 17.6, 6.8), (9.2, 20.0, 9.2), face("#gem", (4, 4, 12, 12), *all_sides)))
	e.append(cube("float_gem_shell", (6.8, 17.6, 6.8), (9.2, 20.0, 9.2), face("#gem", (2, 2, 14, 14), *all_sides),
		rotation=yrot(45, (8, 18.8, 8))))
	e.append(cube("float_gem_halo", (5.6, 18.5, 5.6), (10.4, 19.1, 10.4), face("#gem", (0, 7, 16, 9), *all_sides),
		rotation=yrot(45, (8, 18.8, 8))))
	return e


# --- Altar of Reweaving ---------------------------------------------------------

def reweaving_altar_elements():
	def face(tex, uv, *sides):
		return {side: {"texture": tex, "uv": list(uv)} for side in sides}

	all_sides = ("north", "south", "east", "west", "up", "down")
	e = []
	# Stepped foundation and working platform.
	e.append(cube("base_slab", (0, 0, 0), (16, 2, 16), face("#base", (0, 0, 16, 16), *all_sides)))
	e.append(cube("base_step", (1, 2, 1), (15, 4, 15), face("#base", (0, 2, 16, 14), *all_sides)))
	e.append(cube("platform", (2, 4, 2), (14, 6, 14), face("#base", (2, 4, 14, 12), *all_sides)))
	for name, (x, z) in (("nw", (0.4, 0.4)), ("ne", (13.2, 0.4)), ("sw", (0.4, 13.2)), ("se", (13.2, 13.2))):
		e.append(cube(f"foot_{name}", (x, 2, z), (x + 2.4, 4.6, z + 2.4), face("#base", (5, 10, 11, 16), *all_sides)))
	# Work surface where Foci are rewoven.
	e.append(cube("work_surface", (4, 6, 4), (12, 7.8, 12),
		{"north": {"texture": "#base", "uv": [4, 8, 12, 10]},
		 "south": {"texture": "#base", "uv": [4, 8, 12, 10]},
		 "east": {"texture": "#base", "uv": [4, 8, 12, 10]},
		 "west": {"texture": "#base", "uv": [4, 8, 12, 10]},
		 "up": {"texture": "#top", "uv": [2, 2, 14, 14]},
		 "down": {"texture": "#base", "uv": [4, 4, 12, 12]}}))
	# Loom posts and crossbeam arching over the surface.
	e.append(cube("loom_post_w", (1, 4, 5.5), (4, 13, 10.5), face("#base", (0, 0, 6, 16), *all_sides)))
	e.append(cube("loom_post_e", (12, 4, 5.5), (15, 13, 10.5), face("#base", (10, 0, 16, 16), *all_sides)))
	e.append(cube("loom_spool_w", (1.6, 9, 4.9), (3.4, 10.8, 5.5), face("#gem", (5, 5, 11, 11), *all_sides)))
	e.append(cube("loom_spool_e", (12.6, 9, 10.5), (14.4, 10.8, 11.1), face("#gem", (5, 5, 11, 11), *all_sides)))
	e.append(cube("crossbeam", (0.5, 13, 5), (15.5, 15.5, 11),
		{"north": {"texture": "#base", "uv": [0, 0, 16, 3]},
		 "south": {"texture": "#base", "uv": [0, 0, 16, 3]},
		 "east": {"texture": "#base", "uv": [5, 0, 11, 3]},
		 "west": {"texture": "#base", "uv": [5, 0, 11, 3]},
		 "up": {"texture": "#top", "uv": [0, 5, 16, 11]},
		 "down": {"texture": "#base", "uv": [0, 5, 16, 11]}}))
	e.append(cube("beam_trim_n", (0.5, 14.9, 4.7), (15.5, 15.6, 5.0), face("#gem", (0, 7, 16, 9), *all_sides)))
	e.append(cube("beam_trim_s", (0.5, 14.9, 11.0), (15.5, 15.6, 11.3), face("#gem", (0, 7, 16, 9), *all_sides)))
	# Suspended gem under the beam: thread, glowing core, rotated shell.
	e.append(cube("gem_thread", (7.8, 10.6, 7.8), (8.2, 13.0, 8.2), face("#gem", (7, 0, 9, 16), *all_sides)))
	e.append(cube("hanging_gem_core", (6.9, 8.4, 6.9), (9.1, 10.6, 9.1), face("#gem", (4, 4, 12, 12), *all_sides)))
	e.append(cube("hanging_gem_shell", (6.9, 8.4, 6.9), (9.1, 10.6, 9.1), face("#gem", (2, 2, 14, 14), *all_sides),
		rotation=yrot(45, (8, 9.5, 8))))
	# Warp threads: four thin glowing strands between surface and beam.
	for i, (x, z) in enumerate(((5.0, 6.4), (5.0, 9.3), (10.7, 6.4), (10.7, 9.3))):
		e.append(cube(f"warp_thread_{i}", (x, 7.8, z), (x + 0.3, 13.0, z + 0.3),
			face("#gem", (7, 2, 8, 14), *all_sides)))
	return e


# --- Writers -------------------------------------------------------------------

def write_json(path, data):
	path.write_text(json.dumps(data, indent="\t") + "\n", encoding="utf-8")
	print(f"wrote {path.relative_to(ROOT)}")


def regenerate_ocean_relic():
	elements = ocean_relic_elements()
	for name in ("ocean_relic_trident.json", "ocean_relic_trident_throwing.json"):
		path = MODELS_ITEM / name
		model = json.loads(path.read_text(encoding="utf-8"))
		model["elements"] = elements
		write_json(path, model)
	assert len(elements) >= 36, f"trident model must keep >= 36 elements, got {len(elements)}"
	update_voxel_report(elements)


def update_voxel_report(elements):
	"""Keep the shipped voxel report truthful about the regenerated geometry."""
	report_path = ROOT / ".attuned-art-sources/ocean-relic-trident/ocean_relic_trident_voxel_report.json"
	report = json.loads(report_path.read_text(encoding="utf-8"))
	mins = [min(e["from"][axis] for e in elements) for axis in range(3)]
	maxs = [max(e["to"][axis] for e in elements) for axis in range(3)]
	report["cuboids"] = len(elements)
	report["bbox_min"] = [round(v, 2) for v in mins]
	report["bbox_max"] = [round(v, 2) for v in maxs]
	report["bbox_size"] = [round(maxs[axis] - mins[axis], 2) for axis in range(3)]
	report["enhancement_strategy"] = "hand_authored_showcase_silhouette"
	if "generate_3d_models" not in report["reason"]:
		report["reason"] = (report["reason"].rstrip(".")
			+ "; later regenerated by tools/generate_3d_models.py as a hand-authored showcase "
			+ "silhouette with tapered tines, barbed prongs, glow fins, kelp ribbons, and a "
			+ "rotated crown-gem shell at the same held-pose envelope.")
	report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
	print(f"wrote {report_path.relative_to(ROOT)}")


def regenerate_frostbound():
	write_frost_palette()
	source = json.loads((MODELS_ITEM / "ocean_relic_trident.json").read_text(encoding="utf-8"))
	model = {
		"credit": "Attuned deterministic model generator (tools/generate_3d_models.py)",
		"gui_light": "front",
		"textures": {
			"palette": "attuned:item/frostbound_trident_voxel_palette",
			"particle": "attuned:item/frostbound_trident_voxel_palette",
		},
		"elements": frostbound_elements(),
		# The Ocean Relic display transforms are hand-tuned for this fork's
		# trident-sized models; reuse them so the frost trident holds correctly.
		"display": source["display"],
	}
	write_json(MODELS_ITEM / "frostbound_trident.json", model)


def regenerate_altars():
	altar = {
		"parent": "minecraft:block/block",
		"textures": {
			"base": "attuned:block/attunement_altar_base",
			"pillar": "attuned:block/attunement_altar_pillar_none",
			"top": "attuned:block/attunement_altar_top_none",
			"gem": "attuned:block/attunement_altar_gem_none",
			"particle": "attuned:block/attunement_altar_base",
		},
		"elements": attunement_altar_elements(),
	}
	write_json(MODELS_BLOCK / "attunement_altar.json", altar)
	reweaving = {
		"parent": "minecraft:block/block",
		"textures": {
			"base": "attuned:block/altar_of_reweaving_base",
			"top": "attuned:block/altar_of_reweaving_top",
			"gem": "attuned:block/altar_of_reweaving_gem",
			"particle": "attuned:block/altar_of_reweaving_base",
		},
		"elements": reweaving_altar_elements(),
	}
	write_json(MODELS_BLOCK / "altar_of_reweaving.json", reweaving)


def main():
	regenerate_ocean_relic()
	regenerate_frostbound()
	regenerate_altars()


if __name__ == "__main__":
	main()
