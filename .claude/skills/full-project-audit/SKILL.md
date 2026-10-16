---
name: full-project-audit
description: Deep audit of the whole V-Tempe repository on explicit request - full quality gate (all tests, APK, Android Lint, whole-tree secrets, OSV vulnerabilities of resolved dependencies), architecture, security, database, API contracts, dependencies, performance and test coverage by area, consolidated into one prioritized report. Expensive; run only when the user asks for a full audit.
argument-hint: "[optional focus, e.g. server or security]"
disable-model-invocation: true
---

# /full-project-audit

Focus: $ARGUMENTS (empty = everything).

## 1. Automated baseline (run first; it takes several minutes)
`python scripts/quality/quality_gate.py --profile full` in the background; wait for it. Keep the
verdict, every non-PASS check, test counts, Android Lint result and the `deps.osv` list. If the
network is unavailable, `deps.osv` is NOT RUN - report it, do not guess vulnerabilities.

## 2. Map the system once (no agents)
Modules, server features (`server/src/main/kotlin/com/vtempe/server/features/*`), routes, DB tables
and migrations, client data sources, CI/deploy files. Read `FIX_PROGRESS.md` and
`ARCHITECTURE_SECURITY_BACKLOG.md` so known/accepted items are marked as such, not rediscovered.

## 3. Area reviews (read-only agents, parallel, one message)
Give each agent an explicit file scope (area review has no diff: pass the directory list) and the
baseline facts relevant to it:
- `security-reviewer`: auth plugins, AI access, payments/entitlements, account deletion, sync, LLM
  prompt safety, secrets handling, CI/deploy config.
- `database-reviewer`: SQLDelight schema + migrations, Exposed tables/repos/transactions.
- `api-contract-reviewer`: every route vs. the client network layer.
- `architecture-reviewer`: module/package structure, DI, layering.
- `dependency-reviewer`: catalog, build scripts, wrapper, Dockerfile, workflows + the OSV results.
- `performance-reviewer`: sync, LLM pipeline, DB access, HTTP clients.
- `qa-test-engineer`: coverage by area (what has no tests), highest-value missing tests.
Skip agents outside the requested focus. Do not run `adversarial-code-reviewer` here (it is for
diffs); run `regression-reviewer` only if there are uncommitted changes.

## 4. Consolidate
Verify every CRITICAL/HIGH finding in the code yourself. Deduplicate across agents. Do not fix
anything during the audit unless the user asks.

## Report (Russian, to the owner)
1. Summary: overall state, top 5 risks.
2. Gate results table (all checks with status; NOT RUN/NOT CONFIGURED explicit).
3. Findings by severity: id, area, `path:line`, evidence, impact, recommendation, effort (S/M/L).
4. Known/accepted items (from the backlog) still present.
5. Coverage: what was reviewed, what was not (iOS, device, production config) and why.
6. Suggested order of fixes (groups that fit one change each).
Unfixed vulnerability details stay in the chat report, not in repository files.
