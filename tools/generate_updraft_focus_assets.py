from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image, ImageEnhance

from generate_umbral_eclipse_focus_assets import alpha_bounds, remove_key_background


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / ".attuned-art-sources" / "updraft-focus"
OUTPUT_DIR = ROOT / "build" / "asset-previews" / "updraft-focus"
SOURCE_PATH = SOURCE_DIR / "updraft-focus-source.png"
PREVIEW_PATH = OUTPUT_DIR / "updraft-focus-preview.png"
REPORT_PATH = OUTPUT_DIR / "asset-verification.json"
TEXTURE_DIR = ROOT / "src" / "main" / "resources" / "assets" / "attuned" / "textures" / "item"
ITEM_ID = "updraft_focus"

ICON_SIZE = 64
FRAME_COUNT = 8
MC_META = {
	"animation": {
		"frametime": 2,
		"interpolate": True,
	}
}


def relative(path: Path) -> str:
	return path.relative_to(ROOT).as_posix()


def fit_icon(source: Image.Image) -> Image.Image:
	with_alpha = remove_key_background(source)
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
	brightness = 0.95 + frame_index * 0.007 + phase * 0.05
	color = 0.98 + frame_index * 0.005 + max(phase, 0) * 0.08
	frame = ImageEnhance.Brightness(icon).enhance(brightness)
	return ImageEnhance.Color(frame).enhance(color)


def animated_strip(icon: Image.Image) -> Image.Image:
	strip = Image.new("RGBA", (ICON_SIZE, ICON_SIZE * FRAME_COUNT), (0, 0, 0, 0))
	for frame_index in range(FRAME_COUNT):
		strip.alpha_composite(animation_frame(icon, frame_index), (0, ICON_SIZE * frame_index))
	return strip


def save_mcmeta(path: Path) -> None:
	path.with_suffix(path.suffix + ".mcmeta").write_text(json.dumps(MC_META, indent=2), encoding="utf-8")


def generate_assets() -> dict[str, object]:
	if not SOURCE_PATH.exists():
		raise FileNotFoundError(f"Missing generated source image: {SOURCE_PATH}")

	source = Image.open(SOURCE_PATH).convert("RGBA")
	icon = fit_icon(source)
	strip = animated_strip(icon)
	output_path = TEXTURE_DIR / f"{ITEM_ID}.png"
	TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
	strip.save(output_path, optimize=True)
	save_mcmeta(output_path)

	preview = Image.new("RGBA", (ICON_SIZE + 16, ICON_SIZE + 16), (18, 18, 22, 255))
	preview.alpha_composite(icon, (8, 8))
	OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
	preview.save(PREVIEW_PATH, optimize=True)

	report = {
		"source": relative(SOURCE_PATH),
		"preview": relative(PREVIEW_PATH),
		"workflow": "local source image normalized into the Updraft Focus texture",
		"output": {
			"id": ITEM_ID,
			"path": relative(output_path),
			"size": list(strip.size),
			"frames": FRAME_COUNT,
			"first_frame_bounds": list(alpha_bounds(icon)),
		},
	}
	REPORT_PATH.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
	return report


if __name__ == "__main__":
	result = generate_assets()
	print(json.dumps(result, indent=2))
