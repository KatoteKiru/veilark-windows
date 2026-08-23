# Veilark Windows 0.3.3 — PC acceptance

Install 0.3.3 from the Updates screen over the existing Veilark 0.3.1. Do not
uninstall the old version and do not delete `%LOCALAPPDATA%\Veilark`.

## In-place update and preserved state

1. Check for the update, download it, press Install, and accept the Windows UAC
   prompt. Veilark must close and reopen itself.
2. Windows Apps must contain one Veilark entry, version `0.3.3`, in the same
   installation directory. Existing user subscriptions, selected engines,
   selected nodes and routing settings must remain.
3. `Veilark Trust` must still exist, contain both TrustTunnel nodes, and remain
   protected from deletion.

## Compact UI and subscriptions

1. On Home switch between sing-box and TrustTunnel. Open the server selector:
   every subscription and every nested node of the selected core must appear in
   one hierarchical list.
2. Repeatedly open, search, select and close the list. Opening a subscription or
   selecting a node must not close the app or show a Windows error.
3. Add a compatible x-ui, Remnawave or plain URI/Base64/JSON/YAML subscription.
   Refresh it, run the all-node latency check and delete it. Undo must restore
   only that user subscription; the built-in subscription cannot be deleted.
4. Confirm the Updates screen shows the complete 0.3.3 change list before
   installation and the Journal remains vertically and horizontally scrollable.

## Tunnel lifecycle

1. Connect to TrustTunnel 1 and TrustTunnel 2. `Подключено` must appear only
   after the core logs `Successfully connected to endpoint` and the matching
   WinTUN adapter is operational. Open two HTTPS sites and confirm byte counters
   grow while pages load.
2. Press `Остановить` during preparation. The state must return to `Отключено`
   and no Veilark-owned TrustTunnel process or adapter may remain.
3. Repeat connect, real HTTPS traffic, stop-during-connect, reconnect and
   disconnect with a sing-box node.
4. Happ or another VPN may remain installed, but perform the definitive traffic
   run with competing VPN connections disconnected so the route owner is
   unambiguous.

## Geo routing for both cores

For each core run these checks after the geo indicator finishes updating:

1. `Весь трафик через VPN`: both a Russian and a non-Russian IP-check endpoint
   must observe the VPN egress.
2. `Россия напрямую`: a Russian endpoint must observe the ISP egress while a
   non-Russian endpoint observes the VPN egress.
3. `Россия через VPN`: a Russian endpoint must observe the VPN egress while a
   non-Russian endpoint observes the ISP egress.
4. `Свои правила`: verify one domain and one CIDR in each direction.
5. Disconnect and reconnect after each saved preset. If geo download or
   validation fails, connection must be blocked before either core starts; the
   last verified cache remains intact.

If any step fails, do not delete the profile store. Copy the redacted Journal
and record the selected core, node, routing preset and exact time.

Automated JVM, package, bootstrap and public-redownload verification are release
gates. Elevated real-PC traffic and route ownership remain the final user-run
acceptance gate.
