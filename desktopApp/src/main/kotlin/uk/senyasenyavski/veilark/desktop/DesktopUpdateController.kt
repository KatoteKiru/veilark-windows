package uk.senyasenyavski.veilark.desktop

import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.senyasenyavski.veilark.helper.ScheduledWindowsUpdate
import uk.senyasenyavski.veilark.helper.UpdateInstallOutcome
import uk.senyasenyavski.veilark.helper.VeilarkPaths
import uk.senyasenyavski.veilark.helper.WindowsUpdateInstaller
import uk.senyasenyavski.veilark.helper.SeamlessUpdateCoordinator
import uk.senyasenyavski.veilark.update.AppUpdate
import uk.senyasenyavski.veilark.update.UpdateClient
import uk.senyasenyavski.veilark.update.UpdateDownloadCancellation

sealed interface DesktopUpdateState {
  data object Checking : DesktopUpdateState
  data object Current : DesktopUpdateState
  data class Available(val update: AppUpdate) : DesktopUpdateState
  data class Downloading(val update: AppUpdate, val progress: Float) : DesktopUpdateState
  data class Ready(val update: AppUpdate, val installer: Path) : DesktopUpdateState
  data class Installing(val update: AppUpdate) : DesktopUpdateState
  data class Failed(val message: String, val update: AppUpdate? = null) : DesktopUpdateState
}

/** Application-scoped OTA state. It survives navigation and never stops the VPN during download. */
class DesktopUpdateController(
  private val client: UpdateClient = UpdateClient(),
  private val installer: WindowsUpdateInstaller = WindowsUpdateInstaller(client),
  private val seamless: SeamlessUpdateCoordinator = SeamlessUpdateCoordinator(installer),
  private val directory: Path = VeilarkPaths.dataDirectory.resolve("updates"),
) {
  private val operation = Mutex()
  private val mutableState = MutableStateFlow<DesktopUpdateState>(DesktopUpdateState.Checking)
  private var cancellation: UpdateDownloadCancellation? = null

  val state: StateFlow<DesktopUpdateState> = mutableState.asStateFlow()

  suspend fun initialize(): UpdateInstallOutcome? {
    val outcome = withContext(Dispatchers.IO) {
      installer.cleanupStaleHelpers()
      client.cleanupDownloads(directory)
      installer.readLastOutcome()?.also { installer.clearLastOutcome() }
    }
    check()
    return outcome
  }

  suspend fun check(background: Boolean = false) = operation.withLock {
    val previous = mutableState.value
    if (previous is DesktopUpdateState.Downloading || previous is DesktopUpdateState.Ready ||
      previous is DesktopUpdateState.Installing) return@withLock
    if (!background) mutableState.value = DesktopUpdateState.Checking
    runCatching { withContext(Dispatchers.IO) { client.check() } }
      .onSuccess { update ->
        mutableState.value = update?.let(DesktopUpdateState::Available)
          ?: DesktopUpdateState.Current
      }
      .onFailure { error ->
        if (error is CancellationException) throw error
        if (background) return@onFailure
        mutableState.value = DesktopUpdateState.Failed(
          error.message ?: "Не удалось проверить обновления",
        )
      }
  }

  suspend fun download(update: AppUpdate) = operation.withLock {
    if (mutableState.value is DesktopUpdateState.Downloading) return@withLock
    val token = UpdateDownloadCancellation()
    cancellation = token
    mutableState.value = DesktopUpdateState.Downloading(update, 0f)
    try {
      val path = withContext(Dispatchers.IO) {
        client.download(update, directory, token) { downloaded, total ->
          mutableState.value = DesktopUpdateState.Downloading(
            update,
            if (total > 0L) downloaded.toFloat() / total else 0f,
          )
        }
      }
      mutableState.value = DesktopUpdateState.Ready(update, path)
    } catch (_: CancellationException) {
      mutableState.value = DesktopUpdateState.Available(update)
    } catch (error: Throwable) {
      mutableState.value = DesktopUpdateState.Failed(
        error.message ?: "Не удалось загрузить обновление",
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
    runCatching {
      withContext(Dispatchers.IO) {
        seamless.prepareAndSchedule(ready.update, ready.installer, disconnectVpn)
      }
    }.onFailure { error ->
      mutableState.value = DesktopUpdateState.Failed(
        error.message ?: "Не удалось подготовить обновление",
        ready.update,
      )
    }
  }
}
