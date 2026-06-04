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

## Dependency updates

Gradle dependency locking is enabled. After intentionally changing Gradle dependency versions, refresh and review the locks with:

```sh
./gradlew dependencies --write-locks
```

Keep local secrets in environment variables or untracked `.env` files. The Modrinth publish task expects `MODRINTH_TOKEN` in the environment.

## Release notes

Update `CHANGELOG.md` before publishing. The Modrinth task reads its release notes from that file.
