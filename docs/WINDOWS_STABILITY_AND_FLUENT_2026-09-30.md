# Windows stability and desktop refinement

## Brief and boundaries

Improve stability and desktop usability without replacing the working protocol,
routing, subscription, privilege or update implementation. Preserve the Veilark mark,
existing blue/neutral semantic palette and system window frame. No active VPN on the
owner's PC is restarted for testing. The current production release remains 0.3.19.

## Direction

Refine the existing Compose application towards familiar Windows/Fluent conventions:
left navigation exposing all six sections; narrow icon rail with labels on hover;
installed Segoe UI with bundled font fallback; compact geometry; clear connection
state. This is Fluent-inspired Compose, not native WinUI. Mica is deliberately absent:
opaque Skia surfaces hide a native backdrop, and correct support needs availability,
high-contrast, transparency, inactive-window and power-policy handling.

Sources: [Fluent typography](https://fluent2.microsoft.design/typography),
[Fluent shapes](https://fluent2.microsoft.design/shapes),
[Microsoft Mica guidance](https://learn.microsoft.com/en-us/windows/apps/design/style/mica).

## Execution and acceptance

1. Establish baseline: inspect actual source/release, protect live VPN, run JVM tests.
2. Stability: reproduce candidate defects before patching. ProcessCapture exceeded
   its output limit and allocated unbounded long lines before truncation. Fixed chunk
   reads retain strictly bounded output and continue draining the child pipe.
3. Cancellation/network ownership: the tested cancellation-during-disconnect case
   settles the existing owned teardown and releases its GEO lease. Hypothesized
   cancellation bug was not reproduced; lifecycle implementation was not changed.
4. Desktop shell: adaptive 184dp/56dp navigation, all destinations, hover labels;
   Segoe loaded locally (not redistributed), 900x680 initial and 540x480 minimum
   window, restrained shared shapes and 72dp connection mark. No decorative loops.
5. Visual checks: actual HomeScreen/Page, profiles and routing rendered at minimum
   and wide sizes, 100/125/150% DPI; light/EN and dark/RU. Empty-state fixtures only,
   not proof of populated workflows, accessibility or a running full application.
6. Integration: independent code and screenshot review; GitHub Windows native TUN
   checks on an isolated runner. Then real installed-app, network handover, sleep/wake,
   traffic and CPU/resource tests under separately controlled acceptance conditions.

## Evidence

- Three output-capture regressions: one failed on baseline; fixed version passes.
- Forty targeted helper tests in six suites passed, no failures/skips.
- Combined helper/shared/desktop JVM test command passed after integration.
- Initial Home visual fixture was not faithful to production layout; corrected
   before presentation. Updated renders retain production max-width and scrolling.
- Source review found no blocking correctness/security regression in its scope.
- This patch does not establish split-tunnel throughput improvement, actual Wi-Fi
   handover reliability, battery superiority or Defender reputation. These remain
   measured acceptance tasks, not claims derived from green tests.

## Next release gate

Do not overwrite production or publish the candidate as 0.3.19. Before a future OTA:
assign a new package/manifest version, keep Ed25519/MSI lineage, pass native CI and
package/resource checks, verify rollback backup and hashes, and clearly retain the
preview/unsigned-publisher limitations unless a trusted certificate is acquired.
