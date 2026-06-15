import json
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FOCUS_DIR = ROOT / "src" / "main" / "resources" / "data" / "attuned" / "attuned" / "focus"
TEXTURE_DIR = ROOT / "src" / "main" / "resources" / "assets" / "attuned" / "textures" / "item"
EXPECTED_MCMETA = {"animation": {"frametime": 2, "interpolate": True}}


def git_tracked_paths() -> set[str]:
    output = subprocess.check_output(["git", "ls-files"], cwd=ROOT, text=True)
    return {line.replace("\\", "/") for line in output.splitlines() if line.strip()}


def read_png_size(path: Path) -> tuple[int, int, int, int]:
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise AssertionError(f"not a PNG file: {path}")
    width = int.from_bytes(data[16:20], "big")
    height = int.from_bytes(data[20:24], "big")
    bit_depth = data[24]
    color_type = data[25]
    return width, height, bit_depth, color_type


class FocusArtPackagingContractTest(unittest.TestCase):
    def test_every_shipped_focus_texture_is_packaged_tracked_and_animated(self) -> None:
        tracked = git_tracked_paths()
        focus_ids = sorted(
            json.loads(path.read_text(encoding="utf-8"))["item"].split(":", 1)[1]
            for path in FOCUS_DIR.glob("*_focus.json")
        )

        self.assertEqual(94, len(focus_ids), "1.5.0 currently ships 94 Focus definitions")

        for focus_id in focus_ids:
            texture = TEXTURE_DIR / f"{focus_id}.png"
            mcmeta = texture.with_suffix(texture.suffix + ".mcmeta")
            texture_rel = texture.relative_to(ROOT).as_posix()
            mcmeta_rel = mcmeta.relative_to(ROOT).as_posix()

            self.assertTrue(texture.is_file(), f"Focus texture should exist: {texture_rel}")
            self.assertTrue(mcmeta.is_file(), f"Focus animation metadata should exist: {mcmeta_rel}")
            self.assertIn(texture_rel, tracked, f"Focus texture must be git-tracked for release packaging: {texture_rel}")
            self.assertIn(mcmeta_rel, tracked, f"Focus mcmeta must be git-tracked for release packaging: {mcmeta_rel}")

            width, height, bit_depth, color_type = read_png_size(texture)
            self.assertEqual((64, 512), (width, height), f"{focus_id} should be an 8-frame 64px Focus strip")
            self.assertEqual(8, bit_depth, f"{focus_id} should use 8-bit PNG channels")
            self.assertIn(color_type, {4, 6}, f"{focus_id} should carry alpha for inventory transparency")
            self.assertEqual(EXPECTED_MCMETA, json.loads(mcmeta.read_text(encoding="utf-8")), focus_id)


if __name__ == "__main__":
    unittest.main()
