from __future__ import annotations

import json
import math
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageEnhance


ROOT = Path(__file__).resolve().parents[1]
DOCS_DIR = ROOT / "docs" / "superpowers" / "assets" / "modifier-foci"
SOURCE_PATH = DOCS_DIR / "modifier-foci-source.png"
PREVIEW_PATH = DOCS_DIR / "modifier-foci-preview.png"
VERIFY_PATH = DOCS_DIR / "asset-verification.json"
TEXTURE_DIR = ROOT / "src" / "main" / "resources" / "assets" / "attuned" / "textures" / "item"

ICON_SIZE = 64
FRAME_COUNT = 8
KEY_COLOR = (255, 0, 255)
MC_META = {
	"animation": {
		"frametime": 2,
		"interpolate": True,
	}
}


@dataclass(frozen=True)
class FocusSource:
	item_id: str
	label: str
	row: int
	col: int


FOCI: tuple[FocusSource, ...] = (
	FocusSource("tidewarden_focus", "Tidewarden", 0, 0),
	FocusSource("wellspring_focus", "Wellspring", 0, 1),
	FocusSource("current_runner_focus", "Current Runner", 0, 2),
	FocusSource("saltbrand_focus", "Saltbrand", 0, 3),
	FocusSource("ebbstride_focus", "Ebbstride", 1, 0),
	FocusSource("overgrowth_focus", "Overgrowth", 1, 1),
	FocusSource("deeproot_focus", "Deeproot", 1, 2),
	FocusSource("briarcoat_focus", "Briarcoat", 1, 3),
	FocusSource("fernstride_focus", "Fernstride", 2, 0),
	FocusSource("sapflow_focus", "Sapflow", 2, 1),
	FocusSource("cinderplate_focus", "Cinderplate", 2, 2),
	FocusSource("bellowsfury_focus", "Bellowsfury", 2, 3),
	FocusSource("bloodrush_focus", "Bloodrush", 3, 0),
	FocusSource("ravager_focus", "Ravager", 3, 1),
	FocusSource("granitehide_focus", "Granitehide", 3, 2),
	FocusSource("hammerward_focus", "Hammerward", 3, 3),
)


def relative(path: Path) -> str:
	return path.relative_to(ROOT).as_posix()


def color_distance(rgb: tuple[int, int, int], target: tuple[int, int, int]) -> int:
	return max(abs(rgb[0] - target[0]), abs(rgb[1] - target[1]), abs(rgb[2] - target[2]))


def remove_key_background(image: Image.Image) -> Image.Image:
	source = image.convert("RGBA")
	output = Image.new("RGBA", source.size)
	source_pixels = source.load()
	output_pixels = output.load()
	for y in range(source.height):
		for x in range(source.width):
			red, green, blue, alpha = source_pixels[x, y]
			magenta_score = min(red, blue) - green
			is_key_family = red > 110 and blue > 110 and green < 150 and magenta_score > 30
			distance = color_distance((red, green, blue), KEY_COLOR)
			if distance <= 16 or is_key_family:
				next_alpha = 0
			elif distance < 96:
				coverage = (distance - 16) / 80
				next_alpha = round(255 * (coverage**1.45))
			else:
				next_alpha = 255

			if next_alpha < 255:
				matte = next_alpha / 255
				if matte > 0:
					red = round((red - KEY_COLOR[0] * (1 - matte)) / matte)
					green = round((green - KEY_COLOR[1] * (1 - matte)) / matte)
					blue = round((blue - KEY_COLOR[2] * (1 - matte)) / matte)
				else:
					red, green, blue = 0, 0, 0
				red = max(0, min(255, red))
				green = max(0, min(255, green))
				blue = max(0, min(255, blue))

			output_pixels[x, y] = (red, green, blue, min(alpha, next_alpha))
	return output


def alpha_bounds(image: Image.Image) -> tuple[int, int, int, int]:
	mask = image.getchannel("A").point(lambda value: 255 if value > 8 else 0)
	return mask.getbbox() or (0, 0, image.width, image.height)


