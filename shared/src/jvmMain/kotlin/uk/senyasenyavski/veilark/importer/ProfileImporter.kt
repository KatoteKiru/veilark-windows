package uk.senyasenyavski.veilark.importer

import com.example.veilark.profile.SubscriptionParser
import uk.senyasenyavski.veilark.model.ImportResult
import uk.senyasenyavski.veilark.model.Node
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.update.UpdateClient
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration

class ProfileImporter(
  private val parser: SubscriptionParser = SubscriptionParser(),
) {
  private val client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(12))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

  fun fromText(text: String, sourceLabel: String = "Вставка"): ImportResult =
    compile(text.toByteArray(Charsets.UTF_8), sourceLabel)

  fun fromFile(path: Path): ImportResult = runCatching {
    require(Files.size(path) <= MAX_BYTES) { "Файл профиля больше 4 МБ" }
    compile(Files.readAllBytes(path), path.fileName.toString())
  }.getOrElse { ImportResult.Failure(it.safeMessage()) }

  fun fromHttps(url: String): ImportResult = runCatching {
    val uri = URI(url.trim())
    require(uri.scheme.equals("https", ignoreCase = true)) {
      "Подписка должна использовать HTTPS"
    }
    require(!uri.host.isNullOrBlank()) { "В ссылке подписки нет адреса сервера" }
    val request = HttpRequest.newBuilder(uri)
      .timeout(Duration.ofSeconds(20))
      // Remnawave and modern x-ui installations dispatch the subscription
      // format by User-Agent. Identifying the actual core prevents the generic
      // base64/browser fallback from exposing only a partial server group.
      .header(
        "User-Agent",
        subscriptionUserAgent(),
      )
      .header("X-Client", "Veilark-Windows/${UpdateClient.CURRENT_VERSION_NAME}")
      .header("Accept", "application/json, text/yaml, application/yaml, text/plain, */*")
      .header("Accept-Encoding", "identity")
      .GET()
      .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
    require(response.statusCode() in 200..299) {
      "Сервер подписки ответил HTTP ${response.statusCode()}"
    }
    require(response.body().size <= MAX_BYTES) { "Подписка больше 4 МБ" }
    val contentType = response.headers().firstValue("content-type").orElse("")
    require(!contentType.contains("text/html", ignoreCase = true)) {
      "Вместо подписки сервер вернул HTML-страницу"
    }
    compile(response.body(), uri.host, uri.toString())
  }.getOrElse { ImportResult.Failure(it.safeMessage()) }

  private fun compile(
    bytes: ByteArray,
    sourceLabel: String,
    sourceUrl: String? = null,
  ): ImportResult = runCatching {
    val text = bytes.toString(Charsets.UTF_8).trim().removePrefix("\uFEFF")
    if (looksLikeTrustTunnelToml(text)) {
      return@runCatching trustTunnelProfile(
        configs = listOf(text),
        sourceLabel = sourceLabel,
        sourceUrl = sourceUrl,
      )
    }
    val trustTunnelLinks = runCatching {
      parser.extractTrustTunnelLinks(bytes)
    }.getOrDefault(emptyList())
    val result = runCatching { parser.compile(bytes) }.getOrElse { parseFailure ->
      if (trustTunnelLinks.isNotEmpty()) {
        return@runCatching trustTunnelProfile(
          configs = trustTunnelLinks,
          sourceLabel = sourceLabel,
          sourceUrl = sourceUrl,
        )
      }
      throw parseFailure
    }
    val singBoxProfile = Profile(
      id = stableId(result.json),
      name = sourceLabel.take(80),
      engine = VpnEngine.SingBox,
      config = result.json,
      nodes = result.nodes.map { Node(it.tag, it.name, it.protocol) },
      sourceLabel = sourceLabel.take(100),
      sourceUrl = sourceUrl,
    )
    val profiles = buildList {
      add(singBoxProfile)
      if (result.trustTunnelLinks.isNotEmpty()) {
        add(createTrustTunnelProfile(result.trustTunnelLinks, sourceLabel, sourceUrl))
      }
    }
    ImportResult.Success(profiles, result.rejectedCount)
  }.getOrElse { ImportResult.Failure(it.safeMessage()) }

  private fun trustTunnelProfile(
    configs: List<String>,
    sourceLabel: String,
    sourceUrl: String? = null,
    rejected: Int = 0,
  ): ImportResult.Success = ImportResult.Success(
    profile = createTrustTunnelProfile(configs, sourceLabel, sourceUrl),
    rejectedProfiles = rejected,
  )

  private fun createTrustTunnelProfile(
    configs: List<String>,
    sourceLabel: String,
    sourceUrl: String? = null,
  ): Profile {
    require(configs.isNotEmpty()) { "Профили TrustTunnel не найдены" }
    val endpointConfigs = linkedMapOf<String, String>()
    val nodes = configs.distinct().mapIndexed { index, config ->
      val tag = "tt-${stableId(config)}"
      endpointConfigs[tag] = config
      Node(
        tag = tag,
        name = trustTunnelName(config, index, sourceLabel),
        protocol = "TrustTunnel",
      )
    }
    return Profile(
      id = stableId(configs.joinToString("\n")),
      name = sourceLabel.take(80),
      engine = VpnEngine.TrustTunnel,
      config = configs.first(),
      nodes = nodes,
      sourceLabel = sourceLabel.take(100),
      endpointConfigs = endpointConfigs,
      sourceUrl = sourceUrl,
    )
  }

  private fun trustTunnelName(
    config: String,
    index: Int,
    sourceLabel: String,
  ): String {
    if (looksLikeTrustTunnelToml(config)) {
      Regex("""(?m)^\s*name\s*=\s*"([^"]+)"""")
        .find(config)
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { return it.take(80) }
    }
    val encodedName = config.substringAfterLast('#', "")
    val decodedName = runCatching {
      URLDecoder.decode(encodedName, Charsets.UTF_8)
    }.getOrDefault(encodedName).trim()
    return decodedName.takeIf(String::isNotBlank)?.take(80)
      ?: if (config.startsWith("tt://", ignoreCase = true)) {
        "TrustTunnel ${index + 1}"
      } else {
        sourceLabel.take(80)
      }
  }

  private fun looksLikeTrustTunnelToml(value: String): Boolean =
    value.contains("[endpoint]") &&
      (
        value.contains("addresses") ||
          value.contains("client_link") ||
          value.contains("username")
        )

  private fun stableId(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.toByteArray(Charsets.UTF_8))
      .take(10)
      .joinToString("") { "%02x".format(it.toInt() and 0xff) }

  internal companion object {
    private const val SING_BOX_COMPAT_VERSION = "1.13.19"
    private const val MAX_BYTES = 4L * 1024 * 1024

    internal fun subscriptionUserAgent(): String =
      "SFA/$SING_BOX_COMPAT_VERSION Veilark/${UpdateClient.CURRENT_VERSION_NAME}"
  }

  private fun Throwable.safeMessage(): String = when (this) {
    is IllegalArgumentException -> message ?: "Профиль не поддерживается"
    else -> "Не удалось импортировать профиль: ${message ?: javaClass.simpleName}"
  }
}
