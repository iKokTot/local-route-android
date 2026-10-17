# Android 0.15.0 — local verification

- Added persistent first-launch guide and an always-available help button. Explains local DPI, Telegram proxy confirmation and the need for a user's own VLESS server/link. No ready-made subscription is bundled.
- Kept the existing three-tab design. VLESS startup failures have a plain-language summary and expandable technical details (URI/UUID masked). Telegram native errors are retained in the local, redacted journal instead of being displayed verbatim.
- Telegram WS/TCP connection timeouts raised from 4 to 8 seconds. Probe timeout raised to 22 seconds per candidate. Each attempted DC now records the actual exception or successful MTProto response. Restart no longer clears the recent diagnostic history.
- A successful Telegram probe explicitly describes only a test-node response, not account/media/call availability.

Verification on 2026-10-16:

- Gradle debug/release APK build and release lint: successful.
- 13 unit tests: passed, including two new runtime redaction/history/error tests.
- Android emulator: `telegramStartsChecksRealDcStopsAndRestarts` passed (real MTProto response, two start/stop cycles, 7.756 seconds).
- First-launch guide visually inspected on emulator; screenshot in outputs/android-0.15.0/guide.png. Other integration tests were not rerun in this iteration.

Unresolved: user reports direct Telegram test failure on phone. Emulator connectivity does not reproduce that network condition. Longer deadlines are not a proven fix. Fresh per-DC diagnostics from this build are needed to determine whether the failure is TCP reachability, TLS, protocol or routing. Do not advertise universal Telegram access or white-list bypass.

No remote publication, server changes, telemetry or public proxy subscription added. Existing third-party license obligations remain unchanged.
