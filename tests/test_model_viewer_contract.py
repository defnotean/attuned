from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
VIEWER_DIR = ROOT / "tools" / "attuned_model_viewer"


class ModelViewerContractTest(unittest.TestCase):
    def test_model_viewer_files_are_present(self):
        self.assertTrue((VIEWER_DIR / "index.html").is_file())
        self.assertTrue((VIEWER_DIR / "model-viewer.js").is_file())

    def test_model_viewer_loads_real_mod_assets(self):
        script = (VIEWER_DIR / "model-viewer.js").read_text(encoding="utf-8")

        self.assertIn('const ASSET_ROOT = "/src/main/resources/assets"', script)
        self.assertIn("resolveModel", script)
        self.assertIn("resolveTextureReference", script)
        self.assertIn("attuned:block/attunement_altar_holy", script)
        self.assertIn("attuned:block/altar_of_reweaving", script)

    def test_model_viewer_uses_explicit_minecraft_face_geometry(self):
        script = (VIEWER_DIR / "model-viewer.js").read_text(encoding="utf-8")

        self.assertIn("new THREE.BufferGeometry()", script)
        self.assertIn('geometry.setAttribute("position"', script)
        self.assertIn('geometry.setAttribute("uv"', script)
        self.assertIn("applyAxisAngle", script)
        self.assertIn("new THREE.CanvasTexture", script)
        self.assertIn("THREE.NearestFilter", script)

    def test_model_viewer_page_exposes_canvas_and_controls(self):
        html = (VIEWER_DIR / "index.html").read_text(encoding="utf-8")

        self.assertIn('<canvas id="viewer"', html)
        self.assertIn('<select id="modelSelect"', html)
        self.assertIn('src="https://cdn.jsdelivr.net/npm/three@0.128.0/build/three.min.js"', html)
        self.assertIn('src="./model-viewer.js"', html)


if __name__ == "__main__":
    unittest.main()
