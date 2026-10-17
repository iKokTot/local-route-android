# 0.18.0 — usability goal acceptance, 2026-10-17

Scope: make the current three working modes understandable and their controls predictable. This is not acceptance of the separate, still-pending unified DPI/VLESS engine migration.

## Requirements and current evidence

| Requirement | Implementation / verification |
| --- | --- |
| Preserve dark style | Existing Night/Panel/Mint palette, native Material buttons, three destinations; final emulator screenshot outputs/android-0.18.0/server.png visually inspected |
| Clear modes for ordinary users | DPI header “Локальный обход”, Telegram two numbered actions, server “Свой сервер” with own-link requirement; guide describes all three, starts with VLESS |
| Honest state | STARTING differs from ACTIVE; active VPN asks user to check internet, Telegram node response explicitly does not prove messages/media/calls; inspect MainActivity branches and runtime messages |
| Predictable commands | Separate DPI/server stop callbacks. Switching requires confirmation; cancelled server switch now performs neither save nor VPN start/stop. Quick tile conflicts open the relevant screen instead of silently replacing VPN |
| Clear own-server setup | VLESS-only label, missing-link guidance, encrypted save, masked input/show toggle; other providers' arbitrary links not promised |
| No accidental draft loss | Draft lifted to app composition and retained between tabs, hidden again on re-entry. Unsaved draft is intentionally memory-only, not promised across process death |
| Emulator verification | InterfaceTest: guide+three tabs, masked draft across tabs, cancel switch retaining DPI state and saved profile; all 3 passed in 18.135s. Cancellation test injects presentation state, not a real concurrent VPN session |
| No unfinished combined-mode promise | UI and guide explicitly say modes alternate. CombinedRouting/CombinedConfigCompiler remain staged, not used by release service |
| No remote publication | Only local edits/builds, emulator installs, local outputs; no push/store/post/server modification performed |

## Additional fixes since 0.17

- Cancel confirmation no longer saves a new server link before the user confirms.
- Draft no longer disappears after changing tabs.
- Quick tiles show “Переключение — в приложении” when another VPN module is active; in that case they only open the app.

## Verification gates

- Debug and signed release build, release lint: passed (27 seconds).
- 22 unit tests passed. Earlier guide navigation passed with font scale 1.3; current 3-test suite ran at scale 1.0. Emulator scale remains 1.0.
- Final APK signature verified with apksigner.
- SHA256: 3EA1A7222FE21D6F8ADFD4A1D2C4F7E0FCE5EE32FFFA9990E5905C06640C04D9.

No new networking engine or dependency/license change. No claim that UI tests prove network reachability, all accessibility settings, every OEM or actual user comprehension. Real phone feedback remains useful. Unified service lifecycle and broader VPN protocol support remain separate unfinished project work, not included in this UI release.
