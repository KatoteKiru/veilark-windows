package com.example.veilark.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.senyasenyavski.veilark.net.boundedByteArrayHandler
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.Duration
import uk.senyasenyavski.veilark.importer.ProfileImporter
import uk.senyasenyavski.veilark.update.UpdateClient

/**
 * JVM counterpart of the Android fetcher. Kept in the shared package so the
 * parser's Android tests can run unchanged on Windows.
 */
object SubscriptionFetcher {
  private val client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NEVER)
    .build()

  suspend fun fetch(
    source: String,
    headers: Map<String, String> = SubscriptionClientHeaders.forSource(source),
  ): ByteArray = withContext(Dispatchers.IO) {
    fetchBlocking(source, headers)
  }

  internal fun fetchBlocking(
    source: String,
    headers: Map<String, String> = SubscriptionClientHeaders.forSource(source),
    httpClient: HttpClient = client,
  ): ByteArray {
    var uri = runCatching { URI(source.trim()) }
      .getOrElse { throw IllegalArgumentException("Адрес подписки некорректен", it) }
    repeat(4) { redirect ->
      require(uri.scheme.equals("https", ignoreCase = true)) {
        "Разрешены только HTTPS-подписки"
      }
      require(!uri.host.isNullOrBlank()) { "Адрес подписки некорректен" }
      val request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofSeconds(20))
        .header("User-Agent", ProfileImporter.subscriptionUserAgent())
        .header("X-Client", "Veilark-Windows/${UpdateClient.CURRENT_VERSION_NAME}")
        .header(
          "Accept",
          "application/json, text/yaml, application/yaml, text/plain, " +
            "application/octet-stream, */*",
        )
        .header("Accept-Encoding", "identity")
        .apply { SubscriptionClientObservation.headersForHop(headers, redirect).forEach(::header) }
        .GET()
        .build()
      val response = httpClient.send(
        request,
        boundedByteArrayHandler(MAX_BYTES, "Подписка больше 4 МБ"),
      )
      when (response.statusCode()) {
        in 200..299 -> return validateBody(
          response.body(),
          response.headers().firstValue("content-type").orElse(""),
        )
        in 300..399 -> {
          require(redirect < 3) { "Слишком много перенаправлений" }
          val location = response.headers().firstValue("location").orElse(null)
            ?: error("Перенаправление без адреса")
          uri = uri.resolve(location)
        }
        401 -> error("Сервер подписки требует авторизацию (HTTP 401)")
        403 -> error("Доступ к подписке запрещён или исчерпан лимит устройств (HTTP 403)")
        404 -> error("Ссылка подписки не найдена или устарела (HTTP 404)")
        409 -> error("Лимит устройств подписки исчерпан (HTTP 409)")
        410 -> error("Подписка удалена или истекла (HTTP 410)")
        503 -> error("Подписка ещё подготавливается. Повторите обновление немного позже (HTTP 503)")
        429 -> error("Слишком много запросов к подписке (HTTP 429)")
        else -> error("Сервер подписки ответил HTTP ${response.statusCode()}")
      }
    }
    error("Не удалось загрузить подписку")
  }

  internal fun validateBody(body: ByteArray, contentType: String): ByteArray {
    require(body.size <= MAX_BYTES) { "Подписка больше 4 МБ" }
    require(body.isNotEmpty()) { "Сервер вернул пустую подписку" }
    val prefix = body.decodeToString(0, minOf(body.size, 2_048))
      .trimStart()
      .lowercase()
    require(
      "text/html" !in contentType.lowercase() &&
        !prefix.startsWith("<!doctype html") &&
        !prefix.startsWith("<html"),
    ) {
      "Сервер вернул веб-страницу вместо подписки"
    }
    return body
  }

  private const val MAX_BYTES = 4 * 1024 * 1024
}
