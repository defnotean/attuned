from __future__ import annotations

"""Generate forward-compatible art for the expanded Apex capstones.

The source concept sheet lives under docs/superpowers/assets/apex-capstones.
This script redraws the game-facing sprites deterministically so the HUD art
stays readable at 16-20 GUI pixels and the altar variants match the existing
animated texture strip format.
"""

import json
from pathlib import Path

from PIL import Image, ImageDraw

import generate_block_textures as altar_textures


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/attuned"
HUD_DIR = ASSETS / "textures/gui/sprites/hud"
BLOCK_TEXTURE_DIR = ASSETS / "textures/block"
BLOCK_MODEL_DIR = ASSETS / "models/block"
DOC_ASSET_DIR = ROOT / "docs/superpowers/assets/apex-capstones"

SIZE = 64
CLEAR = (0, 0, 0, 0)
INK = (5, 5, 9, 255)
MCMETA = {"animation": {"frametime": 3, "interpolate": True}}


PALETTES = {
    "tide": {
        "void": (5, 14, 24, 255),
        "dark": (8, 46, 62, 255),
        "mid": (24, 118, 148, 255),
        "rim": (72, 214, 228, 255),
        "glint": (205, 255, 250, 255),
        "accent": (238, 247, 238, 255),
    },
    "forge": {
        "void": (23, 12, 9, 255),
        "dark": (58, 29, 19, 255),
        "mid": (150, 74, 26, 255),
        "rim": (235, 134, 42, 255),
        "glint": (255, 226, 128, 255),
        "accent": (255, 91, 38, 255),
    },
    "verdant": {
        "void": (8, 20, 11, 255),
        "dark": (27, 56, 24, 255),
        "mid": (76, 132, 48, 255),
        "rim": (132, 210, 80, 255),
        "glint": (226, 255, 168, 255),
        "accent": (247, 229, 190, 255),
    },
    "umbral": {
        "void": (12, 8, 23, 255),
        "dark": (31, 22, 55, 255),
        "mid": (75, 52, 122, 255),
        "rim": (154, 112, 230, 255),
        "glint": (226, 202, 255, 255),
        "accent": (102, 58, 190, 255),
    },
}

CAPSTONES = {
    "riptide": "tide",
    "crucible": "forge",
    "bloomward": "verdant",
    "gloaming": "umbral",
}


def clamp(value: int) -> int:
    return max(0, min(255, value))


def shade(color: tuple[int, int, int, int], amount: int) -> tuple[int, int, int, int]:
    return tuple(clamp(color[i] + amount) if i < 3 else color[i] for i in range(4))


def closed_line(draw: ImageDraw.ImageDraw, points, fill, width=1) -> None:
    draw.line([*points, points[0]], fill=fill, width=width, joint="curve")


def diamond(draw: ImageDraw.ImageDraw, cx: int, cy: int, radius: int, fill, outline=INK) -> None:
    points = [(cx, cy - radius), (cx + radius, cy), (cx, cy + radius), (cx - radius, cy)]
    draw.polygon(points, fill=fill, outline=outline)


def medallion(draw: ImageDraw.ImageDraw, palette: dict[str, tuple[int, int, int, int]]) -> None:
    shadow = [(33, 3), (48, 7), (59, 18), (63, 33), (58, 49), (47, 60),
              (31, 63), (16, 59), (5, 48), (1, 32), (6, 17), (18, 6)]
    outer = [(32, 2), (47, 6), (58, 17), (62, 32), (58, 47), (47, 58),
             (32, 62), (17, 58), (6, 47), (2, 32), (6, 17), (17, 6)]
    bevel = [(32, 5), (45, 9), (55, 19), (59, 32), (55, 45), (45, 55),
             (32, 59), (19, 55), (9, 45), (5, 32), (9, 19), (19, 9)]
    face = [(32, 11), (43, 14), (51, 23), (53, 32), (50, 42), (42, 51),
            (32, 54), (22, 51), (14, 42), (11, 32), (13, 23), (22, 14)]

    draw.polygon(shadow, fill=(0, 0, 0, 130))
    draw.polygon(outer, fill=INK)
    draw.polygon(bevel, fill=palette["dark"])
    closed_line(draw, bevel, shade(palette["rim"], -45), 2)
    draw.line((18, 9, 32, 5, 46, 9, 55, 18), fill=palette["glint"], width=3)
    draw.line((58, 32, 54, 45, 45, 55, 32, 59, 19, 55), fill=palette["void"], width=3)
    draw.polygon(face, fill=palette["mid"])
    closed_line(draw, face, palette["rim"], 3)
    for x, y in ((32, 7), (57, 32), (32, 57), (7, 32)):
        diamond(draw, x, y, 4, palette["dark"], INK)
        draw.point((x - 1, y - 1), fill=palette["glint"])


