# V-Tempe: реестр персональных данных

Состояние кода на 2026-10-08. Документ составлен по исходному коду. Это основа для политики
конфиденциальности и уведомления в Роскомнадзор. Пути указаны от корня репозитория. Когда
меняется код, документ нужно обновить.

Сокращения: **ПДн** означает персональные данные, **СК ПДн** означает специальную категорию
(данные о здоровье, ст. 10 152-ФЗ).

---

## 1. Категории данных, которые собирает приложение

| # | Данные | Откуда | Категория | Где в коде |
|---|--------|--------|-----------|------------|
| 1 | Возраст, пол, рост, вес | Онбординг, настройки | ПДн; вес и рост вместе с целями относятся к данным о физическом состоянии | `shared/.../domain/model/Models.kt` (`Profile`), `shared/src/commonMain/sqldelight/.../Profile.sq` |
| 2 | Травмы и ограничения (`constraints.injuries`), медицинские заметки и противопоказания (`constraints.healthNotes`), свободный текст | Онбординг, настройки | **СК ПДн (здоровье)** | `Models.kt` (`Constraints`), столбец `Profile.constraintsJson` |
| 3 | Аллергии и пищевые предпочтения, свободный текст | Онбординг, настройки | Аллергии относятся к **СК ПДн**, предпочтения к обычным ПДн | `ProfileDetails.sq` (`ProfileAllergy`, `ProfileDietPref`) |
| 4 | Цель, опыт, оборудование, расписание тренировок, уровень бытовой активности, бюджет на питание, фокус, длительность занятия, выбранный тренер | Онбординг, настройки | ПДн | `Profile.sq`, `ProfileDetails.sq` |
| 5 | Журнал тренировок: выполненные подходы, веса, повторы, RPE, заметки к тренировке | Экран тренировки | ПДн (физическое состояние) | `shared/.../data/repo/WorkoutProgressStore.kt` |
| 6 | Сон: даты, длительность, заметки к ночи (до 500 символов) | Экран сна | ПДн, заметки могут содержать СК ПДн | `SleepStore.kt`, `MAX_SLEEP_NOTE_LENGTH` в `Models.kt` |
| 7 | История веса (дата и вес) | Еженедельный чек-ин | ПДн | `WeightStore.kt` |
| 8 | Сообщения чата с AI-тренером | Экран чата | ПДн, пользователь может писать о здоровье | `ChatHistoryStore.kt`, `ui/.../presenter/ChatPresenter.kt` |
| 9 | Аккаунт Google: Firebase UID, e-mail, имя, фото | Вход через Google (Firebase Auth) | ПДн, прямые идентификаторы | `shared/.../domain/repository/Repositories.kt` (`AuthUser`), `app-android/.../auth/FirebaseAuthRepository.kt` |
| 10 | Подписка: срок действия, источник оплаты, сумма и валюта платежа, id платежа у провайдера | Google Play Billing, вебхук ЮKassa | ПДн, связанные с UID | `server/.../entitlement/data/db/EntitlementTables.kt` |
| 11 | Настройки: язык, единицы, режим AI (free/paid), согласие на аналитику | Настройки | Технические | `shared/.../data/repo/PreferencesRepository.kt` |
| 12 | IP-адрес, User-Agent | Каждый HTTP-запрос к серверу | ПДн (технические) | Cloud Run (журналы запросов), `server/.../app/Application.kt` (ключ rate-limit) |
| 13 | Технические данные устройства для аналитики и отчётов о сбоях | Firebase SDK | ПДн (псевдонимные идентификаторы) | `app-android/.../analytics/FirebaseAnalyticsRepository.kt` |

Чего приложение **не** собирает: геолокацию, контакты, фото и камеру, рекламный идентификатор
(разрешение `AD_ID` удалено из манифеста, см. п. 5), полные данные банковских карт.

---

## 2. Где хранятся данные и сколько

### 2.1 Устройство пользователя

