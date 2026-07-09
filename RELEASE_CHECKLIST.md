# V-Tempe — Release Checklist

Приоритеты: 🔴 Блокер (P0) · 🟡 Критично (P1) · 🟠 Важно (P2) · 🔵 Желательно (P3)

---

## 🔴 P0 — Блокеры (без этого релиз невозможен)

- [x] **Продакшн URL бэкенда** — ✅ уже настроено (`app-android/build.gradle.kts:42,50`),
  указывает на `https://vtempe-server-eoofh53gda-ew.a.run.app` в debug и release.

- [x] **Задеплоить Ktor-сервер** — ✅ сервер живой на Cloud Run, подтверждено прямыми запросами (2026-07-01/02).

- [x] **Аутентификация на `/ai/*` эндпоинтах** — ✅ `X-App-Token` проверка есть в `Application.kt`
  (`intercept(ApplicationCallPipeline.Plugins)`, сверяет с `APP_SECRET`).

- [x] **Rate limiting на API** — ✅ Ktor `RateLimit` плагин установлен, 30 req/min на `/ai/*`.

- [ ] **Реальная проверка подписки**
  `app-android/src/main/java/com/vtempe/billing/AndroidPurchasesRepository.kt:28` —
  `isSubscriptionActive()` всегда возвращает `false`.
  `shared/src/commonMain/kotlin/com/vtempe/shared/data/di/KoinModule.kt:65` — `StubPurchasesRepository` зарегистрирован в продакшн DI.
  Необходимо: реализовать запрос активных подписок через Play Billing API, зарегистрировать `AndroidPurchasesRepository` в `AppModule`.

---

## 🟡 P1 — Критично (нужно до публикации)

- [x] **Firebase Crashlytics — настройка** — ✅ плагины/зависимости/DI подключены (2026-07-02).
  Плагин `com.google.gms.google-services` + `com.google.firebase.crashlytics` подключаются условно
  (`app-android/build.gradle.kts`) — активируются автоматически, как только в модуль будет положен
  `google-services.json` (зарегистрировать Android-приложение с applicationId `com.vtempe` на
  console.firebase.google.com и скачать файл — это должен сделать владелец аккаунта, не автоматизируется).
  До появления файла сборка остаётся зелёной, `AnalyticsRepository` работает в no-op режиме (лог в Napier).

- [x] **Firebase Analytics — настройка** — ✅ вместе с Crashlytics. События подключены:
  `onboarding_complete` и `plan_generated` (`OnboardingPresenter.kt`), `chat_message_sent` (`ChatPresenter.kt`).
  Полный список — `AnalyticsEvents` в `shared/.../domain/repository/Repositories.kt`.

- [ ] **Убрать Health Connect alpha**
  `libs.versions.toml` — `health-connect = "1.1.0-alpha10"`. Google не пропустит релиз в Play Store с alpha-зависимостью.
  Обновить до стабильной версии или полностью отключить Health Connect до релиза.

- [ ] **Реализовать ввод данных о сне**
  Экран Sleep показывает AI-советы, но нет UI для ввода часов сна и его качества пользователем.
  Без этого "weekly sleep chart" на экране Progress отображает пустые данные.

- [ ] **Проверить Paywall UI с реальной подпиской**
  После реализации `AndroidPurchasesRepository`: пройти полный flow оплаты на тестовом аккаунте Play Console.
  Убедиться, что экран Paywall корректно реагирует на статус active/inactive.

- [ ] **Обработка ошибок сети в UI**
  Проверить все экраны при отсутствии интернета и при таймауте бэкенда.
  У ViewModels должны быть состояния error, а не только loading/success.

- [ ] **Строки локализации — полнота EN/RU**
  ROADMAP.md указывает: "Проверить все строки/форматы в английской локализации после добавления русских подписей."
  Пройти все экраны в EN-локали, убедиться в отсутствии пустых строк.

---

## 🟠 P2 — Важно (желательно до релиза)

- [ ] **Устранить дублирование TDEE/macro логики**
  Функции `tdeeKcal()`, `macrosFor()`, `weeklyMealTemplates()` дословно скопированы в:
  - `shared/src/commonMain/.../data/repo/NutritionRepositoryDb.kt`
  - `shared/src/commonMain/.../data/di/KoinModule.kt` (класс `LocalNutritionRepository`)
  Вынести в общий объект в `shared/domain/` или `shared/data/`.

