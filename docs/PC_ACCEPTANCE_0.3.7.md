# Veilark Windows 0.3.7 — PC acceptance

Update the existing Veilark installation. Do not uninstall the old version and
do not delete `%LOCALAPPDATA%\Veilark`.

## In-place OTA update

1. Open **Settings → Updates**, download 0.3.7, choose Install and accept UAC.
2. Confirm that Veilark reopens as 0.3.7 and Windows Apps contains one Veilark
   entry, not a parallel installation.
3. Confirm that subscriptions, selected servers, routing settings and both
   built-in Veilark Trust endpoints are still present.

## Centered compact UI

1. Confirm the default window remains 600×440 and the Material 3 palette is
   unchanged. The 120 dp connection circle must be visually centered.
2. Confirm that status, core selection, server selector and Add/Refresh/Ping
   actions form one clean vertical hierarchy below the circle.
3. Trigger or observe a degraded connection. Server selection and the control
   actions must remain above the detailed diagnostic strip and stay reachable.
4. Open the grouped server selector for both cores. Every subscription and
   nested server must be selectable; repeated open/select/close must not crash.
5. Repeat the layout check in Russian and English and at the 520×420 minimum.

## Foreign branch of Russia-direct routing

Disconnect Happ and any other VPN before the definitive route test. If another
TUN remains active, Veilark must refuse to start and identify the conflict.

With **Russia direct, other traffic through VPN** and TrustTunnel selected:

1. Open one Russian IP-check page and confirm that it sees the normal Russian
   provider address.
2. Open GitHub and YouTube, then two unrelated foreign HTTPS sites. They must
   load fully, not only the first document or a subset of assets.
3. Confirm with a foreign IP-check page that the foreign branch exits through
   the selected VPN server rather than the Russian provider.
4. Repeat once with browser QUIC enabled and once after a clean reconnect.
5. Confirm traffic counters grow and Disconnect restores normal access without
   an orphan core process or Veilark-owned TUN.

Also smoke-test **All through VPN**, **Russia through VPN**, and one manual
direct/VPN rule on both cores. If a check fails, keep the encrypted profile
store and record the exact time, core, server, preset, domain and whether DNS
resolution succeeded before copying the redacted Journal.
