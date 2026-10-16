---
name: architecture-review
description: Analyse V-Tempe structure, module and package dependencies, layering, DI wiring and conformity with the project's architecture - for the current change or a named module/feature. Combines the automated arch.boundaries check with the read-only architecture-reviewer. Use before larger refactors, when adding modules/features/dependencies, or when the user asks how well an area follows the architecture.
argument-hint: "[module/feature/path, default: current diff vs main]"
---

# /architecture-review

Scope: $ARGUMENTS (empty = current diff vs `main`).

## Steps
1. Automated facts (fast, deterministic):
   `python scripts/quality/quality_gate.py --profile quick --base main` -> `arch.boundaries`
   (module graph vs `modules.ALLOWED_PROJECT_DEPS`, layer import rules, oversized files) and
   `deps.hygiene`. New violations are facts; baseline entries are known debt.
2. Structure map of the scope (Glob + Grep, no agent needed): packages, entry points, Koin modules
   that wire it (`app/di` on the server, `shared/data/di` + platform modules on clients), who
   depends on it (Grep imports of its package).
3. Launch `architecture-reviewer` with the scope (bundle from
   `python scripts/quality/review_bundle.py --base main`, or the file list for an area review) and
   the structure map. For dependency/build scope add `dependency-reviewer` in the same message.
4. Verify findings in the code; separate "violates a documented project rule" (cite the rule) from
   "design opinion".

## Report
- Dependency picture (module edges, notable package dependencies, cycles if any).
- Violations: new (FAIL) vs known baseline debt.
- Reviewer findings with severity, `path:line`, evidence, recommendation.
- Recommendations ordered by value/effort; anything that changes module structure needs the owner's
  decision before implementation.
