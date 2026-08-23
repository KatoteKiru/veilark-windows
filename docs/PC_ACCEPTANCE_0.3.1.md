# Veilark Windows 0.3.1 — PC acceptance

Use the EXE as an in-place update of the existing Veilark installation. Do not
uninstall Veilark and do not delete `%LOCALAPPDATA%\Veilark`.

## Upgrade checks

1. In Veilark 0.3.0 open Updates, check for 0.3.1, download it, press Install,
   and accept the Windows UAC prompt. The updater must close 0.3.0 itself.
2. Windows Apps shows one Veilark entry with version `0.3.1`; the install path
   remains `C:\Program Files\Veilark`.
3. Launch Veilark. It opens without `could not initialise` or a JNA error.
4. Existing user subscriptions, selected engines and selected nodes remain.
5. `Veilark Trust` remains protected and contains both embedded endpoints.

## Tunnel checks

1. Select the Netherlands TrustTunnel endpoint and connect. The app must not
   show «Подключено» until the core confirms the connection; open a website and
   confirm that received/sent byte counters increase.
2. Disconnect, select the Frankfurt TrustTunnel endpoint, and repeat.
3. Start another connection and immediately press `Остановить подключение`.
   The state must return to «Отключено», and no TrustTunnel process may remain.
4. Repeat connect, traffic and disconnect through one sing-box node.

## Subscription and update checks

1. On the main screen, open the subscription selector and nested node selector
   for each engine; confirm every expected inbound is visible.
2. Run `Пинг` after switching nodes; the old node's value must not remain.
3. Refresh an HTTPS subscription and confirm its ID and active selection stay.
4. Delete a user subscription, press `Отменить`, and confirm unrelated settings
   did not roll back. `Veilark Trust` must not offer deletion.
5. Open Updates and confirm the full 0.3.1 notes explain the tunnel, storage,
   subscription-race and process-tree fixes.

The elevated recovery OTA was published on 2026-08-23 to unblock the broken
0.3.0 updater. Real-PC acceptance remains open until all checks above pass. On
failure, preserve the profile store and record the failed step, Windows scaling,
selected engine, selected node and redacted Veilark log; never include
credentials or raw links.
