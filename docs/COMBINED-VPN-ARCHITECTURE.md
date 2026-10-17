# Combined Android VPN — implementation audit

Status: CombinedVpnService is now wired to UI, quick tiles and notification actions. The old standalone VPN services are no longer registered. Final combined-release acceptance is recorded separately; previous APK outputs remain unchanged.

Initial compiler-only checkpoint on 2026-10-17 (historical, not final acceptance):

- CombinedRouting desired-state model, independent flags, package/domain selection and no implicit fallback.
- CombinedConfigCompiler: one TUN, local SOCKS5 outbound with standard UDP, optional VLESS outbound, package/domain rules, explicit DNS policy. Selected service domain DNS uses the physical resolver; this is deliberately not private server DNS and can be filtered by the provider. ECH/IP-only browser traffic is a remaining limitation.
- VLESS outbound serialization extracted into a shared method, preserving existing standalone configuration behavior.
- 22 unit tests passed (9 new routing/compiler tests plus existing 13).
- Emulator bundled Libbox.checkConfig accepted all three active configurations; parsed JSON confirmed one TUN inbound. Instrumentation passed in 2.89 seconds. This validates configuration only, NOT real split traffic or worker lifecycle.
- APK debug built/installed only on test emulator. No new release copied to outputs, no version bump, no remote publication.

## Historical coupling (removed by the common-service migration)

- MainActivity.requestVpn stops VlessVpnService; requestVless stops LocalDpiVpnService.
- MainActivity.stopVpn stops both; both UI stop buttons share this callback.
- ModeShortcuts duplicates mutual exclusion. Each VPN has its own notification and onRevoke handling.
- LocalDpiVpnService owns both ByeDPI SOCKS and HEV TUN. It excludes Local Route but captures all other apps. Service selection does not currently restrict its routes.
- VlessVpnService owns a separate gVisor TUN and libbox with Android connection-owner bridge.
- Telegram selects the VLESS authenticated gateway first, else ByeDPI, and restarts when that route changes.

## Decision

Use one libbox/VpnService TUN, a managed device-local ByeDPI SOCKS worker, and a VLESS outbound. Preserve the existing GPL-compatible Android distribution and dependency notices. No new engine/protocol dependency needed. Do not run HEV and libbox TUN simultaneously.

CombinedRouting is the pure desired-state model. DPI and server flags are independent. Neither enabled => no VPN. DPI alone => selected services use local SOCKS, other traffic direct. Server alone => server default. Both => selected services local, other traffic server. No automatic DPI-to-server fallback.

Match selected native Android package names (covers app-owned UDP/IP traffic) plus carefully bounded domain suffixes for browser traffic. DNS metadata/sniffing is required for domain rules; hardcoded IPs, ECH and unknown CDN domains cannot be promised to match domain-only rules. Package ownership must be verified on actual API levels.

## Original migration checklist (see current test report for acceptance)

1. Compiler and native config validation for each desired state; explicit DNS paths and standard SOCKS5 UDP, not sing-box-specific UDP-over-TCP.
2. Move ByeDPI worker ownership into the common service, with readiness check, serialized transitions and native exit supervision. Never publish ACTIVE just because a worker thread was scheduled.
3. Migrate both UI/quick tiles/notifications to a single command coordinator. Separate stop-DPI and stop-server; stop all only on VPN revoke or explicit global action.
4. Keep server gateway/TG behavior explicit when toggling flags; close stale authenticated gateway credentials without silently changing selected service routes.
5. On engine failure expose component error; do not silently send selected local traffic to server or server traffic direct.
6. Verify 00/10/01/11, both start orders, both stop orders, rapid repeated actions, revoke, process death, network change, DNS, TCP/UDP and Telegram. Use separate-UID traffic and distinct local/server fixture evidence, not UI flags.

Other protocol links (Trojan, Shadowsocks, SOCKS5) and subscriptions are deferred until this lifecycle is verified. A plain address is not a complete authenticated connection profile.

References: https://sing-box.sagernet.org/configuration/route/rule/ (Android package/domain matching), https://sing-box.sagernet.org/configuration/outbound/socks/ (standard SOCKS5 TCP/UDP).

## Current implementation

The common service, UI, notification actions and three quick tiles are wired.
Worker readiness uses an actual SOCKS5 handshake. Commands are serialized across
service instances; old-instance destruction cannot clear a newer session.
The libbox serviceStop callback closes the old native core/TUN during reload,
not the Android service. This distinction fixed a reload that otherwise stopped
the newly created VPN. Invalid saved server input preserves the running session.
The active endpoint is cached so stopping DPI does not reparse an edited draft.

Combined package routing requires Android 10+. Android 8/9 retain standalone modes.
DNS selection is by known domain suffix, not every package-owned unknown domain.
Native engine readiness is not a claim of end-to-end Internet availability.
Changing routes recreates the native core and can briefly interrupt connections.
Final build and device-test evidence: ANDROID-0.19-TEST-REPORT.md.
