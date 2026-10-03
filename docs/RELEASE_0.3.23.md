# Windows 0.3.23 / OTA 323 — candidate

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

Current status: source candidate only; published production remains0.3.22/322.
