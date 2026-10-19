# Android 0.24.10: ChatGPT connection compatibility

Reproduced Chrome ERR_CONNECTION_CLOSED on the owner's WS/TLS VLESS profile.
The TUN routed chatgpt.com to a literal IPv6 destination. Through the authenticated
local VLESS gateway, both public IPv4 destinations completed TLS and returned
HTTP 403; the IPv6 destination closed during TLS. A 403 in this synthetic probe
is not treated as successful ChatGPT usage.

Server mode now answers AAAA and HTTPS DNS queries with empty NOERROR responses
only for chatgpt.com, openai.com, oaistatic.com and oaiusercontent.com (including
subdomains). The HTTPS filter prevents IPv6 hints bypassing the AAAA policy.
Normal A queries retain the existing encrypted DNS route. No static destination
IP is shipped. IPv6 remains enabled for other services and the VLESS endpoint.
DPI-only mode and application exclusions are unchanged.

Manual emulator check uses the owner-entered profile without exporting it.
The explicit test requires `-e manualProfile 1` and is skipped in normal suites.
Chrome loaded the ChatGPT page after the change. This does not verify login,
message generation, voice or all other server profiles. No server-side settings
were changed.
