package uk.senyasenyavski.veilark.helper

import com.example.veilark.profile.CoreArgonHandshakeProfile
import com.example.veilark.profile.CoreArgonProfile
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.RoutingMode
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.model.VpnStatusCode

/** Supplied by the application's installation/uplink preparation, never profile JSON. */
internal class FairwayHostInputs(val wintunPath: Path, val uplinkLuid: Long, val adapterIdentity: FairwayAdapterIdentity)

/** Ready for the release factory, deliberately absent from default registration.
 * App acceptance and native FULL_TUN are independent gates; HOST_LAB is insufficient.
 */
internal class FairwayEngineController(
  private val loadLibrary: () -> FairwayNativeLibrary,
  private val prepareHost: suspend (endpointIpv4: String) -> FairwayHostInputs,
  private val productAccepted: () -> Boolean = { VpnEngine.VeilarkCore.availableInProduct },
) : EngineController {
  override val engine = VpnEngine.VeilarkCore
  private val lifecycle = Mutex()
  @Volatile private var library: FairwayNativeLibrary? = null
  @Volatile private var owner: FairwayNativeLibrary.Owner? = null
  @Volatile private var starting = false
  @Volatile private var readyTunnel: ReadyTunnel? = null
  @Volatile private var adapterIdentity: FairwayAdapterIdentity? = null

  override suspend fun start(profile: Profile): EngineHealth = withContext(Dispatchers.IO) {
    require(profile.engine == engine) { "Профиль предназначен для другого ядра" }
    if (!productAccepted()) throw unavailable()
    lifecycle.withLock {
      check(!hasOwnedResources()) { "Fairway retains resources; stop before reconnecting" }
      starting = true
      try {
        val routing = profile.appliedRouting
        require(!routing.tlsFragment && routing.mode in setOf(RoutingMode.All, RoutingMode.RussiaDirect)) {
          "Unsupported Fairway routing mode"
        }
        val prefixes = if (routing.mode == RoutingMode.RussiaDirect) {
          FairwayIpv4Routes.fromGeoAssets(requireNotNull(profile.geoRoutingAssets) { "Fairway Geo assets required" })
        } else emptyList()
        val loaded = loadLibrary().also { library = it }
        if (!loaded.supportsFullTunnel || !loaded.supportsHostConfiguration) throw unavailable()
        CoreArgonProfile.parse(profile.config).use { core ->
          require(core.handshakeProfile == CoreArgonHandshakeProfile.LEGACY) { "Unsupported Core wire profile" }
          val host = prepareHost(core.endpointIpv4)
          currentCoroutineContext().ensureActive()
          adapterIdentity = host.adapterIdentity
          val owned = loaded.create(core.clientStaticPrivateCopy(), core.serverStaticPublicCopy(), host.adapterIdentity.windowsGuidBytes(),
            ipv4(core.endpointIpv4), core.endpointPort, core.mtu, host.uplinkLuid)
          // Publish custody before any suspend point or native OS effect.
          owner = owned
          owned.configureHost(host.wintunPath, core.dnsIpv4.map(::ipv4), prefixes)
        }
        currentCoroutineContext().ensureActive()
        val owned = checkNotNull(owner)
        owned.beginAuthForTunnel()
        awaitPhase(owned, AUTHENTICATED)
        currentCoroutineContext().ensureActive()
        owned.startHost()
        awaitPhase(owned, RUNNING)
        val identity = checkNotNull(adapterIdentity)
        val adapter = WindowsNetwork.adapters().singleOrNull {
          identity.matchesWindowsGuid(it.interfaceGuid) && it.operational && it.isTunnel
        } ?: throw VpnStartException(VpnStatusCode.TUN_NOT_CREATED, "Owned Fairway adapter is unavailable")
        readyTunnel = ReadyTunnel(adapter.index, adapter.luid, adapter.alias, adapter.description)
        EngineHealth.Healthy
      } finally { starting = false }
    }
  }

  override suspend fun stop() = withContext(NonCancellable + Dispatchers.IO) {
    lifecycle.withLock {
      owner?.let { owned ->
        owned.stopAndRelease(STOP_JOIN_MILLIS)
        check(!owned.hasOwnedResources()) { "Fairway owner cleanup incomplete" }
        owner = null
      }
      // Keep a failed close in custody too; do not clear before success.
      library?.close()
      library = null
      readyTunnel = null
      adapterIdentity = null
    }
  }

  override fun hasOwnedResources(): Boolean = starting || owner != null || library != null

  override fun isAlive(): Boolean = try { owner?.status()?.workerAlive == true } catch (_: Exception) { false }

  override suspend fun health(): EngineHealth = withContext(Dispatchers.IO) {
    val status = try { owner?.status() } catch (_: Exception) { null }
    if (status == null || status.phase != RUNNING || !status.workerAlive) {
      EngineHealth.Unhealthy("Veilark Core is not running", VpnStatusCode.CORE_EXITED,
        status?.terminalOutcome?.toString().orEmpty())
    } else {
      val adapter = readyTunnel?.let { WindowsNetwork.refresh(it.luid) }
      if (adapter?.operational == true && adapterIdentity?.matchesWindowsGuid(adapter.interfaceGuid) == true) EngineHealth.Healthy
      else EngineHealth.Unhealthy("Owned Fairway adapter is unavailable", VpnStatusCode.ADAPTER_LOST)
    }
  }

  // Read real OS counters on the known owned LUID; no synthetic index or alias matching.
  override fun statistics(): TunnelStatistics? = readyTunnel?.let(TunnelTrafficVerifier::statistics)

  private suspend fun awaitPhase(owned: FairwayNativeLibrary.Owner, expected: Int) {
    while (true) {
      currentCoroutineContext().ensureActive()
      val status = owned.status()
      if (status.phase == expected && status.workerAlive) return
      if (status.terminalOutcome != 0 || !status.workerAlive || status.phase >= STOPPING) {
        throw VpnStartException(VpnStatusCode.CORE_START_FAILED,
          "Veilark Core stopped (phase=${status.phase}, outcome=${status.terminalOutcome}, os=${status.nativeError})")
      }
      delay(100) // Startup only; WindowsVpnSession owns the overall 90-second deadline.
    }
  }

  private fun unavailable() = VpnStartException(VpnStatusCode.CORE_NOT_READY, "Fairway for Windows is not ready for product activation")
  private fun ipv4(text: String) = text.split('.').map { it.toInt().toByte() }.toByteArray()

  private companion object {
    const val AUTHENTICATED = 3
    const val RUNNING = 4
    const val STOPPING = 5
    const val STOP_JOIN_MILLIS = 10_000
  }
}
