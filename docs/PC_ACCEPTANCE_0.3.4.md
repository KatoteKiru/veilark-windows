# Veilark Windows 0.3.4 — PC acceptance

Install 0.3.4 from Updates over the existing Veilark installation. Do not
uninstall the old version and do not delete `%LOCALAPPDATA%\Veilark`.

## In-place update

1. Download 0.3.4 in Veilark, choose Install and restart, and accept UAC.
2. Confirm Veilark reopens, Windows Apps contains one Veilark 0.3.4 entry, and
   existing subscriptions, selected nodes and routing settings remain.
3. Confirm Veilark Trust still contains both TrustTunnel endpoints.

## UI and languages

1. Switch RU/EN in the navigation footer and restart. The chosen language must
   remain selected.
2. At 100%, 125% and 150% Windows scaling confirm navigation labels, both core
   names, the server selector, Refresh, Ping and Connect/Stop fit without
   horizontal scrolling.
3. Open the Home server selector for both cores and verify all subscriptions and
   every nested server appear. Repeated open/select/close must not crash.

## Traffic and routing

Disconnect Happ or any competing VPN for the definitive route test.

1. For sing-box and TrustTunnel verify All traffic, Russia direct, Russia through
   VPN and one custom domain/CIDR pair.
2. In every mode open one Russian and one non-Russian HTTPS site and confirm DNS
   resolution, expected egress, and growing Veilark byte counters.
3. Press Stop while connecting, reconnect, then disconnect normally. No
   Veilark-owned core process, TUN adapter, route or DNS setting may remain.

If a check fails, preserve the profile store and copy the redacted Journal with
the exact time, core, server and routing preset.
