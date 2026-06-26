from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
PROFILE_RELATIVE_FILE = Path("config/loader-support-profiles.json")
VALID_LOADERS = {"fabric", "quilt-compat", "quilt", "neoforge", "forge"}
VALID_STATUSES = {"current", "candidate", "planned", "maintenance", "blocked", "dropped"}
REQUIRED_FIELDS = {
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
LOADER_LABELS = {
    "fabric": "Fabric",
    "quilt-compat": "Quilt compatibility",
    "quilt": "Quilt",
    "neoforge": "NeoForge",
    "forge": "Forge",
}


class LoaderProfileError(ValueError):
    """Raised when loader support profile data is missing or invalid."""


def profile_file(root: Path = ROOT) -> Path:
    return root / PROFILE_RELATIVE_FILE


def load_profiles(root: Path = ROOT) -> dict[str, Any]:
    path = profile_file(root)
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise LoaderProfileError(f"{PROFILE_RELATIVE_FILE}: root must be a JSON object")
    return data


def profile_list(data: dict[str, Any]) -> list[dict[str, Any]]:
    profiles = data.get("profiles")
    if not isinstance(profiles, list):
        raise LoaderProfileError(f"{PROFILE_RELATIVE_FILE}: profiles must be a JSON list")
    return [profile for profile in profiles if isinstance(profile, dict)]


def _validate_profile(profile: dict[str, Any], profile_ids: set[str]) -> list[str]:
    problems: list[str] = []
    missing = sorted(REQUIRED_FIELDS.difference(profile))
    if missing:
        return [f"{PROFILE_RELATIVE_FILE}: profile missing fields {', '.join(missing)}"]

    profile_id = str(profile["id"])
    if profile_id in profile_ids:
        problems.append(f"{PROFILE_RELATIVE_FILE}: duplicate profile id {profile_id}")
    profile_ids.add(profile_id)

    loader = str(profile["loader"])
    status = str(profile["status"])
    minecraft_version = str(profile["minecraft_version"])
    java_version = str(profile["java_version"])
    artifact = str(profile["artifact"])

    if loader not in VALID_LOADERS:
        problems.append(f"{PROFILE_RELATIVE_FILE}: {profile_id} has invalid loader {loader!r}")
    if status not in VALID_STATUSES:
        problems.append(f"{PROFILE_RELATIVE_FILE}: {profile_id} has invalid status {status!r}")
    if loader != "fabric" and status == "current":
        problems.append(f"{PROFILE_RELATIVE_FILE}: {profile_id} cannot be current before an artifact ships")
    if loader != "fabric" and "shipping" in artifact.lower():
        problems.append(f"{PROFILE_RELATIVE_FILE}: {profile_id} must not call an unbuilt artifact shipping")
    if not re.fullmatch(r"\d+(?:\.\d+){0,3}(?:[-+][A-Za-z0-9_.-]+)?", minecraft_version):
        problems.append(f"{PROFILE_RELATIVE_FILE}: {profile_id} has invalid minecraft_version")
    if not re.fullmatch(r"\d+", java_version):
        problems.append(f"{PROFILE_RELATIVE_FILE}: {profile_id} java_version must be an integer string")
    if not str(profile["branch"]).strip():
        problems.append(f"{PROFILE_RELATIVE_FILE}: {profile_id} branch must be non-empty")
    if not isinstance(profile["metadata"], dict) or not profile["metadata"]:
        problems.append(f"{PROFILE_RELATIVE_FILE}: {profile_id} metadata must be a non-empty object")
    if not isinstance(profile["verification"], list) or not all(
        isinstance(item, str) and item.strip() for item in profile["verification"]
    ):
        problems.append(f"{PROFILE_RELATIVE_FILE}: {profile_id} verification must be a non-empty string list")
    if not isinstance(profile["notes"], list) or not all(isinstance(item, str) and item.strip() for item in profile["notes"]):
        problems.append(f"{PROFILE_RELATIVE_FILE}: {profile_id} notes must be a non-empty string list")

    return problems


def validate_profiles(root: Path = ROOT) -> list[str]:
    problems: list[str] = []
    try:
        data = load_profiles(root)
    except (OSError, json.JSONDecodeError, LoaderProfileError) as exc:
        return [f"{PROFILE_RELATIVE_FILE}: {exc}"]

    active_loader = data.get("active_loader")
    if not isinstance(active_loader, str) or not active_loader:
        problems.append(f"{PROFILE_RELATIVE_FILE}: active_loader must be a non-empty string")

    try:
        profiles = profile_list(data)
    except LoaderProfileError as exc:
        return [str(exc)]

    if not profiles:
        problems.append(f"{PROFILE_RELATIVE_FILE}: profiles must contain at least one profile")

    profile_ids: set[str] = set()
    loaders: set[str] = set()
    for profile in profiles:
        loaders.add(str(profile.get("loader", "")))
        problems.extend(_validate_profile(profile, profile_ids))

    if isinstance(active_loader, str) and active_loader and active_loader not in profile_ids:
        problems.append(f"{PROFILE_RELATIVE_FILE}: active_loader {active_loader!r} is not defined")

    required_loaders = {"fabric", "quilt-compat", "quilt", "neoforge", "forge"}
    missing_loaders = sorted(required_loaders.difference(loaders))
    if missing_loaders:
        problems.append(f"{PROFILE_RELATIVE_FILE}: missing loader tracks {', '.join(missing_loaders)}")

    return problems


def validate_repository(root: Path = ROOT) -> list[str]:
    problems = validate_profiles(root)
    tool_path = root / "tools" / "loader_support_profile.py"
    if not tool_path.is_file():
        problems.append("tools/loader_support_profile.py: missing loader profile tool")
    support_doc = root / "docs" / "loader-support.md"
    if not support_doc.is_file():
        problems.append("docs/loader-support.md: missing loader support guide")
    plan_doc = root / "docs" / "superpowers" / "plans" / "2026-06-25-loader-port-roadmap.md"
    if not plan_doc.is_file():
        problems.append("docs/superpowers/plans/2026-06-25-loader-port-roadmap.md: missing loader roadmap plan")
    return problems


def profile_by_id(root: Path, profile_id: str) -> dict[str, Any]:
    for profile in profile_list(load_profiles(root)):
        if profile.get("id") == profile_id:
            return profile
    known = ", ".join(profile["id"] for profile in profile_list(load_profiles(root)))
    raise LoaderProfileError(f"Unknown loader profile {profile_id!r}. Known profiles: {known}")


def loader_label(loader: str) -> str:
    return LOADER_LABELS.get(loader, loader.replace("-", " ").title())


def render_checklist(root: Path, profile_id: str, output: Path) -> Path:
    profile = profile_by_id(root, profile_id)
    output = output if output.is_absolute() else root / output
    output.parent.mkdir(parents=True, exist_ok=True)
    loader_name = loader_label(str(profile["loader"]))
    metadata = "\n".join(f"- `{key}`: `{value}`" for key, value in sorted(profile["metadata"].items()))
    verification = "\n".join(f"- [ ] `{item}`" for item in profile["verification"])
    notes = "\n".join(f"- {note}" for note in profile["notes"])
    text = f"""# Loader Port Checklist: {profile_id}

Loader: `{profile["loader"]}` ({loader_name})
Minecraft: `{profile["minecraft_version"]}`
Java: `{profile["java_version"]}`
Status: `{profile["status"]}`
Branch: `{profile["branch"]}`
Artifact: {profile["artifact"]}

## Metadata

{metadata}

## Required Verification

{verification}

## Notes

{notes}

## Release Rule

Do not publish this loader profile until every verification item above has fresh
evidence and the platform upload metadata names {loader_name} explicitly.
"""
    output.write_text(text, encoding="utf-8")
    return output


def parse_args(argv: Iterable[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Manage Attuned loader support profiles.")
    parser.add_argument("--root", type=Path, default=ROOT, help="Repository root. Defaults to this script's parent repo.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("list", help="List known loader support profiles.")
    subparsers.add_parser("validate", help="Validate loader profile schema and required docs.")

    checklist = subparsers.add_parser("render-checklist", help="Render a loader port checklist.")
    checklist.add_argument("profile", help="Loader profile id.")
    checklist.add_argument("--output", required=True, type=Path, help="Checklist markdown path.")
    return parser.parse_args(list(argv))


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    root = args.root.resolve()
    try:
        if args.command == "list":
            data = load_profiles(root)
            print(json.dumps({
                "active_loader": data.get("active_loader"),
                "profiles": [profile["id"] for profile in profile_list(data)],
            }, indent=2))
            return 0
        if args.command == "validate":
            problems = validate_repository(root)
            if problems:
                for problem in problems:
                    print(problem, file=sys.stderr)
                return 1
            print("Loader support profile validation passed.")
            return 0
        if args.command == "render-checklist":
            path = render_checklist(root, args.profile, args.output)
            print(path)
            return 0
    except (OSError, json.JSONDecodeError, LoaderProfileError) as exc:
        print(f"loader_support_profile: {exc}", file=sys.stderr)
        return 1
    raise AssertionError(f"unhandled command {args.command}")


if __name__ == "__main__":
    raise SystemExit(main())
