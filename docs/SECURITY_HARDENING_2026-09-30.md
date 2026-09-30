# Windows hardening — 2026-09-30

Branch `claude/happy-ptolemy-mpow5b`, based on `codex/windows-fluent-stability`
(0.3.21 is live on OTA). **No version bump, no OTA publication.** The Ed25519
public key, manifest URL/host/port, manifest field names, canonical signed
payloads, MSI UpgradeCode, install paths and `perUserInstall` are unchanged.
Installed 0.3.x clients verify manifests exactly as before.

## OTA publisher (`scripts/publish_ota.py`)

| Issue | Fix |
| --- | --- |
| Notes limit counted in Python code points, while `UpdateClient` truncates with `take(4000)` in UTF-16 units **before** verifying `notesSignature`. Notes with astral characters (emoji) could be 2 001–4 000 code points but more than 4 000 units, so every client rejected the manifest. | `validate_notes` measures UTF-16 units and rejects unpaired surrogates (Kotlin would encode them as `?`). `assert_client_verifiable` replays the client normalisation of each signed field (trim `versionName`/`installerUrl`, uppercase `sha256`, truncate notes) and refuses to sign if anything would change. `verify_client_contract` fails if `UpdateClient.MAX_NOTES_LENGTH` or its `take()` normalisation drifts. |
| `--version-code` was not tied to `--version-name`. | Must equal `major*10000 + minor*100 + patch` (mirror of `ota-version.ps1`). |
| The installer's own version was not checked. | The wrapper's `VS_FIXEDFILEINFO` (only inside the PE resource directory, so the embedded jpackage payload is ignored) must equal `<version>.0`. `--allow-installer-without-file-version` exists for exceptional cases. |
| Tests were not run in CI. | CI installs `scripts/requirements-ota.txt` and runs `test_publish_ota.py`; it also parses the version of a CI-built wrapper. |

A Kotlin regression (`notes limit is measured in UTF-16 units…`) documents the
client side of the contract.

## Elevated OTA bootstrap (`installer-bootstrap/VeilarkOtaBootstrap.cs`)

Before: the `requireAdministrator` wrapper, located in the user-writable
`%LOCALAPPDATA%\Veilark\updates`, extracted the payload next to itself, closed
it, renamed it and then started it elevated. An unelevated process of the same
user could replace the file between the hash check and `Process.Start` (EoP).

After:

1. A new `%SystemRoot%\Temp\Veilark-ota-<guid>` directory is created with a
   protected DACL: Administrators and SYSTEM full control, `OWNER RIGHTS`
   read/execute (removes the owner's implicit `WRITE_DAC`), no inheritance.
   Users cannot delete or rename others' entries in `%SystemRoot%\Temp`. The
   ACL and reparse-point state are verified after creation.
2. The payload is written with `FileShare.None`, then reopened with
   `FileAccess.Read, FileShare.Read`, which denies every later writer,
   deleter and rename, and hashed again through that handle.
3. The handle stays open while the installer runs; the directory is removed
   afterwards.

`--bootstrap-verify-only` exercises the same path, asserts that a write open
fails and prints the hash. CI builds a verification wrapper around a random
payload and runs it. A non-elevated verification run (developer shell) falls
back to a user temp directory because it never launches the payload.

## Native core configurations

sing-box and TrustTunnel configs contain credentials and are read by elevated
cores from `%LOCALAPPDATA%\Veilark\runtime`. `LockedConfigFile` now writes each
config once with `CREATE_NEW` under a random name and keeps the handle open
with `NOSHARE_WRITE`/`NOSHARE_DELETE` (`com.sun.nio.file.ExtendedOpenOption`,
module `jdk.unsupported`, added to the runtime image and checked by
`verify-packaged-resources.ps1`) until the core reports readiness. Cores read
with shared read/write access and are unaffected. TrustTunnel wizard output is
read back immediately, validated and routed in memory, then written once to a
locked file, so the client reads exactly what was validated.

Residual (documented, not fixed): the wizard's own `--settings` output exists
briefly before it is read back; and `profiles.dat` is DPAPI current-user data,
so a same-user process can already change profiles. Closing that requires the
planned least-privilege Windows service.

## Core lifetime

Cores are assigned to a Windows Job object with
`JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE` (`CoreProcessJob`), so an elevated core no
longer survives a crash or forced exit of Veilark. Best-effort; failures are
logged and `CoreProcessJanitor` still sweeps orphans before the next connect.

Graceful stop (Ctrl+C / close signal before `TerminateProcess`) was **not**
implemented: Java cannot start a child in its own console process group, and
`GenerateConsoleCtrlEvent` would target Veilark's own console group too. Adapter
cleanup after a forced stop is already handled by `OwnedWinTunCleanup`. A
reliable graceful stop needs a small native launcher or the planned service.

## Updates, localisation, Windows 11

- `DesktopUpdateController`: coroutine cancellation is rethrown; the user's
  Cancel (download token) is distinguished from caller cancellation; the token
  is `@Volatile`; a disconnect timeout during install is a failure. The first
  background check runs 90 s after start, also on the profile-store failure
  screen, which can now download and install updates without writing the store.
- Stable codes replace localized text matching: `VpnStatusCode` (session,
  `VpnStartException`, `EngineHealth.Unhealthy`, curl `ProbeFailure` codes) and
  `UpdateErrorCode`/`UpdateException`. `StatusText.kt` maps codes to RU/EN.
- DWM: dark caption follows the theme (attribute 20, fallback 19); on build
  ≥ 22621 Mica (38) and rounded corners (33). No-op elsewhere.
- Window size/position/maximized state is remembered; single tray click opens
  the window; shortcuts Ctrl+1…6, Ctrl+Shift+C, Ctrl+W.
- Removed dead Russian-only composables and their helpers.

## Validation

- `python3 scripts/test_publish_ota.py`: 20 tests.
- Linux `./gradlew test :shared:jvmTest --continue`: all new tests pass; 11
  pre-existing helper tests fail on Linux only (Windows paths/registry), same
  as on the base commit. Windows CI runs everything natively.
