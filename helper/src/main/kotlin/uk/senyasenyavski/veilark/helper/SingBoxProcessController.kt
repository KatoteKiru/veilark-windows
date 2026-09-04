package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.profile.ProfileConfiguration
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

class SingBoxProcessController(
  private val executableOverride: Path? = null,
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
    writeConfigAtomically(profile.config)
    checkConfig(executable)
    // sing-box cannot reuse a connection name still held by an abandoned core or
    // by an adapter that a previous, force-terminated run left behind.
    CoreProcessJanitor.terminateOrphans(executable)
    WinTunJanitor.removeGhostAdapters()

    val started = ProcessBuilder(
      executable.toString(),
      "run",
      "-c",
      VeilarkPaths.activeConfig.toString(),
    )
      .directory(executable.parent.toFile())
      .redirectErrorStream(true)
      .start()
    synchronized(this@SingBoxProcessController) { process = started }

    val logPump = CoreLogPump(
      process = started,
      engineName = "sing-box",
      readyMarkers = listOf("sing-box started"),
      fatalMarkers = listOf("FATAL", "level=fatal"),
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
    // startup, so it is removed as soon as the tunnel exists.
    Files.deleteIfExists(VeilarkPaths.activeConfig)
    // Keep startup independent from external health endpoints. The session
    // monitor probes browser-equivalent connectivity after Connected is shown.
    EngineHealth.Healthy
  }

  override suspend fun stop() = stopMutex.withLock {
    withContext(Dispatchers.IO) {
      val current = synchronized(this@SingBoxProcessController) { process }
        ?: return@withContext
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
      Files.deleteIfExists(VeilarkPaths.activeConfig)
      delay(700)
      WinTunJanitor.removeGhostAdapters()
    }
  }

  override fun isAlive(): Boolean = synchronized(this) { process?.isAlive == true }

  override suspend fun health(): EngineHealth =
    readyTunnel?.let { TunnelTrafficVerifier.probe(it) }
      ?: EngineHealth.Unhealthy("туннель не создан")

  override fun statistics(): TunnelStatistics? =
    readyTunnel?.let(TunnelTrafficVerifier::statistics)

  private fun resolveExecutable(): Path {
    return RuntimeResourceLocator.requireFile(
      fileName = "sing-box.exe",
      overridePath = executableOverride,
      environmentName = "VEILARK_SING_BOX",
    )
  }

  private suspend fun checkConfig(executable: Path) {
    val result = ProcessBuilder(
      executable.toString(),
      "check",
      "-c",
      VeilarkPaths.activeConfig.toString(),
    ).redirectErrorStream(true).start()
      .captureCancellable(20_000)
    check(result.succeeded) {
      val reason = if (result.timedOut) "тайм-аут проверки" else {
        result.output.lineSequence().lastOrNull().orEmpty().take(240)
      }
      "Конфигурация sing-box отклонена: $reason"
    }
  }

  private fun version(executable: Path): String = runCatching {
    val result = ProcessBuilder(executable.toString(), "version")
      .redirectErrorStream(true)
      .start()
      .capture(5_000)
    result.output.lineSequence().firstOrNull()?.take(120) ?: "unknown"
  }.getOrDefault("unknown")

  private fun writeConfigAtomically(config: String) {
    val temporary = VeilarkPaths.runtimeDirectory.resolve("active.json.tmp")
    Files.writeString(temporary, config, Charsets.UTF_8)
    Files.move(
      temporary,
      VeilarkPaths.activeConfig,
      StandardCopyOption.REPLACE_EXISTING,
      StandardCopyOption.ATOMIC_MOVE,
    )
  }

}
