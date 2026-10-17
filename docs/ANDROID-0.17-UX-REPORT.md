# 0.17.0 — usability iteration, 2026-10-17

- Preserves dark/mint visual style and three tabs. Labels DPI / Telegram / Сервер; headers explain local bypass and own-server VLESS.
- Separates startup, configured/off and engine-on states. Does not claim internet/account availability from successful engine startup or one Telegram resPQ response.
- DPI stop callback no longer stops VLESS. Server screen has its own stop callback. This corrects button scope but DOES NOT implement simultaneous VPN engines.
- Switching from another active VPN mode via app screens requires confirmation; cancel leaves the current mode running. Quick settings retain existing switch behavior; common lifecycle migration remains pending.
- Telegram has two numbered actions; relay/privacy disclosure is expandable, never removed. Main screen instructions use short plain-language wording.
- VLESS link masked by default with explicit show/hide. No automatic clipboard read. No bundled server/subscription implied.
- Guide starts with VLESS, then DPI, then TG; user can reopen it or choose a tab. Fixed-height 54dp buttons changed to minimum height, stop labels shortened.

Verification:

- Debug/release build and release lint passed; 22 unit tests passed before final wording-only shortening.
- Instrumentation InterfaceTest walked full guide and all three screens, passed at font scale 1.0 (8.644s) and 1.3 (11.835s). This checks navigation/labels, not every overflow or network connection.
- Server screen visually inspected at 1.3; long introductory text shortened further after inspection. Emulator font restored to 1.0.
- Network engines not changed in this UX iteration; full network regression, stop-scope runtime integration and switch-confirmation interactions still need expanded tests. No claim of a fully verified combined mode.

No remote publication, no desktop modifications, no server mutations. Dependencies/licenses unchanged. Combined-routing model/compiler are staged and are not wired into release runtime.
