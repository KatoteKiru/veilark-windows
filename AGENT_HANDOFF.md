# Veilark Windows current handoff

26Oct Core per-device binding storage committed in bot sibling ae4ae38: explicit
additive v1 migration, immutable pending public-key identity under existing paid
owner/device/install/slot/expiry guards; same-transaction sealed baseline +pending
revoke/admin/purchase/restricted guards.22 new tempDB testsPASS4.067s,17 old
protected-baseline/support testsPASS7.132s; real init_db schema compatibility,
reviewfixes and secret scan170PASS. No automatic DB migration or live changes.
Still no authorized HTTP claim/publication/daemonack/revoke/admin migration or
encrypted Windows enrollment delivery; pending record grants no network access.
Vector native source a3154e4 improves live-owned IO repair, no new frozen DLL;
restart ownership/executor and real host acceptance remain open before OTA.

26Oct Atlas control-plane prerequisite source fixed in sibling
C:/AI-Agent/veilark-bot-logic-tma branch local/windows-core-provider-boundary-20261026:
unknown kinds no longer become Trust cleanup/revoke or sing-box delivery URLs.
8 targeted temporary-DB/mocked-provider cases passed21.951s, source review clear,
secret scan168filesPASS. Not deployed; Core provider/schema/ownership-to-publisher
integration still absent. Vector native recovery WIP untouched. No OTA readiness.

26Oct owner will perform physical Windows product test after OTA; do necessary
development acceptance ourselves. Owner asks to resend existing admin subscription
when Windows is ready. Do not persist bearer link here. Same account/subscription
should cover Android+Windows via distinct per-install keys; existing static Core
profile copied to PC is NOT concurrent-safe (new auth replaces same-key session).
Existing admin link device-aware compatibility remains unverified; no promise
that copying today's link yields independent credentials. Preserve Android key.

26Oct new private native journal lab e33e479d/40331361B from564e65b paired with
actual Windows JNA: ABI1/caps13/process-pin/optionalconfigure_host verified.
Two load/create/configure/status/stop/release/close cycles in one test passed11s,
0skip. Synthetic endpoint/GUID/LUID and nonexistent Wintun; no begin_auth/host.
Actual consumer FULL_TUN gates rejected start; owner freed, no worker/resources.
New memory pairing test frozen to explicit VEILARK_NATIVE_JOURNAL_TEST_DLL+pin.
No real journal filesystem/crash recovery or host/traffic acceptance established.
Artifact remains private lab; no product capability, installer or OTA changes.

26Oct native terminal error precedence fixed: awaitPhase and health share
Status.isActivePhase requiring expected phase + live worker + zero outcome.
Previously matching phase/worker could hide terminal error, including journal35.
2 new status cases + default closed controller case PASS; explicit AUTH-DLL case
skipped because fixture env absent in this run (earlier actual DLL evidence is
separate). Desktopcompile PASS58s. Scoped source review no blocker. No host run.
Vector current journal hooks WIP includes before-images for routes/address/MTU/
DNS/dynamicWFP and retained custody on failed ack/cleanup; crash rollback executor,
uncertain IO reconciliation and isolated host acceptance still open. No new DLL.

26Oct legacy store rollback checkpoint implemented: before first Core save,
create immutable encrypted profiles.dat.before-fairway.bak. Exact legacy bytes
are reused only after strict raw v1-v6/two-engine decode; otherwise legacy-only
projection excludes Core fields/config. Existing corrupt/incompatible checkpoint
refuses save before replacing primary. No automatic restore or profile deletion.
Create-only hard link publishes forced staged file; unsupported filesystem fails
closed. No directory fsync/full cross-process transaction guarantee claimed.
ProfileStoreTest8 + FairwayRollbackStoreTest5 + CoreProductGateTest3:16 cases,
0fail/0error/0skip; desktopcompile PASS19s. Scoped review found normalization
edge, fixed and regression passed; final source review no remaining blocker.
Store rollback source gate now closed; manual rollback/installer acceptance still
pending with actual isolated Windows runtime. No public push or OTA activation.

