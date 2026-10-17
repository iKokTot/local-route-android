# Сборка Local Route Community 0.24.0

Изменения и сценарии проверки описаны в `COMMUNITY-0.24.0.md` в пакете релиза.

Проект основан на `iKokTot/local-route-android`, commit `385096497ea6061cfff8c01edc9b8b6397104b00`, лицензия GPL-3.0-or-later. Авторство и исходные лицензии сохранены.

Среда проверенной сборки: JDK 17, Gradle 8.9 через wrapper, Android SDK 35, NDK 25.1.8937393, CMake 3.22.1. Нативные исходники из submodules включены в архив исходников, в том числе необходимые для сборки материализованные файлы Windows. Libbox и TGWs скачиваются задачами Gradle с проверкой закреплённых SHA-256.

Из корня проекта:

```powershell
$env:ANDROID_HOME = 'путь к Android SDK'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Релиз с вашим ключом: создайте локальный `release-signing.properties` с `storeFile`, `storePassword`, `keyAlias`, `keyPassword`, затем выполните:

```powershell
.\gradlew.bat :app:testReleaseUnitTest :app:assembleRelease
```

Ключ и файл паролей в архив исходников не включены. Для обновления уже установленной Community-сборки нужен тот же ключ подписи. Локальный ключ текущей сборки сохранён отдельно в `C:\LR\.build-tools\signing`; не включайте его в публичные исходники.

Код приложения: `app/src/main/java/org/localroute/android`. Приоритеты заданы в `core/CombinedRouting.kt` и `tunnel/CommunityConfigCompiler.kt`; Android-сервис — `tunnel/CombinedVpnService.kt`; новые экраны — `CommunityScreens.kt`. Windows имеет соответствующие правила в `src/core/vless.js` и обработчики подписок в `src/main.js`.

Первоначальный upstream README отражает историю проекта. В Community-версии внешний WireGuard не используется: серверный выход VLESS и локальный ByeDPI объединены через libbox в одном VpnService. В APK упакованы arm64-v8a, armeabi-v7a и x86_64.
