from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any, Iterable

ROOT = Path(__file__).resolve().parents[1]
PROFILE_RELATIVE_FILE = Path("config/minecraft-version-profiles.json")
GRADLE_PROPERTIES_RELATIVE_FILE = Path("gradle.properties")
VERSION_KEYS = (
    "minecraft_version",
    "loader_version",
    "loom_version",
    "fabric_api_version",
    "java_version",
)
REQUIRED_PROFILE_FIELDS = VERSION_KEYS + (
    "fabric_loader_range",
    "status",
    "notes",
)
GITHUB_OUTPUT_FIELDS = (
    "minecraft_profile",
    "minecraft_version",
    "loader_version",
    "loom_version",
    "fabric_api_version",
    "java_version",
    "fabric_loader_range",
    "status",
)


class ProfileError(ValueError):
    """Raised when a requested version profile cannot be loaded or applied."""


def profile_file(root: Path = ROOT) -> Path:
    return root / PROFILE_RELATIVE_FILE


def gradle_properties_file(root: Path = ROOT) -> Path:
    return root / GRADLE_PROPERTIES_RELATIVE_FILE


def load_profiles(root: Path = ROOT) -> dict[str, Any]:
    path = profile_file(root)
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise ProfileError(f"{PROFILE_RELATIVE_FILE}: root must be a JSON object")
    return data


def gradle_properties(root: Path = ROOT) -> dict[str, str]:
    props: dict[str, str] = {}
    path = gradle_properties_file(root)
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        props[key.strip()] = value.strip()
    return props


def _profile_map(data: dict[str, Any]) -> dict[str, dict[str, Any]]:
    profiles = data.get("profiles")
    if not isinstance(profiles, dict):
        raise ProfileError(f"{PROFILE_RELATIVE_FILE}: missing object field 'profiles'")
    result: dict[str, dict[str, Any]] = {}
    for profile_id, profile in profiles.items():
        if isinstance(profile_id, str) and isinstance(profile, dict):
            result[profile_id] = profile
    return result


def active_profile(root: Path = ROOT) -> tuple[str, dict[str, Any]]:
    data = load_profiles(root)
    active_id = data.get("active_profile")
    if not isinstance(active_id, str) or not active_id:
        raise ProfileError(f"{PROFILE_RELATIVE_FILE}: missing non-empty 'active_profile'")
    profiles = _profile_map(data)
    if active_id not in profiles:
        raise ProfileError(f"{PROFILE_RELATIVE_FILE}: active profile '{active_id}' is not defined")
    return active_id, profiles[active_id]


def profile_by_id(root: Path, profile_id: str) -> dict[str, Any]:
    profiles = _profile_map(load_profiles(root))
    try:
        return profiles[profile_id]
    except KeyError as exc:
        known = ", ".join(sorted(profiles)) or "none"
        raise ProfileError(f"Unknown Minecraft version profile '{profile_id}'. Known profiles: {known}") from exc


