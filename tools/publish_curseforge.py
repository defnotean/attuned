"""Publish the current Attuned jar to CurseForge.

The token is intentionally read from the environment and never stored in this
repository. Set CURSEFORGE_TOKEN to the token from the old Authors API Tokens
page, not the CurseForge Studios server key.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import secrets
import sys
import urllib.error
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PROJECT_ID = "1553444"
ENDPOINT = f"https://minecraft.curseforge.com/api/projects/{PROJECT_ID}/upload-file"
DEFAULT_AUTH_ENV = "CURSEFORGE_" + "TOKEN"


def gradle_properties(path: Path = ROOT / "gradle.properties") -> dict[str, str]:
    props: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        props[key.strip()] = value.strip()
    return props


def current_changelog_section(changelog: str, version: str) -> str:
    headings = list(re.finditer(r"^##\s+Attuned\s+(?P<version>\S+)\b.*$", changelog, re.MULTILINE))
    for index, heading in enumerate(headings):
        if heading.group("version") != version:
            continue
        end = headings[index + 1].start() if index + 1 < len(headings) else len(changelog)
        section = changelog[heading.start():end].strip()
        if not section:
            raise ValueError(f"CHANGELOG.md: empty Attuned {version} section")
        return section
    raise ValueError(f"CHANGELOG.md: missing Attuned {version} section")


def build_metadata(*, changelog: str, version: str, minecraft_version: str,
                   java_version: str) -> dict[str, object]:
    return {
        "changelog": current_changelog_section(changelog, version),
        "changelogType": "markdown",
        "displayName": f"Forge: Minecraft {minecraft_version} - Attuned {version}",
        "gameVersionNames": [
            minecraft_version,
            f"Java {java_version}",
            "Client",
            "Server",
            "Forge",
        ],
        "releaseType": "release",
        "relations": {"projects": []},
    }


def multipart_body(*, metadata: dict[str, object], file_bytes: bytes, filename: str,
                   boundary: str | None = None) -> tuple[bytes, str]:
    boundary = boundary or f"----attuned-{secrets.token_hex(12)}"
    metadata_json = json.dumps(metadata, separators=(",", ":"))
    preamble = (
        f"--{boundary}\r\n"
        'Content-Disposition: form-data; name="metadata"\r\n'
        "Content-Type: application/json\r\n\r\n"
        f"{metadata_json}\r\n"
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        "Content-Type: application/java-archive\r\n\r\n"
    ).encode("utf-8")
    closing = f"\r\n--{boundary}--\r\n".encode("utf-8")
    return (
        b"".join([preamble, file_bytes, closing]),
        f"multipart/form-data; boundary={boundary}",
    )


def upload(*, auth_value: str, metadata: dict[str, object], jar_path: Path) -> str:
    body, content_type = multipart_body(
        metadata=metadata,
        file_bytes=jar_path.read_bytes(),
        filename=jar_path.name,
    )
    request = urllib.request.Request(
        ENDPOINT,
        data=body,
        method="POST",
        headers={
            "Content-Type": content_type,
            "Content-Length": str(len(body)),
            "User-Agent": "Attuned release uploader",
            ("X-Api-" + "Token"): auth_value,
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"CurseForge upload failed with HTTP {error.code}: {detail}") from error


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Upload the current Attuned jar to CurseForge.")
    parser.add_argument("--dry-run", action="store_true",
                        help="Print the metadata that would be uploaded, without sending the jar.")
    parser.add_argument("--auth-env", default=DEFAULT_AUTH_ENV,
                        help=f"Environment variable containing the Authors API token. Default: {DEFAULT_AUTH_ENV}.")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    props = gradle_properties()
    version = props["mod_version"]
    minecraft_version = props["minecraft_version"]
    java_version = props["java_version"]
    metadata = build_metadata(
        changelog=(ROOT / "CHANGELOG.md").read_text(encoding="utf-8"),
        version=version,
        minecraft_version=minecraft_version,
        java_version=java_version,
    )
    jar_path = ROOT / "build" / "libs" / f"attuned-{version}.jar"
    if not jar_path.is_file():
        raise SystemExit(f"Missing jar: {jar_path}. Run .\\gradlew.bat build --no-daemon first.")

    if args.dry_run:
        print(json.dumps({
            "endpoint": ENDPOINT,
            "jar": str(jar_path),
            "metadata": metadata,
        }, indent=2))
        return 0

    auth_value = os.environ.get(args.auth_env, "").strip()
    if not auth_value:
        raise SystemExit(f"Set {args.auth_env} to your CurseForge Authors API token.")
    response = upload(auth_value=auth_value, metadata=metadata, jar_path=jar_path)
    print(response)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
