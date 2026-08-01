#!/usr/bin/env python3
"""Merge org.quiltmc* verification entries from a reference commit."""

from __future__ import annotations

import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "gradle/verification-metadata.xml"
REF_COMMIT = "a3cc114"


def main() -> None:
	ref_xml = subprocess.check_output(
		["git", "show", f"{REF_COMMIT}:gradle/verification-metadata.xml"],
		cwd=ROOT,
		text=True,
	)
	current = TARGET.read_text(encoding="utf-8")
	components = re.findall(r"(      <component group=\"org\.quiltmc[^>]*>.*?</component>\n)", ref_xml, re.S)
	merged = current
	added = 0
	for block in components:
		group_match = re.search(r'group="([^"]+)"', block)
		name_match = re.search(r'name="([^"]+)".*?version="([^"]+)"', block)
		if not group_match or not name_match:
			continue
		key = f'{group_match.group(1)}:{name_match.group(1)}:{name_match.group(2)}'
		if key in merged:
			continue
		merged = merged.replace("   </components>", block + "   </components>", 1)
		added += 1
	TARGET.write_text(merged, encoding="utf-8", newline="\n")
	print(f"Merged {added} org.quiltmc component blocks")


if __name__ == "__main__":
	main()
