package uk.senyasenyavski.veilark.helper

import com.example.veilark.profile.CoreArgonProfile
import com.sun.jna.Platform
import java.io.ByteArrayOutputStream
import java.net.http.HttpRequest
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.json.JSONObject
import org.junit.jupiter.api.Assumptions.assumeTrue

class FairwayDeviceEnrollmentClientTest {
  private val scope = "s".repeat(32)
  private val source = "https://sub.senyasenyavski.uk:2096/managed/" + "a".repeat(24)
  private val install = "i".repeat(24)
  private fun reply(status: Int, body: String, noStore: Boolean = true) =
    FairwayDeviceEnrollmentClient.Reply(status, noStore, body.toByteArray())
  private fun claim() = reply(200, """{"schema":"veilark-core-device-claim-v1","binding_scope":"$scope"}""")
  private fun response(public: String): JSONObject {
    val pin = Base64.getEncoder().encodeToString(ByteArray(32) { 2 })
    val profile = JSONObject("""{"schema":"veilark-argon-profile-v1","engine":"argon-v1","beta":true,"name":"Windows test","endpoint":{"ipv4":"192.0.2.1","port":443},"identity":{"server_static_public_base64":"$pin"},"network":{"mtu":1360,"dns_ipv4":["1.1.1.1"]}}""")
    return JSONObject().put("schema", "veilark-core-device-profile-v1").put("binding_scope", scope)
      .put("client_public_key_hex", public).put("profile", profile)
  }

  @Test fun `alternate account links reuse one DPAPI key and public idempotency request`() = directory { path ->
    val posts = mutableListOf<String>()
    val requests = mutableListOf<HttpRequest>()
    val client = FairwayDeviceEnrollmentClient(FairwayDeviceKeyStore(path.resolve("keys")), { install }) { request ->
      requests += request
      assertEquals("1", request.headers().firstValue("X-Veilark-Core-Enrollment").orElse(null))
      assertEquals(install, request.headers().firstValue("X-Veilark-Install-Id").orElse(null))
      if (request.method() == "GET") claim() else {
        val raw = body(request).also(posts::add)
        val data = JSONObject(raw)
        assertEquals(setOf("schema", "binding_scope", "client_public_key_hex", "request_id"), data.keySet())
        assertEquals("veilark-core-device-enroll-v1", data.getString("schema"))
        reply(200, response(data.getString("client_public_key_hex")).toString())
      }
    }
    val first = client.fetch(source)
    val second = client.fetch(source.replace("/managed/", "/trust/"))
    try {
      assertContentEquals(first, second)
      assertEquals(posts[0], posts[1])
      assertFalse(posts[0].contains("private", true))
      assertEquals(listOf("GET", "POST", "GET", "POST"), requests.map { it.method() })
      CoreArgonProfile.parse(first).use { assertEquals("Windows test", it.name) }
      assertEquals(1, Files.list(path.resolve("keys")).use { it.count().toInt() })
    } finally { first.fill(0); second.fill(0) }
  }

  @Test fun `pending claim and cacheable claim never generate a local device key`() = directory { path ->
    for ((index, response) in listOf(reply(202, "{}"), reply(200, "{}", false)).withIndex()) {
      val keyPath = path.resolve("keys-$index")
      val client = FairwayDeviceEnrollmentClient(FairwayDeviceKeyStore(keyPath), { install }) { response }
      assertFailsWith<IllegalArgumentException> { client.fetch(source) }
      assertFalse(Files.exists(keyPath))
      assertTrue(response.body.all { it == 0.toByte() })
    }
  }

  @Test fun `wrong identity or server supplied private key never becomes a usable profile`() = directory { path ->
    for (case in 0..2) {
      val client = FairwayDeviceEnrollmentClient(FairwayDeviceKeyStore(path.resolve("keys-$case")), { install }) { request ->
        if (request.method() == "GET") claim() else {
          val public = JSONObject(body(request)).getString("client_public_key_hex")
          val document = response(public)
          when (case) {
            0 -> document.put("binding_scope", "x".repeat(32))
            1 -> document.put("client_public_key_hex", "00".repeat(32))
            2 -> document.getJSONObject("profile").getJSONObject("identity")
              .put("client_static_private_base64", Base64.getEncoder().encodeToString(ByteArray(32) { 9 }))
          }
          reply(200, document.toString())
        }
      }
      val error = assertFailsWith<IllegalArgumentException> { client.fetch(source) }
      assertEquals("Не удалось загрузить подписку", error.message)
      assertNull(error.cause)
    }
  }

  @Test fun `foreign sources are rejected before identity key or HTTP access`() {
    for (invalid in listOf(source.replace("sub.senyasenyavski.uk", "third-party.example"),
      source + "?extra=1", source + "#key", source.replace("https://", "http://"))) {
      val client = FairwayDeviceEnrollmentClient(installation = { error("Must not read identity") },
        send = { error("Must not send HTTP") })
      assertEquals("Не удалось загрузить подписку",
        assertFailsWith<IllegalArgumentException> { client.fetch(invalid) }.message)
    }
  }

  private fun body(request: HttpRequest): String {
    val output = ByteArrayOutputStream()
    val done = CompletableFuture<Unit>()
    request.bodyPublisher().orElseThrow().subscribe(object : Flow.Subscriber<ByteBuffer> {
      override fun onSubscribe(subscription: Flow.Subscription) = subscription.request(Long.MAX_VALUE)
      override fun onNext(item: ByteBuffer) { output.write(ByteArray(item.remaining()).also(item::get)) }
      override fun onError(error: Throwable) { done.completeExceptionally(error) }
      override fun onComplete() { done.complete(Unit) }
    })
    done.get(2, TimeUnit.SECONDS)
    return output.toString(Charsets.UTF_8)
  }

  private fun directory(action: (Path) -> Unit) {
    assumeTrue(Platform.isWindows(), "Real DPAPI required")
    val path = Files.createTempDirectory("veilark-device-enrollment-")
    try { action(path) } finally {
      Files.walk(path).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
  }
}
