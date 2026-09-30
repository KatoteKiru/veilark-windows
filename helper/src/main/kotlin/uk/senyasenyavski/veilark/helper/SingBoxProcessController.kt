package uk.senyasenyavski.veilark.helper

import uk.senyasenyavski.veilark.model.VpnStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.profile.ProfileConfiguration
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class SingBoxProcessController(
  private val executableOverride: Path? = null,
  private val runtimeDirectory: Path = VeilarkPaths.runtimeDirectory,
) : EngineController {
  override val engine: VpnEngine = VpnEngine.SingBox
  private var process: Process? = null
  private val stopMutex = Mutex()

  @Volatile
  private var readyTunnel: ReadyTunnel? = null

  override suspend fun start(profile: Profile): EngineHealth = withContext(Dispatchers.IO) {
    require(profile.engine == engine) { "Профиль предназначен для другого ядра" }
    synchronized(this@SingBoxProcessController) {
      check(process?.isAlive != true) { "VPN уже запущен" }
    }
    val executable = resolveExecutable()
    // Readiness depends on the INFO startup marker. Imported/stored profiles
    // normally use warn logging and must not suppress or redirect that marker.
    // Legacy fixed names from releases before the locked per-run config.
    Files.deleteIfExists(runtimeDirectory.resolve("active.json"))
    Files.deleteIfExists(runtimeDirectory.resolve("active.json.tmp"))
    val config = LockedConfigFile.create(
      directory = runtimeDirectory,
      prefix = "active-",
      suffix = ".json",
      content = SingBoxRuntimeConfiguration.forStartup(profile.config),
    )
    try {
      checkConfig(executable, config.path)
      // sing-box cannot reuse a connection name still held by an abandoned core or
      // by an adapter that a previous, force-terminated run left behind.
      CoreProcessJanitor.terminateOrphans(executable)
      OwnedWinTunCleanup.recoverStoppedAdapters()

      val started = ProcessBuilder(
        executable.toString(),
        "run",
        "-c",
        config.path.toString(),
      )
        .directory(executable.parent.toFile())
        .redirectErrorStream(true)
        .start()
      synchronized(this@SingBoxProcessController) { process = started }
      // The core must not outlive Veilark (crash or forced exit).
      CoreProcessJob.assignToShared(started, "sing-box")

      val logPump = CoreLogPump(
        process = started,
        engineName = "sing-box",
        readyMarkers = listOf("sing-box started"),
        fatalMarkers = listOf("FATAL", "level=fatal"),
        diagnosticOnly = true,
      ).also(CoreLogPump::start)
      SafeLog.write("Запуск sing-box ${version(executable)}")

      val tunnel = TunnelReadiness.await(
        process = started,
        logPump = logPump,
        matcher = TunnelMatcher(
          label = ProfileConfiguration.WINDOWS_TUN_INTERFACE,
          matches = {
            it.alias.equals(ProfileConfiguration.WINDOWS_TUN_INTERFACE, ignoreCase = true)
          },
        ),
        requireReadyMarker = true,
      )
      readyTunnel = tunnel
      SafeLog.write("Туннель sing-box поднят: ${tunnel.alias} (интерфейс ${tunnel.index})")
      // The configuration holds credentials and sing-box only reads it at
      // startup, so it is released and removed as soon as the tunnel exists.
      config.close()
      // Keep startup independent from external health endpoints. The session
      // monitor probes browser-equivalent connectivity after Connected is shown.
      EngineHealth.Healthy
    } finally {
      config.close()
    }
  }

  override suspend fun stop() = stopMutex.withLock {
    withContext(Dispatchers.IO) {
      val current = synchronized(this@SingBoxProcessController) { process }
        ?: return@withContext
      val ownedTunnel = readyTunnel
      val ownedDevice = ownedTunnel?.let { OwnedWinTunCleanup.capture(it) }
      SafeLog.write("Остановка sing-box")
      current.destroy()
      if (!current.waitFor(5, TimeUnit.SECONDS)) {
        current.destroyForcibly()
        current.waitFor(2, TimeUnit.SECONDS)
      }
      check(!current.isAlive) { "Процесс sing-box не завершился" }
      synchronized(this@SingBoxProcessController) {
        if (process === current) {
          process = null
          readyTunnel = null
        }
      }
      if (ownedTunnel != null && ownedDevice != null) {
        if (!OwnedWinTunCleanup.remove(ownedTunnel, ownedDevice)) {
          SafeLog.write("Не удалось удалить собственный остановленный TUN-адаптер")
        }
      }
    }
  }

  override fun isAlive(): Boolean = synchronized(this) { process?.isAlive == true }

  override suspend fun health(): EngineHealth =
    readyTunnel?.let { TunnelTrafficVerifier.probe(it) }
      ?: EngineHealth.Unhealthy("туннель не создан", VpnStatusCode.TUNNEL_MISSING)

  override fun statistics(): TunnelStatistics? =
    readyTunnel?.let(TunnelTrafficVerifier::statistics)

  private fun resolveExecutable(): Path {
    return RuntimeResourceLocator.requireFile(
      fileName = "sing-box.exe",
      overridePath = executableOverride,
      environmentName = "VEILARK_SING_BOX",
    )
  }

  private suspend fun checkConfig(executable: Path, config: Path) {
    val result = ProcessBuilder(
      executable.toString(),
      "check",
      "-c",
      config.toString(),
    ).redirectErrorStream(true).start()
      .captureCancellable(20_000)
    if (!result.succeeded) {
      val reason = if (result.timedOut) "тайм-аут проверки" else {
        result.output.lineSequence().lastOrNull().orEmpty().take(240)
      }
      throw VpnStartException(
        code = VpnStatusCode.CONFIG_INVALID,
        message = "Конфигурация sing-box отклонена: $reason",
        detail = "sing-box",
      )
    }
  }

  private fun version(executable: Path): String = runCatching {
    val result = ProcessBuilder(executable.toString(), "version")
      .redirectErrorStream(true)
      .start()
      .capture(5_000)
    result.output.lineSequence().firstOrNull()?.take(120) ?: "unknown"
  }.getOrDefault("unknown")

}
