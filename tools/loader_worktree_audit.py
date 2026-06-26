from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
WORKTREES_DIR = Path(".worktrees")
PROFILE_FILE = Path("config/loader-support-profiles.json")

METADATA_FILES = {
    "fabric": Path("src/main/resources/fabric.mod.json"),
    "forge": Path("src/main/resources/META-INF/mods.toml"),
    "neoforge": Path("src/main/resources/META-INF/neoforge.mods.toml"),
    "quilt": Path("src/main/resources/quilt.mod.json"),
}

SCAN_PATTERNS = (
    "build.gradle",
    "gradle.properties",
    "settings.gradle",
    "src/main/resources/fabric.mod.json",
    "src/main/resources/quilt.mod.json",
    "src/main/resources/META-INF/*.toml",
    "src/main/java/dev/attuned/**/*.java",
    "src/main/java/net/**/*.java",
    "src/client/java/dev/attuned/**/*.java",
    "src/client/java/net/**/*.java",
    "src/test/java/dev/attuned/**/*.java",
    "tests/*.py",
    "docs/versioning/checklists/*.md",
)

LOADER_REQUIREMENTS = {
    "forge": {
        "metadata.forge",
        "evidence.hud_bridge",
        "evidence.readout_invalidation",
        "evidence.persistent_player_state",
        "evidence.branch_scaffold_tests",
    },
    "neoforge": {
        "metadata.neoforge",
        "evidence.hud_bridge",
        "evidence.owner_state_sync",
        "evidence.client_state_receiver",
        "evidence.readout_invalidation",
        "evidence.persistent_player_state",
        "evidence.branch_scaffold_tests",
    },
    "quilt": {
        "metadata.quilt",
        "evidence.quilt_loom",
        "evidence.quilt_entrypoints",
        "evidence.hud_bridge",
        "evidence.owner_state_sync",
        "evidence.client_state_receiver",
        "evidence.readout_invalidation",
        "evidence.branch_scaffold_tests",
    },
    "fabric": {
        "metadata.fabric",
        "evidence.fabric_loom",
    },
}


def _read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return ""


def _scan_text(worktree: Path) -> str:
    paths: set[Path] = set()
    for pattern in SCAN_PATTERNS:
        paths.update(path for path in worktree.glob(pattern) if path.is_file())
    parts: list[str] = []
    for path in sorted(paths):
        parts.append(str(path.relative_to(worktree)).replace("\\", "/"))
        parts.append(_read_text(path))
    return "\n".join(parts)


def _has_any(text: str, patterns: Iterable[str]) -> bool:
    return any(re.search(pattern, text, re.MULTILINE) for pattern in patterns)


def _loader_from_name(name: str) -> str:
    if name.startswith("forge-minecraft-"):
        return "forge"
    if name.startswith("neoforge-minecraft-"):
        return "neoforge"
    if name.startswith("quilt-minecraft-"):
        return "quilt"
    if name.startswith("minecraft-") or name.startswith("port-minecraft-") or name == "port-latest":
        return "fabric"
    return "unknown"


def _minecraft_version_from_name(name: str) -> str | None:
    match = re.search(r"(?:^|-)minecraft-(.+)$", name)
    return match.group(1) if match else None


def _get_nested_bool(report: dict[str, Any], dotted_key: str) -> bool:
    current: Any = report
    for part in dotted_key.split("."):
        if not isinstance(current, dict) or part not in current:
            return False
        current = current[part]
    return bool(current)


def _classify(loader: str, missing: list[str], present_count: int) -> str:
    if loader == "unknown":
        return "unknown"
    if not missing:
        return "candidate"
    if present_count == 0:
        return "placeholder"
    return "partial"


def _profile_status_priority(status: str) -> int:
    priorities = {
        "current": 4,
        "maintenance": 4,
        "candidate": 4,
        "blocked": 3,
        "dropped": 3,
        "planned": 2,
    }
    return priorities.get(status, 0)


