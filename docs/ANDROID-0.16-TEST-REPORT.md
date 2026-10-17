# Android 0.16.0 — Telegram audit and correction

## Findings

Desktop's Telegram link uses `dd` + 16-byte secret, matching native Rust padded transport. Android's link omitted `dd`, while the handcrafted probe used padded framing directly. Therefore the probe did not validate the actual app integration. The Android link is now corrected; users must re-add/select the updated proxy in Telegram.

Running desktop proxy connections and engine.log showed CF-proxy fallback, unlike Android's previously direct-only WS/TCP configuration. The user confirmed desktop works without VLESS. The fact that a VLESS adapter was active during later inspection does not invalidate that observation. No desktop process or adapter was changed.

## Changes

- Enable upstream `--default-domains` with native tg-ws-proxy-rs 2.5.4. This downloads the Flowseal community CF domain list at startup, with upstream's built-in fallback on fetch failure. CF is the primary configured path without explicit DC-IP overrides; raw TCP remains fallback. No arbitrary list invented by Local Route.
- UI discloses third-party relays, GitHub list fetch, encrypted Telegram payload and exposure of network metadata. This is not a wholly on-device traffic path or a Local Route-operated server. Availability/trust of third-party nodes is not guaranteed.
- Bind and advertised link host explicitly 127.0.0.1; stable secret remains encrypted locally.
- Three short guide pages starting VLESS, then DPI, then TG; bottom navigation highlights the relevant tab. Guide version incremented so existing users see the new guide. No server subscription bundled, no telemetry/ads added.

## Verification

- Debug/release build, release lint, 13 unit tests passed.
- Emulator `telegramLinkUsesPaddedSecretAndCfFallbackReallyAnswers`: passed, 5.024 seconds. Checks actual generated deep-link secret/host and forces `--pinned-upstream cfproxy`; real resPQ response received. Direct WS cannot mask a failed CF transport in this test.
- Emulator `telegramStartsChecksRealDcStopsAndRestarts`: passed, 6.764 seconds, two start/stop cycles.
- Not tested: user's phone/network after this update, logged-in official Telegram messages/media/calls, all account DCs, VLESS and DPI regression suite in this iteration. Do not claim universal compatibility based on resPQ.

Licenses unchanged: tg-ws-proxy-rs / Flowseal MIT notices and bundled dependency license inventory remain under app/src/main/assets/licenses. Android GPL obligations remain as previously documented. Nothing published remotely.
