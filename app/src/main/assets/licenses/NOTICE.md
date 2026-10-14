# Local Route Android — third-party notices

## Licence of this Android project

Local Route Android is GPL-3.0-or-later. A complete license copy is packaged as
`GPL-3.0.txt`. Corresponding source must be offered alongside any published APK;
this repository is the intended source location when publication is authorised.

## Architectural reference / planned native integration

* **ByeDPIAndroid** — GPL-3.0, copyright its contributors.
  Source: https://github.com/dovecoteescapee/ByeDPIAndroid
  License: https://github.com/dovecoteescapee/ByeDPIAndroid/blob/master/LICENSE
  It is an architectural reference; no code or binary from it is in this MVP.
* **ByeDPI** — MIT, copyright hufrea and contributors.
  Source: https://github.com/hufrea/byedpi
  License: https://github.com/hufrea/byedpi/blob/main/LICENSE
  Planned only as a locally compiled SOCKS5 DPI engine.
* **hev-socks5-tunnel** — MIT, copyright heiher and contributors.
  Source: https://github.com/heiher/hev-socks5-tunnel
  License: https://github.com/heiher/hev-socks5-tunnel/blob/main/LICENSE
  Planned only as the local TUN-to-SOCKS bridge.

If any of these native sources is vendored or linked later, retain its copyright
and license texts here, record exact revision hashes, and make the matching
corresponding source available with every distributed APK.

## Java/Kotlin dependencies

AndroidX and Jetpack Compose are consumed as normal Android libraries. Their
license metadata is resolved by Gradle; their source/license details are linked
from https://developer.android.com/jetpack/androidx/releases and
https://github.com/androidx/androidx.
