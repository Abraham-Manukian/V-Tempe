# Risk matrix and reviewer orchestration

Classify every task before implementing it, and re-check against the computed level once the
diff exists: `python scripts/quality/review_bundle.py --base main` (rules:
`scripts/quality/risk-rules.toml`). The higher of your judgement and the computed level wins.
State the level and the reason in one line at the start of the work.

| Level | Typical change in V-Tempe | Process |
| --- | --- | --- |
| LOW | Local refactor without behaviour change, test-only change, docs, strings | Main agent only. Relevant tests, `quality_gate.py --profile standard` (or `quick` if no Gradle module is affected), self-review of the diff. No subagents. |
| MEDIUM | Business logic in server/shared/ui, a new presenter or use case, Claude config | One specialist reviewer (the first one the bundle lists) + `qa-test-engineer`. Standard gate. |
| HIGH | HTTP routes/DTOs, Exposed or SQLDelight persistence, sync, coroutines/concurrency, LLM pipeline and safety rules, dependencies/Gradle, CI | Architecture review if structure changes, every specialist the bundle lists, `qa-test-engineer`, `regression-reviewer`, then `adversarial-code-reviewer`. Standard gate + targeted extra tests; `full` gate when dependencies or build files changed. |
| CRITICAL | Payments/YooKassa/entitlements, authn/authz and AI access policy, account deletion, health/PII, crypto/signatures, release signing, deploy config (`cloudbuild.yaml`, `server-deploy.yml`, `Dockerfile`), production migrations | HIGH process + written threat model (`/security-audit`) + `security-reviewer` always + `adversarial-code-reviewer` mandatory. Every unresolved risk listed explicitly in the report. Any production-facing step (push, deploy, migration, secret) only on the owner's explicit command. |

## Orchestration rules (token budget)

- Never launch reviewers for LOW. Never launch every reviewer "to be safe".
- Reviewers are read-only and independent: launch the ones a level requires **in parallel, in one
  message**, after the implementation and the gate are done. Give each the bundle path, the
  original requirement, and nothing that tells it what to conclude.
- `adversarial-code-reviewer` runs **after** the specialists, on the final diff, and is not told
  their conclusions (only the requirement and the bundle).
- No duplicate work: `python scripts/quality/review_bundle.py --status` lists reviews recorded for
  the current code fingerprint; do not re-run those. Record each finished review with
  `--record <agent> --verdict PASS|FINDINGS|BLOCKED --findings N`. A fix changes the fingerprint,
  so only reviewers whose area the fix touches run again.
- Agents that write (only the main agent in this setup) never work in parallel on the same files.
  If parallel implementation is ever needed, use separate worktrees (`isolation: worktree`).
- A reviewer may be skipped only when the matrix does not require it, or when its area is
  provably untouched by the diff; say which and why in the report.

## Severity (shared by all reviewers)

| Severity | Meaning | Gate |
| --- | --- | --- |
| CRITICAL | Exploitable security hole, data loss/corruption, money or paid access granted wrongly, crash on a main path | Must be fixed before the task is done |
| HIGH | Wrong behaviour in a realistic scenario, broken contract for shipped clients, missing authz check, race with user-visible effect | Must be fixed, or the owner explicitly accepts it |
| MEDIUM | Edge-case bug, missing negative test, maintainability problem that will cause bugs | Fix or record as a remaining risk with a reason |
| LOW | Readability, naming, small duplication | Optional |
| INFO | Observation, no action needed | - |
