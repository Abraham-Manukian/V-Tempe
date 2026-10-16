---
name: performance-audit
description: Find V-Tempe performance and concurrency problems with measurements where possible - test timings, Gradle test reports, targeted micro-measurements in tests, query counts - plus a read-only performance-reviewer pass that separates measured from potential issues. Use when the user reports slowness, for changes on hot paths (sync, plan generation, LLM pipeline, DB access, list rendering), or before scaling decisions.
argument-hint: "[area, endpoint or symptom]"
---

# /performance-audit

Target: $ARGUMENTS

## 1. Define the question
What is slow or risky (endpoint, screen, job), for which input size, against which budget
(e.g. "/ai/bundle under 30 s incl. LLM", "sync PATCH under 300 ms server-side")? Without a budget,
report numbers, not verdicts.

## 2. Measure what can be measured locally
- Existing timings: JUnit XML in `<module>/build/test-results/**` (per-test `time`), Gradle log from
  the gate (`.claude/state/quality-gate/gradle-*.log`).
- Server: a focused Ktor `testApplication` test that calls the endpoint N times with realistic
  payloads and records wall time with `kotlin.time.measureTime` (keep it out of CI, or assert only
  generous bounds). LLM calls are mocked - measure our code, report provider latency separately.
- DB: count queries for a request (Exposed `addLogger(StdOutSqlLogger)` in a test, or a counting
  repository fake) to prove or disprove N+1; for PostgreSQL-specific plans use the local container
  from FIX_PROGRESS.md with `EXPLAIN ANALYZE` - never production.
- Client: unit-level measurement of pure logic; UI/jank needs a device (Android Studio profiler) -
  state that it was not measured if no device is available.

## 3. Review
`python scripts/quality/review_bundle.py --base main` (if there is a diff) and launch
`performance-reviewer` with the scope and your measurements (numbers + how obtained). For DB-heavy
targets add `database-reviewer` in the same message.

## 4. Fix (only with a measurement)
Change one thing, re-measure the same way, keep the change only if the number improves and tests
pass (`quality_gate.py --profile standard`). Remove ad-hoc measurement code or keep it as a
documented, non-flaky test.

## Report
| Scenario | Input size | Before | After | Method |
Then MEASURED findings, POTENTIAL findings (with the measurement that would confirm), what was not
measurable here (device, production load, LLM latency) and why.
