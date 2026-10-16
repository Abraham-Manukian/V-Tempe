---
paths:
  - "**/*.gradle.kts"
  - "gradle/**"
  - "gradle.properties"
  - ".github/**"
  - "Dockerfile"
  - ".dockerignore"
  - "cloudbuild.yaml"
---

# Build, dependency and CI rules

- Versions live in `gradle/libs.versions.toml`, pinned exactly (no `+`, `latest.*`, ranges, SNAPSHOT).
  New repositories need review (`deps.TRUSTED_REPO_HOSTS` in `scripts/quality/qg/deps.py`); never `http://`.
- The Gradle wrapper keeps `distributionSha256Sum`; CI validates the wrapper JAR.
- Upgrading a library: one library (or one BOM) per change, read its changelog for breaking and
  security changes, run `python scripts/quality/quality_gate.py --profile full` (includes OSV lookup).
- `server-deploy.yml` deploys on every push to `main` touching the paths it lists; `cloudbuild.yaml`
  defines the production service (secrets, env, scaling). Changes here are CRITICAL risk and are
  only pushed on the owner's explicit command.
- The Docker image build runs `:server:test` before `installDist`; keep that gate.
- `.github/workflows/quality-gate.yml` runs the static gate and server tests on PRs; it must not
  get secrets or deployment steps.
- Workflow hygiene: least `permissions`, pass `${{ github.* }}` values through `env:` instead of
  interpolating them into `run:` scripts, pin action versions.
- detekt/ktlint are declared but not applied to any module; enabling them is a separate, owner-approved change.
