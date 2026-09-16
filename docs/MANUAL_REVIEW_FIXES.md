# Исправления по ручной проверке от 2026-08-08

Дата: 2026-09-13. Ветка: `codex/fix-manual-review`, база `fe69f05`.

## Результат

- Вход: ожидание Firebase ограничено 20 секундами в общем AuthPresenterDelegate. Таймаут показывает локализованную сетевую ошибку и разрешает повтор. Дублирующие попытки игнорируются. Отмена распространяется через Android FirebaseAuthRepository; loading снимается в finally. Фоновая синхронизация не блокирует завершение входа.
- Google/Apple кнопки блокируются на время авторизации; локальный индикатор снимается в finally. Ожидание выбора учётной записи ограничено 60 секундами (включает взаимодействие пользователя с системным picker), последующий Firebase-вход — 20 секундами. На iOS добавлен opt-in C interop, обработка отмены, проверка результата генератора nonce и защита завершения/очистки delegate от запоздавших callbacks. Эти платформенные изменения требуют проверки на Mac.
- DayOfWeek: общий mapping использует isoDayNumber. Удалена вторая копия проблемного when в WeekdaySelection. Ошибка действительно воспроизведена компиляцией common metadata; исправленная задача проходит.
- Удалены восемь пустых файлов *PresenterDelegate.kt в screens. Expect-фабрики presenters сохранены.
- ChatScreen: вынесены отображение сообщений и composer. OnboardingScreen: вынесены 14 шагов, общий набор небольших компонентов и экран ожидания генерации.
- AppRoot: отдельно AppShell, AppNavigationHost, TopBar/BottomTabs. Убрана запись высоты панели прямо во время композиции, скрытые панели больше не оставляют старые отступы. Добавлены описания кнопок верхней панели для accessibility.
- CoachVisuals и CoachChoiceCard находятся в ui/coach; каталоги изображений разделены по тренерам. EditProfileState находится в screens/profile. Все потребители обновлены.
- SettingsPlatform: Android/iOS реализации сохранены; iOS restartApp теперь пересоздаёт Compose-поддерево через AppRoot вместо пустого метода. Это не перезапуск процесса и не полноценная настройка локализации iOS.

## Проверки

Успешно выполнены:

```text
:ui:compileCommonMainKotlinMetadata
:ui:testDebugUnitTest
:shared:testDebugUnitTest
:server:test
:app-android:assembleDebug
:app-android:lintDebug
git diff --check
```

Всего 90 тестов, 0 failures/errors: ui — 7 (6 auth + 1 календарь), shared — 5, server — 78. Серверные тесты впервые исполнены до изменений и остаются зелёными; в финальном запуске часть задач up-to-date.
Тесты auth используют kotlinx-coroutines-test 1.9.0 и виртуальное время: timeout/retry, дублирование запросов, сеть, отмена, закрытие scope, неблокирующая синхронизация. Это единственная добавленная зависимость, только commonTest, версия соответствует coroutines проекта.
Проверено сохранение содержимого всех 14 шагов онбординга и 9 composable-блоков чата при переносе. Runtime/визуальная проверка ещё требуется.

Android lint: 0 ошибок, 195 предупреждений, преимущественно ресурсы (170 UnusedResources). Предупреждения не отключались и baseline для них не создавался.

Артефакт: `app-android/build/outputs/apk/debug/app-android-debug.apk`.

## Ограничения

- `:ui:compileKotlinIosSimulatorArm64` в этом Windows-окружении — SKIPPED, это не успешная iOS-компиляция. Нужны Mac/Xcode и проверка Apple Sign-In/reset/settings. Подключение Firebase iOS и провайдера Apple остаётся внешней настройкой; работающий end-to-end вход на iOS не заявляется.
- На AVD Pixel_7a проверены запуск и вход с отключёнными Wi-Fi/mobile data: появилась локализованная сетевая ошибка, кнопка входа снова доступна. Успешный вход с реальной учётной записью, accessibility и полная визуальная приёмка ещё нужны. Firebase Task может продолжить собственную работу после отмены ожидания coroutine; таймаут ограничивает ожидание UI, не обещает отменить операцию на сервере Firebase.
- Остальные backlog (питание по актуальному весу, платежи, отказы AI и т.д.) не входили в список ручной проверки от 08.08 и не изменялись.
- Пользовательские исходные удаления/файлы с повреждёнными именами не тронуты. Коммита, push и деплоя нет.

## Запуск Gradle на этой Windows-машине

Исходный запуск падал до компиляции с `Unable to establish loopback connection` / Java NIO UnixDomainSockets `Invalid argument: connect`. Перенаправление временных Unix-сокетов в отдельный короткий путь позволило запустить Gradle. JVM и глобальные настройки системы не менялись.

