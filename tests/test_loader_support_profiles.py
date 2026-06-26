from __future__ import annotations

import json
import subprocess
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PROFILE_PATH = ROOT / "config" / "loader-support-profiles.json"
TOOL_PATH = ROOT / "tools" / "loader_support_profile.py"


class LoaderSupportProfilesTest(unittest.TestCase):
    def profile_data(self) -> dict[str, object]:
        return json.loads(PROFILE_PATH.read_text(encoding="utf-8"))

    def test_profiles_have_required_fields(self) -> None:
        data = self.profile_data()
        self.assertIn("active_loader", data)
        self.assertIn("profiles", data)
        self.assertIsInstance(data["profiles"], list)
        self.assertGreater(len(data["profiles"]), 0)

        required = {
            "id",
            "loader",
            "minecraft_version",
            "java_version",
            "status",
            "artifact",
            "branch",
            "metadata",
            "verification",
            "notes",
        }
        valid_loaders = {"fabric", "quilt-compat", "quilt", "neoforge", "forge"}
        valid_statuses = {"current", "candidate", "planned", "maintenance", "blocked", "dropped"}

        ids: set[str] = set()
        for profile in data["profiles"]:
            self.assertTrue(required.issubset(profile), profile)
            self.assertNotIn(profile["id"], ids)
            ids.add(profile["id"])
            self.assertIn(profile["loader"], valid_loaders)
            self.assertIn(profile["status"], valid_statuses)
            self.assertIsInstance(profile["metadata"], dict)
            self.assertIsInstance(profile["verification"], list)
            self.assertGreater(len(profile["verification"]), 0)
            self.assertIsInstance(profile["notes"], list)
            self.assertGreater(len(profile["notes"]), 0)

    def test_loader_matrix_covers_current_and_port_targets(self) -> None:
        profiles = self.profile_data()["profiles"]
        ids = {profile["id"] for profile in profiles}
        loaders = {profile["loader"] for profile in profiles}

        self.assertTrue({"fabric", "quilt-compat", "quilt", "neoforge", "forge"}.issubset(loaders))
        self.assertIn("fabric-26.2", ids)
        self.assertIn("fabric-1.20.6", ids)
        self.assertIn("quilt-compat-26.2", ids)
        self.assertIn("quilt-native-26.2", ids)
        self.assertIn("quilt-native-1.19.2", ids)
        self.assertIn("quilt-native-1.20.6", ids)
        self.assertIn("neoforge-26.1.2", ids)
        self.assertIn("neoforge-26.2", ids)
        self.assertIn("forge-1.20.1", ids)

    def test_non_fabric_profiles_do_not_claim_current_artifacts(self) -> None:
        for profile in self.profile_data()["profiles"]:
            if profile["loader"] == "fabric":
                continue
            self.assertNotEqual("current", profile["status"], profile)
            self.assertNotIn("shipping", profile["artifact"].lower(), profile)


class LoaderSupportProfileToolTest(unittest.TestCase):
    def run_tool(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(TOOL_PATH), *args],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

    def test_list_outputs_known_profiles(self) -> None:
        result = self.run_tool("list")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("fabric-26.2", result.stdout)
        self.assertIn("quilt-compat-26.2", result.stdout)
        self.assertIn("neoforge-26.1.2", result.stdout)
        self.assertIn("neoforge-26.2", result.stdout)
        self.assertIn("forge-1.20.1", result.stdout)

    def test_validate_passes_initial_registry(self) -> None:
        result = self.run_tool("validate")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Loader support profile validation passed.", result.stdout)

    def test_render_checklist_creates_loader_specific_markdown(self) -> None:
        output = ROOT / "build" / "test-loader-checklist.md"
        if output.exists():
            output.unlink()
        result = self.run_tool("render-checklist", "neoforge-26.1.2", "--output", str(output))
        self.assertEqual(0, result.returncode, result.stderr)

        text = output.read_text(encoding="utf-8")
        self.assertIn("# Loader Port Checklist: neoforge-26.1.2", text)
        self.assertIn("NeoForge", text)
        self.assertIn("Dedicated NeoForge branch build/server-smoke candidate", text)


if __name__ == "__main__":
    unittest.main()
