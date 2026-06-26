from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
LOADER_CHOICES = ("fabric", "quilt-compat", "quilt", "neoforge", "forge")


def run_command(command: list[str]) -> int:
    completed = subprocess.run(command, cwd=ROOT, text=True)
    return completed.returncode


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run loader-specific Attuned runtime smoke commands.")
    parser.add_argument("loader", choices=LOADER_CHOICES)
    parser.add_argument("--server", action="store_true", help="Run the dedicated-server smoke for this loader.")
    parser.add_argument("--client", action="store_true", help="Run the client smoke for this loader.")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    if args.loader == "fabric" and args.server:
        return run_command([
            sys.executable,
            "tools/minecraft_runtime_smoke.py",
            "--accept-eula",
            "--timeout",
            "240",
            "--stop-timeout",
            "60",
        ])
    print(f"No automated smoke command is registered for {args.loader}. Use the loader checklist for manual evidence.")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
