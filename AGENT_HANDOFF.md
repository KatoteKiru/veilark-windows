# Windows Fairway — current handoff, 30 October 2026

## Objective and authority
Deliver real Windows Fairway TUN/traffic/Stop/reconnect, then OTA. One managed subscription must support Windows + Android using separate per-install keys.
Owner explicitly forbids interrupting the working PC, stopping TrustTunnel, or testing host TUN/UAC here. Use disposable GitHub-hosted Windows and scoped synthetic NL peer only.
Direct human consent covers exact linux_lab_v2.py (SHA b51a88a545f003d27b8d9ad7e0e77aafc82e20311482ed7507d6be4f1669cc57) on NL. Do not bypass prior review using another operator/tool/agent. Vector alone operates the Linux lab.

## Recover current state first
Detailed authoritative per-attempt state: C:/AI-Agent/reports/windows-fullhost-20261028/RUN_STATE.md.
Safe receipts/diagnostics and frozen native artifacts are under that folder. Never output profile/key/bearer/transport URL or raw logs.
Windows branch local/windows-fairway-20261025, runtime-tested source9856580.
Vector chat: 01a049ec-1dab-7533-9234-786a387fb4e6. Native worktree C:/AI-Agent/veilark-fairway-passage-integration-20261024.

## Latest real full-TUN PASS — no active lab or CI secrets
CI40891175866; retired synthetic lab193216b1d4532ffb; exact WindowsSHA9856580c546856fc2c44f7f49838f494bfe99c8a.
Native016990a/c3a748ae6b98ca779d26770e81b6019d80161e0f3cbed051b4d9ffde86004623, 4605525 bytes, ABI1/caps11.
Both cycles passed every mandatory TCP/UDP/Stop/restore field; processExit0 and post-exit cleanup true. Receipt ci-40891175866/receipt/fairway-full-tun-receipt.json.
Encrypted bundle, two Actions secrets and local DPAPI custody deleted; Vector lab cleanup/protected production unchanged/keys retired confirmed. No active-run.json.
For future runs ALWAYS use fresh trusted preflight and synthetic keys, and ci_bundle.py cleanup even on failure. fetch_route_diag.py extracts fixed numeric diagnostics without persisting raw logs.

## Confirmed fixes and actual evidence
- 79d162e: real Wintun description is Veilark Tunnel; old cosmetic heuristic rejected the owned adapter. Admission/health now exact installation GUID + operational index/LUID, excludes physical uplinks.
- Actual bare Wintun CI40759834482 PASS1/0skip: create/start/close/absence only, NOT full Fairway Internet.
- Native1370799: manually created unicast routes explicitly disable Loopback/AutoconfigureAddress/Publish.
- CI40846657612 proved strict endpoint readback mismatch only bit13 Immortal. Native d3eb585 explicitly sets Immortal=false, retains infinite lifetimes and strict ownership matcher.
- CI40883247054 confirmed server AUTH admitted1, real Windows TUN created; failed later at DNS/routes/policy. Stop, adapter absence and Internet recovery passed. No full traffic acceptance.
- Native3e2bfd1 preserves original startup outcome/OS BEFORE cleanup overwrites status; stages60 DNScreate,61 routescreate,62 policycreate,63 directenable,64 policyapply,65 routesapply,66 DNSapply.
- CI40885403336 identified original stage64 POLICY_APPLY/outcome15. Raw OS was lost in WFP formatted error wrapper.
- Nativec1ce5eb fixes proven API-contract defect: RPC-only IP_LOCAL_ADDRESS_V4 used on ALE_AUTH_CONNECT_V4; now generic IP_LOCAL_ADDRESS, same UINT32/address/interface/app/protocol constraints. Original raw error preserved, safe WFP filter index/OS diagnostics. Its DLL passed real two-cycle CI40888532816; final016990a inherits the fix and passed CI40891175866.
- Root1d98889 adds strict baseline HTTPS before TUN (normal CA/revocation cache priming, no TLS bypass), finer source/route proof and stable HTTP failure class. Final acceptance requires baseline plus both original cycles.
- JNA exact frozen DLL memory/configure8662-prefix/status/Stop pairing passed1/0skip for each pin. These are NOT host-TUN acceptance.

## Acceptance, not synthetic substitutes
Current CI exercises real WindowsVpnSession: two cycles Connected, exact-owned adapter, bound HTTPS through TUN, bound UDP DNS to1.1.1.1:53, bidirectional counters, hold, Stop, strict adapter absence and Internet restoration; final process exit and parent-after-JVM file cleanup.
Native DLL is process-pinned: only parent after Java exit removes DLLs. Filesystem cleanup alone does not prove network cleanup.
Do not publish from build/unit/ABI/driver-only/source evidence. All fields and both cycles must pass; any residual network resources remain a failure.

## Release and remaining boundaries
Old journal compatibility IS included in final016990a. Known flags0000/0001/1101 are accepted only by the bounded decoder. Absent resources allow metadata-only retirement; present confirmed tagged v2 requires dead previous process, manifest/run/tag/GUID/index/key and every other semantic field exact, only known Immortal1->0 normalization. Present unconfirmed and untagged v1 remain blocked; no journal erasure or broad adoption. Focused5 safety tests and independent reviews passed.
Private accepted8363 installer: build/candidate-8363/Veilark-0.3.27.exe, 132739072B SHA29e3c177f9d618c37235e22443b46e65a2036820a3144d85b16bf2882e4f1425. Exact native extracted from actual inner installer and wrapper payload verified; candidate-8363.json records evidence. Not installed/published. Earlier327 candidates obsolete. Verification-only duplicate EXE removed.
PUBLIC WINDOWS OTA NOW0.3.27/code327, wrapper132739072B SHAa6d0d109f1c54ff41e80697e41a85de0c1bdafd6358150a2aa90e92b7d7948fc, build/candidate-final/Veilark-0.3.27.exe. Exact native in actual inner installer and bootstrap payload verified. Full public download and both manifest/notes signatures independently passed30Oct, report public-ota-verification.json. Signed24 rollback verified at /var/backups/veilark/windows/manifest-before-327-20261029T143749Z-91663b50.json (rollback-327.json). Do not re-run publisher for327: it correctly rejects non-increasing versions. Public Android remainsBeta15/code85. Installer was not run on owner PC; successful readonly ProgramData enumeration found zero exact VeilarkCoreJournal-v1 entries, and host network was not changed. See docs/RELEASE_0.3.27.md.

## Subscription compatibility
Android managed worktree C:/AI-Agent/veilark-android-managed-fairway-20261027 sourceac7a48e pushed: explicit managed import/refresh, encrypted per-source install identity, actual Core resolver, deletion tombstone. Focused15+compile passed, source-only; public Beta15 lacks this new path.
Windows+Android backend synthetic enrollment creates two separate per-install bindings/keys, idempotent retry; normal commercial device slots3. Actual concurrent two-device traffic NOT tested.
Old static compact import carries one shared identity and can evict another device. Do not promise it supports simultaneous devices; use managed enrollment.
Revoked original source-device bearer refresh edge remains separate backlog; never weaken revoke checks.

## Other constraints
Workspace audit delivered C:/AI-Agent/reports/workspace-inventory-20261029/ПОНЯТНЫЙ-ОТЧЕТ.md. User decides cleanup after reading; no project deletion/push under audit. Snapshot115.30GiB, not current live disk assertion.
Preserve unrelated docs/RELEASE_0.3.21.md and ui-research/.
Historical full handoff retained C:/AI-Agent/reports/windows-fullhost-20261028/windows-handoff-before-20261029.md; obsolete release claims there are historical only.
