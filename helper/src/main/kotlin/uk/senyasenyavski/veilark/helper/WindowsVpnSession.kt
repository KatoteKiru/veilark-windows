package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
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
  private var connectJob: Job? = null
  private var monitorJob: Job? = null

  override val state: StateFlow<SessionState> = mutableState

  override suspend fun connect(profile: Profile) {
    val job = mutex.withLock {
      if (connectJob?.isActive == true || activeController?.isAlive() == true) return
      preConnectCheck()?.let { message ->
        publish(
          SessionState(
            engine = profile.engine,
            phase = VpnPhase.Error(message, "COMPETING_TUNNEL"),
            profile = profile,
          ),
        )
        return
      }
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
      val pending = connectJob.also { connectJob = null }
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
    val stopped = controller?.let { stopOwnedController(it) } ?: Result.success(Unit)
    val terminated = stopped.mapCatching {
      check(pendingFinished) { "Попытка подключения не остановилась за 3 секунды" }
      check(controller?.isAlive() != true) { "VPN-ядро всё ещё работает" }
    }
    mutex.withLock {
      if (terminated.isSuccess) {
        publish(mutableState.value.copy(phase = VpnPhase.Idle, traffic = null))
      } else {
        logger("Ошибка остановки: ${terminated.exceptionOrNull()?.message}")
        publish(
          mutableState.value.copy(
            phase = VpnPhase.Error(
              message = "Не удалось остановить VPN-ядро",
              code = "STOP_FAILED",
            ),
          ),
        )
      }
    }
    terminated.getOrThrow()
  }

  private suspend fun runConnect(controller: EngineController, profile: Profile) {
    try {
      mutex.withLock {
        activeController = controller
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
            traffic = controller.statistics()?.toSnapshot(),
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
          phase = VpnPhase.Error(message, code),
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
  private suspend fun stopOwnedController(controller: EngineController): Result<Unit> {
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
    // Retain a live failed process as the active controller so a subsequent
    // Stop can retry instead of reporting a false Idle state.
    if (stopped.isFailure && controller.isAlive()) {
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
        val statistics = controller.statistics()?.toSnapshot()
        val health = if (tick % healthChecksEveryTicks == 0) controller.health() else null
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
