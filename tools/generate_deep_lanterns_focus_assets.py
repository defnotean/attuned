from __future__ import annotations

import json
import math
from collections import deque
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageEnhance


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / ".attuned-art-sources" / "deep-lanterns"
OUTPUT_DIR = ROOT / "build" / "asset-previews" / "deep-lanterns"
SOURCE_PATH = SOURCE_DIR / "deep-lanterns-source.png"
PREVIEW_PATH = OUTPUT_DIR / "deep-lanterns-preview.png"
REPORT_PATH = OUTPUT_DIR / "asset-verification.json"
TEXTURE_DIR = ROOT / "src" / "main" / "resources" / "assets" / "attuned" / "textures" / "item"

ICON_SIZE = 64
FRAME_COUNT = 8
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
	FocusSource("cavewick_focus", "Cavewick", 0, 0),
	FocusSource("glowline_focus", "Glowline", 0, 1),
	FocusSource("rescueflame_focus", "Rescueflame", 1, 0),
	FocusSource("depthglass_focus", "Depthglass", 1, 1),
)


def relative(path: Path) -> str:
	return path.relative_to(ROOT).as_posix()


def luminance(red: int, green: int, blue: int) -> float:
	return red * 0.2126 + green * 0.7152 + blue * 0.0722


def is_backdrop(red: int, green: int, blue: int, alpha: int) -> bool:
	if alpha <= 8:
		return True
	return luminance(red, green, blue) <= 38 and max(red, green, blue) - min(red, green, blue) <= 22


def border_backdrop_mask(image: Image.Image) -> set[tuple[int, int]]:
	source = image.convert("RGBA")
	pixels = source.load()
	width, height = source.size
	seen: set[tuple[int, int]] = set()
	queue: deque[tuple[int, int]] = deque()

	for x in range(width):
		queue.append((x, 0))
		queue.append((x, height - 1))
	for y in range(height):
		queue.append((0, y))
		queue.append((width - 1, y))

	while queue:
		x, y = queue.popleft()
		if (x, y) in seen or x < 0 or y < 0 or x >= width or y >= height:
			continue
		red, green, blue, alpha = pixels[x, y]
		if not is_backdrop(red, green, blue, alpha):
			continue
		seen.add((x, y))
		queue.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
	return seen


def remove_dark_backdrop(image: Image.Image) -> Image.Image:
	source = image.convert("RGBA")
	output = Image.new("RGBA", source.size)
	source_pixels = source.load()
	output_pixels = output.load()
	mask = border_backdrop_mask(source)

	for y in range(source.height):
		for x in range(source.width):
			red, green, blue, alpha = source_pixels[x, y]
			if (x, y) in mask:
				output_pixels[x, y] = (0, 0, 0, 0)
			else:
				output_pixels[x, y] = (red, green, blue, alpha)
	return output


def alpha_bounds(image: Image.Image) -> tuple[int, int, int, int]:
	mask = image.getchannel("A").point(lambda value: 255 if value > 12 else 0)
	return mask.getbbox() or (0, 0, image.width, image.height)


def source_cell(source: Image.Image, row: int, col: int) -> Image.Image:
	cell_width = source.width // 2
	cell_height = source.height // 2
	left = col * cell_width
	upper = row * cell_height
	right = source.width if col == 1 else (col + 1) * cell_width
	lower = source.height if row == 1 else (row + 1) * cell_height
	return source.crop((left, upper, right, lower))


def fit_icon(cell: Image.Image) -> Image.Image:
	with_alpha = remove_dark_backdrop(cell)
	crop = with_alpha.crop(alpha_bounds(with_alpha))
	max_side = ICON_SIZE - 2
	scale = min(max_side / crop.width, max_side / crop.height)
	size = (max(1, round(crop.width * scale)), max(1, round(crop.height * scale)))
	resized = crop.resize(size, Image.Resampling.LANCZOS)
	icon = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
	icon.alpha_composite(resized, ((ICON_SIZE - size[0]) // 2, (ICON_SIZE - size[1]) // 2))
	return icon


def animation_frame(icon: Image.Image, frame_index: int) -> Image.Image:
	phase = math.sin((frame_index / FRAME_COUNT) * math.tau)
	brightness = 0.96 + frame_index * 0.006 + phase * 0.05
	color = 0.98 + frame_index * 0.004 + max(phase, 0) * 0.07
	frame = ImageEnhance.Brightness(icon).enhance(brightness)
	return ImageEnhance.Color(frame).enhance(color)


def animated_strip(icon: Image.Image) -> Image.Image:
	strip = Image.new("RGBA", (ICON_SIZE, ICON_SIZE * FRAME_COUNT), (0, 0, 0, 0))
	for frame_index in range(FRAME_COUNT):
		strip.alpha_composite(animation_frame(icon, frame_index), (0, frame_index * ICON_SIZE))
	return strip


def save_mcmeta(path: Path) -> None:
	path.with_suffix(path.suffix + ".mcmeta").write_text(
		json.dumps(MC_META, indent=2) + "\n", encoding="utf-8")


def save_preview(icons: dict[str, Image.Image]) -> None:
	cell = 80
	preview = Image.new("RGBA", (cell * 2, cell * 2), (18, 18, 22, 255))
	for focus in FOCI:
		preview.alpha_composite(icons[focus.item_id], (focus.col * cell + 8, focus.row * cell + 8))
	PREVIEW_PATH.parent.mkdir(parents=True, exist_ok=True)
	preview.save(PREVIEW_PATH, optimize=True)


def generate_assets() -> dict[str, object]:
	if not SOURCE_PATH.exists():
		raise FileNotFoundError(f"Missing generated source sheet: {SOURCE_PATH}")

	source = Image.open(SOURCE_PATH).convert("RGBA")
	icons: dict[str, Image.Image] = {}
	textures: dict[str, dict[str, object]] = {}
	TEXTURE_DIR.mkdir(parents=True, exist_ok=True)

	for focus in FOCI:
		icon = fit_icon(source_cell(source, focus.row, focus.col))
		icons[focus.item_id] = icon
		strip = animated_strip(icon)
		output_path = TEXTURE_DIR / f"{focus.item_id}.png"
		strip.save(output_path, optimize=True)
		save_mcmeta(output_path)
		textures[focus.item_id] = {
			"label": focus.label,
			"path": relative(output_path),
			"size": list(strip.size),
			"frames": FRAME_COUNT,
			"first_frame_bounds": list(alpha_bounds(icon)),
		}

	save_preview(icons)
	report = {
		"source": relative(SOURCE_PATH),
		"preview": relative(PREVIEW_PATH),
		"workflow": "local source sheet normalized into shipped Focus texture strips",
		"source_grid": [2, 2],
		"operations": [
			"crop generated 2x2 source cells",
			"remove border-connected dark backdrop",
			"fit each generated medallion to 64x64",
			"assemble eight subtle brightness/color pulse frames",
		],
		"format": "64x512 animated PNG sheets, eight 64x64 frames, frametime 2, interpolate true",
		"textures": textures,
	}
	REPORT_PATH.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
	return report


if __name__ == "__main__":
	result = generate_assets()
	print(json.dumps({"generated": len(result["textures"]), "preview": result["preview"]}, indent=2))
