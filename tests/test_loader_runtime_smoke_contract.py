from __future__ import annotations

import subprocess
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "loader_runtime_smoke.py"


class LoaderRuntimeSmokeContractTest(unittest.TestCase):
    def run_tool(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(TOOL), *args],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

    def test_help_lists_loader_choices(self) -> None:
        result = self.run_tool("--help")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("quilt-compat", result.stdout)
        self.assertIn("neoforge", result.stdout)
        self.assertIn("forge", result.stdout)

    def test_unimplemented_loader_returns_clear_status(self) -> None:
        result = self.run_tool("neoforge", "--server")

        self.assertEqual(2, result.returncode)
        self.assertIn("No automated smoke command is registered for neoforge", result.stdout)


if __name__ == "__main__":
    unittest.main()
