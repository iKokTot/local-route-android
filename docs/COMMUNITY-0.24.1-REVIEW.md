# Review of the supplied Community 0.24.1 archive — 2026-10-17

Source: Local-Route-Community-Android-0.24.1.zip supplied by the project owner.
Base: Local Route Android 385096497ea6061cfff8c01edc9b8b6397104b00.
Community authorship and upstream GPL-3.0-or-later notices are preserved.
Historical claims in COMMUNITY documents describe the contributor's checks,
not additional checks independently reproduced in this review.

## Scope and release identity

This branch contains the Community implementation, not a replacement of main.
It replaces the Compose screens with a local HTML/CSS/JS WebView interface,
adds grouped subscriptions, selective domain/package routing, and VLESS
fingerprint/ALPN support. It still supports VLESS subscriptions only.

Package: org.localroute.community, versionCode 25, versionName 0.24.1.
The archive APK has a different signing certificate from org.localroute.android:
ad40492178d942c09c3c21c06b24b4711cb7d1009a7114191dce0e4f2c2a26ad.
It cannot update the original RuStore application or inherit its preferences.
No release, APK upload, RuStore submission or change to main was performed.
The supplied APK predates the source fixes below and was not republished.

## Review changes

1. Refreshing a subscription now clears the selected URI if that URI disappeared
   from every retained group. Previously an invisible removed server could remain
   configured. Added an Android persistence regression test.
2. A mode requested while the WebView was loading is applied after page load;
   previously the initial navigation script could run before navigate existed.
3. The whole-device VLESS integration test explicitly enables all-traffic mode
   and restores prior routes afterwards. Its first run failed with
   DIRECT_UNREACHABLE because Community defaults to selective routing; this
   was a test setup mismatch, not evidence that all VLESS transports fail.
4. Git builds use committed shared-ui deterministically. An unrelated sibling
   desktop directory can no longer silently replace the reviewed interface.
   Generated assets/ui is ignored. Android tests can target debug with
   -PandroidTestBuildType=debug without requiring the contributor's signing key.

## Independently reproduced checks

- testReleaseUnitTest: 45 tests, zero failures, repeated after runtime fixes.
- assembleDebug and assembleDebugAndroidTest: successful.
- Android emulator API 37 x86_64, debug build of the reviewed source:
  - refreshedGroupCannotKeepRemovedServerSelected — passed;
  - sharedPageFitsAndSettingsBackKeepsOriginWithoutTouchingConnections — passed;
  - combinedConfigurationsAreAcceptedByBundledNativeEngine — passed;
  - vlessWebSocketWholeDeviceThroughActualTun — passed after explicit all-traffic
    setup, with actual fixture response through Android TUN.
  Final instrumented run: OK (4 tests), 11.501 seconds.
- The original archive APK signature verifies. Existing pinned native artifact
  checks ran during source build. No signing private key, keystore or obvious
  access-token pattern was found in the source being published.
- The WebView restricts requests to packaged assets, blocks foreign navigation,
  has file/content access disabled and a restrictive CSP. Rendered server names
  are escaped, and the snapshot exposes IDs rather than credential-bearing URIs.

For reproduction, use JDK 17, SDK 35, NDK 25.1.8937393, CMake 3.22.1 and Gradle
8.9. Initialize Git submodules recursively. Build tests with:

    ./gradlew :app:testReleaseUnitTest :app:assembleDebug :app:assembleDebugAndroidTest -PandroidTestBuildType=debug

Network integration uses scripts/vless-test-fixture.py and the separately
installed networkprobe test app. It does not use a real user's subscription.

## Remaining validation before adopting as the main release

Exercise selective domain and package routes, exclusions, combined DPI/TG/VLESS,
Wi-Fi/mobile transitions, sleep and account-specific Telegram data centers on a
real phone. Unit/configuration checks do not prove bypass on the user's network.
The supplied project's network suite was not run in full; several older tests
still assume all-traffic defaults and need setup adapted before that full run.
The original Compose-first product requirement and release package/signature
need an explicit product decision before replacing main with this WebView fork.
No universal compatibility or production readiness is asserted by this review.
