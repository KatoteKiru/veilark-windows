# Veilark Windows 0.3.5 — release evidence

Release date: 2026-08-23

## Scope

- Replaces the wide sidebar with a compact 640×520 Material 3 shell: primary
  pages are in the hamburger menu and routing/language are under the gear.
- Restores one circular Connect/Stop/Disconnect control and a grouped server
  selector containing every nested endpoint from both cores.
- Splits DNS together with traffic for Russia-direct, Russia-through-VPN and
  manual routing instead of resolving every domain through the VPN.
- Bundles pinned RU geo rule sets, removing GitHub from the connection path.
- Updates TrustTunnel from 1.0.49 to 1.1.5-rc.6 and emits only settings supported
  by that core. Geo exclusions skip eager DNS pre-resolution at startup.
- Refuses to start a second TUN while another VPN adapter is active.
- Preserves the 0.3.x MSI UpgradeCode and the additive built-in Veilark Trust
  migration.

## Automated and static gates

- Shared/helper JVM tests and desktop compilation pass.
- Generated routing configurations pass bundled sing-box 1.13.14 `check`.
- Gate A automated smoke passes without starting a live tunnel.
- Production EXE/MSI build and MSI administrative extraction pass.
- Extracted package contains sing-box 1.13.14, TrustTunnel 1.1.5-rc.6 and both
  pinned RU SRS files with their expected hashes.
- Production resources contain exactly two non-fixture built-in TrustTunnel
  profiles; their private links are not printed or committed.
- OTA bootstrap extracts, hashes and removes the embedded installer in
  verification-only mode. The production wrapper requests elevation.

## Package identity

- Version code/name: `305` / `0.3.5`.
- Public OTA installer:
  `https://nl2.senyasenyavski.uk:2096/veilark/windows/Veilark-0.3.5.exe`.
- OTA wrapper size/SHA-256: `129656320` /
  `3B2F1AA49651FF54AD3C396EA317EC86443FD53793959A964F549B4E5473F76F`.
- Inner jpackage EXE size/SHA-256: `129648128` /
  `C85860F005C094B9088CA1793E6FEA1AB895C7FD5B14114FD0CFF165BE25967E`.
- MSI size/SHA-256: `129034177` /
  `DADA17221C4A099A5DE7587D7802DF8640A56E62084512D96FC15B8BD42BFBF6`.
- MSI ProductVersion: `0.3.5`.
- MSI ProductCode: `{237A7AE0-FFE8-3DE3-AC65-538AC35D73EF}`.
- MSI UpgradeCode: `{47A6CDD8-9630-4FA5-A2FD-C29C5774DC1A}` (unchanged).
- The live signed manifest passed the production `UpdateClient` validator.
- A complete public redownload matched the local OTA wrapper byte-for-byte.
- The wrapper is not Authenticode-signed; Windows can show an unknown-publisher
  warning.

The elevated 0.3.x→0.3.5 replacement and real traffic checks remain the
user-run gate in `PC_ACCEPTANCE_0.3.5.md`; no local VPN, route, DNS or installed
Veilark state was changed while preparing this release.
