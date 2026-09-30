# Windows interface replacement — owner brief

Updated2026-09-30. Status: direction selection; not implemented or released.

## Explicit owner decisions

The Android/Material appearance was temporary and is now rejected. Previous
sidebar and compact-card candidates are not approved visual foundations. Replace
the complete Windows interface, not only its navigation or colour palette.
Compactness and visual depth matter. No sidebar. Preserve the Veilark name and
existing monochrome emblem, not the old shield.

## Product and safety boundaries

This is an Operate surface: select subscription/protocol/location, connect and
stop unmistakably, manage subscriptions and routes, diagnose and update.
Preserve the existing session state machine, cancellation/stop behaviour,
configuration locking, DPAPI storage, subscription parsing and OTA validation.
Do not change servers, credentials, running tunnels or signing lineage as part
of visual work. Do not infer connection or traffic health from a mockup.

## Replacement, not a Material skin

Build a desktop component family: compact command buttons, labelled native-style
forms, comboboxes, details lists, menu bars, status panes and properly focused
dialogs. Replace Material-styled controls throughout connection, subscriptions,
routes, diagnostics, updates, logs and import/delete/error dialogs. A renderer
can remain Compose Desktop, but custom controls must preserve keyboard and
accessibility semantics; do not call them native WinUI controls. Any proposed
framework migration needs a separate feasibility and safe IPC review first.

No giant connection logo, mobile engine pills, card-stack scaffold, neon,
permanent blur, always-running decorative animation or invented metrics.
Light/dark follow user/system preferences, with legible high-contrast fallback.
Depth should explain active work planes and overlays, not decorate every row.

## Direction candidates

A. Compact connection utility: labelled property grid, location list, fixed
command strip. Most compact daily workflow; detailed settings open separately.
B. Connection manager: location/subscription details list, command toolbar,
selection-dependent connection pane. Best for many profiles; wider by nature.
C. Desktop inspector: integrated traditional tabs and property rows, distinct
dialog/menu planes. Most familiar desktop utility structure, still compact.

The image concepts are illustrative layouts only, not real app screenshots,
benchmarks, approved designs or runtime evidence. Choose a visual foundation
before replacing executable UI. Skill direction seed f851ad71 is recorded;
owner's Windows-only/non-Material constraints outrank unconstrained catalog styles.

Comps: `.impeccable/mocks/windows-replacement/concept-A.png`, `concept-B.png`,
`concept-C.png`; matching JSON files contain the exact built-in image-generation
prompts and approved:false. Raster prompts are also embedded in PNG metadata.
All directions must support both themes; each concept's shown theme is illustrative.
Generated emblems are placeholders: use the real existing vector mark in code.
Known concept-only defects must not be literalized: A's duplicate location selectors,
C's misspelled generated list label, and any invented icon or decoration. Actual
labels, lists, focus/navigation and state must come from the app, not raster text.

## Implementation sequence after approval

1. Record selected composition and component grammar, approved reference and
   performance/accessibility boundaries. Extract state/actions from presentation
   narrowly, preserving callback ownership and all existing locks.
2. Implement desktop tokens and foundational controls, including hover/focus,
   selected/disabled/busy/error states, keyboard and semantics. No global state
   reset during navigation or engine selection.
3. Replace the connection workspace and populated subscription list first,
   followed by routes, logs, diagnostics, update and import/delete dialogs.
   Preserve manual GEO rules, update/probe actions and cancellation access.
4. Render and exercise populated/empty/long-text states, both languages/themes,
   small/default/large windows and100/125/150/200% DPI. Test keyboard navigation,
   menus, escape/cancel, persisted data and no idle frame loop.
5. Independent code and visual review; JVM/native CI and package/resource checks.
   Real installed-app acceptance remains separate: elevation, VPN, sleep/wake,
   handover, CPU/memory and update preserving user settings/subscriptions.
6. Document the built design, then issue a separately versioned OTA only after
   applicable release gates. Never overwrite0.3.19 or use renders as VPN proof.

## Current handoff

No new executable UI is changed by this brief. The previous top-tab/card source
candidate remains on the development branch only; production is unchanged.
Next action: owner chooses/steers the composition; then replace the entire UI.

## Second owner critique: logical B, unacceptable aesthetic

Owner prefers B's connection-manager logic but rejects the harsh, dated appearance
of every first-round comp. Keep B's list/command/connection organization; soften
the visual grammar with careful typography, quiet layered surfaces, lightweight
outline icons and subtle row selection. Remove spreadsheet grid and bulky frames.
New concept B2 is in `.impeccable/mocks/windows-replacement/concept-B2.png`, with
exact prompt sidecar and embedded provenance, approved:false. It is illustrative,
not a runtime screenshot; generated city icons/logo are not yet approved assets.

### Component-library research

- https://github.com/compose-fluent/compose-fluent-ui : direct Compose desktop
  candidate, Apache-2.0, published tagv0.1.0. Provides Fluent theme/components/icons
  and layered backgrounds. Maintainers explicitly call it experimental with
  workarounds/API-change risk. Release sources use Kotlin2.2.0/Compose1.8.2;
  Veilark currently uses Kotlin2.3.20/Compose1.11.0. Source compatibility is not
  proved by these versions; validate in an isolated build/render/input probe
  before pinning or adding it to production. Do not use snapshots by default.
- https://github.com/lepoco/wpfui : WPF/.NET Fluent candidate and visual reference;
  not a drop-in dependency for the existing JVM/Compose client. Actual adoption
  would require a separately reviewed UI migration and safe core boundary.
- https://github.com/JetBrains/jewel : desktop-focused IntelliJ design language;
  not selected as the aesthetic for this consumer VPN. Original repo moved into
  IntelliJ Platform; do not depend on its archived layout blindly.

No library has been integrated and no native Mica, WinUI runtime or performance
claim follows from B2's image. Current decision: B2 visual approval first, isolated
Fluent feasibility next; existing VPN, packaging and OTA stay unchanged.
# Superseded direction — 2026-09-30

The owner rejected the experimental replacement and requested refinement of the
shipping 0.3.19 interface. Do not implement or publish this replacement brief.
Current decision/evidence: WINDOWS_GROK_TRIAGE_2026-09-30.md.
