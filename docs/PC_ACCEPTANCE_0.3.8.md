# Veilark Windows 0.3.8 — PC acceptance

Update the existing Veilark installation. Do not uninstall the old version and
do not delete `%LOCALAPPDATA%\Veilark`.

## In-place OTA update

1. Open **Settings → Updates**, download 0.3.8, choose Install and accept UAC.
2. Confirm that Veilark reopens as 0.3.8 and Windows Apps contains one Veilark
   entry, not a parallel installation.
3. Confirm that existing subscriptions, selected servers, routing settings and
   both built-in Veilark Trust endpoints are still present.

## sing-box subscription and server picker

1. Disconnect the tunnel, then press **Refresh** for the affected subscription.
   The confirmation must report the sing-box endpoint count returned by the
   panel (for example, `sing-box: 9`).
2. Open the server picker on Home. Its title and subscription header must show
   the same real endpoint count; **Automatic** must not increase that number.
3. Confirm that Germany, Netherlands and Finland are visible with flags and that
   every protocol remains an independently selectable row. Use the visible
   scrollbar when the list is taller than the popup.
4. Select one endpoint from every location, close and reopen the picker, and
   confirm selection persistence. Repeat in **Profiles**.
5. Refresh the subscription again and confirm all locations remain present and
   the app does not crash. Repeat once in Russian and once in English.

If the count reported immediately after refresh is still lower than the panel's
actual count, keep the subscription stored and provide only a redacted response
shape (keys, outbound tags and protocols; no URL, UUID, password or public key).

## Tunnel regression check

On the test PC, connect one endpoint from every location in **All through VPN**.
For each endpoint verify a foreign IP-check page, GitHub and YouTube, then stop
the tunnel and confirm normal access is restored without an orphan core process.
No live TUN test was run on the build PC.
