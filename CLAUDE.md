# V-Tempe - engineering rules for Claude Code

AI fitness app. Kotlin Multiplatform client + Ktor server, one Gradle build (Kotlin 2.0.21, AGP 8.12, Gradle 8.13, JDK 17).

| Module | Role | Depends on |
| --- | --- | --- |
| `shared` | KMP domain (`com.vtempe.shared.domain`) + data (`.data`: Ktor client, SQLDelight DB, repos), Koin DI | - |
| `core-designsystem` | Compose MP theme and components | - |
| `ui` | Compose MP screens + presenters (`commonMain`), Android ViewModels (`androidMain/.../vm`), iOS presenters | shared, core-designsystem |
| `app-android` | Android app: Firebase auth, Play Billing, BuildConfig secrets from local.properties | shared, ui |
| `app-ios` / `iosApp` | iOS framework + Xcode app (needs macOS) | shared, ui |
| `server` | Ktor + Koin + Exposed/PostgreSQL, `features/<name>/{api,data,domain}`, deployed to Cloud Run | nothing (no `:shared` dependency) |

A push to `main` auto-deploys the server (`server-deploy.yml` -> `cloudbuild.yaml`).

## Commands (Windows dev machine; CI is Linux)

- Gradle needs JDK 17+ and, on Windows, `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<short dir>`.
  The gate sets both; for manual runs: `JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot"`.
- Server tests: `./gradlew :server:test` (PostgreSQL integration test runs only with `VTEMPE_PG_TEST_URL`, see FIX_PROGRESS.md).
- Client unit tests: `./gradlew :shared:testDebugUnitTest :ui:testDebugUnitTest :app-android:testDebugUnitTest`.
- Debug APK: `./gradlew :app-android:assembleDebug`; lint: `./gradlew :app-android:lintDebug`.
- Quality gate (use this, it picks the right Gradle tasks): `python scripts/quality/quality_gate.py --profile quick|standard|full`
  (exit 0 PASS, 1 FAIL, 3 INCOMPLETE). Review input for subagents: `python scripts/quality/review_bundle.py --base main`.
  Use `python3` where `python` is not Python 3.11+.

## How every task runs

1. Classify risk LOW / MEDIUM / HIGH / CRITICAL (`.claude/engineering/risk-matrix.md`) and say it in one line.
2. Research first: read the affected code, its callers, its tests and the contracts it touches.
3. Minimal plan, then small logical changes. HIGH/CRITICAL work follows `/engineering-task`.
4. Run the relevant tests and the gate; launch only the reviewers the risk level requires.
5. Report with `.claude/engineering/report-template.md` (short for LOW).

Skills: `/engineering-task` (full cycle), `/bug-investigation`, `/test-generator`, `/quality-gate`,
`/pre-commit-review`, `/code-review`, `/security-audit`, `/architecture-review`, `/performance-audit`,
`/regression-check`, `/full-project-audit` (on request only). Reviewer agents are read-only.

## Architecture

- Follow the existing structure; do not introduce new layers, frameworks or patterns without need.
- Module edges above are enforced by `arch.boundaries`: no new project dependencies, no `android.*`/`java.*`
  in `commonMain`, shared `domain` must not import `data` (two known exceptions in `scripts/quality/arch-baseline.txt`),
  server `features/*/domain` stays free of Ktor/Exposed and of `data`/`api`.
- SOLID/DRY/KISS within reason: narrow interfaces, one responsibility per class, no speculative abstraction.
  Keep files small (soft limit ~300 lines; extract state/sections instead of growing a file).
- DI through Koin modules only; no service locators in business logic, no hidden singletons.
- Changing an interface means updating every implementation in the same change (Android VM, iOS presenter, test fakes).
- Keep backward compatibility of HTTP DTOs and client DB schema unless the task says otherwise:
  deployed apps call the server, and user devices hold SQLDelight data (new `N.sqm` migration, never edit a shipped one).
- No keyword/dictionary hard-coding for behaviour that must scale across languages; fix root causes, not symptoms.

## Implementation

- Analyse existing contracts (DTOs, routes, DB schema, presenter interfaces) before changing them.
- Handle errors explicitly: no swallowed exceptions, no broad `catch` that hides DB/network failures,
  distinguish "could not verify" from "verified negative". No hidden side effects in getters/mappers.
- Coroutines: structured scopes only (no `GlobalScope`), no `runBlocking` in production paths,
  blocking I/O on `Dispatchers.IO`, Exposed work inside transactions.
- Do not suppress compiler/lint warnings without a comment explaining why.

## Security

- Treat all external data as untrusted: request bodies, LLM output, webhook bodies (re-verify with the provider), user profile text in prompts (`PromptSafety`).
- Authorization is decided on the server for every request; never trust client-side flags or IDs (IDOR).
- Secrets live in Secret Manager / env / `local.properties`; never in code, logs, commits, docs or chat.
  Do not log tokens, health data or other PII.
- Critical operations (payments, entitlements, account deletion, sync writes) must be idempotent and atomic.
- Do not describe exploit details of open vulnerabilities in repository docs - only that they exist and their priority.

## Testing

- Every bug fix gets a regression test that fails without the fix.
- Cover boundaries and negative cases; add concurrency tests where state is shared.
- UI presenter tests go in `ui/src/androidUnitTest` (`androidHostTest` is not wired to `testDebugUnitTest`).
- Run the tests related to the change before saying it works; quote the command and the result.

## Definition of done

- The gate ran on the final code (the Stop hook enforces this for edited code) and its verdict is reported as-is.
- Never claim a check passed without its output. NOT RUN, NOT CONFIGURED and skipped checks are reported as such.
- Unverified platforms (iOS, real device) and remaining risks are listed explicitly.
- Git: commit only when asked; never push, deploy, run production migrations or touch production data
  without the owner's explicit command (the guard hook blocks these in bypass mode).
- Commits and repository texts in English; reports to the owner in Russian.