def validate_profiles(root: Path = ROOT) -> list[str]:
    problems: list[str] = []
    try:
        data = load_profiles(root)
    except (OSError, json.JSONDecodeError, ProfileError) as exc:
        return [f"{PROFILE_RELATIVE_FILE}: {exc}"]

    active_id = data.get("active_profile")
    if not isinstance(active_id, str) or not active_id:
        problems.append(f"{PROFILE_RELATIVE_FILE}: missing non-empty active_profile")
    try:
        profiles = _profile_map(data)
    except ProfileError as exc:
        return [str(exc)]
    if not profiles:
        problems.append(f"{PROFILE_RELATIVE_FILE}: profiles must contain at least one profile")
    if isinstance(active_id, str) and active_id and active_id not in profiles:
        problems.append(f"{PROFILE_RELATIVE_FILE}: active_profile '{active_id}' is not defined")

    for profile_id, profile in sorted(profiles.items()):
        for field in REQUIRED_PROFILE_FIELDS:
            if field not in profile:
                problems.append(f"{PROFILE_RELATIVE_FILE}: profile {profile_id} missing {field}")
        minecraft_version = str(profile.get("minecraft_version", ""))
        java_version = str(profile.get("java_version", ""))
        fabric_api_version = str(profile.get("fabric_api_version", ""))
        notes = profile.get("notes")
        if profile_id != minecraft_version:
            problems.append(
                f"{PROFILE_RELATIVE_FILE}: profile key {profile_id} must equal minecraft_version {minecraft_version!r}"
            )
        if not re.fullmatch(r"\d+(?:\.\d+){0,3}(?:[-+][A-Za-z0-9_.-]+)?", minecraft_version):
            problems.append(f"{PROFILE_RELATIVE_FILE}: profile {profile_id} has invalid minecraft_version")
        if not re.fullmatch(r"\d+", java_version):
            problems.append(f"{PROFILE_RELATIVE_FILE}: profile {profile_id} java_version must be an integer string")
        if minecraft_version and f"+{minecraft_version}" not in fabric_api_version:
            problems.append(
                f"{PROFILE_RELATIVE_FILE}: profile {profile_id} fabric_api_version should include '+{minecraft_version}'"
            )
        if not isinstance(notes, list) or not all(isinstance(note, str) and note.strip() for note in notes):
            problems.append(f"{PROFILE_RELATIVE_FILE}: profile {profile_id} notes must be a non-empty string list")
    return problems


def validate_repository(root: Path = ROOT) -> list[str]:
    problems = validate_profiles(root)
    if problems:
        return problems
    try:
        active_id, active = active_profile(root)
        props = gradle_properties(root)
    except (OSError, ProfileError) as exc:
        return [str(exc)]

    for key in VERSION_KEYS:
        expected = str(active.get(key, ""))
        actual = props.get(key)
        if actual != expected:
            problems.append(
                f"{GRADLE_PROPERTIES_RELATIVE_FILE}: {key}={actual!r} does not match active profile "
                f"{active_id} ({expected!r})"
            )
    return problems


def _line_key(line: str) -> str | None:
    stripped = line.strip()
    if not stripped or stripped.startswith("#") or "=" not in stripped:
        return None
    return stripped.split("=", 1)[0].strip()


def _insert_missing_key(lines: list[str], key: str, value: str) -> None:
    new_line = f"{key}={value}"
    if key == "java_version":
        for index, line in enumerate(lines):
            if _line_key(line) == "loom_version":
                lines.insert(index + 1, new_line)
                return
    if key == "fabric_api_version":
        for index, line in enumerate(lines):
            if line.strip() == "# Dependencies":
                lines.insert(index + 1, new_line)
                return
    for index, line in enumerate(lines):
        if line.strip() == "# Fabric Properties":
            lines.insert(index + 1, new_line)
            return
    lines.append(new_line)


def updated_gradle_properties_text(text: str, profile: dict[str, Any]) -> tuple[str, dict[str, dict[str, str | None]]]:
    lines = text.splitlines()
    seen: set[str] = set()
    updates: dict[str, dict[str, str | None]] = {}
    rewritten: list[str] = []
    profile_values = {key: str(profile[key]) for key in VERSION_KEYS}

    for line in lines:
        key = _line_key(line)
        if key in profile_values:
            seen.add(key)
            old_value = line.split("=", 1)[1].strip()
            new_value = profile_values[key]
            if old_value != new_value:
                updates[key] = {"old": old_value, "new": new_value}
            rewritten.append(f"{key}={new_value}")
        else:
            rewritten.append(line)

    for key, value in profile_values.items():
        if key not in seen:
            updates[key] = {"old": None, "new": value}
            _insert_missing_key(rewritten, key, value)

    return "\n".join(rewritten).rstrip() + "\n", updates


