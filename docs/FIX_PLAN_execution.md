# V-Tempe — План исправлений (executable)

Инструкции для исполнителя. Каждая задача самодостаточна: файл, что менять, как проверить, чего НЕ трогать. Выполнять по порядку внутри волны. После каждой задачи — указанная проверка; не переходить к следующей, пока проверка не зелёная.

**Общие правила для исполнителя:**
- Работать только в воркового дереве `amazing-northcutt-fc6005`, не в root.
- Один коммит на задачу (или на логическую группу внутри волны). Сообщение коммита — по шаблону в конце задачи.
- Не рефакторить «заодно» соседний код. Только то, что в задаче.
- После правки Kotlin-кода прогонять `./gradlew :server:compileKotlin -q` (для server) или соответствующий модуль. После правок server-логики — `./gradlew :server:test -q`.
- Если проверка не проходит — остановиться и сообщить, не «чинить наугад».
- Секреты (реальные значения токенов/ключей) никогда не вставлять в код, коммиты, логи.

Легенда приоритета: 🔴 критично · 🟡 важно · 🟠 средне · 🔵 полировка.

---

## ВОЛНА 0 — Хотфиксы (один день, минимальный риск регрессии)

**✅ ЗАВЕРШЕНА (2026-07-13).** Все пункты 0.1–0.7 сделаны, каждый — отдельным коммитом в `main`, проверен компиляцией/тестами, часть прогнана через ревью Fable 5. Единственное сознательное отклонение: 0.1 использует env-флаг `LLM_RAW_STORE_ENABLED` вместо предложенного в плане `AI_RAW_STORE_ENABLED` — функционально то же самое, имя выбрано до чтения этого файла. История git (старые PII-логи в прошлых коммитах) НЕ чистилась, как и предписано — это отдельное решение владельца. Коммиты: `bc8d258`, `e52d5d4`, `33ba43e`, `a4ea0ef`, `769cfa9`.

Порядок внутри волны свободный, кроме 0.1 (сначала прекратить утечку, потом чистить историю).

### 0.1 🔴 Прекратить запись PII-логов и убрать их из git-индекса

**Файлы:**
- `server/src/main/kotlin/com/vtempe/server/app/di/ServerModule.kt` (строки 172, 176)
- `.gitignore` (корень репо)

**Шаг A — выключить запись по умолчанию.** В `ServerModule.kt`:
- Строка 172: было `single { LlmRawStore(enabled = true) }`. Заменить на:
  ```kotlin
  single { LlmRawStore(enabled = Env["AI_RAW_STORE_ENABLED"]?.equals("true", ignoreCase = true) ?: false) }
  ```
- Строка 176: было `single { PipelineConfig(maxAttempts = 3, enableRawStore = true) }`. Заменить `enableRawStore = true` на:
  ```kotlin
  single { PipelineConfig(maxAttempts = 3, enableRawStore = Env["AI_RAW_STORE_ENABLED"]?.equals("true", ignoreCase = true) ?: false) }
  ```
- Убедиться, что `import com.vtempe.server.config.Env` уже есть в файле (он есть — строка 3). Не добавлять дубликат.

**Шаг B — убрать логи из git и заигнорить.** В `.gitignore` добавить в конец блока с логами (рядом со строкой `*.log`):
```
# LLM debug dumps — may contain user profile/health data, never commit
/server/logs/
/server/server/logs/
```
Затем выполнить (в терминале воркового дерева):
```
git rm -r --cached server/server/logs
```
(если путь `server/logs` тоже отслеживается — повторить для него; проверить `git ls-files | grep -i logs/llm`).

**Проверка:**
- `git status` показывает удаление отслеживаемых файлов логов и модификацию `.gitignore`.
- `./gradlew :server:compileKotlin -q` — без ошибок.
- Локально запустить сервер без переменной `AI_RAW_STORE_ENABLED` → каталог `server/logs/llm` не должен пополняться новыми файлами при запросе.

**ВАЖНО про историю git:** файлы всё ещё в истории коммитов. Полная чистка истории (`git filter-repo`) — отдельная операция, требует решения владельца (переписывает историю, нужен force-push). НЕ делать её в рамках этой задачи автоматически — только удаление из индекса + gitignore. Отметить в отчёте, что чистка истории остаётся за владельцем.

