package uk.senyasenyavski.veilark.helper

/** Fails before core startup when another WinTUN/WireGuard tunnel is active. */
object ActiveTunnelConflict {
  /** Alias of the competing operational tunnel (non-localized), or `null`. */
  fun competitorAlias(): String? {
    val competitor = WindowsNetwork.tunnels().firstOrNull(NetworkAdapter::operational)
      ?: return null
    return competitor.alias.ifBlank { "VPN" }
  }
}
