# Сохранение онбординга и подготовка плана

Дата: 2026-09-14. Ветка `codex/fix-manual-review`. Продолжение ручной проверки приложения.

## Исправления и причины

- `OnboardingPresenter` больше не создаёт новый профиль при каждом повторе: использует существующий ID либо один ID на экземпляр presenter. Повторное нажатие во время сохранения игнорируется.
- Пустые, некорректные, нулевые и отрицательные числовые значения не заменяются незаметно значениями по умолчанию. Проверяется конечность веса; ошибка возвращает к шагу ввода. Это проверка формата, не полная продуктовая валидация допустимых диапазонов.
- Отмена coroutine распространяется, `saving` снимается в `finally`. Ошибки имеют тип, отображаются на экране на RU/EN и не показывают сообщения исключений БД/сервера. `false` от bootstrap больше не считается успехом.
- `ProfileRepositoryDb` сохраняет `Constraints`, которые раньше заменялись пустым объектом после чтения БД. Добавлена колонка `constraintsJson`, миграция `7.sqm` переводит схему 7 → 8 с пустым объектом для старых профилей. Утраченные до исправления ограничения восстановить автоматически нельзя.
- Чтение профиля и запись профиля с дочерними таблицами выполняются в транзакциях на IO dispatcher. Сбой вставки деталей откатывает всю запись; уведомление sync вызывается после успешного commit.
- `BootstrapCoachData` проверяет также наличие совета и даты начала плана. Ранее частичный результат мог блокировать восстановление после ошибки. Переданный `force` теперь учитывается; изменение введённых данных требует повторной генерации.

## Изменённые файлы

- `ui/src/commonMain/kotlin/com/vtempe/ui/presenter/OnboardingPresenter.kt`
- `ui/src/commonMain/kotlin/com/vtempe/ui/screens/OnboardingScreen.kt`
- `ui/src/commonMain/composeResources/values/strings.xml` и `values-ru/strings.xml`
- `shared/src/commonMain/kotlin/com/vtempe/shared/data/repo/ProfileRepositoryDb.kt`
- `shared/src/commonMain/kotlin/com/vtempe/shared/domain/usecase/UseCases.kt`
- `shared/src/commonMain/sqldelight/com/vtempe/shared/db/Profile.sq` и `7.sqm`
- `ui/src/commonTest/kotlin/com/vtempe/ui/presenter/OnboardingPresenterTest.kt`
- `shared/src/androidUnitTest/kotlin/com/vtempe/shared/data/repo/ProfileRepositoryDbTest.kt`
- `shared/build.gradle.kts`, `gradle/libs.versions.toml`: JVM SQLite driver только для Android unit tests, версия SQLDelight сохранена — 2.0.2. Драйвер отсутствует в production dependencies; используется стандартный способ [проверки с JVM SQLite](https://sqldelight.github.io/sqldelight/2.0.2/jvm_sqlite/).

## Проверки на Android

Итоговый Gradle запуск успешен: `:ui:compileCommonMainKotlinMetadata`, `:ui:testDebugUnitTest`, `:shared:testDebugUnitTest`, `:app-android:assembleDebug`, `:app-android:lintDebug` с `--max-workers=2`. Всего 32 теста (24 UI, 8 shared), 0 failures/errors. Из них 13 новых: 10 тестов онбординга/bootstrap и 3 SQLite-теста. Lint: 0 ошибок/195 предупреждений. `git diff --check` чистый. Сервер в этой работе не менялся и его тесты повторно не запускались.

Первый набор тестов прошёл; при промежуточном повторе неожиданно завершился Gradle daemon. После закрытия эмулятора и ограничения workers финальный запуск завершился успешно. Глобальные настройки Gradle не менялись.

На существующем AVD Pixel_7a отключены Wi-Fi и mobile data. Пройдено 14 шагов с начальными тестовыми значениями, нажато Save and start. Открыт Home, создан локальный резервный план. После force-stop и нового запуска вновь открыт Home. Crash buffer пуст.

Копия SQLite после остановки приложения проверена отдельно: user_version=8, Profile=1 строка, Workout=3 строки, Meal=21 строка. Сеть эмулятора восстановлена; эмулятор закрыт. Это проверка offline fallback, не подтверждение ответа production AI.

## Ограничения и следующие проверки

- Успешная серверная AI-генерация и восстановление после реального сетевого сбоя ещё требуют отдельной проверки. Сетевые тестовые запросы в production в этой работе не отправлялись.
- iOS native compilation и миграция на устройстве требуют Mac/Xcode; common metadata проверяется отдельно.
- Полная проверка прежних миграций отключена конфигурацией проекта. Для новой миграции добавлен исполняемый SQLite unit test на старой таблице Profile.
- Отдельное наблюдение: `ApiClient.mapApiThrowable` преобразует `TimeoutCancellationException` в сетевой результат, хотя это может быть отмена внешнего scope; нужна отдельная регрессия сетевого слоя. В эту правку API не включён.
- Пользовательские исходные изменения сохранены; коммита, push и деплоя нет.
- Obsidian использовался при поиске контекста. После восстановления MCP результат внесён в «Текущий статус и задачи — V-Tempe», раздел «Сохранение онбординга и план — 2026-09-14 (Codex)».
