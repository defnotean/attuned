from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

from PIL import Image

from generate_umbral_eclipse_focus_assets import alpha_bounds, remove_key_background


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / ".attuned-art-sources" / "custom-foci"
OUTPUT_DIR = ROOT / "build" / "asset-previews" / "custom-foci"
SOURCE_PATH = SOURCE_DIR / "custom-foci-source.png"
PREVIEW_PATH = OUTPUT_DIR / "custom-foci-preview.png"
REPORT_PATH = OUTPUT_DIR / "asset-verification.json"
TEXTURE_DIR = ROOT / "src" / "main" / "resources" / "assets" / "attuned" / "textures" / "item"
ITEM_MODEL_DIR = ROOT / "src" / "main" / "resources" / "assets" / "attuned" / "models" / "item"
ITEM_DEFINITION_DIR = ROOT / "src" / "main" / "resources" / "assets" / "attuned" / "items"

ICON_SIZE = 64
COLS = 4
ROWS = 2


@dataclass(frozen=True)
class CustomFocusSource:
	item_id: str
	row: int
	col: int


CUSTOM_FOCI: tuple[CustomFocusSource, ...] = tuple(
	CustomFocusSource(f"custom_focus_{index + 1}", index // COLS, index % COLS)
	for index in range(COLS * ROWS)
)


def relative(path: Path) -> str:
	return path.relative_to(ROOT).as_posix()


def source_cell(source: Image.Image, row: int, col: int) -> Image.Image:
	cell_width = source.width // COLS
	cell_height = source.height // ROWS
	left = col * cell_width
	upper = row * cell_height
	right = source.width if col == COLS - 1 else (col + 1) * cell_width
	lower = source.height if row == ROWS - 1 else (row + 1) * cell_height
	return source.crop((left, upper, right, lower))


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


def write_json_if_changed(path: Path, data: dict[str, object]) -> None:
	next_text = json.dumps(data, indent=2) + "\n"
	if path.exists() and path.read_text(encoding="utf-8") == next_text:
		return
	path.write_text(next_text, encoding="utf-8")


def ensure_item_assets(item_id: str) -> None:
	write_json_if_changed(
		ITEM_MODEL_DIR / f"{item_id}.json",
		{"parent": "minecraft:item/generated", "textures": {"layer0": f"attuned:item/{item_id}"}},
	)
	write_json_if_changed(
		ITEM_DEFINITION_DIR / f"{item_id}.json",
		{"model": {"type": "minecraft:model", "model": f"attuned:item/{item_id}"}},
	)


def save_preview(icons: dict[str, Image.Image]) -> None:
	cell = 72
	preview = Image.new("RGBA", (COLS * cell, ROWS * cell), (18, 18, 22, 255))
	for focus in CUSTOM_FOCI:
		preview.alpha_composite(icons[focus.item_id], (focus.col * cell + 4, focus.row * cell + 4))
	PREVIEW_PATH.parent.mkdir(parents=True, exist_ok=True)
	preview.save(PREVIEW_PATH, optimize=True)


def generate_assets() -> dict[str, object]:
	if not SOURCE_PATH.exists():
		raise FileNotFoundError(f"Missing generated source sheet: {SOURCE_PATH}")

	source = Image.open(SOURCE_PATH).convert("RGBA")
	icons: dict[str, Image.Image] = {}
	outputs: list[dict[str, object]] = []
	TEXTURE_DIR.mkdir(parents=True, exist_ok=True)

	for focus in CUSTOM_FOCI:
		icon = fit_icon(source_cell(source, focus.row, focus.col))
		icons[focus.item_id] = icon
		output_path = TEXTURE_DIR / f"{focus.item_id}.png"
		icon.save(output_path, optimize=True)
		ensure_item_assets(focus.item_id)
		outputs.append(
			{
				"id": focus.item_id,
				"path": relative(output_path),
				"size": list(icon.size),
				"first_frame_bounds": list(alpha_bounds(icon)),
			}
		)

	save_preview(icons)
	report = {
		"source": relative(SOURCE_PATH),
		"workflow": "private local source sheet normalized into default Focus textures",
		"source_grid": [COLS, ROWS],
		"operations": [
			"crop generated source grid cells",
			"remove border-connected flat magenta key background",
			"fit each item to 64x64",
		],
		"outputs": outputs,
	}
	REPORT_PATH.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
	return report


if __name__ == "__main__":
	result = generate_assets()
	print(json.dumps({"generated": len(result["outputs"]), "preview": relative(PREVIEW_PATH)}, indent=2))
