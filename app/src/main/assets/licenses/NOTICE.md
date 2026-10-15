# Local Route Android — third-party notices

## Licence of this Android project

Local Route Android is GPL-3.0-or-later. A complete license copy is packaged as
`GPL-3.0.txt`. Corresponding source must be offered alongside any published APK;
this repository is the intended source location when publication is authorised.

## Architectural reference and native integration

* **ByeDPIAndroid** — GPL-3.0, copyright its contributors.
  Source: https://github.com/dovecoteescapee/ByeDPIAndroid
  License: https://github.com/dovecoteescapee/ByeDPIAndroid/blob/master/LICENSE
  Android implementation and settings flow additionally reviewed at release
  `v1.2.0`, revision `9d289f096ed8373dd3734ab9966312c4c9e8d0aa`.
  Its local-VPN/JNI architecture was reviewed. The JNI boundary and local
  VpnService/SOCKS design in this project are adapted from that architecture
  and distributed under GPL-3.0-or-later. No source was copied without review.
* **ByeByeDPI** — GPL-3.0, copyright its contributors.
  Source: https://github.com/romanvht/ByeByeDPI
  License: https://github.com/romanvht/ByeByeDPI/blob/master/LICENSE
  Revision reviewed: `4ca350c1831c405010cd7548c3d89ddbbe68f68a`.
  Its maintained ByeDPI 17 JNI lifecycle and Android build configuration were
  reviewed and adapted. These adaptations remain GPL-3.0-or-later.
* **ByeDPI** — MIT, copyright hufrea and contributors.
  Source: https://github.com/hufrea/byedpi
  License: https://github.com/hufrea/byedpi/blob/main/LICENSE
  Vendored as the `app/src/main/cpp/byedpi` Git submodule at
  `ba532298de7b28cfe854aea83d061369d13ca290` (ByeDPI 17.3).
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

## Java/Kotlin dependencies

AndroidX and Jetpack Compose are consumed as normal Android libraries. Their
source is published under the Apache License 2.0, and Kotlin/coroutines are also
Apache-2.0 components. Their source/license details are available from
https://github.com/androidx/androidx and https://github.com/JetBrains/kotlin.
The complete common license text is packaged as `Apache-2.0.txt`.