**Коммит:** `fix(server): stop writing PII debug dumps by default, untrack log dir`

---

### 0.2 🔴 Constant-time сравнение токена + fail-closed в проде

**Файл:** `server/src/main/kotlin/com/vtempe/server/app/Application.kt` (строки 51-64)

**Что сделать:**
1. Вверху файла добавить импорт (в блок импортов, по алфавиту рядом с другими `java.*` нет — просто добавить):
   ```kotlin
   import java.security.MessageDigest
   ```
2. Заменить проверку токена. Было (строки 55-64):
   ```kotlin
   intercept(ApplicationCallPipeline.Plugins) {
       val path = call.request.uri
       if (path.startsWith("/ai/") && appSecret != null) {
           val token = call.request.headers["X-App-Token"]
           if (token != appSecret) {
               call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
               finish()
           }
       }
   }
   ```
   Стало:
   ```kotlin
   intercept(ApplicationCallPipeline.Plugins) {
       val path = call.request.uri
       if (path.startsWith("/ai/") && appSecret != null) {
           val token = call.request.headers["X-App-Token"]
           val expected = appSecret.toByteArray(Charsets.UTF_8)
           val provided = (token ?: "").toByteArray(Charsets.UTF_8)
           if (!MessageDigest.isEqual(expected, provided)) {
               call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
               finish()
           }
       }
   }
   ```
   (`MessageDigest.isEqual` в современных JDK constant-time.)

**Fail-closed — отдельное решение, НЕ автоматизировать здесь.** Сейчас при отсутствии `APP_SECRET` API открыт (строки 51-54, только warning). Менять это на «падать при старте» — рискованно для локальной разработки. Оставить как есть, но в отчёте отметить: «в проде `APP_SECRET` задаётся через Cloud Run secrets (cloudbuild.yaml подтверждает), поэтому прод не открыт; локальный fallback оставлен намеренно».

**Проверка:** `./gradlew :server:compileKotlin -q` без ошибок. Локальный запрос с верным токеном → 200, с неверным → 401.

**Коммит:** `fix(server): use constant-time comparison for app token`

---

### 0.3 🔴 Инъективный ключ кэша (SHA-256 вместо hashCode)

**Файл:** `server/src/main/kotlin/com/vtempe/server/features/ai/data/service/AiService.kt` (метод `cacheKey`, строки ~752-755)

**Проблема:** `fingerprint.hashCode()` — 32 бита, при коллизии разные профили получают один ключ → пользователь получает чужой план.

**Что сделать:**
1. Добавить импорт вверху файла: `import java.security.MessageDigest`
2. Заменить тело `cacheKey`:
   ```kotlin
   private fun cacheKey(profile: AiProfile, weekIndex: Int, localeTag: String): String {
       val fingerprint = json.encodeToString(AiProfile.serializer(), profile)
       val digest = MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray(Charsets.UTF_8))
       val hex = digest.joinToString("") { "%02x".format(it) }
       return "$hex|$weekIndex|$localeTag"
   }
   ```

**Проверка:**
- `./gradlew :server:compileKotlin -q` без ошибок.
- `./gradlew :server:test -q` — все существующие тесты зелёные (кэш-ключ не завязан на конкретный формат в тестах, но убедиться).
- Добавить тест в `server/src/test/kotlin/com/vtempe/server/PersonalizationTest.kt`: два разных профиля (разный `weightKg`) → `cacheKey` даёт разные строки. **Но `cacheKey` приватный** — сделать его `internal` (заменить `private fun cacheKey` на `internal fun cacheKey`), чтобы тест видел, ИЛИ проверять косвенно. Если делаешь `internal` — отметить в коммите. Тест:
  ```kotlin
  @Test fun `different profiles produce different cache keys`() {
      val a = profile(weightKg = 70.0); val b = profile(weightKg = 90.0)
      // вызвать AiService.cacheKey через отражение или сделать internal — выбрать проще
  }
  ```
  Если инъекция теста сложна — достаточно компиляции + ручной проверки логики; отметить в отчёте что юнит-тест ключа отложен.

