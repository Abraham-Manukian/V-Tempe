---
name: bug-investigation
description: Root-cause a V-Tempe bug - reproduce it, trace it to the defect, fix the cause (not the symptom), and prove the fix with a regression test that fails before and passes after. Use when the user reports a crash, wrong result, failing test, CI failure or production error.
argument-hint: "[symptom, error text or failing test]"
---

# /bug-investigation

Bug: $ARGUMENTS

## 1. Capture the symptom
Exact error text, stack trace, failing test name, input/state, platform (server / Android / iOS),
build (debug/release), when it started (`git log` of the suspect area). Do not guess beyond the evidence.

## 2. Reproduce
Smallest reliable reproduction, in this order of preference:
1. An existing test that fails, or a new failing unit/integration test (server: Ktor `testApplication`;
   client: `commonTest`/`androidUnitTest`; presenter: `ui/src/androidUnitTest`).
2. A focused Gradle run: `./gradlew :server:test --tests '*ClassName*'` (JDK 17 + JAVA_TOOL_OPTIONS,
   see CLAUDE.md) or the gate's log in `.claude/state/quality-gate/`.
3. If it cannot be reproduced locally (device-only, production-only), say so and list the evidence
   that would be needed (logcat, server logs) - never mark the bug fixed without reproduction or
   strong code evidence.

## 3. Find the root cause
Trace from the symptom to the first wrong state, with `path:line` at each hop. Check recent commits
in the area (`git log -p -- <path>`), the contract on both sides (server DTO vs client DTO), data
from older versions (local DB, server rows). Ask "why" until the answer is a defect, not a symptom.
Also look for the same defect pattern elsewhere (Grep) - fix siblings or list them.

## 4. Fix
Minimal change at the root cause, consistent with the architecture rules. Classify the fix's risk
(`.claude/engineering/risk-matrix.md`); HIGH/CRITICAL fixes continue with `/engineering-task` steps 4-6.

## 5. Prove it
- Regression test that pins the bug. Show it fails without the fix: back up the fixed file to the
  scratchpad, restore the old code, run only that test (expect FAIL), restore from the backup, run
  again (expect PASS). Quote both results.
- `python scripts/quality/quality_gate.py --profile standard`.
- For MEDIUM+: `regression-reviewer` on the bundle (`review_bundle.py`).

## Report
Symptom -> root cause (`path:line`, why it happened) -> fix -> evidence (failing-then-passing test
output, gate verdict) -> siblings found -> remaining risk. Final status PASS/PARTIAL/BLOCKED.
