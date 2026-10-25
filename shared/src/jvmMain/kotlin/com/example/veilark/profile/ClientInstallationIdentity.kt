package com.example.veilark.profile

import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences

/** Existing app identity shared by subscription metadata and local native owners.
 * Not a credential, hardware identifier or proof of ownership. No network access.
 */
object ClientInstallationIdentity {
  fun get(): String {
    // Preserve the established preference location and flush-before-use policy.
    val preferences = Preferences.userRoot().node("uk/senyasenyavski/veilark/subscription-client")
    return SubscriptionClientObservation.installationId(
      current = { preferences.get("install_id", null) },
      persist = { preferences.put("install_id", it); preferences.flush(); true },
      generate = { Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(18).also(SecureRandom()::nextBytes)) },
    )
  }
}