def source_cell(source: Image.Image, row: int, col: int) -> Image.Image:
	cell_width = source.width // 4
	cell_height = source.height // 4
	left = col * cell_width
	upper = row * cell_height
	right = source.width if col == 3 else (col + 1) * cell_width
	lower = source.height if row == 3 else (row + 1) * cell_height
	return source.crop((left, upper, right, lower))


def fit_icon(cell: Image.Image) -> Image.Image:
	with_alpha = remove_key_background(cell)
	crop = with_alpha.crop(alpha_bounds(with_alpha))
	max_side = ICON_SIZE - 2
	scale = min(max_side / crop.width, max_side / crop.height)
	size = (max(1, round(crop.width * scale)), max(1, round(crop.height * scale)))
	resized = crop.resize(size, Image.Resampling.LANCZOS)
	icon = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
	offset = ((ICON_SIZE - size[0]) // 2, (ICON_SIZE - size[1]) // 2)
	icon.alpha_composite(resized, offset)
	return icon


def animation_frame(icon: Image.Image, frame_index: int) -> Image.Image:
	phase = math.sin((frame_index / FRAME_COUNT) * math.tau)
	brightness = 0.96 + frame_index * 0.006 + phase * 0.045
	color = 0.98 + frame_index * 0.004 + max(phase, 0) * 0.07
	frame = ImageEnhance.Brightness(icon).enhance(brightness)
	frame = ImageEnhance.Color(frame).enhance(color)
	return frame


def animated_strip(icon: Image.Image) -> Image.Image:
	strip = Image.new("RGBA", (ICON_SIZE, ICON_SIZE * FRAME_COUNT), (0, 0, 0, 0))
	for frame_index in range(FRAME_COUNT):
		strip.alpha_composite(animation_frame(icon, frame_index), (0, ICON_SIZE * frame_index))
	return strip


def save_mcmeta(path: Path) -> None:
	path.with_suffix(path.suffix + ".mcmeta").write_text(json.dumps(MC_META, indent=2), encoding="utf-8")


def save_preview(icons: dict[str, Image.Image]) -> None:
	cell = 80
	preview = Image.new("RGBA", (cell * 4, cell * 4), (18, 18, 22, 255))
	for focus in FOCI:
		icon = icons[focus.item_id]
		preview.alpha_composite(icon, (focus.col * cell + 8, focus.row * cell + 8))
	PREVIEW_PATH.parent.mkdir(parents=True, exist_ok=True)
	preview.save(PREVIEW_PATH, optimize=True)


def generate_assets() -> dict[str, object]:
	if not SOURCE_PATH.exists():
		raise FileNotFoundError(f"Missing generated source sheet: {SOURCE_PATH}")

	source = Image.open(SOURCE_PATH).convert("RGBA")
	icons: dict[str, Image.Image] = {}
	outputs: list[dict[str, object]] = []
	TEXTURE_DIR.mkdir(parents=True, exist_ok=True)

	for focus in FOCI:
		icon = fit_icon(source_cell(source, focus.row, focus.col))
		icons[focus.item_id] = icon
		strip = animated_strip(icon)
		output_path = TEXTURE_DIR / f"{focus.item_id}.png"
		strip.save(output_path, optimize=True)
		save_mcmeta(output_path)
		outputs.append(
			{
				"id": focus.item_id,
				"label": focus.label,
				"path": relative(output_path),
				"size": list(strip.size),
				"frames": FRAME_COUNT,
				"first_frame_bounds": list(alpha_bounds(icon)),
			}
		)

	save_preview(icons)
	verification = {
		"source": relative(SOURCE_PATH),
		"preview": relative(PREVIEW_PATH),
		"source_grid": [4, 4],
		"operations": [
			"crop generated source grid cells",
			"remove flat magenta key background",
			"fit each item to 64x64",
			"assemble eight brightness/color pulse frames",
		],
		"outputs": outputs,
	}
	VERIFY_PATH.write_text(json.dumps(verification, indent=2) + "\n", encoding="utf-8")
	return verification


if __name__ == "__main__":
	result = generate_assets()
	print(json.dumps({"generated": len(result["outputs"]), "preview": result["preview"]}, indent=2))