| Хранилище | Что в нём | Срок хранения |
|-----------|-----------|---------------|
| SQLite `app_v2.db` (`app-android/.../di/AppModule.kt`, схемы в `shared/src/commonMain/sqldelight/`) | Профиль, включая травмы и медзаметки (`constraintsJson`), оборудование, аллергии, предпочтения, расписание; сгенерированные тренировки и меню | Пока пользователь не сбросит данные или не удалит приложение. Автоматической очистки нет |
| `Settings` (SharedPreferences) | История чата: последние 30 сообщений (`ChatHistoryStore.MAX_MESSAGES`); кэш ответов AI (`AiResponseCache.kt`); сон: 14 записей и заметки к ним (`SleepStore.kt`); вес: 30 записей (`WeightStore.kt`); прогресс тренировок; UID владельца локальных данных (`SettingsLocalDataOwnerStore.kt`); настройки | Скользящее окно, где лимит задан. Остальное хранится до сброса или удаления приложения |
| Firebase Auth SDK | Сессия Google-аккаунта | До выхода из аккаунта |

Резервное копирование Android отключено (`android:allowBackup="false"`), поэтому эти данные
не попадают в облачные бэкапы Google.

### 2.2 Сервер V-Tempe (Google Cloud Run, регион `europe-west1`, `cloudbuild.yaml`)

> Важно для 152-ФЗ: сервер и его база данных находятся **за пределами РФ**. Всё, что
> отправляется на сервер, является трансграничной передачей. Кроме того, это затрагивает
> требование о локализации (ч. 5 ст. 18 152-ФЗ). См. п. 7.

| Хранилище | Что в нём | Ключ | Срок хранения |
|-----------|-----------|------|---------------|
| БД, таблица `sync_blobs` (`server/.../sync/data/db/SyncTables.kt`) | JSON-снимки доменов `profile` (весь профиль, включая травмы, медзаметки и аллергии), `workoutProgress`, `sleep`, `sleepNotes`, `weight`. Каждый снимок до 256 КБ | Firebase UID | Бессрочно. Хранится последняя версия каждого домена, старая перезаписывается. Удаление выполняется вместе с удалением аккаунта (`DELETE /me`, разрабатывается отдельно) |
| БД, таблица `entitlements` (`EntitlementTables.kt`) | UID, дата окончания подписки, источник, внешний id | Firebase UID | Бессрочно |
| БД, таблица `payments` (`EntitlementTables.kt`) | UID, источник, id платежа, сумма, валюта, `raw_payload`, время | Firebase UID | Бессрочно (append-only журнал). Для ЮKassa `raw_payload` теперь содержит только проверенные поля: id, status, paid, amount, metadata (`YooKassaLedgerPayload.kt`). Данные карты и авторизации не сохраняются |
| Память процесса: `BundleCache` (`server/.../ai/data/service/BundleCache.kt`) | Сгенерированные планы | SHA-256 от профиля | 30 минут, для запасного (fallback) плана 2 минуты. Сбрасывается при перезапуске |
| Память процесса: rate-limit (`Application.kt`) | IP-адрес клиента | IP | Около 1 минуты (окно лимита) |
| Файлы `logs/llm/*.txt` (`server/.../ai/data/llm/telemetry/LlmRawStore.kt`) | Сырые ответы модели, которые могут пересказывать данные о здоровье | Хэш профиля, номер попытки | **По умолчанию выключено** (`LLM_RAW_STORE_ENABLED`, в продакшене не включается). Если включено, файлы старше `LLM_RAW_STORE_RETENTION_HOURS` (по умолчанию 24 ч) удаляются при старте и не реже раза в 10 минут при записи. На Cloud Run файловая система эфемерна |
| Журналы приложения (stdout, затем Cloud Logging) | См. п. 2.3 | | Определяется настройкой Cloud Logging. Для бакета `_Default` по умолчанию 30 дней. Это настройка GCP, а не кода |
| Журналы запросов Cloud Run | IP, User-Agent, метод, путь, статус, время | | Так же, как у Cloud Logging |

