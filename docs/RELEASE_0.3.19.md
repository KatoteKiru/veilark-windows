# Veilark Windows 0.3.19 — release gate

This candidate packages the installed-resource lookup fix from `868b601`. It retains the startup, process cleanup and window activation changes that landed after the published 0.3.18 source. The version code is 319; the OTA manifest URL and Ed25519 trust root are unchanged.

Required before publication:

1. Full JVM tests and native Windows package/resource verification.
2. Build the versioned EXE/MSI and OTA bootstrap; verify its embedded payload hash and size.
3. Validate the existing signed 0.3.18 manifest and that 319 is newer.
4. Review publisher status. The existing preview channel has no Authenticode identity; do not describe it as Microsoft-trusted or dismiss a malware detection without analysis.
5. Publish the verified installer first, then the signed manifest atomically; redownload and compare bytes.
6. On a physical Windows PC, check an in-place upgrade, saved profiles, sing-box and TrustTunnel tunnel traffic, routing, window startup and clean disconnect.

## Evidence on 2026-09-29

- Forced JVM tests: 17 tasks executed, successful. The native TUN test was skipped because this host is not an isolated CI runner and currently has an active VPN.
- Actual sing-box loopback test: 2 executed, 0 skipped, 0 failures; no TUN adapter or system route created.
- EXE and MSI packaging completed. Packaged sing-box 1.13.21, TrustTunnel 1.1.5 and GEO assets matched the pinned source resources.
- OTA bootstrap verification extracted the embedded EXE and matched SHA-256 `B5A08D653FF1CF75FFCD43C9F5980248FF21CB432CAFD8F4CCB04A5357DDCED4` (130554880 bytes). Wrapper SHA-256 is `567D0309B8F5AA17B9CE216559BA86A24CF30828AF96DAFEBD27AD847BC2524E` (130579968 bytes).
- The local signing key matches the embedded client public key; the live 0.3.18 manifest and signed notes verified; candidate 319 is newer. Four publisher-lineage unit tests passed.
- Wrapper Authenticode status is `NotSigned`. GitHub-hosted CI is blocked before job steps by account billing. The real elevated TUN test, physical in-place upgrade and VPN traffic were not run.

The 0.3.19 installer and manifest have **not** been published. Local tests and hashes do not prove that the Windows client now connects on user PCs. Do not move this candidate into the mass OTA channel until the remaining release gates or an explicitly scoped preview acceptance are completed.
