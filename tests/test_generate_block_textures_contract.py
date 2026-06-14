from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "tools" / "generate_block_textures.py"


def load_generator():
    spec = importlib.util.spec_from_file_location("generate_block_textures", MODULE_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec is not None and spec.loader is not None
    spec.loader.exec_module(module)
    return module


class GenerateBlockTexturesContractTest(unittest.TestCase):
    def test_generator_declares_all_redesigned_altar_texture_outputs(self):
        generator = load_generator()

        expected_static = {
            "attunement_altar_base.png",
            "altar_of_reweaving_base.png",
            "altar_of_reweaving_gem.png",
            "altar_of_reweaving_top.png",
        }
        expected_animated = {
            f"attunement_altar_{part}_{affinity}.png"
            for part in ("gem", "pillar", "top")
            for affinity in ("none", "fury", "bastion", "zephyr", "holy")
        }

        self.assertTrue(expected_static <= set(generator.STATIC_OUTPUTS))
        self.assertTrue(expected_animated <= set(generator.ANIMATED_OUTPUTS))

    def test_generated_redesigned_altar_textures_are_correct_sizes_and_animated(self):
        load_generator()
        texture_dir = ROOT / "src" / "main" / "resources" / "assets" / "attuned" / "textures" / "block"

        for name in (
            "attunement_altar_base.png",
            "altar_of_reweaving_base.png",
            "altar_of_reweaving_gem.png",
            "altar_of_reweaving_top.png",
        ):
            with Image.open(texture_dir / name) as image:
                self.assertEqual((64, 64), image.size, name)

        for name in (
            "attunement_altar_gem_none.png",
            "attunement_altar_gem_fury.png",
            "attunement_altar_gem_bastion.png",
            "attunement_altar_gem_zephyr.png",
            "attunement_altar_gem_holy.png",
            "attunement_altar_pillar_none.png",
            "attunement_altar_top_none.png",
        ):
            with Image.open(texture_dir / name) as image:
                self.assertEqual((64, 512), image.size, name)
                first = image.crop((0, 0, 64, 64)).tobytes()
                changed_frames = 0
                for frame in range(1, 8):
                    if image.crop((0, frame * 64, 64, frame * 64 + 64)).tobytes() != first:
                        changed_frames += 1
                self.assertEqual(7, changed_frames, name)


if __name__ == "__main__":
    unittest.main()
