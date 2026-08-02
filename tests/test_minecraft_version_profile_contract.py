from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "tools" / "minecraft_version_profile.py"

spec = importlib.util.spec_from_file_location("minecraft_version_profile", MODULE_PATH)
minecraft_version_profile = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(minecraft_version_profile)


CURRENT_PROFILE = {
    "minecraft_version": "26.2",
    "loader_version": "0.19.3",
    "loom_version": "1.17.11",
    "fabric_api_version": "0.152.1+26.2",
    "java_version": "25",
    "fabric_loader_range": ">=0.19.3",
    "status": "current",
    "notes": ["Current released target."],
}

LEGACY_PROFILE = {
    "minecraft_version": "1.21.1",
    "loader_version": "0.16.14",
    "loom_version": "1.11.8",
    "fabric_api_version": "0.116.5+1.21.1",
    "java_version": "21",
    "fabric_loader_range": ">=0.16.14",
    "status": "candidate",
    "notes": ["Example older maintenance target for tests."],
}

BRANCH_ACTIVE_PROFILES = {
    "latest": "26.2",
    "maintenance/minecraft-26.1.2": "26.1.2",
    "maintenance/minecraft-1.21.11": "1.21.11",
    "maintenance/minecraft-1.20.6": "1.20.6",
    "maintenance/minecraft-1.19.4": "1.19.4",
    "maintenance/minecraft-1.18.2": "1.18.2",
}


