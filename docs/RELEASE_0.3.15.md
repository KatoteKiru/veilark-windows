# Veilark Windows 0.3.15

Windows visual refinement: the shared Veilark vector replaces shield imagery,
follows the interface theme, and is rasterized directly into nine launcher sizes
(16–256 px, including 20/40 px for fractional DPI). The OTA bootstrap now embeds
the same icon. The connection card stays neutral; transitions are shorter and
non-bouncy, and changing status no longer animates the whole card's dimensions.

Subscription and routing destinations use consistent names. Engine selectors
have a 48 dp minimum height, allowing labels to grow without squeezing controls.

No VPN runtime, routing, subscription, storage or updater verification behavior
changed. Package name, MSI UpgradeCode and OTA trust keys are preserved.

Verification: native offscreen Compose screenshots cover light/dark and
100/125/150% density; they do not prove an installed-window appearance, tray icon
cache refresh, elevated TUN connectivity or in-place upgrade data preservation.
Those remain physical Windows acceptance checks. Installer authenticity remains
Ed25519 manifest + SHA-256; no Microsoft-trusted Authenticode identity is claimed.
