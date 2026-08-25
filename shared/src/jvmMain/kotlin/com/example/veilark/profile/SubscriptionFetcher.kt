package com.example.veilark.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.senyasenyavski.veilark.net.boundedByteArrayHandler
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * JVM counterpart of the Android fetcher. Kept in the shared package so the
 * parser's Android tests can run unchanged on Windows.
 */
object SubscriptionFetcher {
  private val client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

  suspend fun fetch(
    source: String,
    headers: Map<String, String> = emptyMap(),
  ): ByteArray = withContext(Dispatchers.IO) {
    val uri = runCatching { URI(source.trim()) }
      .getOrElse { throw IllegalArgumentException("Адрес подписки некорректен", it) }
    require(uri.scheme.equals("https", ignoreCase = true)) {
      "Разрешены только HTTPS-подписки"
    }
    require(!uri.host.isNullOrBlank()) { "Адрес подписки некорректен" }
    val request = HttpRequest.newBuilder(uri)
      .timeout(Duration.ofSeconds(20))
      .header("User-Agent", "SFA/1.13.19 Veilark/Windows-0.1")
      .header("X-Client", "Veilark")
      .header(
        "Accept",
        "application/json, text/yaml, application/yaml, text/plain, " +
          "application/octet-stream, */*",
      )
      .apply { headers.forEach(::header) }
      .GET()
      .build()
    val response = client.send(
      request,
      boundedByteArrayHandler(MAX_BYTES, "Подписка больше 4 МБ"),
    )
    when (response.statusCode()) {
      in 200..299 -> validateBody(
        response.body(),
        response.headers().firstValue("content-type").orElse(""),
      )
      401 -> error("Сервер подписки требует авторизацию (HTTP 401)")
      403 -> error("Доступ к подписке запрещён или исчерпан лимит устройств (HTTP 403)")
      404 -> error("Ссылка подписки не найдена или устарела (HTTP 404)")
      410 -> error("Подписка удалена или истекла (HTTP 410)")
      429 -> error("Слишком много запросов к подписке (HTTP 429)")
      else -> error("Сервер подписки ответил HTTP ${response.statusCode()}")
    }
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
