---
paths:
  - "shared/**"
  - "ui/**"
  - "core-designsystem/**"
  - "app-android/**"
  - "app-ios/**"
  - "iosApp/**"
---

# Kotlin Multiplatform client rules

- `commonMain` is platform-free: no `android.*`, `java.*`, `javax.*`. Platform code goes in
  `androidMain`/`iosMain` behind an interface or `expect`/`actual`.
- `shared.domain` holds models, use cases and repository interfaces; `shared.data` implements them
  (Ktor client, SQLDelight, settings). New domain code must not import `shared.data`.
- `ui` presenters are interfaces in `commonMain` with an Android ViewModel (`ui/src/androidMain/.../vm`)
  and an iOS presenter (`ui/src/iosMain`). Add or change a presenter method in all of them at once.
- Compose: screens stay small - state classes and sections in their own files; no business logic
  in composables; strings in `composeResources` (`values` and `values-ru` together).
- Coroutines: lifecycle-bound scopes (`viewModelScope`/presenter scope); cancel on dispose; no
  `GlobalScope`, no `runBlocking` on the main thread; collect flows with lifecycle awareness.
- Network: one configured Ktor `HttpClient` from DI; timeouts set; errors mapped to domain results,
  offline fallbacks stay consistent with server rules (do not silently use a different formula).
- Secrets: `BuildConfig` values come from `local.properties`/Gradle properties; never hard-code keys,
  tokens or URLs with credentials. `google-services.json` and keystores are never committed.
- Billing/entitlement state on the client is a cache of the server's decision, never the authority.
- iOS cannot be built on Windows: say "iOS not compiled" in the report when `commonMain`/`iosMain` changed.
