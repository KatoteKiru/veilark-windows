# Veilark Windows 0.3.0 — PC acceptance

> Withdrawn: this build produced a live TrustTunnel regression and is no longer
> published by the production OTA channel. Use the 0.3.1 recovery checklist.

Use the EXE for the same path that OTA will use. This is an in-place upgrade of
the existing Veilark installation, not a second application.

## Before the upgrade

1. In Veilark 0.2.5, disconnect the VPN and exit Veilark from the tray.
2. Keep the current `%LOCALAPPDATA%\Veilark` data directory; do not delete it.
3. Run `Veilark-0.3.0.exe` and accept the Windows elevation prompt.

## Upgrade checks

1. Windows Apps shows one Veilark entry with version `0.3.0`.
2. The install location remains `C:\Program Files\Veilark`.
3. Run:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\verify-installed.ps1 -ExpectedVersion 0.3.0
   ```

4. Launch Veilark. It must open without `could not initialise` or a JNA error.
5. Existing user subscriptions and their selected nodes remain present.
6. `Veilark Trust` remains present as a protected built-in subscription with
   both embedded endpoints.

## Functional checks

1. On the main screen, switch between sing-box and TrustTunnel.
2. For each engine, open the subscription selector and then the nested node
   selector. Confirm all expected nodes are visible; test search on a large list.
3. Start a connection and press `Остановить подключение` while it is still in
   Preparing/Connecting. Repeat from the tray menu.
4. Connect through one TrustTunnel node and one sing-box node. For each:
   confirm the adapter name, session time, and increasing traffic counters;
   open a website; then disconnect.
5. Run `Пинг` for both engines.
6. Refresh an HTTPS subscription. Confirm it is updated in place and is not
   duplicated.
7. Delete a user subscription, use `Отменить`, and confirm other selections and
   routing settings did not roll back. Confirm Veilark Trust has no delete action.
8. Open Updates and confirm the installed version and full 0.3.0 changelog are
   shown. Do not publish this withdrawn build.

## Failure evidence

If a check fails, do not delete the profile store. Record the step, screenshot,
Windows version/scaling, selected engine, and the redacted Veilark log. Never
include subscription URLs, credentials, or raw profile payloads.
