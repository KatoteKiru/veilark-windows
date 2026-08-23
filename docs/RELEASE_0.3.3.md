# Veilark Windows 0.3.3 — release evidence

Release date: 2026-08-23

## Published OTA

- Version code/name: `303` / `0.3.3`
- Public installer:
  `https://nl2.senyasenyavski.uk:2096/veilark/windows/Veilark-0.3.3.exe`
- Size: `130542080` bytes
- SHA-256:
  `7FE90B6347725156A07D1F7DB70E4C590CCA94E90614F7388BE6A95BF0DD7228`
- Live manifest and local signed manifest were equal.
- The Ed25519 signature was accepted by the production `UpdateClient` parser.
- A complete public redownload matched the local release byte-for-byte.
- Installed 0.3.1 contains the same manifest URL and update public key; live
  version code 303 is newer than its version code 301.

## Package identity

- Inner EXE size/SHA-256: `130475008` /
  `A0AFD1A54A4FDB6F8CE7A7C2035747852AF55364A428EB3882052CCC7A0ABA0A`
- MSI size/SHA-256: `129861263` /
  `D9A11C9D759FEDC355F3542A5F36D5080DE42EB9745E66854C37FFB807C6B6F6`
- MSI ProductVersion: `0.3.3`
- MSI ProductCode: `{D44C65BE-E1BC-3C02-B5B6-4F43292944F3}`
- MSI UpgradeCode: `{47A6CDD8-9630-4FA5-A2FD-C29C5774DC1A}`
- The same UpgradeCode resolves to installed 0.3.1 ProductCode
  `{D2162046-D87C-3308-92D4-1EBAA6F53BFE}`.
- OTA bootstrap requests administrator elevation and verified the embedded EXE
  size and SHA-256 before launch; verification left no extracted payload.

## Automated gates

- shared JVM: 36 tests, 0 failures/errors, 2 intentional skips.
- helper JVM: 71 tests, 0 failures/errors/skips.
- Desktop compilation, release EXE and release MSI: successful.
- Packaged helper bytecode contains:
  - the later TrustTunnel marker `Successfully connected to endpoint`;
  - `requireReadyMarker=true` passed to tunnel readiness;
  - no synchronous `awaitHealthy` call in TrustTunnel startup;
  - Windows `curl.exe`/Schannel probing and no `HttpURLConnection`;
  - geo preflight and TrustTunnel geo-routing classes.
- Packaged desktop JAR contains exactly two embedded `tt://` profiles without
  recording their contents in this report.
- Official Russian rule-set files are pinned to immutable repository commits,
  matched their expected SHA-256 values, and were successfully decompiled by
  bundled sing-box during visual QA.
- Visual QA in an isolated `%LOCALAPPDATA%` verified Home, hierarchical node
  selector, Profiles, Routing, Updates and full-height Journal without changing
  installed user profiles.

## Remaining user-run gate

The release is published for the requested PC test, but elevated real-tunnel
traffic and route ownership cannot be claimed from build/package tests. Complete
`PC_ACCEPTANCE_0.3.3.md` for both cores and all geo presets. Preserve the profile
store and copy the redacted Journal if any step fails.
