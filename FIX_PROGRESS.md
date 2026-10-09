# V-Tempe — прогресс исправлений аудита

- Ветка: `fix/verified`, worktree `C:/Users/AbrahamSuperStar/AndroidStudioProjects/V-Tempe-Fixes`.
- Основа: коммит аудита `5c884bd` (`main`). Merge в `main` и push — только по команде владельца.
- Источник задач (только чтение): `C:/Users/AbrahamSuperStar/AndroidStudioProjects/V-Tempe/TODO.md`
  (неотслеживаемый файл Astra в основном репозитории) и доказательства в
  `C:/Users/AbrahamSuperStar/Documents/ChatGPT/V-TEMPE/` (`AUDIT_COVERAGE.md`, `audit-iteration-*.md`, `audit-probes/*/RESULTS.md`).
  На 2026-10-09 отсутствуют `V-Tempe-Audit`, ветка `audit/report`, `audit-handoff/AUDIT_HANDOFF.md` и отдельный план для Claude;
  при их появлении перечитать.
- Сборка: JDK 17 (`C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot`), `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=...`
  (иначе Gradle падает с «Unable to establish loopback connection»), `--offline --max-workers=2`.

## Сводка

| ID | Статус | Коммит |
| --- | --- | --- |
| VT-AUD-002 | Исправлено, тесты | `2988e0c` |
| VT-AUD-003 | Исправлено, тесты | `2988e0c` |
| VT-AUD-004 | Исправлено, тесты (политика на этапе тестирования — см. риски) | `2988e0c` |
| VT-AUD-005 | Исправлено, тесты | `2988e0c` |
| VT-AUD-001, 006–050 | Не начато | — |

## Группа 1 — доступ к AI API (VT-AUD-002–005)

- **Аудит-коммит находки:** `5c884bd`. Применимость к ветке проверена: код `Application.kt`/`AiRoutes.kt`/`LlmClientRouter.kt` совпадал с описанием Astra.
- **Решения владельца (2026-10-09):** гость без Firebase-токена допускается, но только в рамках политики; пока подписки не продаются — платная модель доступна всем, кто скачал приложение; в будущем — только по подписке, с возможностью выдавать подписку отдельным аккаунтам вручную.

### Причина и исправление

| ID | Причина | Исправление |
| --- | --- | --- |
| 002 | Глобальный interceptor проверял сырой `request.uri.startsWith("/ai/")`, а роутер сопоставляет декодированные сегменты (`/%61i/training`) | `AiAccessPlugin` — route-scoped plugin на поддереве `route("/ai")`; выполняется для любого вызова, который роутер привёл в AI handler, до чтения тела. Глобальный interceptor удалён |
| 003 | На `/ai` проверялся только общий app token из APK; `Authorization` игнорировался | `AiAccessResolver`: присланный `Authorization` обязан быть валидным Firebase ID token, иначе 401. Без заголовка — явный гость (`AI_GUEST_ACCESS`, по умолчанию `true`) |
| 004 | Любой `llmMode`, кроме `"free"` (включая `null`/мусор), выбирал paid; entitlement не проверялся | Тариф вычисляет сервер: `AI_PAID_ACCESS` = `entitlement` (по умолчанию в коде) / `authenticated` / `everyone`. Handler заменяет `profile.llmMode` на разрешённый (клиент может только понизить до free) до кэша и use case. `LlmClientRouter` берёт paid только при явном `"paid"`. Ошибка entitlement-хранилища → free |
| 005 | Отсутствующий/пустой `APP_SECRET` молча отключал проверку | Без секрета `/ai` отвечает 503 (fail closed, остальные маршруты и `/health` работают). Dev-обход только явным `AI_ALLOW_MISSING_APP_SECRET=true` и пропускает только app token — Firebase и политика тарифа остаются |

### Затронутые файлы

