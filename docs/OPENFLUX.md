# OpenFlux preview 0.24.13

Experimental standalone Android IPv4 VPN using OpenFlux v0.13.0, revision
`4f9c786cdd1d9fcd0c133e2e0fec0c5a12522610` (GPL-3.0-or-later).
This does not supply an exit node, promise access in a restricted network, or
combine the OpenFlux tunnel with DPI/VLESS. Starting either mode stops the other.
Existing application VPN exclusions apply. Stream/PHP mode is not supported yet.
Interactive captcha/account authentication is not implemented in this preview.

Build with Go 1.26.6 and a complete Android NDK (tested NDK 30):

1. Clone https://github.com/p1neappleXpress/OpenFlux at the exact revision above.
2. Run `scripts/build-openflux.ps1 -Core <checkout> -Go <go.exe> -Ndk <ndk>`.
3. Build the Android APK normally. All three ABI helpers must be present under
   `app/build/generated/openfluxJniLibs`. They are PIE executables packaged as
   native libraries, extracted by Android, and started as a separate process.
   This avoids a second gomobile runtime colliding with libbox.

The helper receives the link only through stdin. It emits fixed JSON state
events; upstream diagnostic logs and credentials are not forwarded. Android
stores the profile in encrypted preferences. The adapter rejects invalid links
using the upstream bounded parser before persisting them. `connected` means
the OpenFlux exit handshake succeeded, not that every destination works.
NDK link flag `-checklinkname=0` is needed by upstream anet's Go runtime linkage.

Do not publish a binary without providing this adapter and corresponding pinned
upstream sources, license and dependency notices alongside it.

## Verification, 2026-10-21

- 62 release unit tests passed.
- 17 emulator tests passed (OpenFlux native integration and shared interface).
- Additional debug integration passed: a separate-UID network probe made an HTTP
  request and a UDP echo bound to Android's VPN network through HEV → SOCKS →
  encrypted OpenFlux → the local exit. Destinations were PC-loopback fixtures,
  not public sites. This verifies both stream and datagram forwarding.
- Invalid profiles leave the encrypted saved profile unchanged; UI without a
  profile asks for a link; stopping the native process is not shown as a failure.
- Emulator: Medium_Phone_API_37.0, x86_64. ARM helpers compiled, not device-tested.
- Delivered preview package: `org.localroute.community.preview`, versionCode 37,
  certificate SHA-256 `30bf353ac2f7ab8826e05f4eaa6214279c6bb8a21fd81c45b0970c2a32e6ae15`.
- APK SHA-256: `dac0c758fcbee93280e9fe640c5f654531b40ee01f7b0ec82d518e6844f06688`.

There is no user exit node yet. No real mobile-provider restriction bypass,
YouTube playback, captcha-required carrier, or OpenFlux/VLESS chaining was tested.
