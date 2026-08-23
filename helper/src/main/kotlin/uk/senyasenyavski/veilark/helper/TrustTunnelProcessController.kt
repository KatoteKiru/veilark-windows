package uk.senyasenyavski.veilark.helper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

class TrustTunnelProcessController(
  private val clientOverride: Path? = null,
  private val wizardOverride: Path? = null,
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
    try {
      prepareConfig(profile.config, wizard)
      // tt:// and endpoint TOML must first be expanded by the official wizard;
      // routing is then applied to the complete generated client settings.
      TrustTunnelRouting.apply(trustConfig, routingPlan)
      CoreProcessJanitor.terminateOrphans(client)
      WinTunJanitor.removeGhostAdapters()

      SafeLog.write("Запуск ${version(client)}")
      val started = ProcessBuilder(
        client.toString(),
        "--config",
        trustConfig.toString(),
      )
        .directory(client.parent.toFile())
        .redirectErrorStream(true)
        .start()
      synchronized(this@TrustTunnelProcessController) { process = started }

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
      Files.deleteIfExists(trustConfig)
      Files.deleteIfExists(endpointConfig)
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
      Files.deleteIfExists(trustConfig)
      Files.deleteIfExists(endpointConfig)
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

  private suspend fun prepareConfig(source: String, wizard: Path) {
    when {
      source.trimStart().startsWith("tt://", ignoreCase = true) ->
        runWizard(wizard, "--deeplink", source.trim(), "tt:// профиль")
      source.contains("[listener.tun]") && source.contains("[endpoint]") ->
        writeAtomically(trustConfig, source)
      else -> {
        writeAtomically(endpointConfig, source)
        runWizard(wizard, "--endpoint_config", endpointConfig.toString(), "TOML профиль")
      }
    }
  }

  private suspend fun runWizard(wizard: Path, option: String, value: String, subject: String) {
    val result = ProcessBuilder(
      wizard.toString(),
      "--mode",
      "non-interactive",
      option,
      value,
      "--settings",
      trustConfig.toString(),
    )
      .directory(wizard.parent.toFile())
      .redirectErrorStream(true)
      .start()
      .captureCancellable(20_000)
    check(result.succeeded) {
      val reason = if (result.timedOut) "тайм-аут setup wizard" else {
        result.output.lineSequence().lastOrNull().orEmpty().take(220)
      }
      "TrustTunnel не принял $subject: $reason"
    }
  }

  private fun resolveBinary(
    overridePath: Path?,
    environmentName: String,
    fileName: String,
  ): Path {
    val candidates = buildList {
      overridePath?.let(::add)
      System.getenv(environmentName)?.takeIf(String::isNotBlank)?.let { add(Path.of(it)) }
      System.getProperty("compose.application.resources.dir")
        ?.takeIf(String::isNotBlank)
        ?.let { add(Path.of(it, fileName)) }
      add(
        Path.of("packaging", "resources", "windows", fileName)
          .toAbsolutePath(),
      )
    }
    return candidates.firstOrNull(Files::isRegularFile)
      ?: error("Не найден $fileName. Запустите scripts/bootstrap-runtime.ps1")
  }

  private fun version(client: Path): String = runCatching {
    val result = ProcessBuilder(client.toString(), "--version")
      .redirectErrorStream(true)
      .start()
      .capture(5_000)
    result.output.lineSequence().firstOrNull()?.take(120) ?: "TrustTunnel"
  }.getOrDefault("TrustTunnel")

  private fun writeAtomically(target: Path, content: String) {
    val temporary = target.resolveSibling("${target.fileName}.tmp")
    Files.writeString(temporary, content, Charsets.UTF_8)
    Files.move(
      temporary,
      target,
      StandardCopyOption.REPLACE_EXISTING,
      StandardCopyOption.ATOMIC_MOVE,
    )
  }

  private val trustConfig: Path
    get() = VeilarkPaths.runtimeDirectory.resolve("trusttunnel.toml")
  private val endpointConfig: Path
    get() = VeilarkPaths.runtimeDirectory.resolve("trusttunnel-endpoint.toml")
}
