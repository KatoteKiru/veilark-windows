# Veilark Windows 0.3.16

Fix the SingBox startup logging contract. Imported subscriptions use warning-level
logging, but the Windows readiness gate waits for an INFO startup marker before
accepting an operational Veilark adapter. The marker was suppressed, causing a
30-second timeout even when the process was serving traffic.

The controller now creates a process-only logging configuration that emits the
marker to stdout. Stored profiles, credentials, routing rules, native runtime
versions, MSI UpgradeCode and OTA trust key are unchanged. The existing
process/adapter/marker readiness checks remain enabled.

Routine per-connection INFO output is drained without being retained in the
diagnostic tail or written to the journal. Startup, warning, error and fatal
diagnostics are retained in redacted form. Journal I/O failure cannot prevent
readiness detection or stop draining the core's output pipe.

Regression tests cover warn/error/disabled/file logging imports, unchanged routing
content, dropped traffic INFO, redacted fatal/tail messages and journal failures.
A native localhost SOCKS test proves that warning-level logging suppresses the
marker while the core is alive and answering; normalization restores the marker.
This test creates no TUN adapter and changes no system routes.

Release gates: verify installed-client upgrade and real elevated tunnel traffic
separately. Passing parser/native configuration checks or localhost SOCKS startup
is not physical full-tunnel acceptance. Do not substitute a new signing key.

## Adapter lifecycle and published evidence

The controller also recovers a stopped adapter left by an older failed startup,
using exact native identity and a uniquely matched PnP instance. Removal refuses
active, ambiguous or changed adapters. Normal stop captures the owned identity
before terminating the core and observes bounded Windows state propagation.
PowerShell commands use UTF-16LE encoded argument transport to preserve quoting;
this changes transport, not the deletion policy. TrustTunnel runtime is unchanged.

Release source: `778e421406df80fb5ccabcfd5867ecca74f6a884`.
[Windows CI](https://github.com/KatoteKiru/veilark-windows/actions/runs/33984529120)
passed, including a genuine forced legacy adapter and two complete native
start/stop cycles: executed 1, skipped 0, failures 0, errors 0. The synthetic
fixture adds no default route and does not test remote VPN traffic.

OTA 316 and the Preview catalog were published with the previous Ed25519 key.
Complete public download and GitHub asset hashes agree:
`83ec7a938215d546e6f34b06534d0ee7704544ff2bb14ec9398f75de0efa6bda`,
130526720 bytes. Installer Authenticode status remains NotSigned; this is not a
trusted Windows publisher signature. Physical upgrade and real remote traffic
acceptance remain outstanding. No throughput or energy-efficiency benchmark is
claimed by this release.
