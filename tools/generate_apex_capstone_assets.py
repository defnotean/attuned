from __future__ import annotations

"""Import image-generated art for the expanded Apex capstones.

The game-facing assets are cropped from image-generated source sheets under
docs/superpowers/assets/apex-capstones. This script only normalizes them for
Minecraft: crop, chroma-key, resize, assemble animation strips, and write a
small verification preview.
"""

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/attuned"
HUD_DIR = ASSETS / "textures/gui/sprites/hud"
BLOCK_TEXTURE_DIR = ASSETS / "textures/block"
BLOCK_MODEL_DIR = ASSETS / "models/block"
DOC_ASSET_DIR = ROOT / "docs/superpowers/assets/apex-capstones"
HUD_SOURCE = DOC_ASSET_DIR / "apex-capstones-hud-source.png"
ALTAR_SOURCE = DOC_ASSET_DIR / "apex-capstones-altar-source.png"

SIZE = 64
FRAMES = 8
MCMETA = {"animation": {"frametime": 3, "interpolate": True}}

CAPSTONE_ROW = ("riptide", "crucible", "bloomward", "gloaming")
AFFINITY_ROW = ("affinity_tide", "affinity_forge", "affinity_verdant", "affinity_umbral")
ASPECTS = ("tide", "forge", "verdant", "umbral")
ALTAR_PARTS = ("gem", "pillar", "top")
ASPECT_TINTS = {
    "tide": (72, 214, 228, 255),
    "forge": (235, 134, 42, 255),
    "verdant": (132, 210, 80, 255),
    "umbral": (154, 112, 230, 255),
}


def crop_grid(image: Image.Image, columns: int, rows: int, column: int, row: int) -> Image.Image:
    width, height = image.size
    left = round(width * column / columns)
    upper = round(height * row / rows)
    right = round(width * (column + 1) / columns)
    lower = round(height * (row + 1) / rows)
    return image.crop((left, upper, right, lower))


def remove_green_key(image: Image.Image) -> Image.Image:
    image = image.convert("RGBA")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            if g > 220 and r < 70 and b < 70:
                pixels[x, y] = (r, g, b, 0)
            elif g > 190 and r < 95 and b < 95:
                pixels[x, y] = (r, min(g, max(r, b) + 40), b, min(a, 96))
    return image


def trim_alpha(image: Image.Image, padding_ratio: float = 0.06) -> Image.Image:
    alpha = image.getchannel("A")
    bbox = alpha.getbbox()
    if bbox is None:
        return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    left, upper, right, lower = bbox
    pad = round(max(right - left, lower - upper) * padding_ratio)
    left = max(0, left - pad)
    upper = max(0, upper - pad)
    right = min(image.width, right + pad)
    lower = min(image.height, lower + pad)
    return image.crop((left, upper, right, lower))


def trim_non_white(image: Image.Image, padding_ratio: float = 0.01) -> Image.Image:
    image = image.convert("RGBA")
    pixels = image.load()
    min_x, min_y = image.width, image.height
    max_x, max_y = -1, -1
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            if a > 0 and not (r > 244 and g > 244 and b > 244):
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)
    if max_x < min_x or max_y < min_y:
        return image
    pad = round(max(max_x - min_x + 1, max_y - min_y + 1) * padding_ratio)
    return image.crop((
        max(0, min_x - pad),
        max(0, min_y - pad),
        min(image.width, max_x + 1 + pad),
        min(image.height, max_y + 1 + pad),
    ))


