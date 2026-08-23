package uk.senyasenyavski.veilark.helper

/** Fails before core startup when another WinTUN/WireGuard tunnel is active. */
object ActiveTunnelConflict {
  fun message(): String? {
    val competitor = WindowsNetwork.tunnels().firstOrNull(NetworkAdapter::operational)
      ?: return null
    val name = competitor.alias.ifBlank { "другой VPN" }
    return "Активен другой VPN «$name». Отключите его и повторите подключение Veilark."
  }
}
