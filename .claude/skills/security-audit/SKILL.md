---
name: security-audit
description: Security audit of the V-Tempe components a change or a named area touches - builds a threat model (assets, actors, trust boundaries, entry points, STRIDE threats), then has the read-only security-reviewer trace real execution paths, and verifies the findings. Use for CRITICAL tasks (payments, entitlements, auth, account/PII, crypto, deploy config) before and after implementation, or when the user asks for a security check of an area.
argument-hint: "[area or path, default: current diff vs main]"
---

# /security-audit

Scope: $ARGUMENTS (empty = everything the current branch changes vs `main`).

## 1. Scope and inputs
- Diff scope: `python scripts/quality/review_bundle.py --base main`.
- Area scope (no diff): list the files of the area with Glob and treat them as the scope.
- Read `.claude/rules/server.md` / `kmp-client.md` and the security notes in
  `ARCHITECTURE_SECURITY_BACKLOG.md` for known decisions (do not re-report accepted ones as new).

## 2. Threat model (write it, keep it short)
| Item | Content |
| --- | --- |
| Assets | e.g. paid access, payment records, health/profile data, tokens/secrets, user plans |
| Actors | anonymous client, guest, signed-in user, other user, YooKassa, LLM provider (untrusted output), CI |
| Trust boundaries | HTTP -> Ktor plugins -> routes; webhook -> provider re-verification; LLM output -> validators; device DB; build secrets |
| Entry points | routes/plugins/presenters in scope with `path:line` |
| Threats (STRIDE) | spoofing, tampering, repudiation, information disclosure, DoS (LLM cost), elevation of privilege - one line each where relevant |

## 3. Review
Launch `security-reviewer` with: the threat model, the scope (bundle path + fingerprint, or the
file list), and the question "trace each threat through the code; report CONFIRMED/PLAUSIBLE
findings". For payment/sync/persistence scope also launch `database-reviewer`; for routes/DTOs
`api-contract-reviewer` - in the same message.

## 4. Automated checks
`python scripts/quality/quality_gate.py --profile standard` (secrets in changed files, forbidden
files, architecture boundaries). Optional tools (`tools.gitleaks`, `tools.semgrep`) run only if
installed - report them as NOT CONFIGURED otherwise.

## 5. Verify and conclude
Re-open every finding's `path:line` yourself. For each CONFIRMED CRITICAL/HIGH: a fix plus a
negative test (attack scenario as a test: other user's id -> 403/404, replayed webhook -> no double
grant, forged token -> 401). Record reviews with `review_bundle.py --record`.

## Report
Threat model table, findings (severity, location, evidence, recommendation, status), tests added,
gate verdict, explicit list of unmitigated risks and accepted owner decisions. Do not put exploit
details of unfixed vulnerabilities into repository files - only in the chat report.
