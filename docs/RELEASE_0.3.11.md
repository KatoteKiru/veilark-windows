# Veilark Windows 0.3.11 — release evidence

Release date: 2026-08-26

## Scope

- Updates the bundled stable sing-box core from 1.13.14 to 1.13.19.
- Pins the official Windows amd64 archive SHA-256 published by upstream.
- Identifies subscription requests as sing-box 1.13.19.
- Keeps TrustTunnel 1.1.5-rc.6, WinTUN 0.14.1, stored subscriptions, routing,
  and selected endpoints unchanged.

## Verified gates

- 129 shared/helper JVM tests: zero failures or errors, two environment-gated
  skips.
- Desktop compilation and Gate A passed; the generated TUN configuration was
  accepted by bundled sing-box 1.13.19.
- EXE and MSI packaging passed. Administrative MSI extraction reported
  sing-box 1.13.19 and TrustTunnel 1.1.5-rc.6 and retained the pinned RU SRS
  hashes.
- The non-elevating bootstrap verification extracted and hashed the embedded
  installer and left no payload behind.
- The published OTA installer was downloaded in full and matched the local
  SHA-256 byte-for-byte.

## Published artifact

- Version code/name: `311` / `0.3.11`.
- URL: `https://nl2.senyasenyavski.uk:2096/veilark/windows/Veilark-0.3.11.exe`.
- Size: `130995712` bytes.
- SHA-256: `4ED909DD01769C485DFDB7F652AA69CA576B3418C99063210AF4D0569090D0ED`.
- The OTA wrapper is not Authenticode-signed; Windows may show an
  unknown-publisher warning.

An elevated live TUN traffic test is intentionally not claimed by automated
build/package checks. Complete `PC_ACCEPTANCE_0.3.11.md` on the target PC.
