# Veilark Windows 0.3.8 — release evidence

Release date: 2026-08-23

## Scope

- Identifies HTTPS subscription requests as sing-box 1.13.14 while retaining a
  separate Veilark client header. This lets Remnawave and modern x-ui response
  rules return their sing-box representation instead of an ambiguous fallback.
- Imports supported outbounds from nested JSON envelopes, embedded JSON
  documents and top-level outbound arrays, preserving provider order and
  de-duplicating repeated envelope copies.
- Keeps the encrypted profile-store schema unchanged. Country metadata and
  leading-flag cleanup are presentation-only, so existing subscriptions and
  selections remain compatible.
- Shows the real endpoint count without treating **Automatic** as a server.
  Import and refresh confirmations report counts per VPN core.
- Raises the bounded Home picker list from 240 to 300 dp, adds an explicit
  desktop scrollbar, and shows country flags derived from provider emoji,
  country names, common location names or unambiguous ISO tokens.

The exact response body of the user's private subscription was not available to
the build machine. The release covers both client-side loss paths found in code:
provider format dispatch and a popup whose first viewport ended after one
location's three protocol rows. Exact provider counts remain a user-PC gate.

## Automated and static gates

- 126 shared/helper JVM tests pass with zero failures or errors; two
  environment-gated tests are skipped. New cases cover nested three-location
  envelopes, top-level outbound arrays, sing-box request identity, country
  resolution and provider flag cleanup.
- Desktop Kotlin compilation and Gate A smoke pass without starting a live
  tunnel. Bundled sing-box 1.13.14 and TrustTunnel 1.1.5-rc.6 are detected.
- GitHub Windows CI passes for commit `0eb52092cdd120846a070f6672dda055e038abde`:
  `https://github.com/KatoteKiru/veilark-windows/actions/runs/32648055222`.
- This is native Compose UI, so the HTML/CSS detector is not applicable. The
  picker was reviewed from source for bounded sizing, overflow visibility,
  selection indication, RU/EN copy and persistence invariants.
- Production EXE/MSI packaging passes. MSI administrative extraction contains
  byte-identical sing-box, TrustTunnel, WinTUN and both pinned RU SRS files.
- A non-elevating verification build of the OTA bootstrap extracts the embedded
  installer, verifies the expected SHA-256 and removes its temporary payload.

## Package identity

- Version code/name: `308` / `0.3.8`.
- Public OTA installer:
  `https://nl2.senyasenyavski.uk:2096/veilark/windows/Veilark-0.3.8.exe`.
- OTA wrapper size/SHA-256: `129693184` /
  `E5E35E1DC48BFE429C41A37A6E6527468A1AF7DD35E7D0475D97F8BC8F9BCDB9`.
- Inner jpackage EXE size/SHA-256: `129684992` /
  `2840CB877C2647CF6A4678657414E003A398C691C44836766AF066A6B9915A87`.
- MSI size/SHA-256: `129071043` /
  `F3E794F40E4CD932860D39D721CF65198414384646A1DCA87882A28CF74D9E1C`.
- MSI ProductVersion: `0.3.8`.
- MSI ProductCode: `{7CF7820F-D818-3205-9B0F-F0577EF768CE}`.
- MSI UpgradeCode: `{47A6CDD8-9630-4FA5-A2FD-C29C5774DC1A}` (unchanged).
- The production wrapper contains a `requireAdministrator` manifest and was not
  executed on the build PC. It is not Authenticode-signed, so Windows can show
  an unknown-publisher warning.
- The live manifest passes the production `UpdateClient` Ed25519 validator.
  The complete public EXE was downloaded again and matched the local wrapper by
  size and SHA-256; publication also passed HTTP HEAD and Range checks.

The build and release process did not start Veilark, a VPN core, TUN adapter,
route or DNS mutation on this PC. Refreshing the affected subscription and live
traffic checks remain the user-run gate in `PC_ACCEPTANCE_0.3.8.md`.
