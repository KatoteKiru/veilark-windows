# Veilark Windows current handoff

## Current publication — 2026-10-04 (supersedes historical notes below)

Windows 0.3.23 / OTA 323 is LIVE, TrustTunnel stable 1.1.7. Candidate bb534a7
passed local 256 tests and CI 37154146869 package/0.3.22 upgrade checks.
PR #2 merged; GitHub v0.3.23 is a normal release with EXE/MSI uploaded.
Original OTA key/MSI identity retained. Full public payload hash and manifest/notes
signatures verified. Bot/Mini App catalog updated atomically without restart.
Exact evidence/rollback/limits: docs/RELEASE_0.3.23.md.
Affected-PC cause remains UNKNOWN; physical VPN acceptance and Authenticode remain
open. Local Defender is inactive: do not claim antivirus acceptance.
Cross-platform next action: finish Android native 1.1.7 build in isolated
C:/AI-Agent/veilark-android-trust117; never publish the Core-lab Android branch.
User files docs/RELEASE_0.3.21.md and ui-research/ remain preserved.

The sections below are historical context, not current publication status.

## Stability audit 2026-10-03 — local source only

Report: `C:/AI-Agent/reports/ECOSYSTEM_STABILITY_2026-10-03.md`.
NativeProcessDiagnostics distinguishes startup stage/Win32/exit code without
argv/config values; early readiness exit reports CORE_EXITED; SafeLog masks tt://.
Package smoke now probes setup_wizard --help. Local fresh app-image smoke passed.
Do NOT publish current 0.3.22 local bytes under the existing version; new candidate
needs new identity/upgrade acceptance. Affected-PC cause is still unknown, awaiting
redacted log/UAC/architecture. Production, owner VPN and OTA were not changed.
User's untracked `docs/RELEASE_0.3.21.md` and `ui-research/` preserved.

## Published 2026-10-01 — supersedes prior status

Windows 0.3.22 / 322 is live on existing OTA, source `05b3c5c` from Claude
`d9803bc`, release branch `codex/windows-ota-0322`. Package/upgrade CI
36789555696 passed, including native startup and 0.3.21-to-0.3.22 data preservation.
Original OTA key and MSI identity retained; Authenticode is still absent.
Exact hashes, rollback and acceptance limits: `docs/RELEASE_0.3.22.md`.
Cross-platform continuation: `C:/AI-Agent/reports/ota-20261001/STATUS.md`.
No owner VPN or server service was restarted. Security backlog stays open.

## Hardening branch — 2026-09-30 (not published)

Branch `claude/happy-ptolemy-mpow5b` → draft PR into `codex/windows-fluent-stability`.
No version bump, no OTA publication; trust root, manifest format, UpgradeCode and
install paths unchanged. Covers: publisher UTF-16 notes limit and version checks
(tests in CI), elevated bootstrap payload TOCTOU (protected directory plus share
lock), locked core configs, Job object for cores, update-controller cancellation
and 90 s first background check, stable error codes with RU/EN text, Windows 11
caption (dark/Mica/rounded), window memory, tray single click, shortcuts, removal
of dead UI. Details and residual risks: docs/SECURITY_HARDENING_2026-09-30.md.
Owner publishes releases himself.

## Published Windows OTA — 2026-09-30

Windows 0.3.20 / 320 is live on the existing OTA channel and GitHub preview
v0.3.20, built from 9e3704c. Release CI 36747432837 passed, including isolated
0.3.19 -> 0.3.20 installation/data preservation. Public redownload hash matches;
original OTA trust root unchanged; old manifest privately backed up. Local Defender
found no threat in the wrapper, Authenticode remains NotSigned. See
docs/RELEASE_0.3.20.md for exact hashes, rollback path and acceptance limits.
This supersedes earlier "no OTA" statements below. No VPN/server service restart.
Security backlog remains open; release does not claim full audit closure.

## Latest decision (2026-09-30, supersedes UI directions below)

Owner rejected the replacement Fluent UI and requested polish of the shipping
0.3.19 interface. Experimental Fluent source/dependency removed; shipping bottom
navigation, window dimensions, Noto typography and surfaces restored. All 15 text
roles now have the same bundled family. Previous bounded native-log fixes remain.
Fixed OTA field concatenation and unintended selection on additional import;
new regression checks pass. Detailed eight-point security triage and remaining
release gates: docs/WINDOWS_GROK_TRIAGE_2026-09-30.md.
No OTA or production change. Old 0.3.11 installer is still publicly accessible;
do not call historical credentials cleared. Next: non-executing artifact inspection,
redacted history/CI audit, native kill-switch acceptance and independent review.

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
review. Source6f4f98f is pushed; native CI36715057688 completed successfully.
Combined local results:211 tests,0 failures/errors,4 gated skips. Check final CI
before promoting any newer candidate; this CI does not cover later compact UI edits.

Next: complete independent integration verdict and native Windows CI. Before OTA,
assign a new version and verify packaging, MSI/Ed25519 lineage, rollback and download
hashes. Real installed-app VPN, Wi-Fi/sleep/wake, performance and Defender acceptance
remain open. Do not stop the owner's live VPN without scoped authorization.

No production or OTA publication belongs to the current unpromoted candidate.

Owner feedback: sidebar rejected. Replaced with three top tabs and a labelled Tools
menu (Diagnostics/Updates/Logs), starting window520x600/min480x480. Connection
surface has4dp shadow, selected tab2dp; dark surfaces raised tonally rather than
dark recessed panels. No new background animation or networking change. Renders
cover520x600/480x480,100–150%DPI; DESIGN.md updated to reflect this revision.

Latest owner decision supersedes that refinement: reject Android/Material visual
world entirely; replace ALL Windows UI from scratch. Do not promote the prior
card/tab candidate as an approved design. Direction/implementation gates are in
docs/WINDOWS_INTERFACE_REBUILD_BRIEF.md; visual concept approval is pending.
