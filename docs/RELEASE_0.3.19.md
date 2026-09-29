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

## Public CI follow-up on 2026-09-29

- Repository visibility was changed to public at the owner's request. Workflow token permissions were narrowed to `contents: read` before publication.
- [Windows CI run 36530717421](https://github.com/KatoteKiru/veilark-windows/actions/runs/36530717421) succeeded on `windows-2022` at source commit `0062355`. The downloaded reports contain 85 tests across 15 suites, with 0 failures, 0 errors and 2 skips. `SingBoxNativeTunTest` executed once without a skip or failure.
- The CI-built OTA wrapper is 130579968 bytes with SHA-256 `C32BC0810960E2C538CD06175F82A80D37649624F21F0A25CEE7FC895000CC76`. It differs byte-for-byte from the earlier local build; do not substitute one hash for the other. The same CI artifact also contains the MSI and test reports.
- Windows Authenticode status of the CI wrapper is `NotSigned`. The earlier Microsoft Defender/download warning, physical in-place upgrade and end-user VPN traffic remain unverified. CI's native startup contract does not prove a commercial subscription connects on an affected PC.

## OTA publication on 2026-09-29

- [Final Windows CI run 36533172509](https://github.com/KatoteKiru/veilark-windows/actions/runs/36533172509) passed at source `ebfd3ac`: native TUN startup test 1/1, packaged resources, real isolated 0.3.18→0.3.19 installation, local-data preservation and startup of the installed sing-box core. This does not exercise a customer's subscription, network routing or the in-app update UI on the owner's PC.
- Before publication, the old live manifest was backed up outside the web root at `/var/backups/veilark/windows/manifest-318-20260929T065436Z-9284760a.json` with SHA-256 `B73FCBCFF02D610406C5E469E74E9577B79D2A3ACE387E19E450114F57719AD8`.
- The CI-built installer was published before atomically switching the signed manifest to `0.3.19` / `319`. The published manifest signature and client trust-root lineage verified. A full public redownload matched SHA-256 `6290B4BD9AF1007799FF1761613C9FBFAA6B8FCE559447A9B6AD09B0DB0C7054`, 130579968 bytes.
- Authenticode remains `NotSigned`. Microsoft SmartScreen/Defender behavior across customer PCs, app-driven 0.3.18→0.3.19 OTA, saved customer profiles, real VPN traffic and split-tunnel throughput are **not** accepted by the isolated CI result. Do not describe those as fixed. The 0.3.19 notes deliberately do not claim a split-routing speed fix.
