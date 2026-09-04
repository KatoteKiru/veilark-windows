# Windows implementation notes

## Engine policy

Windows W1 uses two official upstream runtimes:

- sing-box 1.13.19, matching the current Android production core;
- TrustTunnelClient 1.1.5 stable for Windows x86_64, pinned to the official
  release asset SHA-256.

The Android TrustTunnel AAR/JNI is not reused. Veilark packages the official
Windows CLI, its setup wizard, WinTUN, and upstream licenses. `tt://` links are
compiled by the official setup wizard; endpoint and complete TrustTunnel TOML
files are also accepted.

## Runtime layout

- `%LOCALAPPDATA%\Veilark\runtime\active.json` — short-lived startup config,
  deleted immediately after sing-box accepts and starts it;
- `%LOCALAPPDATA%\Veilark\runtime\trusttunnel.toml` — short-lived TrustTunnel
  startup config, deleted after the client accepts and starts it;
- `%LOCALAPPDATA%\Veilark\profiles.dat` — profiles and selected engine,
  encrypted with current-user Windows DPAPI and written atomically;
- `%LOCALAPPDATA%\Veilark\logs\veilark.log` — bounded-input redacted journal;
- packaged resources — `sing-box.exe`, `trusttunnel_client.exe`,
  `setup_wizard.exe`, `libcronet.dll`, `wintun.dll`, pinned RU SRS rule sets,
  and licenses.

Profile configuration and its source are persisted only inside the DPAPI
container. There is no plaintext preference fallback.

Manual direct/VPN domain, IP, and CIDR rules plus TLS ClientHello fragmentation
are stored alongside the profiles and applied to a fresh sing-box configuration
before every connection. TrustTunnel keeps its own runtime configuration; the
sing-box-specific controls are not silently translated to it. RU presets split
DNS together with traffic: direct RU traffic uses the local resolver while the
VPN branch uses secure DNS. TrustTunnel uses DNS interception, CIDR exclusions,
and early SNI matching. Pre-resolution is enabled only for geographic or
manual domain exclusions and is capped at 50 queries, so QUIC/secure-DNS apps
can match exclusions without an unbounded startup DNS burst.

The selected node is persisted separately for each engine. Automatic sing-box
mode keeps the urltest outbound; explicit selection updates the final route,
secure DNS detour, and manual VPN rules together. TrustTunnel subscriptions
retain every `tt://` endpoint in the encrypted catalog and pass the selected
endpoint to the official setup wizard at connection time.

Import is panel-independent: 3x-ui, Remnawave, or another source may return a
plain URI list, Base64, recursive JSON envelope, sing-box/Xray JSON, or
Clash/Mihomo YAML. Mixed subscriptions populate both engine catalogs.

## Windows OTA

The Windows channel is isolated at
`https://nl2.senyasenyavski.uk:2096/veilark/windows/`. It reuses the Android
Ed25519 trust root but has its own manifest and installer. The client requires
the exact HTTPS host/port, verifies the manifest signature, enforces a 300 MiB
bound, supports HTTP Range resume, and verifies installer size and SHA-256
before launch. The signing key is never deployed to the server.

## Elevation and WinTUN

The packaged app starts normally without administrator rights. On Connect it
persists the current encrypted state, requests UAC through the Windows `runas`
verb, relaunches itself elevated, and resumes the selected connection
automatically. Cancelling UAC leaves the original UI open with an actionable
message. The elevated process waits for the asynchronously loaded DPAPI profile
and performs only one automatic attempt. The sing-box adapter is explicitly
named `Veilark`.

Tunnel readiness uses the Windows IP Helper API to match the adapter by its
real connection name (`Veilark` for sing-box, `TrustTunnel*` for TrustTunnel),
not by the driver description Java reports. `Connected` is emitted only after
the adapter is operational and a traffic probe confirms bytes move through it
together with a successful internet response. While connected, a periodic
health check moves the UI to `Degraded` if the core stays alive but traffic
bypasses the tunnel or stops, and returns it to `Connected` after recovery.
Orphaned core processes and non-operational WinTUN adapters are swept before
every connect and after every disconnect.

The production design is a signed, least-privilege helper/service reached
through an authenticated local IPC channel. UI, tray, and future hotkey actions
must call the same `VpnSession` facade. WFP kill-switch rules are not yet
implemented; `strict_route` is enabled but is not advertised as equivalent to a
complete WFP kill-switch.

## Troubleshooting

- `CORE_NOT_FOUND`: run `scripts/bootstrap-runtime.ps1`; it verifies both
  official engine archives against pinned SHA-256 values.
- `CONFIG_INVALID`: open the journal; secrets and URI credentials are redacted.
- `CORE_START_FAILED` with access denied: run the Gate A smoke elevated.
- `CORE_EXITED`: the selected engine stopped after connecting; inspect the
  redacted journal and retry. The UI never keeps showing a stale connected state.
- `CORE_START_FAILED` after TUN creation: the tunnel-bound traffic check failed;
  test another node and ensure no competing full-tunnel VPN owns the routes.
- `COMPETING_TUNNEL`: the named WinTUN/WireGuard adapter is already active;
  Veilark blocks startup before changing routes.
- Antivirus warning: verify the pinned hashes and upstream signatures. Do not
  substitute third-party WinTUN builds.

## Gate A manual check

1. Run `scripts/gate-a-smoke.ps1`.
2. Run `gradlew.bat :desktopApp:run` from an elevated terminal.
3. Import a test HTTPS subscription or supported sing-box URI.
4. Connect and confirm a changed public IP through the tunnel.
5. Disconnect and confirm route restoration.
6. Import a `tt://` or TrustTunnel endpoint TOML profile and repeat.
7. Repeat connect/disconnect ten times per engine and inspect the redacted log.

Do not run another full-tunnel VPN during route verification. Competing default
routes can make an otherwise connected Veilark tunnel lose traffic selection.
