# Veilark Windows 0.3.20 — release gate

Candidate source: 9e3704c, codex/windows-fluent-stability. Version code: 320.
The owner's publication request authorizes the existing unsigned Windows channel;
it does not establish Microsoft trust or closure of the complete security audit.

## Changes

- Preserve the shipping 0.3.19 UI instead of the rejected Fluent experiment.
- Use the bundled font consistently in all 15 Material typography roles.
- Preserve the existing subscription selection when importing another source.
- Bound native process output and oversized log records before classification.
- Correct future OTA counter ordering and version-independent upgrade testing.

Engine pins, routes, profile storage format, MSI UpgradeCode and Ed25519 trust
root remain unchanged. No kill-switch, routing-speed or battery fix is claimed.

## Acceptance gates

- Local helper/shared/desktop JVM tests successful; 4 environment-gated skips.
- Local publisher lineage tests 4/4 and version-order checks passed.
- GitHub source CI 36746849682 passed at 513a516.
- Release/package CI 36747432837 must pass at 9e3704c before publication,
  including native startup, packaged resources, embedded payload verification,
  isolated 0.3.19-to-0.3.20 installation and local-data preservation.
- Publisher must verify the previous signed manifest and original signing key.
- Installer first, manifest last; mandatory private rollback snapshot.
- Published bytes must be redownloaded and match the candidate SHA-256/size.

## Limits

## Publication evidence — 2026-09-30

- Release CI [36747432837](https://github.com/KatoteKiru/veilark-windows/actions/runs/36747432837)
  succeeded at 9e3704c. Captured XML reports: 89 tests, 0 failures/errors,
  2 skips; native TUN acceptance: 1 executed, 0 skips/failures.
- Isolated installer upgrade 0.3.19 -> 0.3.20 passed with local data preserved.
- Wrapper: 130584064 bytes, SHA-256
  `5CBC66EB2EC42D4581D831A77CDEC9A8A0D3092680CA94665133E0BF7EDAF8E3`.
- MSI: 129988546 bytes, SHA-256
  `2384BD17D4C50DEFC3E83289B08238EEB40367392BF0A940B53B197ABB4B040C`.
- Local Defender custom scan reported no threats in this exact wrapper;
  this is not clearance by all antivirus engines or a Microsoft publisher identity.
- Original Ed25519 key/previous manifest verified before publication; live
  320/0.3.20 and signed notes verified again after publication.
- Mandatory previous-manifest snapshot:
  `/var/backups/veilark/windows/manifest-before-320-20260930T171157Z-c4fa5bd2.json`.
  For rollback, restore this exact file atomically to the Windows manifest path.
  Already-updated clients will not downgrade; do not change the trust root or
  re-use 320 for different installer bytes.
- Full public redownload matched the wrapper hash and size; HEAD and Range checks passed.
- GitHub preview [v0.3.20](https://github.com/KatoteKiru/veilark-windows/releases/tag/v0.3.20)
  is published with EXE/MSI/manifest; tag points to 9e3704c and asset digests match.

## Remaining limits

No physical-owner-PC update or customer VPN traffic acceptance is inferred from
CI. Authenticode remains absent. Historical credentials, privileged broker design,
runtime kill-switch fault injection and OTA mirrors remain open as described in
WINDOWS_GROK_TRIAGE_2026-09-30.md. Running user VPN and server services must not be
stopped by this publication.
