package uk.senyasenyavski.veilark.model

enum class VpnEngine {
  SingBox,
  TrustTunnel,
}

enum class RoutingMode {
  All,
  RussiaDirect,
  RussiaVpn,
  Manual,
}

data class RoutingSettings(
  val mode: RoutingMode = RoutingMode.All,
  val directEntries: String = "",
  val vpnEntries: String = "",
  val tlsFragment: Boolean = false,
)

/**
 * Runtime-only result of the local geo-data preflight.
 *
 * Paths point at validated sing-box binary rule-sets. [trustTunnelExclusions]
 * is the equivalent compact backend payload for TrustTunnel; it is generated
 * from the same rule-sets and is never entered manually in the UI.
 */
data class GeoRoutingAssets(
  val geoIpRuPath: String,
  val geoSiteCategoryRuPath: String,
  val trustTunnelExclusions: List<String>,
)

sealed interface VpnPhase {
  data object Idle : VpnPhase
  data object NeedsElevation : VpnPhase
  data object Preparing : VpnPhase
  data object Connecting : VpnPhase
  data class Connected(val sinceEpochMillis: Long) : VpnPhase
  /**
   * [message] is a technical (log) description and is not localized. The UI
   * renders [code] (see [VpnStatusCode]) and uses [detail] only as a
   * non-translatable parameter such as an adapter or engine name.
   */
  data class Degraded(
    val message: String,
    val code: String = VpnStatusCode.DEGRADED,
    val detail: String = "",
  ) : VpnPhase
  data object Stopping : VpnPhase
  data class Error(
    val message: String,
    val code: String,
    val stopRequired: Boolean = false,
    val detail: String = "",
  ) : VpnPhase
}

/** Stable, language-independent status codes shown through localized UI strings. */
object VpnStatusCode {
  // Errors
  const val ENGINE_NOT_FOUND = "ENGINE_NOT_FOUND"
  const val CORE_NOT_FOUND = "CORE_NOT_FOUND"
  const val CONFIG_INVALID = "CONFIG_INVALID"
  const val TUN_NAME_TAKEN = "TUN_NAME_TAKEN"
  const val TUN_NOT_CREATED = "TUN_NOT_CREATED"
  const val CORE_NOT_READY = "CORE_NOT_READY"
  const val ELEVATION_REQUIRED = "ELEVATION_REQUIRED"
  const val COMPETING_TUNNEL = "COMPETING_TUNNEL"
  const val CONNECT_TIMEOUT = "CONNECT_TIMEOUT"
  const val CORE_EXITED = "CORE_EXITED"
  const val STOP_FAILED = "STOP_FAILED"
  const val CORE_START_FAILED = "CORE_START_FAILED"

  // Degraded health
  const val DEGRADED = "DEGRADED"
  const val ADAPTER_LOST = "ADAPTER_LOST"
  const val TUNNEL_MISSING = "TUNNEL_MISSING"
  const val INTERNET_UNREACHABLE = "INTERNET_UNREACHABLE"
  const val TRAFFIC_BYPASSES_TUNNEL = "TRAFFIC_BYPASSES_TUNNEL"
  const val TUNNEL_NO_RESPONSE = "TUNNEL_NO_RESPONSE"
  const val HEALTH_CHECK_FAILED = "HEALTH_CHECK_FAILED"
}

val VpnPhase.requiresStopRetry: Boolean get() = this is VpnPhase.Error && stopRequired

val VpnPhase.locksConfiguration: Boolean get() = when (this) {
  VpnPhase.Preparing, VpnPhase.Connecting, is VpnPhase.Connected,
  is VpnPhase.Degraded, VpnPhase.Stopping -> true
  else -> requiresStopRetry
}

data class Node(
  val tag: String,
  val name: String,
  val protocol: String,
)

data class Profile(
  val id: String,
  val name: String,
  val engine: VpnEngine,
  val config: String,
  val nodes: List<Node>,
  val sourceLabel: String,
  val endpointConfigs: Map<String, String> = emptyMap(),
  val sourceUrl: String? = null,
  /** Applied immediately before connect; imported/stored profiles keep defaults. */
  val appliedRouting: RoutingSettings = RoutingSettings(),
  /** Present only for geo presets after a successful local cache preflight. */
  val geoRoutingAssets: GeoRoutingAssets? = null,
)

sealed interface ImportResult {
  data class Success(
    val profiles: List<Profile>,
    val rejectedProfiles: Int,
  ) : ImportResult {
    init {
      require(profiles.isNotEmpty()) { "Импорт должен содержать хотя бы один профиль" }
    }

    val profile: Profile
      get() = profiles.first()

    constructor(profile: Profile, rejectedProfiles: Int) :
      this(listOf(profile), rejectedProfiles)
  }
  data class Failure(val safeMessage: String) : ImportResult
}

/** Byte counters of the active tunnel adapter, as reported by Windows. */
data class TrafficSnapshot(
  val adapter: String,
  val bytesIn: Long,
  val bytesOut: Long,
)

data class SessionState(
  val engine: VpnEngine = VpnEngine.SingBox,
  val phase: VpnPhase = VpnPhase.Idle,
  val profile: Profile? = null,
  val traffic: TrafficSnapshot? = null,
)
