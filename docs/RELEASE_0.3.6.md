# Veilark Windows 0.3.6 — release evidence

Release date: 2026-08-23

## Scope

- Reduces the Material 3 shell to 600×440, combines connection/status/engine in
  one block, and keeps secondary pages behind hamburger and gear menus.
- Keeps a grouped Home selector with every nested endpoint of the selected core,
  subscription grouping, selection state and latency evidence.
- Makes sing-box routing actions explicit and raises TLS/QUIC sniff timeout to
  one second so geo/domain rules are less likely to fall back to IP only.
- Enables bounded TrustTunnel exclusion pre-resolution and aligns both cores on
  MTU 1280 to reduce partial HTTPS/QUIC loading caused by classification and PMTU
  failure modes.
- Prevents Connect from racing a running Stop, makes TrustTunnel ping process
  capture cancellable, and keeps session monitoring alive after counter/probe
  exceptions.
- Bounds subscription and OTA manifest bodies during receipt and improves
  cancellation/resume of OTA downloads.
- Preserves the 0.3.x MSI UpgradeCode, DPAPI state and additive built-in Veilark
  Trust migration.

## Automated and static gates

- 40 shared and 80 helper JVM tests pass with zero failures; desktop compilation
  and Gate A smoke pass without starting a live tunnel.
- All, Manual, RussiaDirect and RussiaVpn generated sing-box configurations pass
  bundled sing-box 1.13.14 `check` with bundled RU SRS data.
- Isolated RU/EN Home and grouped-picker renders pass at default/minimum sizes.
- Production EXE/MSI build passes. MSI administrative extraction contains
  sing-box 1.13.14, TrustTunnel/setup wizard 1.1.5-rc.6 and both pinned RU SRS
  files. The verification bootstrap extracts, hashes and removes its payload.

## Package identity

- Version code/name: `306` / `0.3.6`.
- Public OTA installer:
  `https://nl2.senyasenyavski.uk:2096/veilark/windows/Veilark-0.3.6.exe`.
- OTA wrapper size/SHA-256: `129680896` /
  `353050C82D81B623BC17F4A068E6E7E67FE0A7F3E6F7EB42F4DF523A8D41FD28`.
- Inner jpackage EXE size/SHA-256: `129672704` /
  `5F29156C40BA192160BE4751B2BB73FFEBB6F55F846E5E076C3379DCA24A27F3`.
- MSI size/SHA-256: `129058754` /
  `172F90F75CA2ABC43B9EDAD9BA1F2AC1C0D266BD325F9D4D5E5869B93F49BF64`.
- MSI ProductVersion: `0.3.6`.
- MSI ProductCode: `{0B3E8250-4581-343E-AD6B-9F5E00F67C59}`.
- MSI UpgradeCode: `{47A6CDD8-9630-4FA5-A2FD-C29C5774DC1A}` (unchanged).
- The production wrapper contains a `requireAdministrator` manifest; it was not
  executed during release preparation.
- The live signed manifest passed the production `UpdateClient` Ed25519
  validator.
- A complete public redownload matched the local OTA wrapper byte-for-byte by
  size and SHA-256.
- The wrapper is not Authenticode-signed; Windows can show an unknown-publisher
  warning.

The elevated 0.3.x→0.3.6 replacement and real traffic checks remain the
user-run gate in `PC_ACCEPTANCE_0.3.6.md`. No local VPN, route, DNS or installed
Veilark state is changed while preparing this release.
