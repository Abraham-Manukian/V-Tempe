---
paths:
  - "server/**"
---

# Server (Ktor + Koin + Exposed) rules

- Structure: `features/<feature>/{api,data,domain}`; `api` = routes + DTO mapping, `domain` = models and
  ports (no Ktor/Exposed imports), `data` = services, repositories, DB tables. Wiring in `app/di`.
- Identity comes only from `call.attributes[UserIdKey]` set by `UserAuthPlugin` after Firebase ID-token
  verification. Never read a user id from a header, query or body for authorization decisions.
- `/ai/*` access is decided by `AiAccessPlugin` (`X-App-Token` + optional Firebase token + `AI_PAID_ACCESS`
  policy). `AI_PAID_ACCESS=everyone` in cloudbuild.yaml is a documented owner decision for the testing phase.
- Respond with precise status codes: 400 invalid input, 401 unauthenticated, 403 forbidden, 404/410 gone,
  409 conflict, 413 too large, 503 dependency unavailable (retryable). Do not return 200 with an error body.
- Validate every DTO field (ranges, lengths, enums) at the `api` boundary; reject unknown or oversized input.
- Webhooks (YooKassa): never trust the body; re-fetch the payment from the provider; idempotent by
  external id; "could not verify" (503, provider retries) differs from "verified unpaid" (200).
- Database: `newSuspendedTransaction` for suspend code; multi-step writes in ONE transaction; guarded
  `UPDATE ... WHERE` or `insertIgnore` for idempotency; no network calls inside a transaction.
- Logging: `LoggerFactory.getLogger(...)`; never log tokens, secrets, prompts with profile/health data or
  LLM raw output (LlmRawStore stays off by default).
- LLM input: user text goes through `PromptSafety` (`untrustedDataBlock` / `sanitizeInlineUserText`);
  LLM output is untrusted and re-validated (allergens, injuries, plan structure) before it reaches a user.
- Config via `Env`/environment only; a missing optional secret degrades the feature, it never crashes the server.
- Tests: JUnit 5 + `ktor-server-test-host` in `server/src/test`. Postgres-specific code also needs
  `PostgresIntegrationTest` coverage (runs with `VTEMPE_PG_TEST_URL`; refuses non-localhost hosts).