def apply_profile(root: Path, profile_id: str, *, dry_run: bool = False) -> dict[str, Any]:
    profile = profile_by_id(root, profile_id)
    path = gradle_properties_file(root)
    original = path.read_text(encoding="utf-8")
    updated, updates = updated_gradle_properties_text(original, profile)
    changed = updated != original
    if changed and not dry_run:
        path.write_text(updated, encoding="utf-8")
    return {
        "profile": profile_id,
        "changed": changed,
        "dry_run": dry_run,
        "updates": updates,
        "gradle_properties": str(path),
    }


def render_checklist(root: Path, profile_id: str, output: Path) -> Path:
    profile = profile_by_id(root, profile_id)
    output = output if output.is_absolute() else root / output
    output.parent.mkdir(parents=True, exist_ok=True)
    minecraft_version = str(profile["minecraft_version"])
    java_version = str(profile["java_version"])
    fabric_api = str(profile["fabric_api_version"])
    loader = str(profile["loader_version"])
    loom = str(profile["loom_version"])
    branch = f"maintenance/minecraft-{minecraft_version}"
    text = f"""# Attuned Minecraft {minecraft_version} Port Checklist

Target profile: `{profile_id}`
Branch: `{branch}`

## Version Profile

- Minecraft: `{minecraft_version}`
- Java: `{java_version}`
- Fabric Loader: `{loader}`
- Fabric API: `{fabric_api}`
- Fabric Loom: `{loom}`
- Loader range for metadata/docs: `{profile.get('fabric_loader_range', '')}`
- Status: `{profile.get('status', '')}`

## 1. Branch and Mechanical Retarget

- [ ] Start from green `latest`: `git checkout latest && git pull --ff-only origin latest`.
- [ ] Create the port branch: `git checkout -b {branch}`.
- [ ] Confirm or add the profile in `config/minecraft-version-profiles.json`.
- [ ] Dry run profile application: `python tools/minecraft_version_profile.py apply {profile_id} --dry-run`.
- [ ] Apply the profile: `python tools/minecraft_version_profile.py apply {profile_id}`.
- [ ] Run `python tools/minecraft_version_profile.py validate`.

## 2. Dependency and Lockfile Pass

- [ ] Check Fabric's develop page for the exact Minecraft, Fabric Loader, Fabric API, and Loom combination.
- [ ] Refresh dependency locks only after the profile is applied: `./gradlew dependencies --write-locks` if Gradle reports lock drift.
- [ ] Review dependency locks for accidental unrelated upgrades.
- [ ] If Fabric API is unavailable for this target, stop and mark the profile `blocked` instead of forcing a bad build.

## 3. Source and Mapping Compatibility Pass

- [ ] Search for direct Minecraft API hotspots: `git grep -n -E "net.minecraft|BuiltInRegistries|DataComponents|ServerPlayer|ResourceKey" src`.
- [ ] Compile first: `./gradlew test --no-daemon`.
- [ ] Fix mapping/package/signature errors one group at a time.
- [ ] For older Minecraft ports, prefer a maintenance branch if source compatibility diverges heavily.
- [ ] For newer Minecraft ports, keep compatibility shims small and tested; avoid speculative multi-version abstractions until two real targets need them.

## 4. Data, Resource, and Pack Format Pass

- [ ] Check `fabric.mod.json` dependency ranges and loader metadata.
- [ ] Check datapack/resource JSON schemas for the target Minecraft version.
- [ ] Check structure/template DataVersion compatibility for generated structures.
- [ ] Run `python tools/verify_repository.py` after every data/resource migration batch.

## 5. Test and QA Gates

- [ ] `python tools/verify_repository.py`
- [ ] `python -m unittest discover -s tests`
- [ ] `uv run --with pytest --with pillow -m pytest tests/ -q`
- [ ] `./gradlew test --no-daemon`
- [ ] `./gradlew build --no-daemon`
- [ ] Dedicated server smoke: `python tools/minecraft_runtime_smoke.py --accept-eula --timeout 240 --stop-timeout 60`
- [ ] client smoke only when needed: `./gradlew runClient`.

## 6. Platform Metadata and Release Prep

- [ ] Update README/current docs only after the build and smoke tests pass.
- [ ] Update Modrinth game version metadata from `minecraft_version`.
- [ ] Update CurseForge `gameVersionNames` from `minecraft_version` and `java_version`.
- [ ] Keep Modrinth and CurseForge release notes neutral and current.
- [ ] Dry-run CurseForge metadata: `python tools/publish_curseforge.py --dry-run`.

## 7. Rollback / Backport Policy

- [ ] If the target cannot compile because dependencies are not available, revert only the profile application commit and keep the profile marked `blocked` with notes.
- [ ] Older-version support should be bugfix/backport-first, not a new-feature branch, unless the same feature is fully tested on all supported targets.
- [ ] Do not merge a port branch to `latest` until repository verification, Python tests, Gradle build, and smoke QA are green.
- [ ] After the work is complete and green, merge to `latest`, push `latest`, and watch GitHub CI to success.
"""
    output.write_text(text, encoding="utf-8")
    return output