def current_branch_name() -> str:
    github_branch = os.environ.get("GITHUB_HEAD_REF") or os.environ.get("GITHUB_REF_NAME")
    if github_branch:
        return github_branch
    result = subprocess.run(
        ["git", "branch", "--show-current"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def write_profile_config(root: Path) -> None:
    config = root / "config"
    config.mkdir(parents=True)
    (config / "minecraft-version-profiles.json").write_text(
        json.dumps(
            {
                "active_profile": "26.2",
                "profiles": {
                    "26.2": CURRENT_PROFILE,
                    "26.1.2": {
                        **CURRENT_PROFILE,
                        "minecraft_version": "26.1.2",
                        "loader_version": "0.19.3",
                        "loom_version": "1.17.11",
                        "fabric_api_version": "0.152.1+26.1.2",
                        "fabric_loader_range": ">=0.19.3",
                        "status": "maintenance",
                    },
                    "1.21.1": LEGACY_PROFILE,
                },
            },
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )


def write_gradle_properties(root: Path) -> None:
    (root / "gradle.properties").write_text(
        "# Fabric Properties\n"
        "minecraft_version=26.2\n"
        "loader_version=0.19.3\n"
        "loom_version=1.17.11\n"
        "java_version=25\n"
        "\n"
        "# Mod Properties\n"
        "mod_version=1.4.0\n"
        "maven_group=dev.attuned\n"
        "\n"
        "# Dependencies\n"
        "fabric_api_version=0.152.1+26.2\n"
        "custom.keep=this-line\n",
        encoding="utf-8",
    )


class MinecraftVersionProfileContractTest(unittest.TestCase):
    def test_repository_active_profile_matches_current_gradle_properties(self) -> None:
        problems = minecraft_version_profile.validate_repository(ROOT)
        self.assertEqual([], problems)

        active_id, active = minecraft_version_profile.active_profile(ROOT)
        expected_active = BRANCH_ACTIVE_PROFILES.get(current_branch_name())
        if expected_active is not None:
            self.assertEqual(expected_active, active_id)
        for key in (
            "minecraft_version",
            "loader_version",
            "loom_version",
            "fabric_api_version",
            "java_version",
            "fabric_loader_range",
            "status",
            "notes",
        ):
            self.assertIn(key, active)
        self.assertIn(active_id, minecraft_version_profile.load_profiles(ROOT)["profiles"])

    def test_forge_repository_validation_uses_only_cross_loader_profile_keys(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            write_profile_config(root)
            (root / "gradle.properties").write_text(
                "minecraft_version=26.2\n"
                "forge_version=65.0.0\n"
                "java_version=25\n",
                encoding="utf-8",
            )

            self.assertEqual([], minecraft_version_profile.validate_repository(root))

    def test_fabric_repository_validation_still_reports_missing_fabric_coordinates(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            write_profile_config(root)
            (root / "gradle.properties").write_text(
                "minecraft_version=26.2\n"
                "java_version=25\n",
                encoding="utf-8",
            )

            problems = minecraft_version_profile.validate_repository(root)

            self.assertTrue(any("loader_version=None" in problem for problem in problems))
            self.assertTrue(any("loom_version=None" in problem for problem in problems))
            self.assertTrue(any("fabric_api_version=None" in problem for problem in problems))

    def test_repository_profiles_cover_latest_and_four_maintenance_targets(self) -> None:
        profiles = minecraft_version_profile.load_profiles(ROOT)["profiles"]

        self.assertIn(minecraft_version_profile.load_profiles(ROOT)["active_profile"], profiles)
        expected = {
            "26.2": "current",
            "26.1.2": "maintenance",
            "1.21.11": "maintenance",
            "1.20.6": "maintenance",
            "1.19.4": "maintenance",
            "1.18.2": "maintenance",
        }
        for profile_id, status in expected.items():
            self.assertIn(profile_id, profiles)
            self.assertEqual(status, profiles[profile_id]["status"])

    def test_apply_profile_dry_run_reports_changes_without_writing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            write_profile_config(root)
            write_gradle_properties(root)

            result = minecraft_version_profile.apply_profile(root, "1.21.1", dry_run=True)

            self.assertTrue(result["changed"])
            self.assertEqual("1.21.1", result["profile"])
            self.assertEqual("26.2", result["updates"]["minecraft_version"]["old"])
            self.assertEqual("1.21.1", result["updates"]["minecraft_version"]["new"])
            self.assertIn("custom.keep=this-line", (root / "gradle.properties").read_text(encoding="utf-8"))
            self.assertIn("minecraft_version=26.2", (root / "gradle.properties").read_text(encoding="utf-8"))

    def test_apply_profile_updates_only_version_keys_and_preserves_comments(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            write_profile_config(root)
            write_gradle_properties(root)

            result = minecraft_version_profile.apply_profile(root, "1.21.1", dry_run=False)
            text = (root / "gradle.properties").read_text(encoding="utf-8")

            self.assertTrue(result["changed"])
            self.assertIn("# Fabric Properties", text)
            self.assertIn("# Dependencies", text)
            self.assertIn("custom.keep=this-line", text)
            self.assertIn("minecraft_version=1.21.1", text)
            self.assertIn("loader_version=0.16.14", text)
            self.assertIn("loom_version=1.11.8", text)
            self.assertIn("fabric_api_version=0.116.5+1.21.1", text)
            self.assertIn("java_version=21", text)

    def test_render_checklist_includes_manual_porting_gates(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            write_profile_config(root)
            output = root / "docs" / "versioning" / "checklists" / "minecraft-1.21.1.md"

            result_path = minecraft_version_profile.render_checklist(root, "1.21.1", output)
            text = result_path.read_text(encoding="utf-8")

            self.assertIn("maintenance/minecraft-1.21.1", text)
            self.assertIn("Fabric API", text)
            self.assertIn("dependency locks", text)
            self.assertIn("server smoke", text)
            self.assertIn("client smoke", text)
            self.assertIn("Modrinth", text)
            self.assertIn("CurseForge", text)
            self.assertIn("rollback", text.lower())

    def test_current_command_can_write_github_outputs(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            write_profile_config(root)
            write_gradle_properties(root)
            output = root / "github-output.txt"

            stdout = io.StringIO()
            with contextlib.redirect_stdout(stdout):
                code = minecraft_version_profile.main(["--root", str(root), "current", "--github-output", str(output)])

            self.assertEqual(0, code)
            github_output = output.read_text(encoding="utf-8")
            self.assertIn("minecraft_version=26.2", github_output)
            self.assertIn("java_version=25", github_output)
            self.assertIn("build_java_version=25", github_output)

    def test_build_java_version_uses_minimum_loom_runtime(self) -> None:
        self.assertEqual("25", minecraft_version_profile.build_java_version(CURRENT_PROFILE))
        self.assertEqual("21", minecraft_version_profile.build_java_version(LEGACY_PROFILE))


if __name__ == "__main__":
    unittest.main()
