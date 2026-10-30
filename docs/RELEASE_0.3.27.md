# Windows 0.3.27 / 327 — verified 2026-10-30

Published to the signed OTA channel; independently verified by full public
download and both client-key signatures on 30 October.

Fairway now creates the real Windows TUN, installs its endpoint and tunnel
routes, applies WFP protection, carries TCP/UDP traffic, and stops cleanly.
Fixes include exact installation-GUID adapter admission, explicit manual-route
flags, the supported ALE local-address condition, and preservation of the
original native failure before cleanup. Previously published 0.3.25/26 were
withdrawn; the public predecessor for this release is 0.3.24.

Old route journals accept only the known historical flag images. Absent routes
can be retired as metadata; present tagged routes require durable confirmation,
the matching manifest/installation/run/tag, a dead former process, and strict
interface and row identity. Only the observed Immortal normalization is allowed.
Present unconfirmed or untagged legacy routes remain blocked rather than being
deleted without proof. Existing subscriptions and profile stores are retained.

## Evidence

- Windows source: `9856580c546856fc2c44f7f49838f494bfe99c8a`.
- Native source: `016990a20c04395dadde17571574b6a2bd89376d`, ABI1, capabilities11.
- Exact native: 4605525 bytes, SHA256
  `c3a748ae6b98ca779d26770e81b6019d80161e0f3cbed051b4d9ffde86004623`.
- Actual isolated Windows full-TUN acceptance:
  [CI40891175866](https://github.com/KatoteKiru/veilark-windows/actions/runs/40891175866).
  Both cycles passed Connected, exact-owned adapter, source/route proof, bound
  HTTPS, bound UDP DNS, bidirectional traffic, Stop, adapter absence, Internet
  restoration, process exit0 and post-exit cleanup. Normal TLS and revocation
  checks remained enabled; a strict pre-TUN baseline primes the runner cache.
- Targeted native recovery safety tests passed5; exact DLL JNA memory/configure/
  Stop pairing passed1 with0 skips. These do not substitute for the runtime CI.
- Actual inner-installer native bytes were extracted and matched to the pin;
  packaged runtime resources and bootstrap extraction/locking passed.

Wrapper: 132739072 bytes, SHA256
`a6d0d109f1c54ff41e80697e41a85de0c1bdafd6358150a2aa90e92b7d7948fc`.
Inner installer: 132709888 bytes, SHA256
`e9a92f4478643278eced36868631e8e5452ac5f313a8412c0b3ce353c8f9e1a4`.
Authenticode remains NotSigned; OTA manifest and notes require the existing
client-trusted Ed25519 key. MSI UpgradeCode is unchanged.

The owner's working PC network was not changed and the installer was not run
there. Physical Windows+Android concurrent traffic is not inferred from this
isolated test. Managed subscriptions use separate per-install identities;
copying a static Core identity to two devices is not equivalent.

## Publication

The live channel serves version0.3.27/code327 and the exact wrapper hash above.
Manifest signature, signed notes, full public132739072-byte download and SHA256
passed independent verification. The prior signed0.3.24/code324 manifest is at:
`/var/backups/veilark/windows/manifest-before-327-20261029T143749Z-91663b50.json`.
Its signature was independently verified. Installer upload/hash verification
precedes the mandatory rollback snapshot and atomic manifest switch.

All temporary lab networks, rules, units and synthetic keys were removed;
production service/file identities remained unchanged. Temporary encrypted
fixtures, two narrow Actions secrets and DPAPI custody were removed. The
interrupted local publisher session's exit code was unavailable after context
recovery; public artifact, both signatures and rollback were verified directly
instead of repeating the already completed release.
