from __future__ import annotations

import json
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
ITEM_TEXTURE_DIR = ROOT / "src" / "main" / "resources" / "assets" / "attuned" / "textures" / "item"
UMBRAL_DOC_DIR = ROOT / "docs" / "superpowers" / "assets" / "umbral-eclipse"
CUSTOM_DOC_DIR = ROOT / "docs" / "superpowers" / "assets" / "custom-foci"
ASPECT_REPLACEMENT_DOC_DIR = ROOT / "docs" / "superpowers" / "assets" / "aspect-counter-foci" / "replacements"
UMBRAL_GENERATOR = ROOT / "tools" / "generate_umbral_eclipse_focus_assets.py"
CUSTOM_GENERATOR = ROOT / "tools" / "generate_custom_focus_assets.py"
ASPECT_REPLACEMENT_GENERATOR = ROOT / "tools" / "generate_aspect_counter_replacement_assets.py"
UI_GENERATOR = ROOT / "tools" / "generate_ui_art.py"

UMBRAL_FOCI = (
	"gloomstride_focus",
	"duskward_focus",
	"shadowmeld_focus",
	"dreadfang_focus",
	"eclipse_focus",
)

CUSTOM_FOCI = tuple(f"custom_focus_{index}" for index in range(1, 9))

ASPECT_REPLACEMENT_FOCI = (
	"bramblegate_focus",
	"seedcall_focus",
	"riptide_heart_focus",
	"pearlguard_focus",
	"slagbrand_focus",
)

EXPECTED_MCMETA = {
	"animation": {
		"frametime": 2,
		"interpolate": True,
	}
}


def alpha_bounds(image: Image.Image) -> tuple[int, int, int, int]:
	mask = image.getchannel("A").point(lambda value: 255 if value > 8 else 0)
	return mask.getbbox() or (0, 0, 0, 0)


def frame(image: Image.Image, index: int) -> Image.Image:
	return image.crop((0, index * 64, 64, index * 64 + 64))


class GenerateImageFocusAssetsContractTest(unittest.TestCase):
	def test_generators_import_image_generated_sources(self) -> None:
		for generator, source_name in (
			(UMBRAL_GENERATOR, "umbral-eclipse-foci-source.png"),
			(CUSTOM_GENERATOR, "custom-foci-source.png"),
			(ASPECT_REPLACEMENT_GENERATOR, "aspect-counter-replacements-source.png"),
		):
			source = generator.read_text(encoding="utf-8")
			self.assertIn(source_name, source)
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
				"pixel-clean",
				"cleaned deterministic",
			):
				self.assertNotIn(forbidden, source, generator.name)

		ui_source = UI_GENERATOR.read_text(encoding="utf-8")
		self.assertIn("from generate_umbral_eclipse_focus_assets import generate_assets", ui_source)
		self.assertIn("from generate_custom_focus_assets import generate_assets", ui_source)

	def test_reports_record_image_generated_provenance(self) -> None:
		umbral_report = json.loads((UMBRAL_DOC_DIR / "umbral-eclipse-foci-report.json").read_text(encoding="utf-8"))
		custom_report = json.loads((CUSTOM_DOC_DIR / "asset-verification.json").read_text(encoding="utf-8"))
		aspect_report = json.loads((ASPECT_REPLACEMENT_DOC_DIR / "asset-verification.json").read_text(encoding="utf-8"))

		for report in (umbral_report, custom_report, aspect_report):
			self.assertEqual("OpenAI built-in image_gen", report["generated_by"])
			self.assertNotIn("cleaned deterministic", json.dumps(report))

		self.assertTrue((UMBRAL_DOC_DIR / "umbral-eclipse-foci-source.png").is_file())
		self.assertTrue((CUSTOM_DOC_DIR / "custom-foci-source.png").is_file())
		self.assertTrue((ASPECT_REPLACEMENT_DOC_DIR / "aspect-counter-replacements-source.png").is_file())

	def assert_animated_focus_textures(self, item_ids: tuple[str, ...]) -> None:
		for item_id in item_ids:
			path = ITEM_TEXTURE_DIR / f"{item_id}.png"
			with Image.open(path) as image:
				self.assertEqual("RGBA", image.mode, item_id)
				self.assertEqual((64, 512), image.size, item_id)
				first = frame(image, 0)
				changed = sum(frame(image, index).tobytes() != first.tobytes() for index in range(1, 8))
				self.assertEqual(7, changed, item_id)
				bounds = alpha_bounds(first)
				self.assertGreaterEqual(bounds[2] - bounds[0], 48, item_id)
				self.assertGreaterEqual(bounds[3] - bounds[1], 48, item_id)
				for xy in ((0, 0), (63, 0), (0, 63), (63, 63)):
					self.assertEqual(0, first.getpixel(xy)[3], f"{item_id} corner {xy}")

			mcmeta = json.loads(path.with_suffix(path.suffix + ".mcmeta").read_text(encoding="utf-8"))
			self.assertEqual(EXPECTED_MCMETA, mcmeta, item_id)

	def test_umbral_focus_replacements_are_animated_focus_textures(self) -> None:
		self.assert_animated_focus_textures(UMBRAL_FOCI)

	def test_aspect_counter_replacements_are_animated_focus_textures(self) -> None:
		self.assert_animated_focus_textures(ASPECT_REPLACEMENT_FOCI)

	def test_custom_focus_replacements_are_static_item_textures(self) -> None:
		for item_id in CUSTOM_FOCI:
			path = ITEM_TEXTURE_DIR / f"{item_id}.png"
			with Image.open(path) as image:
				self.assertEqual("RGBA", image.mode, item_id)
				self.assertEqual((64, 64), image.size, item_id)
				bounds = alpha_bounds(image)
				self.assertGreaterEqual(bounds[2] - bounds[0], 48, item_id)
				self.assertGreaterEqual(bounds[3] - bounds[1], 48, item_id)
				for xy in ((0, 0), (63, 0), (0, 63), (63, 63)):
					self.assertEqual(0, image.getpixel(xy)[3], f"{item_id} corner {xy}")
			self.assertFalse(path.with_suffix(path.suffix + ".mcmeta").exists(), item_id)


if __name__ == "__main__":
	unittest.main()
