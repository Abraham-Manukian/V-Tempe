---
name: engineering-task
description: Full engineering cycle for a serious V-Tempe change - Research, Plan, Implement, Test, Review, Verify - with risk-based reviewer selection and a final engineering report. Use for any feature, behaviour change or fix that touches server logic, persistence, API, auth, payments, sync, LLM pipeline, build or CI (MEDIUM/HIGH/CRITICAL risk). Not needed for a typo, a doc edit or a pure local refactor (LOW).
argument-hint: "[task description]"
---

# /engineering-task

Task: $ARGUMENTS

## 0. Classify
Read `.claude/engineering/risk-matrix.md`. State in one line: risk level + reason. If the task is
LOW, do it directly (tests + `quality_gate.py --profile standard`, short report) and stop using this skill.

## 1. Research (no edits yet)
- Find the code the task touches and its callers/callees (Grep/Glob; for a broad sweep you may use
  the Explore agent once). Read the full files, the related tests, and the contracts involved:
  routes/DTOs on both sides (server `shared/dto`, client `shared/data/network`), DB schema
  (`.sq/.sqm`, Exposed tables), presenter interfaces and ALL their implementations.
- Check `FIX_PROGRESS.md` / `ARCHITECTURE_SECURITY_BACKLOG.md` for prior decisions on this area.
- Write down: current behaviour, the change needed, invariants that must keep holding.

## 2. Plan (short)
3-8 bullets: files to change, contract changes and their compatibility story, tests to add
(regression/boundary/negative/concurrency), risks. For CRITICAL: run `/security-audit` on the
design first (threat model) and adjust the plan.

## 3. Implement
Small logical steps. After each step the PostToolUse hook reports blocking problems - fix them
immediately. Follow `CLAUDE.md` and the path rules in `.claude/rules/`. Update every
implementation of a changed interface in the same step.

## 4. Test
- Write the tests from the plan (`/test-generator` for non-trivial cases; for MEDIUM+ ask
  `qa-test-engineer` for gaps only after your own tests exist).
- Bug fix: prove the regression test fails without the fix (back up the file to the scratchpad,
  revert the fix, run the single test, restore from the backup - never `git checkout`).
- Run `python scripts/quality/quality_gate.py --profile standard` (it compiles and tests every
  affected module and its dependents). Use `--profile full` when dependencies/build files changed.
  Fix failures; do not proceed on FAIL.

## 5. Review (risk-based, read-only agents)
1. `python scripts/quality/review_bundle.py --base main` - writes `.claude/state/review/`, prints
   the computed level and reviewers, and which reviews already exist for this exact code.
2. Launch the reviewers the level requires (`.claude/engineering/risk-matrix.md`) **in one message,
   in parallel**. Prompt each with: the original task text, "Review bundle: .claude/state/review/
   (fingerprint <fp>)", and its focus. Do not tell them your conclusions.
3. For HIGH/CRITICAL, after the specialists: fix CONFIRMED CRITICAL/HIGH findings, re-run the gate,
   rebuild the bundle, then launch `adversarial-code-reviewer` alone with only the task text and the bundle.
4. Record each result: `python scripts/quality/review_bundle.py --record <agent> --verdict <V> --findings <n>`.
5. Verify every finding yourself in the code before acting; reject unsupported ones with a reason.

## 6. Verify and report
- Final `quality_gate.py` run on the final code (the Stop hook checks the fingerprint).
- `git diff --stat` and a read of the full diff: no unrelated changes, no debug code, no secrets.
- Report with `.claude/engineering/report-template.md` (in Russian to the owner). Final status:
  PASS only if gate PASS + no open CRITICAL/HIGH + nothing required NOT RUN; else PARTIAL/BLOCKED.
- Do not commit or push unless the owner asked; pushing needs the owner's explicit command.

## Done when
The behaviour is implemented, tests that pin it pass, the gate verdict for the final fingerprint is
reported verbatim, required reviews are recorded and their CRITICAL/HIGH findings are fixed or
explicitly accepted by the owner, and remaining risks are listed.
