# V-Tempe — Release Checklist

Приоритеты: 🔴 Блокер (P0) · 🟡 Критично (P1) · 🟠 Важно (P2) · 🔵 Желательно (P3)

---

## 🔴 P0 — Блокеры (без этого релиз невозможен)

- [ ] **Продакшн URL бэкенда**
  `app-android/build.gradle.kts:18` — заменить `"https://api.example.com"` на реальный задеплоенный адрес.
  В release buildType и в defaultConfig должен стоять один и тот же реальный адрес.

- [ ] **Задеплоить Ktor-сервер**
  Сервер сейчас работает только локально. Без задеплоенного бэкенда приложение нефункционально.
  Минимум: Docker-контейнер на VPS / Railway / Fly.io. Настроить env-переменные (`OPENROUTER_API_KEY` и пр.).

- [ ] **Аутентификация на `/ai/*` эндпоинтах**
  `server/src/main/kotlin/com/vtempe/server/features/ai/api/AiRoutes.kt` — все эндпоинты открыты.
  Добавить минимум: статический API-ключ в заголовке `X-Api-Key` или JWT. Иначе кто угодно сожжёт кредиты OpenRouter.

- [ ] **Rate limiting на API**
  Ktor-плагин `RateLimit` (встроен с Ktor 2.3). Ограничить по IP: не более N запросов в минуту на `/ai/*`.
  `ThrottledLLMClient` защищает только вызовы LLM — HTTP-уровень не защищён.

- [ ] **Реальная проверка подписки**
  `app-android/src/main/java/com/vtempe/billing/AndroidPurchasesRepository.kt:28` —
  `isSubscriptionActive()` всегда возвращает `false`.
  `shared/src/commonMain/kotlin/com/vtempe/shared/data/di/KoinModule.kt:65` — `StubPurchasesRepository` зарегистрирован в продакшн DI.
  Необходимо: реализовать запрос активных подписок через Play Billing API, зарегистрировать `AndroidPurchasesRepository` в `AppModule`.

---

## 🟡 P1 — Критично (нужно до публикации)

- [ ] **Firebase Crashlytics — настройка**
  Зависимость подключена, но `google-services.json` и плагин `com.google.gms.google-services` отсутствуют.
  Без Crashlytics нет мониторинга крашей в продакшне.

- [ ] **Firebase Analytics — настройка**
  Вместе с Crashlytics. Минимальный набор событий: onboarding_complete, plan_generated, chat_message_sent.

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

- [ ] **Настроить ProGuard/R8 для release**
  Проверить, что правила минификации не ломают Koin DI, kotlinx.serialization, SQLDelight.

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

---

## Итоговая сводка по категориям

| Категория | P0 | P1 | P2 | P3 |
|-----------|----|----|----|----|
| Backend/Infrastructure | 3 | 1 | 1 | — |
| Monetization | 1 | 1 | — | — |
| Security | 1 | — | 1 | 1 |
| Features | — | 1 | — | 3 |
| Code Quality | — | — | 3 | 2 |
| Testing | — | — | 1 | 2 |
| Polish/Release | — | 1 | 2 | 4 |

**Минимум для публикации в Play Store:** все задачи P0 + P1 (9 задач).


Добавить календарь с историей тренеровок питания сна 