# Contributing

## Development setup

- Install JDK 25 and use the checked-in Gradle wrapper (`./gradlew`).
- Install Python 3.10+ for local tooling.
- Install Python tooling dependencies when working on asset tooling:

```sh
python -m pip install -r requirements-dev.txt
```

## Local checks

Run the Java/Fabric build and tests with:

```sh
./gradlew build
```

Run repository-level validation with:

```sh
PYTHONDONTWRITEBYTECODE=1 python tools/verify_repository.py
```

Run the Python contract tests with:

```sh
PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s tests
```

Run the dedicated-server smoke check after reviewing and accepting the Minecraft EULA:

```sh
PYTHONDONTWRITEBYTECODE=1 python tools/minecraft_runtime_smoke.py --accept-eula
```

The smoke check starts the Fabric `runServer` task, waits for the server-ready signal, stops it, and fails on fatal server, mixin, or resource-load errors.

## Pre-push clean-checkout check

Enable the repo's git hooks so each push is checked for the "passes locally, fails on CI" trap — a committed file (often a contract test) that reads a path which exists in your working tree but was never committed, so a clean CI checkout cannot see it:

```sh
git config core.hooksPath .githooks
```

The `pre-push` hook runs `python tools/check_untracked_references.py`, which fails if any committed file references an untracked path. Either `git add` the file (or, for genuinely local-only files, add it to `.gitignore`). To push past it deliberately, use `git push --no-verify`.

## Dependency updates

Gradle dependency locking and dependency verification are enabled. After intentionally changing Gradle dependency versions, refresh and review the locks with:

```sh
./gradlew dependencies --write-locks
```

Then refresh and review the checksum metadata with:

```sh
./gradlew --write-verification-metadata sha256 help
```

Commit the resulting `gradle.lockfile` and `gradle/verification-metadata.xml` changes only after confirming the dependency update was intentional and the checksums came from the expected repositories.

## Large assets

Install Git LFS before working with source/reference assets under `docs/superpowers/assets`:

```sh
git lfs install
git lfs pull
```

GLB, OBJ, and PNG files in `docs/superpowers/assets` are stored as LFS objects. Keep shipped game textures under `src/main/resources` in the normal repository unless a separate release decision changes that.

Keep local secrets in environment variables or untracked `.env` files. The Modrinth publish task expects `MODRINTH_TOKEN` in the environment.

## Release notes

Update `CHANGELOG.md` before publishing. It must contain a non-empty `## Attuned <mod_version>` section matching `gradle.properties`, and the Modrinth task uploads only that current-version section as its release notes.
