from __future__ import annotations

import struct
import unittest
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GENERATOR = ROOT / "tools" / "generate_apex_capstone_assets.py"
HUD_DIR = ROOT / "src" / "main" / "resources" / "assets" / "attuned" / "textures" / "gui" / "sprites" / "hud"
BLOCK_DIR = ROOT / "src" / "main" / "resources" / "assets" / "attuned" / "textures" / "block"


def read_png(path: Path):
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise AssertionError(f"not a PNG: {path}")
    offset = 8
    width = height = bit_depth = color_type = None
    idat = bytearray()
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        chunk_type = data[offset + 4:offset + 8]
        chunk_data = data[offset + 8:offset + 8 + length]
        offset += 12 + length
        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type, _, _, _ = struct.unpack(">IIBBBBB", chunk_data)
        elif chunk_type == b"IDAT":
            idat.extend(chunk_data)
        elif chunk_type == b"IEND":
            break
    if width is None or height is None or bit_depth is None or color_type is None:
        raise AssertionError(f"missing PNG IHDR: {path}")
    return width, height, bit_depth, color_type, zlib.decompress(bytes(idat))


def frame_rows(raw: bytes, *, width: int, frame: int, frame_height: int = 64) -> bytes:
    row_stride = 1 + width * 4
    start = frame * frame_height * row_stride
    end = start + frame_height * row_stride
    return raw[start:end]


class GenerateApexCapstoneAssetsContractTest(unittest.TestCase):
    def test_generator_imports_local_source_sheets(self) -> None:
        source = GENERATOR.read_text(encoding="utf-8")

        self.assertIn("apex-capstones-hud-source.png", source)
        self.assertIn("apex-capstones-altar-source.png", source)
        self.assertIn("crop, chroma-key, resize", source)
        self.assertIn(".attuned-art-sources", source)
        self.assertIn("asset-previews", source)
        self.assertNotIn("draw_tide_glyph", source)
        self.assertNotIn("medallion(", source)

    def test_generated_hud_and_altar_assets_have_minecraft_sizes(self) -> None:
        for name in (
            "affinity_tide.png",
            "affinity_forge.png",
            "affinity_verdant.png",
            "affinity_umbral.png",
            "riptide.png",
            "crucible.png",
            "bloomward.png",
            "gloaming.png",
        ):
            width, height, bit_depth, color_type, _ = read_png(HUD_DIR / name)
            self.assertEqual((64, 64), (width, height), name)
            self.assertEqual(8, bit_depth, name)
            self.assertEqual(6, color_type, name)

        for aspect in ("tide", "forge", "verdant", "umbral"):
            for part in ("gem", "pillar", "top"):
                name = f"attunement_altar_{part}_{aspect}.png"
                width, height, bit_depth, color_type, raw = read_png(BLOCK_DIR / name)
                self.assertEqual((64, 512), (width, height), name)
                self.assertEqual(8, bit_depth, name)
                self.assertEqual(6, color_type, name)
                first = frame_rows(raw, width=width, frame=0)
                changed_frames = sum(
                    frame_rows(raw, width=width, frame=frame) != first
                    for frame in range(1, 8)
                )
                self.assertEqual(7, changed_frames, name)


if __name__ == "__main__":
    unittest.main()