def write_github_output(path: Path, profile_id: str, profile: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    values = {"minecraft_profile": profile_id, **{key: str(profile[key]) for key in VERSION_KEYS}}
    values["fabric_loader_range"] = str(profile.get("fabric_loader_range", ""))
    values["status"] = str(profile.get("status", ""))
    with path.open("a", encoding="utf-8") as handle:
        for key in GITHUB_OUTPUT_FIELDS:
            if key in values:
                handle.write(f"{key}={values[key]}\n")


def _json_print(data: Any) -> None:
    print(json.dumps(data, indent=2, sort_keys=True))


def parse_args(argv: Iterable[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Manage Attuned Minecraft version profiles.")
    parser.add_argument("--root", type=Path, default=ROOT, help="Repository root. Defaults to this script's parent repo.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("list", help="List known Minecraft version profiles.")

    current = subparsers.add_parser("current", help="Print the active Minecraft version profile.")
    current.add_argument("--github-output", type=Path, help="Append profile fields to a GitHub Actions output file.")

    subparsers.add_parser("validate", help="Validate profile schema and active Gradle alignment.")

    apply = subparsers.add_parser("apply", help="Apply a profile to gradle.properties.")
    apply.add_argument("profile", help="Profile id, usually the Minecraft version.")
    apply.add_argument("--dry-run", action="store_true", help="Report changes without writing gradle.properties.")

    checklist = subparsers.add_parser("render-checklist", help="Render a port checklist for a profile.")
    checklist.add_argument("profile", help="Profile id, usually the Minecraft version.")
    checklist.add_argument("--output", required=True, type=Path, help="Checklist markdown path.")
    return parser.parse_args(list(argv))


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    root = args.root.resolve()
    try:
        if args.command == "list":
            data = load_profiles(root)
            profiles = _profile_map(data)
            _json_print({"active_profile": data.get("active_profile"), "profiles": sorted(profiles)})
            return 0
        if args.command == "current":
            profile_id, profile = active_profile(root)
            if args.github_output:
                write_github_output(args.github_output, profile_id, profile)
            _json_print({"profile": profile_id, **profile})
            return 0
        if args.command == "validate":
            problems = validate_repository(root)
            if problems:
                for problem in problems:
                    print(problem, file=sys.stderr)
                return 1
            print("Minecraft version profile validation passed.")
            return 0
        if args.command == "apply":
            _json_print(apply_profile(root, args.profile, dry_run=args.dry_run))
            return 0
        if args.command == "render-checklist":
            path = render_checklist(root, args.profile, args.output)
            print(path)
            return 0
    except (OSError, json.JSONDecodeError, ProfileError) as exc:
        print(f"minecraft_version_profile: {exc}", file=sys.stderr)
        return 1
    raise AssertionError(f"unhandled command {args.command}")


if __name__ == "__main__":
    raise SystemExit(main())
