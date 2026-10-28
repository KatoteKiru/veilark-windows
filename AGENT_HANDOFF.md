# Windows Fairway continuation — 27 October 2026

28Oct STARTUP ROOT CAUSE FIX79d162e (CI fixture54d6f8d): native Wintun type
Veilark yields Veilark Tunnel; old description heuristic rejected actual adapter
after native RUNNING, triggering TUN_NOT_CREATED+cleanup. Now exact-ownedGUID
lookup/health and LiveCheck do not depend on cosmetic descriptions.
Replacement327132726784B SHA315b3295e0fc07e405b2891d3d78790e3781a99b6b18d15869dc02ae56b074fb.
Native remains7ea7989/699c6fad. Public OTA24. User DENIED hostnetwork/UAC:
no stop of activeTrustTunnel, no localhost TUN test. PublicWindowsCI is AVAILABLE
(run40759391327), privatecorebilling restriction does not block publicWindowsrunner.
Independent actualWintun leaf job being added; still NOT fullFairwayInternet.
Android explicitmanagedimport/refresh separateencryptedidentity being committed
by subscription_two_devices in android-managed worktree. One subscription intended
Windows+Android with per-install keys, not shared static credentials.

28Oct FINAL PRIVATE327 REPLACES OLD327 BELOW: Windows b5e8de6, native7ea7989.
Fixes pending-start Stop deadlock and failed-create adapter intent cleanup.
Candidate132726784B SHA125a509eed4da022511017cde7dcb8770b891f3bbbae7f2cb31c09f38287d7f5.
Native4500480B SHA699c6fad51ffe6341cacb76f378d3af0a019f155edca4a8d8573443a0048c308.
Session21+real JNA1 checks PASS, package/native/payload verified. Fullhost NOT RUN;
OTA24 unchanged. Owner asks self-diagnosis; no repeated request for logs.
See final section reports/windows-stop-20261028/ATLAS_RESULT.md.

28Oct LATEST INCIDENT OVERRIDES HISTORICAL RELEASE NOTES BELOW: public Windows
OTA restored to signed0.3.24; installed25/26 not downgraded. Private0.3.27 built
from96e5ea3 (pushed), native ec91c71/2af7d100/4501504B. Candidate132730880B SHA
2f26262277e4e4e97a0b90bec66b4932249688dcb924c246dca395e1452bb955.
Bounded native cleanup retry, noncancellable session completion and safe native
diagnostics fixed. Full helper+desktop tests PASS (optionalOS cases skipped),
actual newDLL JNA memory/policy8662+Stop PASS; package/native/payload hashes PASS.
No actual Wintun/Internet acceptance: both RunAs attempts returned cancellation.
Manual admin launcher reports/windows-stop-20261028/run-wintun-probe-admin.ps1
ready but NOT RUN; do not auto-repeat UAC. Full Fairway harness prepared, NOT RUN.
Owner's failing OS stage UNKNOWN; requested actual failing log lines. Do not
publish327 from current evidence. Read reports/windows-stop-20261028/ATLAS_RESULT.md.
Cleanup completed PC679306766B and NL about4971975754 unique logical bytes;
current/rollback artifacts retained; rc34 archive preserved after auto-review
rejection. Android85 and FI issuer active/health200 unchanged.

28Oct Windows OTA 0.3.26/code326 RELEASED: fixes Fairway-selected import of
legacy compact veilark://import?t= links by routing non-managed sources through
the standard fetcher. Real owner static profile imported through actual corrected
Windows importer; focused managed/legacy tests passed. Android Beta15 parser and
UI source supports same link; 16 parser tests pass, physical import NOT tested.
Source commit d42ecee. OTA SHA256 a3dc7fae910747ebc701c69fcc3b6fe2a8544f75522533f1bdb3c98b035c70e5,
132689920 bytes, signed manifest, Authenticode NotSigned. Native DLL extracted
from actual inner installer matches fa516b... pin; wrapper extraction PASS.
Same legacy static identity on two devices can evict itself; per-device Android
managed enrollment remains unreleased. No live Windows tunnel acceptance.

28Oct RELEASED Windows 0.3.25/code325 OTA. Public manifest verified, installer
SHA256 47ba6c522ef99517111b98d05a9746edb59374229b6b94849349739e58343c84,
132689920 bytes; previous manifest backed up on server. Authenticode NotSigned,
Ed25519 OTA manifest signed. Source metadata commit 2a016d5. FI Core issuance
enabled after readiness race repaired; real Windows admin managed claim and
idempotent retry PASS without VPN. NL snapshot revision2/ACK2, six legacy
identities retained, one active managed binding. Windows real tunnel/device test
deferred to owner. Android managed Fairway parity source 3d35797 not released;
do not claim same managed URL is verified for both platforms yet.

Atlas owns Windows app/import/store/JNA/UI/packaging/sole OTA. Vector owns private
Core/server/native code in C:/AI-Agent/veilark-fairway-passage-integration-20261024.
Windows branch local/windows-fairway-20261025; do not touch unrelated untracked
 docs/RELEASE_0.3.21.md or ui-research/.

## Current state
- 28Oct new server8d20bc9 ELF8a16e889 (2374888B) fixes missing deployed Legacy
  proof; isolated Linux CLI accepts true/rejects invalid; actual TUN + exact
  remotehelper add/revoke + interrupted-update restart/ACK PASS with proof enabled.
  Receipts reports/core-server-snapshot-20261027/frozen-8d20bc93. FI runtime
  operator scripts locally repinned 8a16; NOT activated. Vector sole NL operator.
  Android managed Fairway source prepared separately commit3d35797, 4/4 focused
  and compile PASS; no device/live API evidence or Android OTA yet.
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
  FI runtime helper scripts previously pinned old7fac; repinned8a16 as above.
  Active NL service and Android85 unchanged until an actual rollout confirms it.
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