def _profile_key(loader: str, minecraft_version: str) -> tuple[str, str]:
    if loader == "quilt-compat":
        return ("quilt", minecraft_version)
    return (loader, minecraft_version)


def _load_profile_statuses(root: Path) -> dict[tuple[str, str], str]:
    path = root / PROFILE_FILE
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    profiles = data.get("profiles")
    if not isinstance(profiles, list):
        return {}

    statuses: dict[tuple[str, str], str] = {}
    for profile in profiles:
        if not isinstance(profile, dict):
            continue
        loader = str(profile.get("loader", ""))
        minecraft_version = str(profile.get("minecraft_version", ""))
        status = str(profile.get("status", ""))
        if not loader or not minecraft_version or not status:
            continue
        key = _profile_key(loader, minecraft_version)
        current = statuses.get(key)
        if current is None or _profile_status_priority(status) > _profile_status_priority(current):
            statuses[key] = status
    return statuses


def _apply_profile_status(report: dict[str, Any], profile_statuses: dict[tuple[str, str], str]) -> None:
    minecraft_version = report.get("minecraft_version")
    if not minecraft_version:
        return
    profile_status = profile_statuses.get((str(report["loader"]), str(minecraft_version)))
    if not profile_status:
        return
    report["profile_status"] = profile_status
    if report["status"] in {"partial", "placeholder"} and profile_status in {"blocked", "planned", "dropped"}:
        report["status"] = profile_status


def audit_worktree(worktree: Path) -> dict[str, Any]:
    text = _scan_text(worktree)
    loader = _loader_from_name(worktree.name)
    metadata = {
        key: (worktree / relative_path).is_file()
        for key, relative_path in METADATA_FILES.items()
    }
    evidence = {
        "fabric_loom": _has_any(text, (r"net\.fabricmc\.fabric-loom", r"fabric-loom")),
        "quilt_loom": _has_any(text, (r"org\.quiltmc\.loom", r"quilt-loom")),
        "quilt_entrypoints": _has_any(text, (r"quilt_loader", r"ModInitializer", r"ClientModInitializer", r"AttunedQuilt")),
        "forge_gradle": _has_any(text, (r"net\.minecraftforge\.gradle", r"mods\.toml")),
        "neoforge_gradle": _has_any(text, (r"net\.neoforged\.moddev", r"neoforge\.mods\.toml")),
        "mod_constructor": _has_any(text, (r"@Mod\(", r"ModInitializer", r"ClientModInitializer")),
        "owner_state_sync": _has_any(
            text,
            (
                r"AttunementStatePayload",
                r"AttunedStatePayload",
                r"syncToClient\s*\(",
                r"applySyncedState\s*\(",
                r"applySync\s*\(",
                r"ServerPlayNetworking\.send",
                r"NetworkPackets\.send",
                r"PacketDistributor\.sendToPlayer",
            ),
        ),
        "client_state_receiver": _has_any(
            text,
            (
                r"AttunementStateClient\.init\s*\(",
                r"ClientPlayNetworking\.registerGlobalReceiver",
                r"registerClientbound",
                r"RegisterPayloadHandlersEvent",
            ),
        ),
        "hud_bridge": _has_any(
            text,
            (
                r"HudRenderCallback\.EVENT\.register",
                r"RenderGuiEvent",
                r"RenderGuiOverlayEvent",
                r"RenderGameOverlayEvent",
                r"AddGuiOverlayLayersEvent",
                r"RegisterGuiLayersEvent",
                r"HudElementRegistry",
            ),
        ),
        "readout_invalidation": _has_any(
            text,
            (
                r"AttunementReadout\.invalidate",
                r"public\s+static\s+void\s+invalidate\s*\(\s*Player",
            ),
        ),
        "persistent_player_state": _has_any(
            text,
            (
                r"persistentCodec",
                r"\.persistent\s*\(",
                r"getPersistentData\s*\(",
                r"copyFrom",
                r"PlayerEvent\.Clone",
                r"ServerPlayerEvents\.COPY_FROM",
            ),
        ),
        "branch_scaffold_tests": _has_any(
            text,
            (
                r"test_.*scaffold.*contract",
                r"Forge[A-Za-z0-9_]*ContractTest",
                r"Forge[A-Za-z0-9_]*PipelineContractTest",
                r"Forge[A-Za-z0-9_]*HudContractTest",
                r"Forge[A-Za-z0-9_]*State[A-Za-z0-9_]*ContractTest",
                r"ForgeAttachmentPersistenceContractTest",
                r"owner_state_sync",
                r"hud_cache",
                r"loader.*worktree.*audit",
            ),
        ),
        "checklist": _has_any(text, (r"Loader Port Checklist", r"Required Verification", r"hands-on.*HUD")),
    }
    report: dict[str, Any] = {
        "worktree": worktree.name,
        "path": str(worktree),
        "loader": loader,
        "minecraft_version": _minecraft_version_from_name(worktree.name),
        "metadata": metadata,
        "evidence": evidence,
    }
    requirements = sorted(LOADER_REQUIREMENTS.get(loader, set()))
    missing = [requirement for requirement in requirements if not _get_nested_bool(report, requirement)]
    present_count = len(requirements) - len(missing)
    report["requirements"] = requirements
    report["missing"] = missing
    report["status"] = _classify(loader, missing, present_count)
    return report


