---
name: code-review
description: Comprehensive risk-based review of V-Tempe changes against a base branch (default main) using the project's read-only reviewer agents and the quality gate; produces verified findings with severity, location, evidence and recommendation. Use when the user asks to review a branch, the current diff or a set of commits. Replaces the generic built-in /code-review in this project (the built-in remains available as /review).
argument-hint: "[base ref, default main]"
---

# /code-review

Base: `$ARGUMENTS` (empty = `main`).

## Steps
1. Bundle: `python scripts/quality/review_bundle.py --base <base>`. Read `.claude/state/review/summary.md`:
   computed risk, triggers, reviewers, reviews already recorded for this fingerprint (`--status`).
   If nothing changed, report that and stop.
2. Gate: if `summary.md` shows no current gate result, run
   `python scripts/quality/quality_gate.py --profile standard --base <base>` first (reviewers read its result).
3. Understand intent: commit messages (`git log <base>..HEAD --oneline`), linked audit ids, the user's
   description. This is the "original requirement" you give to reviewers.
4. Launch the reviewers the risk level requires (`.claude/engineering/risk-matrix.md`), skipping
   those already recorded for this fingerprint. Parallel, one message. Each prompt: requirement,
   bundle path + fingerprint, focus. For LOW: no agents - review the diff yourself.
5. HIGH/CRITICAL: then `adversarial-code-reviewer` alone with only the requirement and the bundle.
6. Verify each finding in the code yourself (open the cited `path:line`). Drop findings you cannot
   confirm or mark them PLAUSIBLE; merge duplicates across reviewers.
7. Record: `python scripts/quality/review_bundle.py --record <agent> --verdict <V> --findings <n>`.

## Output
```
Review of <base>..working tree (<n> files, risk <LEVEL>, fingerprint <fp>)
Gate: <profile> <VERDICT>
Reviewers: <agent: verdict> ...

Findings (most severe first):
1. [SEVERITY] title - path:line
   Evidence: ...
   Recommendation: ...
   Source: <agent(s)> | Status: CONFIRMED/PLAUSIBLE

Not covered: <areas/platforms not reviewed or tested>
Verdict: PASS | CHANGES REQUESTED | BLOCKED
```
Do not modify code during the review unless the user asks to fix the findings.
