# Veilark Windows 0.3.5 — PC acceptance

Update from the existing Veilark installation. Do not uninstall the old version
and do not delete `%LOCALAPPDATA%\Veilark`.

## In-place OTA update

1. Open **Settings → Updates**, download 0.3.5, choose Install and accept UAC.
2. Confirm that Veilark reopens as 0.3.5 and Windows Apps contains one Veilark
   entry, not a parallel second installation.
3. Confirm that subscriptions, selected servers, routing settings and both
   built-in Veilark Trust endpoints are still present.

## Compact UI

1. Confirm the default window is 640×520 and the home page fits without a wide
   empty workspace. Primary pages must be in the hamburger menu; language and
   routing must be under the gear.
2. Open the grouped server selector with sing-box and TrustTunnel. Every
   subscription and nested server must be selectable; repeated open/select/close
   must not crash.
3. Start connecting, then press the circular control again. It must stop the
   unfinished attempt. Refresh and Ping must remain usable while disconnected.
4. Switch RU/EN under the gear and restart; the selection must persist.

## Traffic and routing

Disconnect Happ and any other VPN before the definitive route test. If another
TUN remains active, Veilark must refuse to start and identify the conflict.

For both cores:

1. Test **All through VPN** with one Russian and one foreign HTTPS site.
2. Test **Russia direct, other traffic through VPN**. Verify Russian DNS/egress
   is local and foreign DNS/egress uses the tunnel.
3. Test **Russia through VPN, other traffic direct** and confirm the inverse.
4. Test one manual direct domain/CIDR and one manual VPN domain/CIDR.
5. Confirm pages load, Veilark counters grow, and Disconnect restores normal
   access without an orphan core process or Veilark-owned TUN.

If a check fails, do not delete the profile store. Copy the redacted Journal and
record the exact time, core, selected server, routing preset, failing domain and
whether its DNS resolution succeeded.
