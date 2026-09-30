package uk.senyasenyavski.veilark.helper

import uk.senyasenyavski.veilark.model.VpnStatusCode
import kotlinx.coroutines.delay

internal data class ReadyTunnel(
  val index: Int,
  val luid: Long,
  val alias: String,
  val description: String,
)

/**
 * Describes the adapter a core is expected to create.
 *
 * [label] is what the user sees in an error message; [matches] is evaluated
 * against the Windows connection name, which is the only identifier a core
 * actually controls.
 */
internal data class TunnelMatcher(
  val label: String,
  val matches: (NetworkAdapter) -> Boolean,
)

internal object TunnelReadiness {
  /**
   * Waits until the core owns a tunnel adapter.
   *
   * Detection is by Windows connection name rather than by "an interface that
   * did not exist before", because a leaked adapter reuses both the interface
   * index and the driver description and would mask the new tunnel.
   */
  suspend fun await(
    process: Process,
    logPump: CoreLogPump,
    matcher: TunnelMatcher,
    requireReadyMarker: Boolean = false,
    timeoutMillis: Long = 30_000,
  ): ReadyTunnel {
    val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
    var lastCandidate: NetworkAdapter? = null
    while (System.nanoTime() < deadline) {
      check(process.isAlive) {
        "VPN-ядро завершилось до создания туннеля${logPump.reasonSuffix()}"
      }
      logPump.fatal?.let { error("Ядро сообщило об ошибке: $it") }

      // A leaked adapter can carry the same connection name as the tunnel being
      // created, so an operational match always wins over a merely present one.
      val candidates = WindowsNetwork.matching(matcher.matches)
      candidates.firstOrNull(NetworkAdapter::operational)?.let { candidate ->
        if (!requireReadyMarker || logPump.ready) return candidate.toReadyTunnel()
        lastCandidate = candidate
      }
      candidates.firstOrNull()?.let { lastCandidate = it }
      delay(POLL_INTERVAL_MILLIS)
    }

    // The adapter exists but Windows has not flagged it operational yet. The
    // traffic check is a better judge than this status bit, and failing here
    // would tear down a tunnel that may already be carrying packets.
    lastCandidate?.takeIf { !requireReadyMarker || logPump.ready }?.let {
      SafeLog.write("Адаптер ${it.alias} найден, но Windows не отметила его активным")
      return it.toReadyTunnel()
    }
    if (requireReadyMarker && lastCandidate != null && !logPump.ready) {
      throw VpnStartException(
        code = VpnStatusCode.CORE_NOT_READY,
        message = "Ядро не подтвердило подключение туннеля ${matcher.label} за " +
          "${timeoutMillis / 1_000} секунд${logPump.reasonSuffix()}",
        detail = matcher.label,
      )
    }
    throw VpnStartException(
      code = VpnStatusCode.TUN_NOT_CREATED,
      message = "Windows не подняла туннель ${matcher.label} за ${timeoutMillis / 1_000} секунд" +
        logPump.reasonSuffix(),
      detail = matcher.label,
    )
  }

  private fun NetworkAdapter.toReadyTunnel() = ReadyTunnel(
    index = index,
    luid = luid,
    alias = alias,
    description = description,
  )

  private fun CoreLogPump.reasonSuffix(): String =
    tail().lastOrNull()?.let { ". Последнее сообщение ядра: ${it.take(200)}" }.orEmpty()

  private const val POLL_INTERVAL_MILLIS = 200L
}
