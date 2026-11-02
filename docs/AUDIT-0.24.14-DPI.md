# Local Route Android: DPI regression and architecture/UX audit

Date: 2026-10-25. Base: 28e5a9d plus the locally retained OpenFlux work.
No publication or server configuration is part of this change.

## Evidence and limits

The owner's currently working ByeDPI command exactly matches
`AndroidStrategies.BYEDPI_ANDROID_TCP_UDP_COMMAND`. The four former choices
were parameter presets of one native ByeDPI engine, not four independent engines.
The old working implementation (`ad37453`) used the same command but a different
packet path. Current code had put a domain-aware libbox router and DNS interception
in front of ByeDPI even when no server was enabled.

This establishes concrete implementation differences, not proof that every failure
on the owner's provider has one cause. No physical phone or restricted provider
network is available to this audit. Emulator fixtures verify routing and lifecycle,
not YouTube playback, Discord calls or universal censorship circumvention.

## Fixed findings

1. **P1: standalone DPI unnecessarily depended on domain recognition and ISP DNS.**
   Restore `TUN → HEV tun2socks → ByeDPI` when the server is off. IPv4-only Android
   VPN, DNS 1.1.1.1 through this path, no VPN app exclusions. Server mode keeps
   libbox; enabling/disabling it replaces only the packet adapter, retaining the
   selected ByeDPI strategy. Pure DPI no longer starts libbox's command/log clients.
2. **P1: native configuration freed while the event loop could still read it.**
   `jniStopProxy` called `reset_params` immediately after requesting socket shutdown.
   Move parameter cleanup to after `event_loop` returns. Do not free borrowed argv
   until then. Also free argv on failed creation: repeated starts no longer retain
   every command allocation for the process lifetime.
3. **P1: exited native worker could leave an obsolete listener descriptor.**
   Kotlin clears the descriptor on worker completion; an already closed listener
   is an idempotent stop, not a permanent restart failure.
4. **P1: process-global HEV ownership across DPI/OpenFlux.**
   Centralize native tunnel ownership. Cleanup from a revoked service cannot stop
   a replacement service's native worker. This is not simultaneous OpenFlux/DPI.
5. **P1: native app voice/QUIC route depended only on a recoverable domain.**
   Add selected YouTube/Discord packages to local-DPI rules before general server
   routing. Domain rules remain for browsers. Package lookup requires Android 10+;
   older Android falls back to domain recognition in combined mode. Standalone DPI
   does not require process lookup at all.
6. **P2: misleading four-preset workflow and disabled first-run start.**
   Keep one reference command, allow first-run startup without a prior confirmation.
   Custom command is advanced and explicit. Remove obsolete preset selection on
   upgrade without labelling the replacement as already user-confirmed. Deduplicate
   an identical custom/reference command; prioritize a distinct explicit custom
   command unless another command was already confirmed.
7. **P2: stop timeout silently treated as completion.**
   The UI bridge now reports an unconfirmed stop rather than proceeding after its
   bounded wait expires. Preserve independent stop controls.
8. **P2: onboarding and presentation contradicted Android behavior.**
   Explain standalone vs combined DPI, remove repeated trial wording for the single
   reference profile, offer settings rather than a nonexistent next preset, and
   remove the dynamically regenerated 'stored on computer' text on Android.
9. **P2: unbounded advanced command input.**
   Bound custom commands to 4096 characters/256 arguments in addition to the
   existing listener/daemon/redirection restrictions.

## What is intentionally not claimed fixed

- A valid SOCKS handshake or running foreground service is not a real application
  success. Confirmation remains manual; do not auto-save a profile from port checks.
- The owner's DNS/IPv6 switches in the working ByeDPI app are still unknown. The
  restored default matches the upstream default IPv4/DNS design, not an inspected
  copy of every owner preference. Add a validated advanced DNS setting if this
  default is unavailable on the owner's network; do not automatically switch DNS
  or silently send traffic through a server.
- Encrypted ClientHello, external encrypted DNS, unknown app variants and unowned
  UDP flows may prevent domain/app identification in combined mode. Use explicit
  observed routing diagnostics before adding more presets. A browser rule cannot
  reliably identify every unknown-IP flow without a domain.
- A blocked server/IP or unavailable transport cannot be fixed by another local
  command. Capture a report in the failing network rather than claim an emulator
  establishes provider compatibility.

## Further architecture/optimization work, by priority

### P1: operational verification

Run an actual-device matrix: Wi-Fi/mobile, DPI alone and with VLESS, screen lock,
large video, Discord text/voice, network handover and a clean installation/upgrade.
Record Android/build/adapter/strategy and timing, never keys. Compare the same URL
and network against ByeDPI with identical DNS/IPv6, not only the same command.

### P2: UI bridge scaling

`SharedRouteHost.snapshot()` reparses subscription links and regenerates catalogs,
app lists and up to 500 log lines on each poll. Cache immutable library metadata
by revision and send changed state only. Move subscription parsing/large snapshots
off the main thread; measure before/after on 200+ profiles and a full journal.
This is a code-path finding, not a measured battery or latency improvement.

### P2: boundaries and state

Extract connection library, DPI controller, diagnostics and platform command
handlers from the WebView host. Keep a typed desired/observed state model with
`starting / running / stopping / failed`, transport generation and last verified
timestamp. An engine's running state must not be named 'internet verified'.
Use the existing shared routing compiler as the single policy source.

### P2: experimental feature packaging

OpenFlux currently adds another native binary/toolchain requirement to every build.
Introduce a build feature flag with a separate experiment package and a disabled
UI capability when absent. Do not make the stable DPI release depend on a new
exit-node workflow. Preserve all experimental source and licenses.

### P2: build/release reliability

Automate unit, fixture TCP/UDP, adapter-transition and small-screen/large-font UI
tests in CI. Pin source/artifact hashes, test upgrade signing, and publish source
corresponding to every distributed binary. Avoid coupling store builds, private
experiments and public previews; no store resubmission is performed by this audit.

### P3: maintenance

Audit unused legacy VPN service implementations before removing them; consolidate
duplicate native transport control and remove stale desktop-only hidden markup
when Android/desktop view adapters are separated. Keep third-party attribution.
Precompile diagnostic sanitization regexes and avoid reconstructing unchanged DOM
trees; benchmark on a low-memory physical phone. Do not remove privacy bounds or
redaction in the name of performance.

## Verification record

- Unit suite and emulator results are recorded with the final build, below.
- First direct-adapter integration: five start/stop cycles, separate-UID HTTP and
  UDP echo through the actual Android VPN: passed.
- Initial regression pass: 23 shared-interface/combined-routing/native-config/
  VLESS TCP+WebSocket tests: passed.
- ARM binaries compile as part of the APK; runtime testing is x86_64 API 37 only.

Final result:
- 64 unit tests: zero failures/errors.
- Final routing/lifecycle regression: 26 emulator tests passed, including standalone
  five-cycle TCP/UDP, exclusions, invalid server, rapid commands, VLESS TCP/WS,
  network loss/recovery, shared UI and OpenFlux TCP/UDP through a separate UID.
- After final presentation polish, all 17 shared-interface tests passed again,
  including a new primary-button start/stop without a preset wizard.
- Delivered APK 0.24.14 (38), package org.localroute.community.preview.
- SHA-256: c93df4d341f6b6d87c26fc150c94cf9c30bb614c7cc440e27378b1e187c1d50d.
- Release signature verified against the existing Local Route certificate.
- A test run caught an unconditional libbox validation of an empty standalone
  config after an optimization. This was fixed before delivery and the complete
  routing regression rerun passed. Failed intermediate builds were not published.
