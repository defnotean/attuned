#!/usr/bin/env python3
"""Clean-checkout sanity check.

Fails if any *committed* file references a path that is *untracked* (present in your
working tree but never committed). That is the "passes locally, fails on CI" class of
bug: a test or source reads a file that exists on your disk but a clean CI checkout
cannot see, so the build is green here and red there.

Untracked files that nothing committed references are ignored (ordinary local scratch).
Run automatically by the pre-push hook in .githooks/; bypass with `git push --no-verify`.
"""
import subprocess
import sys

# Tracked file globs worth scanning for path references.
SCAN_GLOBS = ["*.java", "*.py", "*.json", "*.gradle", "*.kts", "*.md", "*.mcmeta"]


def git(args):
    return subprocess.run(["git", *args], capture_output=True, text=True, check=False).stdout


def main():
    untracked = [p for p in git(["ls-files", "--others", "--exclude-standard"]).splitlines() if p]
    if not untracked:
        print("clean-checkout check: no untracked files.")
        return 0

    offenders = {}
    for path in untracked:
        refs = [r for r in git(["grep", "-l", "-F", path, "--", *SCAN_GLOBS]).splitlines() if r]
        if refs:
            offenders[path] = refs

    if offenders:
        print("clean-checkout check FAILED: committed files reference untracked paths.")
        print("A clean CI checkout has only committed files, so these references break there.")
        print("Fix: `git add` the file, or add it to .gitignore if it is local-only.\n")
        for path, refs in sorted(offenders.items()):
            print(f"  {path}")
            for ref in refs:
                print(f"      referenced by {ref}")
        return 1

    print(f"clean-checkout check: {len(untracked)} untracked file(s), "
          "none referenced by committed sources. OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
