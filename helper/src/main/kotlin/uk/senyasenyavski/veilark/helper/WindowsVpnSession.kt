package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.SessionState
import uk.senyasenyavski.veilark.model.TrafficSnapshot
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.model.VpnPhase
import uk.senyasenyavski.veilark.model.requiresStopRetry
import uk.senyasenyavski.veilark.session.VpnSession

class WindowsVpnSession(
  controllers: List<EngineController> = listOf(
    SingBoxProcessController(),
    TrustTunnelProcessController(),
  ),
  private val statisticsIntervalMillis: Long = 2_000,
  private val healthChecksEveryTicks: Int = 10,
  private val disconnectJoinTimeoutMillis: Long = 10_000,
  private val logger: (String) -> Unit = SafeLog::write,
  private val preConnectCheck: () -> String? = { null },
) : VpnSession {
  private val controllers = controllers.associateBy(EngineController::engine)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val mutex = Mutex()
  private val disconnectMutex = Mutex()
  private val mutableState = MutableStateFlow(SessionState())

  private var activeController: EngineController? = null
  private var activeGeoLease: AutoCloseable? = null
  private var connectJob: Job? = null
  private var pendingTeardownJob: Job? = null
  private var monitorJob: Job? = null

  init {
    require(statisticsIntervalMillis > 0) { "Интервал статистики должен быть положительным" }
    require(healthChecksEveryTicks > 0) { "Интервал проверки соединения должен быть положительным" }
    require(disconnectJoinTimeoutMillis > 0) { "Тайм-аут остановки должен быть положительным" }
  }

  override val state: StateFlow<SessionState> = mutableState

  override suspend fun connect(profile: Profile) {
    val job = mutex.withLock {
      // disconnect() clears connectJob before waiting for native teardown so it
      // can cancel the attempt without deadlocking this mutex. The explicit
      // Stopping/owner gates prevent a second click from starting the same
      // controller while that teardown is still in progress.
      if (
        connectJob?.isActive == true ||
        pendingTeardownJob != null ||
        activeController != null ||
        mutableState.value.phase is VpnPhase.Stopping ||
        mutableState.value.phase.requiresStopRetry
      ) return
      val controller = controllers[profile.engine] ?: run {
        publish(
          SessionState(
            engine = profile.engine,
            phase = VpnPhase.Error(
              message = "Ядро ${profile.engine.displayName()} не установлено",
              code = "ENGINE_NOT_FOUND",
            ),
            profile = profile,
          ),
        )
        return
      }
      publish(SessionState(profile.engine, VpnPhase.Preparing, profile))
      scope.launch { runConnect(controller, profile) }.also { launched ->
        connectJob = launched
        launched.invokeOnCompletion {
          scope.launch {
            mutex.withLock {
              if (connectJob === launched) connectJob = null
            }
          }
        }
      }
    }
    // Awaiting the job keeps the suspending contract while leaving the session
    // free to accept a disconnect that cancels this very attempt.
    job.join()
  }

  override suspend fun disconnect() = disconnectMutex.withLock {
    val (pending, controller) = mutex.withLock {
      val pending = connectJob ?: pendingTeardownJob
      connectJob = null
      val controller = activeController
      monitorJob?.cancel()
      monitorJob = null
      if (pending == null && controller == null) {
        publish(mutableState.value.copy(phase = VpnPhase.Idle, traffic = null))
      } else {
        // Give immediate feedback and make a second click harmless before any
        // blocking probe or native process shutdown has completed.
        publish(mutableState.value.copy(phase = VpnPhase.Stopping))
      }
      pending to controller
    }
    pending?.cancel()
    val pendingFinished = pending == null || withContext(NonCancellable) {
      withTimeoutOrNull(disconnectJoinTimeoutMillis) {
        pending.join()
        true
      } == true
    }
    val stopped = controller?.let { stopOwnedController(it, retainForPendingStartup = !pendingFinished) }
      ?: Result.success(Unit)
    val terminated = stopped.mapCatching {
      check(pendingFinished) {
        "Попытка подключения не остановилась за $disconnectJoinTimeoutMillis мс"
      }
      check(controller?.isAlive() != true) { "VPN-ядро всё ещё работает" }
    }
    mutex.withLock {
      pendingTeardownJob = if (pendingFinished) null else pending
      if (terminated.isSuccess) {
        publish(mutableState.value.copy(phase = VpnPhase.Idle, traffic = null))
      } else {
        logger("Ошибка остановки: ${terminated.exceptionOrNull()?.message}")
        publish(
          mutableState.value.copy(
            phase = VpnPhase.Error(
              message = "Не удалось остановить VPN-ядро",
              code = "STOP_FAILED",
              stopRequired = true,
            ),
          ),
        )
      }
    }
    terminated.getOrThrow()
  }

  private suspend fun runConnect(controller: EngineController, profile: Profile) {
    try {
      // ActiveTunnelConflict reads the native Windows interface table. Keep it
      // on the session's IO scope rather than blocking the caller/UI thread.
      preConnectCheck()?.let { message ->
        currentCoroutineContext().ensureActive()
        mutex.withLock {
          if (mutableState.value.phase is VpnPhase.Stopping) return@withLock
          publish(
            SessionState(
              engine = profile.engine,
              phase = VpnPhase.Error(message, "COMPETING_TUNNEL"),
              profile = profile,
            ),
          )
        }
        return
      }
      currentCoroutineContext().ensureActive()
      mutex.withLock {
        if (mutableState.value.phase is VpnPhase.Stopping) return
        activeController = controller
        activeGeoLease = GeoGenerationLeases.acquire(profile.geoRoutingAssets)
        publish(mutableState.value.copy(phase = VpnPhase.Connecting))
      }
      val health = withTimeout(CONNECT_TIMEOUT_MILLIS) { controller.start(profile) }
      mutex.withLock {
        if (activeController !== controller) return
        publish(
          mutableState.value.copy(
            phase = when (health) {
              EngineHealth.Healthy -> VpnPhase.Connected(System.currentTimeMillis())
              is EngineHealth.Unhealthy -> VpnPhase.Degraded(health.message)
            },
            traffic = safeStatistics(controller),
          ),
        )
        startMonitor(controller)
      }
    } catch (cancellation: CancellationException) {
      // A cancelled attempt is torn down by whoever cancelled it, except for a
      // connect timeout, which cancels only this coroutine.
      if (cancellation is TimeoutCancellationException) {
        failConnect(controller, "Подключение не завершилось за 90 секунд", "CONNECT_TIMEOUT")
      }
      throw cancellation
    } catch (error: Throwable) {
      logger("Ошибка подключения: ${error.message}")
      failConnect(controller, humanMessage(error), errorCode(error))
    }
  }

  private suspend fun failConnect(
    controller: EngineController,
    message: String,
    code: String,
  ) = withContext(NonCancellable) {
    stopOwnedController(controller)
      .onFailure { logger("Ошибка очистки после неудачного запуска: ${it.message}") }
    mutex.withLock {
      // A user-initiated disconnect owns the visible Stopping -> Idle
      // transition. Do not replace it with a transient startup error while the
      // same attempt is being cancelled.
      if (mutableState.value.phase is VpnPhase.Stopping) return@withLock
      publish(
        mutableState.value.copy(
          phase = VpnPhase.Error(message, code,
            stopRequired = activeController != null || pendingTeardownJob != null),
          traffic = null,
        ),
      )
    }
  }

  /**
   * Atomically claims teardown for one connection attempt. Startup failure,
   * timeout and the Stop button can race, but exactly one of them may call the
   * native controller's blocking [EngineController.stop].
   */
  private suspend fun stopOwnedController(
    controller: EngineController,
    retainForPendingStartup: Boolean = false,
  ): Result<Unit> {
    val claimed = mutex.withLock {
      if (activeController !== controller) {
        false
      } else {
        activeController = null
        true
      }
    }
    if (!claimed) return Result.success(Unit)

    val stopped = withContext(NonCancellable) {
      runCatching {
        controller.stop()
        check(!controller.isAlive()) { "VPN-ядро не завершилось" }
      }
    }
    if (!controller.isAlive() && !retainForPendingStartup) mutex.withLock {
      activeGeoLease?.close()
      activeGeoLease = null
    }
    // Retain a live failed process as the active controller so a subsequent
    // Stop can retry instead of reporting a false Idle state.
    if (retainForPendingStartup || (stopped.isFailure && controller.isAlive())) {
      mutex.withLock {
        if (activeController == null) activeController = controller
      }
    }
    return stopped
  }

  /**
   * Publishes byte counters on a short interval and re-checks connectivity on a
   * longer one, so the UI stays live without paying for a network probe every
   * tick.
   */
  private fun startMonitor(controller: EngineController) {
    monitorJob?.cancel()
    monitorJob = scope.launch {
      var tick = 0
      while (true) {
        delay(statisticsIntervalMillis)
        tick += 1
        if (!controller.isAlive()) {
          reportCoreExit(controller)
          return@launch
        }
        val statistics = safeStatistics(controller)
        val health = if (tick % healthChecksEveryTicks == 0) safeHealth(controller) else null
        mutex.withLock {
          if (activeController !== controller) return@withLock
          val phase = mutableState.value.phase
          if (phase !is VpnPhase.Connected && phase !is VpnPhase.Degraded) return@withLock
          publish(
            mutableState.value.copy(
              phase = when (health) {
                null -> phase
                EngineHealth.Healthy -> if (phase is VpnPhase.Connected) {
                  phase
                } else {
                  VpnPhase.Connected(System.currentTimeMillis())
                }
                is EngineHealth.Unhealthy -> VpnPhase.Degraded(health.message)
              },
              traffic = statistics,
            ),
          )
        }
      }
    }
  }

  private suspend fun reportCoreExit(controller: EngineController) = mutex.withLock {
    if (activeController !== controller) return@withLock
    val phase = mutableState.value.phase
    if (phase !is VpnPhase.Connected && phase !is VpnPhase.Degraded) return@withLock
    activeController = null
    activeGeoLease?.close()
    activeGeoLease = null
    logger("${controller.engine.displayName()} неожиданно завершился")
    publish(
      mutableState.value.copy(
        phase = VpnPhase.Error(
          message = "${controller.engine.displayName()} неожиданно завершился. " +
            "Проверьте журнал и повторите подключение.",
          code = "CORE_EXITED",
        ),
        traffic = null,
      ),
    )
  }

  private fun safeStatistics(controller: EngineController): TrafficSnapshot? = try {
    controller.statistics()?.toSnapshot()
  } catch (_: Exception) {
    logger("Не удалось прочитать счётчики VPN-туннеля")
    null
  }

  private suspend fun safeHealth(controller: EngineController): EngineHealth = try {
    controller.health()
  } catch (cancellation: CancellationException) {
    throw cancellation
  } catch (_: Exception) {
    logger("Не удалось выполнить проверку доступности VPN-туннеля")
    EngineHealth.Unhealthy("проверка доступности туннеля не выполнена")
  }

  private fun publish(state: SessionState) {
    mutableState.value = state
  }

  private fun TunnelStatistics.toSnapshot() = TrafficSnapshot(
    adapter = alias,
    bytesIn = bytesIn,
    bytesOut = bytesOut,
  )

  private fun humanMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    return when {
      message.contains("already exists", ignoreCase = true) ->
        "Предыдущий сетевой адаптер не был освобождён. Запустите Veilark от имени " +
          "администратора, чтобы Veilark смог его удалить."
      message.contains("Access is denied", ignoreCase = true) ||
        message.contains("permission", ignoreCase = true) ->
        "Windows не разрешила создать туннель. Запустите Veilark от имени администратора."
      message.isNotBlank() -> message
      else -> "Подключение не выполнено"
    }
  }

  private fun errorCode(error: Throwable): String {
    val message = error.message.orEmpty()
    return when {
      message.contains("Не найден") -> "CORE_NOT_FOUND"
      message.contains("Конфигурация") -> "CONFIG_INVALID"
      message.contains("already exists", ignoreCase = true) -> "TUN_NAME_TAKEN"
      message.contains("Windows не подняла туннель") -> "TUN_NOT_CREATED"
      else -> "CORE_START_FAILED"
    }
  }

  private fun VpnEngine.displayName(): String = when (this) {
    VpnEngine.SingBox -> "sing-box"
    VpnEngine.TrustTunnel -> "TrustTunnel"
  }

  private companion object {
    const val CONNECT_TIMEOUT_MILLIS = 90_000L
  }
}
