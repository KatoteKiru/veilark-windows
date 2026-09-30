# Windows 0.3.22 / 322 — 2026-10-01

Published from Claude `d9803bc` plus release identity/notes commit `05b3c5c`.
Release CI 36789555696 passed native startup, packaged-resource and bootstrap
checks, and in-place upgrade from live 0.3.21 with user data preserved.
Collected test reports: 114 tests, 0 failures/errors, 2 gated skips.
Publisher contract regression tests: 20/20 pass.

EXE: 130625536 bytes, SHA-256
`3D0ECEFF9F07B19477F3BB143F2A2AECA878455E30172B11E249EE5890683FD0`.
MSI: 130025865 bytes, SHA-256
`4369BF5138561F9B8EA1BF2068FFDD8F1D8C4207E77F51E0677011A71B6C8139`.
Existing Ed25519 OTA key and MSI UpgradeCode retained. Installer version and
client-verifiable signed notes were checked before signing. Installer uploaded
and verified first, manifest atomically switched last. Local Defender scan
completed without a matching threat detection. Authenticode remains NotSigned.

Rollback manifest:
`/var/backups/veilark/windows/manifest-before-322-20260930T232132Z-c5252c98.json`.
The timestamp is UTC; date above is MSK. Do not reuse code 322 for different bytes.

Changes: locked core configs and protected installer payload, process Job object,
improved update cancellation and localized errors, native Windows 11 chrome,
remembered window placement, tray click and shortcuts. Remaining privilege-model
risks are still documented in SECURITY_HARDENING_2026-09-30.md.
Physical-owner-PC update/traffic and user network transitions are not inferred
from CI. Full public-redownload result is recorded in the shared release report.
