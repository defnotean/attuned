from __future__ import annotations

import json
import struct
import unittest
import zlib
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
GENERATOR = ROOT / "tools" / "generate_modifier_focus_assets.py"
DOC_DIR = ROOT / "docs" / "superpowers" / "assets" / "modifier-foci"
TEXTURE_DIR = ROOT / "src" / "main" / "resources" / "assets" / "attuned" / "textures" / "item"

FOCUS_IDS = (
	"tidewarden_focus",
	"wellspring_focus",
	"current_runner_focus",
	"saltbrand_focus",
	"ebbstride_focus",
	"overgrowth_focus",
	"deeproot_focus",
	"briarcoat_focus",
	"fernstride_focus",
	"sapflow_focus",
	"cinderplate_focus",
	"bellowsfury_focus",
	"bloodrush_focus",
	"ravager_focus",
	"granitehide_focus",
	"hammerward_focus",
)

EXPECTED_MCMETA = {
	"animation": {
		"frametime": 2,
		"interpolate": True,
	}
}


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


class GenerateModifierFocusAssetsContractTest(unittest.TestCase):
	def test_generator_imports_image_generated_source_sheet(self) -> None:
		source = GENERATOR.read_text(encoding="utf-8")

		self.assertIn("modifier-foci-source.png", source)
		self.assertIn("crop generated source grid cells", source)
		self.assertIn("remove flat magenta key background", source)
		for forbidden in (
			"ImageDraw",
			"draw_",
			".line(",
			".rectangle(",
			".ellipse(",
			".polygon(",
			".arc(",
			"medallion(",
			"glyph",
		):
			self.assertNotIn(forbidden, source)

		self.assertTrue((DOC_DIR / "modifier-foci-source.png").is_file())
		self.assertTrue((DOC_DIR / "modifier-foci-preview.png").is_file())

	def test_generated_focus_assets_have_minecraft_sizes_and_animation(self) -> None:
		for focus_id in FOCUS_IDS:
			name = f"{focus_id}.png"
			path = TEXTURE_DIR / name
			width, height, bit_depth, color_type, raw = read_png(path)
			self.assertEqual((64, 512), (width, height), name)
			self.assertEqual(8, bit_depth, name)
			self.assertEqual(6, color_type, name)
			first = frame_rows(raw, width=width, frame=0)
			changed_frames = sum(
				frame_rows(raw, width=width, frame=frame) != first
				for frame in range(1, 8)
			)
			self.assertEqual(7, changed_frames, name)

			with Image.open(path) as image:
				first_frame = image.convert("RGBA").crop((0, 0, 64, 64))
				for xy in ((0, 0), (63, 0), (0, 63), (63, 63)):
					self.assertEqual(0, first_frame.getpixel(xy)[3], f"{name} corner {xy}")

			mcmeta = json.loads(path.with_suffix(path.suffix + ".mcmeta").read_text(encoding="utf-8"))
			self.assertEqual(EXPECTED_MCMETA, mcmeta, name)


if __name__ == "__main__":
	unittest.main()
