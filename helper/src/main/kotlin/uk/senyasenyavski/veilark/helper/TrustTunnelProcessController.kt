package uk.senyasenyavski.veilark.helper

import uk.senyasenyavski.veilark.model.VpnStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.LinkOption
import java.util.UUID
import java.util.concurrent.TimeUnit

class TrustTunnelProcessController(
  private val clientOverride: Path? = null,
  private val wizardOverride: Path? = null,
  private val runtimeDirectory: Path = VeilarkPaths.runtimeDirectory,
) : EngineController {
  override val engine: VpnEngine = VpnEngine.TrustTunnel
  private var process: Process? = null
  private val stopMutex = Mutex()

  @Volatile
  private var readyTunnel: ReadyTunnel? = null

  override suspend fun start(profile: Profile): EngineHealth = withContext(Dispatchers.IO) {
    require(profile.engine == engine) { "Профиль предназначен для другого ядра" }
    synchronized(this@TrustTunnelProcessController) {
      check(process?.isAlive != true) { "TrustTunnel уже запущен" }
    }
    val client = resolveBinary(
      overridePath = clientOverride,
      environmentName = "VEILARK_TRUSTTUNNEL",
      fileName = "trusttunnel_client.exe",
    )
    val wizard = resolveBinary(
      overridePath = wizardOverride,
      environmentName = "VEILARK_TRUSTTUNNEL_WIZARD",
      fileName = "setup_wizard.exe",
    )
    // Build the routing plan before the wizard or any network state is touched.
    // A geo preset without a validated local cache therefore fails closed.
    val routingPlan = TrustTunnelRouting.plan(profile)
    // Legacy fixed names from releases before the locked per-run config.
    Files.deleteIfExists(runtimeDirectory.resolve("trusttunnel.toml"))
    Files.deleteIfExists(runtimeDirectory.resolve("trusttunnel-endpoint.toml"))
    LockedConfigFile.removeStale(runtimeDirectory, "trusttunnel-wizard-", ".toml")
    // tt:// and endpoint TOML must first be expanded by the official wizard;
    // routing is then applied to the complete generated client settings. The
    // final settings are validated in memory and written once to a locked
    // file, so what the client reads is exactly what was validated.
    val compiled = compileConfig(profile.config, wizard)
    TrustTunnelRouting.validateNativeConfigContract(compiled)
    val trustConfig = LockedConfigFile.create(
      directory = runtimeDirectory,
      prefix = "trusttunnel-",
      suffix = ".toml",
      content = TrustTunnelRouting.apply(compiled, routingPlan),
    )
    try {
      CoreProcessJanitor.terminateOrphans(client)
      // Adapter lifetime belongs to the native core. A generic TrustTunnel
      // alias cannot prove ownership of a stopped adapter from another client.
      // Never run the legacy global WinTUN cleanup on this path.

      SafeLog.write("Запуск ${version(client)}")
      val started = NativeProcessDiagnostics.launch(
        executable = NativeProcessDiagnostics.Executable.TRUSTTUNNEL_CLIENT,
        stage = NativeProcessDiagnostics.Stage.CLIENT_START,
      ) {
        ProcessBuilder(
          client.toString(),
          "--config",
          trustConfig.path.toString(),
        )
          .directory(client.parent.toFile())
          .redirectErrorStream(true)
          .start()
      }
      synchronized(this@TrustTunnelProcessController) { process = started }
      // The core must not outlive Veilark (crash or forced exit).
      CoreProcessJob.assignToShared(started, "TrustTunnel")

      val logPump = CoreLogPump(
        process = started,
        engineName = "trusttunnel",
        // This marker is emitted after VPN_SS_CONNECTED. Waiting for the later
        // endpoint confirmation prevents an operational WinTUN adapter from
        // being mistaken for a usable TrustTunnel session.
        readyMarkers = listOf("Successfully connected to endpoint"),
        // TrustTunnel can recover from endpoint failures and DISCONNECTED
        // transitions. Process exit and the overall readiness timeout are the
        // terminal startup signals.
        fatalMarkers = emptyList(),
      ).also(CoreLogPump::start)

      val tunnel = TunnelReadiness.await(
        process = started,
        logPump = logPump,
        matcher = TunnelMatcher(
          label = "TrustTunnel",
          matches = { it.alias.startsWith("TrustTunnel", ignoreCase = true) },
        ),
        requireReadyMarker = true,
      )
      readyTunnel = tunnel
      SafeLog.write("Туннель TrustTunnel поднят: ${tunnel.alias} (интерфейс ${tunnel.index})")
      // Core readiness and adapter ownership are the startup gate. Internet
      // diagnostics run from the session monitor and must never add a second,
      // potentially multi-second gate before the UI leaves Connecting.
      EngineHealth.Healthy
    } finally {
      // The compiled settings contain endpoint credentials and are only read
      // when the client starts.
      trustConfig.close()
    }
  }

  override suspend fun stop() = stopMutex.withLock {
    withContext(Dispatchers.IO) {
      val current = synchronized(this@TrustTunnelProcessController) { process }
        ?: return@withContext
      SafeLog.write("Остановка TrustTunnel")
      current.destroy()
      if (!current.waitFor(7, TimeUnit.SECONDS)) {
        current.destroyForcibly()
        current.waitFor(2, TimeUnit.SECONDS)
      }
      check(!current.isAlive) { "Процесс TrustTunnel не завершился" }
      synchronized(this@TrustTunnelProcessController) {
        if (process === current) {
          process = null
          readyTunnel = null
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

  /** Returns complete client settings; wizard input/output never outlives this call. */
  private suspend fun compileConfig(source: String, wizard: Path): String = when {
    source.trimStart().startsWith("tt://", ignoreCase = true) ->
      runWizard(wizard, "--deeplink", source.trim(), "tt:// профиль")
    source.contains("[listener.tun]") && source.contains("[endpoint]") -> source
    else -> LockedConfigFile.create(runtimeDirectory, "trusttunnel-endpoint-", ".toml", source).use { endpoint ->
      runWizard(wizard, "--endpoint_config", endpoint.path.toString(), "TOML профиль")
    }
  }

  private suspend fun runWizard(wizard: Path, option: String, value: String, subject: String): String {
    // The wizard creates this file itself. It is read back immediately and
    // then only the in-memory copy is validated and used.
    val settings = runtimeDirectory.resolve("trusttunnel-wizard-${UUID.randomUUID()}.toml")
    try {
      val result = NativeProcessDiagnostics.launch(
        executable = NativeProcessDiagnostics.Executable.SETUP_WIZARD,
        stage = NativeProcessDiagnostics.Stage.WIZARD_START,
      ) {
        ProcessBuilder(
          wizard.toString(),
          "--mode",
          "non-interactive",
          option,
          value,
          "--settings",
          settings.toString(),
        )
          .directory(wizard.parent.toFile())
          .redirectErrorStream(true)
          .start()
      }
        .captureCancellable(20_000)
      if (!result.succeeded) {
        NativeProcessDiagnostics.recordExitFailure(
          executable = NativeProcessDiagnostics.Executable.SETUP_WIZARD,
          stage = NativeProcessDiagnostics.Stage.WIZARD_START,
          exitCode = result.exitCode,
          timedOut = result.timedOut,
        )
        val reason = if (result.timedOut) "тайм-аут setup wizard" else {
          result.output.lineSequence().lastOrNull().orEmpty().take(220)
        }
        throw VpnStartException(
          code = VpnStatusCode.CONFIG_INVALID,
          message = "TrustTunnel не принял $subject: $reason",
          detail = "TrustTunnel",
        )
      }
      require(Files.isRegularFile(settings, LinkOption.NOFOLLOW_LINKS)) {
        "TrustTunnel setup wizard не создал настройки"
      }
      return Files.readString(settings, Charsets.UTF_8)
    } finally {
      Files.deleteIfExists(settings)
    }
  }

  private fun resolveBinary(
    overridePath: Path?,
    environmentName: String,
    fileName: String,
  ): Path = RuntimeResourceLocator.requireFile(fileName, overridePath, environmentName)

  private fun version(client: Path): String = runCatching {
    val result = NativeProcessDiagnostics.launch(
      executable = NativeProcessDiagnostics.Executable.TRUSTTUNNEL_CLIENT,
      stage = NativeProcessDiagnostics.Stage.CLIENT_VERSION,
    ) {
      ProcessBuilder(client.toString(), "--version")
        .redirectErrorStream(true)
        .start()
    }.capture(5_000)
    if (!result.succeeded) {
      NativeProcessDiagnostics.recordExitFailure(
        executable = NativeProcessDiagnostics.Executable.TRUSTTUNNEL_CLIENT,
        stage = NativeProcessDiagnostics.Stage.CLIENT_VERSION,
        exitCode = result.exitCode,
        timedOut = result.timedOut,
      )
      "TrustTunnel"
    } else {
      result.output.lineSequence().firstOrNull()?.take(120) ?: "TrustTunnel"
    }
  }.getOrDefault("TrustTunnel")
}
