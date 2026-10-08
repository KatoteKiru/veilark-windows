# TrustTunnel client incident evidence — 2026-10-08

## Binary and source correspondence

The packaged `packaging/resources/windows/trusttunnel_client.exe --version` reports `trusttunnel_client 1.1.7`. Its local SHA-256 is `86A5992D4F841DCF605DA374D2D74BAB035FB2C0D9B32719BDD9F80CFDD56C68`. The published Windows release document identifies TrustTunnel 1.1.7 for Veilark 0.3.23.

Local upstream source `C:/AI-Agent/tmp/trust-android117/source` is tag `v1.1.7`, commit `170609c24ca865819fed68437b01c013049bc3fa`. This is version-matched source inspection, not a claim of reproducing the Windows binary build.

## Confirmed diagnostic defect

In `net/src/tcp_socket.cpp:628-639`, native `tcp_socket_connect` calls `bufferevent_socket_connect`. On nonzero return it stores that return value directly in `error.code` and passes it to `evutil_socket_error_to_string`. The conventional failure return `-1` therefore becomes `Winsock error 0xffffffff (-1)` instead of the underlying socket error.

The captured FI native probe shows exactly this log path (`bufferevent_socket_connect returned error`, formatted Winsock -1). This proves the displayed error does not identify the real Winsock cause. It does not establish whether the underlying failure is routing, a local competing tunnel, firewall, remote refusal, or another cause. No production server repair can be justified from -1 alone.

Recommended native fix: capture the platform socket error immediately after a failed call, before additional operations can overwrite it; use the libevent/platform error API (`EVUTIL_SOCKET_ERROR()` / `WSAGetLastError()` on Windows), retain the function return separately, and avoid presenting -1 as an operating-system error code. Include a failing-connect regression that verifies a meaningful nonnegative platform error is preserved. No temporary/vendor source was patched or pushed, and no unsolicited upstream issue was opened.

## Transport and scope corrections

Windows delegates endpoint settings to the official setup wizard. Its routing transformer changes routing/DNS settings without forcing `anti_dpi`. Android separately forces `anti_dpi=true` and HTTP2. An initial hypothesis that Windows `anti_dpi=false` disables the configured ClientRandom was rejected: the server investigation successfully transferred real HTTPS using stored customer links with their unmodified default `anti_dpi=false`. No Windows policy change was made. Do not infer ClientRandom matching failure from that boolean.

The native Windows probe uses SOCKS without creating a TUN. An already active local tunnel can still influence its underlying network environment. Root investigation observed an active WinTUN interface 55 and Wi-Fi interface 6; this is context, not proof of interference. Do not disconnect the owner's live VPN merely to manufacture a clean result. Physical affected-device/version/network/location evidence remains required.

## Checks performed

- Original `TrustTunnelRoutingTest`: 10 tests passed, 0 failures/errors/skips. The first offline attempt lacked Kotlin 2.3.20 plugin cache; the subsequent normal dependency resolution and test build succeeded.
- Temporary tests based on the rejected anti-DPI hypothesis were removed before compilation; no anti-DPI policy change was made. The subsequent bounded endpoint DNS fix is described in RELEASE_0.3.24.md. Existing user files `docs/RELEASE_0.3.21.md` and `ui-research/` are preserved.
- No native rebuild, OTA, running owner VPN, or server service change.

Separate Android callback fix: draft `https://github.com/KatoteKiru/veilark-android/pull/10`, commit `e1d4cbe`; 188 JVM tests, 0 failures/errors, 3 gated skips. It corrects a same-session reconnect UI gate and is not established as the cause of the entire outage.

## Actual installed-app evening evidence

Read-only inspection found active native PID17352 running `C:/Program Files/Veilark/app/resources/trusttunnel_client.exe`, version1.1.7. Its per-run locked configuration has already been deleted after startup, as expected from the controller's `finally` cleanup; current endpoint flags cannot be recovered from that file. No process was stopped.

The real local `Veilark/logs/veilark.log` shows the following timeline on October7, Moscow time (UTC+3):

| Time | Actual installed-app event |
| --- | --- |
| 22:37:27–28 | Native CONNECTED, endpoint ready, then adapter ready. |
| 22:41:37–38 | Native health check timed out (error1), then RECOVERING. |
| 22:46:39 | Failed to resolve endpoint address (public NL token present); failed to resolve any endpoint or relay addresses; startup exit1. |
| 22:46:55 | Same endpoint DNS failure and startup exit1. |
| 22:47:31/36/41 | Failed to ping location repeatedly. |
| 22:47:52 | Native CONNECTED and adapter ready. |
| 22:47:59–22:48:00 | Native health check timed out (error1), then RECOVERING. |
| 22:50:00–20 | Five location ping failures; Error9, number of connection attempts exceeded; process exited normally (exit0), so this is not evidence of a native crash. |
| October8 00:40:09–10 | New start, native CONNECTED, endpoint ready, adapter ready. This is the currently active process. |

The log's last write is October7 21:40:10UTC, corresponding to that final successful startup. Its warn/info configuration provides no raw socket errno or reliable transport-reset classification for the evening failures. These actual-app events confirm a real DNS bootstrap failure and established-session health timeouts; they do not identify the underlying provider/network failure. The later standalone SOCKS probe remains a separate observation.