def collect_audit(root: Path = ROOT) -> list[dict[str, Any]]:
    worktrees_dir = root / WORKTREES_DIR
    if not worktrees_dir.is_dir():
        return []
    profile_statuses = _load_profile_statuses(root)
    reports = [
        audit_worktree(path)
        for path in sorted(worktrees_dir.iterdir(), key=lambda item: item.name)
        if path.is_dir()
    ]
    for report in reports:
        _apply_profile_status(report, profile_statuses)
    return reports


def render_table(reports: list[dict[str, Any]]) -> str:
    headers = ("worktree", "loader", "mc", "status", "missing")
    rows = [headers]
    for report in reports:
        rows.append((
            str(report["worktree"]),
            str(report["loader"]),
            str(report.get("minecraft_version") or ""),
            str(report["status"]),
            ", ".join(str(item) for item in report["missing"]) or "-",
        ))
    widths = [max(len(row[index]) for row in rows) for index in range(len(headers))]
    lines = []
    for index, row in enumerate(rows):
        line = "  ".join(value.ljust(widths[column]) for column, value in enumerate(row))
        lines.append(line)
        if index == 0:
            lines.append("  ".join("-" * width for width in widths))
    return "\n".join(lines)


def parse_args(argv: Iterable[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Audit Attuned loader worktrees for porting evidence.")
    parser.add_argument("--root", type=Path, default=ROOT, help="Repository root. Defaults to this script's parent repo.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    list_parser = subparsers.add_parser("list", help="List loader worktree evidence.")
    list_parser.add_argument("--format", choices=("table", "json"), default="table")

    validate_parser = subparsers.add_parser("validate", help="Fail when candidate worktrees are missing required evidence.")
    validate_parser.add_argument(
        "--loader",
        choices=("fabric", "quilt", "neoforge", "forge"),
        action="append",
        help="Limit validation to one or more loader families.",
    )
    return parser.parse_args(list(argv))


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    root = args.root.resolve()
    reports = collect_audit(root)

    if args.command == "list":
        if args.format == "json":
            print(json.dumps(reports, indent=2, sort_keys=True))
        else:
            print(render_table(reports))
        return 0

    if args.command == "validate":
        loaders = set(args.loader or ("fabric", "quilt", "neoforge", "forge"))
        failures = [
            report
            for report in reports
            if report["loader"] in loaders and report["status"] == "partial"
        ]
        for report in failures:
            print(
                f"{report['worktree']}: missing {', '.join(report['missing'])}",
                file=sys.stderr,
            )
        return 1 if failures else 0

    raise AssertionError(f"unhandled command {args.command}")


if __name__ == "__main__":
    raise SystemExit(main())
