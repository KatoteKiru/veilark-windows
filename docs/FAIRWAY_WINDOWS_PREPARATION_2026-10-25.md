# Fairway Windows preparation — 25 October 2026

Local source preparation, **not a working Windows Core release**. No version bump,
installer installation, OTA, server/client registration or network modification.
Baseline `0a4d12c` is code-equivalent to freshly fetched upstream `a18db2df`.
Keep the existing interface and the owner's unrelated untracked files.

Implemented:

- Byte-identical Android `CoreArgonProfile`/strict JSON codecs for direct v1/v2.
  Exact schemas, duplicate decoded keys, key encoding, MTU and addresses are
  validated. Passage cannot be mistaken for a direct Fairway profile.
- Windows DPAPI-backed `FairwayDeviceKeyStore`, using JDK X25519. An opaque
  binding scope and installation identity select a stable local key. Concurrent
  creators return the same persisted winner. Corruption/mismatch fails without
  rotation or replacement. Protected staging is published create-only; a file
  system without hard-link support fails rather than weakening this contract.
- Closeable key owner with redacted strings and managed private-array cleanup.
  Copies internal to the JVM cryptographic provider cannot be guaranteed erased.
- Core documents do not fall through to the sing-box importer. Until a genuine
  Windows Core backend exists, import reports unavailable support without exposing
  raw credentials. Engine selection and current legacy connections are unchanged.
- Logs redact Fairway private-key fields, installation ID and Veilark bearer
  links, including quoted JSON. Packaged JRE explicitly retains `jdk.crypto.ec`;
  its absence is rejected by the packaging verifier.

Verification:

- Shared/helper + desktop: **286 cases, 0 failures, 0 errors, 4 gated skips**.
  Nine strict-profile and eight device-key cases include a real JDK X25519 peer
  exchange and a six-way concurrent create. Existing Windows DPAPI roundtrip
  also passed in the helper suite.
- Desktop compilation and isolated JDK17 runtime creation passed. Runtime release
  metadata and actual jimage contain `jdk.crypto.ec`, `SunEC` and XDH keygen.
  Full installer, packaged launcher and real Windows VPN were not tested.
- Packaging check initially needed the existing Compose JDK probe downloaded;
  Android JBR lacked jpackage. Final successful run used already-installed full
  Temurin17. Earlier infrastructure failures remain in their separate logs.

Evidence: `C:/AI-Agent/reports/windows-fairway-20261025/validation.json` and logs.

Subscription decision requiring Core/control-plane integration:

**One account, payment, expiry and updater; separate keys for phone and PC.**
The current server replaces the active session sharing the same client static
key (also confirmed in deployed-source `3fea69e`). Do not copy a static profile
to both devices or disable reconnect replacement. Existing commercial devices
already provide three slots, durable installation observations, idempotent
activation and owner-scoped matching. Fairway is not yet wired into that model.
An installation ID is not hardware attestation or sufficient authorization.

Next shared contract: `C:/AI-Agent/reports/windows-fairway-20261025/TEAM_CONTRACT.md`.
Vector ownership requested for native Windows Wintun/UDP backend, explicit Core
provider, atomic config+journal publication and per-device session revocation.
Atlas owns client identity/import/storage integration once the server contract
is agreed. Tool delivery acknowledgement is unavailable; request is also in the
Core checkout handoff. Do not claim it was accepted by Vector.

Existing Android84 and existing subscriptions remain the protected baseline.
