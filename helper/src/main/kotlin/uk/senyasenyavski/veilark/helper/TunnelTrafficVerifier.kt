package uk.senyasenyavski.veilark.helper

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
      )
    val failure = WindowsCurlInternetProbe.probe()
    val after = WindowsNetwork.refresh(tunnel.luid)
      ?: return@withContext EngineHealth.Unhealthy(
        "туннельный адаптер ${tunnel.alias} исчез из системы",
      )
    val carriedTraffic = after.bytesOut > before.bytesOut && after.bytesIn > before.bytesIn

    when {
      carriedTraffic && failure == null -> EngineHealth.Healthy
      carriedTraffic -> EngineHealth.Unhealthy(
        "туннель передаёт данные, но интернет не отвечает: $failure. " +
          "Попробуйте другой узел.",
      )
      failure == null -> EngineHealth.Unhealthy(
        competingTunnelMessage(tunnel)
          ?: "интернет работает в обход туннеля — трафик не защищён",
      )
      else -> EngineHealth.Unhealthy(
        competingTunnelMessage(tunnel) ?: "нет ответа через туннель: $failure",
      )
    }
  }

  /**
   * Reports a competing full-tunnel VPN when one is present. Another active
   * tunnel owning the default route is the usual reason our own counters stay
   * flat while the internet is still reachable.
   */
  private fun competingTunnelMessage(tunnel: ReadyTunnel): String? {
    val competitor = WindowsNetwork.tunnels()
      .firstOrNull { it.operational && it.luid != tunnel.luid }
      ?: return null
    return "трафик идёт через «${competitor.alias}», а не через туннель Veilark. " +
      "Отключите другой VPN и подключитесь заново."
  }
}

/**
 * Uses the Windows-native curl/Schannel stack for the browser-equivalent
 * connectivity check. The packaged JRE has its own CA store; probing HTTPS
 * through [java.net.HttpURLConnection] therefore produced false PKIX failures
 * on machines where Windows and browsers trusted the connection normally.
 */
internal object WindowsCurlInternetProbe {
  suspend fun probe(): String? {
    val curl = resolveCurl()
      ?: return "в Windows не найден curl.exe"
    var lastFailure = "нет ответа"
    for (target in TARGETS) {
      val result = try {
        ProcessBuilder(command(curl, target))
          .redirectErrorStream(true)
          .start()
          .captureCancellable(PROCESS_TIMEOUT_MILLIS, maximumOutputChars = 2_000)
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (_: Throwable) {
        lastFailure = "не удалось запустить проверку интернета"
        continue
      }
      val failure = failure(result)
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
  internal fun failure(result: CapturedProcess): String? {
    val status = HTTP_STATUS_AT_END.find(result.output)
      ?.groupValues
      ?.get(1)
      ?.toIntOrNull()
      ?.takeIf { it in 100..599 }
    if (result.succeeded && status != null && status in 200..499) return null
    if (result.timedOut) return "тайм-аут проверки"
    return when (result.exitCode) {
      6 -> "DNS не отвечает"
      7 -> "сервер проверки недоступен"
      28 -> "тайм-аут проверки"
      35, 51, 58, 60, 77 -> "Windows не подтвердила защищённое соединение"
      else -> status?.let { "сервер проверки ответил кодом $it" }
        ?: "проверка интернета не выполнена"
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
