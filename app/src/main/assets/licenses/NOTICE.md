# Local Route Android — third-party notices

## Licence of this Android project

Local Route Android is GPL-3.0-or-later. A complete license copy is packaged as
`GPL-3.0.txt`. Corresponding source must be offered alongside any published APK;
this repository is the intended source location when publication is authorised.

## Architectural reference and native integration

* **ByeDPIAndroid** — GPL-3.0, copyright its contributors.
  Source: https://github.com/dovecoteescapee/ByeDPIAndroid
  License: https://github.com/dovecoteescapee/ByeDPIAndroid/blob/master/LICENSE
  Android implementation and settings flow reviewed at current `master`,
  revision `43db0955de687d43f9473374dc7c5548eaa55359`, and release `v1.2.0`,
  revision `9d289f096ed8373dd3734ab9966312c4c9e8d0aa`.
  Its local-VPN/JNI architecture was reviewed. The JNI boundary and local
  VpnService/SOCKS design in this project are adapted from that architecture
  and distributed under GPL-3.0-or-later. No source was copied without review.
* **ByeDPI** — MIT, copyright hufrea and contributors.
  Source: https://github.com/hufrea/byedpi
  License: https://github.com/hufrea/byedpi/blob/main/LICENSE
  Vendored as the `app/src/main/cpp/byedpi` Git submodule at
  `078842b084853bc30f33eaaec7acc510cf67e560` (ByeDPI 13), exactly the
  revision referenced by ByeDPIAndroid v1.2.0.
* **hev-socks5-tunnel** — MIT, copyright hev and contributors.
  Source: https://github.com/heiher/hev-socks5-tunnel
  License: https://github.com/heiher/hev-socks5-tunnel/blob/main/LICENSE
  Vendored as the `app/src/main/jni/hev-socks5-tunnel` Git submodule at
  `677bb4530cfc867cd44d88d298960c8d8d9fbfad`.

The native source is linked into the APK. Matching source, exact revisions and
license texts must accompany every distributed APK.

### Transitive native components

`hev-socks5-tunnel` includes `hev-socks5-server`, `hev-task-system` and its YAML
parser under the MIT license, plus lwIP under its BSD-style license. Full texts
are packaged as `hev-socks5-tunnel-MIT.txt`, `hev-task-system-MIT.txt`,
`yaml-MIT.txt` and `lwIP-BSD.txt`. ByeDPI's MIT text is packaged as
`ByeDPI-MIT.txt`.

## VLESS engine

* **sing-box for Android (SFA)** — GPL-3.0-or-later, copyright (C) 2022
  nekohasekai and contributors. The shared Android platform bridge and reload
  lifecycle were reviewed/adapted against SFA BoxService / VPNService.
  Source: https://github.com/SagerNet/sing-box-for-android
  The common DPI/VLESS service remains part of the GPL-compatible Android source.

* **sing-box / libbox** — GPL-3.0-or-later, copyright SagerNet and
  contributors. Source: https://github.com/SagerNet/sing-box
  Android binding source: https://github.com/singbox-android/libbox
  Published artifact: `libbox.aar` 1.14.0, downloaded only at build time from
  the binding project's release, SHA-256
  `13fbeabcb4622250762afa1b137daaba861a4dc1875643d18621339912f32330`.
  It powers the optional VLESS mode and is not affiliated with Local Route.

## Telegram WebSocket engine

* **tg-ws-proxy-rs** 2.5.4 — MIT, copyright (c) 2026 valnesfjord.
  Rust port of **Flowseal/tg-ws-proxy** (MIT, copyright Flowseal and contributors).
  Exact source: https://github.com/valnesfjord/tg-ws-proxy-rs/tree/a796423e561c558fcc7ff4ecfe3e5853a434ce84
  Also pinned as `app/src/main/rust/tg-ws-proxy-rs` Git submodule.
  Only the JNI core is included; the upstream Android UI is not included.
  Native library extracted from the official 2.5.4 universal APK, SHA-256:
  `04e40c1cab8dca82e81749ec9829bdc5d8420a301469e84b139b9172b5524f08`.
  The JNI class name is retained for binary compatibility.
* The complete upstream MIT license and notices for the pinned Cargo.lock's
  175 registry packages are bundled under `licenses/tgws/`. This is a conservative
  superset including host/dev dependencies. Each crate source archive was verified
  against its Cargo.lock checksum before extracting license files.
  Permissive license alternatives are selected where offered (MIT / Apache-2.0 /
  ISC); Unicode-3.0, BSD and CDLA-Permissive-2.0 texts are preserved as applicable.
  Regeneration/audit script: `scripts/audit-tgws-licenses.py`.

## Java/Kotlin dependencies

Telegram mode enables tg-ws-proxy-rs `--default-domains`: community CF relay
domain data is fetched from the Flowseal/tg-ws-proxy upstream at startup,
with upstream's built-in fallback. This is remote configuration data, not
downloaded executable code. Relays are third-party infrastructure, not operated
by Local Route. Source: https://github.com/Flowseal/tg-ws-proxy
The Telegram payload remains encrypted; relay operators can observe network metadata.

AndroidX and Jetpack Compose are consumed as normal Android libraries. Their
source is published under the Apache License 2.0, and Kotlin/coroutines are also
Apache-2.0 components. Their source/license details are available from
https://github.com/androidx/androidx and https://github.com/JetBrains/kotlin.
The complete common license text is packaged as `Apache-2.0.txt`.