### 2.3 Что попадает в серверные журналы

- `LlmErrorTracker` (`.../telemetry/LlmErrorTracker.kt`): операция, `requestId`, номер попытки,
  стадия, текст ошибки. Фрагменты сырого ответа модели **по умолчанию не пишутся**
  (`PipelineConfig.rawSnippetLimit = 0`). Включить их можно переменной окружения `LLM_LOG_SNIPPET_CHARS`.
  То же правило действует для текста `LlmPipelineExhaustedException`.
- `requestId` для планов: SHA-256 от JSON профиля плюс неделя и локаль (`AiService.cacheKey`).
  Для чата: количество сообщений и хэш последнего сообщения. Это псевдонимы, по которым нельзя
  напрямую определить человека.
- `AiQualityMetrics.recordValidation`: ошибки валидации плана. Они могут содержать названия
  блюд и ингредиентов, а также запрещённый термин из списка аллергий пользователя
  (например, `forbidden term 'арахис' in '...'`). UID и e-mail в этих записях отсутствуют.
- Вебхук ЮKassa (`server/.../payments/yookassa/api/YooKassaWebhookRoutes.kt`): id платежа и
  Firebase UID при выдаче подписки.
- `/ai/*` запросы на сервере не связываются с UID: Firebase-токен там не проверяется и не
  логируется (`Application.kt`).

---

## 3. Что уходит в OpenRouter (США) при каждом AI-вызове

Сервер вызывает `https://openrouter.ai/api/v1/chat/completions`
(`server/.../ai/data/llm/OpenRouterLLMClient.kt`). OpenRouter передаёт запрос провайдеру
выбранной модели, а это отдельный обработчик: для генерации планов это Anthropic, для чата
основная модель и запасная бесплатная модель. Модели задаются переменными окружения
`OPENROUTER_*`, см. `server/.../app/di/ServerModule.kt`.

**Ни в одном вызове не передаются** UID, e-mail, имя, фото, IP или User-Agent пользователя,
Firebase-токен, данные подписки или оплаты. Поле `user` OpenRouter не используется. Заголовки
запроса содержат только ключ API сервера, `HTTP-Referer` (адрес проекта) и `X-Title` (название
приложения).

Перед построением любого промпта свободный текст проходит через `DirectIdentifierRedactor`
(`server/.../ai/data/privacy/DirectIdentifierRedactor.kt`). Он заменяет e-mail, номера
телефонов (`+…` и российский формат `8XXXXXXXXXX`) и @-никнеймы на `[email]`, `[phone]` и
`[handle]`. Очищаются поля equipment, dietaryPreferences, allergies, injuries, healthNotes,
заметки к тренировкам и сну, а также все сообщения чата. Редактор вызывается в
`AiService.fetchBundle` и `ChatService.chat`, через эти два места проходят все обращения к LLM.
Имена, написанные свободным текстом, автоматически не распознаются.

### 3.1 Планы: `/ai/bootstrap`, `/ai/training`, `/ai/nutrition`, `/ai/sleep`

Промпты строятся в `CoachBundlePromptBuilder.kt` (монолитный запрос) и `SectionPromptBuilders.kt`
(раздельные запросы для тренировок, питания и сна). В каждый промпт входят:

- JSON профиля (`AiProfile`, `server/.../shared/dto/profile/AiProfile.kt`): age, sex, heightCm,
  weightKg, goal, experienceLevel, equipment, dietaryPreferences, **allergies**, **injuries**,
  **healthNotes**, weeklySchedule, lifestyleActivity, locale, budgetLevel, trainingMode,
  coachTrainerId, llmMode, trainingFocus, sessionDurationMins, splitPreference;
- `recentWorkouts`: до 6 последних тренировок (дата, процент выполнения, объём, средний RPE,
  заметки, веса и повторы по упражнениям);
