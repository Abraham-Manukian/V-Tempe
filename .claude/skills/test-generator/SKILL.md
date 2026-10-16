---
name: test-generator
description: Design, write and run V-Tempe tests derived from the system's contracts (routes/DTOs, repository and use-case behaviour, presenter state, SQLDelight migrations, testdata TSV cases) - boundaries, negative cases, regressions, idempotency and concurrency - in the correct source set, then prove they pass and that regression tests fail without the fix. Use when adding behaviour, fixing a bug, or when qa-test-engineer reports gaps.
argument-hint: "[class, feature or behaviour to test]"
---

# /test-generator

Target: $ARGUMENTS

## 1. Contract first
Read the code under test and its contract: route + DTO (both server and client side), repository
interface, use case, presenter interface, DB schema. Write the behaviour list: input/state ->
expected output/state/side effect/error. Tests assert behaviour, not implementation details.

## 2. Plan (ask for gaps only when useful)
For MEDIUM+ or unfamiliar areas, launch `qa-test-engineer` with the target and your behaviour list;
merge its TEST PLAN with yours. Pick the source set (see `.claude/rules/testing.md`):
- server: `server/src/test/kotlin/...` (JUnit 5, `testApplication`, fakes over mocks);
- shared logic: `shared/src/commonTest`; SQLDelight/JDBC: `shared/src/androidUnitTest`;
- presenters: `ui/src/androidUnitTest`; Android app: `app-android/src/test`;
- shared contract cases: extend `testdata/*.tsv` when the test already reads them.

## 3. Write
Follow the existing test style in the same package (Grep a neighbour test first). Deterministic:
injected clocks, `runTest`, no network, no sleeps. One behaviour per test, descriptive names.
Cover: happy path, boundaries, invalid input (exact error/status), authorization (another user),
idempotency (repeat), concurrency where state is shared, dependency failure.

## 4. Run
- Single class while iterating: `./gradlew :server:test --tests '*ClassName*'` (or
  `:shared:testDebugUnitTest --tests ...`), with JDK 17 + JAVA_TOOL_OPTIONS (CLAUDE.md).
- Regression proof for bug fixes: back up the fixed file to the scratchpad, restore the pre-fix code,
  run the test (must FAIL), restore from the backup, run again (must PASS). Never `git checkout`.
- Finally `python scripts/quality/quality_gate.py --profile standard`.

## Report
| Test | Source set | Behaviour pinned | Result |
Plus the commands with exit codes, the fail-before/pass-after evidence for regression tests, and
behaviours left untested (with reason: device-only, iOS, needs PostgreSQL container).
