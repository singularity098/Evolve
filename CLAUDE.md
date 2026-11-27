# CLAUDE.md

Notes for Claude working on Evolve.

## Git workflow

- **Commit and push directly to `main`.** Don't create feature branches or open pull requests
  unless the owner asks for one.
- Claude Code setups (the web version especially) may create their own working branch for the
  session. Even then, don't use that branch: commit on `main` and push `main`
  (`git push origin HEAD:main`). Don't push the session branch.
- This is a solo project: branches and PRs add very little here and create unwanted overhead.

## The project

See `README.md`. A Rust engine (`crates/evolve`, exposed through UniFFI) under a Jetpack
Compose app (`android/`), styled after Chippy (singularity098/Chippy). `cargo test` tests the
engine; `android/build-apk.sh` builds the APK. The database is JSON and doubles as the export
format: only ever add fields (with serde defaults), never rename them.

## Wording

In anything the user sees, say **activity**, not "workout" (breathing and meditation aren't
workouts). Code identifiers, the engine's types and the JSON fields keep `workout`: renaming
them would break the export format.
