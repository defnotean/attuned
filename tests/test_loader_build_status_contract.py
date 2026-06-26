from __future__ import annotations

import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class LoaderBuildStatusContractTest(unittest.TestCase):
    def test_gradle_declares_loader_status_task_without_enabling_future_modules(self) -> None:
        build_gradle = (ROOT / "build.gradle").read_text(encoding="utf-8")
        settings_gradle = (ROOT / "settings.gradle").read_text(encoding="utf-8")
        active_settings = "\n".join(
            line for line in settings_gradle.splitlines()
            if not line.strip().startswith("//")
        )

        self.assertIn('tasks.register("loaderSupportStatus")', build_gradle)
        self.assertIn("Implemented loader artifact: Fabric", build_gradle)
        self.assertIn("Quilt compatibility", build_gradle)
        self.assertIn("NeoForge", build_gradle)
        self.assertIn("Forge", build_gradle)
        self.assertNotIn('include("quilt")', active_settings)
        self.assertNotIn('include("neoforge")', active_settings)
        self.assertNotIn('include("forge")', active_settings)


if __name__ == "__main__":
    unittest.main()