- `sleepHistory`: до 7 ночей (дата, длительность, заметка до 500 символов);
- `recentWeights`: до 8 замеров (дата, вес);
- сводка тех же фактов текстом, сегодняшняя дата, правила и каталог упражнений.

Медицинские поля нужны модели, чтобы исключить опасные упражнения и аллергены. Без них качество
и безопасность плана падают, поэтому они передаются намеренно.

### 3.2 Чат: `/ai/chat`

Промпт строится в `ChatService.buildChatPrompt`. Он содержит тот же JSON профиля и сводку
(раздел 3.1), последние 8 сообщений истории (каждое обрезано до 400 символов) и последнее
сообщение пользователя целиком, а также текущие недельные планы тренировок и питания.
Клиент присылает на сервер всю локальную историю (до 30 сообщений), но в OpenRouter уходит
только описанное окно.

### 3.3 Хранение у OpenRouter и провайдеров

Код не управляет хранением на стороне OpenRouter и провайдеров моделей. Оно определяется
настройками аккаунта OpenRouter (логирование промптов) и политиками провайдеров, см. п. 7.

---

## 4. Что уходит на сервер V-Tempe с устройства

| Запрос | Данные | Код |
|--------|--------|-----|
| `POST /ai/{training,nutrition,sleep,bootstrap}` | `AiProfileDto`: все поля раздела 3.1, плюс weekIndex и locale. Заголовки `X-App-Token` и `Authorization: Bearer <Firebase ID token>` | `shared/.../data/network/dto/CoachDtos.kt`, `Api.kt` |
| `POST /ai/chat` | `ChatProfileDto`, история чата, текущие планы, те же заголовки | `shared/.../data/repo/NetworkChatRepository.kt` |
| `PUT /me/sync/{domain}`, `GET /me/sync` | Снимки профиля, прогресса, сна, заметок к сну и веса. Авторизация по Firebase ID token | `shared/.../data/repo/NetworkSyncRepository.kt`, `server/.../sync/api/SyncRoutes.kt` |
| `GET /me/entitlement` | Firebase ID token | `server/.../entitlement/api/EntitlementRoutes.kt` |

Firebase ID token содержит claims Google-аккаунта (UID, e-mail, имя). Сервер извлекает из него
только UID (`FirebaseTokenVerifier.kt`) и сам токен не сохраняет.

---

## 5. Firebase Analytics и Crashlytics (Google, США)

Код: `app-android/.../analytics/FirebaseAnalyticsRepository.kt`, обёртка
`shared/.../data/analytics/ConsentGatedAnalyticsRepository.kt`, согласие хранится в
`PreferencesRepository` (`prefs.analyticsConsent`, по умолчанию `false`).

**Analytics работает только при согласии пользователя** (флажок в онбординге и в настройках).

- Без согласия автоматический сбор выключен на уровне манифеста
  (`firebase_analytics_collection_enabled=false`). События и свойства пользователя не
  отправляются, это проверяет `ConsentGatedAnalyticsRepository` при каждом вызове. При отзыве
  согласия вызываются `setAnalyticsCollectionEnabled(false)` и `resetAnalyticsData()`.
- Сбор рекламного ID отключён (`google_analytics_adid_collection_enabled=false`), разрешения
  `AD_ID`, `ACCESS_ADSERVICES_AD_ID` и `ACCESS_ADSERVICES_ATTRIBUTION` удалены из манифеста.
- При согласии отправляются:
  - события без параметров (`AnalyticsEvents` в `Repositories.kt`): `onboarding_complete`,
    `plan_generated`, `chat_message_sent`. Константы `workout_completed`, `paywall_shown` и
    `subscription_purchased` объявлены, но в коде не вызываются;
  - свойства пользователя, только **сгруппированные** (`SyncAnalyticsProfile` в
    `shared/.../domain/usecase/UseCases.kt`): `coach_trainer_id`, `gender`, `age_bucket`,
    `height_bucket`, `weight_bucket`, `budget_level`, `training_focus`, `goal`;
  - автоматические события Firebase (first_open, session_start, user_engagement) с
    идентификатором экземпляра приложения, моделью устройства, ОС, версией приложения и
    страной по IP.
