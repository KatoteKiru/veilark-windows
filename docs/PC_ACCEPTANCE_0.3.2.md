# Veilark Windows 0.3.2 — PC acceptance

Install 0.3.2 through Updates over the existing Veilark. Do not uninstall it
and do not delete `%LOCALAPPDATA%\Veilark`.

## Upgrade and UI

1. From 0.3.0 or 0.3.1 check for the update, download it, press Install and
   accept the Windows UAC prompt. Veilark must close and reopen itself.
2. Windows Apps must contain one Veilark entry, version `0.3.2`, in the same
   installation path. Existing user subscriptions, selected engines and nodes
   must remain; `Veilark Trust` remains protected.
3. On Home open `Сервер` under both engines. Every subscription and every nested
   node must be visible in the one searchable list. Repeated opening, searching,
   selecting and closing must not show a Windows error or close the application.
4. Refresh, ping and delete a user subscription; Undo must restore only that
   subscription. The built-in subscription cannot be deleted.

## Tunnel lifecycle

1. Connect to each embedded TrustTunnel endpoint. `Подключено` must appear only
   after `VPN_SS_CONNECTED`; browse through the tunnel and confirm byte counters
   grow.
2. Press `Остановить подключение` during preparation. It must return to
   `Отключено` and leave no TrustTunnel process or adapter behind.
3. Repeat connect, traffic, stop-during-connect, reconnect and disconnect with a
   sing-box node.
4. If any step fails, keep the profile store intact and save the redacted log;
   do not include raw subscription links or credentials.

Automated JVM, package and public-download verification are release gates.
Elevated real-PC traffic acceptance remains the final user-run gate.