def draw_tide_glyph(draw: ImageDraw.ImageDraw, palette, *, capstone: bool) -> None:
    shift = -1 if capstone else 0
    draw.arc((12, 14 + shift, 52, 54 + shift), 205, 35, fill=INK, width=8)
    draw.arc((13, 15 + shift, 51, 53 + shift), 205, 35, fill=palette["rim"], width=5)
    draw.arc((21, 20 + shift, 47, 46 + shift), 210, 80, fill=palette["glint"], width=4)
    draw.polygon([(43, 24), (54, 29), (45, 36)], fill=palette["glint"], outline=INK)
    draw.ellipse((25, 31, 41, 47), fill=INK)
    draw.ellipse((27, 29, 41, 43), fill=palette["accent"], outline=palette["rim"])
    if capstone:
        draw.line((17, 41, 25, 37, 37, 41, 49, 35), fill=palette["glint"], width=3)


def draw_forge_glyph(draw: ImageDraw.ImageDraw, palette, *, capstone: bool) -> None:
    draw.polygon([(16, 31), (48, 31), (43, 48), (21, 48)], fill=INK)
    draw.polygon([(19, 32), (45, 32), (40, 45), (24, 45)], fill=palette["dark"])
    draw.line((21, 34, 43, 34), fill=palette["glint"], width=3)
    draw.line((25, 46, 39, 46), fill=palette["rim"], width=3)
    if capstone:
        draw.polygon([(32, 12), (42, 26), (34, 25), (39, 38), (25, 26), (31, 26)],
                     fill=palette["glint"], outline=INK)
        draw.line((31, 14, 33, 28, 28, 26), fill=palette["accent"], width=2)
    else:
        draw.polygon([(32, 13), (40, 25), (35, 24), (38, 34), (29, 27), (25, 32), (28, 23)],
                     fill=palette["glint"], outline=INK)
    draw.line((17, 52, 47, 52), fill=palette["rim"], width=2)


def draw_verdant_glyph(draw: ImageDraw.ImageDraw, palette, *, capstone: bool) -> None:
    if capstone:
        shield = [(32, 13), (47, 20), (43, 41), (32, 54), (21, 41), (17, 20)]
        draw.polygon(shield, fill=INK)
        inner = [(32, 17), (43, 22), (40, 39), (32, 49), (24, 39), (21, 22)]
        draw.polygon(inner, fill=palette["dark"])
        closed_line(draw, inner, palette["rim"], 3)
    draw.line((32, 46, 32, 23), fill=INK, width=6)
    draw.line((32, 46, 32, 24), fill=palette["rim"], width=3)
    draw.ellipse((17, 24, 33, 38), fill=palette["dark"], outline=INK)
    draw.ellipse((31, 22, 48, 37), fill=palette["dark"], outline=INK)
    draw.line((22, 31, 32, 31, 43, 29), fill=palette["glint"], width=2)
    flower = [(32, 16), (39, 24), (36, 34), (28, 34), (25, 24)]
    draw.polygon(flower, fill=palette["accent"], outline=INK)
    draw.polygon([(32, 19), (36, 24), (34, 31), (30, 31), (28, 24)],
                 fill=palette["glint"])
    draw.point((32, 24), fill=palette["rim"])


def draw_umbral_glyph(draw: ImageDraw.ImageDraw, palette, *, capstone: bool) -> None:
    draw.ellipse((18, 13, 48, 43), fill=INK)
    draw.ellipse((20, 14, 46, 40), fill=palette["glint"])
    draw.ellipse((30, 10, 54, 38), fill=palette["mid"])
    draw.ellipse((32, 12, 54, 38), fill=palette["void"])
    draw.arc((16, 28, 48, 56), 205, 25, fill=INK, width=5)
    draw.arc((17, 29, 47, 55), 205, 25, fill=palette["rim"], width=3)
    if capstone:
        draw.arc((11, 19, 42, 53), 300, 130, fill=palette["accent"], width=3)
    for x, y in ((21, 46), (34, 51), (48, 44), (47, 18)):
        draw.line((x - 3, y, x + 3, y), fill=palette["glint"], width=2)
        draw.line((x, y - 3, x, y + 3), fill=palette["glint"], width=2)


