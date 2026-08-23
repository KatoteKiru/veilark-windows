package uk.senyasenyavski.veilark.session

import kotlinx.coroutines.flow.StateFlow
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.SessionState

interface VpnSession {
  val state: StateFlow<SessionState>

  suspend fun connect(profile: Profile)
  suspend fun disconnect()
}