- Новые: `server/.../features/ai/access/{AiAccessConfig,AiAccessResolver,AiAccessPlugin}.kt`, `server/.../features/auth/IdTokenVerifier.kt`.
- Изменены: `Application.kt` (удалён URI-interceptor, лог конфигурации), `ServerModule.kt` (DI конфигурации и резолвера), `AiRoutes.kt` (установка плагина, `authorizedProfile`), `LlmClientRouter.kt` (fail closed), `FirebaseTokenVerifier.kt` (реализует `IdTokenVerifier`), `cloudbuild.yaml` (`AI_PAID_ACCESS=everyone`), `gradle/libs.versions.toml` + `server/build.gradle.kts` (test-host только для тестов), `RELEASE_CHECKLIST.md`.
- Тесты: `AiAccessResolverTest` (9), `AiRoutesAccessTest` (9), `LlmClientRouterTest` (1).

### Проверки

- `:server:test :server:installDist --offline` — **BUILD SUCCESSFUL, 113 тестов, 0 failures/errors/skipped** (94 прежних + 19 новых).
- HTTP-тесты через Ktor test host с реальным `registerAiRoutes`: все пять маршрутов; без/с неверным app token → 401; варианты `/%61i/…`, `/%61%69/…`, закодированный первый символ сегмента, query, `//ai/…`, `/ai//…`, завершающий `/` — не 200; счётчик fake use case = 0. Неверный Bearer → 401. Гость → free при `paid`/`null`/`premium`. Подписчик → paid, обычный пользователь → free, подписчик с `free` → free. `everyone` → paid для гостя. Без секрета → 503 (включая закодированный путь); dev-режим → гость 200, неверный Bearer 401.
- Мутационная проверка: при отключении плагина и подмены llmMode падают все 9 HTTP-тестов; код восстановлен.
- Smoke реального `Application.module` с production DI без `.env`: `/health` 200, `/ai/training` и `/%61i/training` → 503, `/me/entitlement` → 401 (временный тест удалён, не коммитился).
- Реальные LLM, сеть, платежи и БД не использовались (fake use case/LLM, fake verifier, in-memory entitlement).
- Probes Astra (`audit-probes/ai-auth`) живут вне репозитория и фиксируют старое дефектное поведение; не изменялись — после исправления их ожидания устарели.

### Риски регрессий и ограничения

- **Деплой:** при выкатке без `APP_SECRET` AI станет недоступен (503) — в `cloudbuild.yaml` секрет задан. `AI_PAID_ACCESS=everyone` задан в `cloudbuild.yaml`; если сервис деплоится иначе, без переменной сработает `entitlement` и все получат free-модель.
- **Этап тестирования:** `everyone` сознательно оставляет paid-модель доступной любому владельцу app token (извлекаемого из APK), ограничение — только IP rate limit. Перед продажей подписок переключить на `entitlement` (отмечено в `RELEASE_CHECKLIST.md`).
- Клиент с истёкшим/битым Firebase-токеном теперь получает 401 вместо ответа; штатный клиент берёт свежий токен через Firebase SDK. Отдельной обработки 401 на AI-экранах нет — отображается общая ошибка сети.
- Ручная выдача подписки админом: сейчас только записью в таблицу entitlements (нет `PaymentSource` для ручного гранта и admin-эндпоинта) — отдельная задача.
- Production ingress (Cloud Run) не проверялся; защита больше не зависит от его нормализации URL.
- `/me` interceptor по-прежнему сравнивает сырой `path()`: закодированный путь (`/%6De/…`) его обходит, но каждый `/me` handler сам отвечает 401 без `UserIdKey` — сейчас не эксплуатируемо; кандидат на тот же route-scoped подход (вне группы, не менялось).
- Paid → free fallback при ошибках провайдера сохранён.

### Зависимости

- 003 опирается на 002 (единая точка проверки); 004 — на 003 (UID для entitlement); 005 — на 002. Будущие квоты на пользователя/тариф (граница политики из итерации 5 Astra) могут использовать `AiAccessKey` (UID/гость/тариф).

## Заблокированные задачи

- Нет.

## Готово к следующей итерации

- Следующая группа по приоритету владельца — питание и аллергии: VT-AUD-025 (offline-план игнорирует аллергии/диету), 026 (аллерген в инструкции приготовления), 027 (предупреждения качества отключают проверку аллергенов); связанные 035 и прочие из 025–037 — уточнить по `TODO.md` перед началом.
