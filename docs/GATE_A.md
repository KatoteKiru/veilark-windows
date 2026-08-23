# Gate A report

## Done

- Compose Desktop shell with a desktop navigation rail and Material 3 theme;
- KMP `shared` module with the Android 0.8.0-rc4 subscription parser snapshot;
- `VpnSession` state machine, two-engine dispatch, and single-core mutex;
- crash watchdog for both engines with stale-connected-state prevention;
- packaged Connect → UAC `runas` → encrypted-state restore → automatic
  connection flow;
- sing-box 1.13.14 check/start/stop process lifecycle;
- TrustTunnelClient 1.0.49 setup/start/stop process lifecycle for `tt://` and
  TOML profiles;
- official x64 WinTUN 0.14.1, sing-box, and TrustTunnel bootstrap with pinned
  SHA-256;
- short-lived active config and a rotating redacted log;
- current-user DPAPI profile storage with atomic writes and fail-closed
  recovery;
- live parallel diagnostics for Cloudflare, YouTube, ChatGPT, OpenAI, and
  Gemini;
- working sing-box manual domain/IP/CIDR routing and TLS fragmentation UI,
  configuration mutators, validation, and encrypted persistence;
- automatic/manual node selection with route and DNS detour mutation;
- complete nested-node catalogs for sing-box and TrustTunnel, including mixed
  3x-ui/Remnawave/plain subscriptions and encrypted endpoint persistence;
- Home-screen nested-node dropdown, persisted HTTPS subscription refresh, and
  parallel per-node latency probes for both engines;
- tray connect/disconnect/open/quit actions, notifications, start-minimized
  mode, file drag-and-drop, and redacted log copy;
- signed Windows OTA client and published isolated `/veilark/windows/` channel;
- native release MSI/EXE installers and self-contained app-image build;
- strict Material 3 desktop adaptation of the Android UI: tonal surfaces,
  one primary connection action, permanent navigation, standard list items,
  branded window icon, dropdown node selector, short state transitions, and
  honest empty states.

## Artifact

Release installers:

- `desktopApp/build/compose/binaries/main-release/exe/Veilark-0.2.2.exe`
- `desktopApp/build/compose/binaries/main-release/msi/Veilark-0.2.2.msi`

## Verified

- Kotlin/Compose compilation on JDK 17;
- 41 parser/import/routing/update/diagnostics and helper tests: 0 failed,
  1 platform-conditional test skipped;
- production subscription compile accepted by packaged sing-box 1.13.14;
- isolated live sing-box SOCKS traffic passed without changing system routes;
- live Windows OTA manifest signature validation, HEAD, and Range passed;
- 7 helper/session/storage/log tests: 0 failed, including a real DPAPI
  protect/unprotect round trip;
- generated TUN config accepted by official sing-box 1.13.14;
- installed 0.2.2 executable cold-starts and remains responsive;
- installed-package smoke confirms complete JNA (2,002,994 bytes) and
  JNA-platform (1,380,744 bytes) runtimes;
- visual QA at 1166×753 for Home, both engine selectors, the nested-node
  dropdown, subscription refresh, per-node latency values, and tray close;
- the release JNA regression was reproduced from 0.2.0, traced to release
  shrinking, fixed by retaining native JNA resources, and covered by an
  installed-package smoke check;
- EXE and MSI 0.2.2 produced at 129,954,816 and 129,341,070 bytes.

## Gate still open

The remaining acceptance check needs disposable production-compatible profiles
for both engines and an elevated Windows 11 session:

1. import;
2. Connect → WinTUN up → public IP changed;
3. Disconnect with route restoration;
4. repeat ten times.

No production credential is committed. WFP kill-switch, Authenticode signing,
and a signed service helper remain Gate B/C work.

## Live evidence

- TrustTunnelClient 1.0.49 created WinTUN 0.14 after automatic UAC elevation;
- endpoint selection completed at 77 ms and runtime reported
  `VPN_SS_CONNECTED`;
- observed public address: `144.31.123.127`;
- Disconnect stopped the runtime and removed the TrustTunnel adapter cleanly;
- a separate active `happ-tun` VPN prevented an isolated sing-box route test
  and was intentionally not modified.