26Oct releasefactory/uplink source ready: fixed appresourceDLL+trustedbuildpin,
fixedWintunpath, existingDPAPIinstallationGUID. ReadonlyGetBestInterfaceEx selects
OSrouteindex; require exactactivephysical/nonfilter/nontunnelLUID, neverfallback
tootherNIC. Sameguard asnativeCapturedUplink; actualreadonlylookupPASS without
changingcurrentVPN. SeparateCorebutton action nowboundtoCoremodel viareleaseflag
(stillfalse); defaultcontrollers stilllegacyonly. Nofake readiness or hoststart.
Finalfullregression: shared93/helper201=294cases/0fail/0error/4conditionalskip,
desktopcompilePASS31s; report full-app-integration-20261026.json. BeforeOTA remain
nativejournal/recovery+acceptedDLL, per-deviceenrollment/encryptedlink transport,
actual isolatedWindows traffic/stop/installer/rollback/OTA.

26Oct app engine integration source implemented: VeilarkCore distinct enum with
availableInProduct=false; strictLegacy profile importer has opt-in preparation
switch(defaultclosed); Core config/routing never enters sing-box; unsupported
routing/TLSfragment fail. Catalog keeps Core subscription records but excludes
them from active profiles/selection until releasegate. Legacyselectorexplicittwo.
FairwayEngineController now uses registeredowner/config/auth/host/status pipeline,
exactinstallationGUID adapter check, realLUIDstats, noidleHTTPhealthprobes; cleanup
retainsDLL/owner and survivescaller cancellation. Not registered in defaultapp.
Shared93cases/0fail/2skip; fullhelperPASS +desktopcompile27s, final2controllercases
actualAUTHDLLPASS10s. Earlier testlabel assumed2engines, fixednowexhaustive3.
Scopedsource review no blocker. NO fullnativepath/trafficacceptance.
Next: releasefactory trustedartifactmanifest/uplinkcapture/installationmetadata;
per-device enrollment/encryptedlinkformat, profile-store rollback policy, then
isolatedhost/installer/OTA. Coreimport/button remain gateduntiltheseareaccepted.

26Oct OWNER UI REQUIREMENT: Fairway must have a separate button, analogous to
Android's separate Core entry below sing-box/TrustTunnel. Implemented in Windows
EngineSelector as separate full-width button with official RU/EN Core/Fairway
label. Disabled until real host/controller/acceptance ready; does not alter
legacy selection/storage/engine enum. Accessible unavailable state, no extra
technical UI text/redesign. Existing nativeCompose visual render PASS54s; inspected
light/dark and150%DPI output, label fits. No real app installation/networkstart.
Next connect this separate entry to the actual Core controller/subscription once
native fullhost/recovery/isolatedtraffic gates pass. Do not foldFairway intolegacy.

26Oct app teardown bridge implemented: Owner.stopAndRelease on NonCancellable+IO,
serialized coroutine mutex, explicit Stop->boundedJoin->confirmedcanRelease/
worker/resources->Release. Failure retainsowner/DLL for explicit retry; no polling
or automatic cleanup loop. Join timeout is not an end-to-end dispatcher/queue
deadline. Actual8fd DLL3targetedcasesPASS/0skip (2new: cancellationfinally and
simultaneousstop), desktopcompilePASS63s includingGradledaemonstartup; bounded
source review no blocker. No real host cleanup failure or traffic acceptance.
Readonlyrunnerinventory: HyperVGet-VM unavailable, WindowsSandbox.exe absent;
not proof no other runner exists. ActiveVPNPC remainsunsuitable. Vector continues
durablejournal/setupgatewaygap. No fullhostartifact/OSchanges/UI/OTAenable.

26Oct consumer now honors native static CAP_PROCESS_PIN8: reports restart-needed
policy and retains verified file lease for process lifetime after logical module
close, including rejected-load cleanup. Retained leases dedup by canonical path
and verified SHA to avoid per-reconnect handle growth. Existing8fd/b45 nonpin
behavior unchanged.6targetedcases0fail/0skip+desktopcompilePASS13s. Newbit8 branch
source implemented; native pin/latecallback execution NOTtested (newlabartifact
not ready). Host-labcaps13 still lacks FULL_TUN2; no appenable/host invocation.
Unknown loadedimages rejected beforecapabilitydiscovery retainleaseconservatively
forprocesslife; no assumption that early ABI/export failure provesunloadability.
Next native durable crash/watch reconciliation, then real isolated Windows runner.

