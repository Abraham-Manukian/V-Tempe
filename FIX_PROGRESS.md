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
| VT-AUD-025 | Исправлено, тесты | `356ef2d` |
| VT-AUD-026 | Исправлено, тесты | `0e57d17` |
| VT-AUD-027 | Исправлено, тесты | `0e57d17` |
| VT-AUD-028 | Исправлено, тесты | `0e57d17` |
| VT-AUD-037 | Исправлено (фасад удалён) | `0e57d17` |
| NEW-CLAUDE-01 — правки плана из чата обходили проверку аллергенов | Исправлено, тесты | `0e57d17` |
| VT-AUD-001, 006–024, 029–036, 038–050 | Не начато | — |

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

## Группа 2 — безопасность питания и аллергии (VT-AUD-025–028, 037)

- **Аудит-коммит находок:** `5c884bd` (итерация 7 Astra). Применимость проверена по коду ветки: все пять описаний совпадали; зависимость 027 → 026/028 → 025 → 037 соблюдена.
- **Новая находка (NEW-CLAUDE-01):** операции правки из чата (`add_meal`, `swap_meal`, `add_ingredient`, `set_ingredient`) применялись `CoachEditApplicator` без проверки аллергенов — валидатор чата видел только `editOps`, а не итоговый план. Исправлено в той же группе.

### Причина и исправление

| ID | Причина | Исправление |
| --- | --- | --- |
| 027 | Проверка аллергенов шла после quality-проверок и останавливалась при 12 сообщениях; relaxed-режим снимал остальное | `nutritionSafetyViolations` выполняется первым и полностью; находки стоят первыми в обрезанном отчёте; маркер `allergen restriction violation` всегда critical в `AiQualityErrorPolicy` |
| 026 | Проверялись только name/ingredients; при наличии `allergenTags` модели текст вообще не проверялся | Новый `AiNutritionSafetyGate`: name, ingredients, recipe (по клаузам), shopping list и `allergenTags` — теги и текст вместе. Нормализатор сохраняет `allergenTags` |
| — (точность) | Метка «-free»/«vegan» где угодно в строке и первое вхождение ключа отключали проверку | Проверяются все вхождения; отрицания, «-free», «non-», растительные молочные префиксы относятся только к своему слову; безопасные составные слова (buckwheat, eggplant, дрожжи, морож-) не дают ложной тревоги |
| 028 | Замены rice/quinoa/… выбирались по индексу без проверки | Кандидаты фильтруются по ограничениям; блюдо без безопасных ингредиентов удаляется; готовый fallback проходит gate; если безопасного шаблона нет — `NoSafeNutritionPlanException` |
| 025 | Offline-план клиента не учитывал allergies/diet | `FoodRestrictions` + `FoodRestrictionMatcher` (порт серверного контракта), `OfflineNutritionPlanner` со статическими аллергенами шаблонов и растительным резервом; при отсутствии совместимых блюд — `NoSafeMealPlanException`, ничего не сохраняется. AI/кэш-план перепроверяется по текущему профилю |
| 037 | Неиспользуемый фасад с отдельными словарями терял customTerms | Фасад удалён (production-потребителей нет), его 5 тестов переведены на канонический парсер и gate |
| NEW-CLAUDE-01 | Правки из чата не проверялись | `CoachEditApplicator` отклоняет операцию, если она добавляет запрещённое (уже существующее в блюде не блокирует правку) |

### Затронутые файлы

- Сервер: `AiNutritionSafetyGate.kt` (новый), `AiNutritionSafetyPolicy.kt`, `AiQualityErrorPolicy.kt`, `AiNutritionPlanPolicy.kt`, `AiFallbackFactory.kt`, `CoachEditApplicator.kt`; удалён `nutrition/FoodRestrictionValidator.kt`.
- Клиент (shared): `domain/nutrition/{FoodRestrictions,FoodKeywords,FoodRestrictionMatcher,NoSafeMealPlanException}.kt`, `data/nutrition/OfflineNutritionPlanner.kt` (шаблоны перенесены дословно), `data/repo/NutritionRepositoryDb.kt` (−185 строк).
- Общий контракт: `testdata/food-restriction-cases.tsv` (42 случая RU/EN).
- Тесты: `NutritionSafetyGateTest` (15), `FoodRestrictionContractTest` server (1) и shared (1), `NutritionOfflineSafetyTest` (7), переписанные 5 тестов в `PersonalizationTest`.

### Проверки

- `:server:test` — 129/129; `:shared:testDebugUnitTest` — 59/59; `:shared:compileCommonMainKotlinMetadata`, `:ui:testDebugUnitTest`, `:app-android:assembleDebug` — BUILD SUCCESSFUL. iOS на Windows не собирается (commonMain без платформенных API).
- Сценарии Astra переведены на правильное поведение: 027 (7 частот + 7 калорий, peanut butter) → critical-ошибка аллергена; 026 recipe-only → отклонён, безопасный рецепт проходит; 028 milk+rice → без rice/milk, все кандидаты запрещены → без повторного использования; 025 milk+vegan cold offline → без milk/chicken/salmon, сохраняется и восстанавливается.
- Матрица клиента: 14 наборов ограничений × RU/EN, скрытые аллергены (granola/bulgur/teriyaki/oats), конфликт → отказ без записи в БД, план без ограничений не изменился, устаревший AI-кэш заменяется, совместимый AI-план сохраняется.
- Контракт-тест поймал реальную ложную тревогу до коммита: «buckwheat» ⊃ «wheat».
- Мутационная проверка: откат порядка safety/quality, проверки recipe, gate в чате, фильтра кандидатов 028 и фильтра offline-планировщика — падают соответствующие тесты (сервер 5+1, клиент 5); код восстановлен.
- Реальные LLM/сеть не использовались.

### Риски регрессий и ограничения

- Больше отказов LLM-плана (рецепт, shopping list, теги, точные отрицания) → больше repair-попыток и чаще fallback. Сплит рецепта по «и/and» может дать ложную тревогу на «без молока и орехов» в рецепте — осознанный выбор в сторону безопасности.
- При отказе (`NoSafeNutritionPlanException` / `NoSafeMealPlanException`) сервер отвечает 500, клиент показывает общий `UiState.Error`, онбординг — `GENERATION_FAILED`. Локализованного текста «нет совместимого меню» нет — нужна UI-задача.
- Порции/БЖУ после замен не пересчитываются (шаблоны без граммовок) — зависит от VT-AUD-036.
- Устаревший bootstrap-кэш (`BootstrapCoachData` → `bundle.nutritionPlan`) не перепроверяется на клиенте — зависит от VT-AUD-021.
- Словари ключевых слов дублируются сервер/клиент (общего JVM+iOS модуля нет); расхождение ловит общий TSV-контракт, но только для перечисленных случаев.
- Медицинская полнота словарей (скрытые аллергены в соусах и т.п.) не проверялась.

### Зависимости

- 021 (кэш другой недели/профиля) и 036 (порции) — следующие кандидаты, опираются на этот gate.

## Заблокированные задачи

- Нет.

## Готово к следующей итерации

- По приоритету владельца — неправильное назначение тренировочной нагрузки: группа 9 Astra (VT-AUD-014, 015, 018 — ограничения и fallback тренировок), затем 012/013/021 и 017/022/024. Уточнить по `TODO.md` перед началом.
