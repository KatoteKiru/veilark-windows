package com.example.veilark.profile

import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences
import uk.senyasenyavski.veilark.update.UpdateClient

internal object SubscriptionClientHeaders {
  fun forSource(source: String, identity: () -> String = ::installationId): Map<String, String> {
    if (!SubscriptionClientObservation.isControlled(source, "sub.senyasenyavski.uk", 2096)) return emptyMap()
    return mapOf(
      "X-Veilark-Install-Id" to identity(),
      "X-Veilark-Device-Label" to "Windows PC",
      "X-Veilark-Device-Model" to "Windows PC",
      "X-Veilark-Platform" to "windows",
      "X-Veilark-App-Version" to UpdateClient.CURRENT_VERSION_NAME,
    )
  }

  private fun installationId(): String {
    val preferences = Preferences.userRoot().node("uk/senyasenyavski/veilark/subscription-client")
    return SubscriptionClientObservation.installationId(
      current = { preferences.get("install_id", null) },
      persist = { preferences.put("install_id", it); preferences.flush(); true },
      generate = { Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(18).also(SecureRandom()::nextBytes)) },
    )
  }
}
