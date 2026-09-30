# Cross-platform update notifications

Scope: Android, Windows and macOS. This is a source change, not an OTA publication.

Windows checks the existing authenticated OTA manifest every six hours while the
application remains running, including in the tray. A newer release produces one
tray notification per release code, persisted in user preferences. Opening the
tray while an update is available opens Updates. Background failures do not erase
available-update state; Ready/Installing/Downloading are left untouched. No
automatic download, installation or VPN interruption is introduced.

Android uses a persisted six-hour JobScheduler job with network and battery-not-low
constraints. The separate `:updates` process bypasses VPN/native initialization.
The system may defer jobs. Notification permission/channel must be enabled.
Tapping opens the update confirmation and release notes, not an automatic install.

macOS uses the application lifetime (including menu-bar-only operation) and native
UNUserNotificationCenter, with user permission. Clicking selects Settings. The
native bridge is identity-gated to the packaged app and does not replace an
existing unrelated notification delegate. Receipt is recorded only after the
native request is accepted, not as a claim of visible delivery.

Local evidence: Windows compilation and UpdateNoticePolicyTest passed; Android
private Kotlin compilation, notice policy unit tests and lint passed; macOS JVM
compilation/tests passed. Native macOS CI: 36754987315 (check final result).
Physical notification delivery/click acceptance remains unverified.

Previous clients do not gain this behavior remotely. It requires an update first.
Desktop clients fully quit do not check; no resident updater service was added.
Installed channels/signing keys/production manifests are unchanged.
