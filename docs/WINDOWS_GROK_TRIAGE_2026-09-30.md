# Windows 0.3.19: evidence and remediation

Date: 2026-09-30. Working branch: codex/windows-fluent-stability.
Production has NOT been changed by this investigation. No new OTA was published.

## Scope and result

The owner rejected the replacement Fluent interface. The candidate now restores
the shipping 0.3.19 navigation, Noto Sans, surfaces and window dimensions, retaining
the previously tested bounded process-output fixes. All 15 Material typography
roles explicitly use the bundled family. This is refinement, not native WinUI or
an implemented Material Expressive migration.

| Claim | Evidence / verdict | Action / remaining acceptance |
|---|---|---|
| Public repository and old credentials | GitHub API confirms PUBLIC. The current release list starts at 0.3.12. Historical code f9c8f1c loads a generated builtin_trust_profiles.txt; a12ae1b removes the bundled-access machinery. The resource was not tracked in the inspected local history. HEAD for the documented 0.3.11 installer still returns 200 and 130995712 bytes. | Exposure risk remains OPEN. Inspect the old installer without executing it, audit all reachable Git history and CI logs/artifacts with redacted output, identify credentials and their server ownership. Presence and continued validity of live credentials are not yet proven. Removing a release is not revocation. Rotation must preserve the protected administrator subscription and legitimate clients. |
| No kill switch | No explicit killswitch setting found in the inspected application source; no independent WFP service established. However the upstream CLI v1.1.5 documentation specifies killswitch_enabled=true by default. | The claim that absence of the key proves absence of protection is incorrect. Verify the generated native config and actual Windows disconnect/core-crash/IPv6/exclusion behavior. No fail-closed guarantee is claimed. Do not deploy untested firewall rules. |
| Elevated process trusts user-controlled inputs | RuntimeResourceLocator ignores overrides for the packaged Veilark.exe, but permits development overrides. ElevationManager can elevate a development Java classpath. VeilarkPaths uses LOCALAPPDATA for runtime and geo/config files. JAVA_TOOL_OPTIONS is not neutralized at the native launcher boundary by inspected application code. | PARTIALLY CONFIRMED risk; a TOCTOU exploit has not been reproduced. A broker/service with authenticated IPC, protected resources/config staging, controlled environment and ACL checks requires separate implementation/acceptance. Clearing Java options inside Kotlin is too late for VM-startup injection. |
| 0.4.0 OTA ordering | Bootstrap concatenated decimal fields; UpdateClient compares integer counters. 0.4.0 would become 40 below 319. | FIXED in source: major*10000 + minor*100 + patch, bounded fields, overflow rejection. 0.3.19=319; 0.4.0=400. Existing published counters are not rewritten. Publication still verifies a signed live predecessor and strictly increasing counter. Regression script is wired into CI. |
| No Authenticode | Release evidence documents NotSigned; publisher checks distinguish signed and explicitly permitted unsigned artifacts. | OPEN. Ed25519 OTA verification protects the in-app delivery channel, not Windows publisher identity or arbitrary downloaded EXEs. No certificate has been invented, SmartScreen suppression or Defender clearance promised. |
| nl2 single point of failure | UpdateClient default manifest and allowed origin, plus publish_ota.py, use nl2. | OTA SPOF CONFIRMED. Subscription-wide SPOF was not established by this Windows source review. Signed mirrors need explicit compatible client allowlists, rollback checks and failover tests; no arbitrary-host fallback added. |
| Import auto-selects profile | SubscriptionCatalog.fromImportedProfiles previously passed select=true. | FIXED in source: preserve an existing selected source. Normalization still selects the first usable import for an empty catalog. Separate tests cover both cases. No claim of reproduced U-1. |
| Release branch not merged | GitHub compare reports cursor/deep-link-import ahead of main by 32 commits, behind by 0; PR query for that head returns empty. | CONFIRMED branch divergence. Current changes are not silently merged into main or represented as reviewed production. Independent review and a coherent release branch remain required. |

## Primary references

- https://github.com/TrustTunnel/TrustTunnelClient/blob/v1.1.5/trusttunnel/README.md
- https://docs.oracle.com/en/java/javase/17/troubleshoot/environment-variables-and-system-properties.html
- https://github.com/KatoteKiru/veilark-windows

## Local verification

- scripts/test-ota-version.ps1: four expected codes, 0.4.0 > 319, six invalid inputs rejected.
- SubscriptionCatalogImportTest: 2 tests, zero failures/errors.
- BrandVisualTest: 3 tests, zero failures/errors; both languages/themes,
  100/125/150 percent density, shipping/minimum window fixtures.
- Typography-role regression proves all 15 roles share a family.
- git diff --check passes. Source rendering does not prove keyboard interaction,
  installed-app VPN, network-change recovery, energy usage or security acceptance.

## Release gates still open

Do not promote this candidate as a fully secured client. Historical artifact/secret
triage, independent review, native CI for this exact revision, installed update
lineage, user subscriptions and kill-switch fault-injection checks remain open.
No user tunnel was stopped and no server credentials or firewall rules changed.
