# Сборка 0.24.1

JDK 17, Gradle 8.9 (wrapper), Android SDK 35, NDK 25.1.8937393, CMake 3.22.1.

Общий интерфейс берётся задачей `prepareSharedUi` из соседнего `Local Route Community 0.24.1/resources/app/src/renderer`, а при его отсутствии — из `shared-ui` в этом проекте. Не редактируйте генерируемую копию `app/src/main/assets/ui` отдельно.

```powershell
.\gradlew.bat :app:testReleaseUnitTest :app:assembleRelease
```

Для подписанного релиза нужен локальный `release-signing.properties` с `storeFile`, `storePassword`, `keyAlias`, `keyPassword`. Ключи и пароли в исходники не включены. Для обновления уже установленной Community-сборки необходим тот же ключ.

Нативный хост общей страницы — `app/src/main/java/org/localroute/android/SharedRouteHost.kt`. Сетевая маршрутизация остаётся в `tunnel/CombinedVpnService.kt` и `tunnel/CommunityConfigCompiler.kt`. Встроенные нативные зависимости закреплены в Gradle и проверяются SHA-256.

Проверка интерфейса `SharedInterfaceTest` выполняет только чтение состояния и навигацию, не добавляет подписки и не запускает DPI/VPN. Сборка тестового APK: `:app:assembleReleaseAndroidTest`. Запускайте конкретно этот класс на уже установленном release-приложении. Прежний тест Compose-интерфейса 0.23 заменён на `SharedInterfaceTest`.

Лицензия Android GPL-3.0-or-later и уведомления сторонних компонентов сохранены. Подробности версии — в `COMMUNITY-0.24.1.md`.
