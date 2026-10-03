# Windows 0.3.23 / OTA 323 — published

TrustTunnel stable 1.1.7 replaces 1.1.5. Official x86_64 ZIP SHA-256:
`AFF81FF4EBCC00D9EDE7F29D0FBB56D277D784168080513074FFB8F357AD3796`.
sing-box, WinTUN, routing settings and update signing identity are unchanged.

Also includes narrow stability-audit fixes: native launch stage/Win32 diagnostics,
wizard/version exit diagnostics, CORE_EXITED classification, tt:// log redaction,
and diagnostic logger failure isolation. No claim that this proves the cause of
the reported single-PC Trust failure.

Local helper/shared/desktop tests and fresh packaged-resource smoke passed.
Physical VPN and the affected PC have not been tested. CI package + in-place
0.3.22 upgrade is required before OTA publication. No Authenticode certificate;
OTA authenticity remains protected by the existing Ed25519 trust key.

Do not reuse this version identity for different public bytes. Publication must
snapshot the previous manifest, upload versioned installer first, replace manifest
last, then redownload and compare the full payload size/SHA-256.

Published on 2026-10-04 Moscow time from bb534a7d4c802c720b9b6b5cf8603851fe53fbd7.
Package/upgrade CI 37154146869 passed, including isolated 0.3.22-to-0.3.23
installation and test-data preservation. PR #2 merged as 34b0b35211f155867e6b3578ce6a64adb12cac97.
GitHub v0.3.23 is a normal release, not a prerelease.

EXE: 130883584 bytes; SHA-256
`cdcb35edcb31c4dd95640602af6e0335303dabe6860fe8fe28017620cd9d1a72`.
MSI: 130283912 bytes; SHA-256
`e3f8ecee00d28e94bf401151bbc1593ee73f4c7b02ffce40cf756a22be4b1262`.
Existing Ed25519 manifest and notes signatures verified; full public EXE redownload
matched size/hash. Installer uploaded before manifest replacement.
OTA: https://nl2.senyasenyavski.uk:2096/veilark/windows/manifest.json
Rollback: `/var/backups/veilark/windows/manifest-before-323-20261003T212503Z-aa7e0ecb.json`.
Bot/Mini App Windows catalog refreshed without restart; catalog backup:
`/var/backups/veilark/catalog/windows-before-323-20261003T212543Z.json`.

Limits: no physical affected-PC VPN acceptance. Authenticode remains absent.
Local Defender reported AntivirusEnabled=false, so its zero detections are NOT
a valid clean antivirus verdict. No antivirus exclusions were added.