26Oct additive host API now CONSUMED: compiled candidateb45a91/34249486B exports
optionalconfigure_host. JNA48-byte descriptor/UTF16path/IPv4DNS/prefix8buffers
match frozenheadera82ce7; private buffers live/immutable through call. RealJNA
accepted all8681 shippedverifiedRU prefixes +DNS inALLOCATED; no worker/routes/
file or host start. Endpoint/32/repeated configure/invalid inputs rejected.
Old8fd9export DLL stillloads; configure unavailable failslocally.2actualDLLcases
0fail/0error/0skip +desktopcompilePASS16s; source review no blocker. FULL_TUN is
stillunset; application engine/UI notenabled. See JNA_V1_INTEGRATION.md follow-up.
Next Vector fullhostactor/DNS/RU/WFP/cleanup, then appcontroller+enrollment and
actual Windows traffic acceptance. No OTA/driver/hostVPN changes.

26Oct RU app input implemented: FairwayIpv4Routes extracts only IPv4 CIDRs from
existing validated GeoRoutingAssets, canonicalizes exact union by sorted ranges,
merges only actual covered addresses, rejects malformed/noncanonical input and
native output overflow before OS changes. No new Geo download/DB/UI; legacy
consumers unchanged. Shipped pinned SRS decoded offline in isolated temp cache:
8681 IPv4 inputs ->8681 exact prefixes (already minimal, NOT advertised1550),
fits native16384.6targetedcases0fail/0error/0skip+desktopcompilePASS11s; independent
bounded source review no blocker. Not yet passed to native host or installed.
Vector's additive configure_host48/prefix8 source contract acknowledged, but
not consumed until compiled/frozen candidate; old8fd DLL has no such export.
Next: bind actual configure_host and runtime owner to app after fullhost contract
is ready; preserve live network. See reports/windows-fairway-20261025/RU_ROUTE_INPUT.md.

26Oct FROZEN API CONSUMED: Vector private Core940801c7 supplies implemented v1
header/config112/status64/CDECL9exports and candidate DLL8fd89a17 (AUTH only).
Atlas FairwayNativeLibrary now resolves actual exports with restricted System32
dependency lookup, exact explicit offsets, caller+Memory key wipe, status parse
only after OK. DLL and verified read lease retained for every owner/inflight;
worker exit does not release custody. Join/Release serialized; failed release
retains owner; rejected-load cleanup also retained for retry. Independent review
caught that last failure path and confirmed fix. Product begin/start blocked
while FULL_TUN absent. Real development DLL memory-only lifecycle/concurrent
Stop/Join/Status test PASS, no begin_auth/socket/routes/Wintun calls.
Full helper185/0fail/0error/2gatedskip +desktopcompile PASS22s. Evidence:
reports/windows-fairway-20261025/JNA_V1_INTEGRATION.md and jna-v1-regression.json.
No installer/OTA/public push. Next: consume Vector full host capability only
after DNS/RU/WFP/cleanup completion and actual acceptance; then EngineController
and subscription enrollment integration. This checkpoint is NOT a working VPN.

26Oct resumed with Vector (successful direct ACK): native host address/MTU and
ownership-before-side-effects continue; implemented exports/header NOT frozen.
Atlas added VerifiedNativeArtifact: absolute canonical regular-file path,
packaged size/SHA256 pin, Windows FILE_SHARE_READ handle held through synchronous
loader callback; denies concurrent write/delete, releases on validation/load
failure, preserves original exception if close also fails. Not wired to a loader
yet; no speculative JNA or product enable. Four actual Windows filesystem tests
PASS; full helper184 cases/0fail/0error/2gatedskip +desktopcompile PASS27s.
Independent bounded source review complete; no broad security/ACL/dependency or
parent-directory race guarantee. See reports/windows-fairway-20261025/NATIVE_ARTIFACT_GATE.md.
Next: Vector completes host policy +real C exports; wire verified pins/load flags,
DPAPI GUID/device keys and owner-aware controller. No public push or OTA.

25Oct appcustodycontract implemented: EngineController.hasOwnedResources default
isAlive (legacyunchanged), Nativeoverride musttrack unreleasedowner independently
ofworker. WindowsVpnSession retainscontroller+GeoLease/stopRequired onworkerexit
withpendingcleanup; failed/incompleteStop blocksIdle/newStart untilretry succeeds.
3newregressions; helpertests+desktopcompilePASS26s. Combinedshared/helper270cases
0fail/0error/4skip. See RESOURCE_CUSTODY.md. Vector ACKsame contract; Core3175902
typedendpointcustodyfixcheckedsource. NativeCABI/fullpolicy/activationpending.