**Коммит:** `fix(server): use SHA-256 for cache key to prevent profile collisions`

---

### 0.4 🟡 Убрать открытый CORS

**Файл:** `server/src/main/kotlin/com/vtempe/server/app/Application.kt` (строка 36)

**Что сделать:** удалить строку `install(CORS) { anyHost() }` целиком. Если после удаления остаётся неиспользуемый импорт `io.ktor.server.plugins.cors.routing.*` (строка 15) — удалить и его.

**Обоснование:** бэкенд обслуживает только нативные мобильные клиенты, они не подчиняются CORS и не шлют Origin. Плагин только расширяет поверхность атаки.

**Проверка:** `./gradlew :server:compileKotlin -q` без ошибок. Локальный запрос с мобильного клиента (или curl без Origin) по-прежнему работает.

**Коммит:** `fix(server): remove permissive CORS anyHost (mobile-only backend)`

---

### 0.5 🟡 Установить XForwardedHeaders (корректный IP за прокси)

**Файл:** `server/src/main/kotlin/com/vtempe/server/app/Application.kt`

**Что сделать:** перед `install(RateLimit)` (сейчас строка 42) добавить:
```kotlin
install(XForwardedHeaders)
```
и импорт вверху:
```kotlin
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
```
Проверить, что артефакт `ktor-server-forwarded-header` доступен — если компиляция не находит `XForwardedHeaders`, добавить зависимость в `server/build.gradle.kts` (`io.ktor:ktor-server-forwarded-header-jvm`, версия из `libs.versions.toml` ktor=2.3.12). Если добавляешь зависимость — отметить в коммите.

**Обоснование:** за Cloud Run без этого `origin.remoteHost` — это IP прокси, и rate-limit по IP схлопывается на всех пользователей.

**Проверка:** `./gradlew :server:compileKotlin -q` без ошибок.

**Коммит:** `fix(server): install XForwardedHeaders so rate-limit keys on real client IP`

---

### 0.6 🟡 Перевести 5 недостающих строк на русский

**Файл:** `ui/src/commonMain/composeResources/values-ru/strings.xml`

**Что сделать:** добавить 5 ключей, которых нет в RU (они есть в `values/strings.xml`). Взять английские значения из `values/strings.xml` (найти по `grep name="settings_ai_model`) и перевести:
- `settings_ai_model_title` → «Модель ИИ»
- `settings_ai_model_paid` → «Платная (умнее)»
- `settings_ai_model_free` → «Бесплатная (быстрее)»
- `settings_ai_model_current_paid` → «Сейчас: платная»
- `settings_ai_model_current_free` → «Сейчас: бесплатная»
(Точные формулировки сверить с тоном соседних строк в файле; сохранить формат `<string name="...">...</string>`.)

**Проверка:** повторить сравнение ключей — списки EN и RU должны совпасть:
```
comm -23 <(grep -oE 'name="[^"]+"' ui/src/commonMain/composeResources/values/strings.xml | sort -u) <(grep -oE 'name="[^"]+"' ui/src/commonMain/composeResources/values-ru/strings.xml | sort -u)
```
Вывод должен быть пустой.

**Коммит:** `fix(l10n): add missing Russian translations for AI model settings`

---

### 0.7 🔵 Убрать мёртвую Health Connect + удалить заглушку iOS + поправить чек-лист

**Файлы:** `gradle/libs.versions.toml`, `iosApp/Vtempe/Vtempe/ContentView.swift`, `RELEASE_CHECKLIST.md`

**Что сделать:**
1. В `libs.versions.toml` удалить строку 22 (`healthConnect = "1.1.0-alpha10"`) и строку 95 (`health-connect = ...`). **Сначала проверить**, что нигде не используется: `grep -rn "libs.health" --include=*.kts .` должно быть пусто (уже проверено — пусто). Если вдруг найдётся — не удалять, остановиться.
2. Удалить файл `iosApp/Vtempe/Vtempe/ContentView.swift` целиком (это дефолтная заглушка Xcode «Hello, world!», нигде не используется; точка входа — `VtempeApp.swift`).
3. В `RELEASE_CHECKLIST.md` поправить два неверных пункта:
   - «Убрать Health Connect alpha» — переписать в «✅ Health Connect не подключён (была мёртвая запись в каталоге, удалена) — не блокирует Play Store».
   - «Реализовать ввод данных о сне» — переписать в «✅ Ввод сна реализован (SleepPresenter.logSleep + SleepStore); при желании — добавить onboarding-подсказку».

