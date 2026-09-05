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