25Oct endpointreview: VectorportableWindowsrecoverycasePASS17.05s(realUDPNoise/
syntheticHostPort), notWintun/WAN. Newprivatewindows-network endpointleaf reviewed
read-only: confirmedcreatedroutecustody lost ifreadbackverify+cleanupbothfail;
sentVector, noAtlasCoreedit. Also endpointrowverify isnotuplinkhealth; own/32 can
maskdefaultgatewaydrift. See reports/windows-fairway-20261025/ENDPOINT_ROUTE_REVIEW.md.
AwaitVectorfix/frozenhostABI; no liveWindowsnetworkoperations/engineactivation.

25Oct UACconsumercheckpoint: sourceconfirmedMain persistsDPAPIprofiles thenpasses
only--connect toexistingrunas; no privatekey transfer viaargv/envatthisboundary.
Addedhandledsavefailure/cancellation inpre-elevationcoroutine; desktopcompile
PASS58s. No actualUAC/nativeDLL/adapter/routingchange. See rootreports/windows-
fairway-20261025/ELEVATION_BOUNDARY.md. Vectorleafcheckpointdc67c3c frozenprivate;
portable1193Legacyrecoveryparitynext, CABIhostpolicy/activationstillpending.

25Oct Atlas GUIDstorage implemented: FairwayAdapterIdentityStore DPAPI metadata,
oneGUIDperexistinginstallation, independentofsubscription/privatekeys; create-only
winner/race-safe, corruptionmismatchfailno rotation, exactWindows16B GUIDlayout.
ClientInstallationIdentity extractedfromexistingheaderprovider: samePreferences
path/install_id/flush policy, sharedbylocalmetadata+subscriptionheaders.6targeted
checks includingrealDPAPI PASS; fullshared+helper269/0fail/0error/4skipPASS. See
reports/windows-fairway-20261025/ADAPTER_IDENTITY.md. No native activation/adapter/
networkchange. Vectorleafrequires nonzeroGUID; futureCABIawaitsfrozenheader.

25Oct nativeconsumerfollowup: Vector WIP has WindowsArcRAII leaf +HostPort bridge,
not a productbackend. Atlas boundedreadreview/artifactinspection saved under
reports/windows-fairway-20261025/NATIVE_CONSUMER_REVIEW.md andwintun-artifact.json.
Existingx64Wintun0.14.1/427552B/SHAe5da8447/AuthentiCodeValid/10requiredexports.
Notloaded/noadapter/networkchanges. Stableinstallation-boundGUID requested before
production toavoidrandomNLAentries; CABIheaderstillpending, no speculativeJNA.

25Oct ACTIVE RESUMED after Beta15 publication: owner says continue Windows.
Vector ACK confirmed: native owns Wintun/routes/DNS/WFP lifecycle in Corebranch
codex/windows-core-enrollment-20261025, privatecheckoutveilark-fairway-passage-
integration-20261024/fc77d7b. DLL+CABI/JNA agreed; header not frozen/implemented,
do NOT invent a JNA layout or enable product engine before native acceptance.
Atlas owns EngineController integration/DPAPI/import/updater. Same subscription
needs independent keys per installation under existing entitlement/device model.
Atlas fixed raw exception/bearer/key disclosure in import UI and malformed or
duplicate schema fallback into sing-box. Shared90cases0fail/0error/2conditional
skipPASS; report reports/windows-fairway-20261025/import-regression.json. Existing
engines/UI/network untouched. Preserve useruntrackeddocs/RELEASE_0.3.21.md and
ui-research/. No newWindowsinstaller/OTA yet. Native recovery must include portable
equivalent of1193Legacy repair before product activation; fc77 pump lacks it.
Android85/server135a published and protected; prior priority override below old.

25Oct priority override: Fairway silent-stall investigation before Windows.
See C:/AI-Agent/reports/fairway-stall-20261025/INCIDENT.md. Live server unchanged;
Legacy return-path monitoring gap confirmed, incident cause not proven.
Vector ownership ACK is now confirmed in reports/windows-fairway-20261025/
VECTOR_HANDOFF.md; Core checkpointfc77d7b includes portable packetpump but no
Wintun/backend acceptance. Windows local app checkpoint6d06a49 remains protected.

