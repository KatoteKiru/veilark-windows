package uk.senyasenyavski.veilark.helper

import uk.senyasenyavski.veilark.model.VpnStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

data class TunnelStatistics(
  val alias: String,
  val index: Int,
  val bytesIn: Long,
  val bytesOut: Long,
)

/**
 * Decides whether a tunnel that Windows reports as up is actually carrying the
 * machine's traffic.
 *
 * The check generates a small request and compares the tunnel's byte counters
 * before and after. Counters come straight from the IP Helper interface table,
 * so the verdict does not depend on parsing routing tables and can tell three
 * situations apart: traffic flowing through this tunnel, traffic flowing
 * through a competing VPN, and no connectivity at all.
 */
internal object TunnelTrafficVerifier {
  fun statistics(tunnel: ReadyTunnel): TunnelStatistics? =
    WindowsNetwork.refresh(tunnel.luid)?.let {
      TunnelStatistics(
        alias = it.alias,
        index = it.index,
        bytesIn = it.bytesIn,
        bytesOut = it.bytesOut,
      )
    }

  suspend fun probe(tunnel: ReadyTunnel): EngineHealth = withContext(Dispatchers.IO) {
    val before = WindowsNetwork.refresh(tunnel.luid)
      ?: return@withContext EngineHealth.Unhealthy(
        "туннельный адаптер ${tunnel.alias} исчез из системы",
        VpnStatusCode.ADAPTER_LOST,
        tunnel.alias,
      )
    val failure = WindowsCurlInternetProbe.probe()
    val after = WindowsNetwork.refresh(tunnel.luid)
      ?: return@withContext EngineHealth.Unhealthy(
        "туннельный адаптер ${tunnel.alias} исчез из системы",
        VpnStatusCode.ADAPTER_LOST,
        tunnel.alias,
      )
    val carriedTraffic = after.bytesOut > before.bytesOut && after.bytesIn > before.bytesIn

    when {
      carriedTraffic && failure == null -> EngineHealth.Healthy
      carriedTraffic -> EngineHealth.Unhealthy(
        "туннель передаёт данные, но интернет не отвечает: ${failure?.message}. " +
          "Попробуйте другой узел.",
        VpnStatusCode.INTERNET_UNREACHABLE,
        failure?.code.orEmpty(),
      )
      else -> competingTunnel(tunnel)?.let { competitor ->
        EngineHealth.Unhealthy(
          "трафик идёт через «$competitor», а не через туннель Veilark. " +
            "Отключите другой VPN и подключитесь заново.",
          VpnStatusCode.COMPETING_TUNNEL,
          competitor,
        )
      } ?: if (failure == null) {
        EngineHealth.Unhealthy(
          "интернет работает в обход туннеля — трафик не защищён",
          VpnStatusCode.TRAFFIC_BYPASSES_TUNNEL,
        )
      } else {
        EngineHealth.Unhealthy(
          "нет ответа через туннель: ${failure.message}",
          VpnStatusCode.TUNNEL_NO_RESPONSE,
          failure.code,
        )
      }
    }
  }

  /**
   * Reports a competing full-tunnel VPN when one is present. Another active
   * tunnel owning the default route is the usual reason our own counters stay
   * flat while the internet is still reachable.
   */
  private fun competingTunnel(tunnel: ReadyTunnel): String? =
    WindowsNetwork.tunnels()
      .firstOrNull { it.operational && it.luid != tunnel.luid }
      ?.alias
}

/**
 * Uses the Windows-native curl/Schannel stack for the browser-equivalent
 * connectivity check. The packaged JRE has its own CA store; probing HTTPS
 * through [java.net.HttpURLConnection] therefore produced false PKIX failures
 * on machines where Windows and browsers trusted the connection normally.
 */
/**
 * [code] is a stable, language-independent classification (see
 * [ProbeFailure.Companion]); [message] is technical journal text.
 */
internal data class ProbeFailure(val code: String, val message: String) {
  companion object {
    const val TIMEOUT = "timeout"
    const val DNS = "dns"
    const val UNREACHABLE = "unreachable"
    const val TLS = "tls"
    const val HTTP = "http"
    const val FAILED = "failed"
  }
}

internal object WindowsCurlInternetProbe {
  suspend fun probe(): ProbeFailure? {
    val curl = resolveCurl()
      ?: return ProbeFailure(ProbeFailure.FAILED, "в Windows не найден curl.exe")
    var lastFailure = ProbeFailure(ProbeFailure.FAILED, "нет ответа")
    for (target in TARGETS) {
      val result = try {
        ProcessBuilder(command(curl, target))
          .redirectErrorStream(true)
          .start()
          .captureCancellable(PROCESS_TIMEOUT_MILLIS, maximumOutputChars = 2_000)
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (_: Throwable) {
        lastFailure = ProbeFailure(ProbeFailure.FAILED, "не удалось запустить проверку интернета")
        continue
      }
      val failure = classify(result)
      if (failure == null) return null
      lastFailure = failure
    }
    return lastFailure
  }

  internal fun command(curl: Path, target: String): List<String> = listOf(
    curl.toString(),
    "--noproxy",
    "*",
    "--silent",
    "--show-error",
    "--output",
    "NUL",
    "--write-out",
    "%{http_code}",
    "--connect-timeout",
    "3",
    "--max-time",
    "6",
    target,
  )

  /** Returns `null` only for a completed HTTP exchange. */
  internal fun failure(result: CapturedProcess): String? = classify(result)?.message

  internal fun classify(result: CapturedProcess): ProbeFailure? {
    val status = HTTP_STATUS_AT_END.find(result.output)
      ?.groupValues
      ?.get(1)
      ?.toIntOrNull()
      ?.takeIf { it in 100..599 }
    if (result.succeeded && status != null && status in 200..499) return null
    if (result.timedOut) return ProbeFailure(ProbeFailure.TIMEOUT, "тайм-аут проверки")
    return when (result.exitCode) {
      6 -> ProbeFailure(ProbeFailure.DNS, "DNS не отвечает")
      7 -> ProbeFailure(ProbeFailure.UNREACHABLE, "сервер проверки недоступен")
      28 -> ProbeFailure(ProbeFailure.TIMEOUT, "тайм-аут проверки")
      35, 51, 58, 60, 77 -> ProbeFailure(ProbeFailure.TLS, "Windows не подтвердила защищённое соединение")
      else -> status?.let { ProbeFailure(ProbeFailure.HTTP, "сервер проверки ответил кодом $it") }
        ?: ProbeFailure(ProbeFailure.FAILED, "проверка интернета не выполнена")
    }
  }

  private fun resolveCurl(): Path? {
    val systemRoot = System.getenv("SystemRoot").orEmpty()
    return buildList {
      if (systemRoot.isNotBlank()) add(Path.of(systemRoot, "System32", "curl.exe"))
      add(Path.of("C:\\Windows\\System32\\curl.exe"))
    }.firstOrNull(Files::isRegularFile)
  }

  internal val TARGETS = listOf(
    // Host names deliberately cover both DNS and browser-like HTTPS. Schannel
    // uses the same Windows trust configuration as native desktop clients.
    "https://cp.cloudflare.com/generate_204",
    "https://www.msftconnecttest.com/connecttest.txt",
  )
  private val HTTP_STATUS_AT_END = Regex("""(\d{3})\s*$""")
  private const val PROCESS_TIMEOUT_MILLIS = 8_000L
}
