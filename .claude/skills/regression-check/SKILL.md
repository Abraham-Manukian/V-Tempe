---
name: regression-check
description: Check a V-Tempe change for the risk of breaking existing behaviour - callers across modules, user flows, stored data from older versions, deployed clients, and whether tests really pin the changed behaviour - using the regression-reviewer plus the affected-module test run. Use before merging shared/server changes, after refactors, and when a fix touches code used by several features.
argument-hint: "[base ref, default main]"
---

# /regression-check

Base: `$ARGUMENTS` (empty = `main`).

## Steps
1. `python scripts/quality/review_bundle.py --base <base>` and read the changed-file list.
2. Impact map (yourself, fast): for each changed public symbol Grep its usages in every module;
   list affected user flows (onboarding, plans, AI chat edits, workout tracking, progress, sync,
   sign-in/guest, paywall, account deletion, offline fallback).
3. Tests of the impact map: `python scripts/quality/quality_gate.py --profile standard --base <base>`
   runs compile + unit tests for affected modules and all dependents. Note which flows have no
   automated test at all (device-only, iOS).
4. Launch `regression-reviewer` with the original requirement, the bundle and your impact map.
   If DTOs or schema changed, add `api-contract-reviewer` / `database-reviewer` in the same message.
5. For each reported risk: write the missing test (or `/test-generator`) or explain why it is not
   reachable; re-run the gate.

## Report
| Flow / caller | Changed? | Evidence | Test covering it |
Then findings (severity, location, scenario), gate verdict, untested flows (explicit), final
status PASS / PARTIAL / BLOCKED.
