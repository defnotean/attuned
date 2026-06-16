from __future__ import annotations

import json
import math
from collections import deque
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageEnhance


ROOT = Path(__file__).resolve().parents[1]
DOCS_DIR = ROOT / "docs" / "superpowers" / "assets" / "umbral-eclipse"
SOURCE_PATH = DOCS_DIR / "umbral-eclipse-foci-source.png"
PREVIEW_PATH = DOCS_DIR / "umbral-eclipse-foci-preview.png"
REPORT_PATH = DOCS_DIR / "umbral-eclipse-foci-report.json"
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
	col: int


FOCI: tuple[FocusSource, ...] = (
	FocusSource("gloomstride_focus", "Gloomstride", 0),
	FocusSource("duskward_focus", "Duskward", 1),
	FocusSource("shadowmeld_focus", "Shadowmeld", 2),
	FocusSource("dreadfang_focus", "Dreadfang", 3),
	FocusSource("eclipse_focus", "Eclipse", 4),
)


def relative(path: Path) -> str:
	return path.relative_to(ROOT).as_posix()


def color_distance(rgb: tuple[int, int, int], target: tuple[int, int, int]) -> int:
	return max(abs(rgb[0] - target[0]), abs(rgb[1] - target[1]), abs(rgb[2] - target[2]))


def is_key_like(rgb: tuple[int, int, int]) -> bool:
	red, green, blue = rgb
	distance = color_distance(rgb, KEY_COLOR)
	return distance <= 86 or (red > 190 and blue > 190 and green < 72 and min(red, blue) - green > 120)


def border_key_mask(image: Image.Image) -> set[tuple[int, int]]:
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
		if alpha == 0 or is_key_like((red, green, blue)):
			seen.add((x, y))
			queue.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
	return seen


def remove_key_background(image: Image.Image) -> Image.Image:
	source = image.convert("RGBA")
	output = Image.new("RGBA", source.size)
	source_pixels = source.load()
	output_pixels = output.load()
	mask = border_key_mask(source)

	for y in range(source.height):
		for x in range(source.width):
			red, green, blue, alpha = source_pixels[x, y]
			if (x, y) in mask:
				distance = color_distance((red, green, blue), KEY_COLOR)
				if distance <= 18 or is_key_like((red, green, blue)):
					next_alpha = 0
				elif distance < 120:
					coverage = (distance - 18) / 102
					next_alpha = round(255 * (coverage**1.6))
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
			else:
				output_pixels[x, y] = (red, green, blue, alpha)
	return output


def alpha_bounds(image: Image.Image) -> tuple[int, int, int, int]:
	mask = image.getchannel("A").point(lambda value: 255 if value > 8 else 0)
	return mask.getbbox() or (0, 0, image.width, image.height)


def source_cell(source: Image.Image, col: int) -> Image.Image:
	cell_width = source.width // len(FOCI)
	left = col * cell_width
	right = source.width if col == len(FOCI) - 1 else (col + 1) * cell_width
	return source.crop((left, 0, right, source.height))


def fit_icon(cell: Image.Image) -> Image.Image:
	with_alpha = remove_key_background(cell)
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
	color = 0.98 + frame_index * 0.004 + max(phase, 0) * 0.08
	frame = ImageEnhance.Brightness(icon).enhance(brightness)
	return ImageEnhance.Color(frame).enhance(color)


def animated_strip(icon: Image.Image) -> Image.Image:
	strip = Image.new("RGBA", (ICON_SIZE, ICON_SIZE * FRAME_COUNT), (0, 0, 0, 0))
	for frame_index in range(FRAME_COUNT):
		strip.alpha_composite(animation_frame(icon, frame_index), (0, frame_index * ICON_SIZE))
	return strip


def save_mcmeta(path: Path) -> None:
	meta_path = path.with_suffix(path.suffix + ".mcmeta")
	if meta_path.exists():
		try:
			if json.loads(meta_path.read_text(encoding="utf-8")) == MC_META:
				return
		except json.JSONDecodeError:
			pass
	meta_path.write_text(json.dumps(MC_META, indent=2) + "\n", encoding="utf-8")


def save_preview(icons: dict[str, Image.Image]) -> None:
	cell = 72
	preview = Image.new("RGBA", (cell * len(FOCI), cell), (18, 18, 22, 255))
	for focus in FOCI:
		preview.alpha_composite(icons[focus.item_id], (focus.col * cell + 4, 4))
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
		icon = fit_icon(source_cell(source, focus.col))
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
	report = {
		"source": relative(SOURCE_PATH),
		"generated_by": "OpenAI built-in image_gen",
		"purpose": "Image-generated source sheet for the Umbral Eclipse Focus texture pass.",
		"source_grid": [5, 1],
		"operations": [
			"crop generated source grid cells",
			"remove border-connected flat magenta key background",
			"fit each item to 64x64",
			"assemble eight brightness/color pulse frames",
		],
		"final_textures": [entry["path"] for entry in outputs],
		"format": "64x512 animated PNG sheets, eight 64x64 frames, frametime 2, interpolate true",
		"outputs": outputs,
	}
	REPORT_PATH.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
	return report


if __name__ == "__main__":
	result = generate_assets()
	print(json.dumps({"generated": len(result["outputs"]), "preview": relative(PREVIEW_PATH)}, indent=2))
