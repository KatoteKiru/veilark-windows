# Windows Fairway continuation — 27 October 2026

Atlas owns Windows app/import/store/JNA/UI/packaging/sole OTA. Vector owns private
Core/server/native code in C:/AI-Agent/veilark-fairway-passage-integration-20261024.
Windows branch local/windows-fairway-20261025; do not touch unrelated untracked
 docs/RELEASE_0.3.21.md or ui-research/.

## Current state
- 28Oct RELEASE GATE CLOSED: NL prepare refused real legacy_session_proof
  config: candidate863c750 lacks deployed Beta15 proof implementation. Vector
  integrating real semantics from1193f81, not disabling the setting. CORRECTION:
  Windows DLL5f0e189 uses separate veilark-windows-runtime: authenticate retains
  legacy renewal credentials and the packet pump calls LegacyMaintenance with
  Fairway. Earlier claim that Windows recovery was not wired was incorrect;
  verify compatibility with fixed server, rebuild DLL only for a concrete defect.
  Previously built 0.3.25 candidate is NOT eligible for OTA; source flag reset false.
  Ignored packaging DLL fa516 remains staged: verifier intentionally rejects it
  while flag false. Preserve candidate as evidence, not a released product.
  All FI runtime helper scripts with pinned old 7fac binary need updated after
  Vector provides rebuilt artifact. Active NL service and Android85 unchanged.
  Android single-managed-link enrollment missing in beta15; separate agent works
  source-only on parity; old static Android subscription remains usable.
- 27Oct historical private candidate BUILT with FairwayRelease=true at build time
  and ignored packaging DLL fa516bfd (4567446B). It is superseded as noted above.
  Do NOT publish/merge until matched recovery, NL daemon and real managed claim pass.
  Existing public OTA remains0.3.24; physical Windows TUN deferred by owner.
  Appimage/resources PASS, 12 focused cases0failure/0skip, actualDLL/JNA pairing,
  EXE inner payload+bootstrap verify PASS, wrapper version0.3.25.0. Candidate
  build/candidate/Veilark-0.3.25.exe 132689920B SHA256
  47ba6c522ef99517111b98d05a9746edb59374229b6b94849349739e58343c84;
  Authenticode NotSigned, matching existing unsigned channel; NOT published.
- Latest app implementation e6cd4da: use exact Android pinned Fairway policy and
  unchanged parser, bundled135358B/8662prefixes/45223232IPv4, SHA256
  fd0fdb390680c4a6a18a02542205b766e345b6caa34de717a2d9c776dd063cd5.
- Core no longer consumes TrustTunnel/SRS Geo preparation. Legacy engines unchanged.
- Actual helperJAR asset verified;5targetedchecks0skip+desktopcompilePASS99s.
- App imports/model/DPAPI/controller/separateFairwaybutton/releasefactory prepared.
  FairwayRelease.accepted=false; no accepted nativepin, newversion or OTA.
- 27Oct: existing import/product/DPAPI checks now follow immutable release metadata
  instead of hardcoding false; six focused cases PASS17s, no tunnel/network action.
  Current release decision remains false until matching native artifact/delivery.
- Prepared next release source version0.3.25/code325; live public OTA read-only
  confirmed0.3.24/code324. No new installer/manifest publication yet.
- Corrected private DLL RECEIVED from Vector source5f0e189:4567446B,
  SHA256fa516bfda35a1d5f743ec96a82ecc386f2a0ca7f20b8eb74f603a075c38855b6,
  reports/windows-fairway-20261025/native-v1-host-beta-20261027, ABI1/caps11/noLAB.
  Actual JNA pairing accepts full8662Androidpolicy PASS8s0skip; no AUTH/host effect.
  Imports onlyWindowsOSDLLs. Native source/build not hardware forwarding PASS.
- Initial isolated0.3.25image assembled59s whileflagfalse; no installer/OTA yet.
- Fixed existing Windows omission: Android compactt/code links now decode through
  HTTPS import; shell/IPC retains opaque form, nested envelopes rejected.15focused
  import checks0fail/0skip+desktopcompilePASS32s. This is legacy encoding parity,
  NOT signed enrollment-v2 or account/device enrollment.
- Account enrollment client/codec now implemented and wired to explicit Fairway
  button/dialog/clipboard and Core-source refresh. Same /managed|trust URL,
  opt-in GET→canonical32char scope; POST only publickey/scope/stable requestid;
  strict response checked before local DPAPI private key merge. No redirects,
  bounded64KiB body+HTTPdeadline, no-store success required, fixed UI errors.
