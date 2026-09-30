package uk.senyasenyavski.veilark.desktop

import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.senyasenyavski.veilark.helper.ScheduledWindowsUpdate
import uk.senyasenyavski.veilark.helper.SeamlessUpdateCoordinator
import uk.senyasenyavski.veilark.helper.UpdateInstallOutcome
import uk.senyasenyavski.veilark.helper.VeilarkPaths
import uk.senyasenyavski.veilark.helper.WindowsUpdateInstaller
import uk.senyasenyavski.veilark.update.AppUpdate
import uk.senyasenyavski.veilark.update.UpdateClient
import uk.senyasenyavski.veilark.update.UpdateDownloadCancellation
import uk.senyasenyavski.veilark.update.UpdateError
import uk.senyasenyavski.veilark.update.UpdateErrorCode
import uk.senyasenyavski.veilark.update.toUpdateError

sealed interface DesktopUpdateState {
  data object Checking : DesktopUpdateState
  data object Current : DesktopUpdateState
  data class Available(val update: AppUpdate) : DesktopUpdateState
  data class Downloading(val update: AppUpdate, val progress: Float) : DesktopUpdateState
  data class Ready(val update: AppUpdate, val installer: Path) : DesktopUpdateState
  data class Installing(val update: AppUpdate) : DesktopUpdateState

  /** [error] is a stable code; the UI localizes it via [updateErrorText]. */
  data class Failed(val error: UpdateError, val update: AppUpdate? = null) : DesktopUpdateState
}

/** Application-scoped OTA state. It survives navigation and never stops the VPN during download. */
class DesktopUpdateController internal constructor(
  private val checkForUpdate: () -> AppUpdate?,
  private val downloadUpdate: (AppUpdate, UpdateDownloadCancellation, (Long, Long) -> Unit) -> Path,
  private val prepareAndSchedule: suspend (AppUpdate, Path, suspend () -> Unit) -> ScheduledWindowsUpdate,
  private val housekeeping: () -> UpdateInstallOutcome?,
) {
  constructor(
    client: UpdateClient = UpdateClient(),
    installer: WindowsUpdateInstaller = WindowsUpdateInstaller(client),
    seamless: SeamlessUpdateCoordinator = SeamlessUpdateCoordinator(installer),
    directory: Path = VeilarkPaths.dataDirectory.resolve("updates"),
  ) : this(
    checkForUpdate = client::check,
    downloadUpdate = { update, token, progress -> client.download(update, directory, token, progress) },
    prepareAndSchedule = { update, installerPath, disconnect ->
      seamless.prepareAndSchedule(update, installerPath, disconnect)
    },
    housekeeping = {
      installer.cleanupStaleHelpers()
      client.cleanupDownloads(directory)
      installer.readLastOutcome()?.also { installer.clearLastOutcome() }
    },
  )

  private val operation = Mutex()
  private val mutableState = MutableStateFlow<DesktopUpdateState>(DesktopUpdateState.Checking)

  /** Written by the download coroutine, read by the UI thread's cancel action. */
  @Volatile
  private var cancellation: UpdateDownloadCancellation? = null

  val state: StateFlow<DesktopUpdateState> = mutableState.asStateFlow()

  suspend fun initialize(): UpdateInstallOutcome? {
    val outcome = withContext(Dispatchers.IO) { housekeeping() }
    check()
    return outcome
  }

  /**
   * Background schedule: the first silent check runs shortly after startup
   * (so a long-running tray session and the profile-store failure screen also
   * learn about releases), then every [intervalMillis].
   */
  suspend fun runBackgroundChecks(
    firstDelayMillis: Long = FIRST_BACKGROUND_CHECK_DELAY_MS,
    intervalMillis: Long = UpdateNoticePolicy.INTERVAL_MS,
  ) {
    delay(firstDelayMillis)
    while (currentCoroutineContext().isActive) {
      check(background = true)
      delay(intervalMillis)
    }
  }

  suspend fun check(background: Boolean = false) = operation.withLock {
    val previous = mutableState.value
    if (previous is DesktopUpdateState.Downloading || previous is DesktopUpdateState.Ready ||
      previous is DesktopUpdateState.Installing) return@withLock
    if (!background) mutableState.value = DesktopUpdateState.Checking
    try {
      val update = withContext(Dispatchers.IO) { checkForUpdate() }
      mutableState.value = update?.let(DesktopUpdateState::Available) ?: DesktopUpdateState.Current
    } catch (cancelled: CancellationException) {
      // Cancellation of the calling scope must propagate; do not leave a
      // visible "Checking" state behind.
      mutableState.value = previous
      throw cancelled
    } catch (error: Throwable) {
      if (background) return@withLock
      mutableState.value = DesktopUpdateState.Failed(error.toUpdateError(UpdateErrorCode.CheckFailed))
    }
  }

  suspend fun download(update: AppUpdate) = operation.withLock {
    if (mutableState.value is DesktopUpdateState.Downloading) return@withLock
    val token = UpdateDownloadCancellation()
    cancellation = token
    mutableState.value = DesktopUpdateState.Downloading(update, 0f)
    try {
      val path = withContext(Dispatchers.IO) {
        downloadUpdate(update, token) { downloaded, total ->
          mutableState.value = DesktopUpdateState.Downloading(
            update,
            if (total > 0L) downloaded.toFloat() / total else 0f,
          )
        }
      }
      mutableState.value = DesktopUpdateState.Ready(update, path)
    } catch (cancelled: CancellationException) {
      mutableState.value = DesktopUpdateState.Available(update)
      // The user's Cancel button is an expected outcome. Anything else is
      // cancellation of the calling coroutine and must reach its parent.
      if (!token.isCancelled()) throw cancelled
    } catch (error: Throwable) {
      mutableState.value = DesktopUpdateState.Failed(
        error.toUpdateError(UpdateErrorCode.DownloadFailed),
        update,
      )
    } finally {
      cancellation = null
    }
  }

  fun cancelDownload() {
    cancellation?.cancel()
  }

  suspend fun install(
    ready: DesktopUpdateState.Ready,
    disconnectVpn: suspend () -> Unit,
  ): Result<ScheduledWindowsUpdate> = operation.withLock {
    mutableState.value = DesktopUpdateState.Installing(ready.update)
    try {
      Result.success(withContext(Dispatchers.IO) {
        prepareAndSchedule(ready.update, ready.installer, disconnectVpn)
      })
    } catch (cancelled: CancellationException) {
      if (!currentCoroutineContext().isActive) {
        // The caller was cancelled before the helper launched; the verified
        // download stays installable.
        mutableState.value = ready
        throw cancelled
      }
      // A disconnect timeout inside the coordinator is a failure, not
      // cancellation of the caller.
      mutableState.value = DesktopUpdateState.Failed(UpdateError(UpdateErrorCode.InstallFailed), ready.update)
      Result.failure(cancelled)
    } catch (error: Throwable) {
      mutableState.value = DesktopUpdateState.Failed(
        error.toUpdateError(UpdateErrorCode.InstallFailed),
        ready.update,
      )
      Result.failure(error)
    }
  }

  companion object {
    const val FIRST_BACKGROUND_CHECK_DELAY_MS = 90_000L
  }
}