- `setUserId` не вызывается: Firebase UID и e-mail в Analytics не передаются.

**Crashlytics не зависит от согласия на аналитику.** Он собирает падения приложения и нефатальные
ошибки вместе с идентификатором установки, моделью устройства, ОС и версией приложения.

- Нефатальные ошибки (`recordNonFatal`) сейчас вызываются только в `ChatPresenter` при ошибке
  отправки сообщения. В отчёт попадают тип исключения, стек, причина (`Reason`) и HTTP-код.
  Тексты исключений вырезаются (`withoutMessages()` в `FirebaseAnalyticsRepository.kt`),
  потому что в них могут оказаться тело ответа сервера или фрагмент ответа AI.
- Custom keys и `setUserId` в Crashlytics не используются.
- Для фатальных падений Crashlytics записывает сообщение исключения как есть, код этим не
  управляет.

Сроки хранения на стороне Google задаются политиками Firebase: Analytics хранит данные на уровне
пользователя столько, сколько указано в настройке проекта (2 или 14 месяцев), Crashlytics
хранит отчёты 90 дней. Это настройки консоли, а не кода.

---

## 6. Прочие обработчики

| Обработчик | Данные | Где |
|------------|--------|-----|
| Google Firebase Authentication (США) | Google-аккаунт: UID, e-mail, имя, фото | `FirebaseAuthRepository.kt` |
| Google Play Billing | Покупка подписки, обрабатывает Google | `app-android/.../billing/AndroidPurchasesRepository.kt` |
| ЮKassa (РФ) | Платёж. Сервер только перезапрашивает его по id. В `metadata` передаётся Firebase UID и срок | `server/.../payments/yookassa/` |
| Google Cloud (Cloud Run, Cloud Logging, БД через `DATABASE_URL`) | Всё серверное хранение из п. 2.2 | `cloudbuild.yaml`, `DatabaseFactory.kt` |
| OpenRouter и провайдеры моделей (США) | Промпты из п. 3 | `OpenRouterLLMClient.kt` |

---

## 7. Открытые вопросы для политики и юриста (код их не решает)

1. **Локализация (ч. 5 ст. 18 152-ФЗ).** Первичная запись и хранение ПДн граждан РФ должны
   идти в БД на территории РФ. Сейчас профиль, включая данные о здоровье, синхронизируется
   напрямую в БД в регионе `europe-west1`, а в РФ базы нет.
2. **Трансграничная передача (ст. 12).** OpenRouter и провайдеры моделей (США), Firebase (США),
   GCP (ЕС). Нужны уведомление РКН и письменное согласие на передачу СК ПДн
   (ст. 10 и ст. 12 в ред. 2022 г.).
3. **Согласие на обработку данных о здоровье.** Отдельного явного согласия на СК ПДн и на их
   передачу AI-провайдеру в коде нет. Есть только согласие на аналитику.
4. **Настройки OpenRouter.** Нужно проверить, что логирование промптов в аккаунте выключено.
   Также стоит рассмотреть параметр маршрутизации `provider.data_collection = "deny"`. Это
   может исключить часть моделей, особенно бесплатные, поэтому в коде не включено.
5. **Сроки хранения на сервере.** Для `sync_blobs`, `entitlements` и `payments` автоматического
   срока нет. Срок хранения платёжных записей нужно согласовать с требованиями учёта.
   В `payments.raw_payload` у записей, сделанных до этого изменения, остаётся полный
   вебхук ЮKassa (тип карты, первые 6 и последние 4 цифры, срок действия).
6. **Crashlytics без согласия.** Это нужно описать в политике как необходимую диагностику
   или включить под отдельное согласие.
7. **Firebase ID token на `/ai/*`.** Клиент прикладывает токен к `/ai/*` (`Api.kt`), хотя сервер
   его там не использует. Токен не логируется и не уходит дальше сервера.