Для проверок использовались JDK 17 и параметры только текущего процесса PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:/Users/AbrahamSuperStar/.codex/tmp/vtempe'
.\gradlew.bat :ui:compileCommonMainKotlinMetadata :ui:testDebugUnitTest :shared:testDebugUnitTest :server:test :app-android:assembleDebug :app-android:lintDebug
```

Каталог временных сокетов должен существовать. На другой машине пути адаптировать; это локальное ограничение окружения, не причина в Kotlin-коде приложения.

## Файлы

Ключевые изменения:

- `app-android/src/main/java/com/vtempe/auth/FirebaseAuthRepository.kt`
- `ui/src/commonMain/kotlin/com/vtempe/ui/presenter/AuthPresenter.kt`
- `ui/src/androidMain/kotlin/com/vtempe/ui/screens/SocialSignInButtons.android.kt`
- `ui/src/iosMain/kotlin/com/vtempe/ui/screens/SocialSignInButtons.ios.kt`
- `ui/src/iosMain/kotlin/com/vtempe/ui/platform/SettingsPlatform.ios.kt`
- `ui/src/commonMain/kotlin/com/vtempe/ui/util/DayOfWeekExt.kt`
- `ui/src/commonMain/kotlin/com/vtempe/ui/screens/WeekdaySelection.kt`
- `ui/src/commonMain/kotlin/com/vtempe/ui/AppRoot.kt`, `AppShell.kt`, `navigation/AppNavigationHost.kt`, `chrome/TopBar.kt`, `chrome/BottomTabs.kt`
- `ui/src/commonMain/kotlin/com/vtempe/ui/screens/ChatScreen.kt`, `screens/chat/ChatMessages.kt`, `screens/chat/ChatComposer.kt`
- `ui/src/commonMain/kotlin/com/vtempe/ui/screens/OnboardingScreen.kt`, `screens/onboarding/*Step.kt`, `OnboardingComponents.kt`, `OnboardingSavingOverlay.kt`
- `ui/src/commonMain/kotlin/com/vtempe/ui/coach/CoachVisuals.kt`, `CoachChoiceCard.kt`, `ArturExerciseIllustrations.kt`, `MiaExerciseIllustrations.kt`, `VtempeExerciseIllustrations.kt`
- `ui/src/commonMain/kotlin/com/vtempe/ui/screens/profile/EditProfileState.kt` и обновлённые импорты в EditProfileScreen/ExerciseLibraryScreen/ExerciseUiMapper
- Удалены пустые Chat/Home/Nutrition/Onboarding/Progress/Settings/Sleep/WorkoutPresenterDelegate.kt из `ui/src/commonMain/.../screens`.
- `ui/src/commonTest/kotlin/com/vtempe/ui/presenter/AuthPresenterTest.kt`, `util/DayOfWeekExtTest.kt`
- `gradle/libs.versions.toml`, `ui/build.gradle.kts` — test dependency.

## Продолжение: история переходов и Back — 2026-09-13

В `AppShell` история сохраняла Splash/завершённый онбординг, а переход EditProfile → Settings создавал дубликат Settings над EditProfile. Правило переходов вынесено в `navigation/BackStack.kt`: Splash заменяется стартовым экраном, вкладка становится корнем, существующий экран открывается с удалением записей над ним. Список заменяется одним обновлением Compose state. Добавлены семь commonTest сценариев в `navigation/BackStackTest.kt`.

На прежнем APK воспроизведено: Welcome → пропуск входа → шаг 1 → Next → шаг 2 → системный Back возвращал на Sign in. В `OnboardingScreen` добавлен BackHandlerCompat: предыдущий шаг при currentStep > 0; во время сохранения поведение соответствует отключённой экранной кнопке Back.

Повторная ручная проверка offline-входа: Wi-Fi и mobile data эмулятора отключены, введены вымышленные данные, после ожидания показано «Network error, please try again», кнопка Sign in enabled=true. Работоспособность успешного сетевого входа это не подтверждает.

Итоговая проверка продолжения: commonMain metadata, 14 UI unit tests (включая 7 новых navigation tests), assembleDebug и lintDebug успешны. Lint: 0 ошибок, 195 предупреждений. На последнем APK подтверждено Step 2 → системный Back → Step 1, затем Back → Welcome → Back → Android launcher. Crash buffer пуст. Результаты предыдущих 5 shared и 78 server tests выше относятся к предыдущему запуску; в этом продолжении эти модули не менялись и тесты повторно не запускались.

Также пройдены переходы через все 14 шагов онбординга с начальными значениями; последний экран расписания проверен по скриншоту. Сохранение профиля и генерация плана не запускались. Проверка чата, вкладок и редактирования профиля на устройстве остаётся в ручной приёмке; их правила истории покрыты unit tests. Сеть эмулятора восстановлена после проверки.

## Ручная приёмка

Дополнительно 2026-09-13 выполнена проверка запуска на существующем AVD Pixel_7a: установка debug APK через adb успешна, явный запуск MainActivity вернул Status: ok, процесс продолжил работать, crash buffer пуст. Скриншот проверен: экран входа отображается. Это проверка запуска; полный проход сценариев ниже ещё требуется.

На Android: вход без сети завершается ошибкой не позднее установленного UI timeout; после восстановления сети повтор работает. Повторные нажатия не запускают несколько auth-запросов. После входа индикатор не ждёт sync. Пройти онбординг и чат, смену вкладок и Back, проверить отступы при скрытых панелях, редактирование профиля/выбор тренера.
На iOS: отдельно скомпилировать native target, проверить отмену Apple picker, ошибку/повтор, сброс настроек и пересоздание UI; затем завершить Firebase SDK/provider configuration и проверить настоящий вход.
