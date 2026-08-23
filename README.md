# Veilark for Windows

Native Windows client for Veilark built with Kotlin and Compose Multiplatform.
The Windows client follows the Android product model while using a desktop
layout and an explicit `VpnSession` boundary.

## Current status

Windows 0.3.10 release candidate is implemented:

- Compose Desktop shell with compact desktop navigation and persistent RU/EN actionable states;
- tunnel detection through the Windows IP Helper API, so the adapter is matched
  by its real connection name instead of the driver description Java reports;
- WinTUN ghost adapters and orphaned core processes are swept before every
  connect and after every disconnect;
- live adapter byte counters, session uptime, and an explicit elevation notice on
  the Home screen;
- the Android `SubscriptionParser` is reused for URI lists, Base64, sing-box /
  Xray JSON, and Clash/Mihomo YAML;
- one-process-at-a-time `VpnSession` with real sing-box / TrustTunnel engine
  selection and strict core-marker plus operational-adapter readiness;
- non-blocking post-connect traffic diagnostics through the Windows system
  `curl.exe` / Schannel, with process exit reported as `CORE_EXITED`;
- automatic UAC relaunch on Connect with encrypted state handoff, profile-load
  synchronization, and exactly one auto-connect attempt after elevation;
- official sing-box 1.13.14, TrustTunnel 1.1.5-rc.6, and WinTUN 0.14.1 bootstrap
  with pinned SHA-256;
- `sing-box check` before every sing-box launch and the official TrustTunnel
  setup wizard for `tt://` and endpoint TOML profiles;
- short-lived config under `%LOCALAPPDATA%\Veilark` (deleted after core start);
- profiles and selected engine persisted in a current-user DPAPI encrypted store;
- parallel HTTPS diagnostics for Cloudflare, YouTube, ChatGPT, OpenAI, and
  Gemini with authentication failures classified as reachable;
- four routing presets for both engines: all traffic through VPN, Russia
  direct, Russia through VPN, and manual domain/IP/CIDR rules;
- installer-bundled, hash-pinned RU SRS data for sing-box and generated
  TrustTunnel exclusions; split routing never waits for GitHub before connect;
- TLS ClientHello fragmentation applied to sing-box before each connection;
- automatic or explicit sing-box node selection, including DNS detour updates;
- panel-independent 3x-ui/Remnawave/plain subscription import with recursive
  JSON envelopes, Base64, URI lists, sing-box/Xray JSON, and Clash YAML;
- explicit sing-box subscription negotiation for panels that dispatch formats
  by User-Agent, including Remnawave and modern x-ui;
- all nested sing-box nodes and TrustTunnel endpoints retained in one bounded,
  scrollable Material 3 server picker on Home and Profiles, with exact endpoint
  counts, country flags and a visible scrollbar; mixed subscriptions populate
  both engines without intrinsic-layout crashes;
- persisted HTTPS subscription sources with manual refresh and a parallel
  per-node TCP latency probe for both engines;
- system tray actions and notifications, start-minimized support, file
  drag-and-drop, and redacted log copy;
- signed Windows OTA channel with HTTPS origin allowlist, Ed25519 manifest
  verification, resumable downloads, size bounds, and installer SHA-256;
- compact Google Material 3 shell with a top app bar, bottom navigation,
  Noto Sans, drawn country flags, and a single update progress indicator;
- redacted technical journal;
- release MSI and EXE installers.

Gate A is not declared complete until imported production profiles for both
engines pass elevated live TUN connect/disconnect tests on Windows 11. No test
credential is stored in this repository.

## Build

Requirements: Windows 10 22H2 or Windows 11, x64, JDK 17+, PowerShell 5.1+.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\bootstrap-runtime.ps1
.\gradlew.bat test
.\gradlew.bat :desktopApp:run
```

Creating installers (the repository downloads the pinned WiX toolset):

```powershell
.\gradlew.bat :desktopApp:packageReleaseMsi
.\gradlew.bat :desktopApp:packageReleaseExe
```

Release artifacts:

- `desktopApp/build-isolated/compose/binaries/main-release/exe/Veilark-0.3.10.exe`
- `desktopApp/build-isolated/compose/binaries/main-release/msi/Veilark-0.3.10.msi`

After installing, verify the package and the unstripped JNA runtime:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-installed.ps1 -ExpectedVersion 0.3.10
```

Releases 0.3.2 and 0.3.3 were withdrawn. Version 0.3.8 fixes sing-box format
negotiation, imports nested server groups, and makes every location visibly
discoverable in the compact selector without changing the Material 3 palette. The signed
public OTA channel uses a self-elevating bootstrap so an installed 0.3.0 or
0.3.1 can be replaced in place after the user accepts the Windows UAC prompt.
The artifacts are not Authenticode-signed, so Windows SmartScreen may show the
publisher as unknown. Real-PC tunnel acceptance is still required before calling
0.3.8 fully accepted. A signed least-privilege Windows Service and WFP kill-switch
remain required before declaring a final 1.0 security release.

See [Windows notes](docs/WINDOWS.md) and the
[parity matrix](docs/PARITY.md). The current acceptance evidence is recorded in
[0.3.8 release evidence](docs/RELEASE_0.3.8.md); the remaining elevated checks
are listed in [PC acceptance](docs/PC_ACCEPTANCE_0.3.8.md).
