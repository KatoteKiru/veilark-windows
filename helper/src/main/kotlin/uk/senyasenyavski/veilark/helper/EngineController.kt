package uk.senyasenyavski.veilark.helper

import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine

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
  data class Unhealthy(val message: String) : EngineHealth
}
