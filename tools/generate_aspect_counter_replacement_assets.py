from __future__ import annotations

import json
import math
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageEnhance

from generate_umbral_eclipse_focus_assets import alpha_bounds, remove_key_background


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / ".attuned-art-sources" / "aspect-counter-foci" / "replacements"
OUTPUT_DIR = ROOT / "build" / "asset-previews" / "aspect-counter-foci" / "replacements"
SOURCE_PATH = SOURCE_DIR / "aspect-counter-replacements-source.png"
PREVIEW_PATH = OUTPUT_DIR / "aspect-counter-replacements-preview.png"
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
	col: int


FOCI: tuple[FocusSource, ...] = (
	FocusSource("bramblegate_focus", "Bramblegate", 0),
	FocusSource("seedcall_focus", "Seedcall", 1),
	FocusSource("riptide_heart_focus", "Riptide Heart", 2),
	FocusSource("pearlguard_focus", "Pearlguard", 3),
	FocusSource("slagbrand_focus", "Slagbrand", 4),
)


def relative(path: Path) -> str:
	return path.relative_to(ROOT).as_posix()


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
		"workflow": "local source sheet normalized into selected Aspect Counter Focus textures",
		"source_grid": [5, 1],
		"operations": [
			"crop generated source grid cells",
			"remove border-connected flat magenta key background",
			"fit each item to 64x64",
			"assemble eight brightness/color pulse frames",
		],
		"outputs": outputs,
	}
	REPORT_PATH.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
	return report


if __name__ == "__main__":
	result = generate_assets()
	print(json.dumps({"generated": len(result["outputs"]), "preview": relative(PREVIEW_PATH)}, indent=2))
