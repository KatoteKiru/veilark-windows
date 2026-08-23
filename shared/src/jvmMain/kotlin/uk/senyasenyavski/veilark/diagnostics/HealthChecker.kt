package uk.senyasenyavski.veilark.diagnostics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.time.measureTimedValue
import uk.senyasenyavski.veilark.update.UpdateClient

data class HealthTarget(
  val id: String,
  val name: String,
  val url: String,
  val acceptedAuthFailures: Set<Int> = emptySet(),
)

data class HealthResult(
  val target: HealthTarget,
  val reachable: Boolean,
  val statusCode: Int?,
  val latencyMillis: Long,
  val safeError: String? = null,
)

class HealthChecker(
  private val client: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(6))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build(),
) {
  suspend fun checkAll(
    targets: List<HealthTarget> = defaultTargets,
  ): List<HealthResult> = coroutineScope {
    targets.map { target -> async { check(target) } }.awaitAll()
  }

  suspend fun check(target: HealthTarget): HealthResult = withContext(Dispatchers.IO) {
    runCatching {
      val measured = measureTimedValue {
        client.send(
          HttpRequest.newBuilder(URI(target.url))
            .timeout(Duration.ofSeconds(8))
            .header("User-Agent", "Veilark-Windows/${UpdateClient.CURRENT_VERSION_NAME}")
            .GET()
            .build(),
          HttpResponse.BodyHandlers.discarding(),
        )
      }
      val status = measured.value.statusCode()
      HealthResult(
        target = target,
        reachable = isReachable(status, target.acceptedAuthFailures),
        statusCode = status,
        latencyMillis = measured.duration.inWholeMilliseconds,
      )
    }.getOrElse {
      HealthResult(
        target = target,
        reachable = false,
        statusCode = null,
        latencyMillis = 0,
        safeError = when (it) {
          is java.net.http.HttpTimeoutException -> "Тайм-аут"
          is java.net.UnknownHostException -> "DNS недоступен"
          else -> "Нет соединения"
        },
      )
    }
  }

  companion object {
    val defaultTargets = listOf(
      HealthTarget("cloudflare", "Cloudflare", "https://cp.cloudflare.com/generate_204"),
      HealthTarget("youtube", "YouTube", "https://www.youtube.com/generate_204"),
      HealthTarget(
        "chatgpt",
        "ChatGPT",
        "https://chatgpt.com/api/auth/session",
        acceptedAuthFailures = setOf(401, 403),
      ),
      HealthTarget(
        "openai",
        "OpenAI API",
        "https://api.openai.com/v1/models",
        acceptedAuthFailures = setOf(401, 403),
      ),
      HealthTarget(
        "gemini",
        "Gemini",
        "https://generativelanguage.googleapis.com/v1beta/models",
        acceptedAuthFailures = setOf(400, 401, 403),
      ),
    )

    fun isReachable(statusCode: Int, acceptedAuthFailures: Set<Int>): Boolean =
      statusCode in 200..399 || statusCode in acceptedAuthFailures
  }
}
