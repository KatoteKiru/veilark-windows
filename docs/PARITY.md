# Android 0.8.0-rc4 → Windows parity

Status values: **yes**, **partial**, **planned**, **Android-only**.

| Capability | Windows | Notes |
|---|---:|---|
| Native desktop UI | yes | Compose Desktop, 600×440 shell, hamburger navigation and gear settings |
| sing-box 1.13.19 | yes | Official Windows amd64 binary, pinned SHA-256 |
| TrustTunnel 1.1.5-rc.6 | partial | Official pinned Windows x86_64 client/setup wizard; config and package gates pass, elevated 0.3.8 traffic QA is user-run |
| Connect / disconnect facade | partial | Both runtimes and watchdog verified; isolated sing-box full-TUN route QA waits for competing Happ VPN to be closed |
| UAC elevation | yes | Connect relaunches packaged Veilark with `runas` and resumes automatically |
| HTTPS subscription import | yes | HTTPS-only, 4 MiB bound, rejects HTML; negotiates sing-box responses with Remnawave and modern x-ui |
| Paste / file import | yes | URI, Base64, JSON, YAML |
| VLESS / VMess / Trojan / SS | yes | Shared Android parser snapshot |
| Hysteria2 / TUIC / AnyTLS | yes | Shared Android parser snapshot |
| Xray JSON | yes | Compiled to sing-box |
| Clash/Mihomo YAML | yes | Safe SnakeYAML parser |
| Profile persistence | yes | Current-user DPAPI container; atomic write; no plaintext fallback |
| Node selection | yes | Home and Profiles retain all entries, show exact endpoint counts, flags and visible scrolling; selection persisted with DPAPI |
| Subscription refresh | yes | Original HTTPS source is encrypted with DPAPI and can be refreshed manually |
| Node latency | yes | Bounded parallel TCP probe for all nodes of either engine |
| All-traffic routing | partial | Generated TUN config and core checks pass; elevated 0.3.8 live QA pending |
| Manual / RU split routing | partial | Bundled RU SRS, direct foreign TrustTunnel path, preserved profile MTU and UI complete; elevated live route QA pending |
| Process-based routing | planned | UI explicitly limits current rules to domain, IP, and CIDR |
| TLS fragmentation | partial | Applied to compatible sing-box TLS outbounds; reconnect QA pending |
| Diagnostics matrix | yes | Parallel HTTPS probes; OpenAI/Gemini auth failures count as reachable |
| Tray | yes | Connect, disconnect, open, quit, state tooltip, and notifications |
| OTA Ed25519 + SHA-256 | yes | Separate published Windows channel; HTTPS allowlist, resume, size and hash verification |
| MSI / EXE | partial | Release installers built and hashed; Authenticode certificate still required |
| Kill-switch | planned | WFP helper required; strict_route alone is insufficient |
| QR camera import | Android-only | Optional Windows Phase C |
| Quick Settings tile | Android-only | Replaced by tray in Gate B |

## Better on Windows

- compact desktop app bar and popup navigation instead of a persistent sidebar;
- native file chooser and file drag-and-drop;
- explicit runtime log location and packaged native-core licenses.

## Known Gate A risks

- helper is not yet an installed signed Windows Service;
- elevated live TUN and clean route restoration are not automated;
- runtime binaries are x64 only;
- current parser sharing is a source snapshot; the next repository unification
  should make Android and Windows consume one KMP artifact.
