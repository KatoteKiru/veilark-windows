package uk.senyasenyavski.veilark.helper

import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.model.VpnStatusCode

interface EngineController {
  val engine: VpnEngine

  /** Brings the core up and waits until it owns its tunnel adapter. */
  suspend fun start(profile: Profile): EngineHealth

  suspend fun stop()
  fun isAlive(): Boolean
  /** Browser-equivalent connectivity check, executed outside the startup gate. */
  suspend fun health(): EngineHealth = EngineHealth.Healthy

  /** Byte counters of the tunnel adapter, or `null` when no tunnel is up. */
  fun statistics(): TunnelStatistics? = null
}

sealed interface EngineHealth {
  data object Healthy : EngineHealth
  /** [message] is technical log text; [code] is a [VpnStatusCode] for the UI. */
  data class Unhealthy(
    val message: String,
    val code: String = VpnStatusCode.DEGRADED,
    val detail: String = "",
  ) : EngineHealth
}

/**
 * Startup failure with a stable [code] from [VpnStatusCode]. The message stays
 * technical (journal) text; the UI never parses it.
 */
class VpnStartException(
  val code: String,
  message: String,
  val detail: String = "",
  cause: Throwable? = null,
) : IllegalStateException(message, cause)
