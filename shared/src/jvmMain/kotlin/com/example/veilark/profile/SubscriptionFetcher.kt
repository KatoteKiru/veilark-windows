package com.example.veilark.profile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.senyasenyavski.veilark.importer.ImportException
import uk.senyasenyavski.veilark.model.ImportErrorCode
import uk.senyasenyavski.veilark.net.boundedByteArrayHandler
import java.io.IOException
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
      .getOrElse { throw ImportException(ImportErrorCode.InvalidAddress, "Адрес подписки некорректен", cause = it) }
    repeat(4) { redirect ->
      if (!uri.scheme.equals("https", ignoreCase = true)) {
        throw ImportException(ImportErrorCode.HttpsRequired, "Разрешены только HTTPS-подписки")
      }
      if (uri.host.isNullOrBlank()) {
        throw ImportException(ImportErrorCode.InvalidAddress, "Адрес подписки некорректен")
      }
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
      val response = try {
        httpClient.send(request, boundedByteArrayHandler(MAX_BYTES, TOO_LARGE))
      } catch (error: IOException) {
        if (error.message == TOO_LARGE) throw ImportException(ImportErrorCode.TooLarge, TOO_LARGE)
        throw error
      }
      val status = response.statusCode()
      when (status) {
        in 200..299 -> return validateBody(
          response.body(),
          response.headers().firstValue("content-type").orElse(""),
        )
        in 300..399 -> {
          if (redirect >= 3) {
            throw ImportException(ImportErrorCode.TooManyRedirects, "Слишком много перенаправлений")
          }
          val location = response.headers().firstValue("location").orElse(null)
            ?: throw ImportException(ImportErrorCode.HttpStatus, "Перенаправление без адреса", "$status")
          uri = uri.resolve(location)
        }
        401 -> httpFailure(ImportErrorCode.Unauthorized, "Сервер подписки требует авторизацию", status)
        403 -> httpFailure(ImportErrorCode.Forbidden, "Доступ к подписке запрещён или исчерпан лимит устройств", status)
        404 -> httpFailure(ImportErrorCode.NotFound, "Ссылка подписки не найдена или устарела", status)
        409 -> httpFailure(ImportErrorCode.DeviceLimit, "Лимит устройств подписки исчерпан", status)
        410 -> httpFailure(ImportErrorCode.Gone, "Подписка удалена или истекла", status)
        503 -> httpFailure(
          ImportErrorCode.NotReady,
          "Подписка ещё подготавливается. Повторите обновление немного позже",
          status,
        )
        429 -> httpFailure(ImportErrorCode.RateLimited, "Слишком много запросов к подписке", status)
        else -> httpFailure(ImportErrorCode.HttpStatus, "Сервер подписки ответил", status)
      }
    }
    throw ImportException(ImportErrorCode.TooManyRedirects, "Не удалось загрузить подписку")
  }

  private fun httpFailure(code: ImportErrorCode, message: String, status: Int): Nothing =
    throw ImportException(code, "$message (HTTP $status)", "$status")

  internal fun validateBody(body: ByteArray, contentType: String): ByteArray {
    if (body.size > MAX_BYTES) throw ImportException(ImportErrorCode.TooLarge, TOO_LARGE)
    if (body.isEmpty()) throw ImportException(ImportErrorCode.Empty, "Сервер вернул пустую подписку")
    val prefix = body.decodeToString(0, minOf(body.size, 2_048))
      .trimStart()
      .lowercase()
    if (
      "text/html" in contentType.lowercase() ||
      prefix.startsWith("<!doctype html") ||
      prefix.startsWith("<html")
    ) {
      throw ImportException(ImportErrorCode.WebPage, "Сервер вернул веб-страницу вместо подписки")
    }
    return body
  }

  private const val MAX_BYTES = 4 * 1024 * 1024
  private const val TOO_LARGE = "Подписка больше 4 МБ"
}