- Existing TrustTunnel/sing-box profiles preserved during Core-only response;
  legacy refresh preserves existing Core entry under the same subscription URL.
  Seven focused DPAPI/mockHTTP/catalog cases PASS35s0skip; final import4cases+
  desktopcompilePASS25s after HTTPS-paste routing and UIretry fix. No live HTTP.
- Independent client/UI review caught pending202/503 retry dropping Core hint;
  fixed: clear hint only on successful import or dismissal, no legacy fallback.
- Speculative larger startup timeout was fully reverted, never compiled/released.
- Critical correction: Android already uses veilark-fairway-dispatch/DirectPolicy.
  Previous Windows native host_actor created bulk OSdirectroutes instead. Vector
  now reuses existing shared Fairway runtime with Windowsboundsocket/TUN adapter.
  Do NOT promote the old bulkGeo host-beta snapshot as feature parity.
- Input HostConfig48/Prefix8/max16384 matches Android policy count bound; no new
  protocol/stack or broader Geo limits required. Android remains unchanged.

## Immediate next actions
1. Matching private artifact/JNA memory pairing now complete. Await real distinct
   per-install delivery before setting release flag/pin and packaging final image.
2. Finish ACTUAL beta issuer/deviceclaim/delivery. Legacy manual script in reports/
   core-fairway-20261016 appendsargon.conf+RESTARTS; restore-kowalski.py overwrites
   hardcoded6entries. NEVERrerun those as modern enrollment/migration.
3. Bot sibling sourcee0fa226 has all-target receipt proof and atomic revokequeue/
   device/request completion before slotreuse. No actual authenticated publisher,
   claim/encrypted delivery or protected-admin additive migration consumes it yet.
4. One entitlement must support Android+Windows with distinct per-install keys;
   never copy Android staticprivate key or promise oldstaticlink concurrent-safe.
5. Match productmetadata, nativepin, resources/installer/OTA signature/rollback.
6. Vector owns backend actualaccount bridge in botlogiccheckout; GETclaim/POST
   publickey contract locked and app side implemented. Backend claims/protected
   grant committede9fc221; publisher/runtime composition3b5e6d9 implemented,
   NOT deployed/configured end-to-end. Reject JWE/allowlist possession as authority
   over owner/slots. Need effective all-target receipt before a usable profile.
   Atlas owns Windows DPAPIkey/claimconsumer/OTA. Newoperatoradapter83e8e6d source
   exists in isolatedsnapshotcheckout, not deployed; 5purechecks+reviewfix complete.
7. Linuxv2 pre-anchorELF source4524403 built/staged privately; CLI golden accepted/
   malformed rejected in isolatednetns, no activeunit changes. Vector adding
   independent durable anchor +verified previous-snapshot boot recovery, then
   matched rebuild/real isolated driver+SSH receipt/controlled rollout still due.
8. 27Oct continuation: final anchored Linux source863c750/2370808B/SHA7fac12c2
   frozen in reports/core-server-snapshot-20261027/frozen-863c750f. Actual isolated
   Linux TUN + EXACT remote publisher helper: initialACK1, addACK2, revokeACK3,
   daemon restart with pending4/anchor3 boots confirmed3, retry givesACK4/anchor4.
   Owned TUN cleanup PASS; CLI missinganchor/corruptprevious/rollback rejected.
   No production service/config/customer key changes, no Windows forwarding test.
   Vector prepares custody-safe live daemon rollout; Atlas prepares backend deploy.
   Read-only FI has real admin entitlements/free slots but no core_* migrations;
   original staticAndroid lineage is not yet proven, never guess entitlement.
9. FI backend deployed source7a7a0fd + 13 newCore tables via SQLite backup and
   journaled one-shot; PID1462340 health PASS. Public managed/trust opt-in GET and
   POST now return503 no-store while runtime remains disabled. Existing managed
   subscription is valid authority for distinct Windows key; legacy static Android
   remains unchanged. Await NL daemon transaction, runtime bootstrap and actual
   managed claim/driverACK before pinning DLL and publishing OTA.

## Authority and evidence boundaries
Owner explicitly chose testing real Windows AFTER release: do not ask again for
VM/Billing or label hardware acceptance PASS. Source/runtime functionality still
must exist before publication. No current-PC network mutation implied. PrivateCore
source/plainDLL must not enter public Git repository. No billing/spending change.
Android85/live server/accounts preserved; no live actions in recent sourcework.

Frozen a8e29e private labDLL from16df365 paired through realJNA8s, memory only,
caps13/FULL_TUNfalse. It is NOT the future commonFairway product artifact.
Full prior decisions, test evidence and historical releases are preserved in
 docs/handoff/windows-history-before-parity-20261027.md; consult narrowly as needed.
