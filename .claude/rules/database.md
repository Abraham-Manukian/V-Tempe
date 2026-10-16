---
paths:
  - "shared/src/commonMain/sqldelight/**"
  - "shared/src/*/kotlin/com/vtempe/shared/data/repo/**"
  - "server/src/main/kotlin/com/vtempe/server/**/data/db/**"
  - "server/src/main/kotlin/com/vtempe/server/**/data/repo/**"
  - "server/src/main/kotlin/com/vtempe/server/**/DatabaseFactory*.kt"
---

# Database rules (SQLDelight on devices, Exposed + PostgreSQL on the server)

- Client schema: user devices keep their data across upgrades. A schema change = a new
  `shared/src/commonMain/sqldelight/com/vtempe/shared/db/<N+1>.sqm` migration; never edit a shipped
  `.sqm`; keep `.sq` table definitions equal to the migrated schema (MigrationEquivalenceTest).
- Never drop or rewrite user data in a migration without an explicit owner decision; prefer additive changes.
- Server schema: changes must be backward compatible with the running Cloud Run revision during a
  rolling deploy (add nullable columns first, backfill, then tighten).
- Integrity in the database, not only in code: UNIQUE for idempotency keys (payment external ids),
  NOT NULL, foreign keys where the model has them.
- Concurrency: read-modify-write sequences in one transaction with a guarded `UPDATE ... WHERE` or
  row lock; consider two devices syncing at once and two webhook deliveries at once.
- Avoid N+1: batch reads (`inList`, joins) instead of a query per row; index columns used in WHERE/JOIN.
- Never run queries against production data from this machine; integration tests use the local
  PostgreSQL 16 container described in FIX_PROGRESS.md.
