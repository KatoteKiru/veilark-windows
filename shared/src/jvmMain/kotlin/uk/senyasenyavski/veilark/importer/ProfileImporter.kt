package uk.senyasenyavski.veilark.importer

import com.example.veilark.profile.SubscriptionParser
import com.example.veilark.profile.SubscriptionFetcher
import uk.senyasenyavski.veilark.model.ImportErrorCode
import uk.senyasenyavski.veilark.model.ImportResult
import uk.senyasenyavski.veilark.model.Node
import uk.senyasenyavski.veilark.model.Profile
import uk.senyasenyavski.veilark.model.VpnEngine
import uk.senyasenyavski.veilark.update.UpdateClient
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * An import failure with a known cause. [message] is technical journal text;
 * the UI renders [code], with [detail] as an untranslated parameter.
 */
internal class ImportException(
  val code: ImportErrorCode,
  message: String,
  val detail: String = "",
  cause: Throwable? = null,
) : IllegalStateException(message, cause)

class ProfileImporter(
  private val parser: SubscriptionParser = SubscriptionParser(),
  private val fetchSubscription: (String) -> ByteArray = { SubscriptionFetcher.fetchBlocking(it) },
) {
  fun fromText(text: String, sourceLabel: String = "Вставка"): ImportResult =
    compile(text.toByteArray(Charsets.UTF_8), sourceLabel)

  fun fromFile(path: Path): ImportResult = runCatching {
    if (Files.size(path) > MAX_BYTES) {
      throw ImportException(ImportErrorCode.TooLarge, "Файл профиля больше 4 МБ")
    }
    compile(Files.readAllBytes(path), path.fileName.toString())
  }.getOrElse(::failure)

  fun fromHttps(url: String): ImportResult = runCatching {
    val uri = runCatching { URI(url.trim()) }.getOrElse {
      throw ImportException(ImportErrorCode.InvalidAddress, "Адрес подписки некорректен", cause = it)
    }
    if (!uri.scheme.equals("https", ignoreCase = true)) {
      throw ImportException(ImportErrorCode.HttpsRequired, "Подписка должна использовать HTTPS")
    }
    if (uri.host.isNullOrBlank()) {
      throw ImportException(ImportErrorCode.InvalidAddress, "В ссылке подписки нет адреса сервера")
    }
    val body = fetchSubscription(uri.toString())
    compile(body, uri.host, uri.toString())
  }.getOrElse(::failure)

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
  }.getOrElse(::failure)

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
    private const val SING_BOX_COMPAT_VERSION = "1.13.21"
    private const val MAX_BYTES = 4L * 1024 * 1024

    internal fun subscriptionUserAgent(): String =
      "SFA/$SING_BOX_COMPAT_VERSION Veilark/${UpdateClient.CURRENT_VERSION_NAME}"
  }

  /**
   * Parser messages are Russian descriptions of what was rejected; they are
   * passed as [ImportResult.Failure.detail] for the Russian UI only.
   */
  private fun failure(error: Throwable): ImportResult.Failure = when (error) {
    is ImportException -> ImportResult.Failure(error.code, error.detail)
    is IOException -> ImportResult.Failure(ImportErrorCode.Network)
    is IllegalArgumentException, is IllegalStateException ->
      ImportResult.Failure(ImportErrorCode.Unsupported, error.message.orEmpty())
    else -> ImportResult.Failure(
      ImportErrorCode.Failed,
      "Не удалось импортировать профиль: ${error.message ?: error.javaClass.simpleName}",
    )
  }
}
