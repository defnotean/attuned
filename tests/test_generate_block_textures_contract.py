from __future__ import annotations

import struct
import unittest
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "tools" / "generate_block_textures.py"


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
    # Generated block textures are 8-bit RGBA PNGs. Each decompressed PNG row
    # starts with a filter byte followed by width * 4 pixel bytes.
    row_stride = 1 + width * 4
    start = frame * frame_height * row_stride
    end = start + frame_height * row_stride
    return raw[start:end]


class GenerateBlockTexturesContractTest(unittest.TestCase):
    def test_generator_declares_all_redesigned_altar_texture_outputs(self):
        source = MODULE_PATH.read_text(encoding="utf-8")

        expected_static = {
            "attunement_altar_base.png",
            "altar_of_reweaving_base.png",
            "altar_of_reweaving_gem.png",
            "altar_of_reweaving_top.png",
        }
        for output in expected_static:
            self.assertIn(output, source)
        self.assertIn("STATIC_OUTPUTS = (", source)
        self.assertIn("ANIMATED_OUTPUTS = tuple(", source)
        self.assertIn('f"attunement_altar_{part}_{affinity}.png"', source)
        for part in ("gem", "pillar", "top"):
            self.assertIn(f'"{part}"', source)
        for affinity in ("none", "fury", "bastion", "zephyr", "holy", "tide", "forge", "verdant", "umbral"):
            self.assertIn(f'"{affinity}"', source)

    def test_generated_redesigned_altar_textures_are_correct_sizes_and_animated(self):
        texture_dir = ROOT / "src" / "main" / "resources" / "assets" / "attuned" / "textures" / "block"

        for name in (
            "attunement_altar_base.png",
            "altar_of_reweaving_base.png",
            "altar_of_reweaving_gem.png",
            "altar_of_reweaving_top.png",
        ):
            width, height, bit_depth, color_type, _ = read_png(texture_dir / name)
            self.assertEqual((64, 64), (width, height), name)
            self.assertEqual(8, bit_depth, name)
            self.assertEqual(6, color_type, name)

        for name in (
            "attunement_altar_gem_none.png",
            "attunement_altar_gem_fury.png",
            "attunement_altar_gem_bastion.png",
            "attunement_altar_gem_zephyr.png",
            "attunement_altar_gem_holy.png",
            "attunement_altar_pillar_none.png",
            "attunement_altar_top_none.png",
            "attunement_altar_gem_tide.png",
            "attunement_altar_gem_forge.png",
            "attunement_altar_gem_verdant.png",
            "attunement_altar_gem_umbral.png",
            "attunement_altar_pillar_tide.png",
            "attunement_altar_top_tide.png",
        ):
            width, height, bit_depth, color_type, raw = read_png(texture_dir / name)
            self.assertEqual((64, 512), (width, height), name)
            self.assertEqual(8, bit_depth, name)
            self.assertEqual(6, color_type, name)
            first = frame_rows(raw, width=width, frame=0)
            changed_frames = 0
            for frame in range(1, 8):
                if frame_rows(raw, width=width, frame=frame) != first:
                    changed_frames += 1
            self.assertEqual(7, changed_frames, name)


if __name__ == "__main__":
    unittest.main()