def square_fit(image: Image.Image, background=(0, 0, 0, 0)) -> Image.Image:
    side = max(image.width, image.height)
    square = Image.new("RGBA", (side, side), background)
    square.alpha_composite(image.convert("RGBA"), ((side - image.width) // 2, (side - image.height) // 2))
    return square


def resize_icon(image: Image.Image) -> Image.Image:
    return square_fit(trim_alpha(remove_green_key(image))).resize((SIZE, SIZE), Image.Resampling.LANCZOS)


def resize_tile(image: Image.Image) -> Image.Image:
    return square_fit(trim_non_white(image), (0, 0, 0, 255)).resize((SIZE, SIZE), Image.Resampling.LANCZOS)


def animation_strip(tile: Image.Image, aspect: str) -> Image.Image:
    strip = Image.new("RGBA", (SIZE, SIZE * FRAMES), (0, 0, 0, 0))
    tint = Image.new("RGBA", (SIZE, SIZE), ASPECT_TINTS[aspect])
    for frame in range(FRAMES):
        pulse = (math.sin(frame / FRAMES * math.tau) + 1.0) * 0.5
        bright = ImageEnhance.Brightness(tile).enhance(0.94 + pulse * 0.12)
        saturated = ImageEnhance.Color(bright).enhance(0.96 + pulse * 0.10)
        overlay = Image.blend(saturated, tint, 0.02 + pulse * 0.04)
        strip.alpha_composite(overlay, (0, frame * SIZE))
    return strip


def generate_hud_sprites() -> list[Path]:
    HUD_DIR.mkdir(parents=True, exist_ok=True)
    source = Image.open(HUD_SOURCE)
    written: list[Path] = []
    rows = (CAPSTONE_ROW, AFFINITY_ROW)
    for row_index, names in enumerate(rows):
        for column, name in enumerate(names):
            out = HUD_DIR / f"{name}.png"
            resize_icon(crop_grid(source, 4, 2, column, row_index)).save(out)
            written.append(out)
    return written


def generate_altar_variants() -> list[Path]:
    BLOCK_TEXTURE_DIR.mkdir(parents=True, exist_ok=True)
    source = Image.open(ALTAR_SOURCE)
    written: list[Path] = []
    for column, aspect in enumerate(ASPECTS):
        for row, part in enumerate(ALTAR_PARTS):
            out = BLOCK_TEXTURE_DIR / f"attunement_altar_{part}_{aspect}.png"
            tile = resize_tile(crop_grid(source, 4, 3, column, row))
            animation_strip(tile, aspect).save(out)
            written.append(out)
            meta = out.with_suffix(out.suffix + ".mcmeta")
            meta.write_text(json.dumps(MCMETA, indent=2) + "\n", encoding="utf-8")
            written.append(meta)

    BLOCK_MODEL_DIR.mkdir(parents=True, exist_ok=True)
    for aspect in ASPECTS:
        out = BLOCK_MODEL_DIR / f"attunement_altar_{aspect}.json"
        model = {
            "parent": "attuned:block/attunement_altar",
            "textures": {
                "pillar": f"attuned:block/attunement_altar_pillar_{aspect}",
                "top": f"attuned:block/attunement_altar_top_{aspect}",
                "gem": f"attuned:block/attunement_altar_gem_{aspect}",
            },
        }
        out.write_text(json.dumps(model, indent="\t") + "\n", encoding="utf-8")
        written.append(out)
    return written


def first_frame(path: Path) -> Image.Image:
    image = Image.open(path).convert("RGBA")
    return image.crop((0, 0, SIZE, SIZE))


def make_preview() -> Path:
    DOC_ASSET_DIR.mkdir(parents=True, exist_ok=True)
    cell = 86
    preview = Image.new("RGBA", (cell * 4, cell * 3), (16, 16, 20, 255))
    draw = ImageDraw.Draw(preview)
    for index, aspect in enumerate(ASPECTS):
        affinity = f"affinity_{aspect}"
        capstone = CAPSTONE_ROW[index]
        x = index * cell + 11
        preview.alpha_composite(Image.open(HUD_DIR / f"{affinity}.png").convert("RGBA"), (x, 8))
        preview.alpha_composite(Image.open(HUD_DIR / f"{capstone}.png").convert("RGBA"), (x, cell + 4))
        preview.alpha_composite(first_frame(BLOCK_TEXTURE_DIR / f"attunement_altar_gem_{aspect}.png"), (x, cell * 2))
        draw.text((index * cell + 6, cell - 12), aspect, fill=(230, 230, 230, 255))
        draw.text((index * cell + 6, cell * 2 - 12), capstone, fill=(230, 230, 230, 255))
    out = DOC_ASSET_DIR / "apex-capstone-assets-preview.png"
    preview.save(out)
    return out


def verify(paths: list[Path], preview: Path) -> Path:
    report = {
        "sources": [
            HUD_SOURCE.relative_to(ROOT).as_posix(),
            ALTAR_SOURCE.relative_to(ROOT).as_posix(),
        ],
        "hudSprites": {},
        "altarTextures": {},
        "blockModels": [],
        "preview": preview.relative_to(ROOT).as_posix(),
    }
    for path in paths:
        relative = path.relative_to(ROOT).as_posix()
        if path.suffix == ".png":
            image = Image.open(path)
            entry = {"width": image.width, "height": image.height, "mode": image.mode}
            if "textures/gui/sprites/hud" in relative:
                report["hudSprites"][relative] = entry
            elif "textures/block" in relative:
                report["altarTextures"][relative] = entry
        elif path.suffix == ".json":
            report["blockModels"].append(relative)
    out = DOC_ASSET_DIR / "asset-verification.json"
    out.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return out


def main() -> None:
    paths = []
    paths.extend(generate_hud_sprites())
    paths.extend(generate_altar_variants())
    preview = make_preview()
    report = verify(paths, preview)
    print(f"wrote {preview.relative_to(ROOT).as_posix()}")
    print(f"wrote {report.relative_to(ROOT).as_posix()}")
    print(f"imported {len(paths)} game asset files from image-generated source sheets")


if __name__ == "__main__":
    main()
