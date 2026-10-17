# Community 0.24.2 — Android UI

- Overview now contains component controls only. Diagnostics, engine log and
  low-level activity events are under a collapsed Settings section.
- DPI services/profile and VPN routes/exclusions live in collapsed mode settings.
  Opening Settings from a component opens its own group. YouTube and Discord
  remain selected by default; existing explicit choices are preserved.
- The Android-only Windows startup checkbox is hidden. No reboot startup added.
- Telegram port is editable while stopped, persisted encrypted and used by the
  native command, the Telegram deep link and the MTProto connection test.
  Values outside 1024–65535 and the local DPI/VPN ports 1080/1081 are rejected.
  Reconnect Telegram using the regenerated link after changing its port.
- ANSI colour escapes and NOERROR/debug noise no longer clutter diagnostics.
  Duplicate pending DPI result is hidden; native confirmation controls remain.
- Compact settings, wrapping buttons/text, scroll padding and a bounded toast
  avoid oversized messages and horizontal overflow on small Android screens.

Verification: testReleaseUnitTest — 46 passing tests; debug/release builds and
release lint successful. SharedInterfaceTest — 2 passing emulator scenarios,
including saved port 2443, native arguments/deep-link consistency, contextual
settings, hidden desktop option and horizontal overflow check. This UI iteration
does not revalidate real user subscriptions or full Telegram messages/media.

The supplied Community 0.24.1 key is unavailable. The test APK is built with
applicationId org.localroute.community.preview, label Local Route Community Test,
versionCode 26/versionName 0.24.2, and the existing local Local Route signing key.
It installs beside Community, preserving that app and its data. Subscription
settings must be entered in the test app separately. The private signing key is
not in the repository. Default source builds keep org.localroute.community.

Preview build flags (in addition to a local signing properties file):

    -PapplicationId=org.localroute.community.preview
    -PapplicationLabel="Local Route Community Test"
    -PandroidTestBuildType=debug
    -PreleaseSigningFile=/absolute/path/to/release-signing.properties

Output: outputs/community-0.24.2/Local-Route-Community-Test-0.24.2.apk.
SHA256: 0A75249849A3100D79088207F906C8613B4D3BC7F596FB1F2D52841075FA403B.

During visual capture the API 37 emulator displayed a system-process ANR dialog.
After reboot the overview and settings rendered normally. Final UI tests were
repeated against the rebuilt assets. No claim about device/network stability is
based on the screenshots alone.
