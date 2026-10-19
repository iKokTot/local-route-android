# 0.24.11 — connection check and settings spacing

- Measure elapsed HTTPS probe duration instead of exporting a fabricated zero.
- HTTP error responses are shown as limited access, not a broken tunnel.
- Android shows a compact check summary; endpoint details are collapsed.
- Add inner padding and spacing to diagnostics controls and technical log.

Emulator checks on 2026-10-19:

- Release SharedInterfaceTest: 15 tests, no failures (manual owner-profile test is opt-in).
- Unit suite: 61 tests, no failures.
- Isolated debug installation: native engine configuration, VLESS TCP and WebSocket
  through actual TUN, combined mode lifecycle, app exclusions, Telegram proxy
  start/check/stop/restart passed.
- LAN TCP/UDP payloads passed while DPI and VLESS were enabled. The old test
  incorrectly expected these private destinations to use DPI; actual engine logs
  confirmed the intentional private-address direct rule. Corrected test passed.

Local fixture traffic does not prove DPI circumvention on the owner's ISP.
Telegram probe does not verify account messages or calls. No publication performed.
