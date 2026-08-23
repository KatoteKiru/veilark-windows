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
  data class Degraded(val message: String) : VpnPhase
  data object Stopping : VpnPhase
  data class Error(val message: String, val code: String) : VpnPhase
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
