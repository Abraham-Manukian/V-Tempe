---
paths:
  - "**/src/test/**"
  - "**/src/*Test/**"
  - "testdata/**"
---

# Test rules

- Server: JUnit 5 (`org.junit.jupiter`), Ktor `testApplication`, `kotlinx-coroutines-test`.
  Client: `kotlin.test` in `commonTest`; JVM-specific tests (SQLDelight JDBC driver) in
  `shared/src/androidUnitTest`; presenter tests in `ui/src/androidUnitTest`.
- `testdata/*.tsv` are contract cases shared by the server tests (allergens, injuries, nutrition
  targets, offline pool). Extend them instead of duplicating cases in code.
- A regression test must fail on the code before the fix. When practical, prove it: revert the fix
  (back up the file to the scratchpad, never `git checkout`), run the test, restore the backup.
- Name tests by behaviour (`refundRemovesUnusedWindow`), one behaviour per test, arrange/act/assert.
- Deterministic tests only: inject clocks (`Clock`/`TimeSource`), no real network, no sleeps;
  use `runTest` virtual time for coroutines.
- Cover: happy path, boundaries (0, 1, max, empty, null), invalid input (expect the exact error/status),
  authorization (other user's id -> 403/404), idempotency (same request twice), concurrency where state is shared.
- Never weaken or delete an existing assertion to make a change pass; if a test is wrong, explain why in the commit.