**Проверка:** проект собирается (`./gradlew :app-android:compileDebugKotlin -q` или полный build, если быстро). Grep по `healthConnect`/`health-connect` в `.kts` — пусто.

**Коммит:** `chore: remove dead Health Connect dep and iOS placeholder, correct release checklist`

---

## ВОЛНА 1 — Обработка ошибок и качество (ближайший рефакторинг)

### 1.1 🟡 Клиент: состояние ошибки на Home + кнопка «повторить»

**Файлы:**
- `ui/src/commonMain/kotlin/com/vtempe/ui/presenter/HomePresenter.kt`
- `ui/src/commonMain/kotlin/com/vtempe/ui/screens/HomeScreen.kt`
- строки RU/EN для текста ошибки (`strings.xml` обеих локалей)

**Что сделать:**
1. В `HomeState` (строки 27-40) добавить поле:
   ```kotlin
   val errorMessage: String? = null,
   ```
2. В `HomePresenterDelegate`, в обоих `.catch { Napier.e(...) }` (строки 79, 87) дополнительно выставлять состояние:
   ```kotlin
   .catch { e -> Napier.e("HomePresenter workouts error", e); _state.update { it.copy(errorMessage = "load_failed", loading = false) } }
   ```
   (использовать строковый ключ, не хардкод текста; текст резолвить в UI через `stringResource`).
3. В `refresh()` и `init`-блоке при успешной загрузке сбрасывать `errorMessage = null`.
4. В `HomeScreen.kt` — если `uiState.errorMessage != null` и данных нет, показать баннер с текстом (новый строковый ресурс `home_load_error` + `common_retry`) и кнопкой, вызывающей `presenter.refresh()`.
5. Добавить строки `home_load_error` / `common_retry` в обе локали.

**Проверка:** `./gradlew :ui:compileDebugKotlin` (или сборка app-android). Ручная проверка живьём — в Волне 3.

**Коммит:** `feat(ui): surface load errors on Home with retry action`

---

### 1.2 🟠 Единый компонент ErrorState(onRetry) и применить где ошибка без действия

**Файлы:** `ui/src/commonMain/kotlin/com/vtempe/ui/screens/NutritionScreen.kt` (строки ~76-112) + новый общий компонент.

**Что сделать:**
1. Создать переиспользуемый composable `ErrorState(title, subtitle, onRetry)` (положить рядом с другими общими компонентами UI — например в `WorkoutUiComponents.kt` или отдельный файл `ui/.../screens/ErrorState.kt`).
2. В `NutritionScreen.kt` в ветке `is UiState.Error` (строки 76-81) использовать `ErrorState` с `onRetry`, пробросив в presenter метод перезагрузки (проверить, есть ли у `NutritionPresenter` метод refresh/reload; если нет — добавить, дергающий тот же источник данных).
3. Пройтись по остальным экранам, где обрабатывается `is UiState.Error` (grep дал: Chat, NutritionDetail, ShoppingList, Settings) — где у ошибки нет действия, добавить retry.

**НЕ трогать** Splash/Onboarding — там другая модель.

**Проверка:** компиляция ui-модуля. Живая проверка — Волна 3.

**Коммит:** `feat(ui): unify error states with retry action across screens`

---

### 1.3 🟠 Сервер: типизированные ошибки LLM вместо string-sniffing

**Файлы:**
- `server/src/main/kotlin/com/vtempe/server/features/ai/data/llm/OpenRouterLLMClient.kt` (метод `shouldTryNextModel` и место, где бросается исключение по HTTP-статусу)
- `server/src/main/kotlin/com/vtempe/server/features/ai/data/service/AiService.kt` (`shouldFallbackToFree`, `shouldAttemptDecomposedGeneration`, строки 780-804)
- `server/src/main/kotlin/com/vtempe/server/features/ai/data/service/ChatService.kt` (дубликат `shouldFallbackToFree`, строки ~236-247)

