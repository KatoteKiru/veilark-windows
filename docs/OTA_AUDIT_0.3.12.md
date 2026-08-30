# Veilark Windows OTA audit — 0.3.x to 0.3.12

Audit date: 2026-08-31. Production was inspected read-only and was not changed.

## Compatibility boundary

The repository history contains complete updater source for 0.3.4 through
0.3.12. Every inspected version uses the same manifest URL, Ed25519 public key,
HTTPS host/port allowlist, and MSI UpgradeCode. The repository does not contain
the 0.3.0–0.3.3 source snapshots; their compatibility is documented in the old
release evidence but cannot be independently reconstructed from this checkout.

The update trust path is:

1. `UpdateClient` fetches an allowlisted HTTPS manifest without redirects.
2. The legacy Ed25519 signature authenticates version, installer URL, SHA-256,
   and size. Starting with 0.3.12, a second signature also authenticates notes;
   the legacy signature remains so older 0.3.x clients can accept the release.
3. The download is bounded by declared size and verified by SHA-256 before it
   is promoted from the resumable file.
4. `WindowsUpdateInstaller` verifies the file before VPN shutdown and again
   before launching a detached helper. The helper holds the verified file open,
   hashes it again, waits for Veilark processes to exit, and invokes the wrapper.
5. The elevated wrapper extracts the embedded jpackage EXE, verifies generated
   size/SHA-256 metadata, launches it with the original installer arguments,
   and removes the extracted payload.
6. The stable MSI UpgradeCode performs the in-place 0.3.x upgrade and the helper
   checks the installed `Veilark.cfg` version before recording success.

## Confirmed causes and defects

- The live manifest is still 311 / 0.3.11. Therefore an installed 0.3.11 must
  report Current and cannot discover the local 0.3.12 preview until a signed
  0.3.12 manifest is published. Older inspected clients correctly discover the
  currently published 0.3.11.
- The initial 0.3.12 preview changed visible wrapper version strings but retained
  the 0.3.11 embedded-payload SHA-256. The new inner EXE has different bytes, so
  that wrapper would exit 1603 before starting the installer.
- Bootstrap payload metadata and version identity were maintained manually,
  which allowed that drift. The new build script derives them from the exact
  payload and rejects disagreement between filename, client version/code, and
  jpackage version.
- Release notes were not covered by the legacy signature even though the UI
  described the manifest as signed. 0.3.12 now requires a compatible second
  Ed25519 signature over the legacy payload plus notes.
- The publisher previously accepted unknown SSH host keys. Publication now uses
  the operator's known-hosts trust store and fails closed for an unknown host.
- Published and locally built wrappers are not Authenticode-signed. Internal
  Ed25519 and SHA-256 checks protect the application-controlled path, but they
  cannot provide a verified Windows publisher in SmartScreen/UAC.

## Proof and remaining gate

- The live 0.3.11 manifest returned HTTP 200, passed the production Ed25519
  validator, and selected an update for a lower version while returning no
  update for version 311.
- Automated tests cover old-version selection, both manifest signatures,
  tamper rejection, completed/resumable download promotion, SHA-256 validation,
  and detached installer scheduling without launching a real installer.
- The real 0.3.12 EXE/MSI package was built. A non-elevating bootstrap extracted
  and verified the exact embedded EXE and left no payload behind. The production
  wrapper contains the `requireAdministrator` manifest but was not executed.
- Publication is intentionally blocked until the final wrapper has a valid
  Authenticode signature. After signing, the release must be rebuilt/verified,
  signed into a 312 manifest, published atomically, and accepted on a real PC
  from at least one installed older version. None of those production actions
  were performed during this audit.
