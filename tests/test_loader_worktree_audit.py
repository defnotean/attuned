from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "loader_worktree_audit.py"


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


class LoaderWorktreeAuditTest(unittest.TestCase):
    def run_tool(self, root: Path, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(TOOL), "--root", str(root), *args],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

    def test_json_audit_reports_candidate_and_missing_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            ready = root / ".worktrees" / "neoforge-minecraft-1.21.1"
            write(ready / "src/main/resources/META-INF/neoforge.mods.toml", "modLoader='javafml'")
            write(ready / "build.gradle", "id 'net.neoforged.moddev'")
            write(
                ready / "src/main/java/dev/attuned/attunement/AttunedAttachments.java",
                """
                class AttunedAttachments {
                    void sync() { syncToClient(player, new AttunementStatePayload(state)); }
                    void cloneState() { player.getPersistentData(); }
                }
                """,
            )
            write(
                ready / "src/client/java/dev/attuned/client/AttunedClient.java",
                "class AttunedClient { void init() { AttunementStateClient.init(); } }",
            )
            write(
                ready / "src/client/java/dev/attuned/client/AttunementReadout.java",
                "class AttunementReadout { public static void invalidate(Player player) {} }",
            )
            write(
                ready / "src/client/java/dev/attuned/client/HudHooks.java",
                "class HudHooks { void render() { RenderGuiEvent.Post event; } }",
            )
            write(ready / "tests/test_neoforge_scaffold_contract.py", "def test_owner_state_sync_invalidates_hud_cache(): pass")

            partial = root / ".worktrees" / "quilt-minecraft-1.20.6"
            write(partial / "src/main/resources/quilt.mod.json", '{"schema_version": 1, "quilt_loader": {}}')
            write(partial / "build.gradle", "plugins { id 'org.quiltmc.loom' }")

            result = self.run_tool(root, "list", "--format", "json")

            self.assertEqual(0, result.returncode, result.stderr)
            reports = {report["worktree"]: report for report in json.loads(result.stdout)}
            self.assertEqual("candidate", reports["neoforge-minecraft-1.21.1"]["status"])
            self.assertEqual([], reports["neoforge-minecraft-1.21.1"]["missing"])
            self.assertEqual("partial", reports["quilt-minecraft-1.20.6"]["status"])
            self.assertIn("evidence.hud_bridge", reports["quilt-minecraft-1.20.6"]["missing"])

    def test_validate_fails_partial_loader_worktree(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            partial = root / ".worktrees" / "neoforge-minecraft-26.1.2"
            write(partial / "src/main/resources/META-INF/neoforge.mods.toml", "modLoader='javafml'")

            result = self.run_tool(root, "validate", "--loader", "neoforge")

            self.assertEqual(1, result.returncode)
            self.assertIn("neoforge-minecraft-26.1.2", result.stderr)
            self.assertIn("evidence.owner_state_sync", result.stderr)

    def test_validate_ignores_known_blocked_profile_worktree(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            partial = root / ".worktrees" / "neoforge-minecraft-1.20.1"
            write(partial / "src/main/resources/META-INF/neoforge.mods.toml", "modLoader='javafml'")
            write(
                root / "config" / "loader-support-profiles.json",
                json.dumps({
                    "active_loader": "fabric-26.2",
                    "profiles": [
                        {
                            "id": "neoforge-1.20.1",
                            "loader": "neoforge",
                            "minecraft_version": "1.20.1",
                            "java_version": "17",
                            "status": "blocked",
                            "artifact": "Blocked legacy coordinate target.",
                            "branch": "port/neoforge-1.20.1-legacy",
                            "metadata": {"mod_file": "src/main/resources/META-INF/neoforge.mods.toml"},
                            "verification": ["Choose legacy coordinate strategy"],
                            "notes": ["Known blocked target."]
                        }
                    ]
                }),
            )

            list_result = self.run_tool(root, "list", "--format", "json")
            validate_result = self.run_tool(root, "validate", "--loader", "neoforge")

            self.assertEqual(0, list_result.returncode, list_result.stderr)
            reports = {report["worktree"]: report for report in json.loads(list_result.stdout)}
            self.assertEqual("blocked", reports["neoforge-minecraft-1.20.1"]["status"])
            self.assertEqual("blocked", reports["neoforge-minecraft-1.20.1"]["profile_status"])
            self.assertEqual(0, validate_result.returncode, validate_result.stderr)


if __name__ == "__main__":
    unittest.main()
