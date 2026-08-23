# Veilark Windows 0.3.4 — release evidence

Release date: 2026-08-23

## Scope

- Restores mandatory sing-box TUN sniff, DNS hijack and private-network direct
  rules in every routing preset.
- Makes Russia-through-VPN use direct DNS for its direct default branch.
- Expands TrustTunnel suffix rules to apex plus wildcard and enables early route
  selection for domain exclusions.
- Uses a compact 940×640 Material 3 shell with a 780 dp workspace and persistent
  Russian/English UI selection.
- Verifies the actually installed `jpackage.app-version` before a future update
  can be recorded as successful.

## Automated gates

- Shared and helper JVM tests pass.
- Generated routing configurations pass bundled sing-box 1.13.14 `check`.
- Fixture-only GitHub CI passes without production Trust links; production
  packaging rejects fixture profiles.
- Release EXE/MSI build and MSI administrative extraction pass.
- Packaged UI smoke covers Home, RU/EN switching and the hierarchical nested
  TrustTunnel server selector.

## Published OTA and package identity

- Version code/name: `304` / `0.3.4`.
- Public installer:
  `https://nl2.senyasenyavski.uk:2096/veilark/windows/Veilark-0.3.4.exe`.
- Public wrapper size/SHA-256: `130507776` /
  `B1575D7D1C44CB5656FE676D02A717DC97C20C600F1EB194C72CE8FF62AE04F0`.
- Inner jpackage EXE size/SHA-256: `130499584` /
  `6E9223E71B6417AA38E1F4B29F801FD03FEE605C32B47F843151288B741D308D`.
- MSI size/SHA-256: `129885840` /
  `814A07D455305E66512633328233C3144907E7889A1767BB89F29F5C6ADE3AC5`.
- MSI ProductVersion: `0.3.4`.
- MSI ProductCode: `{A40ABD5C-2C93-34C3-860B-4FC6E794DDCF}`.
- MSI UpgradeCode: `{47A6CDD8-9630-4FA5-A2FD-C29C5774DC1A}` (unchanged from
  0.3.1 and 0.3.3).
- Live signed manifest passed the production `UpdateClient` validator.
- A complete public redownload matched the local wrapper byte-for-byte.
- The wrapper is not Authenticode-signed; Windows may show an unknown publisher.

Elevated real-PC traffic and the actual 0.3.1→0.3.4 replacement remain the
user-run gate in `PC_ACCEPTANCE_0.3.4.md`.
