package com.example.veilark.profile

import java.net.Authenticator
import java.net.CookieHandler
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import uk.senyasenyavski.veilark.importer.ProfileImporter
import uk.senyasenyavski.veilark.model.ImportResult
import uk.senyasenyavski.veilark.model.VpnEngine

class SubscriptionObservationRequestTest {
  private val id = "_" + "a".repeat(23)

  @Test fun `production import builder sends tuple and strips it after redirect for both engines`() {
    for (path in listOf("managed", "trust")) {
      val source = "https://sub.senyasenyavski.uk:2096/$path/abcdefghijklmnop"
      val client = FakeClient(listOf(307, 200))
      val importer = ProfileImporter(fetchSubscription = {
        SubscriptionFetcher.fetchBlocking(it, SubscriptionClientHeaders.forSource(it) { id }, client)
      })
      val imported = assertIs<ImportResult.Success>(importer.fromHttps(source))
      assertEquals(VpnEngine.TrustTunnel, imported.profile.engine)
      assertEquals(source, imported.profile.sourceUrl)
      assertEquals(2, client.requests.size)
      assertEquals(id, client.requests[0].headers().firstValue("X-Veilark-Install-Id").orElse(null))
      for (name in listOf("Device-Label", "Device-Model", "Platform", "App-Version")) {
        assertTrue(client.requests[0].headers().firstValue("X-Veilark-$name").orElse("").isNotBlank())
      }
      assertTrue(client.requests[1].headers().map().keys.none { it.startsWith("x-veilark-", true) })
      assertEquals("third-party.example", client.requests[1].uri().host)
    }
  }

  @Test fun `third parties and spoof hosts never generate or receive installation metadata`() {
    for (source in listOf("https://third-party.example/sub/test", "https://sub.senyasenyavski.uk.evil.example:2096/trust/abcdefghijklmnop")) {
      val headers = SubscriptionClientHeaders.forSource(source) { error("must not generate identity") }
      assertTrue(headers.isEmpty())
      val client = FakeClient(listOf(200))
      SubscriptionFetcher.fetchBlocking(source, headers, client)
      assertTrue(client.requests.single().headers().map().keys.none { it.startsWith("x-veilark-", true) })
    }
  }

  @Test fun `redirect downgrade and device capacity failures remain explicit`() {
    val source = "https://sub.senyasenyavski.uk:2096/trust/abcdefghijklmnop"
    val client = FakeClient(listOf(307), "http://third-party.example/sub/test")
    assertTrue(runCatching { SubscriptionFetcher.fetchBlocking(source, emptyMap(), client) }.isFailure)
    assertEquals(1, client.requests.size)
    for (status in listOf(409, 503)) {
      val failure = runCatching { SubscriptionFetcher.fetchBlocking(source, emptyMap(), FakeClient(listOf(status))) }.exceptionOrNull()
      assertTrue(failure?.message.orEmpty().contains("HTTP $status"))
      assertTrue(!failure?.message.orEmpty().contains("abcdefghijklmnop"))
    }
  }

  private class FakeClient(private val statuses: List<Int>, private val destination: String = "https://third-party.example/sub/result") : HttpClient() {
    val requests = mutableListOf<HttpRequest>()
    override fun <T : Any?> send(request: HttpRequest, responseBodyHandler: HttpResponse.BodyHandler<T>): HttpResponse<T> {
      val status = statuses[minOf(requests.size, statuses.lastIndex)]
      requests += request
      return object : HttpResponse<T> {
        override fun statusCode() = status
        override fun request() = request
        override fun previousResponse(): Optional<HttpResponse<T>> = Optional.empty()
        override fun headers() = HttpHeaders.of(mapOf("content-type" to listOf("text/plain"), "location" to listOf(destination))) { _, _ -> true }
        @Suppress("UNCHECKED_CAST") override fun body(): T = "tt://synthetic".toByteArray() as T
        override fun sslSession(): Optional<SSLSession> = Optional.empty()
        override fun uri(): URI = request.uri()
        override fun version() = Version.HTTP_1_1
      }
    }
    override fun cookieHandler(): Optional<CookieHandler> = Optional.empty()
    override fun connectTimeout(): Optional<Duration> = Optional.empty()
    override fun followRedirects() = Redirect.NEVER
    override fun proxy(): Optional<ProxySelector> = Optional.empty()
    override fun sslContext(): SSLContext = SSLContext.getDefault()
    override fun sslParameters() = SSLParameters()
    override fun authenticator(): Optional<Authenticator> = Optional.empty()
    override fun version() = Version.HTTP_1_1
    override fun executor(): Optional<Executor> = Optional.empty()
    override fun <T : Any?> sendAsync(request: HttpRequest, responseBodyHandler: HttpResponse.BodyHandler<T>): CompletableFuture<HttpResponse<T>> = error("not used")
    override fun <T : Any?> sendAsync(request: HttpRequest, responseBodyHandler: HttpResponse.BodyHandler<T>, pushPromiseHandler: HttpResponse.PushPromiseHandler<T>): CompletableFuture<HttpResponse<T>> = error("not used")
  }
}
