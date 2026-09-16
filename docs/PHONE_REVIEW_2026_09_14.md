# Проверка на телефоне — 2026-09-14

## Результат

- Google: Android-кнопка использует GetSignInWithGoogleOption для явного входа. Безопасная диагностика пишет тип ошибки без токена. В APK Web Client ID заполнен и совпадает с Firebase Web OAuth client. SHA-1 установленной debug-сборки 53:07:13:C1:85:8B:B5:C9:34:03:84:9B:1F:C8:73:0A:F9:99:CF:ED отсутствует в локальном google-services.json (там старый debug и release). Это вероятная конфигурационная причина, а не доказанная проверкой консоли. Требуется проверить/добавить SHA в Firebase и повторить вход на телефоне. Доступа к Firebase Console и подключённого телефона в этой проверке нет.
- Язык: back stack поднят выше locale key и сохраняется через rememberSaveable с сериализуемыми Destination. AppShell получает состояние извне. Онбординг инициализирует выбранный язык из LanguagePreferences.
- Коуч: add_meal/swap_meal требуют полных неотрицательных макросов и положительной энергии; отсутствие значений больше не превращается в нули. Проверка включена в существующий ограниченный LLM repair pipeline и повторяется перед применением операции. При полном отклонении правок ответ не сохраняет ложное «добавлено». Prompt уважает обычные пищевые предпочтения, короткие подтверждения и требует порции, КБЖУ и рецепт. Настоящие аллергии/медицинские ограничения сохраняются. Старые блюда с нулевыми данными показывают «КБЖУ не рассчитаны» в деталях; значения не выдумываются и старые записи автоматически не пересчитываются.
- Сон: поле «Как спалось?» до 500 символов, сохранение по дате, восстановление и включение в AI-контекст. Старый sleep.history.v1 не меняется. Заметки используют sleep.notes.v1 и отдельный sync domain sleepNotes, чтобы старые клиенты читали длительность как раньше. Обновлены Android/iOS presenters, DTO и серверный whitelist. Повреждённый snapshot заметок не заменяет данные.

## Основные файлы

- ui: AppRoot.kt, AppShell.kt, navigation/Routes.kt, presenter/OnboardingPresenter.kt, androidMain/screens/SocialSignInButtons.android.kt, screens/NutritionDetailScreen.kt.
- Сон: shared/data/repo/SleepStore.kt, domain/model/Models.kt, domain/repository/Repositories.kt, data/repo/NetworkSyncRepository.kt; ui/presenter/SleepPresenter.kt, screens/SleepScreen.kt, Android SleepViewModel.kt и iOS SleepPresenter.ios.kt.
- Сервер: CoachEditApplicator.kt, ChatService.kt, CoachBundlePromptBuilder.kt, SyncService.kt, AiProfile.kt.
- Ресурсы: ui values/strings.xml и values-ru/strings.xml; ui/build.gradle.kts подключает существующий serialization plugin.
- Тесты: CoachEditTest.kt, NavigationPersistenceTest.kt, SleepStoreTest.kt.

## Проверки

Финальный Gradle запуск: BUILD SUCCESSFUL, 38 секунд, --max-workers=2, JDK 17; process-local JAVA_TOOL_OPTIONS для unixdomain tmpdir.

- :server:test — 81 тест.
- :ui:testDebugUnitTest — 25 тестов.
- :shared:testDebugUnitTest — 10 тестов.
- Итого 116, без failures/errors/skips; добавлено 6 регрессионных тестов.
- :app-android:assembleDebug, :app-android:lintDebug, :ui:compileCommonMainKotlinMetadata, :shared:compileCommonMainKotlinMetadata — успешны.
- Lint: 0 ошибок, 195 предупреждений. Существующие Kotlin opt-in/Gradle deprecation warnings остались.
- git diff --check — успешно. Предыдущие пользовательские и инженерные изменения сохранены.

Pixel_7a emulator: отдельный временный Android user 10 → Continue without account → Russian → онбординг остаётся на шаге 1 на русском → Далее → шаг 2. Временный user удалён после проверки. На существующем тестовом профиле: 8 часов + «Woke twice» → Saved → force-stop/relaunch → Sleep восстанавливает 8 часов и текст. Crash buffer пуст; эмулятор закрыт. APK для UI-проверки собран перед последним уточнением server validation и обработкой повреждённых snapshots; итоговый APK пересобран успешно.

## Ограничения и следующий шаг

- Изменения локальные, commit/push/deploy не выполнялись. Серверные исправления коуча и межустройственная синхронизация sleepNotes требуют выкладки обновлённого backend. На текущем production новый sync domain ещё может отклоняться; локальное сохранение работает.
- Реальный диалог с AI и Firebase Google sign-in end-to-end не проверены. После выкладки повторить сценарий бургера, включая «давай» и майонез, и проверить КБЖУ в сохранённом плане.
- iOS native build и ручная проверка требуют macOS. Common metadata и обновление всех платформенных сигнатур выполнены.
- Последний APK: app-android/build/outputs/apk/debug/app-android-debug.apk; на физический телефон в этой итерации не установлен.

## Pixel: подтверждённая причина Google-входа — 2026-09-15

После установки debug APK на физический Pixel 7a воспроизведено: выбор аккаунта → возврат без сообщения. В системном logcat Google: `This android application is not registered to use OAuth2.0, please confirm the package name and SHA-1 certificate fingerprint match what you registered in Google Developer Console`, затем `[16] Account reauth failed`. Это подтверждает конфигурационную ошибку OAuth для текущей сборки. В обработчике GetCredentialCancellationException возвращается null без сообщения, поэтому внешне ничего не происходит. Нельзя считать код 16 доказательством ручной отмены.

Нужно добавить SHA-1 53:07:13:C1:85:8B:B5:C9:34:03:84:9B:1F:C8:73:0A:F9:99:CF:ED к Android-приложению com.vtempe в соответствующем Firebase/Google Cloud проекте и повторить проверку. Консоль в этой итерации не изменялась. Логи отфильтрованы без аккаунтов/токенов; полный logcat не сохранялся. MCP Obsidian в этой итерации недоступен, результат записан локально.

### Повторная проверка после регистрации SHA-1 — 2026-09-15

Пользователь добавил отпечаток в Firebase и подтвердил успешный вход на Pixel 7a. В logcat устройства в 14:39:02 FirebaseAuth сообщил signed-in user через auth state listeners; идентификатор аккаунта не выводился и не сохранялся. Google-вход подтверждён на этой debug-сборке и этом устройстве. Новых изменений кода и переустановки для исправления конфигурации не потребовалось.
