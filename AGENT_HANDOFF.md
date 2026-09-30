# Veilark Windows current handoff

Updated 2026-09-30. Source branch `codex/windows-fluent-stability`, based on0d8fe28.

Objective: evidence-based stability fixes and a Windows desktop refinement, keeping
the running VPN and production0.3.19 unchanged.

Implemented: bounded ProcessCapture reads, regressions for output bounds and existing
cancelled-disconnect settlement; adaptive left navigation, local Segoe fallback,
compact shapes/logo, actual HomeScreen visual fixtures at minimum/wide sizes and DPI.
No engine/routing/privilege/OTA implementation changes. Plan and limitations:
`docs/WINDOWS_STABILITY_AND_FLUENT_2026-09-30.md`; built design: `DESIGN.md`.

Validation: 40 targeted helper tests pass; combined JVM tests pass. Independent
source review reports no blocking regression. Visual fixture correction and tooltip
verification passed the final reviewer verdict. Source candidate94c0849 passed
native Windows CI36714045339. The additional CoreLogPump bounded-framing fix
reproduced two baseline failures and passed11 targeted tests plus independent
review. Final source6f4f98f is pushed; native CI36715057688 is in progress.
Combined local results:211 tests,0 failures/errors,4 gated skips. Check final CI
before promoting the candidate; first-run CI is not evidence for this later commit.

Next: complete independent integration verdict and native Windows CI. Before OTA,
assign a new version and verify packaging, MSI/Ed25519 lineage, rollback and download
hashes. Real installed-app VPN, Wi-Fi/sleep/wake, performance and Defender acceptance
remain open. Do not stop the owner's live VPN without scoped authorization.

No production or OTA publication belongs to the current unpromoted candidate.