- [ ] **bundleCache — вынести из companion object**
  `server/src/main/kotlin/.../service/AiService.kt:666-668` — статические `ConcurrentHashMap` в companion object.
  При горизонтальном масштабировании кэш не синхронизируется между инстансами.
  Минимум: перенести в instance-level поля и инжектировать через Koin как singleton.

- [ ] **Удалить мёртвый код из KoinModule**
  `KoinModule.kt` содержит нигде не регистрируемые классы: `InMemoryProfileRepository`, `LocalTrainingRepository`, `LocalNutritionRepository`.
  Это артефакты MVP-стадии, они занимают ~300 строк и вводят в заблуждение.

- [ ] **Базовые unit-тесты для критических use cases**
  Минимум: тесты для TDEE/macro расчётов, `EnsureCoachData`, `BootstrapCoachData`.
  Текущие тесты (`ExampleUnitTest`, `ExampleInstrumentedTest`) — пустые шаблоны.

- [ ] **CI/CD — автоинкремент versionCode**
  `app-android/build.gradle.kts:17` — `versionCode = 1` захардкожен.
  Настроить инкремент через `GITHUB_RUN_NUMBER` или аналог в CI.

- [x] **Настроить ProGuard/R8 для release** — ✅ проверено (2026-07-02): `./gradlew :app-android:assembleRelease`
  проходит зелёным с `isMinifyEnabled = true`. Добавлены keep-правила для Crashlytics-стектрейсов
  (`proguard-rules.pro`). Koin DI, kotlinx.serialization, SQLDelight уже имели свои правила и не сломаны.

- [ ] **ROADMAP.md — починить кодировку**
  Файл содержит битые Cyrillic-символы (Latin-1 вместо UTF-8). Пересохранить в UTF-8.

---

## 🔵 P3 — Желательно (полировка)

- [ ] **iOS — полная валидация в Xcode**
  README прямо указывает: "iOS validation should be done separately." Если планируется iOS-релиз — пройти полный flow на реальном устройстве.

- [ ] **Тесты сервера**
  Нет ни одного теста для LLM-пайплайна, repair/extraction логики, policy-слоёв.
  Минимум: unit-тесты для `JsonSanitizer`, `SchemaValidator`, `AiQualityErrorPolicy`.

- [ ] **Интеграция сна с Health Connect**
  После обновления HC до stable: читать реальные данные о сне из Health Connect вместо мануального ввода.

- [ ] **Экран Progress — реальные данные**
  Проверить, что все графики (weight, sleep, calories) отображают реальные данные, а не нули.
  Добавить UI для ввода веса и калорий если их нет.

- [ ] **Workout feedback → сервер**
  ROADMAP.md: "Пробросить сохранённые отметки/комментарии из тренировки в запросы к Grok."
  Сейчас `WorkoutProgress.notes` и `PerformedSet` собираются, но в `recentWorkouts` попадают только summaries.

- [ ] **Расширенная аналитика прогресса**
  ROADMAP.md: "Обновить экраны Progress и Sleep более подробными метриками (средние, рекомендации)."

- [ ] **Security — HTTPS enforcement**
  `app-android/src/debug/res/xml/network_security_config.xml` разрешает cleartext только для debug.
  Убедиться, что release-сборка не содержит cleartext-исключений.

- [ ] **Размер APK / App Bundle**
  Убедиться, что релиз собирается как AAB (Android App Bundle), а не APK.
  Настроить `splits` или `bundleRelease` task.

- [ ] **Иконка приложения**
  Текущая иконка — стандартный Android launcher. Заменить на брендовую до публикации.

- [ ] **Onboarding skip / повторный вход**
  Проверить: если пользователь уже прошёл онбординг, он не должен проходить его снова при переустановке (профиль восстанавливается из БД).

- [ ] **Календарь с историей**
  Добавить календарь с историей тренировок, питания и сна.

---

## Итоговая сводка по категориям

| Категория | P0 | P1 | P2 | P3 |
|-----------|----|----|----|----|
| Backend/Infrastructure | 1 | 0 | 1 | — |
| Monetization | 1 | 1 | — | — |
| Security | 0 | — | 0 | 1 |
| Features | — | 3 | — | 4 |
| Code Quality | — | — | 2 | 2 |
| Testing | — | — | 1 | 2 |
| Polish/Release | — | 0 | 1 | 4 |

**Минимум для публикации в Play Store:** оставшиеся 5 задач P0+P1 (подписки, Health Connect, ввод сна, paywall-тест, локализация EN).