## 25 October 2026 — Fairway preparation, NOT a Windows release

Owner requests Fairway compatibility and one subscription usable on phone+PC.
Local branch local/windows-fairway-20261025 from0a4d12c; preserve user-untracked
docs/RELEASE_0.3.21.md and ui-research/. No version bump/OTA/live network changes.
Contract/evidence: C:/AI-Agent/reports/windows-fairway-20261025/TEAM_CONTRACT.md.
Core server has one active session per static client key; separate device keys
are required. Existing controlplane has per-install devices/3slots, but only
Trust/sing-box providers, notCore. Reuse that model, no parallel account store.
Windows has NO Core Wintun dataplane yet; don't enable an inoperative engine.
Atlas prepares identical strict Android Core profile codec, DPAPI device-key
storage and import/log boundaries. Vector requested native backend and atomic
provider enrollment/revoke. Coordination request also in Core checkout handoff;
no delivery/ack claimed via stale app thread connector. Preparation testsPASS:
286cases/0fail/0error/4gatedskips, desktopcompile +isolatedJDK17runtimePASS,
SunEC/X25519provider presentinactualjimage. StrictAndroidparserbyteidentical;
8devicekeystorecases include JDKpeerDH+6waycreate. See docs/FAIRWAY_WINDOWS_PREPARATION_2026-10-25.md.
No WindowsTUN/device-enrollment/installeracceptance inferred. Coreprovider+native
backend stillrequired beforeUIactivation ornewWindowsrelease.
Published Android Beta14 and serverc959/configad5 remain unchanged.

## Current publication — 2026-10-08 (supersedes historical notes below)

Windows0.3.24/324 LIVE on GitHub/established signedOTA/catalog. Sourcefd316faf, PR3mergeda18db2df, CI37723300905 package+0.3.23upgrade PASS; full public EXE hash/size/manifest+notes signatures verified. TLS-preserving bounded endpointDNS bootstrap,266tests/4gatedskips. No activeVPN/endpoint/proxy restart. AuthenticodeNotSigned/unsigned_preview unchanged. Current evidence/backups docs/RELEASE_0.3.24.md. Actual evening DNS fault confirmed; remaining AndroidTelegram upload complaint under investigation, broadoutage not declaredclosed. Direct WiFiFI/DE TCP blockedWSA10013, systemrouteall3passes with existingNLWintun active; exactfilterowner notproven. Physical affectedPCacceptance pending.

### Historical0.3.23 publication

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

## TrustTunnel incident inspection 2026-10-08
Read docs/TRUST_INCIDENT_CLIENT_2026-10-08.md. Packaged native1.1.7; matchingtag170609c24ca source stores bufferevent failure return-1 asWinsockerror, obscuring realcause. Windows DNS bootstrap candidate0.3.24/324 is now implemented locally; no native/OTA/liveVPN change. Read docs/RELEASE_0.3.24.md for bounds and publication gates. Hypothesis anti_dpi=false→prefixfailure rejected by working storedcustomer HTTPS; no policyflip. Original routingtests10pass. Existing ownerfilespreserved. Actual device outage remains unknown; root owns live transport/server diagnosis.

## 26 October: Windows elevation and server enrollment source
- Controller now checks existing elevation policy before DLL/host preparation and
  returns ELEVATION_REQUIRED; UI already preserves store and uses runas/--connect.
- Focused controller/elevation tests and desktop compilation PASS58s; optional
  AUTH-only DLL case skipped because explicit fixture environment was absent.
- Private server snapshot v2 source committed6dd10ec in
  C:/AI-Agent/veilark-core-server-snapshot-20261026;17Python+9portableRust+6Core cases,
  Linux --tests crosscheck and6valid/7invalid producer/checker pairing passed.
- Snapshot ACK disk error no longer stops packet driver; disable/expiry retire
  membership/session/queued packets without permanent revocation tombstones.
- Source only: durable antirollback and real Linux acceptance still open.
- Vector notified. Native FULL_TUN, provider/delivery and isolated Windows traffic
  acceptance still prevent enabling Fairway/OTA. Android/live server unchanged.
