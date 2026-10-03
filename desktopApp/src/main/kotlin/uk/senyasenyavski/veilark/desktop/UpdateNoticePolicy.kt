package uk.senyasenyavski.veilark.desktop

import java.util.prefs.Preferences

/** Stores only a public release counter; no client identity or VPN data. */
internal class UpdateNoticePolicy(
  private val read: () -> Int,
  private val write: (Int) -> Unit,
) {
  private var attempted = read()
  fun shouldNotify(version: Int): Boolean = version > attempted
  fun markAttempted(version: Int) {
    attempted = maxOf(attempted, version)
    runCatching { write(attempted) }
  }
  companion object {
    const val INTERVAL_MS = 6L * 60 * 60 * 1000
    fun persistent(): UpdateNoticePolicy {
      val prefs = runCatching { Preferences.userRoot().node("uk/senyasenyavski/veilark/update-notices") }.getOrNull()
      return UpdateNoticePolicy({ runCatching { prefs?.getInt("last", 0) ?: 0 }.getOrDefault(0) }, { prefs?.putInt("last", it) })
    }
  }
}
