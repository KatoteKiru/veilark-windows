package com.example.veilark.profile

import uk.senyasenyavski.veilark.update.UpdateClient

internal object SubscriptionClientHeaders {
  fun forSource(source: String, identity: () -> String = ClientInstallationIdentity::get): Map<String, String> {
    if (!SubscriptionClientObservation.isControlled(source, "sub.senyasenyavski.uk", 2096)) return emptyMap()
    return mapOf(
      "X-Veilark-Install-Id" to identity(),
      "X-Veilark-Device-Label" to "Windows PC",
      "X-Veilark-Device-Model" to "Windows PC",
      "X-Veilark-Platform" to "windows",
      "X-Veilark-App-Version" to UpdateClient.CURRENT_VERSION_NAME,
    )
  }

}
