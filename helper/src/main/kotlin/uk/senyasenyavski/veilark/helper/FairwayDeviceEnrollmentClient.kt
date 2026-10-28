package uk.senyasenyavski.veilark.helper

import com.example.veilark.profile.ClientInstallationIdentity
import com.example.veilark.profile.FairwayDeviceEnrollmentCodec
import com.example.veilark.profile.SubscriptionFetcher
import kotlinx.coroutines.runBlocking
import uk.senyasenyavski.veilark.importer.ProfileImporter
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.security.MessageDigest
import java.time.Duration
import org.json.JSONObject
import uk.senyasenyavski.veilark.update.UpdateClient
import uk.senyasenyavski.veilark.net.boundedByteArrayHandler

/** Explicit Fairway import path; ordinary TrustTunnel/sing-box imports keep their fetcher. */
object FairwayDeviceEnrollment {
  fun importer(): ProfileImporter = importer(
    managed = FairwayDeviceEnrollmentClient()::fetch,
    standard = { source -> runBlocking { SubscriptionFetcher.fetch(source) } },
  )

  internal fun importer(managed: (String) -> ByteArray, standard: (String) -> ByteArray): ProfileImporter =
    ProfileImporter(fetchSubscription = { source ->
      val uri = URI(source)
      if (FairwayDeviceEnrollmentClient.isControlled(uri)) managed(source) else standard(source)
    })
}

/** Account issuer consumer. No native/host/network-policy effects or legacy-key fallback. */
internal class FairwayDeviceEnrollmentClient(
  private val keys: FairwayDeviceKeyStore = FairwayDeviceKeyStore(),
  private val installation: () -> String = ClientInstallationIdentity::get,
  private val send: (HttpRequest) -> Reply = ::https,
) {
  internal class Reply(val status: Int, val noStore: Boolean, val body: ByteArray)

  fun fetch(source: String): ByteArray {
    try {
      val uri = URI(source)
      require(isControlled(uri))
      val installId = installation()
      require(installId.matches(Regex("[A-Za-z0-9_-]{24}")))
      val claim = send(request(uri, installId).GET().build())
      val scope = try {
        requireOk(claim)
        FairwayDeviceEnrollmentCodec.claimScope(claim.body)
      } finally { claim.body.fill(0) }
      return keys.loadOrCreate(scope, installId).use { key ->
        val publicHex = key.publicKey().joinToString("") { "%02x".format(it.toInt() and 255) }
        val requestId = MessageDigest.getInstance("SHA-256").digest(
          "veilark-core-device-enroll-v1\u0000$scope\u0000$publicHex".toByteArray())
          .joinToString("") { "%02x".format(it.toInt() and 255) }
        val body = JSONObject().put("schema", "veilark-core-device-enroll-v1")
          .put("binding_scope", scope).put("client_public_key_hex", publicHex)
          .put("request_id", requestId).toString()
        val reply = send(request(uri, installId).header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(body)).build())
        try {
          requireOk(reply)
          FairwayDeviceEnrollmentCodec.mergeProfile(reply.body, scope, publicHex, key.copyPrivateKey())
        } finally { reply.body.fill(0) }
      }
    } catch (failure: Exception) {
      // URI/JCA/transport exceptions may contain credentials. Expose only an
      // existing fixed UI message, never a raw response or exception cause.
      val message = when ((failure as? EnrollmentStatus)?.status) {
        202, 503 -> "Подписка ещё подготавливается. Повторите обновление немного позже (HTTP 503)"
        401 -> "Сервер подписки требует авторизацию (HTTP 401)"
        409 -> "Лимит устройств подписки исчерпан (HTTP 409)"
        410 -> "Подписка удалена или истекла (HTTP 410)"
        429 -> "Слишком много запросов к подписке (HTTP 429)"
        else -> "Не удалось загрузить подписку"
      }
      throw IllegalArgumentException(message)
    }
  }

  private fun request(uri: URI, installId: String) = HttpRequest.newBuilder(uri)
    .timeout(Duration.ofSeconds(20))
    .header("Accept", "application/json").header("Accept-Encoding", "identity")
    .header("Cache-Control", "no-store")
    .header("User-Agent", "Veilark-Windows/${UpdateClient.CURRENT_VERSION_NAME}")
    .header("X-Veilark-Core-Enrollment", "1")
    .header("X-Veilark-Install-Id", installId)
    .header("X-Veilark-Device-Label", "Windows PC")
    .header("X-Veilark-Device-Model", "Windows PC")
    .header("X-Veilark-Platform", "windows")
    .header("X-Veilark-App-Version", UpdateClient.CURRENT_VERSION_NAME)

  private fun requireOk(reply: Reply) {
    if (reply.status != 200) throw EnrollmentStatus(reply.status)
    require(reply.noStore && reply.body.size in 1..65_536)
  }

  private class EnrollmentStatus(val status: Int) : Exception()

  companion object {
    fun isControlled(uri: URI): Boolean = uri.scheme.equals("https", true) &&
      uri.host.equals("sub.senyasenyavski.uk", true) && uri.port == 2096 &&
      uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
      Regex("/(managed|trust)/[A-Za-z0-9_-]{16,64}").matches(uri.rawPath.orEmpty())

    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
      .followRedirects(HttpClient.Redirect.NEVER).build()

    private fun https(request: HttpRequest): Reply {
      val reply = client.send(request, boundedByteArrayHandler(65_536, "Enrollment response too large"))
      val noStore = reply.headers().allValues("Cache-Control")
        .flatMap { it.split(',') }.any { it.trim().equals("no-store", true) }
      return Reply(reply.statusCode(), noStore, reply.body())
    }
  }
}