def draw_icon(aspect: str, *, capstone_name: str | None = None) -> Image.Image:
    palette = PALETTES[aspect]
    image = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    draw = ImageDraw.Draw(image, "RGBA")
    medallion(draw, palette)
    capstone = capstone_name is not None
    if aspect == "tide":
        draw_tide_glyph(draw, palette, capstone=capstone)
    elif aspect == "forge":
        draw_forge_glyph(draw, palette, capstone=capstone)
    elif aspect == "verdant":
        draw_verdant_glyph(draw, palette, capstone=capstone)
    elif aspect == "umbral":
        draw_umbral_glyph(draw, palette, capstone=capstone)

    if capstone:
        # Small crown tick at the top makes capstones distinct from plain affinity gems.
        draw.line((25, 6, 32, 1, 39, 6), fill=palette["glint"], width=2)
        draw.point((32, 2), fill=(255, 255, 255, 255))
    return image


def generate_hud_sprites() -> list[Path]:
    HUD_DIR.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    for aspect in PALETTES:
        out = HUD_DIR / f"affinity_{aspect}.png"
        draw_icon(aspect).save(out)
        written.append(out)
    for name, aspect in CAPSTONES.items():
        out = HUD_DIR / f"{name}.png"
        draw_icon(aspect, capstone_name=name).save(out)
        written.append(out)
    return written


def generate_altar_variants() -> list[Path]:
    written: list[Path] = []
    for affinity in PALETTES:
        for part, factory in (
            ("gem", altar_textures.make_attunement_gem),
            ("pillar", altar_textures.make_attunement_pillar),
            ("top", altar_textures.make_attunement_top),
        ):
            name = f"attunement_altar_{part}_{affinity}.png"
            image = altar_textures.animated_strip(
                lambda frame, affinity=affinity, factory=factory: factory(affinity, frame)
            )
            out = BLOCK_TEXTURE_DIR / name
            image.save(out)
            written.append(out)
            meta = out.with_suffix(out.suffix + ".mcmeta")
            meta.write_text(json.dumps(MCMETA, indent=2) + "\n", encoding="utf-8")
            written.append(meta)

    BLOCK_MODEL_DIR.mkdir(parents=True, exist_ok=True)
    for affinity in PALETTES:
        out = BLOCK_MODEL_DIR / f"attunement_altar_{affinity}.json"
        model = {
            "parent": "attuned:block/attunement_altar",
            "textures": {
                "pillar": f"attuned:block/attunement_altar_pillar_{affinity}",
                "top": f"attuned:block/attunement_altar_top_{affinity}",
                "gem": f"attuned:block/attunement_altar_gem_{affinity}",
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
    labels = [
        ("affinity_tide", "riptide", "tide"),
        ("affinity_forge", "crucible", "forge"),
        ("affinity_verdant", "bloomward", "verdant"),
        ("affinity_umbral", "gloaming", "umbral"),
    ]
    preview = Image.new("RGBA", (cell * len(labels), cell * 3), (16, 16, 20, 255))
    draw = ImageDraw.Draw(preview)
    for index, (affinity_sprite, capstone_sprite, aspect) in enumerate(labels):
        x = index * cell + 11
        preview.alpha_composite(Image.open(HUD_DIR / f"{affinity_sprite}.png").convert("RGBA"), (x, 8))
        preview.alpha_composite(Image.open(HUD_DIR / f"{capstone_sprite}.png").convert("RGBA"), (x, cell + 4))
        preview.alpha_composite(first_frame(BLOCK_TEXTURE_DIR / f"attunement_altar_gem_{aspect}.png"), (x, cell * 2))
        draw.text((index * cell + 6, cell - 12), affinity_sprite.replace("affinity_", ""), fill=(230, 230, 230, 255))
        draw.text((index * cell + 6, cell * 2 - 12), capstone_sprite, fill=(230, 230, 230, 255))
    out = DOC_ASSET_DIR / "apex-capstone-assets-preview.png"
    preview.save(out)
    return out


def verify(paths: list[Path], preview: Path) -> Path:
    report = {
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
    print(f"wrote {len(paths)} generated asset files")


if __name__ == "__main__":
    main()