**Что сделать (поэтапно, с сохранением совместимости):**
1. Создать sealed-иерархию (новый файл `server/.../features/ai/data/llm/LlmException.kt`):
   ```kotlin
   sealed class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause) {
       class RateLimited(val retryAfterMs: Long? = null, cause: Throwable? = null) : LlmException("rate limited", cause)
       class PaymentRequired(cause: Throwable? = null) : LlmException("payment required", cause)
       class Auth(val status: Int, cause: Throwable? = null) : LlmException("auth error $status", cause)
       class Timeout(cause: Throwable? = null) : LlmException("timeout", cause)
       class Provider(val status: Int?, message: String, cause: Throwable? = null) : LlmException(message, cause)
   }
   ```
   (Существующий `RateLimitException` можно оставить или заменить — если заменяешь, обновить все использования; проще оставить `RateLimitException` и добавить остальные типы. Выбрать один подход, отметить в коммите.)
2. В `OpenRouterLLMClient` в месте разбора HTTP-ответа маппить статус в эти типы и бросать их. **Сохранить текст сообщения** (чтобы старые string-проверки продолжали работать в переходный период).
3. Переписать `shouldFallbackToFree` на проверку типов:
   ```kotlin
   private fun shouldFallbackToFree(error: Throwable): Boolean = when (error) {
       is LlmException.RateLimited, is LlmException.PaymentRequired,
       is LlmException.Auth, is LlmException.Timeout -> true
       is RateLimitException -> true
       else -> {
           // переходный fallback на старую строковую проверку — удалить после стабилизации
           val m = error.message?.lowercase().orEmpty()
           m.contains(" 429") || m.contains("rate limit") || m.contains("timeout") || m.contains("timed out")
       }
   }
   ```
4. **Дедупликация:** вынести `shouldFallbackToFree` в общее место (например top-level функция в новом `LlmClientRouter.kt` или companion), чтобы `AiService` и `ChatService` использовали одну. Не оставлять две копии.

**Проверка:** `./gradlew :server:compileKotlin -q` + `./gradlew :server:test -q`. Добавить тест: для каждого типа ошибки `shouldFallbackToFree` возвращает ожидаемое.

**Коммит:** `refactor(server): type LLM errors instead of string matching; dedup fallback policy`

---

### 1.4 🟠 Сервер: не глотать неожиданные исключения как «LLM подтормозил»

**Файлы:** `AiService.kt` (верхнеуровневые `runCatching { ... }.getOrElse { fallback }`), `ChatService.kt`

**Что сделать:** в местах, где сейчас ловится любой `Throwable` и подставляется fallback, сузить до ожидаемых типов из 1.3 (`LlmException.*`, `RateLimitException`, таймауты, ошибки декода/валидации). Неожиданные исключения (`NullPointerException`, `SerializationException` в неожиданном месте и т.п.) — пробрасывать наверх, чтобы вернулся 500 и попал в лог как ошибка, а не деградация.

**Осторожно:** это меняет поведение при реальных багах — вместо «пользователь получил fallback» станет «500». Это намеренно (баги должны быть видимы). Но убедиться, что путь fallback всё ещё срабатывает на настоящих LLM-сбоях (rate limit, таймаут) — иначе при недоступности LLM пользователи начнут получать 500 вместо плана. Прогнать `:server:test`.

**Зависит от 1.3** (нужны типы). Делать после неё.

**Коммит:** `fix(server): only fall back on expected LLM failures; let real bugs surface as 500`

---

## ВОЛНА 2 — Архитектура (планируемые, крупнее; каждая — отдельная сессия)

Эти задачи большие и связанные — их НЕ делать наскоком. Для каждой сначала прочитать соответствующий раздел `docs/full_technical_audit_2026-07-09.md`, затем составить под-план. Ниже — только рамка и порядок.

### 2.1 🟡 Единый каталог упражнений (устранить 3 копии)
Ссылка: full-аудит A1/№10 + A2/№11. Первый безопасный шаг — **тест-инвариант** (не рефакторинг): в `server/src/test` добавить тест «каждый ID из `BuiltInExerciseCatalog` существует в `ExerciseLibrary`». Он упадёт и покажет реальный размер дрейфа. Только потом — унификация. Начать с теста, унификацию оформить отдельной сессией.

### 2.2 🟡 Вынести факты об упражнении на элемент каталога
Ссылка: №11. Поля `durationUnit`/`isBodyweightOnly` на `ExerciseCatalogItem`, удалить ручные `Set<String>` из `AiTrainingPlanPolicy.kt`. Делать ПОСЛЕ 2.1 (единый каталог), иначе придётся править в трёх местах.

### 2.3 🟡 Fallback через TrainingSplitPlanner
Ссылка: №9/A4. `fallbackTraining` должен строить `WorkoutSkeleton` тем же планировщиком, а не своим шаблоном. Регрессионный тест: fallback-план проходит те же валидаторы, что основной, на матрице edge-профилей.

### 2.4 🟠 Разбить AiService God object
Ссылка: №14/A5. Извлечь `BundleCache` (инжектируемый singleton), `LlmClientRouter` (из 1.3), промпт-билдеры. Механический рефакторинг без смены поведения; каждый extract — отдельный коммит.

### 2.5 🟠 Канонические формулы питания в shared
Ссылка: №18/A3. Вынести TDEE/макросы в `shared/domain` как чистые функции; клиент и сервер используют одну. Parity-тест до правки.

### 2.6 🟡 Единый error-конверт + `/v1` (API)
Ссылка: №20. `StatusPages`-плагин, формат `{"error": {"code", "message"}}`, префикс `/v1/ai/*` (старые маршруты — alias на переходный период).

---

## ВОЛНА 3 — Требуют владельца / отдельного решения (НЕ автоматизировать)

Эти пункты исполнителю НЕ делать самостоятельно — только с явного решения владельца.

- 🔴 **Per-user идентичность (Firebase Auth)** — фундамент для реальной защиты монетизации и квот (№3, №22, S2). Большой пласт, требует решения по продукту. Все остальные security-фиксы (Волна 0) — это снижение риска до этого, не замена.
- 🔴 **Реальный Play Billing + серверная проверка entitlement** (№2) — после auth.
- 🔴 **Чистка git-истории** от PII (`git filter-repo` + force-push) — переписывает историю, только владелец.
- 🟠 **`allowBackup="false"`** в `AndroidManifest.xml` — решить вместе с восстановлением профиля (иначе переустановка теряет данные). Сцеплено с auth.
- 🟠 **Async-job для bootstrap-генерации** (№8) — крупное изменение контракта, планировать.
- 🟠 **Внешний кэш (Redis/Memorystore)** для кросс-инстансного дедупа (№7 шаг 2) — только по метрикам, когда дубли станут заметны в деньгах. Сначала сделать 2.4 (инжектируемый `BundleCache`).
- 🟡 **iOS**: StoreKit + Firebase iOS + живой прогон — только если iOS в скоупе релиза (решение по продукту).
- 🟡 **Живой прогон Android по экранам** на эмуляторе (онбординг → генерация → тренировка → чат) — поймать визуальные/runtime-баги, невидимые в коде.

---

## Порядок выполнения (сводка)

1. **Волна 0** целиком — один день, каждая задача отдельным коммитом. После неё риск резко падает.
2. **Волна 1** — обработка ошибок (клиент + сервер), 1.3 перед 1.4.
3. **Волна 2** — по одной задаче за сессию, каждая начинается с чтения соответствующего раздела full-аудита; 2.1 перед 2.2.
4. **Волна 3** — не трогать без владельца.

После каждой волны — прогнать полный `./gradlew build` (или хотя бы `:server:test` + компиляцию клиента) и обновить чекбоксы в `ARCHITECTURE_SECURITY_BACKLOG.md`.

## Ссылки на детальные разборы
- Архитектура/безопасность/масштаб (25 проблем, обоснования): `docs/full_technical_audit_2026-07-09.md`
- Клиент/iOS/локализация: `docs/client_ios_l10n_audit_2026-07-09.md`
- Трекер с чекбоксами: `ARCHITECTURE_SECURITY_BACKLOG.md`
